package com.bipros.reporting.infrastructure.export;

import com.bipros.cost.application.dto.MarginActivityDto;
import com.bipros.cost.application.dto.MarginItemDto;
import com.bipros.cost.application.dto.MarginPeriodDto;
import com.bipros.cost.application.dto.MarginSummaryDto;
import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Performance (P&L) downloads — Access-Output sheet row 5. Numbers come verbatim from the
 * SAME services the on-screen tabs read ({@code PerformanceRollupService},
 * {@code BudgetedMarginService}/{@code BoqMarginService}); the only arithmetic added here is
 * the Performance totals row, which mirrors the page's KPI cards (column sums, CPI = ΣEV/ΣAC,
 * SPI = ΣEV/ΣPV — ratio of sums, zero-guarded).
 */
@Component
@Slf4j
public class PnlPerformanceExcelWriter {

    /** Performance (D/W/M) — one sheet mirroring the PV/EV/AC-by-period table + KPI totals. */
    public byte[] generatePerformance(List<PeriodPerformanceRollupDto> rows,
                                      String projectName, String periodLabel) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);

            XSSFSheet sh = wb.createSheet("Performance");
            writeTitle(sh, "Performance (" + periodLabel + ") — " + safe(projectName)
                    + " — amounts in the project's currency", s);
            headers(sh.createRow(2), s, "Period", "Start", "End", "Planned Value",
                    "Earned Value", "Actual Cost", "CV", "SV", "CPI", "SPI");
            int r = 3;
            BigDecimal pv = BigDecimal.ZERO, ev = BigDecimal.ZERO, ac = BigDecimal.ZERO;
            for (PeriodPerformanceRollupDto row : rows) {
                XSSFRow x = sh.createRow(r++);
                text(x, 0, row.periodName(), s.plain);
                text(x, 1, row.startDate() == null ? "" : row.startDate().toString(), s.plain);
                text(x, 2, row.endDate() == null ? "" : row.endDate().toString(), s.plain);
                num(x, 3, row.plannedValue(), s.num);
                num(x, 4, row.earnedValue(), s.num);
                num(x, 5, row.actualCost(), s.num);
                num(x, 6, row.cv(), s.num);
                num(x, 7, row.sv(), s.num);
                num(x, 8, row.cpi(), s.num);
                num(x, 9, row.spi(), s.num);
                pv = pv.add(nz(row.plannedValue()));
                ev = ev.add(nz(row.earnedValue()));
                ac = ac.add(nz(row.actualCost()));
            }
            XSSFRow tot = sh.createRow(r);
            text(tot, 0, "Total", s.header);
            text(tot, 1, "", s.header);
            text(tot, 2, "", s.header);
            num(tot, 3, pv, s.numBold);
            num(tot, 4, ev, s.numBold);
            num(tot, 5, ac, s.numBold);
            num(tot, 6, ev.subtract(ac), s.numBold);
            num(tot, 7, ev.subtract(pv), s.numBold);
            num(tot, 8, ratio(ev, ac), s.numBold);
            num(tot, 9, ratio(ev, pv), s.numBold);
            widths(sh, 6000, 3200, 3200, 4200, 4200, 4200, 4200, 4200, 2600, 2600);

            return bytes(wb);
        } catch (IOException e) {
            log.error("Failed to generate Performance Excel", e);
            throw new RuntimeException("Failed to generate Performance workbook", e);
        }
    }

    /** P&L vs Budgeted / BOQ rates — Summary, By period, BOQ items, Activities sheets. */
    public byte[] generatePnl(String reportTitle, MarginSummaryDto summary,
                              List<MarginPeriodDto> periods, List<MarginItemDto> items,
                              List<MarginActivityDto> activities,
                              String projectName, String periodLabel) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);

            XSSFSheet sum = wb.createSheet("Summary");
            writeTitle(sum, reportTitle + " — " + safe(projectName)
                    + " — amounts in the project's currency", s);
            headers(sum.createRow(2), s, "Revenue", "Actual Cost", "Margin", "Margin %");
            XSSFRow sr = sum.createRow(3);
            num(sr, 0, summary == null ? null : summary.revenue(), s.num);
            num(sr, 1, summary == null ? null : summary.actualCost(), s.num);
            num(sr, 2, summary == null ? null : summary.margin(), s.num);
            num(sr, 3, summary == null ? null : summary.marginPct(), s.num);
            widths(sum, 4200, 4200, 4200, 3200);

            XSSFSheet per = wb.createSheet("By period");
            writeTitle(per, "Revenue vs cost by period (" + periodLabel + ")", s);
            headers(per.createRow(2), s, "Period", "Start", "End", "Revenue",
                    "Actual Cost", "Margin", "Margin %");
            int r = 3;
            for (MarginPeriodDto row : periods) {
                XSSFRow x = per.createRow(r++);
                text(x, 0, row.periodName(), s.plain);
                text(x, 1, row.startDate() == null ? "" : row.startDate().toString(), s.plain);
                text(x, 2, row.endDate() == null ? "" : row.endDate().toString(), s.plain);
                num(x, 3, row.revenue(), s.num);
                num(x, 4, row.actualCost(), s.num);
                num(x, 5, row.margin(), s.num);
                num(x, 6, row.marginPct(), s.num);
            }
            widths(per, 6000, 3200, 3200, 4200, 4200, 4200, 3200);

            XSSFSheet itm = wb.createSheet("BOQ items");
            writeTitle(itm, "Margin by BOQ item", s);
            headers(itm.createRow(2), s, "Item No", "Description", "Unit", "Qty executed",
                    "Rate", "Revenue", "Actual Cost", "Margin", "Margin %");
            r = 3;
            for (MarginItemDto row : items) {
                XSSFRow x = itm.createRow(r++);
                text(x, 0, row.itemNo(), s.plain);
                text(x, 1, row.description(), s.plain);
                text(x, 2, row.unit(), s.plain);
                num(x, 3, row.qtyExecuted(), s.num);
                num(x, 4, row.rate(), s.num);
                num(x, 5, row.revenue(), s.num);
                num(x, 6, row.actualCost(), s.num);
                num(x, 7, row.margin(), s.num);
                num(x, 8, row.marginPct(), s.num);
            }
            widths(itm, 3200, 14000, 2400, 3600, 3600, 4200, 4200, 4200, 3200);

            XSSFSheet act = wb.createSheet("Activities");
            writeTitle(act, "Margin by activity", s);
            headers(act.createRow(2), s, "Activity", "Revenue", "Actual Cost",
                    "Margin", "Margin %");
            r = 3;
            for (MarginActivityDto row : activities) {
                XSSFRow x = act.createRow(r++);
                text(x, 0, row.activity(), s.plain);
                num(x, 1, row.revenue(), s.num);
                num(x, 2, row.actualCost(), s.num);
                num(x, 3, row.margin(), s.num);
                num(x, 4, row.marginPct(), s.num);
            }
            widths(act, 14000, 4200, 4200, 4200, 3200);

            return bytes(wb);
        } catch (IOException e) {
            log.error("Failed to generate P&L Excel", e);
            throw new RuntimeException("Failed to generate P&L workbook", e);
        }
    }

    private static byte[] bytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        return baos.toByteArray();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return numerator.divide(denominator, MathContext.DECIMAL64);
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static void writeTitle(XSSFSheet sh, String title, Styles s) {
        text(sh.createRow(0), 0, title, s.title);
    }

    private static void headers(XSSFRow row, Styles s, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            text(row, i, labels[i], s.header);
        }
    }

    private static void text(XSSFRow row, int col, String value, CellStyle style) {
        XSSFCell c = row.createCell(col, CellType.STRING);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void num(XSSFRow row, int col, BigDecimal value, CellStyle style) {
        XSSFCell c = row.createCell(col, value == null ? CellType.BLANK : CellType.NUMERIC);
        if (value != null) c.setCellValue(value.doubleValue());
        c.setCellStyle(style);
    }

    private static void widths(XSSFSheet sh, int... w) {
        for (int i = 0; i < w.length; i++) sh.setColumnWidth(i, w[i]);
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle plain;
        final CellStyle num;
        final CellStyle numBold;

        Styles(XSSFWorkbook wb) {
            DataFormat df = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            title = wb.createCellStyle();
            title.setFont(titleFont);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(header);

            plain = wb.createCellStyle();
            border(plain);

            num = wb.createCellStyle();
            num.setDataFormat(df.getFormat("#,##0.00"));
            border(num);

            numBold = wb.createCellStyle();
            numBold.setFont(bold);
            numBold.setDataFormat(df.getFormat("#,##0.00"));
            border(numBold);
        }

        private static void border(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
