package com.bipros.api.dprreport;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static com.bipros.api.dprreport.DprReportConfig.WindowPreset.*;

class DprReportWindowTest {
    private final LocalDate today = LocalDate.of(2026, 7, 8);
    private final LocalDate start = LocalDate.of(2026, 1, 1);

    @Test void last_1_day_is_yesterday_to_today() {
        var w = DprReportWindow.ofPreset(LAST_1_DAY, today, start);
        assertThat(w.from()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(w.to()).isEqualTo(today);
    }
    @Test void last_7_days() {
        var w = DprReportWindow.ofPreset(LAST_7_DAYS, today, start);
        assertThat(w.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(w.to()).isEqualTo(today);
    }
    @Test void this_month() {
        var w = DprReportWindow.ofPreset(THIS_MONTH, today, start);
        assertThat(w.from()).isEqualTo(LocalDate.of(2026, 7, 1));
    }
    @Test void project_to_date_uses_start() {
        var w = DprReportWindow.ofPreset(PROJECT_TO_DATE, today, start);
        assertThat(w.from()).isEqualTo(start);
        assertThat(w.to()).isEqualTo(today);
    }
    @Test void custom() {
        var w = DprReportWindow.ofCustom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));
        assertThat(w.label()).contains("2026-03-01");
    }
}
