package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.InsightsResponse;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Consolidated Daily Project Report - one template for both the email body and the PDF
 * (openhtmltopdf = strict XHTML + CSS2: table layouts and inline styles only, no scripts;
 * charts are pure-CSS horizontal bars, identical in the PDF engine and Gmail/Outlook).
 * Every figure comes straight from {@link DprReportMetrics} (canonical engines); the LLM only
 * contributes prose.
 *
 * <p>ENCODING RULE: this file is deliberately pure ASCII - every special character
 * (middle dot &#183;, em dash &#8212;, check &#10004;) is written as an XML numeric entity so
 * no compiler/file-encoding mismatch can ever corrupt the output again (2026-08-05 lesson).
 */
@Service
public class DprReportHtmlRenderer {

    private static final String DOT = " &#183; ";
    private static final String DASH = " &#8212; ";

    private static final String GOLD = "#C9A227";
    private static final String GOLD_DARK = "#8A6D1B";
    private static final String INK = "#232A31";
    private static final String MUTED = "#6A7178";
    private static final String LINE = "#E3E1DA";
    private static final String ZEBRA = "#F7F6F2";
    private static final String GOOD = "#2E7D4F";
    private static final String WARN = "#B07818";
    private static final String BAD = "#B3372E";

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0");
    private static final DecimalFormat QTY = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PCT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat COMPACT = new DecimalFormat("#,##0.00");

    public String render(InsightsResponse r, DprReportMetrics m, String windowLabel) {
        return render(r, m, windowLabel, List.of());
    }

    public String render(InsightsResponse r, DprReportMetrics m, String windowLabel,
                         List<com.bipros.ai.agent.domain.AgentFinding> agentFlags) {
        String cur = m.currencyCode == null ? "" : m.currencyCode;
        StringBuilder b = new StringBuilder(16384);

        b.append("<table width='680' cellpadding='0' cellspacing='0' style='margin:0 auto;font-family:Arial,Helvetica,sans-serif;color:")
         .append(INK).append(";border:1px solid ").append(LINE).append(";border-collapse:collapse'>");

        // Header band
        b.append("<tr><td style='background:").append(INK).append(";padding:20px 26px;border-bottom:4px solid ").append(GOLD).append("'>")
         .append("<div style='font-size:11px;letter-spacing:2px;color:").append(GOLD).append(";text-transform:uppercase'>Sarooj").append(DOT).append("Daily Project Report</div>")
         .append("<div style='font-size:20px;font-weight:bold;color:#ffffff;margin-top:5px'>").append(esc(m.projectName)).append("</div>")
         .append("<div style='font-size:12px;color:#B9C0C6;margin-top:3px'>Window: ").append(esc(windowLabel))
         .append(DOT).append("all money in ").append(esc(cur)).append("</div>")
         .append("</td></tr>");

        b.append("<tr><td style='padding:20px 26px'>");

        // Executive summary + verification note
        if (r.summary() != null && !r.summary().isBlank()) {
            b.append("<div style='font-size:13px;line-height:1.55;margin:0 0 6px'>").append(esc(r.summary())).append("</div>");
        }
        if (r.rationale() != null && r.rationale().contains("could not be verified")) {
            b.append("<div style='margin:8px 0;padding:8px 12px;border:1px solid ").append(WARN)
             .append(";background:#FFFBEB;color:#92400E;font-size:12px'><b>&#9888; Verification note:</b> ")
             .append(esc(r.rationale())).append("</div>");
        }

        // At a glance
        sectionTitle(b, "At a glance");
        b.append("<table width='100%' cellpadding='0' cellspacing='6' style='border-collapse:separate'><tr>");
        kpi(b, String.valueOf(m.totalDprs), "Approved DPRs", INK);
        kpi(b, MONEY.format(m.dbsIncome), "Income " + cur, GOOD);
        kpi(b, MONEY.format(m.dbsExpense), "Expense " + cur, INK);
        kpi(b, MONEY.format(m.dbsContribution), "Contribution (income &#8722; expense)", m.dbsContribution >= 0 ? GOOD : BAD);
        b.append("</tr><tr>");
        kpi(b, String.valueOf(m.openIssues), "Open issues", m.openIssues > 0 ? WARN : INK);
        kpi(b, String.valueOf(m.criticalIssues), "Critical issues", m.criticalIssues > 0 ? BAD : INK);
        kpi(b, String.valueOf(m.safetyIncidents), "Safety incidents", m.safetyIncidents > 0 ? BAD : INK);
        kpi(b, m.evm == null ? "n/a" : PCT.format(m.evm.pctComplete()) + "%", "Project complete (EV/BAC)", GOLD_DARK);
        b.append("</tr></table>");

        // 1 - Daily progress per supervisor
        sectionTitle(b, "1" + DOT + "Daily progress (DPR)" + DASH + "per supervisor");
        if (m.supervisorWork.isEmpty()) {
            zero(b, "No approved DPRs in this window" + DASH + "no site activity was recorded.");
        } else {
            b.append(tableOpen())
             .append(th("Supervisor", "left")).append(th("DPRs", "right")).append(th("Activities", "right"))
             .append(th("Workdone qty*", "right")).append("</tr>");
            int i = 0;
            for (var w : m.supervisorWork) {
                b.append(trOpen(i++))
                 .append(td(esc(w.name()), "left"))
                 .append(td(String.valueOf(w.dprCount()), "right"))
                 .append(td(String.valueOf(w.activityCount()), "right"))
                 .append(td(QTY.format(w.qty()), "right"))
                 .append("</tr>");
            }
            b.append("</table>")
             .append(note("* Raw workdone tally across all operations; billing uses the measurement-operation basis (BOQ tab)."));
        }

        // 2 - Supervisor performance (AI Agent sheet DPR row: reported daily/cumulative)
        sectionTitle(b, "2" + DOT + "Supervisor performance" + DASH + "day &amp; cumulative");
        if (m.supervisorPerformance.isEmpty()) {
            zero(b, "No approved DPRs in this window.");
        } else {
            String dayLabel = m.referenceDay != null ? " " + m.referenceDay : "";
            b.append(tableOpen())
             .append(th("Supervisor", "left"))
             .append(th("DPRs" + dayLabel, "right")).append(th("Qty" + dayLabel, "right"))
             .append(th("DPRs (window)", "right")).append(th("Qty (window)", "right"))
             .append(th("Contribution " + cur, "right")).append("</tr>");
            int spIdx = 0;
            for (var p : m.supervisorPerformance) {
                b.append(trOpen(spIdx++))
                 .append(td(esc(p.name()), "left"))
                 .append(td(String.valueOf(p.filedDay()), "right"))
                 .append(td(QTY.format(p.qtyDay()), "right"))
                 .append(td(String.valueOf(p.filedWindow()), "right"))
                 .append(td(QTY.format(p.qtyWindow()), "right"));
                if (p.contribution() != null) {
                    b.append("<td style='padding:5px 8px;text-align:right;font-size:12px;font-weight:bold;color:")
                     .append(p.contribution() >= 0 ? GOOD : BAD).append("'>")
                     .append(MONEY.format(p.contribution())).append("</td>");
                } else {
                    b.append(td("&#8212;", "right"));
                }
                b.append("</tr>");
            }
            b.append("</table>")
             .append(note("Day = the latest report date in the window" + DOT
                     + "contribution from the DBS engine" + DOT
                     + "per-supervisor efficiency vs norms lives in Capacity Util. (supervisor view)."));
        }

        // 3 - Capacity utilization
        sectionTitle(b, "3" + DOT + "Capacity utilization" + DASH + "efficiency vs productivity norms");
        if (m.roleEfficiencies.isEmpty()) {
            zero(b, "No productivity-tracked resources in this window.");
        } else {
            b.append(barTableOpen());
            for (var e : m.roleEfficiencies) {
                double pct = e.utilizationPct() == null ? 0 : Math.max(0, Math.min(e.utilizationPct(), 130));
                // Bands per client workbook: >=100 green, 90-99 yellow, <90 red (CAP-16).
                String color = e.utilizationPct() == null ? MUTED
                        : e.utilizationPct() >= 100 ? GOOD : e.utilizationPct() >= 90 ? WARN : BAD;
                String label = e.utilizationPct() == null ? "not tracked" : PCT.format(e.utilizationPct()) + "% efficient";
                bar(b, esc(e.role()), pct / 1.3, color, label + moneyImpact(e.costImplication(), cur));
            }
            b.append("</table>")
             .append(note("Efficiency = output vs the productivity norm per resource-day (the Capacity Util. tab's own engine). "
                     + "100% = crews delivered exactly what the norms expect."));
        }
        // Resource-wise tables (capacity-agent-row addition 2026-08-10) - the Capacity Util.
        // tab's own day + window buckets per role, so this email IS the resource capacity
        // utilization report the project-control team receives.
        capacityTable(b, "Manpower", m.capacityManpower, m.capacityManpowerTotal, cur);
        capacityTable(b, "Equipment", m.capacityEquipment, m.capacityEquipmentTotal, cur);

        // 4 - DBS
        sectionTitle(b, "4" + DOT + "Daily balance sheet" + DASH + "income vs expense per day");
        if (m.dbsDays.isEmpty()) {
            zero(b, "No DBS rows in this window.");
        } else {
            double maxMoney = 1;
            for (var d : m.dbsDays) maxMoney = Math.max(maxMoney, Math.max(d.income(), d.expense()));
            b.append(barTableOpen());
            for (var d : m.dbsDays) {
                if (d.income() == 0 && d.expense() == 0 && d.dprCount() == 0) continue;
                bar(b, esc(d.date()), d.income() / maxMoney * 100, GOOD,
                        "income <b>" + MONEY.format(d.income()) + "</b>");
                bar(b, "", d.expense() / maxMoney * 100, BAD,
                        "expense <b>" + MONEY.format(d.expense()) + "</b>");
            }
            b.append("</table>");
            b.append("<div style='font-size:12px;margin:6px 0 0'><b>Window totals:</b> income ")
             .append(MONEY.format(m.dbsIncome)).append(DOT).append("expense ").append(MONEY.format(m.dbsExpense))
             .append(DOT).append("contribution <span style='color:").append(m.dbsContribution >= 0 ? GOOD : BAD)
             .append(";font-weight:bold'>").append(MONEY.format(m.dbsContribution)).append("</span> ").append(esc(cur))
             .append(m.dbsContribution < 0 ? " (spent more than earned in this window)" : "")
             .append("</div>");

            if (!m.dbsSupervisors.isEmpty()) {
                b.append("<div style='height:10px'></div>").append(tableOpen())
                 .append(th("Supervisor", "left")).append(th("Income", "right")).append(th("Expense", "right"))
                 .append(th("Contribution", "right")).append("</tr>");
                int i = 0;
                for (var s : m.dbsSupervisors) {
                    b.append(trOpen(i++))
                     .append(td(esc(s.name()), "left"))
                     .append(td(MONEY.format(s.income()), "right"))
                     .append(td(MONEY.format(s.expense()), "right"))
                     .append("<td style='padding:5px 8px;text-align:right;font-size:12px;font-weight:bold;color:")
                     .append(s.contribution() >= 0 ? GOOD : BAD).append("'>")
                     .append(MONEY.format(s.contribution())).append("</td>")
                     .append("</tr>");
                }
                b.append("</table>");
            }
            b.append(note("Day-basis figures from the DBS engine (supervisor to project rollup)" + DOT
                    + "contribution = income &#8722; expense."));
        }

        // 5 - Issues
        sectionTitle(b, "5" + DOT + "Issues reported on site");
        if (m.issueCategories.isEmpty()) {
            zero(b, "No issues logged with the window's DPRs.");
        } else {
            int maxCat = m.issueCategories.stream().mapToInt(DprReportMetrics.CatCount::count).max().orElse(1);
            b.append(barTableOpen());
            for (var c : m.issueCategories) {
                bar(b, esc(c.name()), c.count() * 100.0 / maxCat, WARN, c.count() + " issue" + (c.count() == 1 ? "" : "s"));
            }
            b.append("</table>")
             .append(note(m.openIssues + " open" + DOT + m.criticalIssues + " critical" + DOT + m.safetyIncidents + " HSE incident(s)."));
        }

        // 6 - Material consumption
        sectionTitle(b, "6" + DOT + "Material consumption (from approved DPRs)");
        if (m.materials.isEmpty()) {
            zero(b, "No material consumption recorded in this window.");
        } else {
            b.append(tableOpen())
             .append(th("Material", "left")).append(th("Qty", "right")).append(th("Unit", "left")).append(th("Cost " + cur, "right")).append("</tr>");
            int i = 0;
            for (var mat : m.materials) {
                b.append(trOpen(i++))
                 .append(td(esc(mat.name()), "left"))
                 .append(td(QTY.format(mat.qty()), "right"))
                 .append(td(esc(mat.unit() == null ? "" : mat.unit()), "left"))
                 .append(td(MONEY.format(mat.cost()), "right"))
                 .append("</tr>");
            }
            b.append("</table>");
        }

        // 6b - Material availability (store) - Material-agent-row 2026-08-11 (MAT-01/MAT-04)
        sectionTitle(b, "6b" + DOT + "Material availability (store)");
        if (!m.materialTracked) {
            zero(b, "Stock not tracked on this project" + DOT
                + "storekeeper GRN / issue-slip entries have not started, so receipts and closing balance cannot be computed.");
        } else if (m.materialAvailability.isEmpty()) {
            zero(b, "No material movements recorded in this window.");
        } else {
            var availTop = m.materialAvailability.stream().limit(15).toList();
            b.append(tableOpen())
             .append(th("Material", "left")).append(th("Unit", "left"))
             .append(th("Received", "right")).append(th("Issued", "right"))
             .append(th("Consumed", "right")).append(th("Closing balance", "right"))
             .append(th("Days cover", "right")).append(th("Alert", "left")).append("</tr>");
            int avIdx = 0;
            for (var a : availTop) {
                b.append(trOpen(avIdx++))
                 .append(td(esc(a.name()), "left"))
                 .append(td(esc(a.unit() == null ? "" : a.unit()), "left"))
                 .append(td(a.receivedWindow() != null ? QTY.format(a.receivedWindow()) : "&#8212;", "right"))
                 .append(td(a.issuedWindow() != null ? QTY.format(a.issuedWindow()) : "&#8212;", "right"))
                 .append(td(a.consumedWindow() != null ? QTY.format(a.consumedWindow()) : "&#8212;", "right"))
                 .append(td(a.storeClosing() != null ? QTY.format(a.storeClosing()) : "&#8212;", "right"))
                 .append(td(a.daysOfCover() != null ? QTY.format(a.daysOfCover()) : "&#8212;", "right"))
                 .append(td(alertLabel(a.alert()), "left"))
                 .append("</tr>");
            }
            b.append("</table>")
             .append(note("Received / Issued / Consumed are window figures" + DOT
                 + "closing balance and days-of-cover are as of the window end" + DOT
                 + "closing = received &#8722; issued, storekeeper log figure wins where entered."));
        }
        if (!m.supervisorMaterialVariances.isEmpty()) {
            var varTop = m.supervisorMaterialVariances.stream().limit(12).toList();
            b.append("<div style='height:10px'></div>").append(tableOpen())
             .append(th("Supervisor", "left")).append(th("Material", "left"))
             .append(th("Issued to date", "right")).append(th("Reported (DPR)", "right"))
             .append(th("Variance", "right")).append(th("Value " + cur, "right")).append("</tr>");
            int svIdx = 0;
            for (var v : varTop) {
                b.append(trOpen(svIdx++))
                 .append(td(esc(v.supervisor()), "left"))
                 .append(td(esc(v.material()) + (v.unit() == null ? "" : " (" + esc(v.unit()) + ")"), "left"))
                 .append(td(QTY.format(v.issuedToDate()), "right"))
                 .append(td(QTY.format(v.reportedToDate()), "right"))
                 .append(td(QTY.format(v.varianceQty()), "right"))
                 .append(td(v.varianceValue() != null ? MONEY.format(v.varianceValue()) : "&#8212;", "right"))
                 .append("</tr>");
            }
            b.append("</table>")
             .append(note("Issued vs supervisor-reported material" + DOT
                 + "cumulative from the first store movement to the window end" + DOT
                 + "flag only &#8212; DBS costing awaits the tolerance ruling (open question Q20)."));
        }

        // 7 - Commodity summary (AI Agent sheet DPR row: executed qty at BOQ + activity level)
        sectionTitle(b, "7" + DOT + "Commodity summary" + DASH + "executed quantities");
        if (m.commodityBoq.isEmpty()) {
            zero(b, "No BOQ-linked execution recorded yet.");
        } else {
            var boqTop = m.commodityBoq.stream().limit(15).toList();
            b.append(tableOpen())
             .append(th("BOQ item", "left")).append(th("Unit", "left")).append(th("Contract qty", "right"))
             .append(th("This month", "right")).append(th("Till date", "right")).append(th("% complete", "right"))
             .append("</tr>");
            int cIdx = 0;
            for (var c : boqTop) {
                b.append(trOpen(cIdx++))
                 .append(td(esc(c.label()), "left"))
                 .append(td(esc(c.unit() == null ? "" : c.unit()), "left"))
                 .append(td(c.contractedQty() != null ? QTY.format(c.contractedQty()) : "&#8212;", "right"))
                 .append(td(QTY.format(c.qtyMonth()), "right"))
                 .append(td(QTY.format(c.qtyToDate()), "right"))
                 .append(td(c.pctComplete() != null ? PCT.format(c.pctComplete()) + "%" : "&#8212;", "right"))
                 .append("</tr>");
            }
            b.append("</table>");
            if (m.commodityBoq.size() > boqTop.size()) {
                b.append(note("Showing " + boqTop.size() + " of " + m.commodityBoq.size()
                        + " BOQ lines with execution" + DOT + "full list on the BOQ tab."));
            }
            if (!m.commodityActivities.isEmpty()) {
                var actTop = m.commodityActivities.stream().limit(10).toList();
                b.append("<div style='height:10px'></div>").append(tableOpen())
                 .append(th("Activity", "left")).append(th("Unit", "left"))
                 .append(th("This month", "right")).append(th("Window total", "right")).append("</tr>");
                int aIdx = 0;
                for (var c : actTop) {
                    b.append(trOpen(aIdx++))
                     .append(td(esc(c.label()), "left"))
                     .append(td(esc(c.unit() == null ? "" : c.unit()), "left"))
                     .append(td(QTY.format(c.qtyMonth()), "right"))
                     .append(td(QTY.format(c.qtyToDate()), "right"))
                     .append("</tr>");
                }
                b.append("</table>");
            }
            b.append(note("Executed quantities from approved DPRs" + DOT
                    + "\"This month\" = the calendar month of the latest report date" + DOT
                    + "till-date uses the BOQ tab's stored columns (billing basis)."));
        }

        // 8 - Costing (informative table, not cryptic bars)
        sectionTitle(b, "8" + DOT + "Costing" + DASH + "largest BOQ cost variances");
        if (m.boqTopVariances.isEmpty()) {
            zero(b, "No cost variances on executed BOQ lines.");
        } else {
            b.append(tableOpen())
             .append(th("BOQ item", "left")).append(th("Description", "left")).append(th("Qty done", "right"))
             .append(th("Rate: actual vs budgeted", "right")).append(th("Result", "right")).append("</tr>");
            int i = 0;
            for (var v : m.boqTopVariances) {
                boolean over = v.variance() > 0;
                b.append(trOpen(i++))
                 .append(td("<b>" + esc(v.itemNo()) + "</b>", "left"))
                 .append(td(esc(truncate(v.label(), 34)), "left"))
                 .append(td(QTY.format(v.qtyExecuted()), "right"))
                 .append(td(QTY.format(v.actualRate()) + " vs " + QTY.format(v.budgetedRate()), "right"))
                 .append("<td style='padding:5px 8px;text-align:right;font-size:12px;font-weight:bold;color:")
                 .append(over ? BAD : GOOD).append("'>")
                 .append(over ? "overrun " : "saved ").append(MONEY.format(Math.abs(v.variance())))
                 .append("</td></tr>");
            }
            b.append("</table>");
            b.append("<div style='font-size:12px;margin:6px 0 0'><b>Whole-project cost variance:</b> <span style='color:")
             .append(m.boqTotalVariance > 0 ? BAD : GOOD).append(";font-weight:bold'>")
             .append(m.boqTotalVariance > 0 ? "overrun " : "saved ").append(MONEY.format(Math.abs(m.boqTotalVariance)))
             .append(" ").append(esc(cur)).append("</span></div>")
             .append(note("Variance = actual spend &#8722; budgeted cost of the executed quantity (the rate gap priced over the "
                     + "quantity done). Red = paying more than budgeted. Stored BOQ-tab columns; split lines on the billable basis."));
        }

        // 8b - Activity costing (Costing-agent-row 2026-08-11): actual vs budgeted vs BOQ value
        sectionTitle(b, "8b" + DOT + "Activity costing" + DASH + "actual vs budgeted vs BOQ value");
        if (m.activityCosting.isEmpty()) {
            zero(b, "No costed activity execution in this window.");
        } else {
            b.append(tableOpen())
             .append(th("Activity", "left")).append(th("Qty", "right")).append(th("Unit", "left"))
             .append(th("Actual " + cur, "right")).append(th("@Budgeted rates", "right"))
             .append(th("@BOQ rates", "right")).append(th("Over/(under) budget", "right")).append("</tr>");
            int acIdx = 0;
            for (var l : m.activityCosting) {
                b.append(trOpen(acIdx++))
                 .append(td(esc(truncate(l.activity(), 40)), "left"))
                 .append(td(l.qty() != null ? QTY.format(l.qty()) : "&#8212;", "right"))
                 .append(td(esc(l.unit() == null ? "" : l.unit()), "left"))
                 .append(td(MONEY.format(l.actualCost()), "right"))
                 .append(td(l.budgetedValue() != null ? MONEY.format(l.budgetedValue()) : "&#8212;", "right"))
                 .append(td(l.boqValue() != null ? MONEY.format(l.boqValue()) : "&#8212;", "right"))
                 .append(varianceCell(l.varVsBudgeted()))
                 .append("</tr>");
            }
            var t = m.activityCostingTotal;
            if (t != null) {
                b.append("<tr style='font-weight:bold;border-top:2px solid #ccc'>")
                 .append(td("<b>Total (all " + m.activityCostingCount + " activities)</b>", "left"))
                 .append(td("&#8212;", "right")).append(td("", "left"))
                 .append(td("<b>" + MONEY.format(t.actualCost()) + "</b>", "right"))
                 .append(td(t.budgetedValue() != null ? "<b>" + MONEY.format(t.budgetedValue()) + "</b>" : "&#8212;", "right"))
                 .append(td(t.boqValue() != null ? "<b>" + MONEY.format(t.boqValue()) + "</b>" : "&#8212;", "right"))
                 .append(varianceCell(t.varVsBudgeted()))
                 .append("</tr>");
            }
            b.append("</table>");
            if (m.activityCostingCount > m.activityCosting.size()) {
                b.append(note("Top " + m.activityCosting.size() + " of " + m.activityCostingCount
                        + " activities by absolute budget variance" + DOT + "full detail on the P&amp;L pages."));
            }
            b.append(note("Actual = approved-DPR line costs (manpower, equipment, material, sub-contractor)" + DOT
                    + "@Budgeted / @BOQ = executed qty priced at the BOQ line's budgeted / contract rate" + DOT
                    + "split-line non-measurement rows carry no BOQ value" + DOT
                    + "positive variance (red) = costing more than the rate allows."));
        }

        // 9 - EVM (EVM-agent-row 2026-08-11: full health parameter set + key notes + dashboard
        // link, per the AI Agents sheet "summarize the project health parameters ... with a key
        // notes / dash board should be available")
        sectionTitle(b, "9" + DOT + "Earned value" + DASH + "project health");
        if (m.evm == null) {
            zero(b, "No project budget configured" + DASH + "EVM unavailable.");
        } else {
            b.append("<table width='100%' cellpadding='0' cellspacing='6' style='border-collapse:separate'><tr>");
            kpi(b, compact(m.evm.bac()), "Budget at completion (BAC)", INK);
            kpi(b, compact(m.evm.ev()), "Earned value (work done)", GOLD_DARK);
            kpi(b, compact(m.evm.ac()), "Actual cost spent", INK);
            kpi(b, compact(m.evm.eac()), "Forecast final cost (EAC)", INK);
            b.append("</tr><tr>");
            kpi(b, m.evm.cpi() == null ? "n/a" : PCT.format(m.evm.cpi()) + (m.evm.cpi() >= 1 ? " &#10004;" : ""),
                    "CPI (cost efficiency, 1.0 = on budget)",
                    m.evm.cpi() == null ? MUTED : m.evm.cpi() >= 1 ? GOOD : BAD);
            kpi(b, m.evm.spi() == null ? "n/a" : PCT.format(m.evm.spi()) + (m.evm.spi() >= 1 ? " &#10004;" : ""),
                    "SPI (schedule pace, 1.0 = on plan)",
                    m.evm.spi() == null ? MUTED : m.evm.spi() >= 1 ? GOOD : BAD);
            kpi(b, compact(Math.abs(m.evm.vac())) + (m.evm.vac() >= 0 ? " under" : " over"),
                    "Forecast vs budget (VAC)", m.evm.vac() >= 0 ? GOOD : BAD);
            kpi(b, PCT.format(m.evm.pctComplete()) + "%", "Project complete (EV/BAC)", GOLD_DARK);
            b.append("</tr><tr>");
            kpi(b, compact(m.evm.pv()), "Planned value (work scheduled)", INK);
            kpi(b, compact(Math.abs(m.evm.sv())) + (m.evm.sv() >= 0 ? " ahead" : " behind"),
                    "Schedule variance (SV = EV &#8722; PV)", m.evm.sv() >= 0 ? GOOD : BAD);
            kpi(b, compact(Math.abs(m.evm.cv())) + (m.evm.cv() >= 0 ? " under" : " over"),
                    "Cost variance (CV = EV &#8722; AC)", m.evm.cv() >= 0 ? GOOD : BAD);
            kpi(b, compact(m.evm.etc()), "Cost still to spend (ETC)", INK);
            b.append("</tr></table>");
            evmKeyNotes(b, m.evm, cur);
            if (m.evmDashboardUrl != null && !m.evmDashboardUrl.isBlank()) {
                // esc() covers element text only; inside a single-quoted attribute an apostrophe
                // in the configured base URL would end the attribute and break the strict XML.
                b.append("<div style='font-size:12px;margin:8px 0 0'><a href='")
                 .append(esc(m.evmDashboardUrl).replace("'", "&#39;"))
                 .append("' style='color:").append(GOLD_DARK)
                 .append(";font-weight:bold'>Open the live EVM dashboard (S-curve, WBS drill-down, forecast methods) &#8594;</a></div>");
            }
            b.append(note("All figures in " + esc(cur) + " (M = million)" + DOT
                     + "canonical cost engine, identical to the Costs and EVM tabs" + DOT
                     + "forecast (EAC/ETC/VAC) uses the CPI-based method, the EVM tab's default view."));
        }

        // AI narrative
        if (r.findings() != null && !r.findings().isEmpty()) {
            sectionTitle(b, "Analysis" + DASH + "what the numbers mean");
            for (var f : r.findings()) {
                b.append("<div style='border-left:3px solid ").append(GOLD_DARK)
                 .append(";padding:6px 12px;margin:0 0 8px;background:").append(ZEBRA).append(";font-size:12px'>")
                 .append("<b>").append(esc(f.label())).append(":</b> ").append(esc(f.detail())).append("</div>");
            }
        }
        if (r.recommendations() != null && !r.recommendations().isEmpty()) {
            sectionTitle(b, "Recommended actions");
            for (var rec : r.recommendations()) {
                b.append("<div style='padding:4px 0;font-size:12px'><b style='color:").append(GOLD_DARK).append("'>[")
                 .append(esc(rec.priority())).append("]</b> <b>").append(esc(rec.title())).append(":</b> ")
                 .append(esc(rec.action())).append("</div>");
            }
        }

        // AI board flags
        if (agentFlags != null && !agentFlags.isEmpty()) {
            sectionTitle(b, "What the AI agents currently flag");
            for (var f : agentFlags) {
                boolean critical = f.getSeverity() != null && "CRITICAL".equals(f.getSeverity().name());
                b.append("<div style='border-left:3px solid ").append(critical ? BAD : WARN)
                 .append(";padding:6px 12px;margin:0 0 6px;background:").append(critical ? "#FBF4F3" : "#FBF7F0")
                 .append(";font-size:12px'><b style='color:").append(critical ? BAD : WARN).append("'>[")
                 .append(f.getSeverity() == null ? "" : f.getSeverity().name()).append("]</b> <b>")
                 .append(esc(f.getTitle())).append("</b> <span style='color:").append(MUTED).append("'>(")
                 .append(esc(f.getAgentKey())).append(")</span></div>");
            }
        }

        // Footer
        b.append("<div style='margin-top:18px;padding-top:10px;border-top:1px solid ").append(LINE)
         .append(";font-size:11px;color:").append(MUTED)
         .append("'>&#10004; Every figure sourced from the canonical engines (DPR / Capacity / DBS / BOQ / Cost) and cross-checked before sending")
         .append(DOT).append("Sarooj EPPM</div>");

        b.append("</td></tr></table>");
        return b.toString();
    }

    // -------------------------------------------------------------- helpers --

    /** Resource-wise capacity sub-table (one per side) - the tab's day + window buckets. */
    private static void capacityTable(StringBuilder b, String title,
                               List<DprReportMetrics.CapacityLine> lines,
                               DprReportMetrics.CapacityLine total, String cur) {
        if (lines == null || lines.isEmpty()) return;
        b.append("<div style='font-size:11px;font-weight:bold;color:").append(INK)
         .append(";margin:10px 0 2px'>").append(title).append("</div>")
         .append(tableOpen())
         .append(th("Role", "left"))
         .append(th("For the day", "right"))
         .append(th("Qty (window)", "right"))
         .append(th("Budget days", "right"))
         .append(th("Counted days", "right"))
         .append(th("Eff %", "right"))
         .append(th("Cost impact " + esc(cur), "right")).append("</tr>");
        int i = 0;
        for (var l : lines) {
            b.append(trOpen(i++)).append(capacityCells(l));
        }
        if (total != null) {
            b.append("<tr style='background:").append(ZEBRA).append(";font-weight:bold'>")
             .append(capacityCells(total));
        }
        b.append("</table>")
         .append(note("For the day = budget &#247; counted days (eff %) on the window's anchor day"
                 + DOT + "window figures = the report window's cumulative bucket, identical to the Capacity Util. tab."));
    }

    private static String capacityCells(DprReportMetrics.CapacityLine l) {
        String day = (l.dayBudget() == null && l.dayCounted() == null) ? "&#8212;"
                : QTY.format(l.dayBudget() == null ? 0 : l.dayBudget())
                    + " &#247; " + QTY.format(l.dayCounted() == null ? 0 : l.dayCounted())
                    + (l.dayEff() == null ? "" : " (" + PCT.format(l.dayEff()) + "%)");
        String eff = l.eff() == null ? "&#8212;" : PCT.format(l.eff()) + "%";
        String effColor = l.eff() == null ? MUTED : l.eff() >= 100 ? GOOD : l.eff() >= 90 ? WARN : BAD;
        String cost;
        if (l.cost() == null || l.cost() == 0) {
            cost = "&#8212;";
        } else {
            boolean over = l.cost() > 0;
            cost = "<span style='color:" + (over ? BAD : GOOD) + "'>"
                    + (over ? "overrun " : "saved ") + MONEY.format(Math.abs(l.cost())) + "</span>";
        }
        return td(esc(l.role()), "left")
                + td(day, "right")
                + td(l.qty() == null ? "&#8212;" : QTY.format(l.qty()), "right")
                + td(l.budgetDays() == null ? "&#8212;" : QTY.format(l.budgetDays()), "right")
                + td(l.countedDays() == null ? "&#8212;" : QTY.format(l.countedDays()), "right")
                + "<td style='padding:5px 8px;text-align:right;font-size:12px;font-weight:bold;color:" + effColor + "'>" + eff + "</td>"
                + td(cost, "right")
                + "</tr>";
    }

    /**
     * Plain-language "key notes" on the health parameters (EVM-agent-row 2026-08-11). Pure
     * restatement of the engine's own figures - the thresholds here are presentation wording,
     * never new arithmetic. Direction words ("under"/"over", "ahead"/"behind") branch on the sign
     * of the EXACT money gaps (CV/SV), never on the 4-dp-rounded CPI/SPI, so the notes can never
     * contradict the KPI cells above them at the 1.0000 rounding boundary. A null CPI/SPI is the
     * engine's "no data" (no actual cost / no planned value yet); a genuine 0.0 is a real score
     * (money spent or time passed with nothing earned) and reads as over budget / behind schedule.
     */
    private static void evmKeyNotes(StringBuilder b, DprReportMetrics.EvmBlock e, String cur) {
        b.append("<div style='font-size:11px;font-weight:bold;letter-spacing:.5px;text-transform:uppercase;color:")
         .append(INK).append(";margin:10px 0 4px'>Key notes</div>");

        // Cost health - CPI is "budgeted work earned per 1 spent"; CV is the exact money gap.
        if (e.cpi() == null) {
            keyNote(b, MUTED, "Cost", "not yet measurable" + DASH + "no actual cost on record yet.");
        } else if (e.cv() >= 0) {
            keyNote(b, GOOD, "Cost", "every 1 " + esc(cur) + " spent has earned "
                + COMPACT.format(e.cpi()) + " " + esc(cur) + " of budgeted work (CPI)" + DOT
                + "work done is worth <b>" + compact(Math.abs(e.cv()))
                + " more</b> than it cost (CV)" + DOT + "under budget so far.");
        } else {
            keyNote(b, BAD, "Cost", "every 1 " + esc(cur) + " spent is earning only "
                + COMPACT.format(e.cpi()) + " " + esc(cur) + " of budgeted work (CPI)" + DOT
                + "spending has run <b>" + compact(Math.abs(e.cv()))
                + " ahead</b> of the value of work done (CV)" + DOT + "over budget.");
        }

        // Schedule health - SPI is pace vs plan; SV is the exact value of work not yet earned.
        if (e.spi() == null) {
            keyNote(b, MUTED, "Schedule", "not yet measurable" + DASH
                + "no planned value on record for this date.");
        } else if (e.sv() >= 0) {
            keyNote(b, GOOD, "Schedule", "work is being earned at " + Math.round(e.spi() * 100)
                + "% of the planned pace (SPI " + COMPACT.format(e.spi()) + ")" + DOT
                + "<b>" + compact(Math.abs(e.sv())) + "</b> ahead of plan (SV)" + DOT + "on or ahead of schedule.");
        } else {
            keyNote(b, BAD, "Schedule", "work is being earned at " + Math.round(e.spi() * 100)
                + "% of the planned pace (SPI " + COMPACT.format(e.spi()) + ")" + DOT
                + "<b>" + compact(Math.abs(e.sv())) + "</b> of planned work is not yet done (SV)"
                + DOT + "behind schedule.");
        }

        // Forecast - EAC vs BAC (CPI-based, the tab's default). Without a positive CPI the engine
        // falls back to EAC = BAC, which is a placeholder, not a projection - say so instead of
        // printing a green "0 under budget" beside a red CV.
        if (e.cpi() == null || e.cpi() <= 0) {
            keyNote(b, MUTED, "Forecast", "no cost forecast yet" + DASH
                + "it needs both earned value and actual cost on record.");
        } else {
            keyNote(b, e.vac() >= 0 ? GOOD : BAD, "Forecast",
                "at the current cost efficiency the project finishes at <b>" + compact(e.eac())
                + "</b> against a " + compact(e.bac()) + " budget" + DASH + "<b>"
                + compact(Math.abs(e.vac())) + (e.vac() >= 0 ? " under" : " over")
                + " budget</b> (VAC), with " + compact(e.etc()) + " still to spend (ETC).");
        }

        // TCPI - how hard the remaining work has to perform to land exactly on budget. When the
        // actual cost has already reached the full budget, TCPI goes negative (or the engine nulls
        // it at BAC = AC) and no efficiency can save the target - never call that "comfortable".
        if (e.bac() > 0 && e.ac() >= e.bac()) {
            keyNote(b, BAD, "To finish on budget", "no longer achievable" + DASH
                + "the actual cost has already reached the full budget.");
        } else if (e.tcpi() != null) {
            keyNote(b, e.tcpi() <= 1 ? GOOD : WARN, "To finish on budget",
                "the remaining work needs a cost efficiency of " + COMPACT.format(e.tcpi())
                + " (TCPI)" + DOT + (e.tcpi() <= 1
                    ? "at or below 1.0" + DASH + "the target is comfortable at today's performance."
                    : "above 1.0" + DASH + "crews must beat their past cost performance to land on budget."));
        }
    }

    /** One key-note line - same left-border card style as the findings block. */
    private static void keyNote(StringBuilder b, String color, String label, String text) {
        b.append("<div style='border-left:3px solid ").append(color)
         .append(";padding:5px 12px;margin:0 0 6px;background:").append(ZEBRA)
         .append(";font-size:12px'><b style='color:").append(color).append("'>")
         .append(label).append(":</b> ").append(text).append("</div>");
    }

    /** Explicit money wording: red "overrun X CUR" or green "saved X CUR"; empty when zero. */
    /** Right-aligned money cell for a cost variance: red positive (overspend), green negative. */
    private static String varianceCell(Double v) {
        if (v == null) return td("&#8212;", "right");
        String color = v > 0 ? BAD : GOOD;
        String sign = v > 0 ? "+" : "";
        return "<td style='padding:5px 8px;text-align:right;font-size:12px;font-weight:bold;color:"
                + color + "'>" + sign + MONEY.format(v) + "</td>";
    }

    private static String alertLabel(String code) {
        if (code == null) return "&#8212;";
        return switch (code) {
            case "BELOW_MIN_STOCK" -> "<span style='color:#b91c1c;font-weight:600'>Below min stock</span>";
            case "LOW_COVER" -> "<span style='color:#b45309;font-weight:600'>Low cover</span>";
            case "NEGATIVE_BALANCE" -> "<span style='color:#b91c1c;font-weight:600'>Negative balance</span>";
            default -> esc(code);
        };
    }

    private static String moneyImpact(double implication, String cur) {
        if (implication == 0) return "";
        boolean over = implication > 0;
        return DOT + "<span style='color:" + (over ? BAD : GOOD) + ";font-weight:bold'>"
                + (over ? "overrun " : "saved ") + MONEY.format(Math.abs(implication)) + " " + esc(cur) + "</span>";
    }

    /** Compact money for large EVM figures: 102,000,000 -> "102.00M". */
    private static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return COMPACT.format(v / 1_000_000_000) + "B";
        if (a >= 1_000_000) return COMPACT.format(v / 1_000_000) + "M";
        if (a >= 10_000) return COMPACT.format(v / 1_000) + "K";
        return MONEY.format(v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "&#8230;";
    }

    private static void sectionTitle(StringBuilder b, String title) {
        b.append("<div style='margin:20px 0 8px;padding-bottom:4px;border-bottom:2px solid ").append(GOLD)
         .append(";font-size:12px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:")
         .append(GOLD_DARK).append("'>").append(title).append("</div>");
    }

    private static void kpi(StringBuilder b, String value, String label, String color) {
        b.append("<td width='25%' style='border:1px solid ").append(LINE).append(";padding:8px 10px'>")
         .append("<div style='font-size:16px;font-weight:bold;color:").append(color).append("'>").append(value).append("</div>")
         .append("<div style='font-size:9px;letter-spacing:.5px;text-transform:uppercase;color:").append(MUTED).append("'>")
         .append(label).append("</div></td>");
    }

    /** One horizontal chart bar: label | track+fill | value. Pure CSS, PDF- and email-safe. */
    private static void bar(StringBuilder b, String label, double pct, String color, String value) {
        double w = Math.max(1, Math.min(pct, 100));
        b.append("<tr><td style='padding:3px 8px 3px 0;font-size:11px;white-space:nowrap' width='120'>").append(label).append("</td>")
         .append("<td style='padding:3px 0'><table width='100%' cellpadding='0' cellspacing='0'><tr>")
         .append("<td width='").append(Math.round(w)).append("%' style='background:").append(color).append(";font-size:4px;line-height:9px'>&#160;</td>")
         .append("<td style='background:#EEECE6;font-size:4px;line-height:9px'>&#160;</td>")
         .append("</tr></table></td>")
         .append("<td style='padding:3px 0 3px 8px;font-size:11px;white-space:nowrap' width='230'>").append(value).append("</td></tr>");
    }

    /** Opens a table AND its header row - every use must append th(...) cells then "</tr>". */
    private static String tableOpen() {
        return "<table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse;font-size:12px'><tr>";
    }

    /** Opens a bare table for bar() rows (each bar closes its own tr) - strict-XML safe. */
    private static String barTableOpen() {
        return "<table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse;font-size:12px'>";
    }

    private static String th(String label, String align) {
        return "<th style='text-align:" + align + ";padding:5px 8px;font-size:9px;letter-spacing:.5px;"
                + "text-transform:uppercase;color:" + MUTED + ";border-bottom:1px solid " + LINE + "'>" + label + "</th>";
    }

    private static String trOpen(int i) {
        return "<tr style='background:" + (i % 2 == 1 ? ZEBRA : "#FFFFFF") + "'>";
    }

    private static String td(String v, String align) {
        return "<td style='padding:5px 8px;text-align:" + align + ";font-size:12px'>" + v + "</td>";
    }

    private static void zero(StringBuilder b, String msg) {
        b.append("<div style='border:1px dashed ").append(LINE).append(";padding:10px 14px;color:")
         .append(MUTED).append(";font-size:12px'>").append(msg).append("</div>");
    }

    private static String note(String msg) {
        return "<div style='font-size:10px;color:" + MUTED + ";margin:4px 0 0'>" + msg + "</div>";
    }

    /** Minimal HTML escaping for values rendered into the template. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
