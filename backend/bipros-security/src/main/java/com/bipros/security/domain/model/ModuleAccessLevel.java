package com.bipros.security.domain.model;

/**
 * Per-module access levels in the IC-PMS permission matrix.
 * Ordinal order encodes privilege ascendance — checks use {@link #ordinal()} comparison.
 */
public enum ModuleAccessLevel {
    NONE,
    VIEW,
    EDIT,
    CERTIFY,
    APPROVE,
    FULL
}
