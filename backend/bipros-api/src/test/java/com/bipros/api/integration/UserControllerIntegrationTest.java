package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
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
@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("bipros_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    private String registerAndGetToken(String suffix) {
        RegisterRequest registerRequest = new RegisterRequest(
                "user" + suffix, "user" + suffix + "@example.com",
                "testPassword123!", "Test", "User");
        restTemplate.postForEntity("/v1/auth/register", registerRequest, ApiResponse.class);

        LoginRequest loginRequest = new LoginRequest("user" + suffix, "testPassword123!");
        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                "/v1/auth/login", loginRequest, ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) loginResponse.getBody().data();
        return (String) data.get("accessToken");
    }

    @Nested
    @DisplayName("GET /v1/users/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return current user with valid token")
        void getCurrentUser_withValidToken_returns200() {
            String suffix = "UM" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users/me", HttpMethod.GET, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error()).isNull();
        }

        @Test
        @DisplayName("should return 401 without token")
        void getCurrentUser_withoutToken_returns401() {
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                    "/v1/users/me", ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /v1/users")
    class ListUsersTests {

        @Test
        @DisplayName("should return paginated user list for admin")
        void listUsers_asAdmin_returns200() {
            String suffix = "UL" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users", HttpMethod.GET, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should return 401 without auth")
        void listUsers_withoutAuth_returns401() {
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                    "/v1/users", ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /v1/users/{id}")
    class GetUserByIdTests {

        @Test
        @DisplayName("should get user by ID with admin token")
        void getUserById_withValidId_returns200() {
            String suffix = "UG" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // First get current user to find the user ID
            ResponseEntity<ApiResponse> meResponse = restTemplate.exchange(
                    "/v1/users/me", HttpMethod.GET, entity, ApiResponse.class);
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> userData = (Map<String, Object>) meResponse.getBody().data();
            String userId = (String) userData.get("id");

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users/" + userId, HttpMethod.GET, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should return 404 for non-existent user ID")
        void getUserById_withInvalidId_returns404() {
            String suffix = "UINV" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users/00000000-0000-0000-0000-000000000000",
                    HttpMethod.GET, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /v1/users/{id}")
    class UpdateUserProfileTests {

        @Test
        @DisplayName("should update user profile")
        void updateProfile_returns200() {
            String suffix = "UP" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get user ID
            ResponseEntity<ApiResponse> meResponse = restTemplate.exchange(
                    "/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);
            Map<String, Object> userData = (Map<String, Object>) meResponse.getBody().data();
            String userId = (String) userData.get("id");

            Map<String, Object> updateBody = Map.of(
                    "firstName", "UpdatedFirst",
                    "lastName", "UpdatedLast");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updateBody, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users/" + userId, HttpMethod.PUT, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("GET /v1/users/{id}/access")
    class GetUserAccessTests {

        @Test
        @DisplayName("should return user access details")
        void getUserAccess_returns200() {
            String suffix = "UA" + System.currentTimeMillis();
            String token = registerAndGetToken(suffix);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse> meResponse = restTemplate.exchange(
                    "/v1/users/me", HttpMethod.GET, entity, ApiResponse.class);
            Map<String, Object> userData = (Map<String, Object>) meResponse.getBody().data();
            String userId = (String) userData.get("id");

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/users/" + userId + "/access", HttpMethod.GET, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
