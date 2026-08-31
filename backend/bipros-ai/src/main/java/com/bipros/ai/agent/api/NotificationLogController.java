package com.bipros.ai.agent.api;

import com.bipros.ai.agent.notify.NotificationLogService;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only Notification Log (owner decision 2026-08-05): who was sent what, when, over which
 * channel, and whether it really left (SENT) or was only rendered (PREVIEW). The project route is
 * visible to the project's PM and ADMINs; the admin route covers every project.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationLogService logService;

    @GetMapping("/projects/{projectId}/notifications/log")
    @PreAuthorize("@aiAccess.canViewNotificationLog(#projectId)")
    public ResponseEntity<ApiResponse<List<NotificationLogService.Entry>>> projectLog(
            @PathVariable UUID projectId,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(logService.list(projectId, limit)));
    }

    @GetMapping("/admin/notifications/log")
    @PreAuthorize("hasPermission(null, 'ADMIN_MASTER.READ')")
    public ResponseEntity<ApiResponse<List<NotificationLogService.Entry>>> adminLog(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(logService.list(projectId, limit)));
    }
}
