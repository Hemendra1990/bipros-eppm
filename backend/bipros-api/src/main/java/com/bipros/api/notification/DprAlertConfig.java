package com.bipros.api.notification;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.resource.application.service.MaterialIdleStockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

/**
 * DPR alert settings ("DPR Alerts" card in Admin → Settings, seeded by
 * {@code DprReportSettingsSeeder}). Same read-fresh-every-call pattern as
 * {@link DprReportConfig} — admin edits apply on the next scheduler tick / event
 * without a restart.
 *
 * <p>Channel: the client's requirement offers WhatsApp OR email per alert. Only EMAIL
 * transmits today — a WHATSAPP value is accepted and stored, but callers fall back to
 * email (with a log line) until a WhatsApp provider is configured.
 */
@Service
public class DprAlertConfig {

    static final String KEY_CHANNEL = "dpr_alert_channel";
    static final String KEY_MISSING_ENABLED = "dpr_missing_alert_enabled";
    static final String KEY_MISSING_TIME = "dpr_missing_alert_time";

    static final LocalTime DEFAULT_MISSING_TIME = LocalTime.of(9, 0);

    private final GlobalSettingRepository repo;
    private final String appBaseUrl;

    public DprAlertConfig(GlobalSettingRepository repo,
                          @Value("${bipros.agent.notify.email.app-base-url:http://localhost:3000}") String appBaseUrl) {
        this.repo = repo;
        this.appBaseUrl = appBaseUrl;
    }

    private Optional<String> value(String key) {
        return repo.findBySettingKey(key).map(GlobalSetting::getSettingValue);
    }

    /** Configured channel, normalized. Only EMAIL transmits today. */
    public String channel() {
        return value(KEY_CHANNEL).map(v -> v.trim().toUpperCase(Locale.ROOT)).orElse("EMAIL");
    }

