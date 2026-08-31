package com.bipros.reporting.infrastructure.export;

import com.bipros.reporting.application.service.CommoditySummaryReportService.CommoditySummary;
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

/**
 * Executed Commodity Summary workbook — three sheets (BOQ level, Activity level, Per supervisor),
 * quantities only. Month column = the requested calendar month; till-date = all approved DPRs
 * (BOQ sheet uses the stored billing-basis columns).
 */
@Component
@Slf4j
public class CommoditySummaryExcelWriter {

    public byte[] generate(CommoditySummary data, String projectName) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);

            XSSFSheet boq = wb.createSheet("BOQ level");
            writeTitle(boq, "Executed Commodity Summary — " + projectName + " — " + data.month(), s);
            XSSFRow h1 = boq.createRow(2);
            headers(h1, s, "Item No", "Description", "Unit", "Contract qty",
                    "Executed " + data.month(), "Executed till date", "% complete");
            int r = 3;
            for (var line : data.boqLines()) {
                XSSFRow row = boq.createRow(r++);
                text(row, 0, line.itemNo(), s.plain);
                text(row, 1, line.description(), s.plain);
                text(row, 2, line.unit(), s.plain);
                num(row, 3, line.contractQty(), s.num);
                num(row, 4, line.monthQty(), s.num);
                num(row, 5, line.toDateQty(), s.num);
                num(row, 6, line.pctComplete(), s.num);
            }
            widths(boq, 3200, 16000, 2400, 3600, 3800, 3800, 3200);

            XSSFSheet act = wb.createSheet("Activity level");
            writeTitle(act, "Executed quantities per activity — " + data.month(), s);
            headers(act.createRow(2), s, "Activity", "Unit", "Executed " + data.month(), "Executed till date");
            r = 3;
            for (var line : data.activityLines()) {
                XSSFRow row = act.createRow(r++);
                text(row, 0, line.activityName(), s.plain);
                text(row, 1, line.unit(), s.plain);
                num(row, 2, line.monthQty(), s.num);
                num(row, 3, line.toDateQty(), s.num);
            }
            widths(act, 14000, 2400, 3800, 3800);

            XSSFSheet sup = wb.createSheet("Per supervisor");
            writeTitle(sup, "Supervisor performance — executed quantities — " + data.month(), s);
            headers(sup.createRow(2), s, "Supervisor", "DPRs " + data.month(), "Qty " + data.month(),
                    "DPRs till date", "Qty till date");
            r = 3;
            for (var line : data.supervisorLines()) {
                XSSFRow row = sup.createRow(r++);
                text(row, 0, line.supervisorName(), s.plain);
                longNum(row, 1, line.dprsMonth(), s.num);
                num(row, 2, line.monthQty(), s.num);
                longNum(row, 3, line.dprsToDate(), s.num);
                num(row, 4, line.toDateQty(), s.num);
            }
            widths(sup, 8000, 3200, 3800, 3200, 3800);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Commodity Summary Excel", e);
            throw new RuntimeException("Failed to generate Commodity Summary workbook", e);
        }
    }

    private static void writeTitle(XSSFSheet sh, String title, Styles s) {
        XSSFRow row = sh.createRow(0);
        text(row, 0, title, s.title);
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

    private static void longNum(XSSFRow row, int col, long value, CellStyle style) {
        XSSFCell c = row.createCell(col, CellType.NUMERIC);
        c.setCellValue(value);
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
        }

        private static void border(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
