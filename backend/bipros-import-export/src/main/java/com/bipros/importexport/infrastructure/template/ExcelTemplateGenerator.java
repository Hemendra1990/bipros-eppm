package com.bipros.importexport.infrastructure.template;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * Generates a downloadable, blank-but-filled-in Excel baseline import template. Sheet names and
 * header strings mirror exactly what {@link com.bipros.importexport.infrastructure.parser.ExcelScheduleParser}
 * reads, so a template filled in by a user round-trips straight back through the parser.
 */
@Component
@Slf4j
public class ExcelTemplateGenerator {

  public byte[] generate() {
    try (Workbook workbook = new XSSFWorkbook()) {
      CellStyle headerStyle = createHeaderStyle(workbook);

      createSheet(workbook, headerStyle, "Activities",
          new String[] {"Activity Code", "Name", "WBS Code", "Type", "Planned Start",
              "Planned Finish", "Duration (days)", "% Complete"},
          new Object[] {"A1000", "Site Clearance", "W100", "Task", "2026-01-05",
              "2026-01-20", 12, 0});

      createSheet(workbook, headerStyle, "WBS",
          new String[] {"WBS Code", "WBS Name", "Parent WBS Code"},
          new Object[] {"W100", "Earthworks", ""});

      createSheet(workbook, headerStyle, "Relationships",
          new String[] {"Predecessor Code", "Successor Code", "Type", "Lag (days)"},
          new Object[] {"A1000", "A1010", "FS", 0});

      createSheet(workbook, headerStyle, "Manpower",
          new String[] {"Activity Code", "Role Code", "Category", "Grade", "Nos"},
          new Object[] {"A1000", "CARPENTER", "Skilled", "Grade A", 5});

      createSheet(workbook, headerStyle, "Equipment",
          new String[] {"Activity Code", "Role Code", "Make", "Model", "Nos"},
          new Object[] {"A1000", "EXCAVATOR", "GENERIC", "STD", 1});

      createSheet(workbook, headerStyle, "Material",
          new String[] {"Activity Code", "Role Code", "Spec/Grade", "Quantity"},
          new Object[] {"A1000", "CONCRETE", "C30", 50});

      createSheet(workbook, headerStyle, "Sub-Contractor",
          new String[] {"Activity Code", "Sub-Contractor Code", "Work Type", "Quantity"},
          new Object[] {"A1000", "SC-01", "Asphalt Laying", 500});

      createInstructionsSheet(workbook, headerStyle);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      byte[] result = baos.toByteArray();
      log.debug("Generated baseline import template: {} bytes", result.length);
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate Excel baseline import template", e);
    }
  }

  private void createSheet(Workbook workbook, CellStyle headerStyle, String sheetName,
      String[] headers, Object[] sampleRow) {
    Sheet sheet = workbook.createSheet(sheetName);

    Row headerRow = sheet.createRow(0);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    Row dataRow = sheet.createRow(1);
    for (int i = 0; i < sampleRow.length; i++) {
      Cell cell = dataRow.createCell(i);
      Object value = sampleRow[i];
      if (value instanceof Number number) {
        cell.setCellValue(number.doubleValue());
      } else {
        cell.setCellValue(String.valueOf(value));
      }
    }

    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createInstructionsSheet(Workbook workbook, CellStyle headerStyle) {
    Sheet sheet = workbook.createSheet("Instructions");

    Row headerRow = sheet.createRow(0);
    Cell headerCell = headerRow.createCell(0);
    headerCell.setCellValue("Baseline Import Template - Instructions");
    headerCell.setCellStyle(headerStyle);

    String[] lines = {
        "Only the Activities sheet is required; WBS/Relationships/resources are optional.",
        "Dates: YYYY-MM-DD.",
        "Durations in days.",
        "Resources reference existing role/variant master codes - no rates in the file.",
        "Do not rename any sheet or column header - the importer matches them exactly.",
        "Remove the sample data row on each sheet before uploading your real schedule."
    };
    for (int i = 0; i < lines.length; i++) {
      Row row = sheet.createRow(i + 1);
      row.createCell(0).setCellValue(lines[i]);
    }

    sheet.autoSizeColumn(0);
  }

  private CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
    style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    return style;
  }
}
