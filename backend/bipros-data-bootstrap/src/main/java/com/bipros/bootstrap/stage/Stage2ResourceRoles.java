package com.bipros.bootstrap.stage;

import com.bipros.bootstrap.BootstrapApplication;
import com.bipros.bootstrap.Stage;
import com.bipros.bootstrap.input.ParsedDatasetStore;
import com.bipros.bootstrap.model.ParsedDataset;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 2 — populate {@code resource_roles} plus their owned variant rate
 * tables ({@code manpower_role_rates}, {@code equipment_role_variants},
 * {@code material_role_variants}) from {@link ParsedDataset}.
 *
 * <p>Idempotent: every write is keyed on the entity's unique constraint and
 * either inserts a new row, updates the rate when it has changed, or no-ops.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Stage2ResourceRoles implements Stage {

    private final ParsedDatasetStore store;
    private final ResourceRoleRepository resourceRoleRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ManpowerRoleRateRepository manpowerRoleRateRepository;
    private final EquipmentRoleVariantRepository equipmentRoleVariantRepository;
    private final MaterialRoleVariantRepository materialRoleVariantRepository;
    private final ManpowerCategoryMasterRepository manpowerCategoryRepository;
    private final GradeMasterRepository gradeMasterRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public static void main(String[] args) {
        BootstrapApplication.runStage(Stage2ResourceRoles.class, args);
    }

    @Override
    @Transactional
    public void run() {
        // Earlier Stage 2 runs (before the name-aware lookup fix) created shadow categories
        // like SKILLED / UNSKILLED / STAFF alongside the canonical MC-SKILLED / MC-UNSKILLED
        // already seeded by ManpowerMasterSeeder. Same name → duplicate entries in dropdowns.
        // Merge them first, then proceed.
        cleanupDuplicateCategoryMasters();
        cleanupDuplicateGradeMasters();

        ParsedDataset d = store.load();
        log.info("Stage 2 — processing {} manpower / {} equipment / {} material variants",
                d.manpowerVariants.size(), d.equipmentVariants.size(), d.materialVariants.size());

        Map<String, ResourceType> typesByCode = loadResourceTypes();

        Counters c = new Counters();
        Map<String, ResourceRole> rolesByCode = new HashMap<>();
        Map<String, ManpowerCategoryMaster> categoryCache = new HashMap<>();
        Map<String, GradeMaster> gradeCache = new HashMap<>();

        // ---- Manpower ----
        int sortSeed = nextSortOrder();
        for (ParsedDataset.ManpowerVariant v : d.manpowerVariants) {
            ResourceRole role = upsertRole(v.roleCode, v.roleName, typesByCode.get("MANPOWER"),
                    rolesByCode, c, sortSeed++);
            if (role == null) continue;

            // The Khasab source often doesn't specify category / grade per role — defaults
            // are applied so every role still gets at least one rate row (UI requires this).
            String catCode = (v.categoryCode == null || v.categoryCode.isBlank())
                    ? "Skilled" : v.categoryCode;
            String gradeCode = (v.gradeCode == null || v.gradeCode.isBlank())
                    ? "A" : v.gradeCode;

            ManpowerCategoryMaster cat = resolveCategory(catCode, categoryCache);
            if (cat == null) {
                log.warn("Skipping manpower variant for role {} — could not resolve or create category '{}'",
                        v.roleCode, catCode);
                continue;
            }
            GradeMaster grade = resolveGrade(gradeCode, gradeCache);
            if (grade == null) {
                log.warn("Skipping manpower variant role={} cat={} — could not resolve or create grade '{}'",
                        v.roleCode, catCode, gradeCode);
                continue;
            }

            upsertManpowerRate(role.getId(), cat.getId(), grade.getId(),
                    v.unit, v.rate, c);
        }

        // ---- Equipment ----
        sortSeed = nextSortOrder();
        for (ParsedDataset.EquipmentVariant v : d.equipmentVariants) {
            ResourceRole role = upsertRole(v.roleCode, v.roleName, typesByCode.get("EQUIPMENT"),
                    rolesByCode, c, sortSeed++);
            if (role == null) continue;
            upsertEquipmentVariant(role.getId(), v.make, v.model, v.unit, v.rate,
                    v.standardOutputPerDay, c);
        }

        // ---- Material ----
        sortSeed = nextSortOrder();
        for (ParsedDataset.MaterialVariant v : d.materialVariants) {
            ResourceRole role = upsertRole(v.roleCode, v.roleName, typesByCode.get("MATERIAL"),
                    rolesByCode, c, sortSeed++);
            if (role == null) continue;
            upsertMaterialVariant(role.getId(), v.specGrade, v.unit, v.rate, c);
        }

        log.info("Stage 2 done — roles: {} inserted, {} updated, {} unchanged. "
                + "Variants: manpower {}/{} insert/update, equipment {}/{} insert/update, "
                + "material {}/{} insert/update.",
                c.rolesInserted, c.rolesUpdated, c.rolesUnchanged,
                c.manpowerInserted, c.manpowerUpdated,
                c.equipmentInserted, c.equipmentUpdated,
                c.materialInserted, c.materialUpdated);
    }

    // ─────────────────────────── ResourceType lookup ───────────────────────────

    private Map<String, ResourceType> loadResourceTypes() {
        Map<String, ResourceType> map = new HashMap<>();
        for (String code : List.of("MANPOWER", "EQUIPMENT", "MATERIAL")) {
            ResourceType rt = resourceTypeRepository.findByCode(code).orElse(null);
            if (rt == null) {
                throw new IllegalStateException("Resource type '" + code + "' not found. "
                        + "Run ResourceTypeSeeder before Stage2ResourceRoles.");
            }
            map.put(code, rt);
        }
        return map;
    }

    private int nextSortOrder() {
        // Roles created here go after any pre-seeded rows. Start at 1000 so we don't collide
        // with hand-curated seeders that use < 1000.
        return 1000;
    }

    // ─────────────────────────── Role upsert ───────────────────────────

    private ResourceRole upsertRole(String code, String name, ResourceType type,
                                    Map<String, ResourceRole> cache, Counters c, int sortOrder) {
        if (code == null || code.isBlank()) {
            log.warn("Skipping role with blank code (name='{}')", name);
            return null;
        }
        if (type == null) {
            log.warn("Skipping role {} — resource type not resolved", code);
            return null;
        }
        ResourceRole cached = cache.get(code);
        if (cached != null) return cached;

        ResourceRole existing = resourceRoleRepository.findByCode(code).orElse(null);
        if (existing == null) {
            ResourceRole role = ResourceRole.builder()
                    .code(code)
                    .name(name != null && !name.isBlank() ? name : code)
                    .resourceType(type)
                    .sortOrder(sortOrder)
                    .active(true)
                    .build();
            role = resourceRoleRepository.save(role);
            c.rolesInserted++;
            cache.put(code, role);
            return role;
        }

        boolean dirty = false;
        if (name != null && !name.isBlank() && !name.equals(existing.getName())) {
            existing.setName(name);
            dirty = true;
        }
        if (existing.getResourceType() == null
                || !type.getId().equals(existing.getResourceType().getId())) {
            existing.setResourceType(type);
            dirty = true;
        }
        if (!Boolean.TRUE.equals(existing.getActive())) {
            existing.setActive(true);
            dirty = true;
        }
        if (dirty) {
            existing = resourceRoleRepository.save(existing);
            c.rolesUpdated++;
        } else {
            c.rolesUnchanged++;
        }
        cache.put(code, existing);
        return existing;
    }

    // ─────────────────────────── Master resolution ───────────────────────────

    private ManpowerCategoryMaster resolveCategory(String rawCode,
                                                   Map<String, ManpowerCategoryMaster> cache) {
        if (rawCode == null || rawCode.isBlank()) return null;
        String input = rawCode.trim();
        String code = input.toUpperCase();
        ManpowerCategoryMaster cached = cache.get(code);
        if (cached != null) return cached;

        // Prefer NAME match. The runtime app seeds canonical categories with codes like
        // "MC-SKILLED" but name "Skilled". The fixture carries the human-readable name
        // ("Skilled"), so matching by name reuses the seeded row instead of creating a
        // duplicate with code "SKILLED".
        List<ManpowerCategoryMaster> all = manpowerCategoryRepository.findAll();
        ManpowerCategoryMaster existing = null;
        for (ManpowerCategoryMaster m : all) {
            if (m.getName() != null && m.getName().equalsIgnoreCase(input)) {
                existing = m;
                break;
            }
        }
        if (existing == null) {
            for (ManpowerCategoryMaster m : all) {
                if (m.getCode() != null && m.getCode().equalsIgnoreCase(code)) {
                    existing = m;
                    break;
                }
            }
        }
        if (existing == null) {
            existing = manpowerCategoryRepository.save(ManpowerCategoryMaster.builder()
                    .code(code)
                    .name(toTitle(code))
                    .sortOrder(0)
                    .active(true)
                    .build());
            log.info("Created ManpowerCategoryMaster '{}'", code);
        }
        cache.put(code, existing);
        return existing;
    }

    private GradeMaster resolveGrade(String rawCode, Map<String, GradeMaster> cache) {
        if (rawCode == null || rawCode.isBlank()) return null;
        String code = rawCode.trim().toUpperCase();
        GradeMaster cached = cache.get(code);
        if (cached != null) return cached;

        GradeMaster existing = gradeMasterRepository.findByCode(code).orElse(null);
        if (existing == null) {
            for (GradeMaster g : gradeMasterRepository.findAll()) {
                if (g.getCode() != null && g.getCode().equalsIgnoreCase(code)) {
                    existing = g;
                    break;
                }
            }
        }
        if (existing == null) {
            existing = gradeMasterRepository.save(GradeMaster.builder()
                    .code(code)
                    .name("Grade " + code)
                    .sortOrder(0)
                    .active(true)
                    .build());
            log.info("Created GradeMaster '{}'", code);
        }
        cache.put(code, existing);
        return existing;
    }

    /**
     * Merge duplicate ManpowerCategoryMaster rows with the same name. Prefer the row whose
     * code starts with {@code MC-} (canonical from ManpowerMasterSeeder) — that's the row
     * the rest of the app's seed data already references. Remap any FK references from
     * the duplicate rows to the kept one before deleting them.
     */
    private void cleanupDuplicateCategoryMasters() {
        List<ManpowerCategoryMaster> all = manpowerCategoryRepository.findAll();
        java.util.Map<String, java.util.List<ManpowerCategoryMaster>> byName = new java.util.HashMap<>();
        for (ManpowerCategoryMaster m : all) {
            if (m.getName() == null) continue;
            byName.computeIfAbsent(m.getName().trim().toLowerCase(), k -> new java.util.ArrayList<>()).add(m);
        }
        int merged = 0;
        for (var entry : byName.entrySet()) {
            java.util.List<ManpowerCategoryMaster> rows = entry.getValue();
            if (rows.size() < 2) continue;
            rows.sort((a, b) -> {
                boolean aMC = a.getCode() != null && a.getCode().startsWith("MC-");
                boolean bMC = b.getCode() != null && b.getCode().startsWith("MC-");
                if (aMC && !bMC) return -1;
                if (!aMC && bMC) return 1;
                String ac = a.getCode() == null ? "" : a.getCode();
                String bc = b.getCode() == null ? "" : b.getCode();
                return ac.compareTo(bc);
            });
            ManpowerCategoryMaster keep = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                ManpowerCategoryMaster dup = rows.get(i);
                em.createNativeQuery("UPDATE resource.manpower_role_rates SET category_id = :keep WHERE category_id = :dup")
                        .setParameter("keep", keep.getId())
                        .setParameter("dup", dup.getId())
                        .executeUpdate();
                manpowerCategoryRepository.delete(dup);
                merged++;
                log.info("Merged duplicate ManpowerCategoryMaster '{}' (code={}) → '{}' (code={})",
                        dup.getName(), dup.getCode(), keep.getName(), keep.getCode());
            }
        }
        if (merged > 0) log.info("Cleanup: merged {} duplicate ManpowerCategoryMaster row(s).", merged);
    }

    /** Same idea for GradeMaster: prefer code with shortest length (canonical "A"/"B"/"C") and lowest code alphabetically. */
    private void cleanupDuplicateGradeMasters() {
        List<GradeMaster> all = gradeMasterRepository.findAll();
        java.util.Map<String, java.util.List<GradeMaster>> byName = new java.util.HashMap<>();
        for (GradeMaster g : all) {
            if (g.getName() == null) continue;
            byName.computeIfAbsent(g.getName().trim().toLowerCase(), k -> new java.util.ArrayList<>()).add(g);
        }
        int merged = 0;
        for (var entry : byName.entrySet()) {
            java.util.List<GradeMaster> rows = entry.getValue();
            if (rows.size() < 2) continue;
            rows.sort((a, b) -> {
                String ac = a.getCode() == null ? "" : a.getCode();
                String bc = b.getCode() == null ? "" : b.getCode();
                if (ac.length() != bc.length()) return ac.length() - bc.length();
                return ac.compareTo(bc);
            });
            GradeMaster keep = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                GradeMaster dup = rows.get(i);
                em.createNativeQuery("UPDATE resource.manpower_role_rates SET grade_id = :keep WHERE grade_id = :dup")
                        .setParameter("keep", keep.getId())
                        .setParameter("dup", dup.getId())
                        .executeUpdate();
                gradeMasterRepository.delete(dup);
                merged++;
                log.info("Merged duplicate GradeMaster '{}' (code={}) → '{}' (code={})",
                        dup.getName(), dup.getCode(), keep.getName(), keep.getCode());
            }
        }
        if (merged > 0) log.info("Cleanup: merged {} duplicate GradeMaster row(s).", merged);
    }

    private static String toTitle(String code) {
        // "MC-SKILLED" → "Mc Skilled"; good enough for an auto-created master row.
        String s = code.replace('_', ' ').replace('-', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (char ch : s.toCharArray()) {
            if (Character.isWhitespace(ch)) { sb.append(ch); cap = true; }
            else if (cap) { sb.append(Character.toUpperCase(ch)); cap = false; }
            else sb.append(ch);
        }
        return sb.toString();
    }

    // ─────────────────────────── Variant upserts ───────────────────────────

    private void upsertManpowerRate(java.util.UUID roleId, java.util.UUID categoryId,
                                    java.util.UUID gradeId, String unit, BigDecimal rate,
                                    Counters c) {
        String safeUnit = (unit == null || unit.isBlank()) ? "Day" : unit;
        BigDecimal safeRate = rate != null ? rate : BigDecimal.ZERO;
        ManpowerRoleRate existing = manpowerRoleRateRepository
                .findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId).orElse(null);
        if (existing == null) {
            manpowerRoleRateRepository.save(ManpowerRoleRate.builder()
                    .roleId(roleId)
                    .categoryId(categoryId)
                    .gradeId(gradeId)
                    .unit(safeUnit)
                    .rate(safeRate)
                    .active(true)
                    .build());
            c.manpowerInserted++;
            return;
        }
        boolean dirty = false;
        if (existing.getRate() == null || existing.getRate().compareTo(safeRate) != 0) {
            existing.setRate(safeRate);
            dirty = true;
        }
        if (!safeUnit.equals(existing.getUnit())) {
            existing.setUnit(safeUnit);
            dirty = true;
        }
        if (!Boolean.TRUE.equals(existing.getActive())) {
            existing.setActive(true);
            dirty = true;
        }
        if (dirty) {
            manpowerRoleRateRepository.save(existing);
            c.manpowerUpdated++;
        }
    }

    private void upsertEquipmentVariant(java.util.UUID roleId, String make, String model,
                                        String unit, BigDecimal rate,
                                        BigDecimal standardOutputPerDay, Counters c) {
        String safeMake = (make == null || make.isBlank()) ? "GENERIC" : make.trim();
        String safeModel = (model == null || model.isBlank()) ? "STD" : model.trim();
        String safeUnit = (unit == null || unit.isBlank()) ? "Day" : unit;
        BigDecimal safeRate = rate != null ? rate : BigDecimal.ZERO;

        EquipmentRoleVariant existing = equipmentRoleVariantRepository
                .findByRoleIdAndMakeAndModel(roleId, safeMake, safeModel).orElse(null);
        if (existing == null) {
            equipmentRoleVariantRepository.save(EquipmentRoleVariant.builder()
                    .roleId(roleId)
                    .make(safeMake)
                    .model(safeModel)
                    .unit(safeUnit)
                    .rate(safeRate)
                    .standardOutputPerDay(standardOutputPerDay)
                    .active(true)
                    .build());
            c.equipmentInserted++;
            return;
        }
        boolean dirty = false;
        if (existing.getRate() == null || existing.getRate().compareTo(safeRate) != 0) {
            existing.setRate(safeRate);
            dirty = true;
        }
        if (!safeUnit.equals(existing.getUnit())) {
            existing.setUnit(safeUnit);
            dirty = true;
        }
        if (standardOutputPerDay != null
                && (existing.getStandardOutputPerDay() == null
                    || existing.getStandardOutputPerDay().compareTo(standardOutputPerDay) != 0)) {
            existing.setStandardOutputPerDay(standardOutputPerDay);
            dirty = true;
        }
        if (!Boolean.TRUE.equals(existing.getActive())) {
            existing.setActive(true);
            dirty = true;
        }
        if (dirty) {
            equipmentRoleVariantRepository.save(existing);
            c.equipmentUpdated++;
        }
    }

    private void upsertMaterialVariant(java.util.UUID roleId, String specGrade, String unit,
                                       BigDecimal rate, Counters c) {
        String safeSpec = (specGrade == null || specGrade.isBlank()) ? "STD" : specGrade.trim();
        String safeUnit = (unit == null || unit.isBlank()) ? "Unit" : unit;
        BigDecimal safeRate = rate != null ? rate : BigDecimal.ZERO;

        MaterialRoleVariant existing = materialRoleVariantRepository
                .findByRoleIdAndSpecGrade(roleId, safeSpec).orElse(null);
        if (existing == null) {
            materialRoleVariantRepository.save(MaterialRoleVariant.builder()
                    .roleId(roleId)
                    .specGrade(safeSpec)
                    .unit(safeUnit)
                    .rate(safeRate)
                    .active(true)
                    .build());
            c.materialInserted++;
            return;
        }
        boolean dirty = false;
        if (existing.getRate() == null || existing.getRate().compareTo(safeRate) != 0) {
            existing.setRate(safeRate);
            dirty = true;
        }
        if (!safeUnit.equals(existing.getUnit())) {
            existing.setUnit(safeUnit);
            dirty = true;
        }
        if (!Boolean.TRUE.equals(existing.getActive())) {
            existing.setActive(true);
            dirty = true;
        }
        if (dirty) {
            materialRoleVariantRepository.save(existing);
            c.materialUpdated++;
        }
    }

    // ─────────────────────────── Counters ───────────────────────────

    private static final class Counters {
        int rolesInserted;
        int rolesUpdated;
        int rolesUnchanged;
        int manpowerInserted;
        int manpowerUpdated;
        int equipmentInserted;
        int equipmentUpdated;
        int materialInserted;
        int materialUpdated;
    }
}
