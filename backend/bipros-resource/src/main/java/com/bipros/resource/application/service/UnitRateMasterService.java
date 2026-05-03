package com.bipros.resource.application.service;

import com.bipros.resource.application.dto.UnitRateMasterRow;
import com.bipros.resource.application.dto.UnitRateMasterRow.Source;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
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
 * Flattens the underlying Resource + ResourceRate tables into the single-row shape shown in
 * the workbook's "Unit Rate Master" (Section A of the Daily Cost Report sheet). One line per
 * costed unit with budgeted vs actual rate and derived variance.
 *
 * <p>Category routing now hangs off {@link ResourceType#getCode()} (LABOR / EQUIPMENT /
 * MATERIAL plus admin-defined custom codes), not the deleted {@code CostCategory} enum. All
 * rate data — for every category, including Manpower — is read from {@code ResourceRate} rows
 * because {@code ResourceRole} carries no rate field (rates live on {@link Resource#getCostPerUnit()}
 * for the standard rate, or on {@code ResourceRate} for the budgeted/actual pair the Unit Rate
 * Master needs).
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class UnitRateMasterService {

  private static final int RATIO_SCALE = 6;

  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository resourceRateRepository;

  public List<UnitRateMasterRow> list(String categoryFilter) {
    String filter = categoryFilter == null ? null : categoryFilter.trim().toUpperCase(Locale.ROOT);

    List<Resource> resources = resourceRepository.findAll();
    Map<java.util.UUID, BigDecimal> budgetedByResourceId = new HashMap<>();
    Map<java.util.UUID, BigDecimal> actualByResourceId = new HashMap<>();
    for (Resource r : resources) {
      for (ResourceRate rr : resourceRateRepository.findByResourceId(r.getId())) {
        if (rr.getBudgetedRate() != null) {
          budgetedByResourceId.putIfAbsent(r.getId(), rr.getBudgetedRate());
        } else if ("BUDGETED".equalsIgnoreCase(rr.getRateType())) {
          budgetedByResourceId.putIfAbsent(r.getId(), rr.getPricePerUnit());
        }
        if (rr.getActualRate() != null) {
          actualByResourceId.putIfAbsent(r.getId(), rr.getActualRate());
        } else if ("ACTUAL".equalsIgnoreCase(rr.getRateType())) {
          actualByResourceId.putIfAbsent(r.getId(), rr.getPricePerUnit());
        }
      }
    }

    List<UnitRateMasterRow> rows = new ArrayList<>();
    for (Resource r : resources) {
      String category = categoryLabelFor(r);
      if (filter != null && !filter.equals(category.toUpperCase(Locale.ROOT))) continue;
      rows.add(fromResource(r, category,
          budgetedByResourceId.get(r.getId()),
          actualByResourceId.get(r.getId())));
    }

    rows.sort(Comparator.comparing(UnitRateMasterRow::category).thenComparing(UnitRateMasterRow::description));
    return rows;
  }

  private static String categoryLabelFor(Resource r) {
    String code = r.getResourceType() == null ? "OTHER" : r.getResourceType().getCode();
    if (code == null) return "Other";
    return switch (code.toUpperCase(Locale.ROOT)) {
      case "LABOR" -> "Manpower";
      case "EQUIPMENT" -> "Equipment";
      case "MATERIAL" -> "Material";
      default -> capitalise(code);
    };
  }

  private static String capitalise(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
  }

  private UnitRateMasterRow fromResource(Resource r, String category, BigDecimal budgeted, BigDecimal actual) {
    BigDecimal variance = null;
    BigDecimal variancePct = null;
    if (budgeted != null && actual != null) {
      variance = actual.subtract(budgeted);
      if (budgeted.signum() != 0) {
        variancePct = variance.divide(budgeted, RATIO_SCALE, RoundingMode.HALF_UP);
      }
    }
    String unit = r.getUnit();
    if (unit == null && r.getRole() != null) unit = r.getRole().getProductivityUnit();
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
