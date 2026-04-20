package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.cost.domain.repository.CostAccountRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS M4 GAP — DMIC-PROG cost accounts + activity-level expense posting.
 *
 * <p>Seeds six top-level cost accounts (LABOUR / EQUIPMENT / MATERIAL / SUBCONTRACT /
 * OVERHEAD / CONTINGENCY) with budgets summing to ~₹22,000 cr (matches N03 WBS). Then
 * posts 24 activity expenses across the live 34 DMIC-N03 activities — amounts are kept
 * modest (roughly 60–95% of each activity's budgeted share) so the programme dashboard
 * continues to show the CPI ~ 0.93 storyline.
 *
 * <p>Field-name reality check:
 * <ul>
 *   <li>{@code CostAccount} is a pure chart-of-accounts record with {@code code},
 *       {@code name}, {@code description}, {@code parentId}, {@code sortOrder}. It has
 *       NO {@code projectId}, {@code accountType} enum, {@code budgetAmount}, or
 *       {@code wbsNodeId} columns — those concepts live on {@link ActivityExpense} or
 *       outside the entity. Account type is encoded as a prefix on {@code code}
 *       (CA-DMIC-N03-LABR / CA-DMIC-N03-EQP / CA-DMIC-N03-MAT / CA-DMIC-N03-SUB /
 *       CA-DMIC-N03-OH / CA-DMIC-N03-CONT). The budget figure is embedded in the
 *       description so the dashboard can parse it.</li>
 *   <li>{@code ActivityExpense} has {@code projectId}, {@code activityId},
 *       {@code costAccountId}, {@code name}, {@code expenseCategory},
 *       {@code budgetedCost}, {@code actualCost}, {@code remainingCost},
 *       {@code atCompletionCost}, {@code percentComplete}, and the planned/actual dates.
 *       Matches the spec terminology "amount" ≈ {@code actualCost}.</li>
 * </ul>
 *
 * <p>Judgment calls:
 * <ul>
 *   <li>Budget split: LABOUR 18%, EQUIPMENT 12%, MATERIAL 40%, SUBCONTRACT 22%,
 *       OVERHEAD 5%, CONTINGENCY 3% of ₹22,000 cr — based on typical DMIC infra mix.</li>
 *   <li>Activity expense posting is weighted to started/in-progress activities only;
 *       NOT_STARTED activities get no posting (makes CPI computation clean).</li>
 * </ul>
 *
 * <p>Sentinel: {@code costAccountRepository.count() > 0} — skips on re-run.
 */
@Slf4j
@Component
@Profile("dev")
@Order(122)
@RequiredArgsConstructor
public class IcpmsCostAccountSeeder implements CommandLineRunner {

    private final CostAccountRepository costAccountRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ActivityRepository activityRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (costAccountRepository.count() > 0) {
            log.info("[IC-PMS M4 GAP] cost accounts already present, skipping");
            return;
        }

        Project project = projectRepository.findByCode("DMIC-PROG")
                .orElseThrow(() -> new IllegalStateException("DMIC-PROG project not seeded — run Phase A first"));
        UUID projectId = project.getId();

        // ---- cost accounts ----
        // Parent roll-up row so the six children hang off a single DMIC-PROG node.
        CostAccount root = saveAccount("CA-DMIC-N03", "DMIC Dholera SIR (N03) — Project Control Account",
                "Roll-up control account for the ₹22,000 cr Dholera SIR (N03) delivery.", null, 0);

        Map<String, CostAccount> byType = new HashMap<>();
        byType.put("LABR", saveAccount("CA-DMIC-N03-LABR", "Labour & Manpower",
                "Direct labour + supervisory payroll. Budget: ₹3,960 cr (18% of N03).", root.getId(), 10));
        byType.put("EQP", saveAccount("CA-DMIC-N03-EQP", "Plant & Equipment",
                "Earthmoving, transport and construction equipment. Budget: ₹2,640 cr (12%).", root.getId(), 20));
        byType.put("MAT", saveAccount("CA-DMIC-N03-MAT", "Materials & Permanent Works",
                "Aggregates, steel, bitumen, cement, ducting, cable, GIS items. Budget: ₹8,800 cr (40%).",
                root.getId(), 30));
        byType.put("SUB", saveAccount("CA-DMIC-N03-SUB", "Subcontractor Payments",
                "EPC partner + specialist subcontractor invoices. Budget: ₹4,840 cr (22%).", root.getId(), 40));
        byType.put("OH", saveAccount("CA-DMIC-N03-OH", "Overhead & Indirects",
                "Site establishment, PMC fees, insurance, bonds. Budget: ₹1,100 cr (5%).", root.getId(), 50));
        byType.put("CONT", saveAccount("CA-DMIC-N03-CONT", "Contingency & Escalation",
                "Reserve against price escalation and scope variance. Budget: ₹660 cr (3%).", root.getId(), 60));

        // ---- activity expenses ----
        List<Activity> activities = activityRepository.findByProjectId(projectId);

        int postings = 0;
        for (Activity a : activities) {
            String name = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
            // Only post expenses for activities that have started
            if (a.getActualStartDate() == null) continue;

            // Pick dominant cost account by activity keywords
            CostAccount acct;
            if (name.contains("mobilis") || name.contains("site establishment")) {
                acct = byType.get("OH");
            } else if (name.contains("survey") || name.contains("testing") || name.contains("report")) {
                acct = byType.get("SUB");
            } else if (name.contains("transformer") || name.contains("switchgear") || name.contains("gis ")
                    || name.contains("fibre") || name.contains("pump") || name.contains("cable")) {
                acct = byType.get("MAT");
            } else if (name.contains("earthwork") || name.contains("excavation") || name.contains("embankment")
                    || name.contains("compaction") || name.contains("clearing")) {
                acct = byType.get("EQP");
            } else if (name.contains("pavement") || name.contains("bituminous") || name.contains("wmm")
                    || name.contains("sub-base") || name.contains("gsb") || name.contains("dbm")
                    || name.contains("bc ")) {
                acct = byType.get("MAT");
            } else if (name.contains("civil") || name.contains("building") || name.contains("wtp")) {
                acct = byType.get("SUB");
            } else {
                acct = byType.get("LABR");
            }

            // Budget figure pulled from the EVM note on the activity (BCWS lakh) if available,
            // otherwise scaled from originalDuration; amounts stay in ₹ lakh converted to ₹.
            double bcwsLakh = parseBcws(a.getNotes());
            double bcwpLakh = parseBcwp(a.getNotes());
            double acwpLakh = parseAcwp(a.getNotes());
            if (bcwsLakh <= 0.0) {
                // Fallback when notes don't have EVM — use duration * ₹5 lakh/day as a ballpark.
                bcwsLakh = (a.getOriginalDuration() == null ? 30.0 : a.getOriginalDuration()) * 5.0;
                bcwpLakh = bcwsLakh * (a.getPercentComplete() == null ? 0.0 : a.getPercentComplete() / 100.0);
                acwpLakh = bcwpLakh * 1.05; // CPI-ish 0.95
            }

            BigDecimal budgeted = BigDecimal.valueOf(bcwsLakh * 100_000L); // lakh → ₹
            BigDecimal actual = BigDecimal.valueOf(acwpLakh * 100_000L);
            BigDecimal remaining = budgeted.subtract(actual).max(BigDecimal.ZERO);
            BigDecimal atCompletion = actual.add(remaining);
            Double pct = a.getPercentComplete();

            ActivityExpense e = new ActivityExpense();
            e.setProjectId(projectId);
            e.setActivityId(a.getId());
            e.setCostAccountId(acct.getId());
            e.setName(a.getName());
            e.setDescription("Expense posting for activity " + a.getCode()
                    + " against " + acct.getCode()
                    + ". BCWS/BCWP/ACWP (₹ lakh): " + bcwsLakh + " / " + bcwpLakh + " / " + acwpLakh);
            e.setExpenseCategory(acct.getCode().substring("CA-DMIC-N03-".length()));
            e.setBudgetedCost(budgeted);
            e.setActualCost(actual);
            e.setRemainingCost(remaining);
            e.setAtCompletionCost(atCompletion);
            e.setPercentComplete(pct);
            e.setPlannedStartDate(a.getPlannedStartDate());
            e.setPlannedFinishDate(a.getPlannedFinishDate());
            e.setActualStartDate(a.getActualStartDate());
            e.setActualFinishDate(a.getActualFinishDate());
            activityExpenseRepository.save(e);
            postings++;
        }

        log.info("[IC-PMS M4 GAP] seeded {} cost accounts (root + 6 types) and {} activity-expense postings",
                1 + byType.size(), postings);
    }

    // ------------------------------------------------------------------ helpers

    private CostAccount saveAccount(String code, String name, String description, UUID parentId, int sortOrder) {
        CostAccount a = new CostAccount();
        a.setCode(code);
        a.setName(name);
        a.setDescription(description);
        a.setParentId(parentId);
        a.setSortOrder(sortOrder);
        return costAccountRepository.save(a);
    }

    /** Parses a single numeric value following {@code tag} in the activity note — e.g. "BCWS 285". */
    private static double parseTag(String note, String tag) {
        if (note == null) return 0.0;
        int idx = note.indexOf(tag);
        if (idx < 0) return 0.0;
        int start = idx + tag.length();
        // skip spaces
        while (start < note.length() && !Character.isDigit(note.charAt(start)) && note.charAt(start) != '.') {
            start++;
            if (start > idx + tag.length() + 3) return 0.0; // guard
        }
        int end = start;
        while (end < note.length() && (Character.isDigit(note.charAt(end)) || note.charAt(end) == '.')) end++;
        try {
            return end > start ? Double.parseDouble(note.substring(start, end)) : 0.0;
        } catch (NumberFormatException nfe) {
            return 0.0;
        }
    }

    private static double parseBcws(String note) { return parseTag(note, "BCWS"); }
    private static double parseBcwp(String note) { return parseTag(note, "BCWP"); }
    private static double parseAcwp(String note) { return parseTag(note, "ACWP"); }
}
