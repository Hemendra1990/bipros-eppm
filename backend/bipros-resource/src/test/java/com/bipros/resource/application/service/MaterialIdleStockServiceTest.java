package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.application.dto.IdleStockRow;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialReturnRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Arithmetic of the idle-stock engine. Money/quantity logic whose output cannot be eyeballed, so
 * it is proved here rather than only in-app: the burn-rate need, the summing of need across a
 * custodian's several open fronts, the rule that a tagged activity never also counts in the
 * person pool, and the grace window.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MaterialIdleStockService")
class MaterialIdleStockServiceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CUSTODIAN = UUID.randomUUID();
    private static final UUID MATERIAL = UUID.randomUUID();
    /** Resource role whose name matches the catalogue material, joining plan rows to the material. */
    private static final UUID MATERIAL_ROLE = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    /** Concrete @ 35/CU_M — the live Khasab rate, so the money gate is exercised realistically. */
    private static final BigDecimal RATE = new BigDecimal("35.0000");

    private static final MaterialIdleStockService.IdleThresholds THRESHOLDS =
        new MaterialIdleStockService.IdleThresholds(90, 20, new BigDecimal("100"), 7);

    @Mock private MaterialIssueRepository issueRepository;
    @Mock private MaterialReturnRepository returnRepository;
    @Mock private GoodsReceiptNoteRepository grnRepository;
    @Mock private MaterialBalanceService balanceService;
    @Mock private ActivityRepository activityRepository;
    @Mock private ResourceAssignmentRepository resourceAssignmentRepository;
    @Mock private ResourceRoleRepository resourceRoleRepository;
    @Mock private DailyProgressReportRepository dprRepository;

    @InjectMocks private MaterialIdleStockService service;

    private UUID act1;
    private UUID act2;

    @BeforeEach
    void setUp() {
        act1 = UUID.randomUUID();
        act2 = UUID.randomUUID();
        when(returnRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        when(resourceAssignmentRepository.findByProjectId(PROJECT)).thenReturn(List.of());
        ResourceRole concreteRole = new ResourceRole();
        concreteRole.setId(MATERIAL_ROLE);
        concreteRole.setName("Concrete");
        when(resourceRoleRepository.findAll()).thenReturn(List.of(concreteRole));
        when(balanceService.resolveMaterialRefs(eq(PROJECT), any()))
            .thenReturn(Map.of(MATERIAL, new MaterialBalanceService.MaterialRef("Concrete", "CU_M")));
        // A GRN carrying the unit rate, so the value gate has a rate even with zero consumption.
        GoodsReceiptNote grn = new GoodsReceiptNote();
        grn.setMaterialId(MATERIAL);
        grn.setUnitRate(RATE);
        grn.setReceivedDate(LocalDate.of(2026, 8, 1));
        when(grnRepository.findByProjectIdOrderByReceivedDateDesc(PROJECT)).thenReturn(List.of(grn));
    }

    @Test
    @DisplayName("holding exactly covers the remaining work → no alert")
    void healthyLeftoverDoesNotAlert() {
        givenActivities(activity(act1, "Trenching crew", 90d));
        givenIssues(issue(100, LocalDate.of(2026, 7, 1), act1));
        givenDprLines(dprLine(90, act1));

        IdleStockRow row = only(service.evaluate(PROJECT, TODAY, THRESHOLDS));

        assertThat(row.holding()).isEqualByComparingTo("10.000");
        assertThat(row.need()).isEqualByComparingTo("10.000");
        assertThat(row.excess()).isEqualByComparingTo("0.000");
        assertThat(row.alerting()).isFalse();
    }

    @Test
    @DisplayName("nothing consumed on a nearly finished activity → full holding is excess")
    void idleStockAlerts() {
        givenActivities(activity(act1, "Trenching crew", 90d));
        givenIssues(issue(100, LocalDate.of(2026, 7, 1), act1));
        givenDprLines();

        IdleStockRow row = only(service.evaluate(PROJECT, TODAY, THRESHOLDS));

        assertThat(row.holding()).isEqualByComparingTo("100.000");
        assertThat(row.need()).isEqualByComparingTo("0.000");
        assertThat(row.excess()).isEqualByComparingTo("100.000");
        assertThat(row.excessValue()).isEqualByComparingTo("3500.00");
        assertThat(row.alerting()).isTrue();
    }

    @Test
    @DisplayName("need is summed across every open front the custodian is running")
    void twoActivitiesNeedIsSummed() {
        givenActivities(activity(act1, "Front A", 90d), activity(act2, "Front B", 40d));
        givenIssues(issue(150, LocalDate.of(2026, 7, 1), null)); // untagged → person pool
        givenDprLines(dprLine(90, act1), dprLine(20, act2));

        IdleStockRow row = only(service.evaluate(PROJECT, TODAY, THRESHOLDS));

        assertThat(row.bucket()).isEqualTo(IdleStockRow.Bucket.PERSON);
        assertThat(row.consumedToDate()).isEqualByComparingTo("110.000");
        assertThat(row.holding()).isEqualByComparingTo("40.000");
        // 90/90 × 10 = 10 on Front A, 20/40 × 60 = 30 on Front B
        assertThat(row.need()).isEqualByComparingTo("40.000");
        assertThat(row.excess()).isEqualByComparingTo("0.000");
        assertThat(row.alerting()).isFalse();
    }

    @Test
    @DisplayName("an activity claimed by a tagged slip is excluded from that custodian's pool")
    void taggedActivityExcludedFromPool() {
        givenActivities(activity(act1, "Front A", 90d));
        givenIssues(
            issue(100, LocalDate.of(2026, 7, 1), act1),   // tagged
            issue(50, LocalDate.of(2026, 7, 1), null));   // untagged pool
        givenDprLines(dprLine(90, act1));

        List<IdleStockRow> rows = service.evaluate(PROJECT, TODAY, THRESHOLDS);

        IdleStockRow tagged = rows.stream()
            .filter(r -> r.bucket() == IdleStockRow.Bucket.ACTIVITY).findFirst().orElseThrow();
        IdleStockRow pool = rows.stream()
            .filter(r -> r.bucket() == IdleStockRow.Bucket.PERSON).findFirst().orElseThrow();

        // The 90 consumed on the tagged activity counts ONCE, against the tagged bucket only.
        assertThat(tagged.consumedToDate()).isEqualByComparingTo("90.000");
        assertThat(tagged.need()).isEqualByComparingTo("10.000");
        assertThat(pool.consumedToDate()).isEqualByComparingTo("0.000");
        assertThat(pool.need()).isEqualByComparingTo("0.000");
        assertThat(pool.excess()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("another person's open front does not create demand for this custodian")
    void plannedMaterialOnSomeoneElsesActivityIsIgnored() {
        // act2 is open, plans a large quantity of the same material, but the custodian has never
        // worked on it. Charging them with its demand would mask their own idle stock — the bug
        // found on Khasab 2026-08-12, where one custodian was charged with 4,316 CU_M of project
        // demand and their real 100 CU_M holding went unreported.
        givenActivities(activity(act1, "Their front", 90d), activity(act2, "Someone else", 20d));
        givenIssues(issue(100, LocalDate.of(2026, 7, 1), null));
        givenDprLines(); // no material lines at all
        givenOwnedActivities(act1); // and act1 only
        when(resourceAssignmentRepository.findByProjectId(PROJECT))
            .thenReturn(List.of(materialPlan(act2, 4000)));

        IdleStockRow row = only(service.evaluate(PROJECT, TODAY, THRESHOLDS));

        assertThat(row.need()).isEqualByComparingTo("0.000");
        assertThat(row.excess()).isEqualByComparingTo("100.000");
        assertThat(row.alerting()).isTrue();
    }

    @Test
    @DisplayName("material issued inside the grace window is presumed in use")
    void graceWindowSuppresses() {
        givenActivities(activity(act1, "Trenching crew", 90d));
        givenIssues(issue(100, TODAY.minusDays(1), act1)); // issued yesterday
        givenDprLines();

        IdleStockRow row = only(service.evaluate(PROJECT, TODAY, THRESHOLDS));

        assertThat(row.holding()).isEqualByComparingTo("100.000");
        assertThat(row.graceExcluded()).isEqualByComparingTo("100.000");
        assertThat(row.excess()).isEqualByComparingTo("0.000");
        assertThat(row.alerting()).isFalse();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenActivities(Activity... activities) {
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of(activities));
    }

    private void givenIssues(MaterialIssue... issues) {
        when(issueRepository.findByProjectId(PROJECT)).thenReturn(List.of(issues));
    }

    private void givenDprLines(MaterialBalanceService.DprLine... lines) {
        when(balanceService.fetchApprovedDprLines(eq(PROJECT), any())).thenReturn(List.of(lines));
        // Ownership mirrors the lines: an activity is the custodian's when they filed a DPR on it.
        List<DailyProgressReport> dprs = new java.util.ArrayList<>();
        for (MaterialBalanceService.DprLine l : lines) {
            DailyProgressReport d = new DailyProgressReport();
            d.setSupervisorUserId(l.supervisorUserId());
            d.setActivityId(l.activityId());
            dprs.add(d);
        }
        when(dprRepository.findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
            PROJECT, DprApprovalStatus.APPROVED)).thenReturn(dprs);
    }

    /** Ownership without any material line — used by the "someone else's front" case. */
    private void givenOwnedActivities(UUID... activityIds) {
        List<DailyProgressReport> dprs = new java.util.ArrayList<>();
        for (UUID id : activityIds) {
            DailyProgressReport d = new DailyProgressReport();
            d.setSupervisorUserId(CUSTODIAN);
            d.setActivityId(id);
            dprs.add(d);
        }
        when(dprRepository.findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(
            PROJECT, DprApprovalStatus.APPROVED)).thenReturn(dprs);
    }

    private static Activity activity(UUID id, String name, double percentComplete) {
        Activity a = new Activity();
        a.setId(id);
        a.setName(name);
        a.setPercentComplete(percentComplete);
        return a;
    }

    private static MaterialIssue issue(int qty, LocalDate date, UUID activityId) {
        MaterialIssue i = MaterialIssue.builder()
            .projectId(PROJECT)
            .challanNumber("ISS-202608-" + qty)
            .materialId(MATERIAL)
            .issueDate(date)
            .quantity(BigDecimal.valueOf(qty))
            .issuedToUserId(CUSTODIAN)
            .activityId(activityId)
            .build();
        i.setId(UUID.randomUUID());
        return i;
    }

    /** A planned Material Requirements row for the activity, in the same material as the issue. */
    private static ResourceAssignment materialPlan(UUID activityId, double plannedUnits) {
        ResourceAssignment ra = new ResourceAssignment();
        ra.setProjectId(PROJECT);
        ra.setActivityId(activityId);
        ra.setRoleId(MATERIAL_ROLE);
        ra.setMaterialRoleVariantId(UUID.randomUUID());
        ra.setPlannedUnits(plannedUnits);
        return ra;
    }

    private static MaterialBalanceService.DprLine dprLine(int qty, UUID activityId) {
        return new MaterialBalanceService.DprLine(
            LocalDate.of(2026, 8, 5), "Concrete", "m3", BigDecimal.valueOf(qty),
            CUSTODIAN, "M. Pradeep", RATE, activityId);
    }

    private static IdleStockRow only(List<IdleStockRow> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
