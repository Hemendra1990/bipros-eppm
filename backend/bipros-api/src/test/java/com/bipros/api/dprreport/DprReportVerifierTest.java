package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DprReportVerifierTest {
    private final DprReportVerifier verifier = new DprReportVerifier();

    private InsightsResponse narrative(String summary) {
        return new InsightsResponse(summary, List.of(), List.of(), List.of(), List.of(), "r", null, List.of());
    }

    @Test void clean_when_all_numbers_allowed() {
        var r = verifier.verify(narrative("Efficiency was 97 and cost variance 1,200."),
            Set.of("97", "1,200"));
        assertThat(r.clean()).isTrue();
        assertThat(r.unverifiedNumbers()).isEmpty();
    }

    @Test void flags_hallucinated_number() {
        var r = verifier.verify(narrative("Efficiency dropped to 55 this week."), Set.of("97"));
        assertThat(r.clean()).isFalse();
        assertThat(r.unverifiedNumbers()).contains("55");
        assertThat(r.sanitized().rationale()).contains("unverified");
    }

    @Test void ignores_small_ordinals_and_years() {
        // single-digit counts and 4-digit years are not treated as claims
        var r = verifier.verify(narrative("In 2026 there were 3 issues."), Set.of());
        assertThat(r.clean()).isTrue();
    }

    @Test void flags_hallucinated_number_in_finding_label() {
        var resp = new InsightsResponse("summary", List.of(), List.of(), List.of(),
            List.of(new InsightFinding("Cost overrun of 5,200,000", "detail", "warning")), "r", null, List.of());
        var r = verifier.verify(resp, Set.of("97"));
        assertThat(r.clean()).isFalse();
        assertThat(r.unverifiedNumbers()).contains("5,200,000");
    }
}
