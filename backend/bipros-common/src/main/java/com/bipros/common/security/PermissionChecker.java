package com.bipros.common.security;

/**
 * Asks whether the caller of the current request holds a permission.
 *
 * <p>Exists so a domain module can redact a field it must not serialise without depending on
 * {@code bipros-security}: the permission set lives in the database behind
 * {@code CurrentUserService}, and dependencies flow inward through {@code bipros-common} only.
 * Method-level authorisation stays where it belongs — {@code @PreAuthorize} on the controller.
 * This is for the narrower job of leaving a field out of an otherwise-permitted response.
 *
 * <p>Implementations must never throw: an unauthenticated or unresolvable caller is simply
 * "does not have it".
 */
public interface PermissionChecker {

    /** @param permissionCode e.g. {@code "COST.READ"} */
    boolean has(String permissionCode);
}
