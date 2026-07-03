package com.bipros.importexport.infrastructure.parser;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.RecordFormatException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a multi-sheet Excel (.xlsx) baseline schedule into the same canonical
 * {@code Map<tableName, List<row>>} shape produced by {@link XerParser}, so downstream
 * consumers (e.g. ScheduleApplyService) don't need to know whether the source was XER or Excel.
 *
 * <p>Only the Activities sheet is required; all resource sheets (Manpower, Equipment, Material,
 * Sub-Contractor) and the WBS/Relationships sheets are optional and simply produce no table key
 * when absent or empty.
 */
@Component
@Slf4j
public class ExcelScheduleParser {

  private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private static final String NOT_A_WORKBOOK_MESSAGE =
      "This file could not be read as an Excel (.xlsx) workbook — it may be corrupted, "
          + "password-protected, or not an Excel file. Re-download the template or re-save "
          + "from Excel and try again.";

  private static final String UNREADABLE_MESSAGE =
      "This Excel file could not be read — it may be corrupted or unreadable.";

  /**
   * Parses {@code content} by first spilling it to a temp file and opening that with POI's
   * random-access {@code File} entry point rather than an in-memory stream. POI's stream-based
   * open speculatively allocates buffers sized from record headers in the file, which can exceed
   * the JVM-global {@code IOUtils} byte-array cap set elsewhere in the app (see
   * {@code PoiSafetyConfig}) and blow up with a {@link RecordFormatException} even for legitimate
   * files; opening from a file never makes that allocation.
   */
  public Map<String, List<Map<String, String>>> parse(byte[] content) {
    if (content == null || content.length == 0) {
      throw new BusinessRuleException("INVALID_IMPORT_FILE", "The uploaded file is empty.");
    }

    Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
    Path tmp;
    try {
      tmp = Files.createTempFile("bipros-baseline-", ".xlsx");
    } catch (IOException e) {
      throw new BusinessRuleException("INVALID_IMPORT_FILE", UNREADABLE_MESSAGE);
    }

    try {
      Files.write(tmp, content);
      try (Workbook workbook = WorkbookFactory.create(tmp.toFile(), null, true)) {
        parseActivities(workbook, tables);
        parseWbs(workbook, tables);
        parseRelationships(workbook, tables);
        parseManpower(workbook, tables);
        parseEquipment(workbook, tables);
        parseMaterial(workbook, tables);
        parseSubContractor(workbook, tables);
      }
    } catch (BusinessRuleException e) {
      throw e;
    } catch (EmptyFileException
        | EncryptedDocumentException
        | OfficeXmlFileException
        | NotOfficeXmlFileException
        | RecordFormatException
        | POIXMLException e) {
      throw new BusinessRuleException("INVALID_IMPORT_FILE", NOT_A_WORKBOOK_MESSAGE);
    } catch (IOException e) {
      throw new BusinessRuleException("INVALID_IMPORT_FILE", UNREADABLE_MESSAGE);
    } catch (RuntimeException e) {
      // Safety net: guarantees no raw POI (or other) RuntimeException ever escapes this method.
      throw new BusinessRuleException("INVALID_IMPORT_FILE", NOT_A_WORKBOOK_MESSAGE);
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
        // best-effort cleanup; nothing useful to do if the temp file can't be removed
      }
    }
    log.debug("Parsed Excel schedule: {} tables", tables.size());
    return tables;
  }

  private void parseActivities(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Activities");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> task = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      String code = v(raw, "Activity Code");
      out.put("task_code", code);
      out.put("task_id", code);
      out.put("task_name", v(raw, "Name"));
      out.put("wbs_id", v(raw, "WBS Code"));
      String type = v(raw, "Type");
      out.put("task_type", "Milestone".equalsIgnoreCase(type) ? "TT_FinMile" : "TT_Task");
      out.put("target_start_date", v(raw, "Planned Start"));
      out.put("target_end_date", v(raw, "Planned Finish"));
      out.put("target_drtn_hr_cnt", daysToHours(v(raw, "Duration (days)")));
      out.put("phys_complete_pct", v(raw, "% Complete"));
      task.add(out);
    }
    tables.put("TASK", task);
  }

  private void parseWbs(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "WBS");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> wbs = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      String code = v(raw, "WBS Code");
      out.put("wbs_id", code);
      out.put("wbs_short_name", code);
      out.put("wbs_name", v(raw, "WBS Name"));
      out.put("parent_wbs_id", v(raw, "Parent WBS Code"));
      wbs.add(out);
    }
    tables.put("PROJWBS", wbs);
  }

  private void parseRelationships(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Relationships");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> preds = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      out.put("pred_task_id", v(raw, "Predecessor Code"));
      out.put("task_id", v(raw, "Successor Code"));
      out.put("pred_type", v(raw, "Type"));
      out.put("lag_hr_cnt", daysToHours(v(raw, "Lag (days)")));
      preds.add(out);
    }
    tables.put("TASKPRED", preds);
  }

  private void parseManpower(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Manpower");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> manpower = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      out.put("activity_code", v(raw, "Activity Code"));
      out.put("role_code", v(raw, "Role Code"));
      out.put("category", v(raw, "Category"));
      out.put("grade", v(raw, "Grade"));
      out.put("nos", v(raw, "Nos"));
      manpower.add(out);
    }
    tables.put("MANPOWER", manpower);
  }

  private void parseEquipment(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Equipment");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> equipment = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      out.put("activity_code", v(raw, "Activity Code"));
      out.put("role_code", v(raw, "Role Code"));
      out.put("make", v(raw, "Make"));
      out.put("model", v(raw, "Model"));
      out.put("nos", v(raw, "Nos"));
      equipment.add(out);
    }
    tables.put("EQUIPMENT", equipment);
  }

  private void parseMaterial(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Material");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> material = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      out.put("activity_code", v(raw, "Activity Code"));
      out.put("role_code", v(raw, "Role Code"));
      out.put("spec_grade", v(raw, "Spec/Grade"));
      out.put("quantity", v(raw, "Quantity"));
      material.add(out);
    }
    tables.put("MATERIAL", material);
  }

  private void parseSubContractor(Workbook workbook, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> rows = readSheetRows(workbook, "Sub-Contractor");
    if (rows.isEmpty()) {
      return;
    }
    List<Map<String, String>> subContractor = new ArrayList<>();
    for (Map<String, String> raw : rows) {
      Map<String, String> out = new LinkedHashMap<>();
      out.put("activity_code", v(raw, "Activity Code"));
      out.put("sub_contractor_code", v(raw, "Sub-Contractor Code"));
      out.put("work_type", v(raw, "Work Type"));
      out.put("quantity", v(raw, "Quantity"));
      subContractor.add(out);
    }
    tables.put("SUBCONTRACTOR", subContractor);
  }

  /**
   * Reads a sheet into a list of raw rows keyed by lower-cased header text (as it appears in
   * row 0). Returns an empty list if the sheet is absent or has no non-blank data rows.
   */
  private List<Map<String, String>> readSheetRows(Workbook workbook, String sheetName) {
    Sheet sheet = workbook.getSheet(sheetName);
    if (sheet == null) {
      return List.of();
    }
    Row headerRow = sheet.getRow(0);
    if (headerRow == null) {
      return List.of();
    }

    Map<String, Integer> headerIndex = new LinkedHashMap<>();
    for (Cell cell : headerRow) {
      String header = cellString(cell);
      if (!header.isBlank()) {
        headerIndex.put(header.toLowerCase(Locale.ROOT), cell.getColumnIndex());
      }
    }

    List<Map<String, String>> rows = new ArrayList<>();
    int lastRowNum = sheet.getLastRowNum();
    for (int r = 1; r <= lastRowNum; r++) {
      Row row = sheet.getRow(r);
      if (row == null) {
        continue;
      }
      Map<String, String> raw = new LinkedHashMap<>();
      boolean blank = true;
      for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
        String value = cellString(row.getCell(entry.getValue()));
        raw.put(entry.getKey(), value);
        if (!value.isBlank()) {
          blank = false;
        }
      }
      if (!blank) {
        rows.add(raw);
      }
    }
    return rows;
  }

  /** Looks up a raw row value by header name, case-insensitively. */
  private String v(Map<String, String> raw, String header) {
    return raw.getOrDefault(header.toLowerCase(Locale.ROOT), "");
  }

  private String daysToHours(String daysStr) {
    double days = parseDouble(daysStr);
    return String.valueOf(Math.round(days * 8));
  }

  private double parseDouble(String s) {
    if (s == null || s.isBlank()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private String cellString(Cell cell) {
    if (cell == null) {
      return "";
    }
    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue().trim();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getLocalDateTimeCellValue().toLocalDate().format(ISO_DATE);
        }
        double value = cell.getNumericCellValue();
        if (!Double.isInfinite(value) && value == Math.rint(value)) {
          return String.valueOf((long) value);
        }
        return String.valueOf(value);
      case BLANK:
      default:
        return "";
    }
  }
}
