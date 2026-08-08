package com.bipros.api.dprreport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportRequestParserDateTest {
    @Test void yesterday() {
        assertThat(ReportRequestParser.parseRelativeDays("give me yesterday's report").get()).containsExactly(1, 0);
    }
    @Test void last_n_days() {
        assertThat(ReportRequestParser.parseRelativeDays("show last 2 days").get()).containsExactly(2, 0);
    }
    @Test void today() {
        assertThat(ReportRequestParser.parseRelativeDays("today").get()).containsExactly(0, 0);
    }
    @Test void none_when_no_date_phrase() {
        assertThat(ReportRequestParser.parseRelativeDays("something else")).isEmpty();
    }
    @Test void extracts_email() {
        assertThat(ReportRequestParser.extractEmails("send this to a@b.com please"))
            .containsExactly("a@b.com");
    }
}
