package com.bipros.api.config.seeder;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Reads the three Oman Barka–Nakhal Road Project workbooks shipped at
 * {@code seed-data/oman-road-project/} and exposes typed row records per section.
 * Modelled on {@link NhaiRoadProjectWorkbookReader} — same parsing strategy
 * (locate by header text, scan to blank-row sentinel) and the same defensive
 * cell helpers (#REF! / ERROR / BLANK collapse to {@code null} so the seeder
 * can fall back to deterministic synthetic values).
 *
 * <p>Three workbooks back this reader:
 * <ul>
 *   <li>File 1 — DPR-internal (resources, daily progress)</li>
 *   <li>File 2 — Capacity_Utilization (productivity norms — plant + manpower)</li>
 *   <li>File 3 — Supervisor-Engineer-CM-PM DBS (project info, BOQ, equipment master)</li>
 * </ul>
 *
 * <p>Reader does no business logic — it just returns rows. Agent 2's seeder
 * decides what to do with {@code null} values.
 */
@Slf4j
@Component
public class OmanRoadProjectWorkbookReader {

  public static final String DPR_INTERNAL_PATH    = "seed-data/oman-road-project/01-DPR-internal.xlsx";
  public static final String CAPACITY_UTIL_PATH   = "seed-data/oman-road-project/02-Capacity-Utilization.xlsx";
  public static final String SUPERVISOR_DBS_PATH  = "seed-data/oman-road-project/03-Supervisor-Engineer-CM-PM-DBS.xlsx";

