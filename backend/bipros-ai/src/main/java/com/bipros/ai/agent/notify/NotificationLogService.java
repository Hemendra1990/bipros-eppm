package com.bipros.ai.agent.notify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view over the delivery audit ({@code ai.agent_notification_delivery}) joined with the
 * finding it belongs to and the recipient's name — powers the Notification Log (owner decision
 * 2026-08-05: PM sees their project's log, ADMIN sees everything). Rows are returned newest first.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationLogService {

    @PersistenceContext
    private EntityManager em;

    /** One delivery row for the log UI. {@code sentAt} is null unless status is SENT. */
    public record Entry(Instant at, UUID findingId, String findingTitle, String severity,
                        String agentKey, UUID projectId, UUID recipientUserId, String recipientName,
                        String channel, String status, String detail, Instant sentAt) {
    }

    /** {@code projectId} null = all projects (admin scope). Limit clamped to [1, 500]. */
    @SuppressWarnings("unchecked")
    public List<Entry> list(UUID projectId, int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder(
                "SELECT d.created_at, d.channel_key, d.status, d.detail, d.sent_at, "
                        + "d.recipient_user_id, "
                        + "TRIM(COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')) AS full_name, "
                        + "u.username, f.id AS finding_id, f.title, f.severity, f.agent_key, f.project_id "
                        + "FROM ai.agent_notification_delivery d "
                        + "JOIN ai.agent_finding f ON f.id = d.finding_id "
                        + "LEFT JOIN public.users u ON u.id = d.recipient_user_id ");
        if (projectId != null) {
            sql.append("WHERE f.project_id = :projectId ");
        }
        sql.append("ORDER BY d.created_at DESC LIMIT ").append(capped);

        var query = em.createNativeQuery(sql.toString());
        if (projectId != null) {
            query.setParameter("projectId", projectId);
        }
        List<Object[]> rows = query.getResultList();
        List<Entry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String fullName = str(r[6]);
            String username = str(r[7]);
            out.add(new Entry(
                    instant(r[0]),
                    uuid(r[8]),
                    str(r[9]),
                    str(r[10]),
                    str(r[11]),
                    uuid(r[12]),
                    uuid(r[5]),
                    fullName == null || fullName.isBlank() ? username : fullName,
                    str(r[1]),
                    str(r[2]),
                    str(r[3]),
                    instant(r[4])));
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static Instant instant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (o instanceof BigDecimal b) {
            return Instant.ofEpochMilli(b.longValue());
        }
        return null;
    }
}
