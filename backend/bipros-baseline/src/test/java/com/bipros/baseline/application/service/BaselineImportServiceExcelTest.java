package com.bipros.baseline.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bipros.baseline.application.dto.BaselineImportResult;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.importexport.application.dto.ApplySummary;
import com.bipros.importexport.application.dto.ImportPreview;
import com.bipros.importexport.application.dto.ResourceApplyResult;
import com.bipros.importexport.application.service.RoleResourcePlanApplier;
import com.bipros.importexport.application.service.ScheduleApplyService;
import com.bipros.importexport.domain.model.ImportExportFormat;
import com.bipros.importexport.infrastructure.parser.ExcelScheduleParser;
import com.bipros.importexport.infrastructure.parser.FormatDetector;
import com.bipros.importexport.infrastructure.parser.XerParser;
import com.bipros.importexport.infrastructure.template.ExcelTemplateGenerator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class BaselineImportServiceExcelTest {

  private XerParser xerParser;
  private ExcelScheduleParser excelScheduleParser;
  private ExcelTemplateGenerator excelTemplateGenerator;
  private ScheduleApplyService scheduleApplyService;
  private RoleResourcePlanApplier roleResourcePlanApplier;
  private BaselineService baselineService;
  private FormatDetector formatDetector;
  private BaselineImportService importService;

  private final UUID projectId = UUID.randomUUID();
  private final byte[] content = "excel-bytes".getBytes(StandardCharsets.UTF_8);
  private Map<String, List<Map<String, String>>> tables;

  @BeforeEach
  void setUp() {
    xerParser = mock(XerParser.class);
    excelScheduleParser = mock(ExcelScheduleParser.class);
    excelTemplateGenerator = mock(ExcelTemplateGenerator.class);
    scheduleApplyService = mock(ScheduleApplyService.class);
    roleResourcePlanApplier = mock(RoleResourcePlanApplier.class);
    baselineService = mock(BaselineService.class);
    formatDetector = mock(FormatDetector.class);
    importService = new BaselineImportService(
        xerParser, excelScheduleParser, excelTemplateGenerator, scheduleApplyService,
        roleResourcePlanApplier, baselineService, formatDetector);

    tables = Map.of("TASK", List.of(Map.of("task_code", "A1")));
    when(excelScheduleParser.parse(content)).thenReturn(tables);
    when(formatDetector.detect(content)).thenReturn(ImportExportFormat.EXCEL);
  }

  @Test
  void previewWithExcelFormatUsesExcelParserNotXerParser() {
    ImportPreview basePreview = new ImportPreview(1, 0, 1, 0, 0, 0, 0, null, null, null,
        List.of(), List.of(), null);
    when(scheduleApplyService.preview(projectId, tables)).thenReturn(basePreview);
    ResourceApplyResult resourceResult =
        new ResourceApplyResult(0, 0, 0, 0, 0, 0, 0, 0, List.of());
    when(roleResourcePlanApplier.preview(projectId, tables)).thenReturn(resourceResult);

    importService.preview(projectId, content, ImportExportFormat.EXCEL);

    verify(excelScheduleParser).parse(content);
    verifyNoInteractions(xerParser);
  }

  @Test
  void previewWithExcelFormatReturnsResourceSummaryFromApplier() {
    ImportPreview basePreview = new ImportPreview(1, 0, 1, 0, 0, 0, 0, null, null, null,
        List.of(), List.of(), null);
    when(scheduleApplyService.preview(projectId, tables)).thenReturn(basePreview);
    ResourceApplyResult resourceResult =
        new ResourceApplyResult(2, 1, 3, 2, 0, 0, 1, 1, List.of("warn"));
    when(roleResourcePlanApplier.preview(projectId, tables)).thenReturn(resourceResult);

    ImportPreview result = importService.preview(projectId, content, ImportExportFormat.EXCEL);

    assertThat(result.resources()).isEqualTo(resourceResult);
    // and the rest of the fields are carried over unchanged from ScheduleApplyService.preview
    assertThat(result.activitiesInFile()).isEqualTo(1);
  }

  @Test
  void importBaselineWithExcelFormatCallsScheduleApplyThenResourceApplyThenCreateBaseline() {
    ApplySummary summary = new ApplySummary(1, 0, 0, 0, 0, 0, List.of());
    when(scheduleApplyService.apply(projectId, tables)).thenReturn(summary);
    ResourceApplyResult resourceResult =
        new ResourceApplyResult(0, 0, 0, 0, 0, 0, 0, 0, List.of());
    when(roleResourcePlanApplier.apply(projectId, tables)).thenReturn(resourceResult);
    BaselineResponse baselineResponse = new BaselineResponse(UUID.randomUUID(), projectId, "BL-1",
        "desc", BaselineType.PRIMARY, null, true, null, null, null, null, null, null, null);
    when(baselineService.createBaseline(eq(projectId), any(CreateBaselineRequest.class)))
        .thenReturn(baselineResponse);

    BaselineImportResult result = importService.importBaseline(
        projectId, content, ImportExportFormat.EXCEL, "BL-1", BaselineType.PRIMARY, "desc");

    InOrder inOrder = Mockito.inOrder(scheduleApplyService, roleResourcePlanApplier, baselineService);
    inOrder.verify(scheduleApplyService).apply(projectId, tables);
    inOrder.verify(roleResourcePlanApplier).apply(projectId, tables);
    inOrder.verify(baselineService).createBaseline(eq(projectId), any(CreateBaselineRequest.class));

    assertThat(result.baseline()).isEqualTo(baselineResponse);
    assertThat(result.summary()).isEqualTo(summary);
    verifyNoInteractions(xerParser);
  }

  @Test
  void previewWithZeroActivities_throwsNoActivitiesInFile() {
    when(excelScheduleParser.parse(content)).thenReturn(Map.of());

    assertThatThrownBy(() -> importService.preview(projectId, content, ImportExportFormat.EXCEL))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((BusinessRuleException) ex).getRuleCode())
            .isEqualTo("NO_ACTIVITIES_IN_FILE"));

    verifyNoInteractions(scheduleApplyService);
  }

  @Test
  void previewDetectsXerWhenExcelSelected() {
    Map<String, List<Map<String, String>>> xerTables =
        Map.of("TASK", List.of(Map.of("task_code", "X1")));
    when(formatDetector.detect(content)).thenReturn(ImportExportFormat.XER);
    when(xerParser.parse(new String(content, StandardCharsets.UTF_8))).thenReturn(xerTables);
    ImportPreview basePreview = new ImportPreview(1, 0, 1, 0, 0, 0, 0, null, null, null,
        List.of(), List.of(), null);
    when(scheduleApplyService.preview(projectId, xerTables)).thenReturn(basePreview);
    ResourceApplyResult resourceResult =
        new ResourceApplyResult(0, 0, 0, 0, 0, 0, 0, 0, List.of());
    when(roleResourcePlanApplier.preview(projectId, xerTables)).thenReturn(resourceResult);

    ImportPreview result = importService.preview(projectId, content, ImportExportFormat.EXCEL);

    verify(xerParser).parse(new String(content, StandardCharsets.UTF_8));
    verifyNoInteractions(excelScheduleParser);
    assertThat(result.warnings()).anyMatch(w -> w.contains("XER"));
  }
}
