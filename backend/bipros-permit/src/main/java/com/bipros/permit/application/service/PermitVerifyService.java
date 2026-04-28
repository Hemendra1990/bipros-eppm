package com.bipros.permit.application.service;

import com.bipros.permit.application.dto.QrVerifyResponse;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Public verification of a permit by its QR token. Returns no PII —
 * just the permit code, type, validity, status, and zone label.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermitVerifyService {

    private final PermitRepository permitRepository;
    private final PermitTypeTemplateRepository typeTemplateRepository;
    private final QrCodeService qrCodeService;

    public QrVerifyResponse verify(String token) {
        if (token == null || token.isBlank()) {
            return new QrVerifyResponse(null, null, null, null, null, null, false);
        }
        return permitRepository.findByQrToken(token)
                .map(this::toResponse)
                .orElseGet(() -> new QrVerifyResponse(null, null, null, null, null, null, false));
    }

    public byte[] renderPermitQrPng(Permit permit, int sizePx) {
        return qrCodeService.renderPng(permit.getQrToken() == null ? "" : permit.getQrToken(), sizePx);
    }

    private QrVerifyResponse toResponse(Permit p) {
        PermitTypeTemplate t = typeTemplateRepository.findById(p.getPermitTypeTemplateId()).orElse(null);
        boolean valid = p.getStatus() == PermitStatus.ISSUED || p.getStatus() == PermitStatus.IN_PROGRESS;
        Instant now = Instant.now();
        if (valid && p.getValidTo() != null && now.isAfter(p.getValidTo())) valid = false;
        if (valid && p.getValidFrom() != null && now.isBefore(p.getValidFrom())) valid = false;
        return new QrVerifyResponse(
                p.getPermitCode(),
                t != null ? t.getName() : null,
                p.getStatus(),
                p.getLocationZone(),
                p.getValidFrom(),
                p.getValidTo(),
                valid);
    }
}
