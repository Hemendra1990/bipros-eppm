package com.bipros.api.notification;

public final class DprNotificationType {
  private DprNotificationType() {}
  public static final String DPR_SUBMITTED_FOR_APPROVAL     = "DPR_SUBMITTED_FOR_APPROVAL";
  public static final String DPR_APPROVAL_OVERDUE_APPROVER  = "DPR_APPROVAL_OVERDUE_APPROVER";
  public static final String DPR_APPROVAL_OVERDUE_ESCALATION = "DPR_APPROVAL_OVERDUE_ESCALATION";
  public static final String DPR_APPROVED                   = "DPR_APPROVED";
  public static final String DPR_REJECTED                   = "DPR_REJECTED";
  public static final String DPR_REPORT_READY              = "DPR_REPORT_READY";
  public static final String DPR_MISSING_ALERT             = "DPR_MISSING_ALERT";
  public static final String SUPERVISOR_CAPACITY_SUMMARY   = "SUPERVISOR_CAPACITY_SUMMARY";
  public static final String ISSUE_ASSIGNED                = "ISSUE_ASSIGNED";
  public static final String OUTSTANDING_ISSUES_DIGEST     = "OUTSTANDING_ISSUES_DIGEST";
  public static final String MATERIAL_SHORT_SUPPLY         = "MATERIAL_SHORT_SUPPLY";
  public static final String MATERIAL_IDLE_STOCK           = "MATERIAL_IDLE_STOCK";
}
