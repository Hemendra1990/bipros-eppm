package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
import com.bipros.security.application.dto.AuthResponse;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Project API Integration Tests")
class ProjectApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("bipros_test")
        .withUsername("postgres")
        .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    private String authToken;

    @BeforeEach
    void setUp() {
        // Clean up and prepare test data
        authToken = null;
    }

    @Test
    @DisplayName("Register user → login → get token")
    void authenticationFlow_completesSuccessfully() {
        // 1. Register a new user
        RegisterRequest registerRequest = new RegisterRequest(
            "testuser",
            "testuser@example.com",
            "testPassword123!",
            "Test",
            "User"
        );

        ResponseEntity<ApiResponse> registerResponse = restTemplate.postForEntity(
            "/v1/auth/register",
            registerRequest,
            ApiResponse.class
        );

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().error()).isNull();

        // 2. Login with registered credentials
        LoginRequest loginRequest = new LoginRequest("testuser", "testPassword123!");

        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
            "/v1/auth/login",
            loginRequest,
            ApiResponse.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().error()).isNull();

        // 3. Extract and verify token
        AuthResponse authResponse = (AuthResponse) loginResponse.getBody().data();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.refreshToken()).isNotBlank();

        this.authToken = authResponse.accessToken();
    }

    @Test
    @DisplayName("Login with invalid credentials returns error")
    void loginWithInvalidCredentials_returnsUnauthorized() {
        LoginRequest loginRequest = new LoginRequest("nonexistent", "wrongpassword");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            "/v1/auth/login",
            loginRequest,
            ApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNotNull();
    }

    @Test
    @DisplayName("Register with duplicate email returns conflict")
    void registerWithDuplicateEmail_returnsConflict() {
        // Register first user
        RegisterRequest firstRequest = new RegisterRequest(
            "firstuser",
            "duplicate@example.com",
            "testPassword123!",
            "First",
            "User"
        );

        restTemplate.postForEntity(
            "/v1/auth/register",
            firstRequest,
            ApiResponse.class
        );

        // Try to register with same email
        RegisterRequest duplicateRequest = new RegisterRequest(
            "seconduser",
            "duplicate@example.com",
            "anotherPassword123!",
            "Second",
            "User"
        );

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            "/v1/auth/register",
            duplicateRequest,
            ApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNotNull();
    }

    @Test
    @DisplayName("Get audit logs without authentication returns unauthorized")
    void getAuditLogsWithoutAuth_returnsUnauthorized() {
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
            "/v1/audit",
            ApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Get audit logs with valid token returns success")
    void getAuditLogsWithValidToken_returnsSuccess() {
        // First authenticate
        authenticateTestUser();

        // Then retrieve audit logs with token
        ResponseEntity<ApiResponse> response = restTemplate
            .getForEntity("/v1/audit", ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNull();
    }

    private void authenticateTestUser() {
        RegisterRequest registerRequest = new RegisterRequest(
            "audittest",
            "audittest@example.com",
            "testPassword123!",
            "Audit",
            "User"
        );

        restTemplate.postForEntity(
            "/v1/auth/register",
            registerRequest,
            ApiResponse.class
        );

        LoginRequest loginRequest = new LoginRequest("audittest", "testPassword123!");

        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
            "/v1/auth/login",
            loginRequest,
            ApiResponse.class
        );

        AuthResponse authResponse = (AuthResponse) loginResponse.getBody().data();
        this.authToken = authResponse.accessToken();
    }
}
