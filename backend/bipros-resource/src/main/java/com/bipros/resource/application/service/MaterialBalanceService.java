package com.bipros.resource.application.service;

import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprMaterial;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.resource.application.dto.MaterialAvailabilityResult;
import com.bipros.resource.application.dto.MaterialBalanceRow;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialReturn;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import com.bipros.resource.domain.repository.MaterialReturnRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical material availability engine (AI Agent sheet, Material row — MAT-01/MAT-02).
 *
 * <p>Balance is COMPUTED, never typed: store closing = received (GRN + storekeeper-log received)
 * − issued (issue slips), cumulative to the report end date; when the storekeeper daily log has an
 * explicit closing stock for a material, its latest figure wins (store authoritative, MAT-05
 * principle). Consumption counts APPROVED DPR material lines; for materials the storekeeper also
 * logs consumption on, the log figure wins per material to avoid double-counting the two capture
 * paths. A project with zero store rows (no GRN / issue / log) is {@code tracked=false} — callers
 * show "stock not tracked" instead of fictional zeros.
 *
 * <p>Identity: GRN/issue reference the catalogue ({@code resource.material}); logs and DPR lines
 * are free text. Everything joins on the normalized name (lowercase, trimmed, collapsed spaces).
 * Unmatched names stay visible as their own rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialBalanceService {

  private static final int BURN_RATE_DAYS = 14;

  private final GoodsReceiptNoteRepository grnRepository;
  private final MaterialIssueRepository issueRepository;
  private final MaterialReturnRepository returnRepository;
  private final MaterialConsumptionLogRepository consumptionLogRepository;
  private final MaterialRepository materialRepository;
  private final ResourceRepository resourceRepository;
  private final DailyProgressReportRepository dprRepository;
  private final DprMaterialRepository dprMaterialRepository;

  /** Material name+unit for a GRN/issue material reference. */
  public record MaterialRef(String name, String unit) {}

  /**
   * GRN and issue rows reference the {@code resource.material} catalogue by design, but live data
   * (and the demo seeder) also carries {@code resource.resources} ids of MATERIAL-type resources —
   * so identity resolution falls back catalogue → resources → "(unknown material)".
   */
  public Map<UUID, MaterialRef> resolveMaterialRefs(UUID projectId, Set<UUID> ids) {
    Map<UUID, MaterialRef> refs = new HashMap<>();
    if (ids.isEmpty()) return refs;
    for (Material m : materialRepository.findByProjectId(projectId)) {
      if (ids.contains(m.getId())) refs.put(m.getId(), new MaterialRef(m.getName(), m.getUnit()));
    }
    Set<UUID> missing = new java.util.HashSet<>(ids);
    missing.removeAll(refs.keySet());
    if (!missing.isEmpty()) {
      for (Resource r : resourceRepository.findAllById(missing)) {
        refs.put(r.getId(), new MaterialRef(r.getName(), r.getUnit()));
      }
    }
    return refs;
  }

  /** Normalized-name join key shared by the balance, comparison and report engines. */
  public static String norm(String s) {
    return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
  }

  /**
   * @param from window start for the movement columns (nullable → window = everything ≤ to)
   * @param to report end date (nullable → today)
   * @param lowCoverDays days-of-cover threshold below which LOW_COVER fires (≤0 disables)
   */
  @Transactional(readOnly = true)
  public MaterialAvailabilityResult availability(
      UUID projectId, LocalDate from, LocalDate to, int lowCoverDays) {
    LocalDate end = to != null ? to : LocalDate.now();

    List<GoodsReceiptNote> grns = grnRepository.findByProjectIdOrderByReceivedDateDesc(projectId);
    List<MaterialIssue> issues = issueRepository.findByProjectId(projectId);
    List<MaterialReturn> returns = returnRepository.findByProjectId(projectId);
    List<MaterialConsumptionLog> logs =
        consumptionLogRepository.findByProjectIdOrderByLogDateAscIdAsc(projectId);
    boolean tracked = !grns.isEmpty() || !issues.isEmpty() || !logs.isEmpty();

    Set<UUID> refIds = new java.util.HashSet<>();
    for (GoodsReceiptNote g : grns) refIds.add(g.getMaterialId());
    for (MaterialIssue i : issues) refIds.add(i.getMaterialId());
    for (MaterialReturn r : returns) refIds.add(r.getMaterialId());
    refIds.remove(null);
    Map<UUID, MaterialRef> refs = resolveMaterialRefs(projectId, refIds);

    // key → accumulator
    Map<String, Acc> byKey = new LinkedHashMap<>();

    for (GoodsReceiptNote g : grns) {
      if (afterEnd(g.getReceivedDate(), end)) continue;
      MaterialRef m = refs.get(g.getMaterialId());
      Acc acc = acc(byKey, m != null ? m.name() : "(unknown material)",
          m != null ? m.unit() : null);
      BigDecimal qty = nz(g.getAcceptedQuantity() != null ? g.getAcceptedQuantity() : g.getQuantity());
      acc.receivedToDate = acc.receivedToDate.add(qty);
      if (inWindow(g.getReceivedDate(), from, end)) acc.receivedWindow = acc.receivedWindow.add(qty);
    }

    for (MaterialIssue i : issues) {
      if (afterEnd(i.getIssueDate(), end)) continue;
      MaterialRef m = refs.get(i.getMaterialId());
      Acc acc = acc(byKey, m != null ? m.name() : "(unknown material)",
          m != null ? m.unit() : null);
      BigDecimal qty = nz(i.getQuantity());
      acc.issuedToDate = acc.issuedToDate.add(qty);
      if (inWindow(i.getIssueDate(), from, end)) acc.issuedWindow = acc.issuedWindow.add(qty);
      if (acc.earliestIssueDate == null || i.getIssueDate().isBefore(acc.earliestIssueDate)) {
        acc.earliestIssueDate = i.getIssueDate();
      }
    }

    // Returns drain what the custodian is holding. They are NOT added back into received here:
    // a USABLE return already writes a `received` row into the daily consumption log
    // (MaterialReturnService), so it reaches receivedToDate — and the store closing balance —
    // through the log loop below. Adding it again here would double-count the same movement.
    for (MaterialReturn r : returns) {
      if (afterEnd(r.getReturnDate(), end)) continue;
      MaterialRef m = refs.get(r.getMaterialId());
      Acc acc = acc(byKey, m != null ? m.name() : "(unknown material)",
          m != null ? m.unit() : null);
      acc.returnedToDate = acc.returnedToDate.add(nz(r.getQuantity()));
    }

    for (MaterialConsumptionLog l : logs) {
      if (afterEnd(l.getLogDate(), end)) continue;
      Acc acc = acc(byKey, l.getMaterialName(), l.getUnit());
      BigDecimal received = nz(l.getReceived());
      acc.receivedToDate = acc.receivedToDate.add(received);
      if (inWindow(l.getLogDate(), from, end)) acc.receivedWindow = acc.receivedWindow.add(received);
      BigDecimal consumed = nz(l.getConsumed());
      acc.logConsumedToDate = acc.logConsumedToDate.add(consumed);
      if (inWindow(l.getLogDate(), from, end)) acc.logConsumedWindow = acc.logConsumedWindow.add(consumed);
      if (l.getClosingStock() != null
          && (acc.latestLogDate == null || !l.getLogDate().isBefore(acc.latestLogDate))) {
        acc.latestLogDate = l.getLogDate();
        acc.latestLogClosing = l.getClosingStock();
      }
    }

    // Approved-DPR consumption (free-text names), with report dates for the burn-rate window.
    LocalDate burnFrom = end.minusDays(BURN_RATE_DAYS - 1L);
    for (DprLine line : fetchApprovedDprLines(projectId, end)) {
      Acc acc = acc(byKey, line.materialName(), line.unit());
      BigDecimal qty = nz(line.quantity());
      acc.dprConsumedToDate = acc.dprConsumedToDate.add(qty);
      if (inWindow(line.reportDate(), from, end)) acc.dprConsumedWindow = acc.dprConsumedWindow.add(qty);
      if (!line.reportDate().isBefore(burnFrom)) acc.dprConsumedBurn = acc.dprConsumedBurn.add(qty);
    }
    for (MaterialConsumptionLog l : logs) {
      if (afterEnd(l.getLogDate(), end) || l.getLogDate().isBefore(burnFrom)) continue;
      Acc acc = acc(byKey, l.getMaterialName(), l.getUnit());
      acc.logConsumedBurn = acc.logConsumedBurn.add(nz(l.getConsumed()));
    }

    // Min-stock levels from the catalogue, joined by normalized name.
    Map<String, BigDecimal> minStockByKey = new HashMap<>();
    for (Material m : materialRepository.findByProjectId(projectId)) {
      if (m.getMinStockLevel() != null && m.getMinStockLevel().signum() > 0) {
        minStockByKey.putIfAbsent(norm(m.getName()), m.getMinStockLevel());
      }
    }

    List<MaterialBalanceRow> rows = new ArrayList<>(byKey.size());
    for (Map.Entry<String, Acc> e : byKey.entrySet()) {
      Acc a = e.getValue();
      // Workface consumption: approved DPRs are canonical. The storekeeper log's "consumed"
      // records store OUTFLOW (recording an issue slip auto-writes a log row), so it stands in
      // for consumption only for materials never issued through slips (true legacy mode B).
      // For a slip-tracked material with no DPR lines yet, the fallback would count the
      // issue itself as consumption and zero the custodian balance — an issue is custody,
      // not consumption, so until a DPR reports usage the consumed figure is simply zero.
      boolean dprPath = a.dprConsumedToDate.signum() > 0;
      boolean slipTracked = a.issuedToDate.signum() > 0;
      BigDecimal consumedToDate = dprPath ? a.dprConsumedToDate
          : (slipTracked ? BigDecimal.ZERO : a.logConsumedToDate);
      BigDecimal consumedWindow = dprPath ? a.dprConsumedWindow
          : (slipTracked ? BigDecimal.ZERO : a.logConsumedWindow);
      BigDecimal consumedBurn = dprPath ? a.dprConsumedBurn
          : (slipTracked ? BigDecimal.ZERO : a.logConsumedBurn);

      BigDecimal storeClosing = null;
      BigDecimal siteBalance = null;
      if (tracked) {
        storeClosing = a.latestLogClosing != null
            ? a.latestLogClosing
            : a.receivedToDate.subtract(a.issuedToDate);
        if (a.issuedToDate.signum() > 0) {
          // What is still physically with the custodians: issued out, less what came back
          // (usable or scrap — both leave their hands), less what was consumed into the works.
          siteBalance = a.issuedToDate.subtract(a.returnedToDate).subtract(consumedToDate);
        }
      }

      BigDecimal avgDaily = consumedBurn.signum() > 0
          ? consumedBurn.divide(BigDecimal.valueOf(BURN_RATE_DAYS), 3, RoundingMode.HALF_UP)
          : null;
      BigDecimal daysOfCover = (storeClosing != null && avgDaily != null && avgDaily.signum() > 0)
          ? storeClosing.max(BigDecimal.ZERO).divide(avgDaily, 1, RoundingMode.HALF_UP)
          : null;

      BigDecimal minStock = minStockByKey.get(e.getKey());
      List<String> alerts = new ArrayList<>(2);
      if (storeClosing != null && storeClosing.signum() < 0) alerts.add("NEGATIVE_BALANCE");
      if (minStock != null && storeClosing != null && storeClosing.compareTo(minStock) < 0) {
        alerts.add("BELOW_MIN_STOCK");
      } else if (lowCoverDays > 0 && daysOfCover != null
          && daysOfCover.compareTo(BigDecimal.valueOf(lowCoverDays)) < 0) {
        alerts.add("LOW_COVER");
      }

      // Ageing over the outstanding balance only — nothing with custodians, nothing to age.
      Integer daysHeld = (siteBalance != null && siteBalance.signum() > 0
          && a.earliestIssueDate != null)
          ? (int) java.time.temporal.ChronoUnit.DAYS.between(a.earliestIssueDate, end)
          : null;

      rows.add(new MaterialBalanceRow(
          e.getKey(), a.displayName, a.unit,
          scale3(a.receivedWindow), scale3(a.issuedWindow), scale3(consumedWindow),
          scale3(a.receivedToDate), scale3(a.issuedToDate), scale3(consumedToDate),
          storeClosing != null ? scale3(storeClosing) : null,
          siteBalance != null ? scale3(siteBalance) : null,
          minStock, avgDaily, daysOfCover, daysHeld, alerts));
    }
    rows.sort(Comparator
        .comparing((MaterialBalanceRow r) -> r.alerts().isEmpty())
        .thenComparing(MaterialBalanceRow::materialName, String.CASE_INSENSITIVE_ORDER));
    return new MaterialAvailabilityResult(tracked, rows);
  }

  /** Rows that are in short supply per the availability rule — feeds the weekly digest + AI finding. */
  public List<MaterialBalanceRow> shortages(UUID projectId, int lowCoverDays) {
    MaterialAvailabilityResult result = availability(projectId, null, LocalDate.now(), lowCoverDays);
    if (!result.tracked()) return List.of();
    return result.rows().stream()
        .filter(r -> r.alerts().contains("BELOW_MIN_STOCK") || r.alerts().contains("LOW_COVER"))
        .toList();
  }

  // ---------- helpers ----------

  record DprLine(LocalDate reportDate, String materialName, String unit, BigDecimal quantity,
                 UUID supervisorUserId, String supervisorName, BigDecimal unitRate,
                 UUID activityId) {}

  /** All APPROVED DPR material lines up to {@code end}, with parent report date + supervisor. */
  List<DprLine> fetchApprovedDprLines(UUID projectId, LocalDate end) {
    List<DailyProgressReport> dprs =
        dprRepository.findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
            projectId, DprApprovalStatus.APPROVED);
    if (dprs.isEmpty()) return List.of();
    Map<UUID, DailyProgressReport> byId = new HashMap<>();
    for (DailyProgressReport d : dprs) {
      if (d.getReportDate() != null && !d.getReportDate().isAfter(end)) byId.put(d.getId(), d);
    }
    if (byId.isEmpty()) return List.of();
    Set<UUID> ids = byId.keySet();
    List<DprLine> lines = new ArrayList<>();
    for (DprMaterial m : dprMaterialRepository.findByDprIdIn(ids)) {
      DailyProgressReport d = byId.get(m.getDprId());
      if (d == null) continue;
      lines.add(new DprLine(d.getReportDate(), m.getMaterialName(), m.getUnit(), m.getQuantity(),
          d.getSupervisorUserId(), d.getSupervisorName(), m.getUnitRate(), d.getActivityId()));
    }
    return lines;
  }

  private static Acc acc(Map<String, Acc> byKey, String name, String unit) {
    String key = norm(name);
    Acc a = byKey.computeIfAbsent(key, k -> new Acc());
    if (a.displayName == null && name != null && !name.isBlank()) a.displayName = name.trim();
    if (a.unit == null && unit != null && !unit.isBlank()) a.unit = unit.trim();
    return a;
  }

  private static boolean inWindow(LocalDate d, LocalDate from, LocalDate end) {
    if (d == null) return false;
    if (d.isAfter(end)) return false;
    return from == null || !d.isBefore(from);
  }

  private static boolean afterEnd(LocalDate d, LocalDate end) {
    return d == null || d.isAfter(end);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }

  private static BigDecimal scale3(BigDecimal v) {
    return v.setScale(3, RoundingMode.HALF_UP);
  }

  private static final class Acc {
    String displayName;
    String unit;
    BigDecimal receivedWindow = BigDecimal.ZERO;
    BigDecimal issuedWindow = BigDecimal.ZERO;
    BigDecimal receivedToDate = BigDecimal.ZERO;
    BigDecimal issuedToDate = BigDecimal.ZERO;
    BigDecimal returnedToDate = BigDecimal.ZERO;
    BigDecimal logConsumedToDate = BigDecimal.ZERO;
    BigDecimal logConsumedWindow = BigDecimal.ZERO;
    BigDecimal logConsumedBurn = BigDecimal.ZERO;
    BigDecimal dprConsumedToDate = BigDecimal.ZERO;
    BigDecimal dprConsumedWindow = BigDecimal.ZERO;
    BigDecimal dprConsumedBurn = BigDecimal.ZERO;
    LocalDate latestLogDate;
    BigDecimal latestLogClosing;
    LocalDate earliestIssueDate;
  }
}
