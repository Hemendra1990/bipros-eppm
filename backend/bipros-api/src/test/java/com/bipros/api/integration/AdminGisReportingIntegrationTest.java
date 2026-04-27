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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Admin / GIS / Reporting Integration Tests")
class AdminGisReportingIntegrationTest {

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
        String suffix = "AGR" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(
                "agruser" + suffix, "agruser" + suffix + "@example.com",
                "testPassword123!", "AGR", "User");
        restTemplate.postForEntity("/v1/auth/register", reg, ApiResponse.class);
        LoginRequest login = new LoginRequest("agruser" + suffix, "testPassword123!");
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

    // ===================== ORGANISATION TESTS =====================

    @Nested
    @DisplayName("Organisations")
    class OrganisationTests {

        @Test
        @DisplayName("POST /v1/organisations - create")
        void createOrganisation_returns201() {
            Map<String, Object> body = Map.of(
                    "name", "Test Org " + System.currentTimeMillis(),
                    "code", "ORG001",
                    "organisationType", "CONTRACTOR",
                    "active", true);
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/organisations", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/organisations - list")
        void listOrganisations_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/organisations", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/organisations/as-contractors")
        void listAsContractors_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/organisations/as-contractors", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== UNIT OF MEASURE TESTS =====================

    @Nested
    @DisplayName("Units of Measure")
    class UnitOfMeasureTests {

        @Test
        @DisplayName("POST /v1/admin/units-of-measure - create")
        void createUom_returns201() {
            Map<String, Object> body = Map.of(
                    "code", "UOM-" + System.currentTimeMillis() % 10000,
                    "name", "Test Unit",
                    "abbreviation", "TU");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/units-of-measure", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/admin/units-of-measure - list")
        void listUoms_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/units-of-measure", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== CURRENCY TESTS =====================

    @Nested
    @DisplayName("Currencies")
    class CurrencyTests {

        @Test
        @DisplayName("POST /v1/admin/currencies - create")
        void createCurrency_returns201() {
            Map<String, Object> body = Map.of(
                    "code", "CUR",
                    "name", "Custom Currency",
                    "symbol", "CC",
                    "exchangeRate", 1.0);
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/currencies", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/admin/currencies - list")
        void listCurrencies_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/currencies", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/admin/currencies/convert - convert")
        void convertCurrency_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/currencies/convert?from=USD&to=INR&amount=100",
                    HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== ADMIN CATEGORY TESTS =====================

    @Nested
    @DisplayName("Admin Categories")
    class AdminCategoryTests {

        @Test
        @DisplayName("POST /v1/admin/categories - create")
        void createCategory_returns201() {
            Map<String, Object> body = Map.of(
                    "categoryType", "METADATA",
                    "code", "CAT-" + System.currentTimeMillis() % 10000,
                    "name", "Test Category");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/categories", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/admin/categories - list")
        void listCategories_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/categories", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== GLOBAL SETTINGS TESTS =====================

    @Nested
    @DisplayName("Global Settings")
    class GlobalSettingTests {

        @Test
        @DisplayName("POST /v1/admin/settings - create")
        void createSetting_returns201() {
            Map<String, Object> body = Map.of(
                    "settingKey", "test.key." + System.currentTimeMillis(),
                    "settingValue", "test-value",
                    "category", "GENERAL");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/settings", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/admin/settings - list")
        void listSettings_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/admin/settings", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== GIS TESTS =====================

    @Nested
    @DisplayName("GIS Layers")
    class GisTests {

        @Test
        @DisplayName("GET /projects/{id}/gis/layers - list")
        void listLayers_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/projects/00000000-0000-0000-0000-000000000000/gis/layers",
                    HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /projects/{id}/gis/polygons - list")
        void listPolygons_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/projects/00000000-0000-0000-0000-000000000000/gis/polygons",
                    HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /projects/{id}/gis/polygons/geojson - GeoJSON")
        void getGeoJson_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/projects/00000000-0000-0000-0000-000000000000/gis/polygons/geojson",
                    HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== INTEGRATION CONFIG TESTS =====================

    @Nested
    @DisplayName("Integration Configs")
    class IntegrationConfigTests {

        @Test
        @DisplayName("GET /v1/integrations - list")
        void listConfigs_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/integrations", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /v1/integrations - create")
        void createConfig_returns201() {
            Map<String, Object> body = Map.of(
                    "systemCode", "PFMS",
                    "systemName", "Public Financial Management System",
                    "baseUrl", "https://pfms.example.com",
                    "isEnabled", true,
                    "authType", "API_KEY",
                    "status", "ACTIVE");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/integrations", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ===================== REPORTING TESTS =====================

    @Nested
    @DisplayName("Reports")
    class ReportTests {

        @Test
        @DisplayName("GET /v1/reports - list")
        void listReports_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /v1/reports/definitions - create definition")
        void createDefinition_returns201() {
            Map<String, Object> body = Map.of(
                    "name", "Report-" + System.currentTimeMillis(),
                    "reportType", "S_CURVE",
                    "configJson", "{}");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/definitions", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/reports/definitions - list definitions")
        void listDefinitions_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/definitions", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/reports/monthly-progress")
        void getMonthlyProgress_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/monthly-progress", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/reports/evm")
        void getEvmReport_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/evm", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/reports/cash-flow")
        void getCashFlowReport_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/cash-flow", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/reports/risk-register")
        void getRiskRegisterReport_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/reports/risk-register", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== ANALYTICS TESTS =====================

    @Nested
    @DisplayName("Analytics")
    class AnalyticsTests {

        @Test
        @DisplayName("POST /v1/analytics/query - NL query")
        void submitQuery_returns200() {
            Map<String, Object> body = Map.of("query", "Show schedule variance");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/analytics/query", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/analytics/queries - query history")
        void getQueryHistory_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/analytics/queries", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
