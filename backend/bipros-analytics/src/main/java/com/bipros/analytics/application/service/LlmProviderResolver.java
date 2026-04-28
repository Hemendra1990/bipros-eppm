package com.bipros.analytics.application.service;

import com.bipros.analytics.application.exception.LlmNotConfiguredException;
import com.bipros.analytics.domain.model.LlmProvider;
import com.bipros.analytics.domain.model.UserLlmProvider;
import com.bipros.analytics.domain.repository.UserLlmProviderRepository;
import com.bipros.analytics.infrastructure.crypto.LlmKeyVault;
import com.bipros.analytics.infrastructure.llm.LlmAdapter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LlmProviderResolver {

    private final UserLlmProviderRepository repo;
    private final LlmKeyVault vault;
    private final List<LlmAdapter> adapters;
    private final Map<LlmProvider, LlmAdapter> byProvider = new EnumMap<>(LlmProvider.class);

    @PostConstruct
    void index() {
        for (LlmAdapter a : adapters) byProvider.put(a.provider(), a);
    }

    public ResolvedProvider forUserDefault(UUID userId) {
        UserLlmProvider cfg = repo.findByUserIdAndIsDefaultTrue(userId)
            .orElseThrow(() -> new LlmNotConfiguredException("No default LLM provider configured for user " + userId));
        return resolve(cfg);
    }

    public ResolvedProvider forProviderId(UUID providerId, UUID userId) {
        UserLlmProvider cfg = repo.findByIdAndUserId(providerId, userId)
            .orElseThrow(() -> new LlmNotConfiguredException("LLM provider " + providerId + " not found for user"));
        return resolve(cfg);
    }

    private ResolvedProvider resolve(UserLlmProvider cfg) {
        LlmAdapter adapter = byProvider.get(cfg.getProvider());
        if (adapter == null) {
            throw new LlmNotConfiguredException("No adapter installed for provider " + cfg.getProvider());
        }
        String apiKey = cfg.getEncryptedApiKey() != null
            ? vault.decrypt(cfg.getEncryptedApiKey())
            : null;
        return new ResolvedProvider(adapter, apiKey, cfg.getEndpointOverride(), cfg.getModelName(), cfg);
    }

    public record ResolvedProvider(
        LlmAdapter adapter,
        String apiKey,
        String endpoint,
        String model,
        UserLlmProvider config
    ) {}
}
