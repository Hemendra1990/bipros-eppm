package com.bipros.common.exception;

public class ConcurrencyException extends RuntimeException {

    public ConcurrencyException(String resourceType, Object resourceId) {
        super("%s with id %s was modified by another user. Please refresh and retry."
                .formatted(resourceType, resourceId));
    }
}
