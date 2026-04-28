package com.bipros.analytics.application.service;

import com.bipros.analytics.application.dto.AnalyticsAssistantRequest;
import com.bipros.analytics.application.dto.AnalyticsAssistantResponse;
import com.bipros.analytics.application.exception.LlmNotConfiguredException;
import com.bipros.analytics.application.exception.RateLimitExceededException;
import com.bipros.analytics.application.sql.SqlNotAllowedException;
import com.bipros.analytics.application.tool.AuthContext;
import com.bipros.analytics.application.tool.AuthContextResolver;
import com.bipros.analytics.application.tool.ToolDispatcher;
import com.bipros.analytics.application.tool.ToolRegistry;
import com.bipros.analytics.application.tool.ToolResult;
import com.bipros.analytics.domain.model.AnalyticsAuditLog;
import com.bipros.analytics.infrastructure.llm.CompletionRequest;
import com.bipros.analytics.infrastructure.llm.CompletionResponse;
import com.bipros.analytics.infrastructure.llm.Message;
import com.bipros.analytics.infrastructure.llm.TokenUsage;
import com.bipros.analytics.infrastructure.ratelimit.AnalyticsRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Phase-2 orchestrator. Implements the 12-step flow:
 * <ol>
 *   <li>HTTP request enters via {@code AnalyticsController}.</li>
 *   <li>Resolve current user via {@link AuthContextResolver}.</li>
 *   <li>Resolve LLM adapter via {@link LlmProviderResolver}; "not configured" → polite reply.</li>
 *   <li>Build conversation history (single-turn in v1).</li>
 *   <li>Build {@link CompletionRequest} with system prompt + tool registry digest.</li>
 *   <li>Call {@code adapter.complete(...)}.</li>
 *   <li>Parse the LLM's text response as JSON: tool-call or final answer.</li>
 *   <li>Validate tool name against the registry.</li>
 *   <li>Dispatch the tool against ClickHouse via {@link ToolDispatcher}.</li>
 *   <li>Format result + accumulate token/cost usage.</li>
 *   <li>Persist the audit log row via {@link AnalyticsAuditService}.</li>
 *   <li>Return {@link AnalyticsAssistantResponse} to the controller.</li>
 * </ol>
 *
 * <p>Tool calls are bounded by {@code bipros.analytics.assistant.max-tool-rounds}.
 * On {@link AccessDeniedException} from a tool, the orchestrator asks the LLM to
 * compose a polite refusal (with a templated fallback if that second call fails).
 */
@Service
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@Slf4j
public class AnalyticsAssistantService {

    private final AuthContextResolver authContextResolver;
    private final LlmProviderResolver llmProviderResolver;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final AnalyticsAuditService auditService;
    private final ObjectMapper objectMapper;
    /** Optional: only present when {@code bipros.analytics.rate-limit.enabled=true}. */
    private final AnalyticsRateLimiter rateLimiter;

    @Value("${bipros.analytics.assistant.max-tool-rounds:3}")
    private int maxToolRounds;
    @Value("${bipros.analytics.assistant.max-output-tokens:8000}")
    private int maxOutputTokens;
    @Value("${bipros.analytics.assistant.timeout-seconds:60}")
    private int timeoutSeconds;

