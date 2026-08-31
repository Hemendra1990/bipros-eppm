package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.resource.application.dto.MaterialBalanceRow;
import com.bipros.resource.application.service.MaterialBalanceService;
import com.bipros.resource.application.service.MaterialKpiService;
import com.bipros.resource.application.service.MaterialKpiService.CostPerUnitRow;
import com.bipros.resource.application.service.MaterialKpiService.MaterialKpiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Material Intelligence agent. Deterministic {@link #gather} over the project material ledger,
 * surfacing the canonical Material KPIs by delegating to {@link MaterialKpiService} (the same
 * computation behind the Material Consumption Report / Insights tab — no formula is re-derived):
 *
 * <ul>
 *   <li><b>Material Utilisation</b> = consumed ÷ issued ; <b>Wastage %</b> = (issued − consumed) ÷ issued</li>
 *   <li><b>Reconciliation Balance</b> = issued − consumed − wastage (0 when the ledger closes)</li>
 *   <li><b>Cost per Unit Finished</b> = Σ material line cost ÷ Σ qty executed, per activity</li>
 * </ul>
 *
 * <p>Dormant on projects with no material ledger (nothing issued or consumed).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "material_intelligence";
    private static final Duration TTL = Duration.ofDays(7);
    /** Wastage share (fraction) bands. */
    private static final double WASTAGE_HIGH = 0.10;
    private static final double WASTAGE_MEDIUM = 0.05;
    private static final int MAX_EXAMPLES = 6;
    /** Days-of-cover threshold for the low-stock finding (the mail digest reads its own
     *  configurable copy in bipros-api — keep the default in step with the seeder value 3). */
    private static final int LOW_COVER_DAYS = 3;

    private final MaterialKpiService materialKpi;
    private final MaterialBalanceService balanceService;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Material Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        MaterialKpiResponse k = materialKpi.compute(projectId, null, null);
        List<MaterialBalanceRow> shortages = safeShortages(projectId);
        if (k.issuedQty() <= 0 && k.consumedQty() <= 0 && shortages.isEmpty()) {
            // No material ledger — nothing to judge.
            return new GatherResult(snapshot, candidates);
        }

        snapshot.put("issuedQty", k.issuedQty());
        snapshot.put("consumedQty", k.consumedQty());
        snapshot.put("wastagePct", k.wastagePct());
        snapshot.put("reconciliationBalance", k.reconciliationBalance());
        snapshot.put("costPerUnitActivities", k.costPerUnitByActivity().size());
        snapshot.put("lowStockMaterials", shortages.size());

        if (!shortages.isEmpty()) {
            candidates.add(lowStock(projectId, shortages, validUntil));
        }

        if (k.issuedQty() > 0 && k.wastagePct() >= WASTAGE_MEDIUM) {
            candidates.add(highWastage(projectId, k, validUntil));
        }
        // Reconciliation only fails to close when consumed exceeds issued (a ledger data gap).
        if (Math.abs(k.reconciliationBalance()) > 0.001 && k.issuedQty() > 0) {
            candidates.add(reconciliationImbalance(projectId, k, validUntil));
        }
        // Activities whose material cost/unit runs over the BOQ budgeted rate (unfavourable variance).
        List<CostPerUnitRow> overBoq = new ArrayList<>();
        for (CostPerUnitRow r : k.costPerUnitByActivity()) {
            if (r.varianceVsBoqPct() != null && r.varianceVsBoqPct() < 0) overBoq.add(r);
        }
        if (!overBoq.isEmpty()) {
            candidates.add(highCostPerUnit(projectId, overBoq, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft highWastage(UUID projectId, MaterialKpiResponse k, Instant validUntil) {
        double wastagePct = k.wastagePct() * 100;
        double utilPct = k.materialUtilizationPct() * 100;
        Severity severity = k.wastagePct() >= WASTAGE_HIGH ? Severity.HIGH : Severity.MEDIUM;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Wastage %", pct(wastagePct)));
        ev.add(EvidenceRef.metric("Material utilisation", pct(utilPct)));
        ev.add(EvidenceRef.metric("Issued", qty(k.issuedQty())));
        ev.add(EvidenceRef.metric("Consumed", qty(k.consumedQty())));
        ev.add(EvidenceRef.metric("Wastage qty", qty(k.wastageQty())));
        ev.add(EvidenceRef.entity("Material report", "Open", "project", projectId,
                "/projects/" + projectId + "/material-consumption"));

        return new AgentFindingDraft(
                "MATERIAL_HIGH_WASTAGE", "PROJECT", severity, 0.9,
                "Material wastage from the ledger (wastage = issued − consumed; wastage % = wastage ÷ issued)",
                "Material wastage is " + pct(wastagePct) + " of what was issued",
                pct(wastagePct) + " of issued material is unaccounted for as consumption (" + qty(k.wastageQty())
                        + " of " + qty(k.issuedQty()) + " issued) — a utilisation of only " + pct(utilPct) + ".",
                "Material is leaving the store faster than it is being consumed into the works — over-issue, "
                        + "site loss, theft, or unrecorded consumption on the fronts.",
                "Wasted material is spent budget with no earned value; it inflates the material cost on the Costs "
                        + "tab and the unit cost on the P&L without moving progress.",
                "Reconcile the store issues against DPR consumption, tighten issue-against-requirement control, and "
                        + "investigate the materials with the widest issued-vs-consumed gap.",
                ev, Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft reconciliationImbalance(UUID projectId, MaterialKpiResponse k, Instant validUntil) {
        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Reconciliation balance", qty(k.reconciliationBalance())));
        ev.add(EvidenceRef.metric("Issued", qty(k.issuedQty())));
        ev.add(EvidenceRef.metric("Consumed", qty(k.consumedQty())));
        ev.add(EvidenceRef.entity("Material report", "Open", "project", projectId,
                "/projects/" + projectId + "/material-consumption"));

        return new AgentFindingDraft(
                "MATERIAL_RECONCILIATION_IMBALANCE", "PROJECT", Severity.MEDIUM, 0.85,
                "Material reconciliation (issued − consumed − wastage should be 0)",
                "Material ledger does not reconcile — balance " + qty(k.reconciliationBalance()),
                "The material reconciliation balance is " + qty(k.reconciliationBalance())
                        + " (consumed " + qty(k.consumedQty()) + " against issued " + qty(k.issuedQty())
                        + ") — the issued, consumed and wastage quantities do not close to zero.",
                "Consumption is being recorded against material that was never issued from the store, or the "
                        + "issue/consumption logs are out of step — a data-integrity gap, not a physical one.",
                "An unreconciled ledger makes every downstream material cost and wastage figure unreliable.",
                "Reconcile the store issues and DPR consumption logs for the affected materials so the balance "
                        + "closes before trusting the material cost KPIs.",
                ev, Map.of("SITE_MANAGER", List.of(), "STORE_KEEPER", List.of()), validUntil);
    }

    private AgentFindingDraft highCostPerUnit(UUID projectId, List<CostPerUnitRow> overBoq, Instant validUntil) {
        overBoq.sort((a, b) -> Double.compare(a.varianceVsBoqPct(), b.varianceVsBoqPct())); // most negative first
        int n = overBoq.size();
        CostPerUnitRow worst = overBoq.get(0);
        double worstOverPct = worst.varianceVsBoqPct() != null ? -worst.varianceVsBoqPct() * 100 : 0;
        Severity severity = worstOverPct >= 25 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Activities over BOQ material rate", String.valueOf(n)));
        for (CostPerUnitRow r : overBoq.subList(0, Math.min(MAX_EXAMPLES, n))) {
            double overPct = r.varianceVsBoqPct() != null ? -r.varianceVsBoqPct() * 100 : 0;
            ev.add(EvidenceRef.entity(trim(r.activityName()),
                    money(r.costPerUnit()) + " / unit vs BOQ " + money(nz(r.boqBudgetedRate()))
                            + " (" + pct(overPct) + " over)",
                    "activity", r.activityId(),
                    r.activityId() == null ? "/projects/" + projectId + "/material-consumption"
                            : "/projects/" + projectId + "/activities/" + r.activityId()));
        }

        return new AgentFindingDraft(
                "HIGH_MATERIAL_COST_PER_UNIT", "PROJECT", severity, 0.8,
                "Actual material cost per unit finished (Σ material line cost ÷ Σ qty executed) vs the BOQ budgeted rate",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " consuming material above the BOQ budgeted rate",
                "The worst, " + trim(worst.activityName()) + ", is costing " + money(worst.costPerUnit())
                        + " of material per unit — about " + pct(worstOverPct) + " over its BOQ budgeted rate of "
                        + money(nz(worst.boqBudgetedRate())) + ".",
                "These activities are consuming more material value per unit built than the BOQ priced — over-use, "
                        + "wastage concentrated on these fronts, or a rate that has drifted above budget.",
                "Material cost overruns here feed straight into the negative cost variance the Costs and P&L tabs "
                        + "report.",
                "Review the highest-variance activities' material consumption against the BOQ norm with the site "
                        + "team; curb over-use and verify the recorded unit rates.",
                ev, Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    /** Short-supply detection must never sink the whole gather. */
    private List<MaterialBalanceRow> safeShortages(UUID projectId) {
        try {
            return balanceService.shortages(projectId, LOW_COVER_DAYS);
        } catch (Exception e) {
            log.warn("Material shortage check failed for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private AgentFindingDraft lowStock(UUID projectId, List<MaterialBalanceRow> shortages, Instant validUntil) {
        int n = shortages.size();
        boolean acute = shortages.stream().anyMatch(r ->
                (r.daysOfCover() != null && r.daysOfCover().doubleValue() < 2)
                || (r.minStockLevel() != null && r.storeClosing() != null
                    && r.storeClosing().doubleValue() < r.minStockLevel().doubleValue() * 0.5));
        Severity severity = acute ? Severity.HIGH : Severity.MEDIUM;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Materials in short supply", String.valueOf(n)));
        for (MaterialBalanceRow r : shortages.subList(0, Math.min(MAX_EXAMPLES, n))) {
            String detail = "closing " + r.storeClosing() + (r.unit() == null ? "" : " " + r.unit())
                    + (r.daysOfCover() != null ? " · " + r.daysOfCover() + " day(s) cover" : "")
                    + (r.minStockLevel() != null ? " · min " + r.minStockLevel() : "");
            ev.add(EvidenceRef.entity(trim(r.materialName()), detail, "project", projectId,
                    "/projects/" + projectId + "/reports/material-consumption"));
        }

        MaterialBalanceRow worst = shortages.get(0);
        return new AgentFindingDraft(
                "MATERIAL_LOW_STOCK", "PROJECT", severity, 0.85,
                "Store closing balance vs minimum stock level (Material Catalogue), else days-of-cover "
                        + "(closing ÷ average daily consumption over the last 14 days, threshold "
                        + LOW_COVER_DAYS + ")",
                n + " material" + (n == 1 ? " is" : "s are") + " in short supply",
                "The tightest, " + trim(worst.materialName()) + ", has a closing balance of "
                        + worst.storeClosing() + (worst.unit() == null ? "" : " " + worst.unit())
                        + (worst.daysOfCover() != null
                            ? " — about " + worst.daysOfCover() + " day(s) of cover at the recent burn rate."
                            : " — below its minimum stock level."),
                "Stock is running down faster than receipts are replenishing it — deliveries lagging, "
                        + "consumption ahead of plan, or GRN entries not being recorded.",
                "A stock-out stops the affected work fronts outright; idle crews and plant keep costing while "
                        + "no progress is earned.",
                "Raise the purchase/indent for the short materials now (respect the lead time on the catalogue), "
                        + "and verify recent receipts were actually entered as GRNs.",
                ev, Map.of("SITE_MANAGER", List.of(), "STORE_KEEPER", List.of(),
                        "PROJECT_MANAGER", List.of()), validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static String trim(String s) {
        if (s == null || s.isBlank()) return "activity";
        return s.length() > 48 ? s.substring(0, 47) + "…" : s;
    }

    private static double nz(Double d) {
        return d == null ? 0 : d;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
    }

    private static String qty(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }
}
