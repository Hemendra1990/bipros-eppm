package com.bipros.analytics.application.service;

import com.bipros.analytics.domain.model.AnalyticsAuditLog;
import com.bipros.analytics.domain.repository.AnalyticsAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Persists {@link AnalyticsAuditLog} rows. Uses REQUIRES_NEW so audit writes succeed
 * even if the orchestrator's outer transaction rolls back. Persistence failures are
 * logged but never propagated — losing an audit row must not break the user's request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAuditService {

    private final AnalyticsAuditLogRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AnalyticsAuditLog row) {
        try {
            repo.save(row);
        } catch (Exception ex) {
            log.error("ANALYTICS_AUDIT_PERSIST_FAILED status={} user={} kind={}",
                    row.getStatus(), row.getUserId(), row.getErrorKind(), ex);
        }
    }

    public static String sha256(String s) {
        if (s == null) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes());
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return null;
        }
    }
}
