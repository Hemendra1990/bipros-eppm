package com.bipros.baseline.application.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates a baseline import: parse a schedule file, apply it onto the project's live
 * activities/WBS/relationships/assignments, then snapshot the result as a Baseline. Phase 1
 * supports XER and Excel; other {@link ImportExportFormat} values (P6 XML) are rejected until
 * that parser lands.
 */
@Service
@RequiredArgsConstructor
public class BaselineImportService {

  private final XerParser xerParser;
  private final ExcelScheduleParser excelScheduleParser;
  private final ExcelTemplateGenerator excelTemplateGenerator;
  private final ScheduleApplyService scheduleApplyService;
  private final RoleResourcePlanApplier roleResourcePlanApplier;
  private final BaselineService baselineService;
  private final FormatDetector formatDetector;

  @Transactional(readOnly = true)
  public ImportPreview preview(UUID projectId, byte[] content, ImportExportFormat format) {
    ImportExportFormat eff = effectiveFormat(content, format);
    Map<String, List<Map<String, String>>> tables = parse(content, eff);
    ImportPreview preview = scheduleApplyService.preview(projectId, tables);
    ResourceApplyResult resources = roleResourcePlanApplier.preview(projectId, tables);
    preview = preview.withResources(resources);
    if (formatDetector.detect(content) != null && eff != format) {
      preview = preview.withPrependedWarning("You selected " + format + ", but the file looks like "
          + eff + " — reading it as " + eff + ".");
    }
    return preview;
  }

  @Transactional
  public BaselineImportResult importBaseline(UUID projectId, byte[] content, ImportExportFormat format,
                                             String name, BaselineType type, String description) {
    Map<String, List<Map<String, String>>> tables = parse(content, effectiveFormat(content, format));
    ApplySummary summary = scheduleApplyService.apply(projectId, tables);
    roleResourcePlanApplier.apply(projectId, tables);
    BaselineResponse baseline = baselineService.createBaseline(projectId,
        new CreateBaselineRequest(name, type != null ? type : BaselineType.PRIMARY, description));
    return new BaselineImportResult(baseline, summary);
  }

  /**
   * Phase 1: downloadable Excel baseline import template. Only {@code EXCEL} is supported; other
   * formats don't have a template to offer yet.
   */
  public byte[] template(ImportExportFormat format) {
    return switch (format) {
      case EXCEL -> excelTemplateGenerator.generate();
      default -> throw new BusinessRuleException("TEMPLATE_NOT_AVAILABLE",
          "No import template is available for format " + format + ".");
    };
  }

  public String templateFilename(ImportExportFormat format) {
    return switch (format) {
      case EXCEL -> "baseline-import-template.xlsx";
      default -> throw new BusinessRuleException("TEMPLATE_NOT_AVAILABLE",
          "No import template is available for format " + format + ".");
    };
  }

  public String templateContentType(ImportExportFormat format) {
    return switch (format) {
      case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      default -> throw new BusinessRuleException("TEMPLATE_NOT_AVAILABLE",
          "No import template is available for format " + format + ".");
    };
  }

  private ImportExportFormat effectiveFormat(byte[] content, ImportExportFormat requested) {
    ImportExportFormat detected = formatDetector.detect(content);
    return detected != null ? detected : requested;
  }

  private Map<String, List<Map<String, String>>> parse(byte[] content, ImportExportFormat format) {
    Map<String, List<Map<String, String>>> tables = switch (format) {
      case XER -> xerParser.parse(new String(content, StandardCharsets.UTF_8));
      case EXCEL -> excelScheduleParser.parse(content);
      default -> throw new BusinessRuleException("UNSUPPORTED_IMPORT_FORMAT",
          "Only XER and Excel import is supported currently; P6 XML is coming soon.");
    };
    if (tables.getOrDefault("TASK", List.of()).isEmpty()) {
      throw new BusinessRuleException("NO_ACTIVITIES_IN_FILE",
          "No activities were found in this file — nothing would be imported. Check you selected "
              + "the right file and used the template layout (the 'Activities' sheet is required).");
    }
    return tables;
  }
}
