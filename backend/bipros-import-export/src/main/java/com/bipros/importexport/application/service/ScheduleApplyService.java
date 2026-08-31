package com.bipros.importexport.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.importexport.application.dto.ApplySummary;
import com.bipros.importexport.application.dto.ImportPreview;
import com.bipros.project.domain.model.Project;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stateless upsert-by-code engine that applies a parsed Primavera (XER) schedule onto an
 * <b>existing</b> project. WBS nodes and Activities are matched by {@code code} within the
 * project; matched rows have their planned-side fields overwritten while actuals/progress are
 * preserved. Unmatched rows are created. Relationships and resource assignments are upserted
 * once activities are resolved to UUIDs.
 */
@Service
@RequiredArgsConstructor
public class ScheduleApplyService {

  private final WbsNodeRepository wbsNodeRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository relationshipRepository;
  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRoleRepository resourceRoleRepository;
  private final ResourceTypeRepository resourceTypeRepository;
  private final ProjectRepository projectRepository;

  private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final double DEFAULT_HOURS_PER_DAY = 8.0;

  @Transactional
  public ApplySummary apply(UUID projectId, Map<String, List<Map<String, String>>> tables) {
    double hoursPerDay = resolveHoursPerDay(projectId);
    Map<String, UUID> wbsByFileId = new HashMap<>();
    int[] wbs = upsertWbs(projectId, tables.getOrDefault("PROJWBS", List.of()), wbsByFileId);
    Map<String, UUID> taskByFileKey = new HashMap<>();
    int[] act = upsertActivities(projectId, tables.getOrDefault("TASK", List.of()), wbsByFileId, taskByFileKey, hoursPerDay);
    int rels = upsertRelationships(projectId, tables.getOrDefault("TASKPRED", List.of()), taskByFileKey);
    Map<String, UUID> resourceByCode = new HashMap<>();
    Map<String, String> rsrcTypeById = new HashMap<>();
    for (Map<String, String> row : tables.getOrDefault("RSRC", List.of())) {
      rsrcTypeById.put(row.get("rsrc_id"), row.get("rsrc_type"));
    }
    int asg = upsertAssignments(projectId, tables.getOrDefault("TASKRSRC", List.of()), taskByFileKey, resourceByCode, rsrcTypeById);
    Set<String> fileCodeSet = new HashSet<>();
    for (Map<String, String> row : tables.getOrDefault("TASK", List.of())) {
      fileCodeSet.add(orDefault(row.get("task_code"), row.get("task_id")));
    }
    List<String> missingActivityCodes = computeMissingActivityCodes(projectId, fileCodeSet);
    return new ApplySummary(act[0], act[1], wbs[0], wbs[1], rels, asg, missingActivityCodes);
  }

  /** Activity codes present in the project but not in the file's TASK rows, sorted. Shared by apply() and preview(). */
  private List<String> computeMissingActivityCodes(UUID projectId, Set<String> fileCodeSet) {
    return activityRepository.findByProjectId(projectId).stream()
        .map(Activity::getCode).filter(c -> c != null && !fileCodeSet.contains(c)).sorted().toList();
  }

  /** Resolve hours-per-day ONCE per apply() from the project's calendar; 8.0 when unset/unavailable. */
  private double resolveHoursPerDay(UUID projectId) {
    UUID calendarId = projectRepository.findById(projectId).map(Project::getCalendarId).orElse(null);
    if (calendarId == null) return DEFAULT_HOURS_PER_DAY;
    return projectRepository.findCalendarHoursPerDay(calendarId)
        .filter(h -> h > 0)
        .orElse(DEFAULT_HOURS_PER_DAY);
  }

