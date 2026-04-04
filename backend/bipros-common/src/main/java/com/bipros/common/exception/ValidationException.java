package com.bipros.common.exception;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = List.of();
    }

    public ValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String message) {}
}
