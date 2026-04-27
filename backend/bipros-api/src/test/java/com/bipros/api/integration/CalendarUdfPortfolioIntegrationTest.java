package com.bipros.api.integration;

import com.bipros.calendar.application.dto.CalendarExceptionRequest;
import com.bipros.calendar.application.dto.CreateCalendarRequest;
import com.bipros.calendar.domain.model.DayType;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Calendar / UDF / Portfolio Integration Tests")
class CalendarUdfPortfolioIntegrationTest {

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
        String suffix = "CUP" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(
                "cupuser" + suffix, "cupuser" + suffix + "@example.com",
                "testPassword123!", "CUP", "User");
        restTemplate.postForEntity("/v1/auth/register", reg, ApiResponse.class);
        LoginRequest login = new LoginRequest("cupuser" + suffix, "testPassword123!");
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

    // ===================== CALENDAR TESTS =====================

    @Nested
    @DisplayName("Calendars")
    class CalendarTests {

        @Test
        @DisplayName("POST /v1/calendars - create")
        void createCalendar_returns201() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "Calendar-" + System.currentTimeMillis(), "Test calendar", null,
                    null, null, null, 8.0, 5);
            HttpEntity<CreateCalendarRequest> e = new HttpEntity<>(req, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/calendars - list")
        void listCalendars_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/calendars/{id} - get by ID")
        void getCalendar_returns200() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-G-" + System.currentTimeMillis(), "Get Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> getE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id, HttpMethod.GET, getE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("PUT /v1/calendars/{id} - update")
        void updateCalendar_returns200() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-U-" + System.currentTimeMillis(), "Update Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            CreateCalendarRequest updateReq = new CreateCalendarRequest(
                    "CAL-U-" + System.currentTimeMillis(), "Updated Calendar", null,
                    null, null, null, 10.0, 6);
            HttpEntity<CreateCalendarRequest> updateE = new HttpEntity<>(updateReq, h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id, HttpMethod.PUT, updateE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DELETE /v1/calendars/{id} - delete")
        void deleteCalendar_returns204() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-D-" + System.currentTimeMillis(), "Delete Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> delE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id, HttpMethod.DELETE, delE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);
        }

