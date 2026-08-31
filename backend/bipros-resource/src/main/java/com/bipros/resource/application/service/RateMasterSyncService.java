package com.bipros.resource.application.service;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cascades rate-master {@code unit} + {@code rate} changes onto every {@link Resource} linked
 * to that rate master row. Called by {@code ManpowerRateMasterService},
 * {@code EquipmentRateMasterService}, and {@code MaterialRateMasterService} after a successful
 * update so all linked resources stay in lock-step with their source-of-truth row.
 *
 * <p>Runs in the caller's transaction so the rate-master row and all linked resources commit
 * atomically.
 */
@Service
@Transactional
@Slf4j
public class RateMasterSyncService {

  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentService resourceAssignmentService;

  /**
   * {@code @Lazy} on {@link ResourceAssignmentService} breaks an otherwise-circular dependency:
   * ResourceService → RateMasterSyncService → ResourceAssignmentService → ResourceService.
   */
  public RateMasterSyncService(
      ResourceRepository resourceRepository,
      @Lazy ResourceAssignmentService resourceAssignmentService) {
    this.resourceRepository = resourceRepository;
    this.resourceAssignmentService = resourceAssignmentService;
  }

  /** Returns the number of resources updated. */
  public int syncResourcesForRateMaster(UUID rateMasterId, String unit, BigDecimal rate) {
    if (rateMasterId == null) return 0;
    List<Resource> linked = resourceRepository.findByRateMasterId(rateMasterId);
    if (linked.isEmpty()) return 0;
    for (Resource r : linked) {
      r.setUnit(unit);
      r.setCostPerUnit(rate);
    }
    int totalAssignmentsUpdated = 0;
    for (Resource r : linked) {
      totalAssignmentsUpdated += resourceAssignmentService.recomputeAssignmentsForResource(r.getId());
    }
    log.info("Synced {} resources from rate master {} ({} assignments recomputed)",
        linked.size(), rateMasterId, totalAssignmentsUpdated);
    return linked.size();
  }
}
