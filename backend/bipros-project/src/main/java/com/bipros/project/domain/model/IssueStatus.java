package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of an issue logged against a DPR. {@link #resolvedAtTerminal()} marks the
 * states where {@code resolved_at} is auto-stamped by the service (and cleared on reopen).
 */
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    BLOCKED,
    RESOLVED,
    CLOSED,
    CANCELLED;

    private static final Set<IssueStatus> RESOLVED_TERMINAL = EnumSet.of(RESOLVED, CLOSED);

    public boolean resolvedAtTerminal() {
        return RESOLVED_TERMINAL.contains(this);
    }

    @JsonCreator
    public static IssueStatus fromString(String value) {
        if (value == null || value.isBlank()) return OPEN;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "OPEN", "NEW" -> OPEN;
            case "IN_PROGRESS", "WIP", "WORKING" -> IN_PROGRESS;
            case "BLOCKED", "STUCK" -> BLOCKED;
            case "RESOLVED", "FIXED" -> RESOLVED;
            case "CLOSED", "DONE" -> CLOSED;
            case "CANCELLED", "CANCELED", "VOID" -> CANCELLED;
            default -> throw new IllegalArgumentException(
                "Unknown IssueStatus '" + value + "'");
        };
    }
}
