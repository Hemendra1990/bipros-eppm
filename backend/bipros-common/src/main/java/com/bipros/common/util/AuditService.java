package com.bipros.common.util;

import com.bipros.common.model.AuditLog;
import com.bipros.common.model.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log entity creation.
     *
     * @param entityType the type of entity (e.g., "Project", "Task")
     * @param entityId the ID of the entity
     * @param entity the created entity object
     */
    public void logCreate(String entityType, UUID entityId, Object entity) {
        try {
            String newValue = objectMapper.writeValueAsString(entity);
            String username = getCurrentUsername();
            UUID userId = getCurrentUserId();

            var auditLog = new AuditLog(
                entityType,
                entityId,
                "CREATE",
                userId,
                username,
                Instant.now()
            );
            auditLog.setNewValue(newValue);

            auditLogRepository.save(auditLog);
            log.info("Audit log created: entityType={}, entityId={}, action=CREATE, user={}", entityType, entityId, username);
        } catch (Exception e) {
            log.error("Failed to log audit: entityType={}, entityId={}", entityType, entityId, e);
        }
    }

    /**
     * Log entity update with field change tracking.
     *
     * @param entityType the type of entity
     * @param entityId the ID of the entity
     * @param field the field that changed
     * @param oldVal the old value
     * @param newVal the new value
     */
    public void logUpdate(String entityType, UUID entityId, String field, Object oldVal, Object newVal) {
        try {
            String oldValue = objectMapper.writeValueAsString(oldVal);
            String newValue = objectMapper.writeValueAsString(newVal);
            String username = getCurrentUsername();
            UUID userId = getCurrentUserId();

            var auditLog = new AuditLog(
                entityType,
                entityId,
                "UPDATE",
                userId,
                username,
                Instant.now()
            );
            auditLog.setFieldName(field);
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);

            auditLogRepository.save(auditLog);
            log.info("Audit log created: entityType={}, entityId={}, action=UPDATE, field={}, user={}", entityType, entityId, field, username);
        } catch (Exception e) {
            log.error("Failed to log audit: entityType={}, entityId={}, field={}", entityType, entityId, field, e);
        }
    }

    /**
     * Log entity deletion.
     *
     * @param entityType the type of entity
     * @param entityId the ID of the entity
     */
    public void logDelete(String entityType, UUID entityId) {
        try {
            String username = getCurrentUsername();
            UUID userId = getCurrentUserId();

            var auditLog = new AuditLog(
                entityType,
                entityId,
                "DELETE",
                userId,
                username,
                Instant.now()
            );

            auditLogRepository.save(auditLog);
            log.info("Audit log created: entityType={}, entityId={}, action=DELETE, user={}", entityType, entityId, username);
        } catch (Exception e) {
            log.error("Failed to log audit: entityType={}, entityId={}", entityType, entityId, e);
        }
    }

    /**
     * Get the current user's username from SecurityContext.
     * Falls back to "SYSTEM" if SecurityContext is unavailable.
     */
    private String getCurrentUsername() {
        try {
            // Try to access SecurityContextHolder (requires spring-security on classpath)
            Class<?> securityContextHolder = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = securityContextHolder.getMethod("getContext").invoke(null);
            Object auth = context.getClass().getMethod("getAuthentication").invoke(context);

            if (auth != null) {
                Boolean isAuthenticated = (Boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
                if (isAuthenticated) {
                    return (String) auth.getClass().getMethod("getName").invoke(auth);
                }
            }
        } catch (Exception e) {
            log.debug("SecurityContext unavailable, using SYSTEM user for audit");
        }
        return "SYSTEM";
    }

    /**
     * Get the current user's ID from SecurityContext.
     * Falls back to null if SecurityContext is unavailable or user is not authenticated.
     */
    private UUID getCurrentUserId() {
        try {
            // Try to access SecurityContextHolder (requires spring-security on classpath)
            Class<?> securityContextHolder = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = securityContextHolder.getMethod("getContext").invoke(null);
            Object auth = context.getClass().getMethod("getAuthentication").invoke(context);

            if (auth != null) {
                Boolean isAuthenticated = (Boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
                if (isAuthenticated) {
                    Object principal = auth.getClass().getMethod("getPrincipal").invoke(auth);
                    // Try to call getId() on the principal if it's a User object
                    if (principal != null) {
                        try {
                            Object idResult = principal.getClass().getMethod("getId").invoke(principal);
                            if (idResult instanceof UUID) {
                                return (UUID) idResult;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // Principal doesn't have getId() method
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not retrieve user ID from SecurityContext", e);
        }
        return null;
    }
}
