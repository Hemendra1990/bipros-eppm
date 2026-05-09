package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.manpower.ManpowerMaster;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.rate.ManpowerRateMaster;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
import com.bipros.resource.domain.repository.ManpowerRateMasterRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Backfills {@link ManpowerRateMaster} from existing Manpower {@link Resource}s and links each
 * resource via {@code rateMasterId}. Distinct (role, category, sub-category) combinations from
 * {@code manpower_master} become rate-master rows with Grade defaulted to {@code A} and rate
 * copied from the resource's existing {@code costPerUnit}.
 *
 * <p>Resources that lack a category/sub-category match (e.g. legacy data with no master row)
 * are skipped — admins can link them manually later.
 *
 * <p>Idempotent: only resources without a {@code rateMasterId} are touched. A default Grade A
 * row is created if missing.
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class ManpowerRateMasterBackfillSeeder implements CommandLineRunner {

  private final ResourceRepository resourceRepository;
  private final ManpowerMasterRepository manpowerMasterRepository;
  private final ManpowerCategoryMasterRepository categoryRepository;
  private final GradeMasterRepository gradeRepository;
  private final ManpowerRateMasterRepository rateRepository;

  @Override
  @Transactional
  public void run(String... args) {
    List<Resource> resources = resourceRepository.findByResourceType_Code("LABOR").stream()
        .filter(r -> r.getRateMasterId() == null)
        .toList();
    if (resources.isEmpty()) {
      log.debug("[ManpowerRateMasterBackfillSeeder] no manpower resources need backfill");
      return;
    }

    GradeMaster gradeA = ensureGradeA();
    int linked = 0;
    int skipped = 0;

    for (Resource r : resources) {
      Optional<ManpowerMaster> mm = manpowerMasterRepository.findById(r.getId());
      if (mm.isEmpty() || r.getRole() == null) { skipped++; continue; }

      ManpowerCategoryMaster cat = resolveTopCategory(mm.get().getCategory());
      if (cat == null) { skipped++; continue; }

      UUID roleId = r.getRole().getId();
      ManpowerRateMaster rateRow = rateRepository
          .findByRoleIdAndCategoryIdAndGradeId(
              roleId, cat.getId(), gradeA.getId())
          .orElseGet(() -> rateRepository.save(ManpowerRateMaster.builder()
              .roleId(roleId)
              .categoryId(cat.getId())
              .gradeId(gradeA.getId())
              .unit(r.getUnit() != null ? r.getUnit() : "Day")
              .rate(r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO)
              .active(true)
              .build()));

      r.setRateMasterId(rateRow.getId());
      resourceRepository.save(r);
      linked++;
    }

    log.info("[ManpowerRateMasterBackfillSeeder] linked {} resources, skipped {} (no category match)",
        linked, skipped);
  }

  private GradeMaster ensureGradeA() {
    return gradeRepository.findByCode("A").orElseGet(() ->
        gradeRepository.save(GradeMaster.builder()
            .code("A").name("Grade A").sortOrder(10).active(true).build()));
  }

  private ManpowerCategoryMaster resolveTopCategory(String name) {
    if (name == null || name.isBlank()) return null;
    return categoryRepository.findByName(name.trim())
        .filter(c -> c.getParentId() == null)
        .orElse(null);
  }
}
