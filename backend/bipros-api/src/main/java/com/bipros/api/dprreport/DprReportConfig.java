package com.bipros.api.dprreport;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DprReportConfig {
    public enum Cadence { DAILY, WEEKLY }
    public enum WindowPreset { LAST_1_DAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, PROJECT_TO_DATE }

    static final String KEY_ENABLED = "dpr_report_enabled";
    static final String KEY_CADENCE = "dpr_report_cadence";
    static final String KEY_WINDOW = "dpr_report_window";
    static final String KEY_RECIPIENTS = "dpr_report_recipients_override";
    static final String KEY_SEND_TIME = "dpr_report_send_time";
    static final String KEY_TIMEZONE = "dpr_report_timezone";

    static final LocalTime DEFAULT_SEND_TIME = LocalTime.of(7, 0);
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Muscat");

    // Accepted send-time inputs (the admin field is free text): "07:00", "7:00", "7:30 PM", "7 am".
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_TIME,
        DateTimeFormatter.ofPattern("H:mm"),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h a").toFormatter(Locale.ENGLISH));

    private final GlobalSettingRepository repo;

    private Optional<String> value(String key) {
        return repo.findBySettingKey(key).map(GlobalSetting::getSettingValue);
    }

    public boolean enabled() { return value(KEY_ENABLED).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false); }

    public Cadence cadence() {
        return value(KEY_CADENCE).map(v -> {
            try { return Cadence.valueOf(v.trim().toUpperCase()); } catch (Exception e) { return Cadence.DAILY; }
        }).orElse(Cadence.DAILY);
    }

    /** Local time of day the scheduled report goes out, interpreted in {@link #zone()}. */
    public LocalTime sendTime() {
        return value(KEY_SEND_TIME).map(DprReportConfig::parseTime).orElse(DEFAULT_SEND_TIME);
    }

    /** Timezone the send time (and the report window's "today") is interpreted in. */
    public ZoneId zone() {
        return value(KEY_TIMEZONE).map(v -> {
            try { return ZoneId.of(v.trim()); } catch (Exception e) { return DEFAULT_ZONE; }
        }).orElse(DEFAULT_ZONE);
    }

    static LocalTime parseTime(String raw) {
        String v = raw.trim();
        for (DateTimeFormatter f : TIME_FORMATS) {
            try { return LocalTime.parse(v, f); } catch (Exception ignored) { }
        }
        return DEFAULT_SEND_TIME;
    }

    public WindowPreset window() {
        return value(KEY_WINDOW).map(v -> {
            try { return WindowPreset.valueOf(v.trim().toUpperCase()); } catch (Exception e) { return WindowPreset.LAST_7_DAYS; }
        }).orElse(WindowPreset.LAST_7_DAYS);
    }

    public List<String> recipientOverrideEmails() {
        return value(KEY_RECIPIENTS).map(v -> Arrays.stream(v.split(","))
            .map(String::trim).filter(s -> !s.isBlank()).toList()).orElse(List.of());
    }
}
