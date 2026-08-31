package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.InsightsResponse;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The PDF engine (openhtmltopdf) parses the rendered report as STRICT XML — one unbalanced tag
 * aborts the whole PDF at runtime (exactly what happened on 2026-08-05 with an unclosed
 * header-row {@code <tr>}). This test renders a report with EVERY section populated and parses
 * it with a strict XML parser, so a malformed template fails the build instead of the send.
 */
class DprReportHtmlRendererXmlTest {

    @Test
    void rendered_report_with_all_sections_is_well_formed_xml() {
        DprReportMetrics m = new DprReportMetrics();
        m.projectName = "Test Project & Co";   // exercises escaping too
        m.currencyCode = "OMR";
        m.totalDprs = 2;
        m.openIssues = 1;
        m.criticalIssues = 1;
        m.safetyIncidents = 0;
        m.roleEfficiencies.add(new DprReportMetrics.RoleEfficiency("Helper", 50.0, 285.0, "critical"));
        m.roleEfficiencies.add(new DprReportMetrics.RoleEfficiency("Crane", null, 0.0, "info"));
        m.dbsDays.add(new DprReportMetrics.DayMoney("2026-08-04", 2400, 12105.63, -9705.63, 2));
        m.dbsIncome = 2400;
        m.dbsExpense = 12105.63;
        m.dbsContribution = -9705.63;
        m.dbsSupervisors.add(new DprReportMetrics.SupMoney("K. Barman", 2400, 12105.63, -9705.63));
        m.boqTopVariances.add(new DprReportMetrics.BoqVar("TEST-1", "Pipeline <spec>", 60, 50, 167.25, 70, 100, 6708));
        m.boqTotalVariance = 6708;
        m.materials.add(new DprReportMetrics.MaterialUse("Cement & Sand", "Bag", 12, 340));
        m.evm = new DprReportMetrics.EvmBlock(102_000_000, 102_000_000, 24_290_000, 4_830_000,
                5.03, 0.24, 20_280_000, 81_720_000, 23.8,
                -77_710_000, 19_460_000, 15_450_000, 0.80);
        m.evmDashboardUrl = "http://localhost:3000/projects/e3d53714/evm";  // exercises the <a href>

        m.supervisorWork.add(new DprReportMetrics.SupWork("K. Barman", 2, 2, 90));
        m.issueCategories.add(new DprReportMetrics.CatCount("MATERIAL", 1));

        InsightsResponse r = new InsightsResponse(
                "Summary with <angle> & ampersand.",
                List.of(), List.of(), List.of(), List.of(),
                "2 numbers could not be verified", null, List.of());

        String html = new DprReportHtmlRenderer().render(r, m, "Last 1 day", List.of());

        assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(html))))
                .doesNotThrowAnyException();
    }

    /**
     * The EVM key notes branch six ways (cost/schedule/forecast x good/bad/not-measurable, TCPI
     * comfortable/warn/no-longer-achievable/absent). The main test walks one combination; this one
     * walks the rest so every branch's markup goes through the strict XML parse.
     */
    @Test
    void evm_key_note_branches_are_well_formed_xml() {
        DprReportMetrics.EvmBlock[] cases = {
            // over budget + ahead of schedule + AC past BAC -> "no longer achievable", no link
            new DprReportMetrics.EvmBlock(102_000_000, 55_000_000, 61_000_000, 110_000_000,
                0.55, 1.08, 185_450_000, -83_450_000, 59.8,
                6_000_000, -49_000_000, 75_450_000, -5.13),
            // nothing on record: null CPI/SPI/TCPI -> three "not yet measurable" notes, no link
            new DprReportMetrics.EvmBlock(102_000_000, 0, 0, 0,
                null, null, 102_000_000, 0, 0,
                0, 0, 102_000_000, null),
            // over budget + behind schedule + TCPI above 1 -> WARN branch, link present
            new DprReportMetrics.EvmBlock(102_000_000, 70_000_000, 63_000_000, 78_750_000,
                0.80, 0.90, 127_500_000, -25_500_000, 61.8,
                -7_000_000, -15_750_000, 48_750_000, 1.68),
        };
        InsightsResponse r = new InsightsResponse("s", List.of(), List.of(), List.of(), List.of(),
                null, null, List.of());
        for (int i = 0; i < cases.length; i++) {
            DprReportMetrics m = new DprReportMetrics();
            m.projectName = "P";
            m.currencyCode = "OMR";
            m.evm = cases[i];
            if (i == 2) m.evmDashboardUrl = "http://localhost:3000/projects/x/evm";
            String html = new DprReportHtmlRenderer().render(r, m, "Last 1 day", List.of());
            assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(html))))
                    .doesNotThrowAnyException();
        }
    }
}
