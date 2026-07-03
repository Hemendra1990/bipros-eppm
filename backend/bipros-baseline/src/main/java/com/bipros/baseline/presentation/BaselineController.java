package com.bipros.baseline.presentation;

import com.bipros.baseline.application.dto.BaselineDetailResponse;
import com.bipros.baseline.application.dto.BaselineImportResult;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.application.dto.ScheduleComparisonResponse;
import com.bipros.baseline.application.dto.UpdateBaselineRequest;
import com.bipros.baseline.application.service.BaselineImportService;
import com.bipros.baseline.application.service.BaselineService;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.common.dto.ApiResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.importexport.application.dto.ImportPreview;
import com.bipros.importexport.domain.model.ImportExportFormat;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/projects/{projectId}/baselines")
@RequiredArgsConstructor
public class BaselineController {

  private final BaselineService baselineService;
  private final BaselineImportService baselineImportService;

  private static final long MAX_IMPORT_BYTES = 50L * 1024 * 1024;

  @PostMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.CREATE')")
  public ResponseEntity<ApiResponse<BaselineResponse>> createBaseline(
      @PathVariable UUID projectId, @Valid @RequestBody CreateBaselineRequest request) {
    BaselineResponse response = baselineService.createBaseline(projectId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.READ')")
  public ResponseEntity<ApiResponse<List<BaselineResponse>>> listBaselines(
      @PathVariable UUID projectId) {
    List<BaselineResponse> response = baselineService.listBaselines(projectId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{baselineId}")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.READ')")
  public ResponseEntity<ApiResponse<BaselineDetailResponse>> getBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    BaselineDetailResponse response = baselineService.getBaseline(baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{baselineId}")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.DELETE')")
  public ResponseEntity<Void> deleteBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    baselineService.deleteBaseline(baselineId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{baselineId}/variance")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.READ')")
  public ResponseEntity<ApiResponse<List<BaselineVarianceResponse>>> getVariance(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    List<BaselineVarianceResponse> response =
        baselineService.getVariance(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{baselineId}/schedule-comparison")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.READ')")
  public ResponseEntity<ApiResponse<List<ScheduleComparisonResponse>>> getScheduleComparison(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    List<ScheduleComparisonResponse> response =
        baselineService.getScheduleComparison(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * Legacy "activate" — maps to assigning the baseline to the PRIMARY slot. Kept for one
   * release while UI clients migrate to the explicit slot endpoints below.
   */
  @PostMapping("/{baselineId}/activate")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.UPDATE')")
  public ResponseEntity<ApiResponse<BaselineResponse>> activateBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    BaselineResponse response = baselineService.setActiveBaseline(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * Phase 3: assign a baseline to one of the three P6 slots ({@code PRIMARY},
   * {@code SECONDARY}, {@code TERTIARY}). Slots are independent — assigning a baseline as
   * SECONDARY does not unset whatever is in PRIMARY.
   */
  @PostMapping("/{baselineId}/assign/{slot}")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.UPDATE')")
  public ResponseEntity<ApiResponse<BaselineResponse>> assignBaselineToSlot(
      @PathVariable UUID projectId,
      @PathVariable UUID baselineId,
      @PathVariable BaselineService.BaselineSlot slot) {
    BaselineResponse response = baselineService.assignBaselineToSlot(projectId, baselineId, slot);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * Phase 3: detach whichever baseline currently occupies the given slot. Other slots are
   * unaffected. Idempotent — clearing an already-empty slot is a no-op.
   */
  @DeleteMapping("/slots/{slot}")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.UPDATE')")
  public ResponseEntity<ApiResponse<Void>> clearBaselineSlot(
      @PathVariable UUID projectId,
      @PathVariable BaselineService.BaselineSlot slot) {
    baselineService.clearBaselineSlot(projectId, slot);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  /**
   * Phase 4.1: P6-style "Restore Baseline" — overwrite the live project's planned dates,
   * durations, and relationships with the snapshot. Actuals are preserved. Destructive — UI
   * must confirm before invoking.
   */
  @PostMapping("/{baselineId}/restore")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.UPDATE')")
  public ResponseEntity<ApiResponse<BaselineResponse>> restoreBaseline(
      @PathVariable UUID projectId, @PathVariable UUID baselineId) {
    BaselineResponse response = baselineService.restoreBaseline(projectId, baselineId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * Phase 4.2: Selective Update Baseline — refresh only the activities/fields the planner
   * picks via the filter spec.
   */
  @PutMapping("/{baselineId}/update")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.UPDATE')")
  public ResponseEntity<ApiResponse<BaselineResponse>> updateBaseline(
      @PathVariable UUID projectId,
      @PathVariable UUID baselineId,
      @RequestBody UpdateBaselineRequest request) {
    BaselineResponse response = baselineService.updateBaseline(projectId, baselineId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * Baseline Import — Phase 1: downloadable Excel template whose sheet headers exactly match
   * what {@code ExcelScheduleParser} reads, so a filled-in template round-trips back through
   * import/preview and import.
   */
  @GetMapping("/import/template")
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.READ')")
  public ResponseEntity<byte[]> downloadImportTemplate(
      @PathVariable UUID projectId,
      @RequestParam("format") ImportExportFormat format) {
    byte[] content = baselineImportService.template(format);
    String filename = baselineImportService.templateFilename(format);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType(baselineImportService.templateContentType(format)))
        .body(content);
  }

  /**
   * Baseline Import (XER) — Phase 1: preview how a schedule file would apply against the
   * project's current activities without writing anything.
   */
  @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.CREATE')")
  public ResponseEntity<ApiResponse<ImportPreview>> previewImport(
      @PathVariable UUID projectId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("format") ImportExportFormat format) throws IOException {
    if (file.getSize() > MAX_IMPORT_BYTES) {
      throw new BusinessRuleException("FILE_TOO_LARGE", "Import file exceeds the 50 MB limit.");
    }
    ImportPreview preview = baselineImportService.preview(projectId, file.getBytes(), format);
    return ResponseEntity.ok(ApiResponse.ok(preview));
  }

  /**
   * Baseline Import (XER) — Phase 1: apply the schedule file onto the project's live activities
   * and capture the result as a new baseline.
   */
  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'BASELINE.CREATE') "
      + "and @projectAccess.hasProjectPermission(#projectId, 'PROJECT.UPDATE')")
  public ResponseEntity<ApiResponse<BaselineImportResult>> importBaseline(
      @PathVariable UUID projectId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("format") ImportExportFormat format,
      @RequestParam("name") String name,
      @RequestParam(value = "type", defaultValue = "PRIMARY") BaselineType type,
      @RequestParam(value = "description", required = false) String description) throws IOException {
    if (file.getSize() > MAX_IMPORT_BYTES) {
      throw new BusinessRuleException("FILE_TOO_LARGE", "Import file exceeds the 50 MB limit.");
    }
    BaselineImportResult result = baselineImportService.importBaseline(
        projectId, file.getBytes(), format, name, type, description);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
  }
}