  @Transactional(readOnly = true)
  public ImportPreview preview(UUID projectId, Map<String, List<Map<String, String>>> tables) {
    List<Map<String, String>> taskRows = tables.getOrDefault("TASK", List.of());
    int inFile = taskRows.size();
    int matched = 0;
    List<String> fileCodes = new ArrayList<>();
    LocalDate min = null, max = null;
    for (Map<String, String> row : taskRows) {
      String code = orDefault(row.get("task_code"), row.get("task_id"));
      fileCodes.add(code);
      if (activityRepository.findByProjectIdAndCode(projectId, code).isPresent()) matched++;
      LocalDate s = parseDate(row.get("target_start_date"));
      LocalDate f = parseDate(row.get("target_end_date"));
      if (s != null && (min == null || s.isBefore(min))) min = s;
      if (f != null && (max == null || f.isAfter(max))) max = f;
    }
    int newCount = inFile - matched;
    Set<String> fileCodeSet = new HashSet<>(fileCodes);
    List<String> missing = computeMissingActivityCodes(projectId, fileCodeSet);

    BigDecimal totalCost = BigDecimal.ZERO;
    for (Map<String, String> row : tables.getOrDefault("TASKRSRC", List.of())) {
      BigDecimal c = parseCost(row.get("target_cost"));
      if (c != null) totalCost = totalCost.add(c);
    }

    List<String> warnings = new ArrayList<>();
    if (inFile > 0 && matched == 0)
      warnings.add("0 of " + inFile + " activities matched an existing code — codes may not align; importing will create all as new.");
    long undated = taskRows.stream().filter(r -> parseDate(r.get("target_start_date")) == null).count();
    if (undated > 0) warnings.add(undated + " activities have no planned start date in the file.");

    return new ImportPreview(inFile, matched, newCount, missing.size(),
        tables.getOrDefault("PROJWBS", List.of()).size(),
        tables.getOrDefault("TASKPRED", List.of()).size(),
        tables.getOrDefault("TASKRSRC", List.of()).size(),
        min, max, totalCost, missing, warnings, null);
  }

  /** @return [created, updated] */
  private int[] upsertWbs(UUID projectId, List<Map<String, String>> rows, Map<String, UUID> wbsByFileId) {
    int created = 0, updated = 0;
    Map<String, WbsNode> nodesByFileId = new HashMap<>();
    // pass 1: upsert every node so every file wbs_id resolves to a live UUID.
    for (Map<String, String> row : rows) {
      String fileId = row.get("wbs_id");
      String code = orDefault(row.get("wbs_short_name"), fileId);
      Optional<WbsNode> match = wbsNodeRepository.findByProjectIdAndCode(projectId, code);
      WbsNode node = match.orElseGet(WbsNode::new);
      node.setProjectId(projectId);
      node.setCode(code);
      node.setName(orDefault(row.get("wbs_name"), "WBS Node"));
      WbsNode saved = wbsNodeRepository.save(node);
      wbsByFileId.put(fileId, saved.getId());
      nodesByFileId.put(fileId, saved);
      if (match.isPresent()) updated++; else created++;
    }
    // pass 2: now that every node has a live UUID, wire up parent/child links.
    for (Map<String, String> row : rows) {
      UUID parentId = wbsByFileId.get(row.get("parent_wbs_id"));
      if (parentId == null) continue;
      WbsNode node = nodesByFileId.get(row.get("wbs_id"));
      if (node == null) continue;
      node.setParentId(parentId);
      wbsNodeRepository.save(node);
    }
    return new int[]{created, updated};
  }

