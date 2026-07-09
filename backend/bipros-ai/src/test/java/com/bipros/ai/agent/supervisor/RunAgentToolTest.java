package com.bipros.ai.agent.supervisor;

import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.domain.AgentRunStatus;
import com.bipros.ai.agent.pipeline.AgentRunService;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunAgentToolTest {

    @Mock
    private AgentRunService agentRunService;

    @Mock
    private AgentFindingRepository agentFindingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RunAgentTool tool;

    @BeforeEach
    void setUp() {
        tool = new RunAgentTool(agentRunService, agentFindingRepository, objectMapper);
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {

        @Test
        @DisplayName("name() returns 'run_agent' and isReadOnly() is false")
        void nameAndReadOnly() {
            assertThat(tool.name()).isEqualTo("run_agent");
            assertThat(tool.isReadOnly()).isFalse();
        }

        @Test
        @DisplayName("inputSchema declares agentKey (required) and projectId (optional, uuid)")
        void inputSchema() {
            JsonNode schema = tool.inputSchema();
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("properties").has("agentKey")).isTrue();
            assertThat(schema.path("properties").path("agentKey").path("type").asText()).isEqualTo("string");
            assertThat(schema.path("properties").has("projectId")).isTrue();
            assertThat(schema.path("properties").path("projectId").path("type").asText()).isEqualTo("string");
            assertThat(schema.path("properties").path("projectId").path("format").asText()).isEqualTo("uuid");
            assertThat(schema.path("required").isArray()).isTrue();
            assertThat(schema.path("required").get(0).asText()).isEqualTo("agentKey");
        }

        @Test
        @DisplayName("description mentions a monitoring agent and findings")
        void description() {
            assertThat(tool.description()).contains("monitoring agent").contains("findings");
        }
    }

    @Nested
    @DisplayName("execute: valid agent run")
    class ValidRun {

        @Test
        @DisplayName("runs the agent with AgentRunContext.manual(projectId, userId) and returns findings")
        void runsAgentAndReturnsFindings() {
            UUID projectId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            AiContext ctx = new AiContext(userId, projectId, "general",
                    "PROJECT_MANAGER", "PROJECT_MANAGER", List.of(projectId));

            AgentRun run = mock(AgentRun.class);
            when(run.getId()).thenReturn(runId);
            when(run.getStatus()).thenReturn(AgentRunStatus.SUCCEEDED);
            when(agentRunService.runSingle(eq("capacity_utilisation"), any(AgentRunContext.class)))
                    .thenReturn(run);

            AgentFinding f1 = mock(AgentFinding.class);
            when(f1.getFindingType()).thenReturn("OVERUTILISATION");
            when(f1.getSeverity()).thenReturn(Severity.HIGH);
            when(f1.getConfidence()).thenReturn(0.9);
            when(f1.getTitle()).thenReturn("Resource X overutilised");
            when(f1.getSubjectRef()).thenReturn("resource:" + UUID.randomUUID());

            AgentFinding f2 = mock(AgentFinding.class);
            when(f2.getFindingType()).thenReturn("UNDERUTILISATION");
            when(f2.getSeverity()).thenReturn(Severity.MEDIUM);
            when(f2.getConfidence()).thenReturn(0.7);
            when(f2.getTitle()).thenReturn("Resource Y underutilised");
            when(f2.getSubjectRef()).thenReturn(null);

            when(agentFindingRepository.findByRunId(runId)).thenReturn(List.of(f1, f2));

            ObjectNode input = objectMapper.createObjectNode();
            input.put("agentKey", "capacity_utilisation");
            input.put("projectId", projectId.toString());

            ToolResult result = tool.execute(input, ctx);

            assertThat(result.success()).isTrue();
            assertThat(result.summary()).contains("capacity_utilisation").contains("2 findings");
            assertThat(result.summary()).contains("1 HIGH").contains("1 MEDIUM");
            assertThat(result.data()).isNotNull();
            assertThat(result.data().path("runId").asText()).isEqualTo(runId.toString());
            assertThat(result.data().path("agentKey").asText()).isEqualTo("capacity_utilisation");
            assertThat(result.data().path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(result.data().path("findingsCount").asInt()).isEqualTo(2);
            assertThat(result.data().path("findings").isArray()).isTrue();
            assertThat(result.data().path("findings").size()).isEqualTo(2);

            JsonNode f0 = result.data().path("findings").get(0);
            assertThat(f0.path("findingType").asText()).isEqualTo("OVERUTILISATION");
            assertThat(f0.path("severity").asText()).isEqualTo("HIGH");
            assertThat(f0.path("confidence").asDouble()).isCloseTo(0.9, within(0.0001));
            assertThat(f0.path("title").asText()).isEqualTo("Resource X overutilised");
            assertThat(f0.path("subjectRef").asText()).startsWith("resource:");

            ArgumentCaptor<AgentRunContext> captor = ArgumentCaptor.forClass(AgentRunContext.class);
            verify(agentRunService).runSingle(eq("capacity_utilisation"), captor.capture());
            AgentRunContext runCtx = captor.getValue();
            assertThat(runCtx.projectId()).isEqualTo(projectId);
            assertThat(runCtx.requestedBy()).isEqualTo(userId);
            assertThat(runCtx.triggerType()).isEqualTo("MANUAL");
            assertThat(runCtx.portfolio()).isFalse();
        }
    }

    @Nested
    @DisplayName("execute: input validation")
    class Validation {

        @Test
        @DisplayName("blank agentKey returns error and does not invoke the agent")
        void blankAgentKeyReturnsError() {
            UUID projectId = UUID.randomUUID();
            AiContext ctx = new AiContext(UUID.randomUUID(), projectId, "general",
                    "PROJECT_MANAGER", "PROJECT_MANAGER", List.of(projectId));

            ObjectNode input = objectMapper.createObjectNode();
            input.put("agentKey", "   ");

            ToolResult result = tool.execute(input, ctx);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("agentKey");
            verifyNoInteractions(agentRunService);
        }

        @Test
        @DisplayName("no projectId in input or context returns error")
        void missingProjectIdEverywhereReturnsError() {
            AiContext ctx = new AiContext(UUID.randomUUID(), null, "general",
                    "PROJECT_MANAGER", "PROJECT_MANAGER", List.of());

            ObjectNode input = objectMapper.createObjectNode();
            input.put("agentKey", "capacity_utilisation");

            ToolResult result = tool.execute(input, ctx);

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("projectId");
            verifyNoInteractions(agentRunService);
        }
    }

    @Nested
    @DisplayName("execute: access control")
    class AccessControl {

        @Test
        @DisplayName("non-admin with projectId not in scope is denied (even when ctx.projectId() is null)")
        void deniesWhenProjectNotInScope() {
            UUID projectId = UUID.randomUUID();
            UUID otherProject = UUID.randomUUID();
            AiContext ctx = new AiContext(UUID.randomUUID(), null, "general",
                    "PROJECT_MANAGER", "PROJECT_MANAGER", List.of(otherProject));

            ObjectNode input = objectMapper.createObjectNode();
            input.put("agentKey", "capacity_utilisation");
            input.put("projectId", projectId.toString());

            assertThatThrownBy(() -> tool.execute(input, ctx))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(agentRunService);
        }

        @Test
        @DisplayName("ADMIN bypasses scope check and runs the agent")
        void adminBypassesScopeCheck() {
            UUID projectId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            AiContext ctx = new AiContext(UUID.randomUUID(), null, "general",
                    "ADMIN", "ADMIN", List.of());

            AgentRun run = mock(AgentRun.class);
            when(run.getId()).thenReturn(runId);
            when(run.getStatus()).thenReturn(AgentRunStatus.SUCCEEDED);
            when(agentRunService.runSingle(eq("forecasting"), any(AgentRunContext.class)))
                    .thenReturn(run);
            when(agentFindingRepository.findByRunId(runId)).thenReturn(List.of());

            ObjectNode input = objectMapper.createObjectNode();
            input.put("agentKey", "forecasting");
            input.put("projectId", projectId.toString());

            ToolResult result = tool.execute(input, ctx);

            assertThat(result.success()).isTrue();
            assertThat(result.summary()).contains("forecasting").contains("0 findings");
            assertThat(result.data().path("findingsCount").asInt()).isEqualTo(0);
            verify(agentRunService).runSingle(eq("forecasting"), any(AgentRunContext.class));
        }
    }
}
