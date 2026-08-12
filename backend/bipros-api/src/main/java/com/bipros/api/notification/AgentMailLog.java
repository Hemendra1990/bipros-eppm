package com.bipros.api.notification;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only delivery log — one row per recipient per agent-sent communication (email or
 * in-app mirror). Written fail-safe by {@link AgentMailLogService} from the four senders
 * (daily report, supervisor summary, missing-DPR alert, DPR rejection) and surfaced on the
 * AI tab's "Agent deliverables" panel. For {@code DPR_REPORT} rows the body is NOT copied —
 * {@code reportId} references the stored {@code ai.dpr_agent_report} row instead.
 */
@Entity
@Table(name = "agent_mail_log", schema = "ai",
       indexes = @Index(name = "ix_agent_mail_log_project_sent", columnList = "project_id, sent_at"))
@Getter @Setter @NoArgsConstructor
public class AgentMailLog extends BaseEntity {

    public static final String CAT_DPR_REPORT = "DPR_REPORT";
    public static final String CAT_SUPERVISOR_SUMMARY = "SUPERVISOR_SUMMARY";
    public static final String CAT_MISSING_DPR = "MISSING_DPR";
    public static final String CAT_DPR_REJECTION = "DPR_REJECTION";
    public static final String CAT_ISSUE_ASSIGNMENT = "ISSUE_ASSIGNMENT";
    public static final String CAT_OUTSTANDING_ISSUES = "OUTSTANDING_ISSUES";
    public static final String CAT_MATERIAL_SHORT_SUPPLY = "MATERIAL_SHORT_SUPPLY";
    public static final String CAT_MATERIAL_IDLE_STOCK = "MATERIAL_IDLE_STOCK";
    public static final String CH_EMAIL = "EMAIL";
    public static final String CH_IN_APP = "IN_APP";
    /** Recipient could not be reached on the channel (e.g. user has no email address). */
    public static final String STATUS_SKIPPED = "SKIPPED";

    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "category", nullable = false, length = 40) private String category;
    @Column(name = "channel", nullable = false, length = 16) private String channel;
    @Column(name = "recipient_user_id") private UUID recipientUserId;
    @Column(name = "recipient_name") private String recipientName;
    @Column(name = "recipient_email") private String recipientEmail;
    @Column(name = "subject", length = 500) private String subject;
    /** Full HTML for the small mails; NULL for DPR_REPORT (body lives on ai.dpr_agent_report). */
    @Column(name = "body_html", columnDefinition = "text") private String bodyHtml;
    /** Reference into ai.dpr_agent_report for category DPR_REPORT. */
    @Column(name = "report_id") private UUID reportId;
    /** SENT | FAILED | PREVIEW (SMTP unconfigured) | SKIPPED — email statuses mirror
     *  {@code EmailService.SendResult}; IN_APP rows are always SENT. */
    @Column(name = "status", nullable = false, length = 16) private String status;
    @Column(name = "detail", length = 500) private String detail;
    @Column(name = "sent_at", nullable = false) private Instant sentAt;
}
