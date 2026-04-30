package com.bipros.ai.provider;

import com.bipros.ai.provider.crypto.ApiKeyCipher;
import com.bipros.common.dto.ApiResponse;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderConfigRepository repository;
    private final ApiKeyCipher apiKeyCipher;
    private final OpenAiCompatibleProvider openAiProvider;

    public LlmProviderConfig create(CreateLlmProviderRequest req) {
        if (repository.findByName(req.name()).isPresent()) {
            throw new IllegalArgumentException("Provider name already exists: " + req.name());
        }

        LlmProviderConfig config = new LlmProviderConfig();
        config.setName(req.name());
        config.setBaseUrl(req.baseUrl());
        config.setModel(req.model());
        config.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 4096);
        config.setTemperature(req.temperature() != null ? req.temperature() : new java.math.BigDecimal("0.20"));
        config.setTimeoutMs(req.timeoutMs() != null ? req.timeoutMs() : 60000);
        config.setAuthScheme(req.authScheme() != null ? req.authScheme() : "BEARER");
        config.setSupportsNativeTools(req.supportsNativeTools() != null ? req.supportsNativeTools() : true);
        config.setActive(req.isActive() != null ? req.isActive() : true);
        config.setExtraHeaders(req.extraHeaders());

        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            ApiKeyCipher.EncryptedKey encrypted = apiKeyCipher.encrypt(req.apiKey());
            config.setApiKeyIv(encrypted.iv());
            config.setApiKeyCiphertext(encrypted.ciphertext());
            config.setApiKeyVersion(encrypted.version());
        }

        boolean wantsDefault = Boolean.TRUE.equals(req.isDefault())
                || repository.findByIsDefaultTrue().isEmpty();
        if (wantsDefault) {
            repository.findByIsDefaultTrue().ifPresent(existing -> {
                existing.setDefault(false);
                repository.save(existing);
            });
            config.setDefault(true);
        }

        return repository.save(config);
    }

    public LlmProviderConfig update(UUID id, UpdateLlmProviderRequest req) {
        LlmProviderConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LLM provider", id.toString()));

        if (req.name() != null) config.setName(req.name());
        if (req.baseUrl() != null) config.setBaseUrl(req.baseUrl());
        if (req.model() != null) config.setModel(req.model());
        if (req.maxTokens() != null) config.setMaxTokens(req.maxTokens());
        if (req.temperature() != null) config.setTemperature(req.temperature());
        if (req.timeoutMs() != null) config.setTimeoutMs(req.timeoutMs());
        if (req.authScheme() != null) config.setAuthScheme(req.authScheme());
        if (req.supportsNativeTools() != null) config.setSupportsNativeTools(req.supportsNativeTools());
        if (req.isActive() != null) config.setActive(req.isActive());
        if (req.extraHeaders() != null) config.setExtraHeaders(req.extraHeaders());

        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            ApiKeyCipher.EncryptedKey encrypted = apiKeyCipher.encrypt(req.apiKey());
            config.setApiKeyIv(encrypted.iv());
            config.setApiKeyCiphertext(encrypted.ciphertext());
            config.setApiKeyVersion(encrypted.version());
        }

        if (Boolean.TRUE.equals(req.isDefault())) {
            repository.findByIsDefaultTrue().ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setDefault(false);
                    repository.save(existing);
                }
            });
            config.setDefault(true);
        }

        return repository.save(config);
    }

    @Transactional(readOnly = true)
    public LlmProviderConfig findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LLM provider", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<LlmProviderConfig> findAll() {
        return repository.findAll();
    }

    public void delete(UUID id) {
        LlmProviderConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LLM provider", id.toString()));
        repository.delete(config);
    }

    public ProviderTestResponse testProvider(UUID id) {
        long start = System.currentTimeMillis();
        LlmProviderConfig config = findById(id);
        try {
            LlmProvider.ChatResponse resp = openAiProvider.testConnection(config);
            long latency = System.currentTimeMillis() - start;
            boolean ok = resp.content() != null && resp.content().toLowerCase().contains("pong");
            return new ProviderTestResponse(ok, latency,
                    resp.usage() != null ? resp.usage().promptTokens() : 0,
                    resp.usage() != null ? resp.usage().completionTokens() : 0,
                    resp.model(), null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Provider test failed for {}: {}", config.getName(), e.getMessage());
            String errorType = classifyError(e);
            return new ProviderTestResponse(false, latency, 0, 0, null, errorType);
        }
    }

    private String classifyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("auth")) {
            return "auth";
        }
        if (msg.contains("404") || msg.contains("not found") || msg.contains("model")) {
            return "model_not_found";
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many")) {
            return "rate_limited";
        }
        if (msg.contains("timeout") || msg.contains("connection")) {
            return "network";
        }
        return "unknown";
    }

    public record CreateLlmProviderRequest(String name, String baseUrl, String apiKey, String model,
                                           Integer maxTokens, java.math.BigDecimal temperature, Integer timeoutMs,
                                           String extraHeaders, String authScheme, Boolean supportsNativeTools,
                                           Boolean isDefault, Boolean isActive) {
    }

    public record UpdateLlmProviderRequest(String name, String baseUrl, String apiKey, String model,
                                           Integer maxTokens, java.math.BigDecimal temperature, Integer timeoutMs,
                                           String extraHeaders, String authScheme, Boolean supportsNativeTools,
                                           Boolean isDefault, Boolean isActive) {
    }

    public record ProviderTestResponse(boolean ok, long latencyMs, int tokensIn, int tokensOut,
                                       String modelEcho, String error) {
    }
}
