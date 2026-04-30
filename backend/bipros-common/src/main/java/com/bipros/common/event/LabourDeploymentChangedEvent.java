package com.bipros.common.event;

import java.util.UUID;

/**
 * Published when a project labour deployment is created, updated, or deleted.
 * Triggers a single-row dimension refresh; daily fact rows for deployments are
 * written nightly by the dimension sync job.
 */
public record LabourDeploymentChangedEvent(
        UUID projectId,
        UUID deploymentId,
        UUID designationId,
        ChangeType changeType
) {
    public enum ChangeType {
        CREATED, UPDATED, DELETED
    }
}
