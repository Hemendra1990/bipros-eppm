package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.master.EmploymentTypeMaster;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.master.NationalityMaster;
import com.bipros.resource.domain.model.master.SkillLevelMaster;
import com.bipros.resource.domain.model.master.SkillMaster;
import com.bipros.resource.domain.repository.EmploymentTypeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.NationalityMasterRepository;
import com.bipros.resource.domain.repository.SkillLevelMasterRepository;
import com.bipros.resource.domain.repository.SkillMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Idempotently seeds the Manpower master tables on boot. Each master is seeded only when its
 * table is empty — subsequent boots no-op. Uses construction-PM-relevant defaults appropriate
 * for an Indian construction project context.
 *
 * <p>Existing manpower data continues to work because the seeder creates rows with
 * <em>names</em> matching the legacy enum strings (Skilled / Unskilled / Staff for category;
 * Permanent / Contract / Daily Wage for employment type; Beginner / Intermediate / Expert for
 * skill level). The form looks up dropdown options by name — old data resolves cleanly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManpowerMasterSeeder {

  private final ManpowerCategoryMasterRepository categoryRepo;
  private final EmploymentTypeMasterRepository employmentTypeRepo;
  private final SkillMasterRepository skillRepo;
  private final SkillLevelMasterRepository skillLevelRepo;
  private final NationalityMasterRepository nationalityRepo;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seedAll() {
    seedCategories();
    seedEmploymentTypes();
    seedSkills();
    seedSkillLevels();
    seedNationalities();
  }

  private void seedCategories() {
    if (categoryRepo.count() > 0) {
      log.info("[ManpowerMasterSeeder] manpower_category_master not empty — skipping");
      return;
    }
    int sortOrder = 0;

    // Top-level categories — names match the legacy enum strings (Skilled / Unskilled / Staff)
    // so existing manpower_master rows with those values resolve cleanly to the new master rows.
    UUID skilledId = saveCategory("MC-SKILLED", "Skilled", "Skilled trades", null, sortOrder++);
    UUID unskilledId = saveCategory("MC-UNSKILLED", "Unskilled", "Unskilled labour", null, sortOrder++);
    UUID staffId = saveCategory("MC-STAFF", "Staff", "Site staff and management", null, sortOrder++);

    // Skilled sub-categories
    int s = 0;
    saveCategory("MC-SK-MASON", "Mason", null, skilledId, s++);
    saveCategory("MC-SK-CARP", "Carpenter", null, skilledId, s++);
    saveCategory("MC-SK-WELD", "Welder", null, skilledId, s++);
    saveCategory("MC-SK-PLUMB", "Plumber", null, skilledId, s++);
    saveCategory("MC-SK-ELEC", "Electrician", null, skilledId, s++);
    saveCategory("MC-SK-BARB", "Bar Bender", null, skilledId, s++);
    saveCategory("MC-SK-PAINT", "Painter", null, skilledId, s++);
    saveCategory("MC-SK-TILE", "Tile Layer", null, skilledId, s++);
    saveCategory("MC-SK-EQOP", "Equipment Operator", null, skilledId, s++);
    saveCategory("MC-SK-CRAOP", "Crane Operator", null, skilledId, s++);
    saveCategory("MC-SK-FIT", "Fitter", null, skilledId, s++);
    saveCategory("MC-SK-STFIX", "Steel Fixer", null, skilledId, s++);

    // Unskilled sub-categories
    int u = 0;
    saveCategory("MC-UN-HLP", "Helper", null, unskilledId, u++);
    saveCategory("MC-UN-LOAD", "Loader", null, unskilledId, u++);
    saveCategory("MC-UN-CLN", "Cleaner", null, unskilledId, u++);
    saveCategory("MC-UN-WCH", "Watchman", null, unskilledId, u++);
    saveCategory("MC-UN-BLDR", "Beldar", null, unskilledId, u++);

    // Staff sub-categories
    int t = 0;
    saveCategory("MC-ST-SE", "Site Engineer", null, staffId, t++);
    saveCategory("MC-ST-FORE", "Foreman", null, staffId, t++);
    saveCategory("MC-ST-SUP", "Supervisor", null, staffId, t++);
    saveCategory("MC-ST-PM", "Project Manager", null, staffId, t++);
    saveCategory("MC-ST-SAFE", "Safety Officer", null, staffId, t++);
    saveCategory("MC-ST-QE", "Quality Engineer", null, staffId, t++);
    saveCategory("MC-ST-SURV", "Surveyor", null, staffId, t++);
    saveCategory("MC-ST-STORE", "Storekeeper", null, staffId, t++);
    saveCategory("MC-ST-ACC", "Accountant", null, staffId, t++);
    saveCategory("MC-ST-JE", "Junior Engineer", null, staffId, t++);
    saveCategory("MC-ST-SIC", "Site-In-Charge", null, staffId, t++);

    log.info("[ManpowerMasterSeeder] seeded {} category rows (top-level + sub-categories)",
        categoryRepo.count());
  }

  private UUID saveCategory(String code, String name, String desc, UUID parentId, int sort) {
    return categoryRepo.save(ManpowerCategoryMaster.builder()
        .code(code)
        .name(name)
        .description(desc)
        .parentId(parentId)
        .sortOrder(sort)
        .active(true)
        .build()).getId();
  }

  private void seedEmploymentTypes() {
    if (employmentTypeRepo.count() > 0) {
      log.info("[ManpowerMasterSeeder] employment_type_master not empty — skipping");
      return;
    }
    // Names match legacy enum strings so existing data continues to resolve.
    List<String[]> rows = List.of(
        new String[]{"ET-PERM", "Permanent"},
        new String[]{"ET-CONT", "Contract"},
        new String[]{"ET-DAILY", "Daily Wage"},
        new String[]{"ET-SUBC", "Sub-Contract"},
        new String[]{"ET-CASUAL", "Casual"});
    int sort = 0;
    for (String[] r : rows) {
      employmentTypeRepo.save(EmploymentTypeMaster.builder()
          .code(r[0]).name(r[1]).sortOrder(sort++).active(true).build());
    }
    log.info("[ManpowerMasterSeeder] seeded {} employment_type rows", rows.size());
  }

  private void seedSkills() {
    if (skillRepo.count() > 0) {
      log.info("[ManpowerMasterSeeder] skill_master not empty — skipping");
      return;
    }
    List<String[]> rows = List.of(
        new String[]{"SK-MASN", "Masonry"},
        new String[]{"SK-CARP", "Carpentry"},
        new String[]{"SK-WELD", "Welding"},
        new String[]{"SK-PLUM", "Plumbing"},
        new String[]{"SK-ELEC", "Electrical Wiring"},
        new String[]{"SK-BARB", "Bar Bending"},
        new String[]{"SK-PAINT", "Painting"},
        new String[]{"SK-TILE", "Tiling"},
        new String[]{"SK-CONC", "Concreting"},
        new String[]{"SK-PLAS", "Plastering"},
        new String[]{"SK-EXOP", "Excavator Operation"},
        new String[]{"SK-CROP", "Crane Operation"},
        new String[]{"SK-SURV", "Surveying"},
        new String[]{"SK-QC", "Quality Control"},
        new String[]{"SK-SAFE", "Safety Inspection"},
        new String[]{"SK-STFIX", "Steel Fixing"},
        new String[]{"SK-SHUT", "Shuttering"},
        new String[]{"SK-SCAF", "Scaffolding"});
    int sort = 0;
    for (String[] r : rows) {
      skillRepo.save(SkillMaster.builder()
          .code(r[0]).name(r[1]).sortOrder(sort++).active(true).build());
    }
    log.info("[ManpowerMasterSeeder] seeded {} skill rows", rows.size());
  }

  private void seedSkillLevels() {
    if (skillLevelRepo.count() > 0) {
      log.info("[ManpowerMasterSeeder] skill_level_master not empty — skipping");
      return;
    }
    // Names match legacy enum strings (Beginner / Intermediate / Expert) so existing data resolves.
    List<String[]> rows = List.of(
        new String[]{"SL-APP", "Apprentice"},
        new String[]{"SL-BEG", "Beginner"},
        new String[]{"SL-INT", "Intermediate"},
        new String[]{"SL-EXP", "Expert"},
        new String[]{"SL-MAS", "Master"});
    int sort = 0;
    for (String[] r : rows) {
      skillLevelRepo.save(SkillLevelMaster.builder()
          .code(r[0]).name(r[1]).sortOrder(sort++).active(true).build());
    }
    log.info("[ManpowerMasterSeeder] seeded {} skill_level rows", rows.size());
  }

  private void seedNationalities() {
    if (nationalityRepo.count() > 0) {
      log.info("[ManpowerMasterSeeder] nationality_master not empty — skipping");
      return;
    }
    // Common nationalities for an Indian / South-Asian / Gulf construction labour pool.
    List<String[]> rows = List.of(
        new String[]{"NAT-IND", "Indian"},
        new String[]{"NAT-NPL", "Nepali"},
        new String[]{"NAT-BGD", "Bangladeshi"},
        new String[]{"NAT-LKA", "Sri Lankan"},
        new String[]{"NAT-BTN", "Bhutanese"},
        new String[]{"NAT-PAK", "Pakistani"},
        new String[]{"NAT-AFG", "Afghan"},
        new String[]{"NAT-PHL", "Filipino"},
        new String[]{"NAT-IDN", "Indonesian"},
        new String[]{"NAT-VNM", "Vietnamese"},
        new String[]{"NAT-CHN", "Chinese"},
        new String[]{"NAT-MYS", "Malaysian"},
        new String[]{"NAT-THA", "Thai"},
        new String[]{"NAT-MMR", "Burmese"},
        new String[]{"NAT-ARE", "Emirati"},
        new String[]{"NAT-SAU", "Saudi"},
        new String[]{"NAT-OMN", "Omani"},
        new String[]{"NAT-QAT", "Qatari"},
        new String[]{"NAT-KWT", "Kuwaiti"},
        new String[]{"NAT-BHR", "Bahraini"},
        new String[]{"NAT-USA", "American"},
        new String[]{"NAT-GBR", "British"},
        new String[]{"NAT-CAN", "Canadian"},
        new String[]{"NAT-AUS", "Australian"},
        new String[]{"NAT-DEU", "German"},
        new String[]{"NAT-FRA", "French"});
    int sort = 0;
    for (String[] r : rows) {
      nationalityRepo.save(NationalityMaster.builder()
          .code(r[0]).name(r[1]).sortOrder(sort++).active(true).build());
    }
    log.info("[ManpowerMasterSeeder] seeded {} nationality rows", rows.size());
  }
}
