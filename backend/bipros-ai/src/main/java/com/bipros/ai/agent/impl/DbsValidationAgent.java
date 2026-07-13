package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.dbs.api.dto.DbsProjectDayResponse;
import com.bipros.dbs.domain.model.DbsDailyProject;
import com.bipros.dbs.domain.repository.DbsDailyProjectRepository;
import com.bipros.dbs.service.DbsAlertEvaluator;
import com.bipros.dbs.service.DbsQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DBS Validation agent — health-checks the project's Daily Business Statement (DBS) financial
 * rollup. Enumerates the {@link DbsDailyProject} project-tier rows, but reads every displayed
 * figure through {@link DbsQueryService#getProjectDay} so the contribution / margin / total-expense
 * it shows are the SAME live, fuel-corrected values the DBS tab renders (raw stored columns can be
 * stale; {@code liveExpense} swaps fuel to machinery×0.35 at read time). Alert triggers stay on the
 * raw row, exactly as the tab evaluates them via {@link DbsAlertEvaluator}.
 *
 * <p>Findings:
 * <ul>
 *   <li>{@code NEGATIVE_CONTRIBUTION} — the latest project-day DBS rollup is running at a loss
 *       (expense &gt; income); sourced from {@link DbsAlertEvaluator#NEGATIVE_CONTRIBUTION}.</li>
 *   <li>{@code MARGIN_DETERIORATION} — the contribution margin has trended downward across the
 *       recent daily rollups (computed from the live-corrected {@code contributionPct}).</li>
 *   <li>{@code DATA_QUALITY_GAP} — work was booked (income &gt; 0) yet manpower and machinery cost
 *       both resolved to zero, indicating a missing rate-master mapping; sourced from
 *       {@link DbsAlertEvaluator#MISSING_RATE_DATA}.</li>
 * </ul>
 *
 * <p>All figures are currency-neutral raw numbers (relabelled per project on the frontend); this
 * agent never applies a currency symbol or FX conversion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbsValidationAgent extends AbstractAgent {

    private static final String KEY = "dbs_validation";
    private static final Duration TTL = Duration.ofDays(7);

    /** Widest possible window — the repository has no "latest" finder, so fetch every day and pick. */
    private static final LocalDate FAR_PAST = LocalDate.of(1970, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);

    /** Number of recent revenue days used to assess the margin trend. */
    private static final int TREND_WINDOW = 6;
    /** Minimum revenue days required before a trend is meaningful. */
    private static final int MIN_TREND_DAYS = 3;
    /** Minimum margin drop (in fraction points) before MARGIN_DETERIORATION fires. */
    private static final double MARGIN_DROP_THRESHOLD = 0.02;

    private final DbsDailyProjectRepository projectRepo;
    private final DbsQueryService dbsQueryService;
    private final DbsAlertEvaluator alertEvaluator;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "DBS Validation";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        if (projectId == null) {
            return new GatherResult(snapshot, List.of());
        }

        List<DbsDailyProject> rows = new ArrayList<>(
                projectRepo.findByProjectIdAndReportDateBetween(projectId, FAR_PAST, FAR_FUTURE));
        if (rows.isEmpty()) {
            return new GatherResult(snapshot, List.of());
        }
        rows.sort(Comparator.comparing(DbsDailyProject::getReportDate,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        DbsDailyProject latestRow = rows.get(rows.size() - 1);
        // Data-quality check evaluates the raw latest row (matches getAlertsForProjectDay).
        List<String> alerts = alertEvaluator.evaluate(latestRow);
        // Live, fuel-corrected read — the same DbsQueryService the DBS tab uses. We report the PROJECT-LEVEL,
        // CUMULATIVE-TO-DATE P&L (not a single day/week/month): the cumulative* fields sum every project-day
        // up to and including this latest date.
        DbsProjectDayResponse latest = dbsQueryService.getProjectDay(projectId, latestRow.getReportDate());

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);
        List<AgentFindingDraft> candidates = new ArrayList<>();

        double cumIncome = nz(latest.cumulativeIncome()).doubleValue();
        double cumExpense = nz(latest.cumulativeExpense()).doubleValue();
        double cumContribution = nz(latest.cumulativeContribution()).doubleValue();
        double cumMargin = cumIncome > 0 ? cumContribution / cumIncome : 0.0;

        // ── snapshot: cumulative-to-date project P&L (drives change detection) ─────
        snapshot.put("asOfDate", latest.reportDate() == null ? null : latest.reportDate().toString());
        snapshot.put("cumulativeIncome", round4(cumIncome));
        snapshot.put("cumulativeExpense", round4(cumExpense));
        snapshot.put("cumulativeContribution", round4(cumContribution));
        snapshot.put("cumulativeContributionPct", round4(cumMargin));
        snapshot.put("manpowerAmount", round(latest.manpowerAmount()));
        snapshot.put("machineryAmount", round(latest.machineryAmount()));
        ArrayNode alertsNode = snapshot.putArray("alerts");
        alerts.forEach(alertsNode::add);

        // ── NEGATIVE_CONTRIBUTION on the cumulative-to-date project P&L ──────────────────
        if (cumContribution < 0) {
            candidates.add(negativeContribution(projectId, latest, cumIncome, cumExpense, cumContribution,
                    cumMargin, validUntil));
        }

        // ── DATA_QUALITY_GAP (missing rate data on the latest day) ──────────────────────
        if (alerts.contains(DbsAlertEvaluator.MISSING_RATE_DATA)) {
            candidates.add(dataQualityGap(projectId, latest, validUntil));
        }

        // ── MARGIN_DETERIORATION (trend across recent revenue days, live-corrected) ──────
        List<LocalDate> revenueDates = new ArrayList<>();
        for (DbsDailyProject r : rows) {
            if (nz(r.getTotalIncome()).signum() > 0 && r.getReportDate() != null) {
                revenueDates.add(r.getReportDate());
            }
        }
        if (revenueDates.size() >= MIN_TREND_DAYS) {
            List<LocalDate> windowDates = revenueDates.subList(
                    Math.max(0, revenueDates.size() - TREND_WINDOW), revenueDates.size());
            List<DbsProjectDayResponse> window = new ArrayList<>();
            for (LocalDate d : windowDates) {
                window.add(dbsQueryService.getProjectDay(projectId, d));
            }
            double firstMargin = nz(window.get(0).contributionPct()).doubleValue();
            double lastMargin = nz(window.get(window.size() - 1).contributionPct()).doubleValue();
            double drop = firstMargin - lastMargin;

            snapshot.put("trendWindowDays", window.size());
            snapshot.put("trendFirstMargin", round4(firstMargin));
            snapshot.put("trendLastMargin", round4(lastMargin));
            snapshot.put("trendDrop", round4(drop));

            if (drop >= MARGIN_DROP_THRESHOLD) {
                candidates.add(marginDeterioration(projectId, window, firstMargin, lastMargin, drop, validUntil));
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft negativeContribution(UUID projectId, DbsProjectDayResponse row,
                                                   double cumIncome, double cumExpense, double cumContribution,
                                                   double cumMargin, Instant validUntil) {
        Severity severity = cumMargin <= -0.10 ? Severity.CRITICAL : Severity.HIGH;
        String date = dateOf(row);
        return new AgentFindingDraft(
                "NEGATIVE_CONTRIBUTION",
                "PROJECT",
                severity,
                0.95,
                "Project cumulative-to-date DBS rollup (Σ income − Σ live expense, all days through " + date + ")",
                "Project is running at a loss to date — contribution margin " + pct(cumMargin),
                "As of " + date + " the project has booked " + num(BigDecimal.valueOf(cumIncome))
                        + " of revenue against " + num(BigDecimal.valueOf(cumExpense)) + " of expense — a cumulative "
                        + "negative contribution of " + num(BigDecimal.valueOf(cumContribution)) + " ("
                        + pct(cumMargin) + " margin).",
                "Cumulative day-rate expense (manpower, machinery, fuel, material, sub-contract and overheads) "
                        + "exceeds the cumulative BOQ value earned for the work executed to date.",
                "A sustained negative contribution erodes project profit directly and threatens the "
                        + "budget-at-completion and the project's commercial viability.",
                "Review the cost sections against the BOQ earned: confirm rate-master mappings, check for "
                        + "unproductive deployments or fuel/machinery over-run, and re-baseline the resource plan if the "
                        + "loss is structural.",
                List.of(
                        EvidenceRef.money("Contribution to date", BigDecimal.valueOf(cumContribution)),
                        EvidenceRef.metric("Contribution margin", pct(cumMargin)),
                        EvidenceRef.money("Revenue to date", BigDecimal.valueOf(cumIncome)),
                        EvidenceRef.money("Expense to date", BigDecimal.valueOf(cumExpense)),
                        EvidenceRef.entity("DBS", "Open project P&L", "dbs", projectId,
                                "/projects/" + projectId + "/dbs?tab=pm")),
                Map.of("PROJECT_MANAGER", List.of(), "COST_CONTROLLER", List.of()),
                validUntil);
    }

    private AgentFindingDraft dataQualityGap(UUID projectId, DbsProjectDayResponse row, Instant validUntil) {
        String date = dateOf(row);
        return new AgentFindingDraft(
                "DATA_QUALITY_GAP",
                "PROJECT",
                Severity.MEDIUM,
                0.80,
                "Rate-master heuristic: manpower and machinery cost both zero with positive booked income",
                "DBS booked revenue with no resolved labour or plant cost on " + date,
                "On " + date + " the DBS rollup recorded " + num(row.totalIncome()) + " of BOQ revenue but both "
                        + "manpower and machinery cost resolved to zero, so the day's contribution is overstated.",
                "Work was reported (BOQ earned) but the rate-master lookup returned nothing for the deployed labour "
                        + "and equipment — typically a missing unit-rate mapping or an unmatched resource role.",
                "Missing cost data inflates the reported margin and hides the true daily P&L; downstream DBS, EVM and "
                        + "capacity-utilisation figures inherit the gap.",
                "Check the rate-book and role mappings for the resources deployed on " + date + ", then re-run the DBS "
                        + "recompute so the day's manpower and machinery cost populate correctly.",
                List.of(
                        EvidenceRef.money("Manpower cost", nz(row.manpowerAmount())),
                        EvidenceRef.money("Machinery cost", nz(row.machineryAmount())),
                        EvidenceRef.money("Revenue booked", nz(row.totalIncome())),
                        EvidenceRef.entity("DBS day", date, "dbs", projectId,
                                "/projects/" + projectId + "/dbs?date=" + date)),
                Map.of("PROJECT_MANAGER", List.of(), "COST_CONTROLLER", List.of()),
                validUntil);
    }

    private AgentFindingDraft marginDeterioration(UUID projectId, List<DbsProjectDayResponse> window,
                                                  double firstMargin, double lastMargin, double drop,
                                                  Instant validUntil) {
        Severity severity = drop >= 0.10 ? Severity.HIGH : drop >= 0.05 ? Severity.MEDIUM : Severity.LOW;
        double confidence = confidenceForSample(window.size());
        String firstDate = dateOf(window.get(0));
        String lastDate = dateOf(window.get(window.size() - 1));
        return new AgentFindingDraft(
                "MARGIN_DETERIORATION",
                "PROJECT",
                severity,
                confidence,
                "Contribution-margin trend across " + window.size() + " recent revenue days",
                "DBS contribution margin is trending down (" + pct(firstMargin) + " → " + pct(lastMargin) + ")",
                "Across the last " + window.size() + " revenue days (" + firstDate + " to " + lastDate + "), the "
                        + "project's daily contribution margin fell from " + pct(firstMargin) + " to " + pct(lastMargin)
                        + ", a drop of " + pctPoints(drop) + ".",
                "Daily expense is growing faster than the BOQ value earned — driven by rising manpower, machinery, "
                        + "fuel or material cost per unit of work, or by lower-value activities dominating recent days.",
                "A steady margin decline signals the project is drifting toward a loss; left unchecked it compresses "
                        + "contribution and puts the budget-at-completion at risk.",
                "Break down the recent daily cost sections to find the driver of the decline, tighten resource "
                        + "deployment and fuel/machinery usage, and validate the productivity norms behind the BOQ rates.",
                List.of(
                        EvidenceRef.metric("Margin now", pct(lastMargin)),
                        EvidenceRef.metric("Margin " + window.size() + " revenue days ago", pct(firstMargin)),
                        EvidenceRef.metric("Margin drop", pctPoints(drop)),
                        EvidenceRef.entity("DBS trend", firstDate + " → " + lastDate, "dbs", projectId,
                                "/projects/" + projectId + "/dbs")),
                Map.of("PROJECT_MANAGER", List.of(), "COST_CONTROLLER", List.of()),
                validUntil);
    }

    /** Confidence rises with the number of revenue days sampled: 3 days ≈ 0.65, 6+ days ≈ 0.80. */
    private static double confidenceForSample(int days) {
        return Math.min(0.90, 0.5 + days / 20.0);
    }

    private static String dateOf(DbsProjectDayResponse row) {
        LocalDate d = row == null ? null : row.reportDate();
        return d == null ? "unknown date" : d.toString();
    }

    /** Format a raw (currency-neutral) money figure with thousands grouping and no symbol. */
    private static String num(BigDecimal v) {
        return String.format(Locale.ROOT, "%,.2f", nz(v).doubleValue());
    }

    /** Render a fraction (e.g. 0.05) as a percentage string (e.g. "5.0%"). */
    private static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    /** Render a fraction delta as percentage points (e.g. 0.03 → "3.0 pts"). */
    private static String pctPoints(double fraction) {
        return String.format(Locale.ROOT, "%.1f pts", fraction * 100.0);
    }

    private static double round(BigDecimal v) {
        return round4(nz(v).doubleValue());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
