package com.bipros.importexport.infrastructure.export;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExcelExporter {

  private final ProjectRepository projectRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final WbsNodeRepository wbsNodeRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;

  public byte[] export(UUID projectId) throws Exception {
    Workbook workbook = new XSSFWorkbook();

    try {
      var project = projectRepository.findById(projectId)
          .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

      // Create sheets
      createActivitiesSheet(workbook, projectId);
      createRelationshipsSheet(workbook, projectId);
      createWbsSheet(workbook, projectId);
      createResourcesSheet(workbook);
      createResourceAssignmentsSheet(workbook, projectId);
      createCostsSheet(workbook, projectId);

      // Write to byte array
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      byte[] result = baos.toByteArray();

      log.info("Excel export completed for project: {}", projectId);
      return result;
    } finally {
      workbook.close();
    }
  }

  private void createActivitiesSheet(Workbook workbook, UUID projectId) {
    Sheet sheet = workbook.createSheet("Activities");
    CellStyle headerStyle = createHeaderStyle(workbook);
    CellStyle dateStyle = createDateStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Code", "Name", "Type", "Duration (hrs)", "Start Date", "Finish Date", "Status", "% Complete", "Float"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<Activity> activities = activityRepository.findByProjectId(projectId);
    int rowNum = 1;
    for (Activity activity : activities) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(activity.getCode());
      row.createCell(1).setCellValue(activity.getName());
      row.createCell(2).setCellValue(activity.getActivityType() != null ? activity.getActivityType().toString() : "");
      row.createCell(3).setCellValue(activity.getRemainingDuration() != null ? activity.getRemainingDuration() : 0.0);

      Cell startCell = row.createCell(4);
      if (activity.getPlannedStartDate() != null) {
        startCell.setCellValue(activity.getPlannedStartDate().toString());
        startCell.setCellStyle(dateStyle);
      }

      Cell finishCell = row.createCell(5);
      if (activity.getPlannedFinishDate() != null) {
        finishCell.setCellValue(activity.getPlannedFinishDate().toString());
        finishCell.setCellStyle(dateStyle);
      }

      row.createCell(6).setCellValue("In Progress"); // Placeholder status
      row.createCell(7).setCellValue(activity.getPercentComplete() != null ? activity.getPercentComplete() * 100 : 0.0);
      row.createCell(8).setCellValue(0); // Float - calculated later
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createRelationshipsSheet(Workbook workbook, UUID projectId) {
    Sheet sheet = workbook.createSheet("Relationships");
    CellStyle headerStyle = createHeaderStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Predecessor", "Successor", "Type", "Lag (hrs)"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<ActivityRelationship> relationships = activityRelationshipRepository.findByProjectId(projectId);
    int rowNum = 1;
    for (ActivityRelationship rel : relationships) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(rel.getPredecessorActivityId().toString());
      row.createCell(1).setCellValue(rel.getSuccessorActivityId().toString());
      row.createCell(2).setCellValue(rel.getRelationshipType() != null ? rel.getRelationshipType().toString() : "FS");
      row.createCell(3).setCellValue(rel.getLag() != null ? rel.getLag() : 0.0);
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createWbsSheet(Workbook workbook, UUID projectId) {
    Sheet sheet = workbook.createSheet("WBS");
    CellStyle headerStyle = createHeaderStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Code", "Name", "Parent"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
    int rowNum = 1;
    for (WbsNode wbs : wbsNodes) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(wbs.getCode());
      row.createCell(1).setCellValue(wbs.getName());
      row.createCell(2).setCellValue(wbs.getParentId() != null ? wbs.getParentId().toString() : "");
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createResourcesSheet(Workbook workbook) {
    Sheet sheet = workbook.createSheet("Resources");
    CellStyle headerStyle = createHeaderStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Code", "Name", "Type", "Max Units"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<Resource> resources = resourceRepository.findAll();
    int rowNum = 1;
    for (Resource resource : resources) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(resource.getCode());
      row.createCell(1).setCellValue(resource.getName());
      row.createCell(2).setCellValue(resource.getResourceType() != null ? resource.getResourceType().toString() : "");
      row.createCell(3).setCellValue(100); // Default max units
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createResourceAssignmentsSheet(Workbook workbook, UUID projectId) {
    Sheet sheet = workbook.createSheet("Assignments");
    CellStyle headerStyle = createHeaderStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Activity Code", "Activity Name", "Resource Code", "Resource Name", "Planned Units", "Actual Units"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<ResourceAssignment> assignments = resourceAssignmentRepository.findByProjectId(projectId);
    int rowNum = 1;
    for (ResourceAssignment assignment : assignments) {
      Row row = sheet.createRow(rowNum++);

      // Get activity and resource details for display
      var activity = activityRepository.findById(assignment.getActivityId());
      var resource = resourceRepository.findById(assignment.getResourceId());

      if (activity.isPresent()) {
        row.createCell(0).setCellValue(activity.get().getCode());
        row.createCell(1).setCellValue(activity.get().getName());
      }

      if (resource.isPresent()) {
        row.createCell(2).setCellValue(resource.get().getCode());
        row.createCell(3).setCellValue(resource.get().getName());
      }

      row.createCell(4).setCellValue(assignment.getPlannedUnits() != null ? assignment.getPlannedUnits() : 0.0);
      row.createCell(5).setCellValue(assignment.getActualUnits() != null ? assignment.getActualUnits() : 0.0);
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private void createCostsSheet(Workbook workbook, UUID projectId) {
    Sheet sheet = workbook.createSheet("Costs");
    CellStyle headerStyle = createHeaderStyle(workbook);

    // Headers
    Row headerRow = sheet.createRow(0);
    String[] headers = {"WBS", "Budget", "Actual", "Remaining"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data
    List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
    int rowNum = 1;
    for (WbsNode wbs : wbsNodes) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(wbs.getCode());
      row.createCell(1).setCellValue(0.0); // Budget - placeholder
      row.createCell(2).setCellValue(0.0); // Actual - placeholder
      row.createCell(3).setCellValue(0.0); // Remaining - placeholder
    }

    // Auto-fit columns
    for (int i = 0; i < headers.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    return style;
  }

  private CellStyle createDateStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
    return style;
  }
}
