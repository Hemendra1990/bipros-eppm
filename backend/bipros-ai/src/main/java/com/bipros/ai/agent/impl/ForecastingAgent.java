package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.evm.application.service.EvmService;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.risk.application.dto.MonteCarloRunRequest;
import com.bipros.risk.application.dto.MonteCarloSimulationDto;
import com.bipros.risk.application.service.MonteCarloService;
import com.bipros.risk.domain.model.MonteCarloSimulation;
import com.bipros.risk.domain.repository.MonteCarloSimulationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Forecasting agent — the flagship for <em>explainable</em> confidence. Every probabilistic finding
 * states the exact statistic behind its confidence number (a Monte Carlo P-value: "P80 of N
 * iterations"), so a reader can see precisely where the 0.80 came from.
 *
 * <p>Data sources (all read-only in the normal path):
 * <ul>
 *   <li>{@link MonteCarloSimulationRepository#findLatestByProjectId(UUID)} — the most-recent
 *       PERSISTED Monte Carlo simulation (schedule/cost percentiles). Preferred over re-running the
 *       10k-iteration engine, which persists a simulation plus per-iteration rows (an unwanted side
 *       effect in a read pass) and is heavy on a sweep. Only when no completed simulation exists AND
 *       this is a user-forced/manual run ({@code ctx.force()}) is one fresh simulation computed via
 *       {@link MonteCarloService#runSimulation} (fixed seed, so the data-hash stays stable); a
 *       routine event/sweep degrades gracefully instead.</li>
 *   <li>{@link EvmService#computeEvmSnapshot(UUID)} — read-only EAC/BAC/CPI cost forecast.</li>
 *   <li>{@link ProjectRepository} — the project's start anchor and contract/planned finish dates,
 *       used to turn the P80 project <em>duration</em> into a P80 completion <em>date</em>.</li>
 * </ul>
 *
 * <p>Findings: {@code COMPLETION_FORECAST} (P80 finish vs contract finish), {@code COST_AT_COMPLETION}
 * (EVM EAC vs BAC) and {@code CASHFLOW_PRESSURE} (Monte Carlo P80 cost tail vs the cost baseline — the
 * contingency needed to be 80% sure of funding to completion). Active planning/risk findings are read
 * from shared memory and referenced in the business impact where relevant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastingAgent extends AbstractAgent {

    private static final String KEY = "forecasting";
    private static final Duration TTL = Duration.ofDays(7);
    private static final long FRESH_RUN_SEED = 424242L;

    private final MonteCarloSimulationRepository monteCarloSimulationRepository;
    private final MonteCarloService monteCarloService;
    private final EvmService evmService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Forecasting";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);

        Forecast mc = resolveForecast(projectId, ctx.force());
        EvmCalculation evm = safeEvm(projectId);
        boolean haveEvm = evm != null && evm.getBudgetAtCompletion() != null
                && evm.getBudgetAtCompletion().signum() > 0;

        // Degrade to empty when neither probabilistic nor deterministic cost data is available.
        if (mc == null && !haveEvm) {
            return new GatherResult(snapshot, candidates);
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        List<String> relatedTitles = relatedForecastTitles(projectId);

        // --- Completion forecast (Monte Carlo P80 schedule) ---
        if (mc != null && mc.p80Duration != null && mc.baselineDuration > 0) {
            ObjectNode m = snapshot.putObject("monteCarloSchedule");
            m.put("iterations", mc.iterations);
            m.put("p80Duration", round(mc.p80Duration));
            m.put("baselineDuration", round(mc.baselineDuration));
            AgentFindingDraft f = completionForecast(projectId, mc, project, relatedTitles, validUntil);
            if (f != null) {
                candidates.add(f);
            }
        }

        // --- Cost at completion (EVM EAC vs BAC) ---
        if (haveEvm) {
            AgentFindingDraft f = costAtCompletion(projectId, evm, mc, snapshot, relatedTitles, validUntil);
            if (f != null) {
                candidates.add(f);
            }
        }

        // --- Cashflow pressure (Monte Carlo P80 cost tail) ---
        if (mc != null && mc.p80Cost != null && mc.baselineCost != null && mc.baselineCost.signum() > 0) {
            ObjectNode m = snapshot.putObject("monteCarloCost");
            m.put("iterations", mc.iterations);
            m.put("p80Cost", mc.p80Cost.doubleValue());
            m.put("baselineCost", mc.baselineCost.doubleValue());
            AgentFindingDraft f = cashflowPressure(projectId, mc, validUntil);
            if (f != null) {
                candidates.add(f);
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ---------------------------------------------------------------- findings

    private AgentFindingDraft completionForecast(UUID projectId, Forecast mc, Project project,
                                                 List<String> relatedTitles, Instant validUntil) {
        double overrunDays = mc.p80Duration - mc.baselineDuration;
        double overrunRatio = mc.baselineDuration > 0 ? overrunDays / mc.baselineDuration : 0.0;

        LocalDate start = project != null ? project.getPlannedStartDate() : null;
        LocalDate contractFinish = project == null ? null
                : (project.getMustFinishByDate() != null ? project.getMustFinishByDate()
                : project.getPlannedFinishDate());
        LocalDate p80Finish = start != null ? start.plusDays(Math.round(mc.p80Duration)) : null;
        long breachDays = (p80Finish != null && contractFinish != null && p80Finish.isAfter(contractFinish))
                ? ChronoUnit.DAYS.between(contractFinish, p80Finish) : 0L;

        // Emit only when there is a real schedule-risk signal: a meaningful P80 overrun of the
        // baseline duration, or a P80 finish that breaches the contract/planned finish date.
        if (overrunRatio < 0.02 && breachDays <= 0) {
            return null;
        }

        Severity severity = ratioSeverity(overrunRatio, 0.15, 0.08, 0.03);
        if (breachDays > 0 && severity.ordinal() < Severity.HIGH.ordinal()) {
            severity = Severity.HIGH;   // a contractual date breach is serious regardless of ratio
        }

        String p80Label = p80Finish != null
                ? p80Finish + " (" + days(mc.p80Duration) + "-day duration)"
                : days(mc.p80Duration) + "-day project duration";

        StringBuilder what = new StringBuilder();
        what.append("The P80 (80%-confidence) forecast completion is ").append(p80Label)
                .append(" — ").append(days(overrunDays)).append(" days beyond the ")
                .append(days(mc.baselineDuration)).append("-day baseline (").append(pct(overrunRatio))
                .append(" longer).");
        if (breachDays > 0) {
            what.append(" That is ").append(breachDays).append(" days past the committed finish date of ")
                    .append(contractFinish).append(".");
        } else if (contractFinish != null && p80Finish != null) {
            what.append(" It still lands on or before the committed finish date of ")
                    .append(contractFinish).append(".");
        }

        String impact = breachDays > 0
                ? "At 80% confidence the project misses its committed finish by " + breachDays
                + " days, exposing it to liquidated-damages and delayed milestone payments; only a 1-in-5 "
                + "chance remains of finishing on time or better."
                : "There is a 4-in-5 chance the finish slips " + days(overrunDays)
                + " days or more beyond the baseline, eroding the schedule contingency well before the end date.";
        impact += relatedSuffix(relatedTitles);

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("P80 forecast duration", days(mc.p80Duration) + " days"));
        evidence.add(EvidenceRef.metric("Baseline duration", days(mc.baselineDuration) + " days"));
        evidence.add(EvidenceRef.metric("Overrun at P80", days(overrunDays) + " days"));
        if (mc.p50Duration != null) {
            evidence.add(EvidenceRef.metric("P50 (median) duration", days(mc.p50Duration) + " days"));
        }
        if (p80Finish != null) {
            evidence.add(EvidenceRef.metric("P80 completion date", p80Finish.toString()));
        }
        if (contractFinish != null) {
            evidence.add(EvidenceRef.metric("Committed finish date", contractFinish.toString()));
        }
        if (breachDays > 0) {
            evidence.add(EvidenceRef.metric("Days past committed finish", breachDays + " days"));
        }
        evidence.add(EvidenceRef.entity("Monte Carlo", "Open schedule risk analysis", "monte_carlo",
                mc.simId, "/projects/" + projectId + "/risk-analysis"));

        return new AgentFindingDraft(
                "COMPLETION_FORECAST",
                "PROJECT",
                severity,
                0.80,
                "P80 completion percentile across " + mc.iterations + " Monte Carlo iterations",
                breachDays > 0
                        ? "P80 finish misses the committed date by " + breachDays + " days"
                        : "P80 finish runs " + days(overrunDays) + " days past the baseline",
                what.toString(),
                "Simulated activity-duration uncertainty across the network pushes the 80th-percentile "
                        + "completion beyond the baseline; the driving paths carry little schedule contingency.",
                impact,
                breachDays > 0
                        ? "Crash or fast-track the driving critical activities to pull the P80 finish back "
                        + "inside the committed date, or escalate a schedule change request now."
                        : "Protect the contingency by resequencing near-critical work and monitoring the "
                        + "driving activities before the overrun becomes unrecoverable.",
                evidence,
                Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft costAtCompletion(UUID projectId, EvmCalculation evm, Forecast mc,
                                               ObjectNode snapshot, List<String> relatedTitles,
                                               Instant validUntil) {
        BigDecimal bac = evm.getBudgetAtCompletion();
        BigDecimal eac = evm.getEstimateAtCompletion();
        if (bac == null || bac.signum() <= 0 || eac == null) {
            return null;
        }
        BigDecimal overrun = eac.subtract(bac);
        double overrunRatio = overrun.doubleValue() / bac.doubleValue();

        ObjectNode e = snapshot.putObject("evm");
        e.put("bac", bac.doubleValue());
        e.put("eac", eac.doubleValue());
        e.put("cpi", nz(evm.getCostPerformanceIndex()));
        e.put("performancePct", nz(evm.getPerformancePercentComplete()));

        // Emit only when the forecast is an overrun of at least 2% of budget.
        if (overrun.signum() <= 0 || overrunRatio < 0.02) {
            return null;
        }

        Severity severity = ratioSeverity(overrunRatio, 0.15, 0.08, 0.03);
        double cpi = nz(evm.getCostPerformanceIndex());
        double perfPct = nz(evm.getPerformancePercentComplete());
        double confidence = clamp(0.5 + perfPct / 250.0, 0.5, 0.9);

        // Confidence basis names the Monte Carlo P-value when a simulation corroborates the EVM
        // point estimate; otherwise it names the CPI-based EAC method and progress to date.
        String basis = mc != null && mc.p80Cost != null
                ? "CPI-based EAC (CPI " + fmt2(cpi) + "), corroborated by the Monte Carlo P80 cost of "
                + mc.iterations + " iterations"
                : "CPI-based EAC (CPI " + fmt2(cpi) + ") at " + fmt0(perfPct) + "% performance-complete";

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Estimate at completion (EAC)", money(eac)));
        evidence.add(EvidenceRef.metric("Budget at completion (BAC)", money(bac)));
        evidence.add(EvidenceRef.metric("Forecast overrun (VAC)", money(overrun.negate())));
        evidence.add(EvidenceRef.metric("Cost performance index (CPI)", fmt2(cpi)));
        if (mc != null && mc.p80Cost != null) {
            evidence.add(EvidenceRef.metric("Monte Carlo P80 cost", money(mc.p80Cost)));
        }
        evidence.add(EvidenceRef.entity("EVM", "Open cost performance", "evm", projectId,
                "/projects/" + projectId + "/cost?tab=evm"));

        String impact = "Left uncorrected, completing the project costs about " + money(overrun)
                + " more than the approved budget (" + pct(overrunRatio) + " over), consuming contingency "
                + "and putting the project margin at risk." + relatedSuffix(relatedTitles);

        return new AgentFindingDraft(
                "COST_AT_COMPLETION",
                "PROJECT",
                severity,
                confidence,
                basis,
                "Forecast cost overruns the budget by " + money(overrun) + " (" + pct(overrunRatio) + ")",
                "The estimate at completion (" + money(eac) + ") exceeds the budget at completion ("
                        + money(bac) + ") by " + money(overrun) + ", a " + pct(overrunRatio)
                        + " overrun projected from a cost performance index of " + fmt2(cpi) + "."
                        + (mc != null && mc.p80Cost != null
                        ? " The Monte Carlo P80 cost of " + money(mc.p80Cost) + " points the same way."
                        : ""),
                "Costs are being incurred faster than value is earned (CPI " + fmt2(cpi)
                        + " < 1.0), so the CPI-based forecast projects the current inefficiency across the "
                        + "remaining work.",
                impact,
                "Investigate the cost accounts driving the CPI shortfall, tighten commitment control on the "
                        + "remaining scope, and re-forecast; escalate a budget change request if the overrun is structural.",
                evidence,
                Map.of("PROJECT_MANAGER", List.of(), "COST_CONTROLLER", List.of()),
                validUntil);
    }

    private AgentFindingDraft cashflowPressure(UUID projectId, Forecast mc, Instant validUntil) {
        BigDecimal gap = mc.p80Cost.subtract(mc.baselineCost);
        if (gap.signum() <= 0) {
            return null;
        }
        double gapRatio = gap.doubleValue() / mc.baselineCost.doubleValue();
        if (gapRatio < 0.03) {
            return null;
        }
        Severity severity = ratioSeverity(gapRatio, 0.20, 0.10, 0.05);

        List<EvidenceRef> evidence = List.of(
                EvidenceRef.metric("P80 cost (80% confidence)", money(mc.p80Cost)),
                EvidenceRef.metric("Cost baseline", money(mc.baselineCost)),
                EvidenceRef.metric("Contingency gap at P80", money(gap)),
                EvidenceRef.metric("Gap vs baseline", pct(gapRatio)),
                EvidenceRef.entity("Monte Carlo", "Open cost risk analysis", "monte_carlo",
                        mc.simId, "/projects/" + projectId + "/risk-analysis"));

        return new AgentFindingDraft(
                "CASHFLOW_PRESSURE",
                "PROJECT",
                severity,
                0.80,
                "P80 cost percentile across " + mc.iterations + " Monte Carlo iterations",
                "Funding to 80% confidence needs " + money(gap) + " above the cost baseline",
                "At 80% confidence the project cost reaches " + money(mc.p80Cost) + " versus the "
                        + money(mc.baselineCost) + " baseline — a contingency gap of " + money(gap) + " ("
                        + pct(gapRatio) + "). That gap is the cash buffer required to be 80% sure of funding "
                        + "the work to completion.",
                "Simulated cost uncertainty across activities (and any layered risks) produces a right-skewed "
                        + "cost distribution whose 80th percentile sits well above the committed baseline.",
                "If contingency and drawdown are not sized for the P80 outcome, the project risks a funding "
                        + "shortfall in later periods when the tail costs materialise.",
                "Size the cost contingency and the cashflow drawdown plan against the P80 outcome, not the "
                        + "baseline, and revisit the funding schedule so later periods are not underfunded.",
                evidence,
                Map.of("PROJECT_MANAGER", List.of(), "COST_CONTROLLER", List.of()),
                validUntil);
    }

    // ---------------------------------------------------------------- data resolution

    /**
     * Latest COMPLETED persisted simulation (cheap read). Only when none exists AND this is a
     * user-forced/manual run does it compute one fresh simulation (fixed seed, heavy). Any failure
     * degrades to {@code null} so gather() never throws.
     */
    private Forecast resolveForecast(UUID projectId, boolean force) {
        try {
            MonteCarloSimulation sim = monteCarloSimulationRepository.findLatestByProjectId(projectId).orElse(null);
            if (sim != null && sim.getStatus() == MonteCarloSimulation.MonteCarloStatus.COMPLETED) {
                return Forecast.fromEntity(sim);
            }
        } catch (Exception ex) {
            log.debug("Latest Monte Carlo read failed for project {}: {}", projectId, ex.getMessage());
        }
        if (!force) {
            return null;
        }
        try {
            MonteCarloRunRequest request = new MonteCarloRunRequest();
            request.setRandomSeed(FRESH_RUN_SEED);
            MonteCarloSimulationDto dto = monteCarloService.runSimulation(projectId, request);
            return Forecast.fromDto(dto);
        } catch (Exception ex) {
            log.debug("Fresh Monte Carlo run skipped for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private EvmCalculation safeEvm(UUID projectId) {
        try {
            return evmService.computeEvmSnapshot(projectId);
        } catch (Exception ex) {
            log.debug("EVM snapshot unavailable for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    /** Titles of active planning/risk findings, to cross-reference in the business impact. Null-safe. */
    private List<String> relatedForecastTitles(UUID projectId) {
        if (runtime == null) {
            return List.of();
        }
        try {
            return runtime.memory()
                    .activeFindings(projectId, Set.of("planning_intelligence", "risk_intelligence"), Severity.MEDIUM)
                    .stream()
                    .map(AgentFinding::getTitle)
                    .filter(Objects::nonNull)
                    .limit(3)
                    .toList();
        } catch (Exception ex) {
            log.debug("Planning/risk memory read failed for project {}: {}", projectId, ex.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String relatedSuffix(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return "";
        }
        return " Related planning/risk findings already flag: " + String.join("; ", titles) + ".";
    }

    private static Severity ratioSeverity(double ratio, double crit, double high, double med) {
        return ratio >= crit ? Severity.CRITICAL
                : ratio >= high ? Severity.HIGH
                : ratio >= med ? Severity.MEDIUM
                : Severity.LOW;
    }

    private static String money(BigDecimal v) {
        return v == null ? "0" : String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String days(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Normalised view over the fields this agent needs from either a persisted entity or a fresh DTO. */
    private static final class Forecast {
        final UUID simId;
        final int iterations;
        final Double p80Duration;
        final Double p50Duration;
        final double baselineDuration;
        final BigDecimal p80Cost;
        final BigDecimal baselineCost;

        private Forecast(UUID simId, Integer iterations, Double p80Duration, Double p50Duration,
                         Double baselineDuration, BigDecimal p80Cost, BigDecimal baselineCost) {
            this.simId = simId;
            this.iterations = iterations != null ? iterations : 0;
            this.p80Duration = p80Duration;
            this.p50Duration = p50Duration;
            this.baselineDuration = baselineDuration != null ? baselineDuration : 0.0;
            this.p80Cost = p80Cost;
            this.baselineCost = baselineCost;
        }

        static Forecast fromEntity(MonteCarloSimulation s) {
            int iters = s.getIterationsCompleted() != null ? s.getIterationsCompleted()
                    : (s.getIterations() != null ? s.getIterations() : 0);
            return new Forecast(s.getId(), iters, s.getConfidenceP80Duration(), s.getConfidenceP50Duration(),
                    s.getBaselineDuration(), s.getConfidenceP80Cost(), s.getBaselineCost());
        }

        static Forecast fromDto(MonteCarloSimulationDto d) {
            int iters = d.getIterationsCompleted() != null ? d.getIterationsCompleted()
                    : (d.getIterations() != null ? d.getIterations() : 0);
            return new Forecast(d.getId(), iters, d.getConfidenceP80Duration(), d.getConfidenceP50Duration(),
                    d.getBaselineDuration(), d.getConfidenceP80Cost(), d.getBaselineCost());
        }
    }
}
