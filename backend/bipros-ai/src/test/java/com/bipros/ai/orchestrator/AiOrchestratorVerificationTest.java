package com.bipros.ai.orchestrator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.persona.RolePersonaProvider;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.tool.DataGraphCatalog;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolRegistry;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the orchestrator's mandatory verification pass: after the model
 * drafts a data-backed answer (any round in which a tool was called), we
 * inject a verifier system message and force one more round of tool calls
 * before the user sees anything.
 */
class AiOrchestratorVerificationTest {

    private ToolRegistry toolRegistry;
    private DataGraphCatalog catalog;
    private RolePersonaProvider personaProvider;
    private ProjectRepository projectRepository;
    private LlmProvider provider;
    private LlmProviderConfig providerConfig;
    private AiOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        toolRegistry = Mockito.mock(ToolRegistry.class);
        catalog = Mockito.mock(DataGraphCatalog.class);
        personaProvider = Mockito.mock(RolePersonaProvider.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        provider = Mockito.mock(LlmProvider.class);
        providerConfig = new LlmProviderConfig();
        providerConfig.setMaxTokens(2048);
        providerConfig.setTemperature(new BigDecimal("0.2"));
        providerConfig.setTimeoutMs(30000);

        when(catalog.compact()).thenReturn("(graph)");
        when(personaProvider.forProfile(any())).thenReturn(null);
        when(toolRegistry.toolsForProfile(anyString())).thenReturn(List.of());

        orchestrator = new AiOrchestrator(toolRegistry, catalog, personaProvider,
                projectRepository, 12, 10);
    }

    private static AiContext ctx() {
        return new AiContext(UUID.randomUUID(), null, "general", "ADMIN", "ADMIN",
                Collections.emptyList());
    }

    /** Helper: stub a fake tool ("count_activities") that returns a canned summary. */
    private void stubFakeTool(String summary) {
        Tool fakeTool = Mockito.mock(Tool.class);
        when(fakeTool.name()).thenReturn("count_activities");
        when(fakeTool.execute(any(), any())).thenReturn(ToolResult.ok(summary));
        when(toolRegistry.get("count_activities")).thenReturn(fakeTool);
        when(toolRegistry.isAllowed(any(), any())).thenReturn(true);
    }

    private static LlmProvider.ChatResponse toolCallResponse(String toolName) {
        JsonNode args = MissingNode.getInstance();
        return new LlmProvider.ChatResponse(
                "", List.of(new LlmProvider.ToolCall("call_1", toolName, args)),
                null, "test-model");
    }

    private static LlmProvider.ChatResponse textResponse(String text) {
        return new LlmProvider.ChatResponse(text, null, null, "test-model");
    }

