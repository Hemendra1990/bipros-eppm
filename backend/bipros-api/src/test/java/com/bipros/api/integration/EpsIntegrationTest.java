package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateEpsNodeRequest;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("EPS Integration Tests")
class EpsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("bipros_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    private String authenticate() {
        String suffix = "EPS" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(
                "epsuser" + suffix, "epsuser" + suffix + "@example.com",
                "testPassword123!", "EPS", "User");
        restTemplate.postForEntity("/v1/auth/register", reg, ApiResponse.class);
        LoginRequest login = new LoginRequest("epsuser" + suffix, "testPassword123!");
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/v1/auth/login", login, ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("accessToken");
    }

    private HttpHeaders authJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(authenticate());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Nested
    @DisplayName("GET /v1/eps")
    class GetEpsTreeTests {

        @Test
        @DisplayName("should return EPS tree")
        void getEpsTree_returns200() {
            HttpEntity<Void> entity = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps", HttpMethod.GET, entity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should return 401 without auth")
        void getEpsTree_withoutAuth_returns401() {
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                    "/v1/eps", ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /v1/eps")
    class CreateEpsNodeTests {

        @Test
        @DisplayName("should create a root EPS node")
        void createRootEpsNode_returns201() {
            String suffix = "C" + System.currentTimeMillis();
            CreateEpsNodeRequest request = new CreateEpsNodeRequest(
                    "EPS-" + suffix, "EPS Node " + suffix, null, null);

            HttpEntity<CreateEpsNodeRequest> entity = new HttpEntity<>(request, authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, entity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error()).isNull();
        }

        @Test
        @DisplayName("should create a child EPS node")
        void createChildEpsNode_returns201() {
            String suffix = "CH" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();

            // Create root first
            CreateEpsNodeRequest rootReq = new CreateEpsNodeRequest(
                    "EPS-R-" + suffix, "EPS Root " + suffix, null, null);
            HttpEntity<CreateEpsNodeRequest> rootEntity = new HttpEntity<>(rootReq, headers);
            ResponseEntity<ApiResponse> rootResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, rootEntity, ApiResponse.class);
            Map<String, Object> rootData = (Map<String, Object>) rootResp.getBody().data();
            UUID rootId = UUID.fromString((String) rootData.get("id"));

            // Create child
            CreateEpsNodeRequest childReq = new CreateEpsNodeRequest(
                    "EPS-CH-" + suffix, "EPS Child " + suffix, rootId, null);
            HttpEntity<CreateEpsNodeRequest> childEntity = new HttpEntity<>(childReq, headers);
            ResponseEntity<ApiResponse> childResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, childEntity, ApiResponse.class);

            assertThat(childResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should reject duplicate EPS code")
        void createDuplicateCode_returns409() {
            String suffix = "DUP" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();
            CreateEpsNodeRequest req = new CreateEpsNodeRequest(
                    "EPS-DUP-" + suffix, "EPS Dup " + suffix, null, null);

            HttpEntity<CreateEpsNodeRequest> entity = new HttpEntity<>(req, headers);
            restTemplate.exchange("/v1/eps", HttpMethod.POST, entity, ApiResponse.class);

            // Duplicate
            ResponseEntity<ApiResponse> dupResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, entity, ApiResponse.class);
            assertThat(dupResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should reject blank code")
        void createWithBlankCode_returns400() {
            CreateEpsNodeRequest req = new CreateEpsNodeRequest("", "Blank Code", null, null);
            HttpEntity<CreateEpsNodeRequest> entity = new HttpEntity<>(req, authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, entity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should reject blank name")
        void createWithBlankName_returns400() {
            CreateEpsNodeRequest req = new CreateEpsNodeRequest("EPS-BN", "", null, null);
            HttpEntity<CreateEpsNodeRequest> entity = new HttpEntity<>(req, authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, entity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /v1/eps/{id}")
    class GetEpsNodeTests {

        @Test
        @DisplayName("should get EPS node by ID")
        void getEpsNode_returns200() {
            String suffix = "G" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();
            CreateEpsNodeRequest req = new CreateEpsNodeRequest(
                    "EPS-G-" + suffix, "EPS Get " + suffix, null, null);
            HttpEntity<CreateEpsNodeRequest> createEntity = new HttpEntity<>(req, headers);
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, createEntity, ApiResponse.class);
            Map<String, Object> data = (Map<String, Object>) createResp.getBody().data();
            String id = (String) data.get("id");

            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/" + id, HttpMethod.GET, getEntity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> epsData = (Map<String, Object>) response.getBody().data();
            assertThat(epsData.get("code")).isEqualTo("EPS-G-" + suffix);
        }

        @Test
        @DisplayName("should return 404 for non-existent EPS node")
        void getNonExistentEpsNode_returns404() {
            HttpEntity<Void> entity = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/00000000-0000-0000-0000-000000000000",
                    HttpMethod.GET, entity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /v1/eps/{id}")
    class UpdateEpsNodeTests {

        @Test
        @DisplayName("should update EPS node name")
        void updateEpsNode_returns200() {
            String suffix = "U" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();
            CreateEpsNodeRequest createReq = new CreateEpsNodeRequest(
                    "EPS-U-" + suffix, "EPS Update " + suffix, null, null);
            HttpEntity<CreateEpsNodeRequest> createEntity = new HttpEntity<>(createReq, headers);
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, createEntity, ApiResponse.class);
            Map<String, Object> data = (Map<String, Object>) createResp.getBody().data();
            String id = (String) data.get("id");

            UpdateEpsNodeRequest updateReq = new UpdateEpsNodeRequest("Updated Name", null, 0, null);
            HttpEntity<UpdateEpsNodeRequest> updateEntity = new HttpEntity<>(updateReq, headers);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/" + id, HttpMethod.PUT, updateEntity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("PATCH /v1/eps/{id}/move")
    class MoveEpsNodeTests {

        @Test
        @DisplayName("should move EPS node to new parent")
        void moveEpsNode_returns200() {
            String suffix = "MV" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();

            CreateEpsNodeRequest rootReq = new CreateEpsNodeRequest(
                    "EPS-MV-R-" + suffix, "MV Root", null, null);
            HttpEntity<CreateEpsNodeRequest> rootEntity = new HttpEntity<>(rootReq, headers);
            ResponseEntity<ApiResponse> rootResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, rootEntity, ApiResponse.class);
            Map<String, Object> rootData = (Map<String, Object>) rootResp.getBody().data();
            String rootId = (String) rootData.get("id");

            CreateEpsNodeRequest childReq = new CreateEpsNodeRequest(
                    "EPS-MV-C-" + suffix, "MV Child", null, null);
            HttpEntity<CreateEpsNodeRequest> childEntity = new HttpEntity<>(childReq, headers);
            ResponseEntity<ApiResponse> childResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, childEntity, ApiResponse.class);
            Map<String, Object> childData = (Map<String, Object>) childResp.getBody().data();
            String childId = (String) childData.get("id");

            Map<String, Object> moveBody = Map.of("parentId", rootId);
            HttpEntity<Map<String, Object>> moveEntity = new HttpEntity<>(moveBody, headers);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/" + childId + "/move", HttpMethod.PATCH, moveEntity, ApiResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("DELETE /v1/eps/{id}")
    class DeleteEpsNodeTests {

        @Test
        @DisplayName("should delete EPS node without children")
        void deleteLeafEpsNode_returns204() {
            String suffix = "D" + System.currentTimeMillis();
            HttpHeaders headers = authJsonHeaders();
            CreateEpsNodeRequest req = new CreateEpsNodeRequest(
                    "EPS-D-" + suffix, "EPS Delete " + suffix, null, null);
            HttpEntity<CreateEpsNodeRequest> createEntity = new HttpEntity<>(req, headers);
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    "/v1/eps", HttpMethod.POST, createEntity, ApiResponse.class);
            Map<String, Object> data = (Map<String, Object>) createResp.getBody().data();
            String id = (String) data.get("id");

            HttpEntity<Void> deleteEntity = new HttpEntity<>(headers);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/" + id, HttpMethod.DELETE, deleteEntity, ApiResponse.class);

            assertThat(response.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);
        }

        @Test
        @DisplayName("should return 404 for non-existent EPS node")
        void deleteNonExistent_returns404() {
            HttpEntity<Void> entity = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    "/v1/eps/00000000-0000-0000-0000-000000000000",
                    HttpMethod.DELETE, entity, ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
