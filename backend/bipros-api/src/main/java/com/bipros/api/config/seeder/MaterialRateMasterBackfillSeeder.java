package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.MaterialCategoryMaster;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.rate.MaterialRateMaster;
import com.bipros.resource.domain.repository.MaterialCategoryMasterRepository;
import com.bipros.resource.domain.repository.MaterialRateMasterRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Backfills {@link MaterialRateMaster} from existing Material {@link Resource}s. Legacy data
 * lacks any category breakdown so all rows go under a single "Uncategorized" master category;
 * spec/grade is the resource code (kept distinct, lets admins clean up later by editing each
 * row and assigning a real category).
 *
 * <p>Idempotent: only material resources without a {@code rateMasterId} are touched. The
 * "Uncategorized" master row is created on first run and reused thereafter.
 */
@Slf4j
@Component
@Order(62)
@RequiredArgsConstructor
public class MaterialRateMasterBackfillSeeder implements CommandLineRunner {

  private static final String FALLBACK_CODE = "UNCATEGORIZED";
  private static final String FALLBACK_NAME = "Uncategorized";

  private final ResourceRepository resourceRepository;
  private final MaterialCategoryMasterRepository categoryRepository;
  private final MaterialRateMasterRepository rateRepository;

  @Override
  @Transactional
  public void run(String... args) {
    List<Resource> resources = resourceRepository.findByResourceType_Code("MATERIAL").stream()
        .filter(r -> r.getRateMasterId() == null)
        .toList();
    if (resources.isEmpty()) {
      log.debug("[MaterialRateMasterBackfillSeeder] no material resources need backfill");
      return;
    }

    MaterialCategoryMaster fallback = ensureFallbackCategory();

    int linked = 0;
    for (Resource r : resources) {
      String specGrade = r.getCode() != null ? r.getCode() : (r.getName() != null ? r.getName() : "—");
      MaterialRateMaster rateRow = rateRepository
          .findByCategoryIdAndSpecGrade(fallback.getId(), specGrade)
          .orElseGet(() -> rateRepository.save(MaterialRateMaster.builder()
              .categoryId(fallback.getId())
              .specGrade(specGrade)
              .unit(r.getUnit() != null ? r.getUnit() : "Bag")
              .rate(r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO)
              .active(true)
              .build()));
      r.setRateMasterId(rateRow.getId());
      resourceRepository.save(r);
      linked++;
    }
    log.info("[MaterialRateMasterBackfillSeeder] linked {} resources to placeholder rate rows", linked);
  }

  private MaterialCategoryMaster ensureFallbackCategory() {
    return categoryRepository.findByCode(FALLBACK_CODE).orElseGet(() ->
        categoryRepository.save(MaterialCategoryMaster.builder()
            .code(FALLBACK_CODE)
            .name(FALLBACK_NAME)
            .description("Auto-created bucket for backfilled materials with no real category")
            .sortOrder(999)
            .active(true)
            .build()));
  }
}
