package com.bipros.permit.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.permit.application.dto.QrVerifyResponse;
import com.bipros.permit.application.service.PermitVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PermitVerifyController {

    private final PermitVerifyService verifyService;

    /**
     * Public-ish QR scan endpoint. Returns minimal, PII-free verification.
     * Security wiring (rate limiting + permit_all on this path) is configured outside this module.
     */
    @GetMapping("/v1/permits/verify/{token}")
    public ResponseEntity<ApiResponse<QrVerifyResponse>> verify(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(verifyService.verify(token)));
    }
}
