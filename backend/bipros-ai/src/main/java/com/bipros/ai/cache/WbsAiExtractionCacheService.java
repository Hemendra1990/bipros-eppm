package com.bipros.ai.cache;

import com.bipros.ai.wbs.dto.WbsAiGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 24-hour content-keyed cache for WBS-from-document results. Re-uploading the
 * same file (sha256 match) within the TTL skips the LLM call entirely and
 * returns the cached response in milliseconds.
 *
 * <p>Cache key: {@code (sha256(file_bytes), prompt_version, model_family)}.
 * Bumping {@code prompt_version} invalidates entries when the prompt template
 * meaningfully changes; the model_family scope prevents OpenAI / Anthropic
 * results from cross-contaminating.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbsAiExtractionCacheService {

    /** Bump this when the prompt template changes in a way that affects output. */
    public static final int CURRENT_PROMPT_VERSION = 2;

    /** Stale entries past this are ignored at lookup time. */
    public static final Duration TTL = Duration.ofHours(24);

    private final WbsAiExtractionCacheRepository repository;
    private final ObjectMapper objectMapper;

    public String hash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WbsAiGenerationResponse> lookup(String sha, String modelFamily) {
        WbsAiExtractionCache.CacheKey key = new WbsAiExtractionCache.CacheKey(
                sha, CURRENT_PROMPT_VERSION, modelFamily);
        return repository.findById(key)
                .filter(e -> Duration.between(e.getCreatedAt(), Instant.now()).compareTo(TTL) <= 0)
                .map(e -> {
                    try {
                        return objectMapper.readValue(e.getResultJson(), WbsAiGenerationResponse.class);
                    } catch (Exception ex) {
                        log.warn("Cache deserialize failed for {}: {}", sha, ex.getMessage());
                        return null;
                    }
                });
    }

    @Transactional
    public void store(String sha, String modelFamily, WbsAiGenerationResponse result) {
        try {
            WbsAiExtractionCache row = new WbsAiExtractionCache();
            row.setId(new WbsAiExtractionCache.CacheKey(sha, CURRENT_PROMPT_VERSION, modelFamily));
            row.setResultJson(objectMapper.writeValueAsString(result));
            row.setCreatedAt(Instant.now());
            repository.save(row);
        } catch (Exception e) {
            // Cache failure must never fail the actual generation.
            log.warn("Cache store failed for {}: {}", sha, e.getMessage());
        }
    }

    @Transactional
    public void recordHit(String sha, String modelFamily) {
        try {
            WbsAiExtractionCache.CacheKey key = new WbsAiExtractionCache.CacheKey(
                    sha, CURRENT_PROMPT_VERSION, modelFamily);
            repository.findById(key).ifPresent(e -> {
                e.setHitCount(e.getHitCount() + 1);
                e.setLastHitAt(Instant.now());
            });
        } catch (Exception e) {
            log.debug("Hit-counter update failed for {}: {}", sha, e.getMessage());
        }
    }
}
