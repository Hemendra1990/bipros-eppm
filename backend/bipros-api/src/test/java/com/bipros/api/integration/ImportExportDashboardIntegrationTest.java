package com.bipros.api.integration;

import com.bipros.common.dto.ApiResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Import-Export & Dashboard Integration Tests")
class ImportExportDashboardIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("bipros_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    private String token;

    @BeforeEach
    void setUp() {
        String suffix = "IED" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(
                "ieduser" + suffix, "ieduser" + suffix + "@example.com",
                "testPassword123!", "IED", "User");
        restTemplate.postForEntity("/v1/auth/register", reg, ApiResponse.class);
        LoginRequest login = new LoginRequest("ieduser" + suffix, "testPassword123!");
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/v1/auth/login", login, ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        token = (String) data.get("accessToken");
    }

    private HttpHeaders authJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ===================== IMPORT-EXPORT TESTS =====================

    @Nested
    @DisplayName("Import-Export")
    class ImportExportTests {

        @Test
        @DisplayName("GET /v1/import-export/jobs - list jobs")
        void listJobs_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/import-export/jobs", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /v1/import-export/export - export project")
        void exportProject_returns200or400() {
            Map<String, Object> body = Map.of(
                    "projectId", "00000000-0000-0000-0000-000000000000",
                    "format", "P6_XML",
                    "exportOptions", Map.of());
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/import-export/export", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /v1/import-export/projects/{id}/export/p6xml - P6 XML export")
        void exportP6Xml_returns200or404() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    "/v1/import-export/projects/00000000-0000-0000-0000-000000000000/export/p6xml",
                    HttpMethod.GET, e, String.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /v1/import-export/projects/{id}/export/msp - MSP XML export")
        void exportMspXml_returns200or404() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    "/v1/import-export/projects/00000000-0000-0000-0000-000000000000/export/msp",
                    HttpMethod.GET, e, String.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /v1/import-export/projects/{id}/export/excel - Excel export")
        void exportExcel_returns200or404() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    "/v1/import-export/projects/00000000-0000-0000-0000-000000000000/export/excel",
                    HttpMethod.GET, e, byte[].class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /v1/import-export/projects/{id}/export/csv - CSV export")
        void exportCsv_returns200or404() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    "/v1/import-export/projects/00000000-0000-0000-0000-000000000000/export/csv",
                    HttpMethod.GET, e, byte[].class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        }
    }

    // ===================== DASHBOARD TESTS =====================

    @Nested
    @DisplayName("Dashboards")
    class DashboardTests {

        @Test
        @DisplayName("GET /v1/dashboards - list")
        void listDashboards_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/dashboards", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/dashboards/{tier} - dashboard by tier")
        void getDashboardByTier_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/dashboards/PORTFOLIO", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/dashboards/{tier}/kpis - KPIs by tier")
        void getKpis_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/dashboards/PORTFOLIO/kpis", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/dashboards/kpi-definitions - KPI definitions")
        void getKpiDefinitions_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/dashboards/kpi-definitions", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== PORTFOLIO REPORT TESTS =====================

    @Nested
    @DisplayName("Portfolio Reports")
    class PortfolioReportTests {

        @Test
        @DisplayName("GET /v1/portfolio/evm-rollup")
        void getEvmRollup_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/evm-rollup", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/scorecard")
        void getScorecard_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/scorecard", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/delayed-projects")
        void getDelayedProjects_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/delayed-projects", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/cost-overrun-projects")
        void getCostOverrun_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/cost-overrun-projects", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/funding-utilization")
        void getFundingUtilization_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/funding-utilization", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/contractor-league")
        void getContractorLeague_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/contractor-league", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/risk-heatmap")
        void getRiskHeatmap_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/risk-heatmap", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/cash-flow-outlook")
        void getCashFlowOutlook_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/cash-flow-outlook", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/compliance")
        void getCompliance_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/compliance", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolio/schedule-health")
        void getScheduleHealth_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolio/schedule-health", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
