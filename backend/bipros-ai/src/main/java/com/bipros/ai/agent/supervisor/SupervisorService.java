package com.bipros.ai.agent.supervisor;

import com.bipros.ai.agent.core.Agent;
import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.domain.AgentInvestigation;
import com.bipros.ai.agent.domain.AgentInvestigationRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.orchestrator.AiOrchestrator;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.OpenAiCompatibleLlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Supervisor mode: answer a free-form question by driving the EXISTING {@link AiOrchestrator} ReAct
 * loop with a supervisor persona addendum. The {@code run_agent} and {@code read_agent_findings}
 * tools are auto-registered {@code Tool} beans, so they sit alongside the other ~79 tools with the
 * orchestrator's verification gates and SSE streaming — no new loop. Each investigation is persisted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisorService {

    private final AiOrchestrator orchestrator;
    private final OpenAiCompatibleLlmProvider llmProvider;
    private final LlmProviderConfigRepository llmProviderConfigRepository;
    private final AgentInvestigationRepository investigationRepository;
    private final AgentRegistry registry;
    private final ObjectMapper objectMapper;

    /** Stream the supervisor's answer as SSE frames (same envelope as {@code ChatController.chatStream}). */
    public Flux<ServerSentEvent<String>> investigate(String question, AiContext ctx) {
        LlmProviderConfig config = llmProviderConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
                .or(llmProviderConfigRepository::findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc)
                .orElseThrow(() -> new IllegalStateException(
                        "No active LLM provider configured. Add one via /v1/admin/llm-providers."));

        String framed = frame(question);
        StringBuilder answer = new StringBuilder();

        return orchestrator.handle(framed, null, List.of(), ctx, llmProvider, config)
                .doOnNext(event -> {
                    if ("done".equals(event.event()) && event.data().get("text") != null) {
                        answer.setLength(0);
                        answer.append(String.valueOf(event.data().get("text")));
                    } else if ("token".equals(event.event()) && answer.length() == 0
                            && event.data().get("delta") != null) {
                        answer.append(String.valueOf(event.data().get("delta")));
                    }
                })
                .map(event -> {
                    String json;
                    try {
                        json = objectMapper.writeValueAsString(event.data());
                    } catch (Exception e) {
                        json = "{}";
                    }
                    return ServerSentEvent.<String>builder().event(event.event()).data(json).build();
                })
                .doOnComplete(() -> persist(question, answer.toString(), ctx));
    }

    private void persist(String question, String answer, AiContext ctx) {
        try {
            AgentInvestigation inv = new AgentInvestigation();
            inv.setProjectId(ctx.projectId());
            inv.setQuestion(question);
            inv.setAnswer(answer.isBlank() ? null : answer);
            inv.setAskedBy(ctx.userId());
            investigationRepository.save(inv);
        } catch (Exception e) {
            log.warn("Failed to persist investigation: {}", e.getMessage());
        }
    }

    private String frame(String question) {
        String agents = registry.all().stream()
                .map(a -> a.key() + " (" + a.displayName() + ")")
                .collect(Collectors.joining(", "));
        return "You are the supervisor of a team of project-intelligence agents. You can call the "
                + "`run_agent` tool to run one of them now, and `read_agent_findings` to read what they "
                + "have already found, alongside your other tools. Available agents: " + agents + ". "
                + "Prefer read_agent_findings before run_agent when existing findings suffice. Answer the "
                + "user's question concisely, citing which agents/findings you relied on.\n\nQuestion: " + question;
    }
}