    @Test
    void verificationPassRunsAfterToolBackedDraftAnswer() {
        stubFakeTool("2 activities");
        // Sequence:
        //   round 0 → model emits tool call → tool returns "2 activities"
        //   round 1 → model emits draft "ROAD-001 has 2 activities."
        //          → orchestrator injects verification, emits "verifying"
        //   round 2 → model emits tool call (re-verifying) → tool returns "2 activities"
        //   round 3 → model emits the final verified answer.
        when(provider.chatCompletion(any())).thenReturn(
                toolCallResponse("count_activities"),
                textResponse("ROAD-001 has 2 activities."),
                toolCallResponse("count_activities"),
                textResponse("ROAD-001 has 2 activities.")
        );

        List<AiOrchestrator.ChatEvent> events = new ArrayList<>();
        orchestrator.handle("how many activities are there in ROAD-001",
                        null, List.of(), ctx(), provider, providerConfig)
                .doOnNext(events::add)
                .blockLast();

        // The model was called 4 times (initial draft + verification round trip).
        verify(provider, times(4)).chatCompletion(any());

        // The orchestrator emitted a "verifying" event before the final answer.
        long verifyingEvents = events.stream().filter(e -> "verifying".equals(e.event())).count();
        assertEquals(1, verifyingEvents,
                "Exactly one 'verifying' event should fire after a tool-backed draft");

        // Final answer carries verified: true.
        AiOrchestrator.ChatEvent finalEvent = events.stream()
                .filter(e -> "final_answer".equals(e.event()))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, finalEvent.data().get("verified"),
                "final_answer must carry verified=true after a verification pass");
        assertTrue(((String) finalEvent.data().get("text")).contains("2 activities"));
    }

    @Test
    void chitChatAnswerWithNoToolsSkipsVerification() {
        // No tool stubbed; the model produces a direct text answer in round 0.
        when(provider.chatCompletion(any())).thenReturn(
                textResponse("Hi! I can help with project planning, cost, schedule, and risk.")
        );

        List<AiOrchestrator.ChatEvent> events = new ArrayList<>();
        orchestrator.handle("hello", null, List.of(), ctx(), provider, providerConfig)
                .doOnNext(events::add)
                .blockLast();

        // Exactly one LLM call: no verification triggered for tool-less chit-chat.
        verify(provider, times(1)).chatCompletion(any());

        // No 'verifying' event for chit-chat answers.
        long verifyingEvents = events.stream().filter(e -> "verifying".equals(e.event())).count();
        assertEquals(0, verifyingEvents);

        AiOrchestrator.ChatEvent finalEvent = events.stream()
                .filter(e -> "final_answer".equals(e.event()))
                .findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, finalEvent.data().get("verified"),
                "Chit-chat answers carry verified=false");
    }

    @Test
    void verificationDoesNotRunTwice() {
        stubFakeTool("2 activities");
        // After the first verification pass, the model produces a second draft
        // (e.g. it changed its mind). The orchestrator must NOT inject a second
        // verification — only one pass per request.
        when(provider.chatCompletion(any())).thenReturn(
                toolCallResponse("count_activities"),                           // draft round → tool
                textResponse("Draft: 252 activities."),                         // first draft (wrong)
                toolCallResponse("count_activities"),                           // verifier re-calls tool
                textResponse("Verified: 2 activities (earlier count was wrong).") // verified final
        );

        List<AiOrchestrator.ChatEvent> events = new ArrayList<>();
        orchestrator.handle("how many activities in ROAD-001", null, List.of(),
                        ctx(), provider, providerConfig)
                .doOnNext(events::add)
                .blockLast();

        long verifyingEvents = events.stream().filter(e -> "verifying".equals(e.event())).count();
        assertEquals(1, verifyingEvents, "Verification must fire at most once per request");

        AiOrchestrator.ChatEvent finalEvent = events.stream()
                .filter(e -> "final_answer".equals(e.event()))
                .findFirst().orElseThrow();
        assertTrue(((String) finalEvent.data().get("text")).contains("2 activities"));
        assertEquals(Boolean.TRUE, finalEvent.data().get("verified"));
    }

    @Test
    void verifyingEventIsEmittedBeforeFinalAnswer() {
        stubFakeTool("2 activities");
        when(provider.chatCompletion(any())).thenReturn(
                toolCallResponse("count_activities"),
                textResponse("2 activities."),
                textResponse("2 activities.")   // verifier confirms without tool call
                                                // (allowed only if model judges no claim)
        );

        List<AiOrchestrator.ChatEvent> events = new ArrayList<>();
        orchestrator.handle("how many activities", null, List.of(), ctx(),
                        provider, providerConfig)
                .doOnNext(events::add)
                .blockLast();

        // Ordering invariant: 'verifying' precedes 'final_answer'.
        int verifyingIdx = -1, finalIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if ("verifying".equals(events.get(i).event()) && verifyingIdx < 0) verifyingIdx = i;
            if ("final_answer".equals(events.get(i).event()) && finalIdx < 0) finalIdx = i;
        }
        assertTrue(verifyingIdx >= 0, "verifying event must be emitted");
        assertTrue(finalIdx >= 0, "final_answer must be emitted");
        assertTrue(verifyingIdx < finalIdx,
                "verifying must come before final_answer in the event stream");
    }
}
