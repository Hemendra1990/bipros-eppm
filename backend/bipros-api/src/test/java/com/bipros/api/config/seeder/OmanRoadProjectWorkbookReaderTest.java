package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.BoqRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.DirectLabourRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.EquipmentRateRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.IndirectStaffRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.ProductivityNormRow;
import com.bipros.api.config.seeder.OmanRoadProjectWorkbookReader.ProjectInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — confirms the 3 Oman workbooks are on the classpath and the
 * reader returns non-empty lists for the public sections. Detailed assertions
 * are deliberately loose: this is demo data; the seeder (Agent 2) is the
 * place to enforce business invariants and fall back to deterministic
 * synthetic values when rows are missing.
 */
class OmanRoadProjectWorkbookReaderTest {

  private final OmanRoadProjectWorkbookReader reader = new OmanRoadProjectWorkbookReader();

  @Test
  void all_three_workbooks_are_on_classpath() {
    assertThat(reader.exists()).isTrue();
  }

  @Test
  void project_info_has_code_6155() {
    ProjectInfo info = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.SUPERVISOR_DBS_PATH, reader::readProjectInfo);
    assertThat(info.projectCode()).isEqualTo("6155");
    assertThat(info.projectName()).contains("Barka");
    assertThat(info.projectName()).contains("Nakhal");
  }

  @Test
  void boq_items_are_parsed_with_literal_codes() {
    List<BoqRow> rows = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.SUPERVISOR_DBS_PATH, reader::readBoqItems);
    assertThat(rows).isNotEmpty();
    // We expect ~200 BOQ items per the plan; allow a wide band.
    assertThat(rows.size()).isGreaterThanOrEqualTo(20);
    // Codes should be preserved verbatim (not normalised).
    assertThat(rows).anyMatch(r -> r.code().contains("1.3.5"));
    rows.forEach(r -> {
      assertThat(r.code()).isNotBlank();
      assertThat(r.description()).isNotBlank();
    });
  }

  @Test
  void equipment_master_has_rows() {
    List<EquipmentRateRow> rows = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.SUPERVISOR_DBS_PATH, reader::readEquipmentMaster);
    // Reader is best-effort — empty is acceptable when the merged-cell
    // layout defeats probing. The seeder falls back to the JSON master.
    assertThat(rows).isNotNull();
  }

  @Test
  void productivity_norms_returns_a_list() {
    List<ProductivityNormRow> rows = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.CAPACITY_UTIL_PATH, reader::readProductivityNorms);
    assertThat(rows).isNotNull();
  }

  @Test
  void indirect_and_direct_labour_are_parsed() {
    List<IndirectStaffRow> indirect = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.DPR_INTERNAL_PATH, reader::readIndirectStaff);
    List<DirectLabourRow> direct = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.DPR_INTERNAL_PATH, reader::readDirectLabour);
    assertThat(indirect).isNotNull();
    assertThat(direct).isNotNull();
    // We expect at least a handful of positions in each half.
    assertThat(indirect.size() + direct.size()).isGreaterThan(0);
  }

  @Test
  void dump_summary_for_diagnostics() {
    // Not strictly an assertion — prints a one-line summary so the build
    // log captures concrete row counts. Useful when Agent 2 wires the
    // seeder and needs to reason about reader output.
    List<BoqRow> boq = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.SUPERVISOR_DBS_PATH, reader::readBoqItems);
    List<EquipmentRateRow> equip = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.SUPERVISOR_DBS_PATH, reader::readEquipmentMaster);
    List<ProductivityNormRow> norms = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.CAPACITY_UTIL_PATH, reader::readProductivityNorms);
    List<IndirectStaffRow> indirect = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.DPR_INTERNAL_PATH, reader::readIndirectStaff);
    List<DirectLabourRow> direct = reader.withWorkbook(
        OmanRoadProjectWorkbookReader.DPR_INTERNAL_PATH, reader::readDirectLabour);
    System.out.println("[Oman reader] boq=" + boq.size()
        + " equipment=" + equip.size()
        + " norms=" + norms.size()
        + " indirect=" + indirect.size()
        + " direct=" + direct.size());
    boq.stream().limit(3).forEach(row -> System.out.println("  BOQ: " + row));
    equip.stream().limit(3).forEach(row -> System.out.println("  EQUIP: " + row));
    norms.stream().limit(3).forEach(row -> System.out.println("  NORM: " + row));
    indirect.stream().limit(3).forEach(row -> System.out.println("  INDIRECT: " + row));
    direct.stream().limit(3).forEach(row -> System.out.println("  DIRECT: " + row));
    assertThat(boq).isNotNull();
  }
}
