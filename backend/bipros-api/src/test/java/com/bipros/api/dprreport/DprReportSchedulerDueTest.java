package com.bipros.api.dprreport;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class DprReportSchedulerDueTest {
    private static final ZoneId MUSCAT = ZoneId.of("Asia/Muscat"); // UTC+4, no DST
    private static final LocalTime SEND = LocalTime.of(7, 0);

    private static ZonedDateTime muscat(String utcInstant) {
        return Instant.parse(utcInstant).atZone(MUSCAT);
    }

    @Test void not_due_before_send_time_even_when_never_run() {
        // 06:00 Muscat, never run
        assertThat(DprReportScheduler.isDue(null, muscat("2026-07-08T02:00:00Z"), SEND,
                DprReportConfig.Cadence.DAILY)).isFalse();
    }

    @Test void due_when_never_run_and_past_send_time() {
        // 07:05 Muscat
        assertThat(DprReportScheduler.isDue(null, muscat("2026-07-08T03:05:00Z"), SEND,
                DprReportConfig.Cadence.DAILY)).isTrue();
    }

    @Test void not_due_when_already_sent_today() {
        // sent 07:10 Muscat today, now 14:00 Muscat
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-08T03:10:00Z"),
                muscat("2026-07-08T10:00:00Z"), SEND, DprReportConfig.Cadence.DAILY)).isFalse();
    }

    @Test void due_next_day_at_send_time() {
        // sent 07:10 Muscat yesterday, now 07:05 Muscat today
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-07T03:10:00Z"),
                muscat("2026-07-08T03:05:00Z"), SEND, DprReportConfig.Cadence.DAILY)).isTrue();
    }

    @Test void catches_up_late_in_the_day_after_downtime() {
        // sent yesterday 07:10; JVM was down over 07:00 — first tick at 19:00 Muscat still sends
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-07T03:10:00Z"),
                muscat("2026-07-08T15:00:00Z"), SEND, DprReportConfig.Cadence.DAILY)).isTrue();
    }

    @Test void last_run_date_is_evaluated_in_the_configured_zone() {
        // lastRun 2026-07-07T21:00:00Z = 01:00 Muscat on Jul 8 — already "today" locally,
        // so 07:05 Muscat the same day is NOT due (a UTC reading would wrongly say due).
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-07T21:00:00Z"),
                muscat("2026-07-08T03:05:00Z"), SEND, DprReportConfig.Cadence.DAILY)).isFalse();
    }

    @Test void weekly_not_due_after_three_days() {
        // sent Jul 1 07:05 Muscat, now Jul 4 07:05 Muscat
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-01T03:05:00Z"),
                muscat("2026-07-04T03:05:00Z"), SEND, DprReportConfig.Cadence.WEEKLY)).isFalse();
    }

    @Test void weekly_due_after_seven_days() {
        // sent Jul 1 07:05 Muscat, now Jul 8 07:05 Muscat
        assertThat(DprReportScheduler.isDue(Instant.parse("2026-07-01T03:05:00Z"),
                muscat("2026-07-08T03:05:00Z"), SEND, DprReportConfig.Cadence.WEEKLY)).isTrue();
    }
}
