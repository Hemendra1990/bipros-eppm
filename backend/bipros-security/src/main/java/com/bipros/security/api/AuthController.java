package com.bipros.security.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.security.application.dto.AuthResponse;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
import com.bipros.security.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
@PreAuthorize("permitAll()")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Login error", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", e.getMessage()));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Registration error", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("BAD_REQUEST", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generate a new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.refreshToken());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", e.getMessage()));
        }
    }

    public record RefreshTokenRequest(String refreshToken) {}
}
