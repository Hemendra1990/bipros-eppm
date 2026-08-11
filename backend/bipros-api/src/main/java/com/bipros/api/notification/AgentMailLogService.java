package com.bipros.api.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Append-only writer for {@link AgentMailLog}. MUST never break a send path — every
 * exception is swallowed with a WARN. Callers construct the row (plain setters) and the
 * service stamps {@code sentAt} when absent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMailLogService {

    private final AgentMailLogRepository repository;

    public void log(AgentMailLog row) {
        try {
            if (row.getSentAt() == null) row.setSentAt(Instant.now());
            repository.save(row);
        } catch (Exception ex) {
            log.warn("[AgentMailLog] write failed category={} project={}: {}",
                row.getCategory(), row.getProjectId(), ex.getMessage());
        }
    }
}
