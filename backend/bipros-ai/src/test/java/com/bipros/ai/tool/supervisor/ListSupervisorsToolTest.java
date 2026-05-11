package com.bipros.ai.tool.supervisor;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.query.SupervisorRosterCalculator;
import com.bipros.ai.query.SupervisorRosterCalculator.RankBy;
import com.bipros.ai.query.SupervisorRosterCalculator.StatusBreakdown;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRoster;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRow;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the access-control + argument-handling layer of {@link ListSupervisorsTool}.
 * Roster computation logic is exercised by {@link com.bipros.ai.query.SupervisorRosterCalculatorTest};
 * here the calculator is mocked.
 */
class ListSupervisorsToolTest {

  private SupervisorRosterCalculator calculator;
  private ObjectMapper objectMapper;
  private ListSupervisorsTool tool;

  @BeforeEach
  void setUp() {
    calculator = mock(SupervisorRosterCalculator.class);
    objectMapper = new ObjectMapper();
    tool = new ListSupervisorsTool(calculator, objectMapper);
  }

  @Test
  void toolMetadata() {
    assertThat(tool.name()).isEqualTo("list_supervisors");
    assertThat(tool.allowedRoles()).isEmpty();
    assertThat(tool.description())
        .contains("supervisor", "compare_supervisors")
        .containsAnyOf("rank", "ranking");
    JsonNode schema = tool.inputSchema();
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("properties").has("project_id")).isTrue();
    assertThat(schema.path("properties").has("include_eligible_pool")).isTrue();
    assertThat(schema.path("properties").has("rank_by")).isTrue();
    assertThat(schema.path("properties").has("limit")).isTrue();
  }

  @Test
  void executesUsingExplicitProjectIdAndReturnsRoster() {
    UUID projectId = UUID.randomUUID();
    AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", projectId);

    SupervisorRow row = sampleRow(UUID.randomUUID(), "RES-A", "Alice", 3);
    SupervisorRoster roster =
        new SupervisorRoster(projectId, "PRJ-1", "Demo", 1, List.of(row));
    when(calculator.compute(eq(projectId), anyBoolean(), org.mockito.ArgumentMatchers.any(), anyInt()))
        .thenReturn(roster);

    ObjectNode input = objectMapper.createObjectNode();
    input.put("project_id", projectId.toString());

    ToolResult result = tool.execute(input, ctx);

    assertThat(result.success()).isTrue();
    assertThat(result.data()).isNotNull();
    assertThat(result.data().path("project_id").asText()).isEqualTo(projectId.toString());
    assertThat(result.data().path("project_code").asText()).isEqualTo("PRJ-1");
    assertThat(result.data().path("total_supervisors").asInt()).isEqualTo(1);
    assertThat(result.data().path("rows").isArray()).isTrue();
    assertThat(result.data().path("rows").size()).isEqualTo(1);
    assertThat(result.data().path("rows").get(0).path("name").asText()).isEqualTo("Alice");
    assertThat(result.data().path("rows").get(0).path("activity_count").asInt()).isEqualTo(3);
    assertThat(result.data().path("ranked_by").asText()).isEqualTo("activity_count");
    // The calculator was called with the explicit project_id, not ctx.projectId()
    verify(calculator).compute(eq(projectId), eq(false), eq(RankBy.ACTIVITY_COUNT), anyInt());
  }

  @Test
  void fallsBackToContextProjectIdWhenArgumentMissing() {
    UUID projectId = UUID.randomUUID();
    AiContext ctx = AiContextFixtures.forProfile("PROJECT_MANAGER", projectId);

    SupervisorRoster roster = new SupervisorRoster(projectId, "PRJ-1", "Demo", 0, List.of());
    when(calculator.compute(eq(projectId), anyBoolean(), org.mockito.ArgumentMatchers.any(), anyInt()))
        .thenReturn(roster);

    ToolResult result = tool.execute(objectMapper.createObjectNode(), ctx);

    assertThat(result.success()).isTrue();
    verify(calculator).compute(eq(projectId), eq(false), eq(RankBy.ACTIVITY_COUNT), anyInt());
  }

  @Test
  void rejectsAdminPortfolioCallWithoutProjectIdWithHelpfulMessage() {
    AiContext ctx = new AiContext(
        UUID.randomUUID(),
        null,
        "general",
        "ADMIN",
        "ADMIN",
        null);

    ToolResult result = tool.execute(objectMapper.createObjectNode(), ctx);

    assertThat(result.success()).isFalse();
    assertThat(result.error())
        .contains("project_id")
        .contains("list_projects");
    verify(calculator, never())
        .compute(org.mockito.ArgumentMatchers.any(), anyBoolean(), org.mockito.ArgumentMatchers.any(), anyInt());
  }

  @Test
  void rejectsNonAdminWithoutProjectInScope() {
    AiContext ctx = new AiContext(
        UUID.randomUUID(),
        null,
        "general",
        "PROJECT_MANAGER",
        "PROJECT_MANAGER",
        List.of());

    ToolResult result = tool.execute(objectMapper.createObjectNode(), ctx);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("project");
    verify(calculator, never())
        .compute(org.mockito.ArgumentMatchers.any(), anyBoolean(), org.mockito.ArgumentMatchers.any(), anyInt());
  }

  @Test
  void deniesAccessWhenProjectNotInScope() {
    UUID projectId = UUID.randomUUID();
    UUID otherProjectId = UUID.randomUUID();
    // Caller's scope only includes a different project.
    AiContext ctx = new AiContext(
        UUID.randomUUID(),
        otherProjectId,
        "general",
        "PROJECT_MANAGER",
        "PROJECT_MANAGER",
        List.of(otherProjectId));

    ObjectNode input = objectMapper.createObjectNode();
    input.put("project_id", projectId.toString());

    assertThatThrownBy(() -> tool.execute(input, ctx))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  // ---- helpers ----

  private static SupervisorRow sampleRow(UUID id, String code, String name, int activityCount) {
    return new SupervisorRow(
        id,
        code,
        name,
        "Foreman",
        activityCount,
        new StatusBreakdown(1, 1, 1, 0),
        new BigDecimal("50.00"),
        new BigDecimal("100"),
        new BigDecimal("120"),
        new BigDecimal("20"),
        new BigDecimal("1.10"),
        new BigDecimal("0.95"),
        false);
  }
}