  /** @return [created, updated] */
  private int[] upsertActivities(UUID projectId, List<Map<String, String>> rows,
      Map<String, UUID> wbsByFileId, Map<String, UUID> taskByFileKey, double hoursPerDay) {
    int created = 0, updated = 0;
    UUID[] rootWbsCache = new UUID[1];
    for (Map<String, String> row : rows) {
      String code = orDefault(row.get("task_code"), row.get("task_id"));
      Optional<Activity> match = activityRepository.findByProjectIdAndCode(projectId, code);
      Activity a = match.orElseGet(Activity::new);
      a.setProjectId(projectId);
      a.setCode(code);
      a.setName(orDefault(row.get("task_name"), "Task"));
      // planned side — always overwritten:
      a.setPlannedStartDate(parseDate(row.get("target_start_date")));
      a.setPlannedFinishDate(parseDate(row.get("target_end_date")));
      a.setOriginalDuration(hoursToDays(parseDouble(row.get("target_drtn_hr_cnt")), hoursPerDay));
      a.setRemainingDuration(hoursToDays(parseDouble(row.get("remain_drtn_hr_cnt")), hoursPerDay));
      a.setActivityType(mapActivityType(row.get("task_type")));
      // wbs link (fall back to any existing wbs the activity already had, else a resolved node):
      UUID wbsNodeId = wbsByFileId.get(row.get("wbs_id"));
      if (wbsNodeId != null) a.setWbsNodeId(wbsNodeId);
      if (a.getWbsNodeId() == null) a.setWbsNodeId(resolveFallbackWbs(projectId, wbsByFileId, rootWbsCache));
      // NOTE: actuals (actualStartDate/actualFinishDate/percentComplete) are intentionally NOT touched for MATCHED rows.
      if (match.isEmpty()) {
        Double physComplete = parseDouble(row.get("phys_complete_pct"));
        if (physComplete != null) {
          double value = physComplete / 100.0;
          a.setPercentComplete(value);
          a.setPhysicalPercentComplete(value);
        }
      }
      Activity saved = activityRepository.save(a);
      String fileKey = orDefault(row.get("task_id"), code);
      taskByFileKey.put(fileKey, saved.getId());
      if (match.isPresent()) updated++; else created++;
    }
    return new int[]{created, updated};
  }

  private static Double hoursToDays(Double hours, double hoursPerDay) {
    return hours == null ? null : hours / hoursPerDay;
  }

  /** Map XER P6 task_type codes to ActivityType; case-insensitive; "MILESTONE" kept as a lenient alias. */
  private ActivityType mapActivityType(String taskType) {
    if (taskType == null) return ActivityType.TASK_DEPENDENT;
    return switch (taskType.toUpperCase()) {
      case "TT_FINMILE", "TT_MILE", "MILESTONE" -> ActivityType.FINISH_MILESTONE;
      case "TT_STARTMILE" -> ActivityType.START_MILESTONE;
      default -> ActivityType.TASK_DEPENDENT;
    };
  }

  private int upsertRelationships(UUID projectId, List<Map<String, String>> rows, Map<String, UUID> taskByFileKey) {
    int created = 0;
    for (Map<String, String> row : rows) {
      UUID pred = taskByFileKey.get(row.get("pred_task_id"));
      UUID succ = taskByFileKey.get(row.get("task_id"));
      if (pred == null || succ == null) continue;
      if (relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(pred, succ)) continue;
      ActivityRelationship rel = new ActivityRelationship();
      rel.setProjectId(projectId);
      rel.setPredecessorActivityId(pred);
      rel.setSuccessorActivityId(succ);
      rel.setRelationshipType(mapRelationshipType(row.get("pred_type")));
      Double lag = parseDouble(row.get("lag_hr_cnt"));
      rel.setLag(lag != null ? lag : 0.0);
      relationshipRepository.save(rel);
      created++;
    }
    return created;
  }

  private int upsertAssignments(UUID projectId, List<Map<String, String>> rows, Map<String, UUID> taskByFileKey,
      Map<String, UUID> resourceByCode, Map<String, String> rsrcTypeById) {
    int count = 0;
    for (Map<String, String> row : rows) {
      UUID activityId = taskByFileKey.get(row.get("task_id"));
      if (activityId == null) continue;
      UUID resourceId = ensureResource(row.get("rsrc_id"), resourceByCode, rsrcTypeById);
      Optional<ResourceAssignment> match =
          assignmentRepository.findByProjectIdAndActivityIdAndResourceId(projectId, activityId, resourceId);
      ResourceAssignment asg = match.orElseGet(ResourceAssignment::new);
      asg.setProjectId(projectId);
      asg.setActivityId(activityId);
      asg.setResourceId(resourceId);
      Double qty = parseDouble(row.get("target_qty"));
      if (qty != null) asg.setPlannedUnits(qty);
      BigDecimal cost = parseCost(row.get("target_cost"));
      if (cost != null) { asg.setPlannedCost(cost); asg.setBudgetedCost(cost); }
      assignmentRepository.save(asg);
      count++;
    }
    return count;
  }

