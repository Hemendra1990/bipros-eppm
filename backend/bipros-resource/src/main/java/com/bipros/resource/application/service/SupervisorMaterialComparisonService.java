package com.bipros.resource.application.service;

import com.bipros.resource.application.dto.SupervisorMaterialRow;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bipros.resource.application.service.MaterialBalanceService.norm;

/**
 * Supervisor-wise issued material comparison (AI Agent sheet, Material row — MAT-04/MAT-05).
 *
 * <p>Issued = store issue slips grouped by {@code issued_to_user_id} × catalogue material name.
 * Reported = APPROVED DPR material lines grouped by the parent DPR's supervisor
 * (id-when-present-else-normalized-name, the platform convention) × normalized material name.
 * Both cumulative to {@code asOf}; a strict monthly window would false-flag material issued at
 * month end and consumed early the next month. Variance value = qty × (avg DPR unit rate for the
 * material, else latest GRN rate) — a FLAG only. Automatic DBS costing is deferred until the
 * client answers open question Q20 (tolerance + authority).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorMaterialComparisonService {

  private final MaterialIssueRepository issueRepository;
  private final GoodsReceiptNoteRepository grnRepository;
  private final MaterialBalanceService balanceService;

  @PersistenceContext
  private EntityManager entityManager;

  /**
   * @param asOf cumulative cut-off (nullable → today)
   * @param windowFrom start of the movement window (nullable → no window columns, zeros)
   */
  @Transactional(readOnly = true)
  public List<SupervisorMaterialRow> compare(UUID projectId, LocalDate asOf, LocalDate windowFrom) {
    LocalDate end = asOf != null ? asOf : LocalDate.now();

    List<MaterialIssue> issues = issueRepository.findByProjectId(projectId);
    if (issues.isEmpty()) return List.of();

    Set<UUID> refIds = new HashSet<>();
    List<GoodsReceiptNote> grns = grnRepository.findByProjectIdOrderByReceivedDateDesc(projectId);
    for (MaterialIssue i : issues) refIds.add(i.getMaterialId());
    for (GoodsReceiptNote g : grns) refIds.add(g.getMaterialId());
    refIds.remove(null);
    Map<UUID, MaterialBalanceService.MaterialRef> refs =
        balanceService.resolveMaterialRefs(projectId, refIds);

    // ---- issued side: (userId × materialKey) ----
    Map<String, Cell> cells = new LinkedHashMap<>();
    Set<UUID> issuedUserIds = new HashSet<>();
    Set<String> issuedMaterialKeys = new HashSet<>();
    for (MaterialIssue i : issues) {
      if (i.getIssuedToUserId() == null || i.getIssueDate() == null || i.getIssueDate().isAfter(end)) {
        continue;
      }
      MaterialBalanceService.MaterialRef m = refs.get(i.getMaterialId());
      String materialName = m != null ? m.name() : "(unknown material)";
      String unit = m != null ? m.unit() : null;
      String matKey = norm(materialName);
      issuedUserIds.add(i.getIssuedToUserId());
      issuedMaterialKeys.add(matKey);
      Cell c = cells.computeIfAbsent("id:" + i.getIssuedToUserId() + "|" + matKey,
          k -> new Cell("id:" + i.getIssuedToUserId(), materialName, unit));
      c.issuedToDate = c.issuedToDate.add(nz(i.getQuantity()));
      c.wastageQty = c.wastageQty.add(nz(i.getWastageQuantity()));
      if (windowFrom != null && !i.getIssueDate().isBefore(windowFrom)) {
        c.issuedWindow = c.issuedWindow.add(nz(i.getQuantity()));
      }
    }
    if (cells.isEmpty()) return List.of();

    Map<UUID, String> userNames = resolveUserNames(issuedUserIds);

    // Bridge: DPR rows keyed by name match an issued user whose display name normalizes equal.
    Map<String, String> nameKeyToIdKey = new HashMap<>();
    for (Map.Entry<UUID, String> e : userNames.entrySet()) {
      nameKeyToIdKey.put("nm:" + norm(e.getValue()), "id:" + e.getKey());
    }

    // The comparison starts when store tracking started (first GRN or issue slip). Consumption
    // reported before the store existed has no issue slips to compare against — on a project
    // that adopts the store mid-way it would show as a permanent false anomaly.
    LocalDate storeStart = null;
    for (MaterialIssue i : issues) {
      if (i.getIssueDate() != null && (storeStart == null || i.getIssueDate().isBefore(storeStart))) {
        storeStart = i.getIssueDate();
      }
    }
    for (GoodsReceiptNote g : grns) {
      if (g.getReceivedDate() != null && (storeStart == null || g.getReceivedDate().isBefore(storeStart))) {
        storeStart = g.getReceivedDate();
      }
    }

    // ---- reported side: fold APPROVED DPR lines into the same cells ----
    Map<String, BigDecimal[]> rateAcc = new HashMap<>(); // matKey → [rateSum, count]
    Map<String, String> dprNameByKey = new HashMap<>();  // display fallback for DPR-side supervisors
    Set<UUID> dprSupIds = new HashSet<>();
    for (MaterialBalanceService.DprLine line : balanceService.fetchApprovedDprLines(projectId, end)) {
      String matKey = norm(line.materialName());
      if (!issuedMaterialKeys.contains(matKey)) continue; // comparison only covers store-issued materials
      if (storeStart != null && line.reportDate().isBefore(storeStart)) continue;
      String supKey = line.supervisorUserId() != null
          ? "id:" + line.supervisorUserId()
          : nameKeyToIdKey.getOrDefault("nm:" + norm(line.supervisorName()),
              "nm:" + norm(line.supervisorName()));
      if (line.supervisorUserId() != null) {
        dprSupIds.add(line.supervisorUserId());
        if (line.supervisorName() != null && !line.supervisorName().isBlank()) {
          dprNameByKey.putIfAbsent("id:" + line.supervisorUserId(), line.supervisorName());
        }
      }
      Cell c = cells.computeIfAbsent(supKey + "|" + matKey,
          k -> new Cell(supKey, line.materialName(), line.unit()));
      c.reportedToDate = c.reportedToDate.add(nz(line.quantity()));
      if (windowFrom != null && !line.reportDate().isBefore(windowFrom)) {
        c.reportedWindow = c.reportedWindow.add(nz(line.quantity()));
      }
      if (line.unitRate() != null && line.unitRate().signum() > 0) {
        BigDecimal[] acc = rateAcc.computeIfAbsent(matKey, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        acc[0] = acc[0].add(line.unitRate());
        acc[1] = acc[1].add(BigDecimal.ONE);
      }
    }

    // Resolve DPR-side supervisor names too (they need not appear on any issue slip).
    dprSupIds.removeAll(userNames.keySet());
    if (!dprSupIds.isEmpty()) {
      userNames.putAll(resolveUserNames(dprSupIds));
    }

    // GRN rate fallback: latest received date per material key.
    Map<String, BigDecimal> grnRate = new HashMap<>();
    Map<String, LocalDate> grnRateDate = new HashMap<>();
    for (GoodsReceiptNote g : grns) {
      MaterialBalanceService.MaterialRef m = refs.get(g.getMaterialId());
      if (m == null || g.getUnitRate() == null || g.getUnitRate().signum() <= 0) continue;
      String matKey = norm(m.name());
      LocalDate prev = grnRateDate.get(matKey);
      if (prev == null || (g.getReceivedDate() != null && g.getReceivedDate().isAfter(prev))) {
        grnRate.put(matKey, g.getUnitRate());
        if (g.getReceivedDate() != null) grnRateDate.put(matKey, g.getReceivedDate());
      }
    }

    List<SupervisorMaterialRow> rows = new ArrayList<>(cells.size());
    for (Cell c : cells.values()) {
      if (c.issuedToDate.signum() == 0 && c.reportedToDate.signum() == 0) continue;
      String matKey = norm(c.materialName);
      BigDecimal variance = c.issuedToDate.subtract(c.reportedToDate);
      BigDecimal rate = null;
      BigDecimal[] acc = rateAcc.get(matKey);
      if (acc != null && acc[1].signum() > 0) {
        rate = acc[0].divide(acc[1], 4, RoundingMode.HALF_UP);
      } else if (grnRate.containsKey(matKey)) {
        rate = grnRate.get(matKey);
      }
      BigDecimal varianceValue = rate != null
          ? variance.multiply(rate).setScale(2, RoundingMode.HALF_UP) : null;
      String supName = c.supervisorKey.startsWith("id:")
          ? userNames.getOrDefault(UUID.fromString(c.supervisorKey.substring(3)),
              dprNameByKey.getOrDefault(c.supervisorKey, "(unknown user)"))
          : stripNamePrefix(c.supervisorKey);
      rows.add(new SupervisorMaterialRow(
          c.supervisorKey, supName, c.materialName, c.unit,
          scale3(c.issuedToDate), scale3(c.reportedToDate), scale3(variance),
          varianceValue, scale3(c.wastageQty),
          scale3(c.issuedWindow), scale3(c.reportedWindow)));
    }
    rows.sort(Comparator
        .comparing((SupervisorMaterialRow r) -> r.varianceQty().abs()).reversed()
        .thenComparing(SupervisorMaterialRow::supervisorName, String.CASE_INSENSITIVE_ORDER));
    return rows;
  }

  private static String stripNamePrefix(String key) {
    String raw = key.startsWith("nm:") ? key.substring(3) : key;
    return raw.isBlank() ? "(unattributed)" : raw;
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, String> resolveUserNames(Set<UUID> ids) {
    Map<UUID, String> names = new HashMap<>();
    if (ids.isEmpty()) return names;
    List<Object[]> rows = entityManager.createNativeQuery(
            "SELECT id, COALESCE(NULLIF(TRIM(CONCAT(first_name, ' ', last_name)), ''), username) "
                + "FROM public.users WHERE id IN (:ids)")
        .setParameter("ids", ids)
        .getResultList();
    for (Object[] r : rows) {
      names.put((UUID) r[0], (String) r[1]);
    }
    return names;
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }

  private static BigDecimal scale3(BigDecimal v) {
    return v.setScale(3, RoundingMode.HALF_UP);
  }

  private static final class Cell {
    final String supervisorKey;
    final String materialName;
    final String unit;
    BigDecimal issuedToDate = BigDecimal.ZERO;
    BigDecimal issuedWindow = BigDecimal.ZERO;
    BigDecimal reportedToDate = BigDecimal.ZERO;
    BigDecimal reportedWindow = BigDecimal.ZERO;
    BigDecimal wastageQty = BigDecimal.ZERO;

    Cell(String supervisorKey, String materialName, String unit) {
      this.supervisorKey = supervisorKey;
      this.materialName = materialName;
      this.unit = unit;
    }
  }
}
