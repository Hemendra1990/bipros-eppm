package com.bipros.analytics.application.sql;

/**
 * Thrown by {@link SqlSafetyValidator} or {@link SqlScopeRewriter} when an SQL string
 * is not safe to execute. The orchestrator catches this and returns a polite message.
 */
public class SqlNotAllowedException extends RuntimeException {
    public SqlNotAllowedException(String message) { super(message); }
}
