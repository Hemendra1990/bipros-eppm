package com.bipros.api.service.kpi;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeploymentUtilisationTest {

    @Test
    void averages_daily_deployment_and_caps_at_one() {
        // 900 nos over 3 active days = 300/day; planned 400 → 0.75, not capped
        var r = DeploymentUtilisation.of(900, 3, 400);
        assertThat(r.avgDailyNos()).isEqualTo(300);
        assertThat(r.rawPct()).isEqualTo(0.75d);
        assertThat(r.cappedPct()).isEqualTo(0.75d);
        assertThat(r.overflow()).isFalse();
    }

    @Test
    void over_deployment_caps_and_flags_overflow() {
        // 1000 nos over 2 days = 500/day; planned 400 → 1.25 raw, capped 1.0
        var r = DeploymentUtilisation.of(1000, 2, 400);
        assertThat(r.avgDailyNos()).isEqualTo(500);
        assertThat(r.rawPct()).isEqualTo(1.25d);
        assertThat(r.cappedPct()).isEqualTo(1.0d);
        assertThat(r.overflow()).isTrue();
    }

    @Test
    void zero_active_days_or_zero_planned_is_zero() {
        assertThat(DeploymentUtilisation.of(900, 0, 400).cappedPct()).isEqualTo(0d);
        assertThat(DeploymentUtilisation.of(900, 3, 0).cappedPct()).isEqualTo(0d);
        assertThat(DeploymentUtilisation.of(900, 0, 400).avgDailyNos()).isEqualTo(0);
    }
}
