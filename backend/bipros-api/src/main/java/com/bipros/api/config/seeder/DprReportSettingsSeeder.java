package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the "AI DPR Reports" global settings ({@code dpr_report_*}) once, in EVERY profile,
 * so they appear in Admin → Settings and are editable there. Keys must match
 * {@link com.bipros.api.dprreport.DprReportConfig}.
 *
 * <p>Idempotency: {@code findBySettingKey} is checked per-row so re-runs only insert
 * missing keys and never overwrite an admin-edited value.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1001)
public class DprReportSettingsSeeder implements CommandLineRunner {

  private static final String CATEGORY = "AI DPR Reports";

  private final GlobalSettingRepository globalSettingRepository;

  @Override
  @Transactional
  public void run(String... args) {
    int inserted = 0;
    inserted += upsert("dpr_report_enabled", "false",
        "Enable the scheduled DPR AI report agent");
    inserted += upsert("dpr_report_cadence", "DAILY",
        "How often the scheduled DPR report runs (DAILY or WEEKLY)");
    inserted += upsert("dpr_report_window", "LAST_7_DAYS",
        "Analysis window each scheduled run covers (LAST_1_DAY / LAST_7_DAYS / LAST_30_DAYS / THIS_MONTH / PROJECT_TO_DATE)");
    inserted += upsert("dpr_report_recipients_override", "",
        "Optional comma-separated emails; empty = auto PM + Construction Manager");
    inserted += upsert("dpr_report_send_time", "07:00",
        "Local time of day the scheduled report is generated and emailed (e.g. 07:00 or 7:30 PM)");
    inserted += upsert("dpr_report_timezone", "Asia/Muscat",
        "IANA timezone the send time is interpreted in (e.g. Asia/Muscat)");

    inserted += upsertAlert("dpr_alert_channel", "EMAIL",
        "Delivery channel for DPR alerts (EMAIL or WHATSAPP; WHATSAPP falls back to email until a provider is configured)");
    inserted += upsertAlert("dpr_missing_alert_enabled", "false",
        "Enable the daily missing-DPR alert to each supervisor's reporting manager + project control");
    inserted += upsertAlert("dpr_missing_alert_time", "09:00",
        "Local time the missing-DPR check runs, verifying the PREVIOUS day's submissions (dpr_report_timezone applies)");

    inserted += upsertIssue("issue_digest_enabled", "false",
        "Enable the weekly outstanding-issues digest to project control + assignees, critical issues highlighted");
    inserted += upsertIssue("issue_digest_day", "MONDAY",
        "Day of week the outstanding-issues digest is sent (MONDAY..SUNDAY)");
    inserted += upsertIssue("issue_digest_time", "09:00",
        "Local time the outstanding-issues digest is sent (dpr_report_timezone applies)");

    inserted += upsertMaterial("material_shortage_enabled", "false",
        "Enable the weekly material short-supply digest to project control (store-tracked projects only)");
    inserted += upsertMaterial("material_shortage_day", "MONDAY",
        "Day of week the material short-supply digest is sent (MONDAY..SUNDAY)");
    inserted += upsertMaterial("material_shortage_time", "09:00",
        "Local time the material short-supply digest is sent (dpr_report_timezone applies)");
    inserted += upsertMaterial("material_shortage_days_cover", "3",
        "Days-of-cover threshold: materials whose closing stock covers fewer days of recent consumption count as short supply (min-stock level from the Material Catalogue wins when set)");

    if (inserted == 0) {
      log.info("[DprReportSettingsSeeder] all AI DPR Reports settings already present, skipping");
    } else {
      log.info("[DprReportSettingsSeeder] seeded {} AI DPR Reports settings", inserted);
    }
  }

  private int upsert(String key, String value, String description) {
    return upsert(key, value, description, CATEGORY);
  }

  private int upsertAlert(String key, String value, String description) {
    return upsert(key, value, description, "DPR Alerts");
  }

  private int upsertIssue(String key, String value, String description) {
    return upsert(key, value, description, "Issue Alerts");
  }

  private int upsertMaterial(String key, String value, String description) {
    return upsert(key, value, description, "Material Alerts");
  }

  private int upsert(String key, String value, String description, String category) {
    if (globalSettingRepository.findBySettingKey(key).isPresent()) {
      return 0;
    }
    GlobalSetting s = new GlobalSetting();
    s.setSettingKey(key);
    s.setSettingValue(value);
    s.setDescription(description);
    s.setCategory(category);
    globalSettingRepository.save(s);
    return 1;
  }
}
