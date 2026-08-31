package com.bipros.reporting.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the canonical-alignment math in PortfolioReportService. */
class PortfolioCanonicalAlignmentTest {

  @Test
  void avgProgressIsMeanOfCostPercentComplete() {
    // costPercentComplete 0.20 and 0.40 → avg 30.0 (%)
    double avg = PortfolioReportService.avgCostPercent(
        List.of(new BigDecimal("0.20"), new BigDecimal("0.40")));
    assertThat(avg).isEqualTo(30.0);
  }

  @Test
  void avgProgressIgnoresNullCostPercent() {
    double avg = PortfolioReportService.avgCostPercent(
        Arrays.asList(new BigDecimal("0.50"), null));
    assertThat(avg).isEqualTo(50.0);
  }
}
