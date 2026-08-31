package com.bipros.api.dprreport;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DprReportConfigTest {
    private final GlobalSettingRepository repo = mock(GlobalSettingRepository.class);
    private final DprReportConfig cfg = new DprReportConfig(repo);

    private void setting(String key, String val) {
        GlobalSetting s = new GlobalSetting(); s.setSettingKey(key); s.setSettingValue(val);
        when(repo.findBySettingKey(key)).thenReturn(Optional.of(s));
    }

    @Test void defaults_when_unset() {
        when(repo.findBySettingKey(anyString())).thenReturn(Optional.empty());
        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.cadence()).isEqualTo(DprReportConfig.Cadence.DAILY);
        assertThat(cfg.window()).isEqualTo(DprReportConfig.WindowPreset.LAST_7_DAYS);
        assertThat(cfg.recipientOverrideEmails()).isEmpty();
        assertThat(cfg.sendTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(cfg.zone()).isEqualTo(ZoneId.of("Asia/Muscat"));
    }

    @Test void parses_enabled_and_weekly() {
        setting("dpr_report_enabled", "true");
        setting("dpr_report_cadence", "WEEKLY");
        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.cadence()).isEqualTo(DprReportConfig.Cadence.WEEKLY);
    }

    @Test void parses_send_time_formats() {
        assertThat(DprReportConfig.parseTime("07:00")).isEqualTo(LocalTime.of(7, 0));
        assertThat(DprReportConfig.parseTime("7:30")).isEqualTo(LocalTime.of(7, 30));
        assertThat(DprReportConfig.parseTime("7:30 PM")).isEqualTo(LocalTime.of(19, 30));
        assertThat(DprReportConfig.parseTime(" 7 am ")).isEqualTo(LocalTime.of(7, 0));
        assertThat(DprReportConfig.parseTime("garbage")).isEqualTo(LocalTime.of(7, 0));
    }

    @Test void parses_timezone_with_fallback() {
        setting("dpr_report_timezone", "Asia/Kolkata");
        assertThat(cfg.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        setting("dpr_report_timezone", "not-a-zone");
        assertThat(cfg.zone()).isEqualTo(ZoneId.of("Asia/Muscat"));
    }

    @Test void parses_recipient_override_list() {
        setting("dpr_report_recipients_override", " a@b.com , c@d.com ,, ");
        assertThat(cfg.recipientOverrideEmails()).containsExactly("a@b.com", "c@d.com");
    }

    @Test void bad_cadence_falls_back_to_daily() {
        setting("dpr_report_cadence", "garbage");
        assertThat(cfg.cadence()).isEqualTo(DprReportConfig.Cadence.DAILY);
    }
}
