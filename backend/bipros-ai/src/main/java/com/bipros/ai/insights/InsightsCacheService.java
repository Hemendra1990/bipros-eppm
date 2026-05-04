package com.bipros.ai.insights;

import com.bipros.ai.insights.dto.InsightsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsCacheService {

    private final AiInsightCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration STABLE_DATA_TTL = Duration.ofHours(24);

    @Transactional(readOnly = true)
    public Optional<InsightsResponse> getCached(UUID projectId, String tab, String dataHash) {
        Optional<AiInsightCache> cached = cacheRepository.findByProjectIdAndTab(projectId, tab);
        if (cached.isEmpty()) {
            return Optional.empty();
        }

        AiInsightCache entry = cached.get();
        Instant now = Instant.now();
        Instant createdAt = entry.getCreatedAt();

        if (createdAt.plus(TTL).isAfter(now)) {
            return parseResponse(entry.getResponseJson());
        }

        if (dataHash.equals(entry.getDataHash()) && createdAt.plus(STABLE_DATA_TTL).isAfter(now)) {
            return parseResponse(entry.getResponseJson());
        }

        return Optional.empty();
    }

    @Transactional
    public void save(UUID projectId, String tab, String dataHash, InsightsResponse response,
                     String model, Integer tokenUsageInput, Integer tokenUsageOutput) {
        cacheRepository.deleteByProjectIdAndTab(projectId, tab);

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize InsightsResponse for projectId={}, tab={}", projectId, tab, e);
            throw new RuntimeException("Failed to serialize insights response", e);
        }

        AiInsightCache cache = AiInsightCache.builder()
                .projectId(projectId)
                .tab(tab)
                .dataHash(dataHash)
                .responseJson(responseJson)
                .model(model)
                .tokenUsageInput(tokenUsageInput)
                .tokenUsageOutput(tokenUsageOutput)
                .build();

        cacheRepository.save(cache);
    }

    @Transactional
    public void invalidate(UUID projectId, String tab) {
        cacheRepository.deleteByProjectIdAndTab(projectId, tab);
    }

    private Optional<InsightsResponse> parseResponse(String responseJson) {
        try {
            InsightsResponse parsed = objectMapper.readValue(responseJson, InsightsResponse.class);
            return Optional.of(parsed);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse cached InsightsResponse", e);
            return Optional.empty();
        }
    }
}
