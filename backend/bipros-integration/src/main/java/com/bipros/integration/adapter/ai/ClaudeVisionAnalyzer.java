package com.bipros.integration.adapter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;

/**
 * Claude vision-based construction-progress analyzer.
 * <p>
 * Calls Anthropic's messages API directly over HTTP. Model default is
 * {@code claude-sonnet-4-6} (matches the repo's {@code CLAUDE.md} preference).
 * The prompt is structured so the model returns a single JSON object with the
 * four numeric fields + free-text rationale. We parse the first JSON object
 * found in the response text — defensive against models that occasionally wrap
 * output in prose despite a strict instruction.
 * <p>
 * Cost tracking: Anthropic returns {@code usage.input_tokens} and
 * {@code usage.output_tokens}; we compute approximate USD micro-cents using
 * Sonnet 4.6's public rates ($3/M input, $15/M output as of 2026-04). If the
 * prices change, update {@link #INPUT_COST_PER_MTOK} / {@link #OUTPUT_COST_PER_MTOK}.
 * <p>
 * Active when {@code bipros.ai.progress-analyzer.provider=claude-vision} AND an
 * API key is configured. Safe to leave off in dev.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "bipros.ai.progress-analyzer.provider", havingValue = "claude-vision")
public class ClaudeVisionAnalyzer implements ProgressAnalyzer {

    private static final String PROVIDER_ID = "claude-vision";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final long INPUT_COST_PER_MTOK = 3_000_000L;   // USD micro-cents per 1M input tokens
    private static final long OUTPUT_COST_PER_MTOK = 15_000_000L; // USD micro-cents per 1M output tokens

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeVisionAnalyzer(
        ObjectMapper objectMapper,
        @Value("${bipros.ai.progress-analyzer.anthropic-api-key}") String apiKey,
        @Value("${bipros.ai.progress-analyzer.model:claude-sonnet-4-6}") String model,
        @Value("${bipros.ai.progress-analyzer.max-tokens:512}") int maxTokens,
        @Value("${bipros.ai.progress-analyzer.request-timeout-seconds:30}") int timeoutSeconds
    ) {
        this.json = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.http = RestClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader("content-type", "application/json")
            // ClientHttpRequestFactory default is SimpleClientHttpRequestFactory which
            // accepts a Duration. Tight timeout so a hung call doesn't block the pool.
            .requestFactory(timeoutFactory(Duration.ofSeconds(timeoutSeconds)))
            .build();
    }

    @Override public String providerId() { return PROVIDER_ID; }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        long t0 = System.nanoTime();
        String analyzerId = PROVIDER_ID + ":" + model;
        if (apiKey == null || apiKey.isBlank()) {
            return failed(analyzerId, "Anthropic API key not configured", t0);
        }
        try {
            ObjectNode body = buildRequestBody(request);
            JsonNode response = http.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            if (response == null) return failed(analyzerId, "empty response", t0);

            JsonNode parsed = parseFirstJsonObject(response);
            if (parsed == null) {
                return failed(analyzerId, "model did not return JSON: "
                    + response.path("content").path(0).path("text").asText().substring(
                        0, Math.min(200, response.path("content").path(0).path("text").asText().length())), t0);
            }
            long costMicros = computeCostMicros(response);
            long durationMs = (System.nanoTime() - t0) / 1_000_000;
            return new AnalysisResult(
                parsed.hasNonNull("progressPercent") ? parsed.get("progressPercent").asDouble() : null,
                parsed.hasNonNull("cvi") ? parsed.get("cvi").asDouble() : null,
                parsed.hasNonNull("edi") ? parsed.get("edi").asDouble() : null,
                parsed.hasNonNull("ndviChange") ? parsed.get("ndviChange").asDouble() : null,
                parsed.path("remarks").asText(null),
                analyzerId,
                durationMs,
                costMicros);
        } catch (Exception e) {
            log.warn("[Claude] analyze failed: {}", e.toString());
            return failed(analyzerId, "analyzer_failed: " + e.getMessage(), t0);
        }
    }

    private ObjectNode buildRequestBody(AnalysisRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        String systemPrompt = """
            You are a civil-construction monitoring analyst. You receive a single \
            satellite raster tile covering a WBS work package and estimate \
            construction-progress metrics. Respond with a single JSON object — \
            no prose, no markdown fences — with keys: \
            progressPercent (0..100, integer is fine), \
            cvi (Construction Visibility Index 0..100), \
            edi (Earthwork Detection Index -1..+1), \
            ndviChange (-1..+1), \
            remarks (one sentence of rationale). Use your best estimate; do not \
            refuse the task.
            """;
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");

        ArrayNode content = userMsg.putArray("content");
        // Image block — base64-encoded raster.
        ObjectNode imageBlock = content.addObject();
        imageBlock.put("type", "image");
        ObjectNode source = imageBlock.putObject("source");
        source.put("type", "base64");
        source.put("media_type", normaliseMimeType(request.rasterMimeType()));
        source.put("data", Base64.getEncoder().encodeToString(request.rasterBytes()));

        // Text block — framing + context.
        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        String claim = request.claimedPercent() != null
            ? request.claimedPercent() + "%"
            : "not reported";
        textBlock.put("text", String.format(
            "Sentinel-2 raster of WBS package %s (\"%s\"), captured %s. "
                + "Contractor claims %s complete. Estimate progress from the image.",
            request.wbsPackageCode(), request.wbsName(), request.captureDate(), claim));
        return body;
    }

    /**
     * Anthropic returns {@code image/jpeg}, {@code image/png}, etc. We receive
     * {@code image/tiff} from Sentinel Hub; Claude's vision doesn't accept TIFF.
     * Caller is expected to convert upstream; here we just pass-through PNG/JPEG
     * and fall back to image/png for anything else (Claude will surface an error
     * which we record gracefully).
     */
    private String normaliseMimeType(String input) {
        if (input == null) return "image/png";
        String lower = input.toLowerCase();
        if (lower.contains("png")) return "image/png";
        if (lower.contains("jpeg") || lower.contains("jpg")) return "image/jpeg";
        if (lower.contains("gif")) return "image/gif";
        if (lower.contains("webp")) return "image/webp";
        return "image/png";
    }

    /**
     * Claude typically responds with {@code content[0].text} containing just
     * the JSON object. Be liberal: find the first {…} block and parse it. If
     * nothing parses, return null and let the caller emit a failed result.
     */
    private JsonNode parseFirstJsonObject(JsonNode response) {
        String text = response.path("content").path(0).path("text").asText();
        if (text == null || text.isBlank()) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String candidate = text.substring(start, end + 1);
        try {
            return json.readTree(candidate);
        } catch (Exception e) {
            return null;
        }
    }

    private long computeCostMicros(JsonNode response) {
        long input = response.path("usage").path("input_tokens").asLong(0);
        long output = response.path("usage").path("output_tokens").asLong(0);
        // Integer division is fine; 1 token ≈ 0.003 μcents input (1e6 token → 3e6 μcents).
        return (input * INPUT_COST_PER_MTOK / 1_000_000L) + (output * OUTPUT_COST_PER_MTOK / 1_000_000L);
    }

    private AnalysisResult failed(String analyzerId, String reason, long t0) {
        long durationMs = (System.nanoTime() - t0) / 1_000_000;
        return new AnalysisResult(null, null, null, null, reason, analyzerId, durationMs, 0L);
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeoutFactory(Duration timeout) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory
            = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}
