package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.application.dto.IdleStockRow;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialReturn;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialReturnRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
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
 * Idle-stock engine (owner request 2026-08-12): when an activity nears completion, how much of
 * the material the store issued to a person is surplus to the work they still have left?
 *
 * <pre>
 *   holding = issued − returned − consumed
 *   need    = Σ over their open activities A of  (consumed_A ÷ %complete_A) × (100 − %complete_A)
 *   excess  = holding − need − (issued inside the grace window)
 * </pre>
 *
 * <p><b>Burn-rate basis, never issue-proportional</b>: the yardstick is what this person actually
 * consumed per point of progress, so an over-issue is caught instead of being treated as the
 * correct quantity.
 *
 * <p><b>Two buckets, and an activity belongs to exactly one.</b> Issue slips that name an
 * activity are judged per activity, and their consumption side counts DPR lines filed by
 * <i>anyone</i> on that activity — otherwise material signed for by one supervisor and poured by
 * another reads as permanently idle. Untagged slips fall into a per-person pool whose consumption
 * counts only that person's own DPR lines. Any activity claimed by a tagged slip is excluded from
 * that custodian's pool, so its consumption and its need are never counted twice.
 *
 * <p>Reuses the established ledger rules rather than restating them: consumption counts APPROVED
 * DPR lines only, lines before the project's first store movement are ignored (a project that
 * adopts the store mid-way has nothing to compare them against), materials join on normalized
 * name, and the rate is the average DPR unit rate falling back to the latest GRN rate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialIdleStockService {

    /** Thresholds from Admin → Settings; see {@code DprAlertConfig.idleThresholds()}. */
    public record IdleThresholds(int percentTrigger, int excessPct, BigDecimal valueFloor,
                                 int graceDays) {
    }

    private final MaterialIssueRepository issueRepository;
    private final MaterialReturnRepository returnRepository;
    private final GoodsReceiptNoteRepository grnRepository;
    private final MaterialBalanceService balanceService;
    private final ActivityRepository activityRepository;
    private final DailyProgressReportRepository dprRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final ResourceRoleRepository resourceRoleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<IdleStockRow> evaluate(UUID projectId, LocalDate asOf, IdleThresholds t) {
        LocalDate end = asOf != null ? asOf : LocalDate.now();

        List<MaterialIssue> issues = issueRepository.findByProjectId(projectId).stream()
            .filter(i -> i.getIssuedToUserId() != null && i.getIssueDate() != null
                && !i.getIssueDate().isAfter(end))
            .toList();
        if (issues.isEmpty()) {
            return List.of();
        }

        Map<UUID, BigDecimal> returnedByIssue = new HashMap<>();
        for (MaterialReturn r : returnRepository.findByProjectId(projectId)) {
            if (r.getReturnDate() == null || r.getReturnDate().isAfter(end)) continue;
            returnedByIssue.merge(r.getMaterialIssueId(), nz(r.getQuantity()), BigDecimal::add);
        }

        List<GoodsReceiptNote> grns = grnRepository.findByProjectIdOrderByReceivedDateDesc(projectId);
        Set<UUID> refIds = new HashSet<>();
        for (MaterialIssue i : issues) refIds.add(i.getMaterialId());
        for (GoodsReceiptNote g : grns) refIds.add(g.getMaterialId());
        refIds.remove(null);
        Map<UUID, MaterialBalanceService.MaterialRef> refs =
            balanceService.resolveMaterialRefs(projectId, refIds);

        LocalDate storeStart = storeStart(issues, grns);
        List<MaterialBalanceService.DprLine> dprLines =
            balanceService.fetchApprovedDprLines(projectId, end).stream()
                .filter(l -> storeStart == null || !l.reportDate().isBefore(storeStart))
                .toList();

        // Which activities each person actually works on, from their approved DPRs. Ownership is
        // NOT time-bounded by storeStart — a supervisor's fronts are their fronts regardless of
        // when the store started. Without this the person pool would charge one custodian with
        // the planned demand of every open activity in the project.
        Map<UUID, Set<UUID>> ownedActivities = new HashMap<>();
        for (DailyProgressReport d : dprRepository
                .findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
                    projectId, DprApprovalStatus.APPROVED)) {
            if (d.getSupervisorUserId() == null || d.getActivityId() == null) continue;
            ownedActivities.computeIfAbsent(d.getSupervisorUserId(), k -> new HashSet<>())
                .add(d.getActivityId());
        }

        Map<UUID, Activity> activities = new HashMap<>();
        for (Activity a : activityRepository.findByProjectId(projectId)) {
            activities.put(a.getId(), a);
        }

        // (custodian, materialKey) -> activities whose slips named them; those activities are
        // handled in the ACTIVITY bucket and must not be re-counted in the person pool.
        Map<String, Set<UUID>> claimed = new HashMap<>();
        for (MaterialIssue i : issues) {
            if (i.getActivityId() == null) continue;
            claimed.computeIfAbsent(cellKey(i.getIssuedToUserId(), materialKey(refs, i)),
                k -> new HashSet<>()).add(i.getActivityId());
        }

        Map<String, Cell> cells = new LinkedHashMap<>();
        for (MaterialIssue i : issues) {
            String matKey = materialKey(refs, i);
            MaterialBalanceService.MaterialRef ref = refs.get(i.getMaterialId());
            Cell cell = cells.computeIfAbsent(
                bucketKey(i.getIssuedToUserId(), matKey, i.getActivityId()),
                k -> new Cell(i.getIssuedToUserId(), matKey,
                    ref != null ? ref.name() : "(unknown material)",
                    ref != null ? ref.unit() : null,
                    i.getActivityId() != null ? IdleStockRow.Bucket.ACTIVITY
                        : IdleStockRow.Bucket.PERSON,
                    i.getActivityId()));
            cell.issued = cell.issued.add(nz(i.getQuantity()));
            cell.returned = cell.returned.add(returnedByIssue.getOrDefault(i.getId(), BigDecimal.ZERO));
            if (i.getChallanNumber() != null) cell.challans.add(i.getChallanNumber());
            if (cell.earliestIssueDate == null || i.getIssueDate().isBefore(cell.earliestIssueDate)) {
                cell.earliestIssueDate = i.getIssueDate();
            }
            if (t.graceDays() > 0 && i.getIssueDate().isAfter(end.minusDays(t.graceDays()))) {
                cell.graceExcluded = cell.graceExcluded.add(nz(i.getQuantity()));
            }
        }

        Map<UUID, String> custodianNames = resolveUserNames(
            cells.values().stream().map(c -> c.custodianUserId).collect(java.util.stream.Collectors.toSet()));
        Map<String, BigDecimal> rateByMaterial = rates(dprLines, grns, refs);

        // Planned material per (activity, material key), built once — the burn-rate fallback for
        // an open activity that has not consumed this material yet.
        Map<UUID, String> roleNames = new HashMap<>();
        for (ResourceRole role : resourceRoleRepository.findAll()) {
            roleNames.put(role.getId(), role.getName());
        }
        Map<String, BigDecimal> plannedByActivityMaterial = new HashMap<>();
        for (ResourceAssignment ra : resourceAssignmentRepository.findByProjectId(projectId)) {
            if (ra.getMaterialRoleVariantId() == null || ra.getRoleId() == null
                || ra.getActivityId() == null) continue;
            BigDecimal qty = ra.getPlannedUnits() != null
                ? BigDecimal.valueOf(ra.getPlannedUnits())
                : ra.getQuantity();
            if (qty == null || qty.signum() <= 0) continue;
            plannedByActivityMaterial.merge(
                ra.getActivityId() + "|" + norm(roleNames.get(ra.getRoleId())), qty, BigDecimal::add);
        }

        List<IdleStockRow> rows = new ArrayList<>(cells.size());
        for (Cell cell : cells.values()) {
            Set<UUID> claimedForCell =
                claimed.getOrDefault(cellKey(cell.custodianUserId, cell.materialKey), Set.of());

            BigDecimal consumed;
            BigDecimal need;
            BigDecimal pct;
            if (cell.bucket == IdleStockRow.Bucket.ACTIVITY) {
                Activity a = activities.get(cell.activityId);
                // Activity-tagged: anyone's consumption on that activity drains the holding,
                // so material signed for by one supervisor and poured by another still nets off.
                consumed = sumLines(dprLines, cell.materialKey,
                    l -> cell.activityId.equals(l.activityId()));
                pct = pctOf(a);
                need = needFor(consumed, a, cell.materialKey, plannedByActivityMaterial);
            } else {
                consumed = sumLines(dprLines, cell.materialKey,
                    l -> isSameUser(l, cell.custodianUserId, custodianNames)
                        && !claimedForCell.contains(l.activityId()));
                need = BigDecimal.ZERO;
                pct = BigDecimal.ZERO;
                Set<UUID> owned = ownedActivities.getOrDefault(cell.custodianUserId, Set.of());
                for (Activity a : activities.values()) {
                    if (claimedForCell.contains(a.getId()) || !isOpen(a)) continue;
                    // Only this custodian's own fronts create demand for them.
                    if (!owned.contains(a.getId())) continue;
                    BigDecimal onActivity = sumLines(dprLines, cell.materialKey,
                        l -> isSameUser(l, cell.custodianUserId, custodianNames)
                            && a.getId().equals(l.activityId()));
                    BigDecimal planned =
                        plannedByActivityMaterial.get(a.getId() + "|" + cell.materialKey);
                    if (onActivity.signum() == 0 && planned == null) continue;
                    need = need.add(needFor(onActivity, a, cell.materialKey, plannedByActivityMaterial));
                    BigDecimal p = pctOf(a);
                    if (p.compareTo(pct) > 0) pct = p;
                }
            }

            BigDecimal holding = cell.issued.subtract(cell.returned).subtract(consumed);
            BigDecimal excess = holding.subtract(need).subtract(cell.graceExcluded);
            BigDecimal rate = rateByMaterial.get(cell.materialKey);
            BigDecimal excessValue = rate == null ? null
                : excess.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            boolean alerting = excess.signum() > 0
                && excess.compareTo(cell.issued.multiply(BigDecimal.valueOf(t.excessPct()))
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP)) >= 0
                // No rate resolvable ⇒ the value gate cannot be applied; the share gate alone
                // decides, so a missing rate never hides genuinely idle material.
                && (rate == null || excessValue.compareTo(t.valueFloor()) >= 0);

            rows.add(new IdleStockRow(
                cell.custodianUserId,
                custodianNames.getOrDefault(cell.custodianUserId, "(unknown user)"),
                cell.materialKey, cell.materialName, cell.unit,
                cell.bucket, cell.activityId,
                cell.activityId == null ? null : nameOf(activities.get(cell.activityId)),
                scale2(pct),
                scale3(cell.issued), scale3(cell.returned), scale3(consumed),
                scale3(holding), scale3(need), scale3(cell.graceExcluded), scale3(excess),
                rate, excessValue, alerting,
                cell.earliestIssueDate, List.copyOf(cell.challans)));
        }
        rows.sort(Comparator.comparing((IdleStockRow r) -> r.excess().abs()).reversed());
        return rows;
    }

    /** Rows for one custodian — the per-person email and the DPR panel. */
    @Transactional(readOnly = true)
    public List<IdleStockRow> evaluateForCustodian(UUID projectId, UUID custodianUserId,
                                                   LocalDate asOf, IdleThresholds t) {
        return evaluate(projectId, asOf, t).stream()
            .filter(r -> custodianUserId.equals(r.custodianUserId()))
            .toList();
    }

    // ---------------------------------------------------------------- need

    /**
     * Burn-rate need for one activity, falling back to its planned material quantity when it has
     * consumed none of the material yet (rate undefined). An activity with neither contributes
     * nothing — we never invent demand.
     */
    private static BigDecimal needFor(BigDecimal consumed, Activity a, String materialKey,
                                      Map<String, BigDecimal> plannedByActivityMaterial) {
        BigDecimal pct = pctOf(a);
        if (a == null || pct.signum() <= 0 || pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = BigDecimal.valueOf(100).subtract(pct);
        if (consumed.signum() > 0) {
            return consumed.divide(pct, 6, RoundingMode.HALF_UP).multiply(remaining);
        }
        BigDecimal planned = plannedByActivityMaterial.get(a.getId() + "|" + materialKey);
        return planned == null ? BigDecimal.ZERO
            : planned.multiply(remaining).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isOpen(Activity a) {
        Double pct = a.getPercentComplete();
        return pct != null && pct < 100d;
    }

    private static BigDecimal pctOf(Activity a) {
        if (a == null || a.getPercentComplete() == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(a.getPercentComplete());
    }

    private static String nameOf(Activity a) {
        return a == null ? null : a.getName();
    }

    private static boolean isSameUser(MaterialBalanceService.DprLine line, UUID custodianUserId,
                                      Map<UUID, String> names) {
        if (line.supervisorUserId() != null) {
            return custodianUserId.equals(line.supervisorUserId());
        }
        String custodian = names.get(custodianUserId);
        return custodian != null && norm(custodian).equals(norm(line.supervisorName()));
    }

    private static BigDecimal sumLines(List<MaterialBalanceService.DprLine> lines, String materialKey,
                                       java.util.function.Predicate<MaterialBalanceService.DprLine> p) {
        BigDecimal sum = BigDecimal.ZERO;
        for (MaterialBalanceService.DprLine l : lines) {
            if (!materialKey.equals(norm(l.materialName()))) continue;
            if (!p.test(l)) continue;
            sum = sum.add(nz(l.quantity()));
        }
        return sum;
    }

    private static String materialKey(Map<UUID, MaterialBalanceService.MaterialRef> refs,
                                      MaterialIssue i) {
        MaterialBalanceService.MaterialRef ref = refs.get(i.getMaterialId());
        return norm(ref != null ? ref.name() : "(unknown material)");
    }

    private static String cellKey(UUID custodianUserId, String materialKey) {
        return custodianUserId + "|" + materialKey;
    }

    private static String bucketKey(UUID custodianUserId, String materialKey, UUID activityId) {
        return custodianUserId + "|" + materialKey + "|" + (activityId == null ? "POOL" : activityId);
    }

    private static LocalDate storeStart(List<MaterialIssue> issues, List<GoodsReceiptNote> grns) {
        LocalDate start = null;
        for (MaterialIssue i : issues) {
            if (i.getIssueDate() != null && (start == null || i.getIssueDate().isBefore(start))) {
                start = i.getIssueDate();
            }
        }
        for (GoodsReceiptNote g : grns) {
            if (g.getReceivedDate() != null && (start == null || g.getReceivedDate().isBefore(start))) {
                start = g.getReceivedDate();
            }
        }
        return start;
    }

    /** Average DPR unit rate per material, falling back to the latest GRN rate. */
    private static Map<String, BigDecimal> rates(List<MaterialBalanceService.DprLine> lines,
                                                 List<GoodsReceiptNote> grns,
                                                 Map<UUID, MaterialBalanceService.MaterialRef> refs) {
        Map<String, BigDecimal[]> acc = new HashMap<>();
        for (MaterialBalanceService.DprLine l : lines) {
            if (l.unitRate() == null || l.unitRate().signum() <= 0) continue;
            BigDecimal[] a = acc.computeIfAbsent(norm(l.materialName()),
                k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(l.unitRate());
            a[1] = a[1].add(BigDecimal.ONE);
        }
        Map<String, BigDecimal> out = new HashMap<>();
        for (Map.Entry<String, BigDecimal[]> e : acc.entrySet()) {
            if (e.getValue()[1].signum() > 0) {
                out.put(e.getKey(), e.getValue()[0].divide(e.getValue()[1], 4, RoundingMode.HALF_UP));
            }
        }
        Map<String, LocalDate> latest = new HashMap<>();
        for (GoodsReceiptNote g : grns) {
            MaterialBalanceService.MaterialRef ref = refs.get(g.getMaterialId());
            if (ref == null || g.getUnitRate() == null || g.getUnitRate().signum() <= 0) continue;
            String key = norm(ref.name());
            if (out.containsKey(key)) continue;
            LocalDate prev = latest.get(key);
            if (prev == null || (g.getReceivedDate() != null && g.getReceivedDate().isAfter(prev))) {
                latest.put(key, g.getReceivedDate());
                out.put(key, g.getUnitRate());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> resolveUserNames(Set<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (entityManager == null || ids.isEmpty()) return names;
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

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Cell {
        final UUID custodianUserId;
        final String materialKey;
        final String materialName;
        final String unit;
        final IdleStockRow.Bucket bucket;
        final UUID activityId;
        BigDecimal issued = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        BigDecimal graceExcluded = BigDecimal.ZERO;
        LocalDate earliestIssueDate;
        final List<String> challans = new ArrayList<>();

        Cell(UUID custodianUserId, String materialKey, String materialName, String unit,
             IdleStockRow.Bucket bucket, UUID activityId) {
            this.custodianUserId = custodianUserId;
            this.materialKey = materialKey;
            this.materialName = materialName;
            this.unit = unit;
            this.bucket = bucket;
            this.activityId = activityId;
        }
    }
}
