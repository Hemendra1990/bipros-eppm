package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.resource.domain.model.ProductivityNormType;
import lombok.extern.slf4j.Slf4j;
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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reads the NH-48 PMS workbook ({@code seed-data/road-project/PMS RoadProject TestData.xlsx}) and
 * exposes typed row records per sheet section. The seeder uses these in place of hardcoded Java
 * arrays so that updating the sample data never requires a code change.
 *
 * <p>Parsing strategy: locate each section by scanning the first column for its header text
 * ("SECTION A – UNIT RATE MASTER", "SECTION B – …", etc.), skip the next (column-header) row, then
 * iterate rows until a blank row or the next section header is encountered. This survives layout
 * drift as long as section titles stay stable.
 */
@Slf4j
@Component
public class NhaiRoadProjectWorkbookReader {

  public static final String WORKBOOK_PATH = "seed-data/road-project/PMS RoadProject TestData.xlsx";

  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ENGLISH);
  private static final DateTimeFormatter[] DATE_FORMATS = {
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
  };

  public boolean exists() {
    return new ClassPathResource(WORKBOOK_PATH).exists();
  }

  // ───────────────────────── Top-level record types ─────────────────────────

  public record ProjectInfo(
      String projectName, String projectCode, String client, String contractor,
      String projectManager, LocalDate startDate, LocalDate plannedCompletion,
      String contractValue, String totalLength, String location) {}

  public record BoqRow(
      String itemNo, String description, String unit,
      BigDecimal boqQty, BigDecimal boqRate, BigDecimal budgetedRate,
      BigDecimal qtyExecutedToDate, BigDecimal actualRate) {}

  public record UnitRateRow(
      String category, String description, String unit,
      BigDecimal budgetedRate, BigDecimal actualRate, String remarks) {}

  public record ProductivityNormRow(
      ProductivityNormType normType, String activityName, String unit,
      BigDecimal outputPerManPerDay, Integer crewSize, BigDecimal outputPerDay,
      BigDecimal outputPerHour, Double workingHoursPerDay,
      BigDecimal fuelLitresPerHour, String equipmentSpec, String remarks) {}

  public record DprRow(
      LocalDate reportDate, String supervisor, Long chainageFromM, Long chainageToM,
      String activity, String unit, BigDecimal qtyExecuted, String remarks) {}

  public record MaterialConsumptionRow(
      LocalDate logDate, String materialName, String unit,
      BigDecimal openingStock, BigDecimal received, BigDecimal consumed,
      BigDecimal closingStock, BigDecimal wastagePercent,
      String issuedBy, String receivedBy) {}

  public record ResourceDeploymentRow(
      LocalDate logDate, DeploymentResourceType type, String description,
      Integer nosPlanned, Integer nosDeployed,
      Double hoursWorked, Double idleHours, String remarks) {}

  public record WeatherRow(
      LocalDate logDate, Double tempMaxC, Double tempMinC, Double rainfallMm,
      Double windKmh, String condition, Double workingHours, String remarks) {}

  public record NextDayPlanRow(
      LocalDate reportDate, String activity, Long chainageFromM, Long chainageToM,
      BigDecimal targetQty, String unit, String concerns,
      String actionBy, LocalDate dueDate) {}

  // ───────────────────────────── Public readers ─────────────────────────────

  public interface WorkbookConsumer<T> {
    T accept(Workbook wb) throws Exception;
  }

  /** Opens the workbook for a block and closes it afterwards. */
  public <T> T withWorkbook(WorkbookConsumer<T> fn) {
    ClassPathResource res = new ClassPathResource(WORKBOOK_PATH);
    if (!res.exists()) {
      throw new IllegalStateException("Workbook not on classpath: " + WORKBOOK_PATH);
    }
    try (InputStream is = res.getInputStream();
         Workbook wb = new XSSFWorkbook(is)) {
      return fn.accept(wb);
    } catch (Exception e) {
      throw new RuntimeException("Failed reading " + WORKBOOK_PATH + ": " + e.getMessage(), e);
    }
  }

  public ProjectInfo readProjectInfo(Workbook wb) {
    Sheet s = requireSheet(wb, "Project Info");
    // Key in col A/B (index 0), value in col C (index 2)
    String name = null, code = null, client = null, contractor = null, pm = null;
    String contractValue = null, totalLength = null, location = null;
    LocalDate start = null, finish = null;
    for (Row r : s) {
      if (r == null) continue;
      String key = stringValue(r.getCell(0));
      String val = stringValue(r.getCell(2));
      if (key == null || val == null) continue;
      switch (key.trim().toLowerCase(Locale.ROOT)) {
        case "project name" -> name = val;
        case "project code" -> code = val;
        case "client" -> client = val;
        case "contractor" -> contractor = val;
        case "project manager" -> pm = val;
        case "start date" -> start = parseDate(val);
        case "planned completion" -> finish = parseDate(val);
        case "contract value" -> contractValue = val;
        case "total length" -> totalLength = val;
        case "project location" -> location = val;
        default -> { /* ignore */ }
      }
    }
    return new ProjectInfo(name, code, client, contractor, pm, start, finish, contractValue, totalLength, location);
  }

  public List<BoqRow> readBoqItems(Workbook wb) {
    Sheet s = requireSheet(wb, "BOQ & Budget");
    int header = findRowStartingWith(s, 0, "Item");
    if (header < 0) return List.of();
    List<BoqRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) continue;
      String itemNo = stringValue(r.getCell(0));
      if (itemNo == null || "GRAND TOTAL".equalsIgnoreCase(itemNo)) continue;
      String description = stringValue(r.getCell(1));
      String unit = stringValue(r.getCell(2));
      BigDecimal boqQty = decimalValue(r.getCell(3));
      BigDecimal boqRate = decimalValue(r.getCell(4));
      BigDecimal budgetedRate = decimalValue(r.getCell(6));
      BigDecimal qtyExecuted = decimalValue(r.getCell(8));
      BigDecimal actualRate = decimalValue(r.getCell(9));
      if (description == null) continue;
      out.add(new BoqRow(itemNo, description, unit, boqQty, boqRate, budgetedRate, qtyExecuted, actualRate));
    }
    return out;
  }

  public List<UnitRateRow> readUnitRateMaster(Workbook wb) {
    Sheet s = requireSheet(wb, "Daily Cost Report");
    int header = findRowStartingWith(s, 0, "S.No.");
    if (header < 0) return List.of();
    List<UnitRateRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String sno = stringValue(r.getCell(0));
      if (sno == null) break; // blank row = end of Section A
      String category = stringValue(r.getCell(1));
      String description = stringValue(r.getCell(2));
      String unit = stringValue(r.getCell(3));
      BigDecimal budgeted = decimalValue(r.getCell(4));
      BigDecimal actual = decimalValue(r.getCell(5));
      String remarks = stringValue(r.getCell(8));
      if (category == null || description == null) continue;
      out.add(new UnitRateRow(category, description, unit, budgeted, actual, remarks));
    }
    return out;
  }

  public List<ProductivityNormRow> readProductivityNorms(Workbook wb) {
    Sheet s = requireSheet(wb, "Productivity Norms");
    List<ProductivityNormRow> out = new ArrayList<>();

    // Section A — Manpower (row starts "S.No." under the MANPOWER section)
    int sectionA = findRowStartingWith(s, 0, "SECTION A");
    int manpowerHeader = findRowStartingWith(s, sectionA + 1, "S.No.");
    if (manpowerHeader >= 0) {
      for (int i = manpowerHeader + 1; i <= s.getLastRowNum(); i++) {
        Row r = s.getRow(i);
        if (r == null) break;
        String sno = stringValue(r.getCell(0));
        if (sno == null) break;
        String activity = stringValue(r.getCell(1));
        String unit = stringValue(r.getCell(2));
        BigDecimal outputPerMan = decimalValue(r.getCell(3));
        Integer crew = intValue(r.getCell(4));
        BigDecimal outputPerDay = decimalValue(r.getCell(5));
        String remarks = stringValue(r.getCell(6));
        if (activity == null) continue;
        out.add(new ProductivityNormRow(
            ProductivityNormType.MANPOWER, activity, unit, outputPerMan, crew, outputPerDay,
            null, null, null, null, remarks));
      }
    }

    // Section B — Equipment
    int sectionB = findRowStartingWith(s, 0, "SECTION B");
    int equipmentHeader = findRowStartingWith(s, sectionB + 1, "S.No.");
    if (equipmentHeader >= 0) {
      for (int i = equipmentHeader + 1; i <= s.getLastRowNum(); i++) {
        Row r = s.getRow(i);
        if (r == null) break;
        String sno = stringValue(r.getCell(0));
        if (sno == null) break;
        String equipment = stringValue(r.getCell(1));
        String spec = stringValue(r.getCell(2));
        String activity = stringValue(r.getCell(3));
        String unit = stringValue(r.getCell(4));
        BigDecimal outputHr = decimalValue(r.getCell(5));
        Double workingHrs = doubleValue(r.getCell(6));
        BigDecimal outputDay = decimalValue(r.getCell(7));
        BigDecimal fuel = decimalValue(r.getCell(8));
        String remarks = stringValue(r.getCell(9));
        if (activity == null) continue;
        String equipmentSpec = (equipment != null && spec != null)
            ? equipment + " — " + spec
            : (spec != null ? spec : equipment);
        out.add(new ProductivityNormRow(
            ProductivityNormType.EQUIPMENT, activity, unit, null, null, outputDay,
            outputHr, workingHrs, fuel, equipmentSpec, remarks));
      }
    }
    return out;
  }

  public List<DprRow> readSupervisorDailyReport(Workbook wb) {
    Sheet s = requireSheet(wb, "Supervisor Daily Rpt");
    int sectionA = findRowStartingWith(s, 0, "SECTION A");
    int header = findRowStartingWith(s, sectionA + 1, "Date");
    if (header < 0) return List.of();
    List<DprRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String dateStr = stringValue(r.getCell(0));
      if (dateStr == null) break;
      LocalDate date = cellToDate(r.getCell(0));
      String supervisor = stringValue(r.getCell(1));
      String chainage = stringValue(r.getCell(2));
      long[] ch = parseChainageRange(chainage);
      String activity = stringValue(r.getCell(3));
      String unit = stringValue(r.getCell(6));
      BigDecimal qty = decimalValue(r.getCell(5));
      String remarks = stringValue(r.getCell(8));
      if (date == null || activity == null) continue;
      out.add(new DprRow(date, supervisor, ch[0] == -1 ? null : ch[0], ch[1] == -1 ? null : ch[1],
          activity, unit, qty, remarks));
    }
    return out;
  }

  public List<ResourceDeploymentRow> readResourceDeployment(Workbook wb) {
    Sheet s = requireSheet(wb, "Supervisor Daily Rpt");
    int sectionB = findRowStartingWith(s, 0, "SECTION B");
    int header = findRowStartingWith(s, sectionB + 1, "Date");
    if (header < 0) return List.of();
    List<ResourceDeploymentRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String dateStr = stringValue(r.getCell(0));
      if (dateStr == null) break;
      LocalDate date = cellToDate(r.getCell(0));
      String typeStr = stringValue(r.getCell(1));
      if (typeStr == null) break;
      DeploymentResourceType type = "equipment".equalsIgnoreCase(typeStr)
          ? DeploymentResourceType.EQUIPMENT
          : DeploymentResourceType.MANPOWER;
      String description = stringValue(r.getCell(2));
      Integer planned = intValue(r.getCell(3));
      Integer deployed = intValue(r.getCell(4));
      Double hoursWorked = doubleValue(r.getCell(5));
      Double idleHours = doubleValue(r.getCell(6));
      String remarks = stringValue(r.getCell(7));
      if (description == null) continue;
      out.add(new ResourceDeploymentRow(date, type, description, planned, deployed, hoursWorked, idleHours, remarks));
    }
    return out;
  }

  public List<WeatherRow> readDailyWeather(Workbook wb) {
    Sheet s = requireSheet(wb, "Supervisor Daily Rpt");
    int sectionC = findRowStartingWith(s, 0, "SECTION C");
    int header = findRowStartingWith(s, sectionC + 1, "Date");
    if (header < 0) return List.of();
    List<WeatherRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String dateStr = stringValue(r.getCell(0));
      if (dateStr == null) break;
      LocalDate date = cellToDate(r.getCell(0));
      Double tempMax = doubleValue(r.getCell(1));
      Double tempMin = doubleValue(r.getCell(2));
      Double rainfall = doubleValue(r.getCell(3));
      Double wind = doubleValue(r.getCell(4));
      String condition = stringValue(r.getCell(5));
      Double working = doubleValue(r.getCell(6));
      String remarks = stringValue(r.getCell(7));
      out.add(new WeatherRow(date, tempMax, tempMin, rainfall, wind, condition, working, remarks));
    }
    return out;
  }

  public List<NextDayPlanRow> readNextDayPlans(Workbook wb) {
    Sheet s = requireSheet(wb, "Supervisor Daily Rpt");
    int sectionD = findRowStartingWith(s, 0, "SECTION D");
    int header = findRowStartingWith(s, sectionD + 1, "Date");
    if (header < 0) return List.of();
    List<NextDayPlanRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String dateStr = stringValue(r.getCell(0));
      if (dateStr == null) break;
      LocalDate reportDate = cellToDate(r.getCell(0));
      String activity = stringValue(r.getCell(1));
      String chainage = stringValue(r.getCell(2));
      long[] ch = parseChainageRange(chainage);
      BigDecimal targetQty = decimalValue(r.getCell(3));
      String unit = stringValue(r.getCell(4));
      String concerns = stringValue(r.getCell(5));
      String actionBy = stringValue(r.getCell(6));
      LocalDate dueDate = cellToDate(r.getCell(7));
      if (reportDate == null || activity == null) continue;
      out.add(new NextDayPlanRow(reportDate, activity, ch[0] == -1 ? null : ch[0], ch[1] == -1 ? null : ch[1],
          targetQty, unit, concerns, actionBy, dueDate));
    }
    return out;
  }

  public List<MaterialConsumptionRow> readMaterialConsumption(Workbook wb) {
    Sheet s = requireSheet(wb, "Daily Cost Report");
    int sectionC = findRowStartingWith(s, 0, "SECTION C");
    int header = findRowStartingWith(s, sectionC + 1, "Date");
    if (header < 0) return List.of();
    List<MaterialConsumptionRow> out = new ArrayList<>();
    for (int i = header + 1; i <= s.getLastRowNum(); i++) {
      Row r = s.getRow(i);
      if (r == null) break;
      String dateStr = stringValue(r.getCell(0));
      if (dateStr == null) break;
      LocalDate date = cellToDate(r.getCell(0));
      String material = stringValue(r.getCell(1));
      String unit = stringValue(r.getCell(2));
      BigDecimal opening = decimalValue(r.getCell(3));
      BigDecimal received = decimalValue(r.getCell(4));
      BigDecimal consumed = decimalValue(r.getCell(5));
      BigDecimal closing = decimalValue(r.getCell(6));
      BigDecimal wastage = decimalValue(r.getCell(7));
      String issuedBy = stringValue(r.getCell(8));
      String receivedBy = stringValue(r.getCell(9));
      if (material == null) continue;
      out.add(new MaterialConsumptionRow(date, material, unit, opening, received, consumed, closing,
          wastage, issuedBy, receivedBy));
    }
    return out;
  }

  // ───────────────────────────── Parsing helpers ─────────────────────────────

  private Sheet requireSheet(Workbook wb, String name) {
    Sheet s = wb.getSheet(name);
    if (s == null) {
      throw new IllegalStateException("Sheet '" + name + "' not found in workbook");
    }
    return s;
  }

  private int findRowStartingWith(Sheet s, int fromRow, String prefix) {
    int last = s.getLastRowNum();
    for (int i = Math.max(0, fromRow); i <= last; i++) {
      Row r = s.getRow(i);
      if (r == null) continue;
      String v = stringValue(r.getCell(0));
      if (v != null && v.startsWith(prefix)) return i;
    }
    return -1;
  }

  private String stringValue(Cell c) {
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.BLANK || type == CellType.ERROR) return null;
    String v = FORMATTER.formatCellValue(c).trim();
    if (v.isEmpty() || "—".equals(v) || "-".equals(v)) return null;
    return v;
  }

  private BigDecimal decimalValue(Cell c) {
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.NUMERIC) {
      return BigDecimal.valueOf(c.getNumericCellValue());
    }
    String s = stringValue(c);
    if (s == null) return null;
    try {
      return new BigDecimal(s.replace(",", "").replace("₹", "").trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer intValue(Cell c) {
    BigDecimal v = decimalValue(c);
    return v == null ? null : v.intValue();
  }

  private Double doubleValue(Cell c) {
    BigDecimal v = decimalValue(c);
    return v == null ? null : v.doubleValue();
  }

  private LocalDate cellToDate(Cell c) {
    if (c == null) return null;
    try {
      if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
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
    log.warn("[NH-48 reader] could not parse date '{}'", s);
    return null;
  }

  /** Parse "145+000 to 145+500" → [145000, 145500]; returns [-1,-1] on failure. */
  private long[] parseChainageRange(String s) {
    if (s == null) return new long[]{-1, -1};
    String[] parts = s.split("(?i)\\s*(to|–|-|—)\\s*");
    if (parts.length < 2) return new long[]{parseChainage(s), -1};
    return new long[]{parseChainage(parts[0]), parseChainage(parts[1])};
  }

  /** Parse "145+000" → 145000; "145" → 145000; returns -1 on failure. */
  private long parseChainage(String s) {
    if (s == null) return -1;
    String trimmed = s.trim();
    int plus = trimmed.indexOf('+');
    try {
      if (plus < 0) {
        return Long.parseLong(trimmed) * 1000;
      }
      long km = Long.parseLong(trimmed.substring(0, plus).trim());
      long m = Long.parseLong(trimmed.substring(plus + 1).trim());
      return km * 1000 + m;
    } catch (NumberFormatException ex) {
      return -1;
    }
  }
}
