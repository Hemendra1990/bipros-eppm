package com.bipros.common.exception;

import com.bipros.common.dto.ApiError;
import com.bipros.common.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("HttpMessageNotReadableException (malformed request body)")
    class MalformedBody {

        @Test
        @DisplayName("returns HTTP 400 with MALFORMED_JSON error code")
        void returns400WithMalformedJsonCode() {
            // Simulate what Spring produces when the client posts unparseable JSON
            JsonParseException parse = new JsonParseException(null,
                    "Unexpected character (')' (code 41))", JsonLocation.NA);
            HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                    "JSON parse error: " + parse.getOriginalMessage(),
                    parse,
                    new MockHttpInputMessage(new byte[0]));

            ResponseEntity<ApiResponse<Void>> resp = handler.handleMalformedRequest(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            ApiError err = resp.getBody().error();
            assertThat(err).isNotNull();
            assertThat(err.code()).isEqualTo("MALFORMED_JSON");
            assertThat(err.message()).contains("Unexpected character");
        }
    }

    @Nested
    @DisplayName("MissingServletRequestParameterException")
    class MissingParam {

        @Test
        @DisplayName("returns HTTP 400 naming the missing parameter")
        void returns400WithParamName() throws NoSuchMethodException {
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("percentComplete", "Double");

            ResponseEntity<ApiResponse<Void>> resp = handler.handleMissingParam(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("MISSING_PARAMETER");
            assertThat(err.message()).contains("percentComplete");
        }
    }

    @Nested
    @DisplayName("DataIntegrityViolationException (DB unique/FK/check constraint)")
    class DataIntegrity {

        @Test
        @DisplayName("unique-constraint violation returns HTTP 409 DUPLICATE_RESOURCE")
        void uniqueConstraint_returns409() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException(
                    "could not execute statement",
                    new RuntimeException("duplicate key value violates unique constraint \"uk_eps_code\""));

            ResponseEntity<ApiResponse<Void>> resp = handler.handleDataIntegrity(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("DUPLICATE_RESOURCE");
            assertThat(err.message()).contains("unique constraint");
        }

        @Test
        @DisplayName("non-unique constraint returns HTTP 409 DATA_INTEGRITY")
        void otherConstraint_returns409WithGenericCode() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException(
                    "foreign key violation",
                    new RuntimeException("violates foreign key constraint \"fk_project_eps\""));

            ResponseEntity<ApiResponse<Void>> resp = handler.handleDataIntegrity(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("DATA_INTEGRITY");
        }
    }

    @Nested
    @DisplayName("NoResourceFoundException (unknown path)")
    class NotFound {

        @Test
        @DisplayName("returns HTTP 404 with NOT_FOUND code and the requested path")
        void returns404() {
            NoResourceFoundException ex = new NoResourceFoundException(
                    HttpMethod.GET, "v1/projects/abc/critical-path");

            ResponseEntity<ApiResponse<Void>> resp = handler.handleNoResource(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("NOT_FOUND");
            assertThat(err.message()).contains("critical-path");
        }
    }

    @Nested
    @DisplayName("HttpRequestMethodNotSupportedException")
    class MethodNotAllowed {

        @Test
        @DisplayName("returns HTTP 405 naming the unsupported method")
        void returns405() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("GET", List.of("POST", "PUT"));

            ResponseEntity<ApiResponse<Void>> resp = handler.handleMethodNotSupported(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("METHOD_NOT_ALLOWED");
            assertThat(err.message()).contains("GET");
            // Spec-required Allow header listing supported methods
            assertThat(resp.getHeaders().get("Allow")).containsExactlyInAnyOrder("POST", "PUT");
        }
    }

    @Nested
    @DisplayName("Generic Exception handler")
    class Generic {

        @Test
        @DisplayName("default profile: returns HTTP 500 INTERNAL_ERROR with no stack leak")
        void prodProfile_hidesStackTrace() {
            ReflectionTestUtils.setField(handler, "includeExceptionDetail", false);
            RuntimeException ex = new IllegalStateException("something deep broke");

            ResponseEntity<ApiResponse<Void>> resp = handler.handleGeneral(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("INTERNAL_ERROR");
            assertThat(err.message()).isEqualTo("An unexpected error occurred");
            // Must NOT leak the specific exception type or message
            assertThat(err.message()).doesNotContain("IllegalStateException");
            assertThat(err.message()).doesNotContain("something deep broke");
        }

        @Test
        @DisplayName("dev profile: includes exception class + message + top frame in response")
        void devProfile_includesExceptionDetail() {
            ReflectionTestUtils.setField(handler, "includeExceptionDetail", true);
            RuntimeException ex = new IllegalStateException("something deep broke");

            ResponseEntity<ApiResponse<Void>> resp = handler.handleGeneral(ex);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            ApiError err = resp.getBody().error();
            assertThat(err.code()).isEqualTo("INTERNAL_ERROR");
            assertThat(err.message()).contains("IllegalStateException");
            assertThat(err.message()).contains("something deep broke");
        }
    }
}
