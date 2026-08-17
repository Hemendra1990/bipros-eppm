package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.role.EquipmentRoleVariant;
import com.bipros.resource.domain.model.role.ManpowerRoleRate;
import com.bipros.resource.domain.model.role.MaterialRoleVariant;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import com.bipros.resource.domain.repository.role.EquipmentRoleVariantRepository;
import com.bipros.resource.domain.repository.role.ManpowerRoleRateRepository;
import com.bipros.resource.domain.repository.role.MaterialRoleVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the client's rate book ("Requirements final - 01 Aug 2026.xlsx", Rates sheet) into the
 * role-variant masters that drive the DPR pickers and activity resource planning:
 * materials → {@code material_role_variants}, plant → {@code equipment_role_variants} (per-Day),
 * manpower → {@code manpower_role_rates} (category National/Expat, grade A, daily = salary/26).
 *
 * <p><b>Runs in every profile including production</b> (deliberately NOT gated behind the demo
 * profiles). Safety properties:
 * <ul>
 *   <li>Insert-only idempotency — roles are keyed by client code, variants by their natural key;
 *       existing rows are NEVER updated, so rates edited in-app survive every redeploy.</li>
 *   <li>Rows with no rate in the book seed the role only (visible in Admin, absent from DPR
 *       pickers until someone enters a rate) — nothing can silently cost 0.</li>
 *   <li>Fast-skip marker: GlobalSetting {@code client_rate_book_version} records the applied
 *       version; warm boots do one lookup. Bump {@code version} in the JSON to apply a revised
 *       book (still insert-only).</li>
 *   <li>Single transaction — a failed pass leaves nothing half-seeded.</li>
 * </ul>
 *
 * <p>Order 70: after {@link ResourceTypeSeeder} (50) which guarantees the MANPOWER / EQUIPMENT /
 * MATERIAL type rows exist on a clean database.
 */
@Slf4j
@Component
@Order(70)
@RequiredArgsConstructor
public class ClientRateBookSeeder implements CommandLineRunner {

  static final String VERSION_KEY = "client_rate_book_version";
  /** Working days per month — the sheet's own convention (plant Rate/d values are monthly/26). */
  static final BigDecimal DAYS_PER_MONTH = new BigDecimal("26");

  private final ResourceRoleRepository roleRepository;
  private final ResourceTypeRepository typeRepository;
  private final ManpowerCategoryMasterRepository categoryRepository;
  private final GradeMasterRepository gradeRepository;
  private final ManpowerRoleRateRepository manpowerRateRepository;
  private final EquipmentRoleVariantRepository equipmentVariantRepository;
  private final MaterialRoleVariantRepository materialVariantRepository;
  private final GlobalSettingRepository globalSettingRepository;
  private final ObjectMapper objectMapper;

  private int rolesInserted;
  private int rolesSkipped;
  private int ratesInserted;
  private int ratesSkipped;
  private final List<String> heldBackNoRate = new ArrayList<>();

  @Override
  @Transactional
  public void run(String... args) {
    ClientRateBook book;
    try {
      book = ClientRateBook.load(objectMapper);
    } catch (IOException e) {
      log.error("[ClientRateBookSeeder] failed to load {} — client rate book not seeded",
          ClientRateBook.RESOURCE_PATH, e);
      return;
    }

    int applied = globalSettingRepository.findBySettingKey(VERSION_KEY)
        .map(s -> parseIntSafe(s.getSettingValue()))
        .orElse(0);
    if (applied >= book.version()) {
      log.debug("[ClientRateBookSeeder] version {} already applied — skipping", applied);
      return;
    }

    ResourceType manpowerType = typeRepository.findByCode("MANPOWER")
        .or(() -> typeRepository.findByCode("LABOR")).orElse(null);
    ResourceType equipmentType = typeRepository.findByCode("EQUIPMENT").orElse(null);
    ResourceType materialType = typeRepository.findByCode("MATERIAL").orElse(null);
    if (manpowerType == null || equipmentType == null || materialType == null) {
      log.warn("[ClientRateBookSeeder] resource types missing (manpower={}, equipment={}, "
              + "material={}) — aborting; ResourceTypeSeeder should run first",
          manpowerType != null, equipmentType != null, materialType != null);
      return;
    }

    GradeMaster gradeA = ensureGradeA();
    Map<String, ManpowerCategoryMaster> categories = Map.of(
        "National", ensureCategory("National"),
        "Expat", ensureCategory("Expat"));

    seedMaterials(book, materialType);
    seedEquipment(book, equipmentType);
    seedManpower(book, manpowerType, gradeA, categories);

    GlobalSetting marker = globalSettingRepository.findBySettingKey(VERSION_KEY)
        .orElseGet(GlobalSetting::new);
    marker.setSettingKey(VERSION_KEY);
    marker.setSettingValue(String.valueOf(book.version()));
    marker.setDescription("Applied client rate book version (source: " + book.source() + ")");
    marker.setCategory("SEEDER");
    globalSettingRepository.save(marker);

    log.info("[ClientRateBookSeeder] v{} applied — roles inserted={} existing={} | rate rows "
            + "inserted={} existing={} | held back (no rate in book)={}",
        book.version(), rolesInserted, rolesSkipped, ratesInserted, ratesSkipped,
        heldBackNoRate.size());
    if (!heldBackNoRate.isEmpty()) {
      log.info("[ClientRateBookSeeder] no-rate entries seeded as role-only (enter rates in "
          + "Admin to make them pickable in DPRs): {}", heldBackNoRate);
    }
  }

