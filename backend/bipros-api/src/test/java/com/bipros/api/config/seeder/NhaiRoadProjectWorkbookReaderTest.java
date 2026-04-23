package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.BoqRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.DprRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.MaterialConsumptionRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.NextDayPlanRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.ProductivityNormRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.ProjectInfo;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.ResourceDeploymentRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.UnitRateRow;
import com.bipros.api.config.seeder.NhaiRoadProjectWorkbookReader.WeatherRow;
import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.resource.domain.model.ProductivityNormType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real workbook from the classpath and asserts the reader parses every section
 * correctly. Guards against silent drift if the workbook is edited or the sheet layout changes.
 */
class NhaiRoadProjectWorkbookReaderTest {

  private final NhaiRoadProjectWorkbookReader reader = new NhaiRoadProjectWorkbookReader();

  @Test
  void workbook_is_on_classpath() {
    assertThat(reader.exists()).isTrue();
  }

  @Test
  void project_info_has_the_expected_fields() {
    ProjectInfo info = reader.withWorkbook(reader::readProjectInfo);
    assertThat(info.projectCode()).isEqualTo("BIPROS/NHAI/RJ/2025/001");
    assertThat(info.projectName()).contains("NH-48");
    assertThat(info.client()).contains("NHAI");
    assertThat(info.contractor()).contains("ABC Infracon");
    assertThat(info.projectManager()).contains("Rajesh");
    assertThat(info.startDate()).isNotNull();
    assertThat(info.plannedCompletion()).isNotNull();
  }

  @Test
  void boq_items_match_workbook() {
    List<BoqRow> rows = reader.withWorkbook(reader::readBoqItems);
    assertThat(rows).hasSize(15);
    // Spot-check row 3.1 DBM
    BoqRow dbm = rows.stream().filter(r -> "3.1".equals(r.itemNo())).findFirst().orElseThrow();
    assertThat(dbm.description()).startsWith("DBM");
    assertThat(dbm.boqQty()).isEqualByComparingTo("5800");
    assertThat(dbm.boqRate()).isEqualByComparingTo("4400");
    assertThat(dbm.budgetedRate()).isEqualByComparingTo("4500");
    assertThat(dbm.qtyExecutedToDate()).isEqualByComparingTo("960");
    assertThat(dbm.actualRate()).isEqualByComparingTo("4620");
  }

  @Test
  void productivity_norms_has_manpower_and_equipment() {
    List<ProductivityNormRow> rows = reader.withWorkbook(reader::readProductivityNorms);
    long manpower = rows.stream().filter(r -> r.normType() == ProductivityNormType.MANPOWER).count();
    long equipment = rows.stream().filter(r -> r.normType() == ProductivityNormType.EQUIPMENT).count();
    assertThat(manpower).isGreaterThanOrEqualTo(10);
    assertThat(equipment).isGreaterThanOrEqualTo(10);
  }

  @Test
  void unit_rate_master_has_23_rows_across_four_categories() {
    List<UnitRateRow> rows = reader.withWorkbook(reader::readUnitRateMaster);
    assertThat(rows).hasSize(23);
    long manpower = rows.stream().filter(r -> "Manpower".equalsIgnoreCase(r.category())).count();
    long equipment = rows.stream().filter(r -> "Equipment".equalsIgnoreCase(r.category())).count();
    long material = rows.stream().filter(r -> "Material".equalsIgnoreCase(r.category())).count();
    long subContract = rows.stream().filter(r -> "Sub-Contract".equalsIgnoreCase(r.category())).count();
    assertThat(manpower).isEqualTo(5);
    assertThat(equipment).isEqualTo(7);
    assertThat(material).isEqualTo(7);
    assertThat(subContract).isEqualTo(4);
    // Spot-check Excavator row
    UnitRateRow exc = rows.stream().filter(r -> "Excavator (JCB 210)".equals(r.description())).findFirst().orElseThrow();
    assertThat(exc.budgetedRate()).isEqualByComparingTo("1800");
    assertThat(exc.actualRate()).isEqualByComparingTo("1850");
    assertThat(exc.unit()).isEqualTo("Hour");
  }

  @Test
  void supervisor_daily_rpt_reads_20_rows() {
    List<DprRow> rows = reader.withWorkbook(reader::readSupervisorDailyReport);
    assertThat(rows).hasSize(20);
    DprRow first = rows.get(0);
    assertThat(first.activity()).isEqualTo("Earthwork – Excavation");
    assertThat(first.qtyExecuted()).isEqualByComparingTo("850");
    // "145+000 to 145+500" → [145000, 145500]
    assertThat(first.chainageFromM()).isEqualTo(145000L);
    assertThat(first.chainageToM()).isEqualTo(145500L);
    assertThat(first.supervisor()).isEqualTo("R.K. Verma");
  }

  @Test
  void material_consumption_reads_16_rows() {
    List<MaterialConsumptionRow> rows = reader.withWorkbook(reader::readMaterialConsumption);
    assertThat(rows).hasSize(16);
    MaterialConsumptionRow first = rows.get(0);
    assertThat(first.materialName()).isEqualTo("Aggregate (20mm)");
    assertThat(first.openingStock()).isEqualByComparingTo("120");
    assertThat(first.received()).isEqualByComparingTo("50");
    assertThat(first.consumed()).isEqualByComparingTo("45");
    assertThat(first.closingStock()).isEqualByComparingTo("125");
  }

  @Test
  void resource_deployment_reads_rows_with_both_types() {
    List<ResourceDeploymentRow> rows = reader.withWorkbook(reader::readResourceDeployment);
    assertThat(rows).isNotEmpty();
    assertThat(rows).anyMatch(r -> r.type() == DeploymentResourceType.MANPOWER);
    assertThat(rows).anyMatch(r -> r.type() == DeploymentResourceType.EQUIPMENT);
  }

  @Test
  void weather_reads_at_least_10_rows() {
    List<WeatherRow> rows = reader.withWorkbook(reader::readDailyWeather);
    assertThat(rows.size()).isGreaterThanOrEqualTo(10);
    assertThat(rows).allMatch(r -> r.logDate() != null);
  }

  @Test
  void next_day_plans_reads_rows_with_chainage_parsed() {
    List<NextDayPlanRow> rows = reader.withWorkbook(reader::readNextDayPlans);
    assertThat(rows).isNotEmpty();
    // First row chainage "145+500–146+000" should parse
    NextDayPlanRow first = rows.get(0);
    assertThat(first.chainageFromM()).isEqualTo(145500L);
    assertThat(first.chainageToM()).isEqualTo(146000L);
  }
}
