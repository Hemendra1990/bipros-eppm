package com.bipros.importexport.infrastructure.parser;

import com.bipros.common.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelScheduleParserTest {

  private final ExcelScheduleParser parser = new ExcelScheduleParser();

  @Test
  void parsesActivitiesToXerKeys_andManpowerTable() throws IOException {
    byte[] content;
    try (Workbook workbook = new XSSFWorkbook()) {
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

      Sheet activities = workbook.createSheet("Activities");
      Row activitiesHeader = activities.createRow(0);
      String[] activitiesHeaders = {
          "Activity Code", "Name", "WBS Code", "Type",
          "Planned Start", "Planned Finish", "Duration (days)", "% Complete"
      };
      for (int i = 0; i < activitiesHeaders.length; i++) {
        activitiesHeader.createCell(i).setCellValue(activitiesHeaders[i]);
      }

      Row activityRow = activities.createRow(1);
      activityRow.createCell(0).setCellValue("A1");
      activityRow.createCell(1).setCellValue("Clearing");
      activityRow.createCell(2).setCellValue("W1");
      activityRow.createCell(3).setCellValue("Task");

      Cell startCell = activityRow.createCell(4);
      startCell.setCellValue(LocalDate.of(2026, 1, 5));
      startCell.setCellStyle(dateStyle);

      Cell finishCell = activityRow.createCell(5);
      finishCell.setCellValue(LocalDate.of(2026, 1, 30));
      finishCell.setCellStyle(dateStyle);

      activityRow.createCell(6).setCellValue(20);
      activityRow.createCell(7).setCellValue(0);

      Sheet manpower = workbook.createSheet("Manpower");
      Row manpowerHeader = manpower.createRow(0);
      String[] manpowerHeaders = {"Activity Code", "Role Code", "Category", "Grade", "Nos"};
      for (int i = 0; i < manpowerHeaders.length; i++) {
        manpowerHeader.createCell(i).setCellValue(manpowerHeaders[i]);
      }

      Row manpowerRow = manpower.createRow(1);
      manpowerRow.createCell(0).setCellValue("A1");
      manpowerRow.createCell(1).setCellValue("CARPENTER");
      manpowerRow.createCell(2).setCellValue("Skilled");
      manpowerRow.createCell(3).setCellValue("Grade A");
      manpowerRow.createCell(4).setCellValue(5);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      content = baos.toByteArray();
    }

    Map<String, List<Map<String, String>>> tables = parser.parse(content);

    List<Map<String, String>> task = tables.get("TASK");
    assertNotNull(task);
    assertEquals(1, task.size());
    assertEquals("A1", task.get(0).get("task_code"));
    assertEquals("A1", task.get(0).get("task_id"));
    assertEquals("Clearing", task.get(0).get("task_name"));
    assertEquals("2026-01-05", task.get(0).get("target_start_date"));
    assertEquals("160", task.get(0).get("target_drtn_hr_cnt"));

    List<Map<String, String>> manpowerTable = tables.get("MANPOWER");
    assertNotNull(manpowerTable);
    assertEquals(1, manpowerTable.size());
    assertEquals("A1", manpowerTable.get(0).get("activity_code"));
    assertEquals("CARPENTER", manpowerTable.get(0).get("role_code"));
    assertEquals("Skilled", manpowerTable.get(0).get("category"));
    assertEquals("Grade A", manpowerTable.get(0).get("grade"));
    assertEquals("5", manpowerTable.get(0).get("nos"));
  }

  @Test
  void activitiesOnly_isValid_resourceTablesEmpty() throws IOException {
    byte[] content;
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet activities = workbook.createSheet("Activities");
      Row header = activities.createRow(0);
      String[] headers = {
          "Activity Code", "Name", "WBS Code", "Type",
          "Planned Start", "Planned Finish", "Duration (days)", "% Complete"
      };
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }

      Row row = activities.createRow(1);
      row.createCell(0).setCellValue("A1");
      row.createCell(1).setCellValue("Clearing");
      row.createCell(2).setCellValue("W1");
      row.createCell(3).setCellValue("Task");
      row.createCell(4).setCellValue("2026-01-05");
      row.createCell(5).setCellValue("2026-01-30");
      row.createCell(6).setCellValue(20);
      row.createCell(7).setCellValue(0);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      content = baos.toByteArray();
    }

    Map<String, List<Map<String, String>>> t = parser.parse(content);

    List<Map<String, String>> task = t.get("TASK");
    assertNotNull(task);
    assertFalse(task.isEmpty());

    assertTrue(t.get("MANPOWER") == null || t.get("MANPOWER").isEmpty());
    assertTrue(t.get("EQUIPMENT") == null || t.get("EQUIPMENT").isEmpty());
    assertTrue(t.get("MATERIAL") == null || t.get("MATERIAL").isEmpty());
    assertTrue(t.get("SUBCONTRACTOR") == null || t.get("SUBCONTRACTOR").isEmpty());
  }

  @Test
  void parsesAllSheets_toCanonicalKeys() throws IOException {
    byte[] content;
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet activities = workbook.createSheet("Activities");
      Row activitiesHeader = activities.createRow(0);
      String[] activitiesHeaders = {
          "Activity Code", "Name", "WBS Code", "Type",
          "Planned Start", "Planned Finish", "Duration (days)", "% Complete"
      };
      for (int i = 0; i < activitiesHeaders.length; i++) {
        activitiesHeader.createCell(i).setCellValue(activitiesHeaders[i]);
      }

      Row taskRow = activities.createRow(1);
      taskRow.createCell(0).setCellValue("A1");
      taskRow.createCell(1).setCellValue("Clearing");
      taskRow.createCell(2).setCellValue("W1");
      taskRow.createCell(3).setCellValue("Task");
      taskRow.createCell(4).setCellValue("2026-01-05");
      taskRow.createCell(5).setCellValue("2026-01-30");
      taskRow.createCell(6).setCellValue(20);
      taskRow.createCell(7).setCellValue(45);

      Row milestoneRow = activities.createRow(2);
      milestoneRow.createCell(0).setCellValue("A2");
      milestoneRow.createCell(1).setCellValue("Handover");
      milestoneRow.createCell(2).setCellValue("W1");
      milestoneRow.createCell(3).setCellValue("Milestone");
      milestoneRow.createCell(4).setCellValue("2026-01-31");
      milestoneRow.createCell(5).setCellValue("2026-01-31");
      milestoneRow.createCell(6).setCellValue(0);
      milestoneRow.createCell(7).setCellValue(100);

      Sheet wbs = workbook.createSheet("WBS");
      Row wbsHeader = wbs.createRow(0);
      String[] wbsHeaders = {"WBS Code", "WBS Name", "Parent WBS Code"};
      for (int i = 0; i < wbsHeaders.length; i++) {
        wbsHeader.createCell(i).setCellValue(wbsHeaders[i]);
      }
      Row wbsRow = wbs.createRow(1);
      wbsRow.createCell(0).setCellValue("W1");
      wbsRow.createCell(1).setCellValue("Earthworks");
      wbsRow.createCell(2).setCellValue("PROJ");

      Sheet relationships = workbook.createSheet("Relationships");
      Row relHeader = relationships.createRow(0);
      String[] relHeaders = {"Predecessor Code", "Successor Code", "Type", "Lag (days)"};
      for (int i = 0; i < relHeaders.length; i++) {
        relHeader.createCell(i).setCellValue(relHeaders[i]);
      }
      Row relRow = relationships.createRow(1);
      relRow.createCell(0).setCellValue("A1");
      relRow.createCell(1).setCellValue("A2");
      relRow.createCell(2).setCellValue("FS");
      relRow.createCell(3).setCellValue(2);

      Sheet equipment = workbook.createSheet("Equipment");
      Row equipmentHeader = equipment.createRow(0);
      String[] equipmentHeaders = {"Activity Code", "Role Code", "Make", "Model", "Nos"};
      for (int i = 0; i < equipmentHeaders.length; i++) {
        equipmentHeader.createCell(i).setCellValue(equipmentHeaders[i]);
      }
      Row equipmentRow = equipment.createRow(1);
      equipmentRow.createCell(0).setCellValue("A1");
      equipmentRow.createCell(1).setCellValue("EXCAVATOR");
      equipmentRow.createCell(2).setCellValue("GENERIC");
      equipmentRow.createCell(3).setCellValue("STD");
      equipmentRow.createCell(4).setCellValue(3);

      Sheet material = workbook.createSheet("Material");
      Row materialHeader = material.createRow(0);
      String[] materialHeaders = {"Activity Code", "Role Code", "Spec/Grade", "Quantity"};
      for (int i = 0; i < materialHeaders.length; i++) {
        materialHeader.createCell(i).setCellValue(materialHeaders[i]);
      }
      Row materialRow = material.createRow(1);
      materialRow.createCell(0).setCellValue("A1");
      materialRow.createCell(1).setCellValue("CONCRETE");
      materialRow.createCell(2).setCellValue("C30");
      materialRow.createCell(3).setCellValue(50);

      Sheet subContractor = workbook.createSheet("Sub-Contractor");
      Row subContractorHeader = subContractor.createRow(0);
      String[] subContractorHeaders = {"Activity Code", "Sub-Contractor Code", "Work Type", "Quantity"};
      for (int i = 0; i < subContractorHeaders.length; i++) {
        subContractorHeader.createCell(i).setCellValue(subContractorHeaders[i]);
      }
      Row subContractorRow = subContractor.createRow(1);
      subContractorRow.createCell(0).setCellValue("A1");
      subContractorRow.createCell(1).setCellValue("SC-01");
      subContractorRow.createCell(2).setCellValue("Asphalt Laying");
      subContractorRow.createCell(3).setCellValue(500);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      content = baos.toByteArray();
    }

    Map<String, List<Map<String, String>>> tables = parser.parse(content);

    List<Map<String, String>> task = tables.get("TASK");
    assertNotNull(task);
    assertEquals(2, task.size());
    assertEquals("W1", task.get(0).get("wbs_id"));
    assertEquals("TT_Task", task.get(0).get("task_type"));
    assertEquals("2026-01-30", task.get(0).get("target_end_date"));
    assertEquals("45", task.get(0).get("phys_complete_pct"));
    assertEquals("TT_FinMile", task.get(1).get("task_type"));

    List<Map<String, String>> wbsTable = tables.get("PROJWBS");
    assertNotNull(wbsTable);
    assertEquals(1, wbsTable.size());
    assertEquals("W1", wbsTable.get(0).get("wbs_id"));
    assertEquals("W1", wbsTable.get(0).get("wbs_short_name"));
    assertEquals("Earthworks", wbsTable.get(0).get("wbs_name"));
    assertEquals("PROJ", wbsTable.get(0).get("parent_wbs_id"));

    List<Map<String, String>> preds = tables.get("TASKPRED");
    assertNotNull(preds);
    assertEquals(1, preds.size());
    assertEquals("A1", preds.get(0).get("pred_task_id"));
    assertEquals("A2", preds.get(0).get("task_id"));
    assertEquals("FS", preds.get(0).get("pred_type"));
    assertEquals("16", preds.get(0).get("lag_hr_cnt"));

    List<Map<String, String>> equipmentTable = tables.get("EQUIPMENT");
    assertNotNull(equipmentTable);
    assertEquals(1, equipmentTable.size());
    assertEquals("A1", equipmentTable.get(0).get("activity_code"));
    assertEquals("EXCAVATOR", equipmentTable.get(0).get("role_code"));
    assertEquals("GENERIC", equipmentTable.get(0).get("make"));
    assertEquals("STD", equipmentTable.get(0).get("model"));
    assertEquals("3", equipmentTable.get(0).get("nos"));

    List<Map<String, String>> materialTable = tables.get("MATERIAL");
    assertNotNull(materialTable);
    assertEquals(1, materialTable.size());
    assertEquals("A1", materialTable.get(0).get("activity_code"));
    assertEquals("CONCRETE", materialTable.get(0).get("role_code"));
    assertEquals("C30", materialTable.get(0).get("spec_grade"));
    assertEquals("50", materialTable.get(0).get("quantity"));

    List<Map<String, String>> subContractorTable = tables.get("SUBCONTRACTOR");
    assertNotNull(subContractorTable);
    assertEquals(1, subContractorTable.size());
    assertEquals("A1", subContractorTable.get(0).get("activity_code"));
    assertEquals("SC-01", subContractorTable.get(0).get("sub_contractor_code"));
    assertEquals("Asphalt Laying", subContractorTable.get(0).get("work_type"));
    assertEquals("500", subContractorTable.get(0).get("quantity"));
  }

  @Test
  void headerOnlySheet_emitsNoTable() throws IOException {
    byte[] content;
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet activities = workbook.createSheet("Activities");
      Row header = activities.createRow(0);
      String[] headers = {
          "Activity Code", "Name", "WBS Code", "Type",
          "Planned Start", "Planned Finish", "Duration (days)", "% Complete"
      };
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }

      Row row = activities.createRow(1);
      row.createCell(0).setCellValue("A1");
      row.createCell(1).setCellValue("Clearing");
      row.createCell(2).setCellValue("W1");
      row.createCell(3).setCellValue("Task");
      row.createCell(4).setCellValue("2026-01-05");
      row.createCell(5).setCellValue("2026-01-30");
      row.createCell(6).setCellValue(20);
      row.createCell(7).setCellValue(0);

      Sheet manpower = workbook.createSheet("Manpower");
      Row manpowerHeader = manpower.createRow(0);
      String[] manpowerHeaders = {"Activity Code", "Role Code", "Category", "Grade", "Nos"};
      for (int i = 0; i < manpowerHeaders.length; i++) {
        manpowerHeader.createCell(i).setCellValue(manpowerHeaders[i]);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      content = baos.toByteArray();
    }

    Map<String, List<Map<String, String>>> tables = parser.parse(content);

    List<Map<String, String>> task = tables.get("TASK");
    assertNotNull(task);
    assertFalse(task.isEmpty());

    assertTrue(tables.get("MANPOWER") == null || tables.get("MANPOWER").isEmpty());
  }

  @Test
  void emptyBytes_throwInvalidImportFile() {
    BusinessRuleException ex =
        assertThrows(BusinessRuleException.class, () -> parser.parse(new byte[0]));
    assertEquals("INVALID_IMPORT_FILE", ex.getRuleCode());
  }

  @Test
  void nonXlsxBytes_throwInvalidImportFile_notRawPoiException() {
    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> parser.parse("not a workbook".getBytes(StandardCharsets.UTF_8)));
    assertEquals("INVALID_IMPORT_FILE", ex.getRuleCode());
  }

  @Test
  void validWorkbook_stillParses() throws IOException {
    byte[] content;
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet activities = workbook.createSheet("Activities");
      Row header = activities.createRow(0);
      String[] headers = {
          "Activity Code", "Name", "WBS Code", "Type",
          "Planned Start", "Planned Finish", "Duration (days)", "% Complete"
      };
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }

      Row row = activities.createRow(1);
      row.createCell(0).setCellValue("A1");
      row.createCell(1).setCellValue("Clearing");
      row.createCell(2).setCellValue("W1");
      row.createCell(3).setCellValue("Task");
      row.createCell(4).setCellValue("2026-01-05");
      row.createCell(5).setCellValue("2026-01-30");
      row.createCell(6).setCellValue(20);
      row.createCell(7).setCellValue(0);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      content = baos.toByteArray();
    }

    Map<String, List<Map<String, String>>> tables = parser.parse(content);

    assertEquals("A1", tables.get("TASK").get(0).get("task_code"));
  }
}
