package com.bipros.importexport.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.DurationType;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class XerImportMapper {

  private final ProjectRepository projectRepository;
  private final WbsNodeRepository wbsNodeRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final CalendarRepository calendarRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final ResourceTypeRepository resourceTypeRepository;
  private final ResourceRoleRepository resourceRoleRepository;

  private Map<String, UUID> xerIdToUuidMap;
  private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public XerImportMapper(
      ProjectRepository projectRepository,
      WbsNodeRepository wbsNodeRepository,
      ActivityRepository activityRepository,
      ActivityRelationshipRepository activityRelationshipRepository,
      CalendarRepository calendarRepository,
      ResourceRepository resourceRepository,
      ResourceAssignmentRepository resourceAssignmentRepository,
      ResourceTypeRepository resourceTypeRepository,
      ResourceRoleRepository resourceRoleRepository) {
    this.projectRepository = projectRepository;
    this.wbsNodeRepository = wbsNodeRepository;
    this.activityRepository = activityRepository;
    this.activityRelationshipRepository = activityRelationshipRepository;
    this.calendarRepository = calendarRepository;
    this.resourceRepository = resourceRepository;
    this.resourceAssignmentRepository = resourceAssignmentRepository;
    this.resourceTypeRepository = resourceTypeRepository;
    this.resourceRoleRepository = resourceRoleRepository;
    this.xerIdToUuidMap = new HashMap<>();
  }

  /**
   * Maps XER parsed data to domain entities and persists them in correct order.
   *
   * @param xerData parsed XER tables (table_name -> list of rows)
   * @return the created project ID
   */
  public UUID importProject(Map<String, List<Map<String, String>>> xerData) {
    this.xerIdToUuidMap = new HashMap<>();

    UUID projectId = importProjects(xerData.get("PROJECT"));
    if (projectId == null) {
      throw new IllegalArgumentException("No PROJECT table found in XER data");
    }

    importCalendars(xerData.get("CALENDAR"), projectId);
    importResources(xerData.get("RSRC"));
    importWbsNodes(xerData.get("PROJWBS"), projectId);
    importActivities(xerData.get("TASK"), projectId);
    importActivityRelationships(xerData.get("TASKPRED"), projectId);
    importResourceAssignments(xerData.get("TASKRSRC"), projectId);

    return projectId;
  }

  private UUID importProjects(List<Map<String, String>> rows) {
    if (rows == null || rows.isEmpty()) {
      return null;
    }

    Map<String, String> row = rows.get(0);
    Project project = new Project();

    String projId = row.get("proj_id");
    project.setCode(row.getOrDefault("proj_short_name", "P" + System.nanoTime()).substring(0, Math.min(20, row.getOrDefault("proj_short_name", "P").length())));
    project.setName(row.getOrDefault("proj_name", row.getOrDefault("proj_short_name", "Imported Project")));
    project.setStatus(ProjectStatus.PLANNED);
    project.setEpsNodeId(UUID.randomUUID());

    project.setPlannedStartDate(parseDate(row.get("plan_start_date")));
    project.setPlannedFinishDate(parseDate(row.get("plan_end_date")));

    Project saved = projectRepository.save(project);
    xerIdToUuidMap.put("PROJECT:" + projId, saved.getId());

    log.info("Imported project: {} (UUID: {})", saved.getCode(), saved.getId());
    return saved.getId();
  }

  private void importCalendars(List<Map<String, String>> rows, UUID projectId) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      Calendar calendar = new Calendar();
      calendar.setName(row.getOrDefault("clndr_name", "Calendar"));
      calendar.setCalendarType(CalendarType.PROJECT);
      calendar.setProjectId(projectId);

      String dayHours = row.get("day_hr_cnt");
      if (dayHours != null && !dayHours.isEmpty()) {
        calendar.setDescription("Day hours: " + dayHours);
      }

      Calendar saved = calendarRepository.save(calendar);
      xerIdToUuidMap.put("CALENDAR:" + row.get("clndr_id"), saved.getId());

      log.debug("Imported calendar: {} for project {}", saved.getName(), projectId);
    }
  }

  private void importWbsNodes(List<Map<String, String>> rows, UUID projectId) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      WbsNode wbs = new WbsNode();
      String wbsId = row.get("wbs_id");
      String parentWbsId = row.get("parent_wbs_id");

      wbs.setCode(row.getOrDefault("wbs_short_name", wbsId));
      wbs.setName(row.getOrDefault("wbs_name", "WBS Node"));
      wbs.setProjectId(projectId);

      if (parentWbsId != null && !parentWbsId.isEmpty()) {
        UUID parentId = xerIdToUuidMap.get("WBS:" + parentWbsId);
        if (parentId != null) {
          wbs.setParentId(parentId);
        }
      }

      WbsNode saved = wbsNodeRepository.save(wbs);
      xerIdToUuidMap.put("WBS:" + wbsId, saved.getId());

      log.debug("Imported WBS node: {} for project {}", saved.getCode(), projectId);
    }
  }

  private void importActivities(List<Map<String, String>> rows, UUID projectId) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      Activity activity = new Activity();
      String taskId = row.get("task_id");

      activity.setCode(row.getOrDefault("task_code", taskId));
      activity.setName(row.getOrDefault("task_name", "Task"));
      activity.setProjectId(projectId);
      activity.setActivityType(ActivityType.TASK_DEPENDENT);
      activity.setDurationType(DurationType.FIXED_DURATION_AND_UNITS);
      activity.setPercentCompleteType(PercentCompleteType.DURATION);

      String taskType = row.get("task_type");
      if ("MILESTONE".equalsIgnoreCase(taskType)) {
        activity.setActivityType(ActivityType.FINISH_MILESTONE);
      }

      activity.setPlannedStartDate(parseDate(row.get("target_start_date")));
      activity.setPlannedFinishDate(parseDate(row.get("target_end_date")));
      activity.setActualStartDate(parseDate(row.get("act_start_date")));
      activity.setActualFinishDate(parseDate(row.get("act_end_date")));

      String remainingDuration = row.get("remain_drtn_hr_cnt");
      if (remainingDuration != null && !remainingDuration.isEmpty()) {
        try {
          activity.setRemainingDuration(Double.parseDouble(remainingDuration));
        } catch (NumberFormatException e) {
          activity.setRemainingDuration(0.0);
        }
      }

      String physComplete = row.get("phys_complete_pct");
      if (physComplete != null && !physComplete.isEmpty()) {
        try {
          double pct = Double.parseDouble(physComplete);
          activity.setPercentComplete(pct / 100.0);
          activity.setPhysicalPercentComplete(pct / 100.0);
        } catch (NumberFormatException e) {
          activity.setPercentComplete(0.0);
        }
      }

      String wbsId = row.get("wbs_id");
      UUID wbsNodeId = xerIdToUuidMap.get("WBS:" + wbsId);
      if (wbsNodeId == null) {
        UUID rootWbs = createRootWbsIfNeeded(projectId);
        wbsNodeId = rootWbs;
      }
      activity.setWbsNodeId(wbsNodeId);

      Activity saved = activityRepository.save(activity);
      xerIdToUuidMap.put("TASK:" + taskId, saved.getId());

      log.debug("Imported activity: {} for project {}", saved.getCode(), projectId);
    }
  }

  private void importActivityRelationships(List<Map<String, String>> rows, UUID projectId) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      String predTaskId = row.get("pred_task_id");
      String taskId = row.get("task_id");

      UUID predecessorId = xerIdToUuidMap.get("TASK:" + predTaskId);
      UUID successorId = xerIdToUuidMap.get("TASK:" + taskId);

      if (predecessorId == null || successorId == null) {
        log.warn("Skipping relationship: predecessor {} or successor {} not found", predTaskId, taskId);
        continue;
      }

      ActivityRelationship relationship = new ActivityRelationship();
      relationship.setPredecessorActivityId(predecessorId);
      relationship.setSuccessorActivityId(successorId);
      relationship.setProjectId(projectId);

      String predType = row.get("pred_type");
      relationship.setRelationshipType(mapRelationshipType(predType));

      String lagHours = row.get("lag_hr_cnt");
      if (lagHours != null && !lagHours.isEmpty()) {
        try {
          relationship.setLag(Double.parseDouble(lagHours));
        } catch (NumberFormatException e) {
          relationship.setLag(0.0);
        }
      }

      activityRelationshipRepository.save(relationship);
      log.debug("Imported relationship: {} -> {}", predTaskId, taskId);
    }
  }

  private void importResources(List<Map<String, String>> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      Resource resource = new Resource();
      String rsrcId = row.get("rsrc_id");

      resource.setCode(row.getOrDefault("rsrc_short_name", rsrcId).substring(0, Math.min(50, row.getOrDefault("rsrc_short_name", rsrcId).length())));
      resource.setName(row.getOrDefault("rsrc_name", "Resource"));

      String rsrcType = row.get("rsrc_type");
      String typeCode = mapResourceTypeCode(rsrcType);
      ResourceType type = resourceTypeRepository.findByCode(typeCode)
          .orElseThrow(() -> new IllegalStateException(
              "Required ResourceType " + typeCode + " has not been seeded"));
      resource.setResourceType(type);
      resource.setRole(ensureDefaultRoleForImport(type));

      Resource saved = resourceRepository.save(resource);
      xerIdToUuidMap.put("RSRC:" + rsrcId, saved.getId());

      log.debug("Imported resource: {}", saved.getCode());
    }
  }

  private void importResourceAssignments(List<Map<String, String>> rows, UUID projectId) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (Map<String, String> row : rows) {
      String taskId = row.get("task_id");
      String rsrcId = row.get("rsrc_id");

      UUID activityId = xerIdToUuidMap.get("TASK:" + taskId);
      UUID resourceId = xerIdToUuidMap.get("RSRC:" + rsrcId);

      if (activityId == null || resourceId == null) {
        log.warn("Skipping resource assignment: activity {} or resource {} not found", taskId, rsrcId);
        continue;
      }

      ResourceAssignment assignment = new ResourceAssignment();
      assignment.setActivityId(activityId);
      assignment.setResourceId(resourceId);
      assignment.setProjectId(projectId);

      String targetQty = row.get("target_qty");
      if (targetQty != null && !targetQty.isEmpty()) {
        try {
          assignment.setPlannedUnits(Double.parseDouble(targetQty));
        } catch (NumberFormatException e) {
          assignment.setPlannedUnits(0.0);
        }
      }

      String actualQty = row.get("act_reg_qty");
      if (actualQty != null && !actualQty.isEmpty()) {
        try {
          assignment.setActualUnits(Double.parseDouble(actualQty));
        } catch (NumberFormatException e) {
          assignment.setActualUnits(0.0);
        }
      }

      resourceAssignmentRepository.save(assignment);
      log.debug("Imported resource assignment: activity {} -> resource {}", taskId, rsrcId);
    }
  }

  private RelationshipType mapRelationshipType(String xerType) {
    if (xerType == null) {
      return RelationshipType.FINISH_TO_START;
    }

    return switch (xerType.toUpperCase()) {
      case "FS", "PR_FS" -> RelationshipType.FINISH_TO_START;
      case "FF", "PR_FF" -> RelationshipType.FINISH_TO_FINISH;
      case "SS", "PR_SS" -> RelationshipType.START_TO_START;
      case "SF", "PR_SF" -> RelationshipType.START_TO_FINISH;
      default -> RelationshipType.FINISH_TO_START;
    };
  }

  private String mapResourceTypeCode(String xerType) {
    if (xerType == null) {
      return "LABOR";
    }

    return switch (xerType.toUpperCase()) {
      case "LABOR", "L" -> "LABOR";
      case "MATERIAL", "M" -> "MATERIAL";
      case "EQUIPMENT", "E", "NONLABOR" -> "EQUIPMENT";
      default -> "LABOR";
    };
  }

  private ResourceRole ensureDefaultRoleForImport(ResourceType type) {
    String code = "IMPORTED-" + type.getCode();
    return resourceRoleRepository.findByCode(code)
        .orElseGet(() -> {
          ResourceRole role = new ResourceRole();
          role.setCode(code);
          role.setName("Imported " + type.getName());
          role.setResourceType(type);
          role.setActive(true);
          role.setSortOrder(999);
          return resourceRoleRepository.save(role);
        });
  }

  private LocalDate parseDate(String dateStr) {
    if (dateStr == null || dateStr.isEmpty()) {
      return null;
    }

    try {
      return LocalDate.parse(dateStr, dateFormatter);
    } catch (Exception e) {
      log.warn("Could not parse date: {}", dateStr);
      return null;
    }
  }

  private UUID createRootWbsIfNeeded(UUID projectId) {
    WbsNode root = new WbsNode();
    root.setCode("ROOT");
    root.setName("Project");
    root.setProjectId(projectId);
    root.setSortOrder(0);
    return wbsNodeRepository.save(root).getId();
  }
}
