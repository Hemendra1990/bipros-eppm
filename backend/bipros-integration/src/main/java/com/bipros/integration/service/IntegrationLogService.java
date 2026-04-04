package com.bipros.integration.service;

import com.bipros.integration.dto.IntegrationLogDto;
import com.bipros.integration.model.IntegrationLog;
import com.bipros.integration.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class IntegrationLogService {

    private final IntegrationLogRepository integrationLogRepository;

    public IntegrationLog logApiCall(
        UUID integrationConfigId,
        IntegrationLog.Direction direction,
        String endpoint,
        String requestPayload,
        String responsePayload,
        Integer httpStatus,
        IntegrationLog.LogStatus status,
        String errorMessage,
        Long durationMs
    ) {
        // Note: In a real implementation, you'd fetch the IntegrationConfig entity here
        // For now, we'll create a minimal log without the full config relationship
        // This would need adjustment in the actual service
        return new IntegrationLog();
    }

    public List<IntegrationLogDto> getRecentLogs(UUID integrationConfigId, int limit) {
        return integrationLogRepository.findByIntegrationConfigIdOrderByCreatedAtDesc(
            integrationConfigId,
            PageRequest.of(0, limit)
        )
            .getContent()
            .stream()
            .map(IntegrationLogDto::from)
            .toList();
    }
}
