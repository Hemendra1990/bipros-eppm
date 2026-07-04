package com.bipros.api.service;

import com.bipros.api.config.WhatsAppProperties;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.security.infrastructure.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final JwtTokenProvider jwtTokenProvider;
    private final ProjectRepository projectRepository;
    private final WhatsAppProperties whatsAppProperties;

    public ResponseEntity<Void> redirectToGis(String projectId, String token) {
        UUID projectUuid;
        try {
            projectUuid = UUID.fromString(projectId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid project UUID in WhatsApp deep-link: {}", projectId);
            return buildErrorRedirect("invalid_project_id");
        }

        if (token == null || token.isBlank()) {
            return buildErrorRedirect("missing_token");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Invalid or expired WhatsApp deep-link token for project {}", projectId);
            return buildErrorRedirect("invalid_token");
        }

        if (!projectRepository.existsById(projectUuid)) {
            log.warn("Project not found for WhatsApp deep-link: {}", projectId);
        } else {
            log.info("WhatsApp deep-link: redirecting authenticated user to GIS for project {}", projectId);
        }

        return buildSuccessRedirect(projectId, token);
    }

    private ResponseEntity<Void> buildSuccessRedirect(String projectId, String token) {
        String location = whatsAppProperties.getFrontendUrl()
                + "/auth/deeplink"
                + "?auth=" + token
                + "&projectId=" + projectId;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private ResponseEntity<Void> buildErrorRedirect(String errorCode) {
        String location = whatsAppProperties.getFrontendUrl() + "/auth/login?error=" + errorCode;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