    public AnalyticsAssistantService(AuthContextResolver authContextResolver,
                                     LlmProviderResolver llmProviderResolver,
                                     SystemPromptBuilder systemPromptBuilder,
                                     ToolRegistry toolRegistry,
                                     ToolDispatcher toolDispatcher,
                                     AnalyticsAuditService auditService,
                                     ObjectMapper objectMapper,
                                     @Autowired(required = false) AnalyticsRateLimiter rateLimiter) {
        this.authContextResolver = authContextResolver;
        this.llmProviderResolver = llmProviderResolver;
        this.systemPromptBuilder = systemPromptBuilder;
        this.toolRegistry = toolRegistry;
        this.toolDispatcher = toolDispatcher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    public AnalyticsAssistantResponse handle(AnalyticsAssistantRequest req) {
        long t0 = System.currentTimeMillis();
        AnalyticsAuditLog audit = AnalyticsAuditLog.builder()
                .queryText(req.queryText())
                .projectContextId(req.projectContext())
                .build();

        // Step 2 — resolve user.
        AuthContext auth;
        try {
            auth = authContextResolver.resolve();
        } catch (Exception e) {
            return finishError(audit, t0, AnalyticsAuditLog.Status.LLM_ERROR,
                    "AUTH_RESOLVE", "Could not resolve current user.");
        }
        audit.setUserId(auth.userId());

        // Step 2.5 — per-user rate limit. Done before LLM provider resolution so we
        // don't pay the decryption cost on rejected requests. The rejection still
        // writes a RATE_LIMITED audit row so the admin health page sees the spike.
        if (rateLimiter != null) {
            AnalyticsRateLimiter.Decision d = rateLimiter.check(auth.userId());
            if (!d.allowed()) {
                audit.setStatus(AnalyticsAuditLog.Status.RATE_LIMITED);
                audit.setErrorKind("RATE_LIMIT_EXCEEDED");
                audit.setLatencyMs((int) (System.currentTimeMillis() - t0));
                auditService.persist(audit);
                throw new RateLimitExceededException(d.retryAfterSeconds());
            }
        }

        // Step 3 — resolve LLM provider.
        LlmProviderResolver.ResolvedProvider provider;
        try {
            provider = llmProviderResolver.forUserDefault(auth.userId());
        } catch (LlmNotConfiguredException e) {
            return finishStatus(audit, t0, AnalyticsAuditLog.Status.NOT_CONFIGURED,
                    "Configure an LLM provider on /settings/llm-providers to enable the assistant.",
                    null);
        }
        audit.setLlmProvider(provider.config().getProvider().name());
        audit.setLlmModel(provider.model());

        // Steps 4-5 — build prompt.
        String systemPrompt = systemPromptBuilder.buildStaticPrefix()
                + systemPromptBuilder.buildDynamicSuffix(
                        auth.accessibleProjectIds() == null ? Integer.MAX_VALUE
                                : auth.accessibleProjectIds().size(),
                        auth.isAdmin());

        // Steps 6-9 — tool-calling loop.
        List<Message> conversation = new ArrayList<>();
        conversation.add(new Message(Message.Role.USER, req.queryText()));

        int tokensIn = 0;
        int tokensOut = 0;
        ToolResult finalResult = null;
        String finalNarrative = null;
        String lastToolName = null;

        for (int round = 0; round < maxToolRounds; round++) {
            CompletionRequest cr = new CompletionRequest(
                    systemPrompt, List.copyOf(conversation),
                    maxOutputTokens, Duration.ofSeconds(timeoutSeconds));

            CompletionResponse resp;
            try {
                resp = provider.adapter().complete(cr, provider.apiKey(),
                        provider.endpoint(), provider.model());
            } catch (Exception e) {
                log.error("LLM call failed", e);
                return finishError(audit, t0, AnalyticsAuditLog.Status.LLM_ERROR,
                        e.getClass().getSimpleName(),
                        "The LLM call failed. Please try again later.");
            }
            if (resp.tokens() != null) {
                tokensIn += resp.tokens().inputTokens();
                tokensOut += resp.tokens().outputTokens();
            }

            ParsedTurn turn = parseTurn(resp.text());
            if (turn.kind == TurnKind.ANSWER) {
                finalNarrative = turn.answer;
                break;
            }
            if (turn.kind == TurnKind.UNPARSEABLE) {
                conversation.add(new Message(Message.Role.ASSISTANT, resp.text() == null ? "" : resp.text()));
                conversation.add(new Message(Message.Role.USER,
                        "Your previous reply was not valid JSON. Reply with EXACTLY one JSON "
                                + "object: {\"tool\": ..., \"arguments\": ...} or {\"answer\": ...}."));
                continue;
            }

            // TurnKind.TOOL — validate, dispatch.
            String toolName = turn.toolName;
            lastToolName = toolName;
            audit.setToolCalled(toolName);
            try {
                audit.setToolArgsJson(objectMapper.writeValueAsString(turn.toolArgs));
            } catch (Exception ignore) { /* best effort */ }

            if (!toolRegistry.has(toolName)) {
                conversation.add(new Message(Message.Role.ASSISTANT, resp.text() == null ? "" : resp.text()));
                conversation.add(new Message(Message.Role.USER,
                        "Unknown tool: " + toolName + ". Pick a tool from the registry or finish "
                                + "with {\"answer\": ...}."));
                continue;
            }

            try {
                finalResult = toolDispatcher.dispatch(toolName, turn.toolArgs, auth);
                conversation.add(new Message(Message.Role.ASSISTANT, resp.text() == null ? "" : resp.text()));
                String resultJson = serializeToolResultForLlm(toolName, finalResult);
                conversation.add(new Message(Message.Role.USER, resultJson));
                // Loop continues: the LLM should respond with {"answer": ...} on the next turn.
            } catch (AccessDeniedException ade) {
                String refusal = composeRefusal(provider, ade.getMessage());
                audit.setStatus(AnalyticsAuditLog.Status.REFUSED);
                audit.setNarrativeReturned(refusal);
                audit.setLatencyMs((int) (System.currentTimeMillis() - t0));
                audit.setTokensInput(tokensIn);
                audit.setTokensOutput(tokensOut);
                auditService.persist(audit);
                return new AnalyticsAssistantResponse(refusal, toolName, List.of(), List.of(),
                        null, tokensIn, tokensOut, null, "REFUSED");
            } catch (SqlNotAllowedException sne) {
                String narrative = "I couldn't run that SQL: " + sne.getMessage();
                return finishStatus(audit, t0, AnalyticsAuditLog.Status.SQL_REJECTED,
                        narrative, toolName);
            } catch (Exception ex) {
                log.error("Tool {} failed", toolName, ex);
                return finishError(audit, t0, AnalyticsAuditLog.Status.TOOL_ERROR,
                        ex.getClass().getSimpleName(),
                        "Sorry, the " + toolName + " tool failed. Please try again or rephrase.");
            }
        }

        // Steps 10-12 — finalize.
        audit.setLatencyMs((int) (System.currentTimeMillis() - t0));
        audit.setTokensInput(tokensIn);
        audit.setTokensOutput(tokensOut);

        if (finalResult == null) {
            String narrative = finalNarrative != null && !finalNarrative.isBlank()
                    ? finalNarrative
                    : "I'm not sure how to answer that. Please rephrase or ask about a specific project.";
            audit.setStatus(AnalyticsAuditLog.Status.SUCCESS);
            audit.setNarrativeReturned(narrative);
            auditService.persist(audit);
            return new AnalyticsAssistantResponse(narrative, lastToolName, List.of(), List.of(),
                    null, tokensIn, tokensOut, null, "SUCCESS");
        }

        String narrative = (finalNarrative != null && !finalNarrative.isBlank())
                ? finalNarrative
                : finalResult.narrative();
        audit.setStatus(AnalyticsAuditLog.Status.SUCCESS);
        audit.setNarrativeReturned(narrative);
        audit.setSqlExecuted(finalResult.sqlExecuted());
        audit.setSqlHash(AnalyticsAuditService.sha256(finalResult.sqlExecuted()));
        audit.setResultRowCount(finalResult.rows().size());
        auditService.persist(audit);

        return new AnalyticsAssistantResponse(
                narrative,
                lastToolName,
                finalResult.columns(),
                finalResult.rows(),
                finalResult.sqlExecuted(),
                tokensIn, tokensOut, null, "SUCCESS");
    }

    /** Composes a polite refusal via a second LLM call. Falls back to a static template. */
    private String composeRefusal(LlmProviderResolver.ResolvedProvider provider, String detail) {
        String fallback = "You don't have access to the requested data. "
                + "Please contact your PMO if you need access.";
        try {
            CompletionRequest cr = new CompletionRequest(
                    "Compose a one-sentence polite refusal explaining that the user can't access "
                            + "the requested project data. Do not include any project IDs or "
                            + "system details. Output the sentence only — no JSON, no markdown.",
                    List.of(new Message(Message.Role.USER, "Refusal context: " + detail)),
                    200, Duration.ofSeconds(15));
            CompletionResponse r = provider.adapter().complete(cr, provider.apiKey(),
                    provider.endpoint(), provider.model());
            String t = r.text();
            return (t != null && !t.isBlank()) ? t.trim() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String serializeToolResultForLlm(String toolName, ToolResult tr) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tool_result_for", toolName,
                    "narrative", tr.narrative() == null ? "" : tr.narrative(),
                    "row_count", tr.rows().size(),
                    "rows_preview", previewRows(tr.rows())));
        } catch (Exception e) {
            return "{\"tool_result_for\": \"" + toolName + "\", \"row_count\": "
                    + tr.rows().size() + "}";
        }
    }

    /** First 20 rows; large results don't need to round-trip through the LLM. */
    private static List<Map<String, Object>> previewRows(List<Map<String, Object>> all) {
        return all.size() <= 20 ? all : all.subList(0, 20);
    }

    private ParsedTurn parseTurn(String text) {
        if (text == null || text.isBlank()) return ParsedTurn.unparseable();
        String trimmed = stripFences(text.trim());
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.has("answer")) {
                return ParsedTurn.answer(node.get("answer").asText());
            }
            if (node.has("tool")) {
                String name = node.get("tool").asText();
                JsonNode args = node.has("arguments") ? node.get("arguments") : null;
                return ParsedTurn.tool(name, args);
            }
            return ParsedTurn.unparseable();
        } catch (Exception e) {
            return ParsedTurn.unparseable();
        }
    }

    /** Strip a single-pair ```...``` markdown fence if present. */
    private static String stripFences(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }

    private AnalyticsAssistantResponse finishStatus(AnalyticsAuditLog audit, long t0,
            AnalyticsAuditLog.Status status, String narrative, String toolUsed) {
        audit.setStatus(status);
        audit.setNarrativeReturned(narrative);
        audit.setLatencyMs((int) (System.currentTimeMillis() - t0));
        auditService.persist(audit);
        return new AnalyticsAssistantResponse(narrative, toolUsed, List.of(), List.of(),
                null, null, null, null, status.name());
    }

    private AnalyticsAssistantResponse finishError(AnalyticsAuditLog audit, long t0,
            AnalyticsAuditLog.Status status, String errorKind, String narrative) {
        audit.setStatus(status);
        audit.setErrorKind(errorKind);
        audit.setNarrativeReturned(narrative);
        audit.setLatencyMs((int) (System.currentTimeMillis() - t0));
        auditService.persist(audit);
        return new AnalyticsAssistantResponse(narrative, null, List.of(), List.of(),
                null, null, null, null, status.name());
    }

    private enum TurnKind { TOOL, ANSWER, UNPARSEABLE }

    private record ParsedTurn(TurnKind kind, String toolName, JsonNode toolArgs, String answer) {
        static ParsedTurn unparseable() { return new ParsedTurn(TurnKind.UNPARSEABLE, null, null, null); }
        static ParsedTurn tool(String n, JsonNode a) { return new ParsedTurn(TurnKind.TOOL, n, a, null); }
        static ParsedTurn answer(String a) { return new ParsedTurn(TurnKind.ANSWER, null, null, a); }
    }
}