  private UUID resolveFallbackWbs(UUID projectId, Map<String, UUID> wbsByFileId, UUID[] rootWbsCache) {
    if (!wbsByFileId.isEmpty()) return wbsByFileId.values().iterator().next();
    if (rootWbsCache[0] != null) return rootWbsCache[0];
    UUID resolved = wbsNodeRepository.findByProjectId(projectId).stream().findFirst()
        .map(WbsNode::getId)
        .orElseGet(() -> createRootWbs(projectId));
    rootWbsCache[0] = resolved;
    return resolved;
  }

  /** Mirrors XerImportMapper.createRootWbsIfNeeded: a project with zero WBS nodes must not block import. */
  private UUID createRootWbs(UUID projectId) {
    WbsNode root = new WbsNode();
    root.setProjectId(projectId);
    root.setCode("ROOT");
    root.setName("Project");
    root.setSortOrder(0);
    return wbsNodeRepository.save(root).getId();
  }

  private RelationshipType mapRelationshipType(String x) {
    if (x == null) return RelationshipType.FINISH_TO_START;
    return switch (x.toUpperCase()) {
      case "FF", "PR_FF" -> RelationshipType.FINISH_TO_FINISH;
      case "SS", "PR_SS" -> RelationshipType.START_TO_START;
      case "SF", "PR_SF" -> RelationshipType.START_TO_FINISH;
      default -> RelationshipType.FINISH_TO_START;
    };
  }

  /** Map a Primavera (XER) RSRC.rsrc_type token to this app's ResourceType code. Unknown/blank tokens
   *  (including the plain "LABOR" some XER exports use) fall back to MANPOWER — this app has no LABOR type. */
  static String mapResourceType(String xerType) {
    if (xerType == null || xerType.isBlank()) return "MANPOWER";
    String upper = xerType.toUpperCase();
    if (upper.contains("EQUIP") || upper.contains("NONLABOR")) return "EQUIPMENT";
    if (upper.contains("MAT")) return "MATERIAL";
    return "MANPOWER";
  }

  /** Dedupe Resource by global code, create with an IMPORTED-<TYPE> role. resourceByCode is an in-process
   *  cache scoped to a single apply() call so repeated rsrc_id values reuse the same resource id. */
  private UUID ensureResource(String rsrcCode, Map<String, UUID> resourceByCode, Map<String, String> rsrcTypeById) {
    String code = orDefault(rsrcCode, "IMPORTED-RSRC");
    UUID cached = resourceByCode.get(code);
    if (cached != null) return cached;
    Optional<Resource> existing = resourceRepository.findByCode(code);
    if (existing.isPresent()) {
      resourceByCode.put(code, existing.get().getId());
      return existing.get().getId();
    }
    String appTypeCode = mapResourceType(rsrcTypeById.get(rsrcCode));
    ResourceType type = resourceTypeRepository.findByCode(appTypeCode)
        .or(() -> resourceTypeRepository.findByCode("MANPOWER"))
        .orElseThrow(() -> new BusinessRuleException("MASTER_DATA_MISSING",
            "Required master data (MANPOWER resource type) is not configured. Contact an administrator."));
    ResourceRole role = resourceRoleRepository.findByCode("IMPORTED-" + type.getCode())
        .orElseGet(() -> {
          ResourceRole r = new ResourceRole();
          r.setCode("IMPORTED-" + type.getCode());
          r.setName("Imported " + type.getCode());
          r.setResourceType(type);
          r.setActive(true);
          r.setSortOrder(999);
          return resourceRoleRepository.save(r);
        });
    Resource res = new Resource();
    res.setCode(code.length() > 50 ? code.substring(0, 50) : code);
    res.setName(code);
    res.setResourceType(type);
    res.setRole(role);
    UUID id = resourceRepository.save(res).getId();
    resourceByCode.put(code, id);
    return id;
  }

  static String orDefault(String v, String fallback) { return (v == null || v.isBlank()) ? fallback : v; }

  static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    try { return LocalDate.parse(s.substring(0, Math.min(10, s.length())), DF); }
    catch (Exception e) { return null; }
  }

  static Double parseDouble(String s) {
    if (s == null || s.isBlank()) return null;
    try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
  }

  static BigDecimal parseCost(String s) {
    if (s == null || s.isBlank()) return null;
    try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
  }
}
