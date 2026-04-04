package com.bipros.importexport.infrastructure.export;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvExporter {

  private final ProjectRepository projectRepository;
  private final ActivityRepository activityRepository;

  public String export(UUID projectId) throws Exception {
    var project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

    StringBuilder csv = new StringBuilder();

    // Header
    csv.append("Code,Name,Type,Duration (hrs),Start Date,Finish Date,Status,% Complete,Float\n");

    // Data
    List<Activity> activities = activityRepository.findByProjectId(projectId);
    for (Activity activity : activities) {
      csv.append(escapeCsvField(activity.getCode())).append(",");
      csv.append(escapeCsvField(activity.getName())).append(",");
      csv.append(escapeCsvField(activity.getActivityType() != null ? activity.getActivityType().toString() : "")).append(",");
      csv.append(activity.getRemainingDuration() != null ? activity.getRemainingDuration() : 0.0).append(",");
      csv.append(activity.getPlannedStartDate() != null ? activity.getPlannedStartDate() : "").append(",");
      csv.append(activity.getPlannedFinishDate() != null ? activity.getPlannedFinishDate() : "").append(",");
      csv.append("In Progress").append(","); // Status placeholder
      csv.append(activity.getPercentComplete() != null ? activity.getPercentComplete() * 100 : 0.0).append(",");
      csv.append("0").append("\n"); // Float - calculated later
    }

    log.info("CSV export completed for project: {}", projectId);
    return csv.toString();
  }

  private String escapeCsvField(String field) {
    if (field == null || field.isEmpty()) {
      return "";
    }
    // Escape double quotes and wrap field in quotes if it contains special characters
    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
      return "\"" + field.replace("\"", "\"\"") + "\"";
    }
    return field;
  }
}
