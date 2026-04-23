package com.bipros.resource.application.service;

import com.bipros.resource.application.dto.UnitRateMasterRow;
import com.bipros.resource.application.dto.UnitRateMasterRow.Source;
import com.bipros.resource.domain.model.CostCategory;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flattens the underlying Resource + ResourceRate + ResourceRole tables into the single-row
 * shape shown in the workbook's "Unit Rate Master" (Section A of the Daily Cost Report sheet):
 * one line per costed unit with budgeted vs actual rate and derived variance.
 *
 * <p>Data-source routing by category:
 * <ul>
 *   <li><b>Manpower</b>: read {@code ResourceRole} where resourceType = LABOR; use its
 *       {@code budgetedRate} / {@code actualRate} directly.</li>
 *   <li><b>Equipment / Material / Sub-Contract</b>: read {@code Resource} (skip resourceType =
 *       LABOR to avoid duplicating Manpower) + its {@code ResourceRate} children, picking the
 *       most-recent {@code BUDGETED} and {@code ACTUAL} rate rows.</li>
 * </ul>
 *
 * <p>Rows are sorted Manpower → Equipment → Material → Sub-Contract, then alphabetically by
 * description, so the UI table is deterministic.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class UnitRateMasterService {

  private static final int RATIO_SCALE = 6;

  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository resourceRateRepository;
  private final ResourceRoleRepository resourceRoleRepository;

  public List<UnitRateMasterRow> list(String categoryFilter) {
    String filter = categoryFilter == null ? null : categoryFilter.trim().toUpperCase(Locale.ROOT);

    List<UnitRateMasterRow> rows = new ArrayList<>();

    // ───── Manpower (ResourceRole) ─────
    if (filter == null || filter.equals("MANPOWER")) {
      for (Role role : resourceRoleRepository.findByResourceType(ResourceType.LABOR)) {
        rows.add(fromRole(role));
      }
    }

    // ───── Equipment / Material / Sub-Contract (Resource + ResourceRate) ─────
    List<Resource> resources = resourceRepository.findAll();
    // Pre-fetch all rates in one shot so we don't issue N queries.
    Map<java.util.UUID, BigDecimal> budgetedByResourceId = new HashMap<>();
    Map<java.util.UUID, BigDecimal> actualByResourceId = new HashMap<>();
    for (Resource r : resources) {
      for (ResourceRate rr : resourceRateRepository.findByResourceId(r.getId())) {
        if ("BUDGETED".equalsIgnoreCase(rr.getRateType())) {
          budgetedByResourceId.putIfAbsent(r.getId(), rr.getPricePerUnit());
        } else if ("ACTUAL".equalsIgnoreCase(rr.getRateType())) {
          actualByResourceId.putIfAbsent(r.getId(), rr.getPricePerUnit());
        }
      }
    }
    for (Resource r : resources) {
      if (r.getResourceType() == ResourceType.LABOR) {
        continue; // Manpower authoritative = ResourceRole
      }
      CostCategory cc = r.getCostCategory();
      if (cc == null) {
        cc = r.getResourceType() == ResourceType.MATERIAL ? CostCategory.MATERIAL : CostCategory.EQUIPMENT;
      }
      if (filter != null && !filter.equals(cc.name())) continue;
      rows.add(fromResource(r, cc,
          budgetedByResourceId.get(r.getId()),
          actualByResourceId.get(r.getId())));
    }

    rows.sort(Comparator.comparing(UnitRateMasterRow::category).thenComparing(UnitRateMasterRow::description));
    return rows;
  }

  private UnitRateMasterRow fromRole(Role role) {
    BigDecimal budgeted = role.getBudgetedRate();
    BigDecimal actual = role.getActualRate();
    BigDecimal variance = null;
    BigDecimal variancePct = null;
    if (budgeted != null && actual != null) {
      variance = actual.subtract(budgeted);
      if (budgeted.signum() != 0) {
        variancePct = variance.divide(budgeted, RATIO_SCALE, RoundingMode.HALF_UP);
      }
    }
    return new UnitRateMasterRow(
        role.getId(),
        Source.RESOURCE_ROLE,
        "Manpower",
        role.getName(),
        role.getRateUnit(),
        budgeted,
        actual,
        variance,
        variancePct,
        role.getRateRemarks());
  }

  private UnitRateMasterRow fromResource(Resource r, CostCategory cc, BigDecimal budgeted, BigDecimal actual) {
    BigDecimal variance = null;
    BigDecimal variancePct = null;
    if (budgeted != null && actual != null) {
      variance = actual.subtract(budgeted);
      if (budgeted.signum() != 0) {
        variancePct = variance.divide(budgeted, RATIO_SCALE, RoundingMode.HALF_UP);
      }
    }
    String category = switch (cc) {
      case EQUIPMENT -> "Equipment";
      case MATERIAL -> "Material";
      case SUB_CONTRACT -> "Sub-Contract";
    };
    String unit = r.getUnit() != null ? r.getUnit().name() : null;
    return new UnitRateMasterRow(
        r.getId(),
        Source.RESOURCE,
        category,
        r.getName(),
        unit,
        budgeted,
        actual,
        variance,
        variancePct,
        null);
  }
}
