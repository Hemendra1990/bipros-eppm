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
         .append("<div style='font-size:11px;letter-spacing:2px;color:").append(GOLD).append(";text-transform:uppercase'>Bipros").append(DOT).append("Daily Project Report</div>")
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

        // 2 - Capacity utilization
        sectionTitle(b, "2" + DOT + "Capacity utilization" + DASH + "efficiency vs productivity norms");
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

        // 3 - DBS
        sectionTitle(b, "3" + DOT + "Daily balance sheet" + DASH + "income vs expense per day");
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

        // 4 - Issues
        sectionTitle(b, "4" + DOT + "Issues reported on site");
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

        // 5 - Material consumption
        sectionTitle(b, "5" + DOT + "Material consumption (from approved DPRs)");
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
            b.append("</table>")
             .append(note("Store availability (receipts / closing balance) is excluded until its source data is repaired."));
        }

        // 6 - Costing (informative table, not cryptic bars)
        sectionTitle(b, "6" + DOT + "Costing" + DASH + "largest BOQ cost variances");
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

        // 7 - EVM
        sectionTitle(b, "7" + DOT + "Earned value" + DASH + "project health");
        if (m.evm == null) {
            zero(b, "No project budget configured" + DASH + "EVM unavailable.");
        } else {
            b.append("<table width='100%' cellpadding='0' cellspacing='6' style='border-collapse:separate'><tr>");
            kpi(b, compact(m.evm.bac()), "Budget at completion (BAC)", INK);
            kpi(b, compact(m.evm.ev()), "Earned value (work done)", GOLD_DARK);
            kpi(b, compact(m.evm.ac()), "Actual cost spent", INK);
            kpi(b, compact(m.evm.eac()), "Forecast final cost (EAC)", INK);
            b.append("</tr><tr>");
            kpi(b, PCT.format(m.evm.cpi()) + (m.evm.cpi() >= 1 ? " &#10004;" : ""),
                    "CPI (cost efficiency, 1.0 = on budget)", m.evm.cpi() >= 1 ? GOOD : BAD);
            kpi(b, PCT.format(m.evm.spi()) + (m.evm.spi() >= 1 ? " &#10004;" : ""),
                    "SPI (schedule pace, 1.0 = on plan)", m.evm.spi() >= 1 ? GOOD : BAD);
            kpi(b, compact(Math.abs(m.evm.vac())) + (m.evm.vac() >= 0 ? " under" : " over"),
                    "Forecast vs budget (VAC)", m.evm.vac() >= 0 ? GOOD : BAD);
            kpi(b, PCT.format(m.evm.pctComplete()) + "%", "Project complete (EV/BAC)", GOLD_DARK);
            b.append("</tr></table>")
             .append(note("All figures in " + esc(cur) + " (M = million)" + DOT
                     + "canonical cost engine, identical to the Costs and EVM tabs."));
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
         .append(DOT).append("Bipros EPPM</div>");

        b.append("</td></tr></table>");
        return b.toString();
    }

    // -------------------------------------------------------------- helpers --

    /** Explicit money wording: red "overrun X CUR" or green "saved X CUR"; empty when zero. */
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
