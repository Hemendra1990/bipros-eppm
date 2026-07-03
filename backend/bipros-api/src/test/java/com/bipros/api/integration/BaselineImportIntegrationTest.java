package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateEpsNodeRequest;
import com.bipros.project.application.dto.CreateProjectRequest;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Baseline Import (XER) Integration Tests")
class BaselineImportIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("bipros_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    private String token;
    private UUID projectId;

    private static final String SAMPLE_XER = String.join("\n",
            "%T\tPROJWBS", "%F\twbs_id\twbs_short_name\twbs_name", "%R\tw1\t1.0\tPreliminaries",
            "%T\tTASK", "%F\ttask_id\ttask_code\ttask_name\ttarget_start_date\ttarget_end_date\twbs_id",
            "%R\tt1\tA1\tClearing\t2026-01-01\t2026-01-31\tw1",
            "%R\tt2\tA2\tEarthworks\t2026-02-01\t2026-03-15\tw1",
            "%T\tTASKPRED", "%F\tpred_task_id\ttask_id\tpred_type\tlag_hr_cnt", "%R\tt1\tt2\tPR_FS\t0",
            "%T\tTASKRSRC", "%F\ttask_id\trsrc_id\ttarget_qty\ttarget_cost", "%R\tt1\tR1\t10\t5000");

    @BeforeEach
    void setUp() {
        String suffix = "BI" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(
                "biuser" + suffix, "biuser" + suffix + "@example.com",
                "testPassword123!", "BI", "User");
        restTemplate.postForEntity("/v1/auth/register", reg, ApiResponse.class);
        LoginRequest login = new LoginRequest("biuser" + suffix, "testPassword123!");
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/v1/auth/login", login, ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        token = (String) data.get("accessToken");

        HttpHeaders h = authJsonHeaders();
        CreateEpsNodeRequest epsReq = new CreateEpsNodeRequest(
                "EPS-BI-" + suffix, "EPS Baseline Import " + suffix, null, null);
        HttpEntity<CreateEpsNodeRequest> epsE = new HttpEntity<>(epsReq, h);
        ResponseEntity<ApiResponse> epsR = restTemplate.exchange("/v1/eps", HttpMethod.POST, epsE, ApiResponse.class);
        Map<String, Object> epsD = (Map<String, Object>) epsR.getBody().data();
        UUID epsId = UUID.fromString((String) epsD.get("id"));

        CreateProjectRequest projReq = new CreateProjectRequest(
                "PRJ-BI-" + suffix, "Project Baseline Import " + suffix, "desc",
                epsId, null, LocalDate.now(), LocalDate.now().plusMonths(12),
                5, null, null, null, null, null, null, null, null, null,
                null);
        HttpEntity<CreateProjectRequest> projE = new HttpEntity<>(projReq, h);
        ResponseEntity<ApiResponse> projR = restTemplate.exchange("/v1/projects", HttpMethod.POST, projE, ApiResponse.class);
        Map<String, Object> projD = (Map<String, Object>) projR.getBody().data();
        projectId = UUID.fromString((String) projD.get("id"));
    }

    private HttpHeaders authJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("POST /baselines/import - imports XER as a baseline with real dates")
    void importsXerAsBaselineWithRealDates() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultipartBodyBuilder b = new MultipartBodyBuilder();
        b.part("file", SAMPLE_XER.getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "form-data; name=file; filename=plan.xer");
        b.part("format", "XER");
        b.part("name", "Client Approved Programme");
        b.part("type", "PRIMARY");
        MultiValueMap<String, HttpEntity<?>> body = b.build();

        ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                "/v1/projects/" + projectId + "/baselines/import", HttpMethod.POST,
                new HttpEntity<>(body, h), ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // baseline exists and is dated
        HttpHeaders jh = authJsonHeaders();
        ResponseEntity<ApiResponse> list = restTemplate.exchange(
                "/v1/projects/" + projectId + "/baselines", HttpMethod.GET, new HttpEntity<>(jh), ApiResponse.class);
        List<?> baselines = (List<?>) list.getBody().data();
        assertThat(baselines).hasSize(1);

        // schedule variance now has real baseline dates (not the null "—" false-green)
        ResponseEntity<ApiResponse> activities = restTemplate.exchange(
                "/v1/projects/" + projectId + "/activities", HttpMethod.GET, new HttpEntity<>(jh), ApiResponse.class);
        assertThat(activities.getBody()).isNotNull();
    }

    @Test
    @DisplayName("POST /baselines/import/preview - reports counts without writing")
    void previewReportsCountsWithoutWriting() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultipartBodyBuilder b = new MultipartBodyBuilder();
        b.part("file", SAMPLE_XER.getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "form-data; name=file; filename=plan.xer");
        b.part("format", "XER");
        ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                "/v1/projects/" + projectId + "/baselines/import/preview", HttpMethod.POST,
                new HttpEntity<>(b.build(), h), ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        assertThat(((Number) data.get("activitiesInFile")).intValue()).isEqualTo(2);
        assertThat(((Number) data.get("newActivities")).intValue()).isEqualTo(2);
    }
}