  private void seedMaterials(ClientRateBook book, ResourceType materialType) {
    int sort = 1000;
    for (ClientRateBook.MaterialRow m : book.materials()) {
      ResourceRole role = ensureRole(m.code(), m.name(), materialType, m.unit(), sort++,
          "Client rate book (M.Code " + m.code() + ")");
      if (m.rate() == null || m.unit() == null) {
        heldBackNoRate.add(m.code());
        continue;
      }
      if (materialVariantRepository.findByRoleIdAndSpecGrade(role.getId(), "STD").isPresent()) {
        ratesSkipped++;
        continue;
      }
      materialVariantRepository.save(MaterialRoleVariant.builder()
          .roleId(role.getId())
          .specGrade("STD")
          .unit(m.unit())
          .rate(new BigDecimal(m.rate()))
          .active(true)
          .build());
      ratesInserted++;
    }
  }

  private void seedEquipment(ClientRateBook book, ResourceType equipmentType) {
    int sort = 2000;
    for (ClientRateBook.EquipmentRow e : book.equipment()) {
      String desc = "Client rate book (P.Code " + e.code() + ")"
          + (e.operatorCode() == null ? "" : "; operator/driver L.Code: " + e.operatorCode());
      ResourceRole role = ensureRole(e.code(), e.name(), equipmentType, "Day", sort++, desc);
      if (e.ratePerDay() == null) {
        heldBackNoRate.add(e.code());
        continue;
      }
      if (equipmentVariantRepository
          .findByRoleIdAndMakeAndModel(role.getId(), "GENERIC", "STD").isPresent()) {
        ratesSkipped++;
        continue;
      }
      equipmentVariantRepository.save(EquipmentRoleVariant.builder()
          .roleId(role.getId())
          .make("GENERIC")
          .model("STD")
          .unit("Day")
          .rate(new BigDecimal(e.ratePerDay()))
          .active(true)
          .build());
      ratesInserted++;
    }
  }

  private void seedManpower(ClientRateBook book, ResourceType manpowerType, GradeMaster gradeA,
                            Map<String, ManpowerCategoryMaster> categories) {
    int sort = 3000;
    for (ClientRateBook.ManpowerRole mp : book.manpower()) {
      // Estimated-salary entries carry a note that replaces the "Client rate book." prefix —
      // the description must not attribute an estimate to the client's rate sheet.
      StringBuilder desc = new StringBuilder(
          mp.note() != null && !mp.note().isBlank() ? mp.note() + " " : "Client rate book. ");
      for (ClientRateBook.ManpowerVariant v : mp.variants()) {
        desc.append(v.category()).append(v.lCode() == null ? "" : " (L.Code " + v.lCode() + ")")
            .append(": ").append(v.salaryPerMonth()).append("/month; ");
      }
      desc.append("daily rate = monthly / ").append(DAYS_PER_MONTH).append(".");
      ResourceRole role = ensureRole(mp.code(), mp.title(), manpowerType, "Day", sort++,
          desc.toString());

      for (ClientRateBook.ManpowerVariant v : mp.variants()) {
        BigDecimal salary = new BigDecimal(v.salaryPerMonth());
        if (salary.signum() <= 0) {
          heldBackNoRate.add(mp.code() + "/" + v.category());
          continue;
        }
        ManpowerCategoryMaster category = categories.get(v.category());
        if (category == null) {
          log.warn("[ClientRateBookSeeder] unknown category '{}' on {} — skipped",
              v.category(), mp.code());
          continue;
        }
        if (manpowerRateRepository.findByRoleIdAndCategoryIdAndGradeId(
            role.getId(), category.getId(), gradeA.getId()).isPresent()) {
          ratesSkipped++;
          continue;
        }
        // The single place the monthly→daily conversion happens (calc log entry 13).
        BigDecimal daily = salary.divide(DAYS_PER_MONTH, 4, RoundingMode.HALF_UP);
        manpowerRateRepository.save(ManpowerRoleRate.builder()
            .roleId(role.getId())
            .categoryId(category.getId())
            .gradeId(gradeA.getId())
            .unit("Day")
            .rate(daily)
            .active(true)
            .build());
        ratesInserted++;
      }
    }
  }

  private ResourceRole ensureRole(String code, String name, ResourceType type,
                                  String productivityUnit, int sortOrder, String description) {
    var existing = roleRepository.findByCode(code);
    if (existing.isPresent()) {
      rolesSkipped++;
      return existing.get();
    }
    rolesInserted++;
    return roleRepository.save(ResourceRole.builder()
        .code(code)
        .name(name)
        .description(description)
        .resourceType(type)
        .productivityUnit(productivityUnit)
        .sortOrder(sortOrder)
        .active(true)
        .build());
  }

  private GradeMaster ensureGradeA() {
    return gradeRepository.findByCode("A").orElseGet(() ->
        gradeRepository.save(GradeMaster.builder()
            .code("A").name("Grade A")
            .description("Default grade (client rate book)")
            .sortOrder(10).active(true)
            .build()));
  }

  private ManpowerCategoryMaster ensureCategory(String name) {
    return categoryRepository.findByName(name).orElseGet(() ->
        categoryRepository.save(ManpowerCategoryMaster.builder()
            .code("MC-" + name.toUpperCase())
            .name(name)
            .description("Client rate book N/E dimension")
            .parentId(null)
            .sortOrder(50)
            .active(true)
            .build()));
  }

  private static int parseIntSafe(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (RuntimeException e) {
      return 0;
    }
  }
}
