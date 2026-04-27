package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
import com.bipros.security.application.dto.AuthResponse;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest {

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
        authToken = null;
    }

    private String registerAndLogin(String suffix) {
        RegisterRequest registerRequest = new RegisterRequest(
                "testuser" + suffix, "testuser" + suffix + "@example.com",
                "testPassword123!", "Test", "User");
        restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

        LoginRequest loginRequest = new LoginRequest("testuser" + suffix, "testPassword123!");
        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                "/v1/auth/login", loginRequest, ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) loginResponse.getBody().data();
        return (String) data.get("accessToken");
    }

    @Nested
    @DisplayName("POST /v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("should register a new user and return 201")
        void registerNewUser_returns201() {
            RegisterRequest request = new RegisterRequest(
                    "register1", "register1@example.com",
                    "testPassword123!", "First", "Last");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", request, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error()).isNull();
        }

        @Test
        @DisplayName("should reject registration with duplicate email")
        void registerDuplicateEmail_returns409() {
            String suffix = System.currentTimeMillis() + "";
            RegisterRequest first = new RegisterRequest(
                    "dup1" + suffix, "duplicate" + suffix + "@example.com",
                    "testPassword123!", "First", "User");
            restTemplate.postForEntity("/v1/auth/register", first, ApiResponse.class);

            RegisterRequest duplicate = new RegisterRequest(
                    "dup2" + suffix, "duplicate" + suffix + "@example.com",
                    "testPassword123!", "Second", "User");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", duplicate, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject registration with duplicate username")
        void registerDuplicateUsername_returns409() {
            String suffix = System.currentTimeMillis() + "";
            RegisterRequest first = new RegisterRequest(
                    "sameuser" + suffix, "user1" + suffix + "@example.com",
                    "testPassword123!", "First", "User");
            restTemplate.postForEntity("/v1/auth/register", first, ApiResponse.class);

            RegisterRequest duplicate = new RegisterRequest(
                    "sameuser" + suffix, "user2" + suffix + "@example.com",
                    "testPassword123!", "Second", "User");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", duplicate, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject registration with invalid email")
        void registerInvalidEmail_returns400() {
            RegisterRequest request = new RegisterRequest(
                    "invalidemail", "not-an-email",
                    "testPassword123!", "Test", "User");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", request, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should reject registration with short password")
        void registerShortPassword_returns400() {
            RegisterRequest request = new RegisterRequest(
                    "shortpass", "shortpass@example.com",
                    "123", "Test", "User");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", request, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should reject registration with blank username")
        void registerBlankUsername_returns400() {
            RegisterRequest request = new RegisterRequest(
                    "", "blank@example.com",
                    "testPassword123!", "Test", "User");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/register", request, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("should login with valid credentials and return JWT tokens")
        void loginWithValidCredentials_returnsTokens() {
            String suffix = "L1" + System.currentTimeMillis();
            RegisterRequest registerRequest = new RegisterRequest(
                    "validuser" + suffix, "validuser" + suffix + "@example.com",
                    "testPassword123!", "Test", "User");
            restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

            LoginRequest loginRequest = new LoginRequest("validuser" + suffix, "testPassword123!");
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error()).isNull();

            Map<String, Object> data = (Map<String, Object>) response.getBody().data();
            assertThat(data.get("accessToken")).isNotNull();
            assertThat(data.get("refreshToken")).isNotNull();
            assertThat(data.get("tokenType")).isEqualTo("Bearer");

            authToken = (String) data.get("accessToken");
        }

        @Test
        @DisplayName("should reject login with invalid password")
        void loginWithInvalidPassword_returns401() {
            String suffix = "L2" + System.currentTimeMillis();
            RegisterRequest registerRequest = new RegisterRequest(
                    "badpass" + suffix, "badpass" + suffix + "@example.com",
                    "testPassword123!", "Test", "User");
            restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

            LoginRequest loginRequest = new LoginRequest("badpass" + suffix, "wrongpassword!");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject login with non-existent username")
        void loginWithNonExistentUser_returns401() {
            LoginRequest loginRequest = new LoginRequest("nonexistent", "testPassword123!");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject login with blank username")
        void loginWithBlankUsername_returns400() {
            LoginRequest loginRequest = new LoginRequest("", "testPassword123!");

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("should refresh token with valid refresh token")
        void refreshWithValidToken_returnsNewTokens() {
            String suffix = "R1" + System.currentTimeMillis();
            RegisterRequest registerRequest = new RegisterRequest(
                    "refreshuser" + suffix, "refreshuser" + suffix + "@example.com",
                    "testPassword123!", "Test", "User");
            restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

            LoginRequest loginRequest = new LoginRequest("refreshuser" + suffix, "testPassword123!");
            ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);
            Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().data();
            String refreshToken = (String) loginData.get("refreshToken");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(
                    Map.of("refreshToken", refreshToken), headers);

            ResponseEntity<ApiResponse> refreshResponse = restTemplate.exchange(
                    "/v1/auth/refresh", HttpMethod.POST, requestEntity, ApiResponse.class);

            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(refreshResponse.getBody()).isNotNull();
            Map<String, Object> data = (Map<String, Object>) refreshResponse.getBody().data();
            assertThat(data.get("accessToken")).isNotNull();
        }

        @Test
        @DisplayName("should reject refresh with invalid token")
        void refreshWithInvalidToken_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(
                    Map.of("refreshToken", "invalid-refresh-token"), headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/auth/refresh", HttpMethod.POST, requestEntity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /v1/auth/me")
    class MeTests {

        @Test
        @DisplayName("should return current user details with valid token")
        void getCurrentUser_withValidToken_returnsUser() {
            String suffix = "M1" + System.currentTimeMillis();
            RegisterRequest registerRequest = new RegisterRequest(
                    "meuser" + suffix, "meuser" + suffix + "@example.com",
                    "testPassword123!", "MeFirst", "MeLast");
            restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

            LoginRequest loginRequest = new LoginRequest("meuser" + suffix, "testPassword123!");
            ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                    "/v1/auth/login", loginRequest, ApiResponse.class);
            Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().data();
            String token = (String) loginData.get("accessToken");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/auth/me", HttpMethod.GET, requestEntity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should reject without token")
        void getCurrentUser_withoutToken_returns401() {
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                    "/v1/auth/me", ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
