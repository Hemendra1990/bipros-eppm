package com.bipros.ai.wbs;

import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.wbs.dto.WbsAiNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Map-reduce orchestration for very large documents.
 *
 * <p>Big DPRs (50+ pages, 100K+ characters) hit two single-shot limits at once:
 * the model's working-memory budget thins out across pages, and the JSON
 * output gets truncated mid-tree. Splitting the work into focused per-phase
 * calls yields tighter per-call prompts, parallel execution, and per-phase
 * retry — one transient 5xx no longer torpedoes the whole document.
 *
 * <p>Algorithm:
 * <ol>
 *   <li><b>MAP</b>: a single cheap call that asks the model to list the
 *       top-level WBS phases described in the document
 *       ({@code [{phaseCode, phaseName, summary}]}).</li>
 *   <li><b>REDUCE</b>: in parallel (Semaphore-bounded), one call per phase
 *       to extract its sub-packages and activities. Each call attaches the
 *       same document but is told "focus only on phase X".</li>
 *   <li><b>MERGE</b>: build a single tree where each top-level phase is the
 *       MAP output and its children are the REDUCE output. Failures of any
 *       single phase yield a placeholder node rather than aborting the run.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MapReduceWbsExtractor {

    private static final int MAX_PARALLEL = 4;
    private static final int MAX_RETRIES = 3;

    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final ObjectMapper objectMapper;

    /**
     * @param progress Optional callback invoked as each phase completes
     *                 with cumulative percent (30 → 80 over the REDUCE stage).
     *                 Null is safe.
     */
    public List<WbsAiNode> extract(LlmProviderConfig config,
                                    String mapPrompt,
                                    String reducePromptTemplate,
                                    List<LlmProvider.Message> baseMessages,
                                    List<LlmProvider.DocumentInput> documents,
                                    String reasoningEffort,
                                    IntConsumer progress) {

        List<MapPhase> phases = runMap(config, mapPrompt, baseMessages, documents, reasoningEffort);
        log.info("Map stage identified {} phase(s)", phases.size());
        if (phases.isEmpty()) {
            return List.of();
        }

        Semaphore gate = new Semaphore(MAX_PARALLEL);
        ScheduledExecutorService backoffPool = Executors.newScheduledThreadPool(2,
                r -> {
                    Thread t = new Thread(r, "wbs-mr-backoff");
                    t.setDaemon(true);
                    return t;
                });
        try {
            List<CompletableFuture<WbsAiNode>> futures = new ArrayList<>(phases.size());
            int total = phases.size();
            int[] done = {0};
            for (MapPhase phase : phases) {
                CompletableFuture<WbsAiNode> f = CompletableFuture.supplyAsync(() -> {
                    try {
                        gate.acquire();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return placeholder(phase, "interrupted");
                    }
                    try {
                        return runReduceWithRetry(config, reducePromptTemplate, baseMessages,
                                documents, reasoningEffort, phase, backoffPool);
                    } finally {
                        gate.release();
                        synchronized (done) {
                            done[0]++;
                            if (progress != null) {
                                int pct = 30 + (int) Math.round(((double) done[0] / total) * 50.0);
                                progress.accept(pct);
                            }
                        }
                    }
                });
                futures.add(f);
            }
            List<WbsAiNode> results = new ArrayList<>(futures.size());
            for (CompletableFuture<WbsAiNode> f : futures) {
                try {
                    results.add(f.get(/* timeout handled inside provider */));
                } catch (ExecutionException | InterruptedException e) {
                    log.warn("Phase future failed: {}", e.getMessage());
                    results.add(placeholder(new MapPhase("?", "Unknown", ""), e.getMessage()));
                }
            }
            return results;
        } finally {
            backoffPool.shutdown();
        }
    }

    private List<MapPhase> runMap(LlmProviderConfig config,
                                   String mapPrompt,
                                   List<LlmProvider.Message> baseMessages,
                                   List<LlmProvider.DocumentInput> documents,
                                   String reasoningEffort) {
        List<LlmProvider.Message> msgs = new ArrayList<>(baseMessages);
        msgs.add(new LlmProvider.Message("user", mapPrompt));

        LlmProvider.ChatRequest req = new LlmProvider.ChatRequest(
                msgs, null, config.getMaxTokens(),
                config.getTemperature() == null ? null : config.getTemperature().doubleValue(),
                (long) config.getTimeoutMs(),
                buildMapResponseSchema(),
                documents, reasoningEffort);
        LlmProvider.ChatResponse resp = openAiCompatibleProvider.chat(config, req);
        try {
            JsonNode root = objectMapper.readTree(resp.content());
            JsonNode arr = root.has("phases") ? root.get("phases") : root;
            List<MapPhase> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String code = n.path("phaseCode").asText("");
                    String name = n.path("phaseName").asText("");
                    String summary = n.path("summary").asText("");
                    if (!code.isBlank() && !name.isBlank()) {
                        out.add(new MapPhase(code, name, summary));
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.error("Map stage parse failed", e);
            return List.of();
        }
    }

    private WbsAiNode runReduceWithRetry(LlmProviderConfig config,
                                          String reducePromptTemplate,
                                          List<LlmProvider.Message> baseMessages,
                                          List<LlmProvider.DocumentInput> documents,
                                          String reasoningEffort,
                                          MapPhase phase,
                                          ScheduledExecutorService backoff) {
        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return runReduceOnce(config, reducePromptTemplate, baseMessages,
                        documents, reasoningEffort, phase);
            } catch (Exception e) {
                last = e;
                if (attempt == MAX_RETRIES) break;
                long delay = (long) (Math.pow(2, attempt - 1) * 1000) + ThreadLocalRandom.current().nextInt(500);
                log.warn("Reduce phase '{}' attempt {} failed: {}; retrying in {} ms",
                        phase.code, attempt, e.getMessage(), delay);
                try { TimeUnit.MILLISECONDS.sleep(delay); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.warn("Phase '{}' giving up after {} attempts; emitting placeholder", phase.code, MAX_RETRIES);
        return placeholder(phase, last == null ? "unknown" : last.getMessage());
    }

    private WbsAiNode runReduceOnce(LlmProviderConfig config,
                                     String reducePromptTemplate,
                                     List<LlmProvider.Message> baseMessages,
                                     List<LlmProvider.DocumentInput> documents,
                                     String reasoningEffort,
                                     MapPhase phase) {
        String prompt = reducePromptTemplate
                .replace("{{phaseCode}}", phase.code)
                .replace("{{phaseName}}", phase.name)
                .replace("{{phaseSummary}}", phase.summary);
        List<LlmProvider.Message> msgs = new ArrayList<>(baseMessages);
        msgs.add(new LlmProvider.Message("user", prompt));

        LlmProvider.ChatRequest req = new LlmProvider.ChatRequest(
                msgs, null, config.getMaxTokens(),
                config.getTemperature() == null ? null : config.getTemperature().doubleValue(),
                (long) config.getTimeoutMs(),
                buildReduceResponseSchema(),
                documents, reasoningEffort);
        LlmProvider.ChatResponse resp = openAiCompatibleProvider.chat(config, req);
        try {
            JsonNode root = objectMapper.readTree(resp.content());
            JsonNode nodeJson = root.has("node") ? root.get("node") : root;
            return objectMapper.convertValue(nodeJson, WbsAiNode.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse reduce response for phase " + phase.code, e);
        }
    }

    private static WbsAiNode placeholder(MapPhase phase, String reason) {
        return new WbsAiNode(phase.code, phase.name,
                "[Failed to extract sub-packages for this phase: " + reason + "]",
                null, List.of());
    }

    /** {@code {phases:[{phaseCode, phaseName, summary}]}}. */
    private JsonNode buildMapResponseSchema() {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "json_schema");
        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "wbs_phases");
        jsonSchema.put("strict", true);
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        ObjectNode phasesProp = objectMapper.createObjectNode();
        phasesProp.put("type", "array");
        ObjectNode itemSchema = objectMapper.createObjectNode();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        ObjectNode itemProps = objectMapper.createObjectNode();
        itemProps.set("phaseCode", stringProp());
        itemProps.set("phaseName", stringProp());
        itemProps.set("summary", stringProp());
        itemSchema.set("properties", itemProps);
        ArrayNode itemReq = objectMapper.createArrayNode();
        itemReq.add("phaseCode"); itemReq.add("phaseName"); itemReq.add("summary");
        itemSchema.set("required", itemReq);
        phasesProp.set("items", itemSchema);
        props.set("phases", phasesProp);
        schema.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("phases");
        schema.set("required", req);
        jsonSchema.set("schema", schema);
        wrapper.set("json_schema", jsonSchema);
        return wrapper;
    }

    /** Per-phase reduce: returns one wbs_node tree. Reuses the WBS node shape. */
    private JsonNode buildReduceResponseSchema() {
        // Reuse the existing tree definition by referencing a wbs_node identical
        // to WbsAiGenerationService.buildWbsNodeDef. Inline here to keep the
        // map-reduce module independent of the generation service's private API.
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "json_schema");
        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "wbs_phase_node");
        jsonSchema.put("strict", true);
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        ObjectNode nodeRef = objectMapper.createObjectNode();
        nodeRef.put("$ref", "#/$defs/wbs_node");
        props.set("node", nodeRef);
        schema.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("node");
        schema.set("required", req);
        ObjectNode defs = objectMapper.createObjectNode();
        defs.set("wbs_node", buildWbsNodeDef());
        schema.set("$defs", defs);
        jsonSchema.set("schema", schema);
        wrapper.set("json_schema", jsonSchema);
        return wrapper;
    }

    private ObjectNode buildWbsNodeDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        props.set("code", stringProp());
        props.set("name", stringProp());
        props.set("description", nullableString());
        props.set("parentCode", nullableString());
        ObjectNode childrenProp = objectMapper.createObjectNode();
        childrenProp.put("type", "array");
        ObjectNode childRef = objectMapper.createObjectNode();
        childRef.put("$ref", "#/$defs/wbs_node");
        childrenProp.set("items", childRef);
        props.set("children", childrenProp);
        node.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("code"); req.add("name"); req.add("description");
        req.add("parentCode"); req.add("children");
        node.set("required", req);
        return node;
    }

    private ObjectNode stringProp() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type", "string");
        return n;
    }

    private ObjectNode nullableString() {
        ObjectNode n = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("string"); types.add("null");
        n.set("type", types);
        return n;
    }

    /** Internal: a single phase identified during the MAP stage. */
    private record MapPhase(String code, String name, String summary) {
    }
}
