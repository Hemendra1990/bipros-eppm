package com.bipros.analytics.application.service;

import com.bipros.analytics.application.dto.LlmProviderRequest;
import com.bipros.analytics.application.dto.LlmProviderResponse;
import com.bipros.analytics.application.dto.TestConnectionResponse;
import com.bipros.analytics.application.exception.LlmProviderTestFailedException;
import com.bipros.analytics.domain.model.ProviderStatus;
import com.bipros.analytics.domain.model.UserLlmProvider;
import com.bipros.analytics.domain.repository.UserLlmProviderRepository;
import com.bipros.analytics.infrastructure.crypto.LlmKeyVault;
import com.bipros.analytics.infrastructure.llm.CompletionRequest;
import com.bipros.analytics.infrastructure.llm.CompletionResponse;
import com.bipros.analytics.infrastructure.llm.Message;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.security.application.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LlmProviderService {

    private final UserLlmProviderRepository repo;
    private final LlmKeyVault vault;
    private final LlmProviderResolver resolver;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<LlmProviderResponse> listMine() {
        UUID userId = requireUser();
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public LlmProviderResponse create(LlmProviderRequest req) {
        UUID userId = requireUser();
        UserLlmProvider e = new UserLlmProvider();
        e.setUserId(userId);
        e.setProvider(req.provider());
        e.setModelName(req.modelName());
        e.setDisplayName(req.displayName());
        e.setEndpointOverride(blankToNull(req.endpointOverride()));
        e.setEncryptedApiKey(req.apiKey() == null || req.apiKey().isBlank() ? null : vault.encrypt(req.apiKey()));
        e.setStatus(ProviderStatus.ACTIVE);
        if (req.isDefault()) clearOtherDefaults(userId);
        e.setDefault(req.isDefault());
        if (!req.isDefault() && repo.findByUserIdAndIsDefaultTrue(userId).isEmpty()) {
            e.setDefault(true);
        }
        return toDto(repo.save(e));
    }

    @Transactional
    public LlmProviderResponse setDefault(UUID providerId) {
        UUID userId = requireUser();
        UserLlmProvider e = repo.findByIdAndUserId(providerId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("LlmProvider", providerId));
        clearOtherDefaults(userId);
        e.setDefault(true);
        return toDto(repo.save(e));
    }

    @Transactional
    public void delete(UUID providerId) {
        UUID userId = requireUser();
        UserLlmProvider e = repo.findByIdAndUserId(providerId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("LlmProvider", providerId));
        boolean wasDefault = e.isDefault();
        repo.delete(e);
        repo.flush();
        if (wasDefault) {
            List<UserLlmProvider> rest = repo.findByUserIdOrderByCreatedAtDesc(userId);
            if (!rest.isEmpty()) {
                UserLlmProvider newDefault = rest.get(0);
                newDefault.setDefault(true);
                repo.save(newDefault);
            }
        }
    }

    @Transactional(noRollbackFor = LlmProviderTestFailedException.class)
    public TestConnectionResponse testConnection(UUID providerId) {
        UUID userId = requireUser();
        var resolved = resolver.forProviderId(providerId, userId);
        UserLlmProvider cfg = resolved.config();
        try {
            CompletionResponse resp = resolved.adapter().complete(
                new CompletionRequest(
                    "You are a connectivity probe. Respond with the literal word 'ok'.",
                    List.of(new Message(Message.Role.USER, "ping")),
                    8,
                    Duration.ofSeconds(20)
                ),
                resolved.apiKey(),
                resolved.endpoint(),
                resolved.model()
            );
            cfg.setStatus(ProviderStatus.ACTIVE);
            cfg.setLastValidatedAt(Instant.now());
            cfg.setLastValidationError(null);
            repo.save(cfg);
            return new TestConnectionResponse(true, "ok — " + truncate(resp.text(), 60), cfg.getLastValidatedAt());
        } catch (Exception ex) {
            log.warn("LLM test failed for provider {} ({}): {}", providerId, cfg.getProvider(), ex.getMessage());
            cfg.setStatus(ProviderStatus.KEY_INVALID);
            cfg.setLastValidatedAt(Instant.now());
            cfg.setLastValidationError(truncate(ex.getMessage(), 480));
            repo.save(cfg);
            throw new LlmProviderTestFailedException("Test connection failed: " + ex.getMessage(), ex);
        }
    }

    private void clearOtherDefaults(UUID userId) {
        repo.findByUserIdAndIsDefaultTrue(userId).ifPresent(other -> {
            other.setDefault(false);
            repo.saveAndFlush(other);
        });
    }

    private LlmProviderResponse toDto(UserLlmProvider e) {
        return new LlmProviderResponse(
            e.getId(), e.getProvider(), e.getModelName(), e.getDisplayName(),
            e.getEndpointOverride(), e.isDefault(),
            e.getEncryptedApiKey() != null,
            e.getStatus(), e.getLastValidatedAt(), e.getLastValidationError(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private UUID requireUser() {
        UUID id = currentUserService.getCurrentUserId();
        if (id == null) throw new IllegalStateException("No authenticated user in context");
        return id;
    }

    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
