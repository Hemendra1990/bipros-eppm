package com.bipros.ai.tool.dpr;

import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListIssuesTool — rollups + scope guard")
class ListIssuesToolTest {

    @Mock private DprIssueRepository issueRepository;
    @Mock private ActivityRepository activityRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID projectId = UUID.randomUUID();
    private final UUID supA = UUID.randomUUID();
    private final UUID supB = UUID.randomUUID();
    private ListIssuesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListIssuesTool(issueRepository, activityRepository, mapper);
    }

    @Test
    @DisplayName("groups by supervisor / activity / category with open counts; excludes CANCELLED by default")
    void rollupShape() {
        when(issueRepository.findByProjectIdOrderByOpenedAtDesc(projectId)).thenReturn(List.of(
                issue("Bench Cutting", supA, "Mohd", IssueCategory.MATERIAL_SHORTAGE, IssueSeverity.HIGH, IssueStatus.OPEN),
                issue("Bench Cutting", supA, "Mohd", IssueCategory.WEATHER, IssueSeverity.LOW, IssueStatus.RESOLVED),
                issue("Asphalting", supB, "Ravi", IssueCategory.MATERIAL_SHORTAGE, IssueSeverity.CRITICAL, IssueStatus.OPEN),
                issue("Asphalting", supB, "Ravi", IssueCategory.OTHER, IssueSeverity.LOW, IssueStatus.CANCELLED)));

        AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", projectId);
        ToolResult result = tool.execute(mapper.createObjectNode(), ctx);

        assertThat(result.success()).isTrue();
        JsonNode data = result.data();
        // CANCELLED excluded by default → 3 visible
        assertThat(data.get("matched").asInt()).isEqualTo(3);
        assertThat(data.get("open_count").asInt()).isEqualTo(2);

        // Activity rollup: 2 activities
        JsonNode byActivity = data.get("by_activity");
        assertThat(byActivity).hasSize(2);

        // Supervisor rollup: 2 supervisors, each with 1 open + 1 total (Mohd) and (Ravi)
        JsonNode bySup = data.get("by_supervisor");
        assertThat(bySup).hasSize(2);

        // Category rollup: MATERIAL_SHORTAGE wins (2), then WEATHER (1)
        JsonNode byCat = data.get("by_category");
        String top = byCat.get(0).get("category").asText();
        assertThat(top).isEqualTo("MATERIAL_SHORTAGE");
    }

    @Test
    @DisplayName("include_cancelled=true brings cancelled rows into rollups")
    void includeCancelled() {
        when(issueRepository.findByProjectIdOrderByOpenedAtDesc(projectId)).thenReturn(List.of(
                issue("Asphalting", supB, "Ravi", IssueCategory.OTHER, IssueSeverity.LOW, IssueStatus.CANCELLED),
                issue("Asphalting", supB, "Ravi", IssueCategory.OTHER, IssueSeverity.LOW, IssueStatus.OPEN)));

        var input = mapper.createObjectNode();
        input.put("include_cancelled", true);
        ToolResult result = tool.execute(input, AiContextFixtures.forProfile("PROJECT_MANAGER", projectId));

        assertThat(result.data().get("matched").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("no project in scope ⇒ structured error")
    void noProjectScopeError() {
        AiContext noProject = new AiContext(UUID.randomUUID(), null, "general",
                "PROJECT_MANAGER", "PROJECT_MANAGER", List.of());
        ToolResult result = tool.execute(mapper.createObjectNode(), noProject);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("project in scope");
    }

    private DprIssue issue(String activity, UUID supId, String supName,
                           IssueCategory cat, IssueSeverity sev, IssueStatus status) {
        DprIssue i = DprIssue.builder()
                .dprId(UUID.randomUUID())
                .projectId(projectId)
                .activityId(UUID.randomUUID())
                .activityName(activity)
                .supervisorResourceId(supId)
                .supervisorName(supName)
                .assignedToResourceId(supId)
                .assignedToName(supName)
                .reportDate(LocalDate.now())
                .category(cat)
                .severity(sev)
                .status(status)
                .title("issue")
                .openedAt(Instant.now())
                .build();
        i.setId(UUID.randomUUID());
        return i;
    }
}
