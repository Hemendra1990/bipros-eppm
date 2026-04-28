package com.bipros.analytics.application.exception;

public class LlmProviderTestFailedException extends RuntimeException {
    public LlmProviderTestFailedException(String message, Throwable cause) { super(message, cause); }
}
