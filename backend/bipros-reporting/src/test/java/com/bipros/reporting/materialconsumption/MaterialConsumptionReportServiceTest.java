package com.bipros.reporting.materialconsumption;

import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivitySupervisorRepository;
import com.bipros.project.application.dto.DprMaterialLine;
import com.bipros.project.application.service.DprMaterialConsumptionLookup;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialConsumptionReportServiceTest {

    private static final UUID PID = UUID.randomUUID();
    private static final UUID ACT = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 3, 19);
    private static final LocalDate TO = LocalDate.of(2026, 6, 5);

    @Mock MaterialConsumptionLogRepository consumptionRepo;
    @Mock MaterialIssueRepository issueRepo;
    @Mock ActivityRepository activityRepo;
    @Mock ActivitySupervisorRepository activitySupervisorRepo;
    @Mock DprMaterialConsumptionLookup dprMaterialLookup;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks MaterialConsumptionReportService service;

    @BeforeEach
    void setUp() {
        when(consumptionRepo.findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(PID, FROM, TO))
            .thenReturn(List.of());
        when(issueRepo.findByProjectIdAndIssueDateBetween(PID, FROM, TO)).thenReturn(List.of());
        // every native name/supervisor lookup resolves to empty → null
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    @Test
    void dprMaterialSurfacesWithActualCostAndNoPlanned() {
        when(dprMaterialLookup.findApprovedLines(PID, FROM, TO)).thenReturn(List.of(
            new DprMaterialLine(FROM, ACT, "Concrete", "m3", new BigDecimal("9"), new BigDecimal("62"), new BigDecimal("558")),
            new DprMaterialLine(FROM, ACT, "Concrete", "m3", new BigDecimal("6"), new BigDecimal("62"), new BigDecimal("372"))));

        var filter = new MaterialConsumptionFilter(PID, FROM, TO, null, null, null, null, null, null);
        MaterialConsumptionReportResponse resp = service.generate(filter);

        assertThat(resp.rows()).hasSize(2);
        assertThat(resp.rows().get(0).consumedQty()).isEqualByComparingTo("9");
        assertThat(resp.rows().get(0).actualCost()).isEqualByComparingTo("558");
        assertThat(resp.totals().get("actualCost")).isEqualByComparingTo("930");
        assertThat(resp.totals()).doesNotContainKeys("plannedCost", "variance");
        // EM supervisor lookup returns empty in this test → no supervisor resolved.
        assertThat(resp.supervisors()).isEmpty();
    }

    @Test
    void noLedgerAndNoDprMaterialYieldsEmpty() {
        when(dprMaterialLookup.findApprovedLines(PID, FROM, TO)).thenReturn(java.util.List.of());
        var filter = new MaterialConsumptionFilter(PID, FROM, TO, null, null, null, null, null, null);
        MaterialConsumptionReportResponse resp = service.generate(filter);
        assertThat(resp.rows()).isEmpty();
        assertThat(resp.totals().get("actualCost")).isEqualByComparingTo("0");
        assertThat(resp.supervisors()).isEmpty();
    }
}
