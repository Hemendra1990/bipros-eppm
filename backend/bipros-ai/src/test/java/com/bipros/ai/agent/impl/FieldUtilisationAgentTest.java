package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyResourceDeployment;
import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.project.domain.repository.DailyResourceDeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldUtilisationAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000fd");

    @Mock
    private DailyResourceDeploymentRepository deploymentRepository;

    private FieldUtilisationAgent agent() {
        return new FieldUtilisationAgent(deploymentRepository, new ObjectMapper());
    }

    private static DailyResourceDeployment row(DeploymentResourceType type, LocalDate date,
                                               int planned, int deployed, double worked, double idle) {
        return DailyResourceDeployment.builder()
                .projectId(PROJECT).logDate(date).resourceType(type)
                .resourceDescription(type.name())
                .nosPlanned(planned).nosDeployed(deployed)
                .hoursWorked(worked).idleHours(idle)
                .build();
    }

    /** 5 days each: manpower healthy (88% deployed, low idle), equipment poor (63% deployed, 37% idle). */
    private List<DailyResourceDeployment> mixedLog() {
        List<DailyResourceDeployment> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate d = LocalDate.of(2026, 7, 1).plusDays(i);
            rows.add(row(DeploymentResourceType.MANPOWER, d, 20, 18, 80, 4));   // 90% deployed, ~5% idle
            rows.add(row(DeploymentResourceType.EQUIPMENT, d, 20, 12, 40, 24)); // 60% deployed, 37.5% idle
        }
        return rows;
    }

    @Test
    void flagsEquipmentUnderDeploymentAndIdle() {
        when(deploymentRepository.findByProjectIdOrderByLogDateAscIdAsc(PROJECT)).thenReturn(mixedLog());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("UNDER_DEPLOYMENT", "HIGH_IDLE_TIME");
        // Every finding is about equipment; the healthy manpower class raises nothing.
        assertThat(c).allSatisfy(f -> assertThat(f.subjectRef()).isEqualTo("deployment:EQUIPMENT"));
        // Equipment idle share 37.5% (> 0.35) is HIGH and sorts first.
        assertThat(c.get(0).findingType()).isEqualTo("HIGH_IDLE_TIME");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(result.dataSnapshot().get("types").size()).isEqualTo(2);
    }

    @Test
    void healthyLogYieldsNoFindings() {
        List<DailyResourceDeployment> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LocalDate d = LocalDate.of(2026, 7, 1).plusDays(i);
            rows.add(row(DeploymentResourceType.MANPOWER, d, 20, 19, 90, 3));
            rows.add(row(DeploymentResourceType.EQUIPMENT, d, 10, 10, 80, 2));
        }
        when(deploymentRepository.findByProjectIdOrderByLogDateAscIdAsc(PROJECT)).thenReturn(rows);

        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }

    @Test
    void tooFewDaysYieldsNoFindings() {
        // Only 2 reported days for equipment — below MIN_DAYS, so no judgement despite a poor ratio.
        List<DailyResourceDeployment> rows = List.of(
                row(DeploymentResourceType.EQUIPMENT, LocalDate.of(2026, 7, 1), 20, 5, 20, 30),
                row(DeploymentResourceType.EQUIPMENT, LocalDate.of(2026, 7, 2), 20, 4, 15, 30));
        when(deploymentRepository.findByProjectIdOrderByLogDateAscIdAsc(PROJECT)).thenReturn(rows);

        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }

    @Test
    void emptyLogYieldsNoFindings() {
        when(deploymentRepository.findByProjectIdOrderByLogDateAscIdAsc(PROJECT)).thenReturn(List.of());
        assertThat(agent().gather(AgentRunContext.manual(PROJECT, null)).candidates()).isEmpty();
    }
}
