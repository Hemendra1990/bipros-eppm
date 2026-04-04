package com.bipros.reporting.infrastructure.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class ExcelReportGenerator {

  public byte[] generateActivityReport(UUID projectId, List<Map<String, Object>> activityData) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet sheet = workbook.createSheet("Activities");

      String[] headers = {
        "Code", "Name", "Status", "Start", "Finish", "Duration", "Float", "Critical", "% Complete"
      };
      createHeaderRow(sheet, headers);

      int rowNum = 1;
      for (Map<String, Object> activity : activityData) {
        var row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue((String) activity.getOrDefault("code", ""));
        row.createCell(1).setCellValue((String) activity.getOrDefault("name", ""));
        row.createCell(2).setCellValue((String) activity.getOrDefault("status", ""));
        row.createCell(3).setCellValue((String) activity.getOrDefault("start", ""));
        row.createCell(4).setCellValue((String) activity.getOrDefault("finish", ""));
        row.createCell(5).setCellValue((Double) activity.getOrDefault("duration", 0.0));
        row.createCell(6).setCellValue((Double) activity.getOrDefault("float", 0.0));
        row.createCell(7).setCellValue((Boolean) activity.getOrDefault("critical", false));
        row.createCell(8).setCellValue((Double) activity.getOrDefault("percentComplete", 0.0));
      }

      autoSizeColumns(sheet, headers.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      log.error("Error generating activity report for project: {}", projectId, e);
      throw new RuntimeException("Failed to generate activity report", e);
    }
  }

  public byte[] generateResourceReport(UUID projectId, List<Map<String, Object>> resourceData) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet sheet = workbook.createSheet("Resources");

      String[] headers = {"Code", "Name", "Type", "Units"};
      createHeaderRow(sheet, headers);

      int rowNum = 1;
      for (Map<String, Object> resource : resourceData) {
        var row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue((String) resource.getOrDefault("code", ""));
        row.createCell(1).setCellValue((String) resource.getOrDefault("name", ""));
        row.createCell(2).setCellValue((String) resource.getOrDefault("type", ""));
        row.createCell(3).setCellValue((Double) resource.getOrDefault("units", 0.0));
      }

      autoSizeColumns(sheet, headers.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      log.error("Error generating resource report for project: {}", projectId, e);
      throw new RuntimeException("Failed to generate resource report", e);
    }
  }

  public byte[] generateCostReport(UUID projectId, List<Map<String, Object>> costData) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet sheet = workbook.createSheet("Costs");

      String[] headers = {"WBS", "Budget", "Actual", "Remaining", "EAC"};
      createHeaderRow(sheet, headers);

      int rowNum = 1;
      for (Map<String, Object> cost : costData) {
        var row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue((String) cost.getOrDefault("wbs", ""));
        row.createCell(1).setCellValue((Double) cost.getOrDefault("budget", 0.0));
        row.createCell(2).setCellValue((Double) cost.getOrDefault("actual", 0.0));
        row.createCell(3).setCellValue((Double) cost.getOrDefault("remaining", 0.0));
        row.createCell(4).setCellValue((Double) cost.getOrDefault("eac", 0.0));
      }

      autoSizeColumns(sheet, headers.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      log.error("Error generating cost report for project: {}", projectId, e);
      throw new RuntimeException("Failed to generate cost report", e);
    }
  }

  private void createHeaderRow(XSSFSheet sheet, String[] headers) {
    var headerRow = sheet.createRow(0);
    CellStyle headerStyle = sheet.getWorkbook().createCellStyle();

    Font headerFont = sheet.getWorkbook().createFont();
    headerFont.setBold(true);
    headerFont.setColor(IndexedColors.WHITE.getIndex());

    headerStyle.setFont(headerFont);
    headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
    headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

    for (int i = 0; i < headers.length; i++) {
      var cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }
  }

  private void autoSizeColumns(XSSFSheet sheet, int columnCount) {
    for (int i = 0; i < columnCount; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private byte[] toByteArray(XSSFWorkbook workbook) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    workbook.write(baos);
    return baos.toByteArray();
  }
}
