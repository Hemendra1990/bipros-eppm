package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.AgentRuntime;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.application.service.ScheduleHealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000bc");

    @Mock private ScheduleHealthService scheduleHealthService;
    @Mock private BaselineService baselineService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityRelationshipRepository relationshipRepository;
    @Mock private AgentMemoryService memory;

    private BaselineIntelligenceAgent agent() throws Exception {
        BaselineIntelligenceAgent a = new BaselineIntelligenceAgent(
                scheduleHealthService, baselineService, projectRepository,
                activityRepository, relationshipRepository, new ObjectMapper());
        Field f = AbstractAgent.class.getDeclaredField("runtime");
        f.setAccessible(true);
        f.set(a, new AgentRuntime(null, memory, null, null, null, null, new ObjectMapper()));
        return a;
    }

    private static ScheduleHealthResponse health(double score, double missingLogic, double highFloat, boolean stale) {
        return new ScheduleHealthResponse(UUID.randomUUID(), PROJECT, UUID.randomUUID(),
                40, 1, 0, 141.0, score, java.util.Map.of("10plus", 39), null,
                missingLogic, highFloat, 0.08, 12, null, null, null, stale);
    }

    private static Activity work(String name, Double totalFloat) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setActivityType(ActivityType.TASK_DEPENDENT);
        a.setTotalFloat(totalFloat);
        return a;
    }

    private static ActivityRelationship rel(UUID pred, UUID succ) {
        ActivityRelationship r = new ActivityRelationship();
        r.setProjectId(PROJECT);
        r.setPredecessorActivityId(pred);
        r.setSuccessorActivityId(succ);
        return r;
    }

    @Test
    void scoresHealthAndFlagsQualityGaps() throws Exception {
        Project p = new Project();
        p.setPrimaryBaselineId(UUID.randomUUID()); // has a baseline → no NO_BASELINE
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(p));
        when(scheduleHealthService.getLatestHealth(PROJECT)).thenReturn(health(36.6, 1.0, 0.925, false));
        lenient().when(baselineService.listBaselines(PROJECT)).thenReturn(List.of());
        lenient().when(memory.activeFindings(any(), any(), any())).thenReturn(List.of());

        Activity a1 = work("Asphalt wearing course", 5.0);   // tier 4
        Activity a2 = work("Earthwork excavation", -8.0);     // tier 0, negative float
        Activity a3 = work("Concrete pier", 10.0);            // tier 2, no links → open-ended
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of(a1, a2, a3));
        // a1 -> a2 : asphalt(4) before earthwork(0) = construction-logic violation; both then open-ended too
        when(relationshipRepository.findByProjectId(PROJECT)).thenReturn(List.of(rel(a1.getId(), a2.getId())));

        GatherResult result = agent().gather(agentCtx());
        var types = result.candidates().stream().map(f -> f.findingType()).toList();

        assertThat(types).contains("BASELINE_HEALTH_SCORE", "OPEN_ENDED_ACTIVITIES",
                "NEGATIVE_FLOAT", "MISSING_MILESTONES", "CONSTRUCTION_LOGIC_VIOLATION",
                "SCHEDULE_COMPRESSION_OPPORTUNITY");
        assertThat(types).doesNotContain("NO_BASELINE", "SCHEDULE_NOT_RUN");
        assertThat(result.dataSnapshot().get("openEnded").asInt()).isPositive();
        assertThat(result.dataSnapshot().get("logicViolations").asInt()).isEqualTo(1);
    }

    @Test
    void nudgesWhenNoBaselineAndNoSchedule() throws Exception {
        Project p = new Project(); // primaryBaselineId null
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(p));
        when(scheduleHealthService.getLatestHealth(PROJECT)).thenReturn(null); // CPM never run
        when(baselineService.listBaselines(PROJECT)).thenReturn(List.of());
        when(activityRepository.findByProjectId(PROJECT)).thenReturn(List.of(work("Excavation", null)));
        when(relationshipRepository.findByProjectId(PROJECT)).thenReturn(List.of());

        GatherResult result = agent().gather(agentCtx());
        var types = result.candidates().stream().map(f -> f.findingType()).toList();

        assertThat(types).contains("NO_BASELINE", "SCHEDULE_NOT_RUN");
        assertThat(types).doesNotContain("BASELINE_HEALTH_SCORE"); // no schedule → no score
    }

    private static AgentRunContext agentCtx() {
        return new AgentRunContext(PROJECT, false, "MANUAL", null, true, null, null, null);
    }
}
