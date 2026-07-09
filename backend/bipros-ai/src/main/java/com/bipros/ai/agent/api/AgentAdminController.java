package com.bipros.ai.agent.api;

import com.bipros.ai.agent.budget.AgentBudgetProperties;
import com.bipros.ai.agent.domain.AgentBudgetUsage;
import com.bipros.ai.agent.domain.AgentBudgetUsageRepository;
import com.bipros.ai.agent.domain.AgentChannelConfig;
import com.bipros.ai.agent.domain.AgentChannelConfigRepository;
import com.bipros.ai.provider.crypto.ApiKeyCipher;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin surface for the agent platform: outbound messaging channel config (WhatsApp/SMS creds) and
 * a read view of the LLM token budget caps + today's global usage. Auth tokens are encrypted at rest
 * with {@link ApiKeyCipher} and never returned to the client (only a {@code hasAuthToken} flag).
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AgentAdminController {

    private final AgentChannelConfigRepository channelRepository;
    private final AgentBudgetUsageRepository budgetUsageRepository;
    private final AgentBudgetProperties budgetProperties;
    private final ApiKeyCipher apiKeyCipher;

    // ---- channels ----

    @GetMapping("/agent-channels")
    @PreAuthorize("hasPermission(null, 'ADMIN_MASTER.READ')")
    public ResponseEntity<ApiResponse<List<ChannelDto>>> listChannels() {
        List<ChannelDto> out = channelRepository.findAll().stream().map(AgentAdminController::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PutMapping("/agent-channels")
    @PreAuthorize("hasPermission(null, 'ADMIN_MASTER.UPDATE')")
    public ResponseEntity<ApiResponse<ChannelDto>> upsertChannel(@RequestBody ChannelUpsertRequest req) {
        AgentChannelConfig cfg = channelRepository.findByChannelKey(req.channelKey())
                .orElseGet(AgentChannelConfig::new);
        cfg.setChannelKey(req.channelKey());
        cfg.setApiUrl(req.apiUrl());
        cfg.setAccountSid(req.accountSid());
        cfg.setFromNumber(req.fromNumber());
        cfg.setActive(Boolean.TRUE.equals(req.active()));
        if (req.authToken() != null && !req.authToken().isBlank()) {
            ApiKeyCipher.EncryptedKey enc = apiKeyCipher.encrypt(req.authToken());
            cfg.setAuthTokenCiphertext(enc.ciphertext());
            cfg.setAuthTokenIv(enc.iv());
            cfg.setAuthTokenVersion(enc.version());
        }
        return ResponseEntity.ok(ApiResponse.ok(toDto(channelRepository.save(cfg))));
    }

    // ---- budgets ----

    @GetMapping("/agent-budgets")
    @PreAuthorize("hasPermission(null, 'ADMIN_MASTER.READ')")
    public ResponseEntity<ApiResponse<BudgetView>> budgets() {
        AgentBudgetUsage global = budgetUsageRepository
                .findByProjectIdAndUsageDate(AgentBudgetUsage.GLOBAL_SCOPE, LocalDate.now())
                .orElse(null);
        long reserved = global == null ? 0 : global.getTokensReserved();
        long used = global == null ? 0 : global.getTokensUsed();
        return ResponseEntity.ok(ApiResponse.ok(new BudgetView(
                budgetProperties.getPerRunTokens(),
                budgetProperties.getPerProjectDailyTokens(),
                budgetProperties.getGlobalDailyTokens(),
                budgetProperties.getSupervisorPerInvestigationTokens(),
                reserved, used)));
    }

    private static ChannelDto toDto(AgentChannelConfig c) {
        return new ChannelDto(c.getChannelKey(), c.getApiUrl(), c.getAccountSid(), c.getFromNumber(),
                c.isActive(), c.getAuthTokenCiphertext() != null);
    }

    public record ChannelDto(String channelKey, String apiUrl, String accountSid, String fromNumber,
                             boolean active, boolean hasAuthToken) {
    }

    public record ChannelUpsertRequest(String channelKey, String apiUrl, String accountSid,
                                       String authToken, String fromNumber, Boolean active) {
    }

    public record BudgetView(long perRunTokens, long perProjectDailyTokens, long globalDailyTokens,
                             long supervisorPerInvestigationTokens,
                             long globalReservedToday, long globalUsedToday) {
    }
}
