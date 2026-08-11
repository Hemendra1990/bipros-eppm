package com.bipros.api.notification;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.api.dprreport.DprReportConfig;
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
}
