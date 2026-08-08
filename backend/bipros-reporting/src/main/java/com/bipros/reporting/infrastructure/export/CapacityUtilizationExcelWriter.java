package com.bipros.reporting.infrastructure.export;

import com.bipros.reporting.application.dto.CapacityUtilizationClientWorkbook;
import com.bipros.reporting.application.dto.CapacityUtilizationClientWorkbook.ActivityLine;
import com.bipros.reporting.application.dto.CapacityUtilizationClientWorkbook.RoleGroup;
import com.bipros.reporting.application.dto.CapacityUtilizationClientWorkbook.Rollup;
import com.bipros.reporting.application.dto.CapacityUtilizationClientWorkbook.Section;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Writes the 3-sheet Capacity Utilisation workbook (Plant utilisation, Manpower utilisation,
 * SUMMARY) to an .xlsx byte array. Layout mirrors the client's "Resource Capacity Utilization
 * Report" template used by the construction-industry site teams: merged group headers, yellow
 * Work-days highlight, one banner row per resource with its per-activity task rows beneath
 * (full norm catalogue — idle tasks render with blank actuals).
 */
@Component
@Slf4j
public class CapacityUtilizationExcelWriter {

  public byte[] generate(
      CapacityUtilizationClientWorkbook data,
      YearMonth month,
      String projectName) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Styles s = new Styles(wb);
      writeCapacitySheet(wb, "Plant utilization", data.equipment(), false, data, month, projectName, s);
      writeCapacitySheet(wb, "Manpower utilization", data.manpower(), true, data, month, projectName, s);
      writeSummarySheet(wb, data, month, s);
      return toByteArray(wb);
    } catch (IOException e) {
      log.error("Failed to generate Capacity Utilization Excel for month {}", month, e);
      throw new RuntimeException("Failed to generate Capacity Utilization workbook", e);
    }
  }

  // ── Sheets 1 & 2: Plant utilization / Manpower utilization ─────────────────────────────────
  //
  // Client-template layout (0-based columns):
  //   0=S.No, 1=Description, 2=Budget Unit, 3=Prod'vity Norm, 4=Unit,
  //   5-8  = Actual (For the day): Work done Qty / Budgeted Days / Actual Days / % of Utiliz'tn
  //   9-12 = Cumulative (For the month): Cum. Work done / Budgeted Days / Actual Days / % of Utiliz'tn
  //   Manpower extra: 13 = Actual Prod'vity · 14 = Remarks (blank, hand-annotated)
  //   Plant extra:    13 = Site conditions (Actual) Prod'vity Norm
  private void writeCapacitySheet(
      XSSFWorkbook wb, String sheetName, Section section, boolean manpower,
      CapacityUtilizationClientWorkbook data, YearMonth month, String projectName, Styles s) {
    XSSFSheet sh = wb.createSheet(sheetName);

    // Top metadata band: Work days · Date · Project Name.
    XSSFRow r1 = sh.createRow(0);
    setText(r1, 5, "Work days", s.bold);
    setNumber(r1, 6, data.workDays(), s.workDaysHighlight);
    setText(r1, 10, "Date :", s.bold);
    setText(r1, 11, data.referenceDate() == null ? month.toString() : data.referenceDate().toString(), s.plain);

    XSSFRow r2 = sh.createRow(1);
    setText(r2, 0, "Resource Capacity Utilization Report — " + month, s.title);
    setText(r2, 10, "Project Name :", s.bold);
    setText(r2, 11, projectName == null ? "" : projectName, s.plain);

    XSSFRow r3 = sh.createRow(2);
    XSSFRow r4 = sh.createRow(3);
    setText(r3, 0, "S.No.", s.headerCenter);
    setText(r3, 1, "Description", s.headerCenter);
    setText(r3, 2, "Budget", s.headerCenter);
    setText(r3, 4, "Unit", s.headerCenter);
    setText(r3, 5, "Actual (For the day)", s.headerCenter);
    setText(r3, 9, "Cumulative (For the month)", s.headerCenter);
    sh.addMergedRegion(new CellRangeAddress(2, 2, 2, 3));   // "Budget" spans Unit + Norm columns
    sh.addMergedRegion(new CellRangeAddress(2, 2, 5, 8));   // "Actual (For the day)"
    sh.addMergedRegion(new CellRangeAddress(2, 2, 9, 12));  // "Cumulative (For the month)"
    sh.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));   // S.No. vertical
    sh.addMergedRegion(new CellRangeAddress(2, 3, 1, 1));   // Description vertical
    sh.addMergedRegion(new CellRangeAddress(2, 3, 4, 4));   // Unit vertical

    setText(r4, 2, "Unit", s.headerCenter);
    setText(r4, 3, "Prod'vity Norm", s.headerCenter);
    setText(r4, 5, "Work done Qty", s.headerCenter);
    setText(r4, 6, "Budgeted Days", s.headerCenter);
    setText(r4, 7, "Actual Days", s.headerCenter);
    setText(r4, 8, "% of Utiliz'tn", s.headerCenter);
    setText(r4, 9, "Cum. Work done", s.headerCenter);
    setText(r4, 10, "Budgeted Days", s.headerCenter);
    setText(r4, 11, "Actual Days", s.headerCenter);
    setText(r4, 12, "% of Utiliz'tn", s.headerCenter);
    if (manpower) {
      setText(r3, 13, "Actual Prod'vity", s.headerCenter);
      setText(r3, 14, "Remarks", s.headerCenter);
      sh.addMergedRegion(new CellRangeAddress(2, 3, 13, 13));
      sh.addMergedRegion(new CellRangeAddress(2, 3, 14, 14));
    } else {
      setText(r3, 13, "Site conditions (Actual) Prod'vity Norm", s.headerCenter);
      sh.addMergedRegion(new CellRangeAddress(2, 3, 13, 13));
    }

    int rowNum = 5;
    int sNo = 1;
    for (RoleGroup group : section.groups()) {
      // Resource banner row with running S.No. and the role's day / month rollups.
      XSSFRow groupRow = sh.createRow(rowNum++);
      setNumber(groupRow, 0, sNo++, s.groupBold);
      setText(groupRow, 1, group.roleName() == null ? "(role)" : group.roleName(), s.groupBold);
      for (int c = 2; c <= 4; c++) {
        groupRow.createCell(c).setCellStyle(s.groupBold);
      }
      Rollup day = group.day();
      Rollup mon = group.month();
      setBigDecimal(groupRow, 6, day.budgetDays(), s.numCell);
      setBigDecimal(groupRow, 7, day.actualDays(), s.numCell);
      setBigDecimal(groupRow, 8, toFraction(day.utilizationPct()), s.pctCell);
      setBigDecimal(groupRow, 10, mon.budgetDays(), s.numCell);
      setBigDecimal(groupRow, 11, mon.actualDays(), s.numCell);
      setBigDecimal(groupRow, 12, toFraction(mon.utilizationPct()), s.pctCell);

      for (ActivityLine line : group.lines()) {
        XSSFRow body = sh.createRow(rowNum++);
        setText(body, 1, line.activityName() == null ? "" : line.activityName(), s.plain);
        String unit = line.unit();
        // Budget Unit renders "Cum/day" style only when the task has a norm — no-norm rows
        // keep the bare unit column, like the client template's untracked tasks.
        setText(body, 2, line.normOutputPerDay() != null && unit != null ? unit + "/day" : "", s.plain);
        setBigDecimal(body, 3, line.normOutputPerDay(), s.numCell);
        setText(body, 4, unit == null ? "" : unit, s.plain);
        setBigDecimal(body, 5, line.dayQty(), s.numCell);
        setBigDecimal(body, 6, line.dayBudgetDays(), s.numCell);
        setBigDecimal(body, 7, line.dayActualDays(), s.numCell);
        setBigDecimal(body, 8, toFraction(line.dayUtilizationPct()), s.pctCell);
        setBigDecimal(body, 9, line.monthQty(), s.numCell);
        setBigDecimal(body, 10, line.monthBudgetDays(), s.numCell);
        setBigDecimal(body, 11, line.monthActualDays(), s.numCell);
        setBigDecimal(body, 12, toFraction(line.monthUtilizationPct()), s.pctCell);
        setBigDecimal(body, 13, line.actualProductivityMonth(), s.numCell);
        if (manpower) setText(body, 14, "", s.plain);
      }
    }

    setColumnWidths(sh, new int[] {2200, 12000, 3200, 3200, 2400,
        3200, 3200, 3200, 2600, 3600, 3200, 3200, 2600, 3400, 4800});
  }

  // ── Sheet 3: SUMMARY ───────────────────────────────────────────────────────────────────────
  private void writeSummarySheet(
      XSSFWorkbook wb, CapacityUtilizationClientWorkbook data, YearMonth month, Styles s) {
    XSSFSheet sh = wb.createSheet("SUMMARY");

    XSSFRow r1 = sh.createRow(0);
    setText(r1, 1, "Work days", s.bold);
    setNumber(r1, 2, data.workDays(), s.workDaysHighlight);
    XSSFRow r2 = sh.createRow(1);
    setText(r2, 1, "Date :", s.bold);
    setText(r2, 2, data.referenceDate() == null ? month.toString() : data.referenceDate().toString(), s.plain);
    setText(r2, 6, month.getMonth() + "-" + month.getYear(), s.titleUnderline);

    XSSFRow head1 = sh.createRow(3);
    setText(head1, 2, "For the Day", s.headerCenter);
    setText(head1, 5, "For the Month", s.headerCenter);
    sh.addMergedRegion(new CellRangeAddress(3, 3, 2, 4));
    sh.addMergedRegion(new CellRangeAddress(3, 3, 5, 7));

    XSSFRow head2 = sh.createRow(4);
    setText(head2, 1, "Equipment", s.headerCenter);
    setText(head2, 2, "Budg. Days", s.headerCenter);
    setText(head2, 3, "Actual Days", s.headerCenter);
    setText(head2, 4, "% of Util", s.headerCenter);
    setText(head2, 5, "Budg. Days", s.headerCenter);
    setText(head2, 6, "Actual Days", s.headerCenter);
    setText(head2, 7, "% of Util", s.headerCenter);

    int rowNum = 5;
    rowNum = writeSummaryBlock(sh, data.equipment(), rowNum, s);

    XSSFRow gap = sh.createRow(rowNum++);
    setText(gap, 1, "Manpower", s.groupBold);

    writeSummaryBlock(sh, data.manpower(), rowNum, s);
    setColumnWidths(sh, new int[] {800, 4000, 2800, 2800, 2400, 2800, 2800, 2400});
  }

  /** One line per resource + an AVERAGE row (simple mean of the non-null % columns, matching
   *  the client template's {@code =AVERAGE(...)} cells). */
  private int writeSummaryBlock(XSSFSheet sh, Section section, int startRow, Styles s) {
    int rowNum = startRow;
    List<RoleGroup> groups = section.groups();
    BigDecimal daySum = BigDecimal.ZERO, monthSum = BigDecimal.ZERO;
    int dayN = 0, monthN = 0;
    for (RoleGroup g : groups) {
      XSSFRow row = sh.createRow(rowNum++);
      setText(row, 1, g.roleName() == null ? "(role)" : g.roleName(), s.plain);
      setBigDecimal(row, 2, g.day().budgetDays(), s.numCell);
      setBigDecimal(row, 3, g.day().actualDays(), s.numCell);
      setBigDecimal(row, 4, toFraction(g.day().utilizationPct()), s.pctCell);
      setBigDecimal(row, 5, g.month().budgetDays(), s.numCell);
      setBigDecimal(row, 6, g.month().actualDays(), s.numCell);
      setBigDecimal(row, 7, toFraction(g.month().utilizationPct()), s.pctCell);
      if (g.day().utilizationPct() != null) { daySum = daySum.add(g.day().utilizationPct()); dayN++; }
      if (g.month().utilizationPct() != null) { monthSum = monthSum.add(g.month().utilizationPct()); monthN++; }
    }
    XSSFRow avg = sh.createRow(rowNum++);
    setText(avg, 1, "AVERAGE", s.groupBold);
    setBigDecimal(avg, 4, dayN == 0 ? null
        : toFraction(daySum.divide(BigDecimal.valueOf(dayN), 4, RoundingMode.HALF_UP)), s.pctCell);
    setBigDecimal(avg, 7, monthN == 0 ? null
        : toFraction(monthSum.divide(BigDecimal.valueOf(monthN), 4, RoundingMode.HALF_UP)), s.pctCell);
    return rowNum;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────────────────────

  /** util% as displayed comes back as 0..999 (percent). Convert to 0..1 for Excel's % format. */
  private static BigDecimal toFraction(BigDecimal pct) {
    if (pct == null) return null;
    return pct.movePointLeft(2);
  }

  private static void setText(XSSFRow row, int col, String value, CellStyle style) {
    XSSFCell c = row.createCell(col, CellType.STRING);
    c.setCellValue(value == null ? "" : value);
    if (style != null) c.setCellStyle(style);
  }

  private static void setNumber(XSSFRow row, int col, double value, CellStyle style) {
    XSSFCell c = row.createCell(col, CellType.NUMERIC);
    c.setCellValue(value);
    if (style != null) c.setCellStyle(style);
  }

  private static void setBigDecimal(XSSFRow row, int col, BigDecimal value, CellStyle style) {
    XSSFCell c = row.createCell(col, value == null ? CellType.BLANK : CellType.NUMERIC);
    if (value != null) c.setCellValue(value.doubleValue());
    if (style != null) c.setCellStyle(style);
  }

  private static void setColumnWidths(XSSFSheet sh, int[] widths) {
    for (int i = 0; i < widths.length; i++) sh.setColumnWidth(i, widths[i]);
  }

  private static byte[] toByteArray(XSSFWorkbook wb) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    wb.write(baos);
    return baos.toByteArray();
  }

  /** Per-workbook style cache so we don't blow Excel's 64k-style limit on large books. */
  private static final class Styles {
    final CellStyle plain;
    final CellStyle bold;
    final CellStyle title;
    final CellStyle titleUnderline;
    final CellStyle headerCenter;
    final CellStyle groupBold;
    final CellStyle numCell;
    final CellStyle pctCell;
    final CellStyle workDaysHighlight;

    Styles(XSSFWorkbook wb) {
      DataFormat df = wb.createDataFormat();

      Font fontPlain = wb.createFont();
      Font fontBold = wb.createFont();
      fontBold.setBold(true);
      Font fontTitle = wb.createFont();
      fontTitle.setBold(true);
      fontTitle.setFontHeightInPoints((short) 12);
      Font fontTitleU = wb.createFont();
      fontTitleU.setBold(true);
      fontTitleU.setUnderline(Font.U_SINGLE);

      plain = wb.createCellStyle();
      plain.setFont(fontPlain);
      borderAll(plain);

      bold = wb.createCellStyle();
      bold.setFont(fontBold);

      title = wb.createCellStyle();
      title.setFont(fontTitle);

      titleUnderline = wb.createCellStyle();
      titleUnderline.setFont(fontTitleU);

      headerCenter = wb.createCellStyle();
      headerCenter.setFont(fontBold);
      headerCenter.setAlignment(HorizontalAlignment.CENTER);
      headerCenter.setVerticalAlignment(VerticalAlignment.CENTER);
      headerCenter.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerCenter.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      borderAll(headerCenter);
      headerCenter.setWrapText(true);

      groupBold = wb.createCellStyle();
      groupBold.setFont(fontBold);
      groupBold.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
      groupBold.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      borderAll(groupBold);

      numCell = wb.createCellStyle();
      numCell.setFont(fontPlain);
      numCell.setDataFormat(df.getFormat("#,##0.00"));
      numCell.setAlignment(HorizontalAlignment.RIGHT);
      borderAll(numCell);

      pctCell = wb.createCellStyle();
      pctCell.setFont(fontPlain);
      pctCell.setDataFormat(df.getFormat("0.0%"));
      pctCell.setAlignment(HorizontalAlignment.RIGHT);
      borderAll(pctCell);

      workDaysHighlight = wb.createCellStyle();
      workDaysHighlight.setFont(fontBold);
      workDaysHighlight.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
      workDaysHighlight.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      workDaysHighlight.setAlignment(HorizontalAlignment.CENTER);
      borderAll(workDaysHighlight);
    }

    private static void borderAll(CellStyle s) {
      s.setBorderBottom(BorderStyle.THIN);
      s.setBorderTop(BorderStyle.THIN);
      s.setBorderLeft(BorderStyle.THIN);
      s.setBorderRight(BorderStyle.THIN);
    }
  }
}