        @Test
        @DisplayName("PUT /v1/calendars/{id}/work-week - set work week")
        void setWorkWeek_returns200() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-WW-" + System.currentTimeMillis(), "Work Week Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            Map<String, Object> wwBody = Map.of(
                    "monday", true, "tuesday", true, "wednesday", true,
                    "thursday", true, "friday", true, "saturday", false, "sunday", false);
            HttpEntity<Map<String, Object>> wwE = new HttpEntity<>(wwBody, h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id + "/work-week",
                    HttpMethod.PUT, wwE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /v1/calendars/{id}/exceptions - add exception")
        void addException_returns200() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-EX-" + System.currentTimeMillis(), "Exception Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            CalendarExceptionRequest exReq = new CalendarExceptionRequest(
                    LocalDate.now().plusDays(15), DayType.NON_WORKING,
                    "Test Holiday",
                    LocalTime.of(9, 0), LocalTime.of(17, 0),
                    null, null);
            HttpEntity<CalendarExceptionRequest> exE = new HttpEntity<>(exReq, h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id + "/exceptions",
                    HttpMethod.POST, exE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/calendars/{id}/exceptions - get exceptions in range")
        void getExceptions_returns200() {
            CreateCalendarRequest req = new CreateCalendarRequest(
                    "CAL-EG-" + System.currentTimeMillis(), "Exception Get Calendar", null,
                    null, null, null, 8.0, 5);
            HttpHeaders h = authJsonHeaders();
            HttpEntity<CreateCalendarRequest> createE = new HttpEntity<>(req, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/calendars", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> getE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/calendars/" + id + "/exceptions",
                    HttpMethod.GET, getE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== UDF TESTS =====================

    @Nested
    @DisplayName("UDF")
    class UdfTests {

        @Test
        @DisplayName("GET /v1/udf - catalog")
        void getCatalog_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/udf", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /v1/udf/fields - create field")
        void createField_returns201() {
            Map<String, Object> body = Map.of(
                    "name", "CustomField-" + System.currentTimeMillis(),
                    "dataType", "TEXT",
                    "subject", "ACTIVITY");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/udf/fields", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/udf/fields - list fields")
        void listFields_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/udf/fields", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("PUT /v1/udf/fields/{fieldId} - update field")
        void updateField_returns200() {
            Map<String, Object> createBody = Map.of(
                    "name", "UDF-U-" + System.currentTimeMillis(),
                    "dataType", "TEXT",
                    "subject", "ACTIVITY");
            HttpHeaders h = authJsonHeaders();
            HttpEntity<Map<String, Object>> createE = new HttpEntity<>(createBody, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/udf/fields", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            Map<String, Object> updateBody = Map.of(
                    "name", "Updated UDF",
                    "dataType", "NUMBER",
                    "subject", "ACTIVITY");
            HttpEntity<Map<String, Object>> updateE = new HttpEntity<>(updateBody, h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/udf/fields/" + id, HttpMethod.PUT, updateE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DELETE /v1/udf/fields/{fieldId} - delete field")
        void deleteField_returns204() {
            Map<String, Object> createBody = Map.of(
                    "name", "UDF-D-" + System.currentTimeMillis(),
                    "dataType", "TEXT",
                    "subject", "ACTIVITY");
            HttpHeaders h = authJsonHeaders();
            HttpEntity<Map<String, Object>> createE = new HttpEntity<>(createBody, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/udf/fields", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> delE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/udf/fields/" + id, HttpMethod.DELETE, delE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);
        }
    }

    // ===================== PORTFOLIO TESTS =====================

    @Nested
    @DisplayName("Portfolios")
    class PortfolioTests {

        @Test
        @DisplayName("POST /v1/portfolios - create")
        void createPortfolio_returns201() {
            Map<String, Object> body = Map.of(
                    "name", "Portfolio-" + System.currentTimeMillis(),
                    "description", "Test portfolio");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolios", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/portfolios - list")
        void listPortfolios_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolios", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /v1/portfolios/{id} - get by ID")
        void getPortfolio_returns200() {
            Map<String, Object> body = Map.of(
                    "name", "PF-G-" + System.currentTimeMillis(),
                    "description", "Get portfolio");
            HttpHeaders h = authJsonHeaders();
            HttpEntity<Map<String, Object>> createE = new HttpEntity<>(body, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/portfolios", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> getE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolios/" + id, HttpMethod.GET, getE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("PUT /v1/portfolios/{id} - update")
        void updatePortfolio_returns200() {
            Map<String, Object> body = Map.of(
                    "name", "PF-U-" + System.currentTimeMillis(),
                    "description", "Update portfolio");
            HttpHeaders h = authJsonHeaders();
            HttpEntity<Map<String, Object>> createE = new HttpEntity<>(body, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/portfolios", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            Map<String, Object> updateBody = Map.of(
                    "name", "Updated Portfolio", "description", "Updated desc");
            HttpEntity<Map<String, Object>> updateE = new HttpEntity<>(updateBody, h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolios/" + id, HttpMethod.PUT, updateE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DELETE /v1/portfolios/{id} - delete")
        void deletePortfolio_returns204() {
            Map<String, Object> body = Map.of(
                    "name", "PF-D-" + System.currentTimeMillis(),
                    "description", "Delete portfolio");
            HttpHeaders h = authJsonHeaders();
            HttpEntity<Map<String, Object>> createE = new HttpEntity<>(body, h);
            ResponseEntity<ApiResponse> createR = restTemplate.exchange(
                    "/v1/portfolios", HttpMethod.POST, createE, ApiResponse.class);
            Map<String, Object> d = (Map<String, Object>) createR.getBody().data();
            String id = (String) d.get("id");

            HttpEntity<Void> delE = new HttpEntity<>(h);
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/portfolios/" + id, HttpMethod.DELETE, delE, ApiResponse.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);
        }
    }

    // ===================== SCORING MODEL TESTS =====================

    @Nested
    @DisplayName("Scoring Models")
    class ScoringModelTests {

        @Test
        @DisplayName("POST /v1/scoring-models - create")
        void createModel_returns201() {
            Map<String, Object> body = Map.of(
                    "name", "SM-" + System.currentTimeMillis(),
                    "description", "Test model");
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/scoring-models", HttpMethod.POST, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("GET /v1/scoring-models - list")
        void listModels_returns200() {
            HttpEntity<Void> e = new HttpEntity<>(authJsonHeaders());
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    "/v1/scoring-models", HttpMethod.GET, e, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
