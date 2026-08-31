package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.rate.EquipmentRateMaster;
import com.bipros.resource.domain.repository.EquipmentRateMasterRepository;
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
 * Backfills {@link EquipmentRateMaster} from existing Equipment {@link Resource}s. Because
 * legacy data has no Make/Model, each resource gets a placeholder row with Make = "Unknown",
 * Model = the resource code (kept distinct, lets admins clean up later by editing each row).
 *
 * <p>Idempotent: only equipment resources without a {@code rateMasterId} are touched. Each
 * placeholder row is unique by (equipmentName, make, model) so re-runs become no-ops.
 */
@Slf4j
@Component
@Order(61)
@RequiredArgsConstructor
public class EquipmentRateMasterBackfillSeeder implements CommandLineRunner {

  private static final String UNKNOWN_MAKE = "Unknown";

  private final ResourceRepository resourceRepository;
  private final EquipmentRateMasterRepository rateRepository;

  @Override
  @Transactional
  public void run(String... args) {
    List<Resource> resources = resourceRepository.findByResourceType_Code("EQUIPMENT").stream()
        .filter(r -> r.getRateMasterId() == null)
        .toList();
    if (resources.isEmpty()) {
      log.debug("[EquipmentRateMasterBackfillSeeder] no equipment resources need backfill");
      return;
    }

    int linked = 0;
    for (Resource r : resources) {
      String name = r.getName() != null ? r.getName() : "Equipment";
      String model = r.getCode() != null ? r.getCode() : "—";
      EquipmentRateMaster rateRow = rateRepository
          .findByEquipmentNameAndMakeAndModel(name, UNKNOWN_MAKE, model)
          .orElseGet(() -> rateRepository.save(EquipmentRateMaster.builder()
              .equipmentName(name)
              .make(UNKNOWN_MAKE)
              .model(model)
              .unit(r.getUnit() != null ? r.getUnit() : "Hour")
              .rate(r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO)
              .active(true)
              .build()));
      r.setRateMasterId(rateRow.getId());
      resourceRepository.save(r);
      linked++;
    }
    log.info("[EquipmentRateMasterBackfillSeeder] linked {} resources to placeholder rate rows", linked);
  }
}
