package com.bipros.analytics.presentation;

import com.bipros.analytics.application.exception.LlmNotConfiguredException;
import com.bipros.analytics.application.exception.RateLimitExceededException;
import com.bipros.analytics.application.sql.SqlNotAllowedException;
import com.bipros.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-local backstop for analytics exceptions that escape the orchestrator's
 * own catch blocks. The orchestrator already turns {@link LlmNotConfiguredException}
 * and {@link SqlNotAllowedException} into structured responses, but a direct caller
 * (e.g. a future endpoint that uses {@code LlmProviderResolver} without going through
 * the orchestrator) would otherwise hit the generic 500 handler.
 *
 * <p>Higher precedence than the catch-all in {@code bipros-common} so these win.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AnalyticsExceptionHandler {

    @ExceptionHandler(LlmNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotConfigured(LlmNotConfiguredException ex) {
        log.warn("LLM_NOT_CONFIGURED: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("LLM_NOT_CONFIGURED",
                        "Configure an LLM provider on /settings/llm-providers to use the assistant."));
    }

    @ExceptionHandler(SqlNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSqlNotAllowed(SqlNotAllowedException ex) {
        log.warn("SQL_NOT_ALLOWED: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("SQL_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        long retry = ex.getRetryAfterSeconds();
        log.warn("RATE_LIMIT_EXCEEDED retry-after={}s", retry);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retry))
                .body(ApiResponse.error("RATE_LIMIT_EXCEEDED",
                        "Rate limit reached. Try again in " + retry + " seconds."));
    }
}