    public boolean missingAlertEnabled() {
        return value(KEY_MISSING_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    /** Local time of the daily missing-DPR check, in {@link DprReportConfig#zone()}. */
    public LocalTime missingAlertTime() {
        return value(KEY_MISSING_TIME).map(v -> {
            LocalTime parsed = DprReportConfig.parseTime(v);
            // parseTime falls back to the report default (07:00) on garbage; alerts default 09:00.
            return v.trim().isEmpty() ? DEFAULT_MISSING_TIME : parsed;
        }).orElse(DEFAULT_MISSING_TIME);
    }

    /** Frontend base URL for deep links inside alert emails. */
    public String appBaseUrl() {
        return appBaseUrl;
    }

    // ── weekly outstanding-issues digest ("Issue Alerts" card) ──────────────────

    static final String KEY_ISSUE_DIGEST_ENABLED = "issue_digest_enabled";
    static final String KEY_ISSUE_DIGEST_DAY = "issue_digest_day";
    static final String KEY_ISSUE_DIGEST_TIME = "issue_digest_time";

    public boolean issueDigestEnabled() {
        return value(KEY_ISSUE_DIGEST_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    /** Day of week the digest goes out; tolerant parse, default MONDAY. */
    public java.time.DayOfWeek issueDigestDay() {
        return value(KEY_ISSUE_DIGEST_DAY).map(v -> {
            try {
                return java.time.DayOfWeek.valueOf(v.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return java.time.DayOfWeek.MONDAY;
            }
        }).orElse(java.time.DayOfWeek.MONDAY);
    }

    /** Local time of the weekly digest, in {@link DprReportConfig#zone()}; default 09:00. */
    public LocalTime issueDigestTime() {
        return value(KEY_ISSUE_DIGEST_TIME).map(v ->
            v.trim().isEmpty() ? DEFAULT_MISSING_TIME : DprReportConfig.parseTime(v)
        ).orElse(DEFAULT_MISSING_TIME);
    }

    // ── Act-by SLA: overdue-issue reminder + escalation ("Issue Alerts" card) ─────
    //    Owner decision 2026-08-31: daily reminder to the assignee while overdue, then a
    //    one-shot escalation to the assignee's reporting manager. Default ON — the feature
    //    was explicitly requested, unlike the opt-in digests above.

    static final String KEY_ISSUE_REMINDER_ENABLED = "issue_reminder_enabled";
    static final String KEY_ISSUE_REMINDER_TIME = "issue_reminder_time";
    static final String KEY_ISSUE_REMINDER_EVERY_DAYS = "issue_reminder_every_days";
    static final String KEY_ISSUE_ESCALATION_AFTER_DAYS = "issue_escalation_after_days";

    public boolean issueReminderEnabled() {
        return value(KEY_ISSUE_REMINDER_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(true);
    }

    /** Local time of the daily overdue-issue check, in {@link DprReportConfig#zone()}; default 09:00. */
    public LocalTime issueReminderTime() {
        return value(KEY_ISSUE_REMINDER_TIME).map(v ->
            v.trim().isEmpty() ? DEFAULT_MISSING_TIME : DprReportConfig.parseTime(v)
        ).orElse(DEFAULT_MISSING_TIME);
    }

    /** Days between reminders to the assignee while an issue stays overdue; default 1 (daily), min 1. */
    public int issueReminderEveryDays() {
        return Math.max(1, intValue(KEY_ISSUE_REMINDER_EVERY_DAYS, 1));
    }

    /** Days overdue after which the one-shot manager escalation fires; default 2. */
    public int issueEscalationAfterDays() {
        return intValue(KEY_ISSUE_ESCALATION_AFTER_DAYS, 2);
    }

    // ── weekly material short-supply digest ("Material Alerts" card) ─────────────

    static final String KEY_MATERIAL_SHORTAGE_ENABLED = "material_shortage_enabled";
    static final String KEY_MATERIAL_SHORTAGE_DAY = "material_shortage_day";
    static final String KEY_MATERIAL_SHORTAGE_TIME = "material_shortage_time";
    static final String KEY_MATERIAL_SHORTAGE_DAYS_COVER = "material_shortage_days_cover";

    public boolean materialShortageEnabled() {
        return value(KEY_MATERIAL_SHORTAGE_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    /** Day of week the short-supply digest goes out; tolerant parse, default MONDAY. */
    public java.time.DayOfWeek materialShortageDay() {
        return value(KEY_MATERIAL_SHORTAGE_DAY).map(v -> {
            try {
                return java.time.DayOfWeek.valueOf(v.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return java.time.DayOfWeek.MONDAY;
            }
        }).orElse(java.time.DayOfWeek.MONDAY);
    }

    /** Local time of the short-supply digest, in {@link DprReportConfig#zone()}; default 09:00. */
    public LocalTime materialShortageTime() {
        return value(KEY_MATERIAL_SHORTAGE_TIME).map(v ->
            v.trim().isEmpty() ? DEFAULT_MISSING_TIME : DprReportConfig.parseTime(v)
        ).orElse(DEFAULT_MISSING_TIME);
    }

    /** Days-of-cover threshold below which a material counts as short-supply; default 3. */
    public int materialShortageDaysCover() {
        return value(KEY_MATERIAL_SHORTAGE_DAYS_COVER).map(v -> {
            try {
                return Math.max(0, Integer.parseInt(v.trim()));
            } catch (NumberFormatException ex) {
                return 3;
            }
        }).orElse(3);
    }

    // ---- idle-material alert (owner request 2026-08-12) ----

    static final String KEY_IDLE_ENABLED = "material_idle_enabled";
    static final String KEY_IDLE_PERCENT_TRIGGER = "material_idle_percent_trigger";
    static final String KEY_IDLE_EXCESS_PCT = "material_idle_excess_pct";
    static final String KEY_IDLE_VALUE_FLOOR = "material_idle_value_floor";
    static final String KEY_IDLE_GRACE_DAYS = "material_idle_grace_days";
    static final String KEY_IDLE_MAX_REMINDERS = "material_idle_max_reminders";

    public boolean materialIdleEnabled() {
        return value(KEY_IDLE_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(true);
    }

    /** Maximum reminder mails per outstanding item before it lives only in the weekly digest. */
    public int materialIdleMaxReminders() {
        return intValue(KEY_IDLE_MAX_REMINDERS, 3);
    }

    /** The four numbers the engine needs, read fresh so admin edits apply on the next event. */
    public MaterialIdleStockService.IdleThresholds idleThresholds() {
        return new MaterialIdleStockService.IdleThresholds(
            intValue(KEY_IDLE_PERCENT_TRIGGER, 90),
            intValue(KEY_IDLE_EXCESS_PCT, 20),
            java.math.BigDecimal.valueOf(intValue(KEY_IDLE_VALUE_FLOOR, 100)),
            intValue(KEY_IDLE_GRACE_DAYS, 7));
    }

    private int intValue(String key, int fallback) {
        return value(key).map(v -> {
            try {
                return Math.max(0, Integer.parseInt(v.trim()));
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