  /** Hard-coded project code for the Barka–Nakhal Dualisation project. */
  public static final String PROJECT_CODE = "6155";

  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ENGLISH);
  private static final DateTimeFormatter[] DATE_FORMATS = {
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
  };

  static {
    // The Oman workbooks contain heavily-formatted XML with very high
    // compression ratios — POI's default 1% inflate-ratio guard flags
    // them as zip bombs. Lower the threshold (these are trusted bundled
    // resources, not user uploads).
    ZipSecureFile.setMinInflateRatio(0.0001);
  }

  /** True only if all 3 workbooks are present on the classpath. */
  public boolean exists() {
    return new ClassPathResource(DPR_INTERNAL_PATH).exists()
        && new ClassPathResource(CAPACITY_UTIL_PATH).exists()
        && new ClassPathResource(SUPERVISOR_DBS_PATH).exists();
  }

  // ───────────────────────── Top-level record types ─────────────────────────

  public record ProjectInfo(
      String projectName, String projectCode, LocalDate dataDate) {}

  public record BoqRow(
      String code, String description, String unit,
      BigDecimal rate, BigDecimal planQty, BigDecimal planAmount,
      BigDecimal achievedQty, BigDecimal achievedAmount) {}

  public record EquipmentRateRow(
      String code, String description, String unit, BigDecimal ratePerDay) {}

  public record MaterialRow(
      String code, String description, String unit, BigDecimal rate) {}

  public record IndirectStaffRow(
      Integer itemNo, String position) {}

  public record DirectLabourRow(
      Integer itemNo, String position) {}

  public record ProductivityNormRow(
      String equipmentOrLabour, String activity, BigDecimal outputPerDay, String unit) {}

  public record SupervisorTeamRow(
      String name, String role, String section) {}

  // ─────────────────────────── Workbook open helper ───────────────────────────

  /**
   * Opens the workbook at {@code path} (classpath), runs {@code fn}, and
   * closes it afterwards. Single signature — callers pass the path constant
   * they want.
   */
  public <R> R withWorkbook(String path, Function<Workbook, R> fn) {
    ClassPathResource res = new ClassPathResource(path);
    if (!res.exists()) {
      throw new IllegalStateException("Workbook not on classpath: " + path);
    }
    try (InputStream is = res.getInputStream();
         Workbook wb = new XSSFWorkbook(is)) {
      return fn.apply(wb);
    } catch (Exception e) {
      throw new RuntimeException("Failed reading " + path + ": " + e.getMessage(), e);
    }
  }

  // ───────────────────────────── Public readers ─────────────────────────────

  /**
   * Project header — observed in File 3 sheet "PRE" row 2 col B as
   * "6155_Dualization of Barka Nakhal Road" and similarly in
   * "Anbazhagan-TS". The DPR sheet's title row is a placeholder ("xxxx")
   * so we scan a small set of supervisor sheets for the literal token.
   * Falls back to the hard-coded code "6155" if parsing fails.
   */
  public ProjectInfo readProjectInfo(Workbook wb) {
    String name = "Dualization of Barka Nakhal Road Project";
    String code = PROJECT_CODE;
    LocalDate dataDate = null;
    String[] candidateSheets = { "PRE", "Anbazhagan-TS", "DPR" };
    for (String sheetName : candidateSheets) {
      Sheet s = wb.getSheet(sheetName);
      if (s == null) continue;
      int last = Math.min(s.getLastRowNum(), 6);
      for (int i = 0; i <= last; i++) {
        Row r = s.getRow(i);
        if (r == null) continue;
        for (int c = 0; c < Math.min(r.getLastCellNum(), 12); c++) {
          Cell cell = r.getCell(c);
          if (cell == null) continue;
          String v = stringValue(cell);
          if (v != null && v.contains("Barka") && v.contains("Nakhal")) {
            int us = v.indexOf('_');
            if (us > 0) {
              String maybeCode = v.substring(0, us).trim();
              if (maybeCode.matches("\\d{2,6}")) code = maybeCode;
              name = v.substring(us + 1).trim();
            } else {
              name = v.trim();
            }
          }
          if (dataDate == null) {
            LocalDate d = cellToDate(cell);
            if (d != null) dataDate = d;
          }
        }
      }
      if (name.contains("Barka")) break;
    }
    return new ProjectInfo(name, code, dataDate);
  }

  /**
   * File 3 sheet "DPR" (sheet 5). Header row 4 (1-based), data rows 5+.
   * Observed columns (1-based / 0-based-index): C/2=code, D/3=description,
   * E/4=unit, F/5=rate (OMR), G/6=plan qty, H/7=plan amount, I/8=achieved
   * qty, J/9=achieved amount. The plan's spec said B=code/C=description
   * but the actual sheet is shifted one column right; the reader follows
   * the actual layout. Stops at 5+ consecutive blank rows. Preserves
   * "1.3.5(i)a"-style codes verbatim. Skips section-title rows (e.g.
   * "Preliminaries" with no code-shaped value in C).
   */
  public List<BoqRow> readBoqItems(Workbook wb) {
    Sheet s = findSheet(wb, "DPR", 4);
    if (s == null) return List.of();
    List<BoqRow> out = new ArrayList<>();
    int last = s.getLastRowNum();
    int firstDataRow = 4; // 0-based row index 4 == 1-based row 5
    int blankStreak = 0;
    for (int i = firstDataRow; i <= last; i++) {
      Row r = s.getRow(i);
      if (r == null) { if (++blankStreak >= 5) break; continue; }
      String code = stringValue(r.getCell(2)); // col C
      String description = stringValue(r.getCell(3)); // col D
      if (code == null && description == null) {
        if (++blankStreak >= 5) break;
        continue;
      }
      blankStreak = 0;
      if (description == null) continue;   // require description
      // Skip aggregate / total markers
      String lc = description.toLowerCase(Locale.ROOT);
      if (lc.startsWith("total") || lc.startsWith("grand total")) continue;
      // Skip section header rows where "code" is just a numeric section
      // index (e.g. "1") — real BOQ codes look like "1.3.5(i)a".
      if (code == null) continue;
      String trimmedCode = code.trim();
      if (trimmedCode.matches("\\d+")) continue; // section header like "1"
      String unit = stringValue(r.getCell(4));
      BigDecimal rate = numericValue(r.getCell(5));
      BigDecimal planQty = numericValue(r.getCell(6));
      BigDecimal planAmount = numericValue(r.getCell(7));
      BigDecimal achievedQty = numericValue(r.getCell(8));
      BigDecimal achievedAmount = numericValue(r.getCell(9));
      out.add(new BoqRow(trimmedCode, description.trim(), unit, rate,
          planQty, planAmount, achievedQty, achievedAmount));
    }
    return out;
  }

  /**
   * File 3 sheet "MP & Eqpt Summary 1". Reads code (col A) + description
   * (col D). Rates in this sheet are formula cells with broken
   * {@code #REF!} lookups — they cache as 0 — so the rate is always
   * {@code null} here; the seeder falls back to the JSON master
   * ({@code oman-road-equipment-master.json}) which has the literal OMR
   * values from plan §4.6. Header at row 2 (1-based), data from row 3.
   * Stops at 5+ consecutive blank rows or after ~80 entries.
   */
  public List<EquipmentRateRow> readEquipmentMaster(Workbook wb) {
    Sheet s = findSheet(wb, "MP & Eqpt Summary 1", 7);
    if (s == null) return List.of();
    List<EquipmentRateRow> out = new ArrayList<>();
    int last = Math.min(s.getLastRowNum(), 100);
    int blankStreak = 0;
    for (int i = 2; i <= last; i++) { // 0-based row 2 == 1-based row 3
      Row r = s.getRow(i);
      if (r == null) { if (++blankStreak >= 5) break; continue; }
      String code = stringValue(r.getCell(0));   // col A
      String desc = stringValue(r.getCell(3));   // col D
      if (code == null || desc == null) {
        if (++blankStreak >= 5) break;
        continue;
      }
      blankStreak = 0;
      // Skip header / title rows
      String lc = desc.toLowerCase(Locale.ROOT);
      if (lc.contains("description") || lc.contains("major equipment")) continue;
      // Code should be a short identifier (≤ 8 chars, alpha-prefixed).
      if (code.length() > 8 || !code.matches("[A-Za-z][A-Za-z0-9 \\-]*")) continue;
      // Rate cells contain VLOOKUP-with-#REF! — cached as 0 — so this is
      // typically null. The seeder uses the JSON master for the real rate.
      BigDecimal rate = numericValue(r.getCell(4)); // col E "Rate" header
      out.add(new EquipmentRateRow(code.trim(), desc.trim(), "Day", rate));
      if (out.size() >= 100) break;
    }
    return out;
  }

  /**
   * File 2 sheets "Plant utilization" + "Manpower utilization". Each row:
   * equipment-or-labour name + activity + outputPerDay + unit. The sheet
   * uses a wide layout with the resource name in col B and activity / norm
   * in cols C-E. ~50 rows total expected.
   */
  public List<ProductivityNormRow> readProductivityNorms(Workbook wb) {
    List<ProductivityNormRow> out = new ArrayList<>();
    out.addAll(readProductivityNormsFromSheet(wb, "Plant utilization"));
    out.addAll(readProductivityNormsFromSheet(wb, "Manpower utilization"));
    return out;
  }

  /**
   * Observed layout (File 2 "Plant utilization" / "Manpower utilization"):
   * row 3 = "S.No." header; data from row 7 onwards. Resource header rows
   * have a value in col A (S.No.) and col B (resource name, e.g.
   * "Bull Dozer"). Activity rows have <strong>blank A</strong>, with col B
   * holding the activity name (e.g. "Clearing & grubbing"), col C the
   * unit descriptor ("Sqm/Day"), col D the productivity norm (4000), and
   * col E the output unit ("Sqm").
   */
  private List<ProductivityNormRow> readProductivityNormsFromSheet(Workbook wb, String sheetName) {
    Sheet s = wb.getSheet(sheetName);
    if (s == null) return List.of();
    List<ProductivityNormRow> rows = new ArrayList<>();
    int last = Math.min(s.getLastRowNum(), 200);
    String currentResource = null;
    int blankStreak = 0;
    for (int i = 6; i <= last; i++) { // 0-based row 6 == 1-based row 7
      Row r = s.getRow(i);
      if (r == null) { if (++blankStreak >= 10) break; continue; }
      String colA = stringValue(r.getCell(0));
      String colB = stringValue(r.getCell(1));
      // Resource header row: A has a serial number, B has the resource name.
      if (colA != null && colB != null && !colA.isEmpty()) {
        // Skip non-data rows
        String lcA = colA.toLowerCase(Locale.ROOT);
        if (lcA.startsWith("s.no") || lcA.startsWith("budget") || lcA.startsWith("description")) {
          continue;
        }
        currentResource = colB.trim();
        blankStreak = 0;
        continue;
      }
      // Activity row: A blank, B = activity name, D = norm, E = unit
      if (colA == null && colB != null) {
        BigDecimal output = numericValue(r.getCell(3)); // col D
        String unit = stringValue(r.getCell(4));        // col E
        if (currentResource != null && output != null) {
          rows.add(new ProductivityNormRow(currentResource, colB.trim(), output, unit));
        }
        blankStreak = 0;
        continue;
      }
      if (colA == null && colB == null) {
        if (++blankStreak >= 10) break;
      }
      if (rows.size() >= 120) break;
    }
    return rows;
  }

  /**
   * File 1 sheet "Resource" rows 7–49 left half (cols A=Item#, B=Position).
   * Direct labour lives in the right half (cols F=Item#, G=Position) and
   * is read by {@link #readDirectLabour(Workbook)}.
   */
  public List<IndirectStaffRow> readIndirectStaff(Workbook wb) {
    Sheet s = findSheet(wb, "Resource", 3);
    if (s == null) return List.of();
    List<IndirectStaffRow> out = new ArrayList<>();
    for (int i = 6; i <= Math.min(s.getLastRowNum(), 60); i++) { // 0-based row 6 = spec row 7
      Row r = s.getRow(i);
      if (r == null) continue;
      Integer itemNo = intValue(r.getCell(0));    // col A
      String position = stringValue(r.getCell(1)); // col B
      if (position == null) continue;
      // Skip section headers / total rows
      String lc = position.toLowerCase(Locale.ROOT);
      if (lc.startsWith("position") || lc.contains("indirect staff") || lc.startsWith("total")) continue;
      out.add(new IndirectStaffRow(itemNo, position.trim()));
    }
    return out;
  }

  /**
   * File 1 sheet "Resource" rows 7–39+ right half (cols F=Item#, G=Position).
   */
  public List<DirectLabourRow> readDirectLabour(Workbook wb) {
    Sheet s = findSheet(wb, "Resource", 3);
    if (s == null) return List.of();
    List<DirectLabourRow> out = new ArrayList<>();
    for (int i = 6; i <= Math.min(s.getLastRowNum(), 60); i++) {
      Row r = s.getRow(i);
      if (r == null) continue;
      Integer itemNo = intValue(r.getCell(5));    // col F
      String position = stringValue(r.getCell(6)); // col G
      if (position == null) continue;
      String lc = position.toLowerCase(Locale.ROOT);
      if (lc.startsWith("position") || lc.contains("skilled") || lc.contains("un-skilled")
          || lc.contains("direct staff") || lc.startsWith("total")) continue;
      out.add(new DirectLabourRow(itemNo, position.trim()));
    }
    return out;
  }

  // ───────────────────────────── Sheet helpers ─────────────────────────────

  /** Find a sheet by name first, otherwise fall back to the given index. */
  private Sheet findSheet(Workbook wb, String name, int fallbackIndex) {
    Sheet s = wb.getSheet(name);
    if (s != null) return s;
    if (fallbackIndex >= 0 && fallbackIndex < wb.getNumberOfSheets()) {
      return wb.getSheetAt(fallbackIndex);
    }
    log.warn("[Oman reader] sheet '{}' (idx {}) not found in workbook", name, fallbackIndex);
    return null;
  }

  private String firstNonNull(String... vals) {
    for (String v : vals) if (v != null) return v;
    return null;
  }

  // ───────────────────────────── Cell parsers ─────────────────────────────

  /** String view of a cell — returns null on BLANK / ERROR / empty / dash. */
  private String stringValue(Cell c) {
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.BLANK || type == CellType.ERROR) return null;
    String v = FORMATTER.formatCellValue(c).trim();
    if (v.isEmpty() || "—".equals(v) || "-".equals(v) || "#REF!".equals(v) || "#N/A".equals(v)) return null;
    return v;
  }

  /**
   * Numeric view of a cell — returns null on ERROR / BLANK / unparseable
   * text. This is the fence that keeps {@code #REF!} from ever propagating
   * to the seeder.
   */
  private BigDecimal numericValue(Cell c) {
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.ERROR || type == CellType.BLANK) return null;
    if (type == CellType.NUMERIC) {
      try {
        return BigDecimal.valueOf(c.getNumericCellValue());
      } catch (Exception e) {
        return null;
      }
    }
    String s = stringValue(c);
    if (s == null) return null;
    try {
      return new BigDecimal(s.replace(",", "").replace("OMR", "").replace("R.O", "").trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer intValue(Cell c) {
    BigDecimal v = numericValue(c);
    return v == null ? null : v.intValue();
  }

  /** Date view of a cell — returns null on parse failure. */
  @SuppressWarnings("unused")
  private LocalDate dateValue(Cell c) {
    return cellToDate(c);
  }

  private LocalDate cellToDate(Cell c) {
    if (c == null) return null;
    try {
      CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
      if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
        Date d = c.getDateCellValue();
        if (d == null) return null;
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      }
      String v = stringValue(c);
      return v == null ? null : parseDate(v);
    } catch (Exception e) {
      return null;
    }
  }

  private LocalDate parseDate(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    for (DateTimeFormatter f : DATE_FORMATS) {
      try {
        return LocalDate.parse(trimmed, f);
      } catch (Exception ignored) {}
    }
    return null;
  }
}
