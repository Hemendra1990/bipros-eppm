package com.bipros.project.application.dto;

import java.util.UUID;

/**
 * Pre-submit answer to "who will this DPR go to for approval?" for the current user, resolved
 * by the same ladder that assigns the approver at submit time (chain → CM → PM → project
 * control → admin), so what the form shows is what actually happens. {@code source} is
 * CHAIN / CONSTRUCTION_MANAGER / PM / PROJECT_CONTROL / ADMIN, or NONE when nobody resolves
 * (the DPR would land in the unassigned pool). {@code projectRole} is the approver's
 * project-team seat when known (null for the admin rung).
 */
public record ApproverPreviewResponse(
    UUID userId,
    String name,
    String projectRole,
    String source
) {
  public static final String SOURCE_NONE = "NONE";

  public static ApproverPreviewResponse none() {
    return new ApproverPreviewResponse(null, null, null, SOURCE_NONE);
  }
}
