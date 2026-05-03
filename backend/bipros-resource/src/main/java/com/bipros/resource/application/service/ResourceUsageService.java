package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.calendar.application.service.CalendarService;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ActivityUsage;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ResourceTypeUsage;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ResourceUsage;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Time-phased ("Resource Assignments" P6 window) view of a project's resource consumption.
 * Spreads each {@link ResourceAssignment#getPlannedUnits()} linearly across the assignment's
 * working days (per the project calendar) and rolls up to Resource Type → Resource → Activity.
 * Actual units come from {@link DailyActivityResourceOutput} and are bucketed by month per
 * (resource × activity) so they line up alongside the planned distribution.
 *
 * <p>When the assignment row has no {@code plannedStartDate} / {@code plannedFinishDate} (common
 * in seeded data, where dates live only on the parent activity), the activity's planned dates are
 * used as a fallback so the planned distribution still renders.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ResourceUsageService {

  private static final DateTimeFormatter PERIOD_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRoleRepository roleRepository;
  private final ResourceTypeRepository resourceTypeRepository;
  private final ActivityRepository activityRepository;
  private final ProjectRepository projectRepository;
  private final DailyActivityResourceOutputRepository dailyOutputRepository;
  private final CalendarService calendarService;

  public ResourceUsageTimePhasedResponse getTimePhased(UUID projectId, LocalDate from, LocalDate to) {
    Project project = projectRepository.findById(projectId).orElse(null);
    List<ResourceAssignment> assignments = assignmentRepository.findByProjectId(projectId);

    LocalDate resolvedFrom = resolveFrom(from, project, projectId);
    LocalDate resolvedTo = resolveTo(to, project, projectId);

    if (resolvedFrom == null || resolvedTo == null || resolvedTo.isBefore(resolvedFrom)) {
      return new ResourceUsageTimePhasedResponse(List.of(), null, null, List.of());
    }

    List<String> periods = buildPeriodKeys(resolvedFrom, resolvedTo);

    UUID calendarId = project != null ? project.getCalendarId() : null;

    Map<UUID, Activity> activitiesById = bulkLoad(
        assignments.stream().map(ResourceAssignment::getActivityId).filter(Objects::nonNull),
        ids -> activityRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Activity::getId, a -> a)));

    Map<UUID, Resource> resourcesById = bulkLoad(
        assignments.stream().map(ResourceAssignment::getResourceId).filter(Objects::nonNull),
        ids -> resourceRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Resource::getId, r -> r)));

    Map<UUID, ResourceRole> rolesById = bulkLoad(
        java.util.stream.Stream.concat(
            assignments.stream().map(ResourceAssignment::getRoleId).filter(Objects::nonNull),
            resourcesById.values().stream()
                .map(Resource::getRole)
                .filter(Objects::nonNull)
                .map(ResourceRole::getId)),
        ids -> roleRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ResourceRole::getId, r -> r)));

    Map<UUID, ResourceType> typesById = bulkLoad(
        resourcesById.values().stream()
            .map(Resource::getResourceType)
            .filter(Objects::nonNull)
            .map(ResourceType::getId),
        ids -> resourceTypeRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ResourceType::getId, t -> t)));

    // Actuals: pre-bucket by (resourceId, activityId) → Map<period, sum(qtyExecuted)>.
    List<DailyActivityResourceOutput> dailyOutputs =
        dailyOutputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
            projectId, resolvedFrom, resolvedTo);
    Map<UUID, Map<UUID, Map<String, Double>>> actualsByResourceActivity = bucketActuals(dailyOutputs);

    return assemble(
        assignments, periods, resolvedFrom, resolvedTo, calendarId,
        resourcesById, rolesById, activitiesById, typesById, actualsByResourceActivity);
  }

  // ────────────────────────── range resolution ──────────────────────────

  private LocalDate resolveFrom(LocalDate explicit, Project project, UUID projectId) {
    if (explicit != null) return explicit;
    if (project != null && project.getPlannedStartDate() != null) return project.getPlannedStartDate();
    return activityRepository.findByProjectId(projectId).stream()
        .map(Activity::getPlannedStartDate)
        .filter(Objects::nonNull)
        .min(LocalDate::compareTo)
        .orElse(null);
  }

  private LocalDate resolveTo(LocalDate explicit, Project project, UUID projectId) {
    if (explicit != null) return explicit;
    if (project != null && project.getPlannedFinishDate() != null) return project.getPlannedFinishDate();
    return activityRepository.findByProjectId(projectId).stream()
        .map(Activity::getPlannedFinishDate)
        .filter(Objects::nonNull)
        .max(LocalDate::compareTo)
        .orElse(null);
  }

  private static List<String> buildPeriodKeys(LocalDate from, LocalDate to) {
    List<String> keys = new ArrayList<>();
    YearMonth cursor = YearMonth.from(from);
    YearMonth end = YearMonth.from(to);
    while (!cursor.isAfter(end)) {
      keys.add(cursor.format(PERIOD_KEY));
      cursor = cursor.plusMonths(1);
    }
    return keys;
  }

  // ────────────────────────── time-phasing core ──────────────────────────

  /**
   * Spread an assignment's {@code plannedUnits} linearly across the working days between its
   * planned start and finish (inclusive). Falls back to the parent activity's planned dates when
   * the assignment row's are null — common in seeded data where dates live on the activity only.
   * Returns a map keyed by {@code "YYYY-MM"} with monthly accumulated units.
   *
   * <p>If the calendar is unknown or every day in the range is non-working, the units fall into
   * the start month as a defensive fallback so we don't silently drop them.
   */
  Map<String, Double> spreadAssignment(ResourceAssignment a, Activity activity, UUID calendarId) {
    Double plannedUnits = a.getPlannedUnits();
    LocalDate start = a.getPlannedStartDate() != null
        ? a.getPlannedStartDate()
        : (activity != null ? activity.getPlannedStartDate() : null);
    LocalDate finish = a.getPlannedFinishDate() != null
        ? a.getPlannedFinishDate()
        : (activity != null ? activity.getPlannedFinishDate() : null);
    if (plannedUnits == null || start == null || finish == null || finish.isBefore(start)) {
      return Map.of();
    }

    if (calendarId == null) {
      return Map.of(YearMonth.from(start).format(PERIOD_KEY), plannedUnits);
    }

    // countWorkingDays uses half-open [start, end); add a day so finishDate is included.
    double workingDays = calendarService.countWorkingDays(calendarId, start, finish.plusDays(1));
    if (workingDays <= 0.0) {
      return Map.of(YearMonth.from(start).format(PERIOD_KEY), plannedUnits);
    }

    double unitsPerDay = plannedUnits / workingDays;
    Map<String, Double> bucket = new TreeMap<>();
    for (LocalDate d = start; !d.isAfter(finish); d = d.plusDays(1)) {
      if (calendarService.isWorkingDay(calendarId, d)) {
        bucket.merge(YearMonth.from(d).format(PERIOD_KEY), unitsPerDay, Double::sum);
      }
    }
    return bucket;
  }

  /**
   * Bucket daily output rows by (resourceId, activityId) → period code → summed qtyExecuted.
   * The {@code qtyExecuted} field is in the resource's productivity unit (Day for labour /
   * equipment per the seeder convention; Bag/Nos/Cum for materials), so it lines up directly
   * with the planned distribution computed by {@link #spreadAssignment}.
   */
  private static Map<UUID, Map<UUID, Map<String, Double>>> bucketActuals(
      List<DailyActivityResourceOutput> dailyOutputs) {
    Map<UUID, Map<UUID, Map<String, Double>>> map = new HashMap<>();
    for (DailyActivityResourceOutput o : dailyOutputs) {
      if (o.getResourceId() == null || o.getActivityId() == null || o.getOutputDate() == null) continue;
      double qty = o.getQtyExecuted() == null ? 0.0 : o.getQtyExecuted().doubleValue();
      String period = YearMonth.from(o.getOutputDate()).format(PERIOD_KEY);
      map.computeIfAbsent(o.getResourceId(), k -> new HashMap<>())
          .computeIfAbsent(o.getActivityId(), k -> new TreeMap<>())
          .merge(period, qty, Double::sum);
    }
    return map;
  }

  // ────────────────────────── tree assembly ──────────────────────────

  private ResourceUsageTimePhasedResponse assemble(
      List<ResourceAssignment> assignments,
      List<String> periods,
      LocalDate from,
      LocalDate to,
      UUID calendarId,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceRole> rolesById,
      Map<UUID, Activity> activitiesById,
      Map<UUID, ResourceType> typesById,
      Map<UUID, Map<UUID, Map<String, Double>>> actualsByResourceActivity) {

    // Group assignments by (typeId, resourceId, activityId). Skip rows with no resource (role-only
    // unstaffed slots) — they have no concrete consumption to display.
    Map<UUID, Map<UUID, Map<UUID, List<ResourceAssignment>>>> byTypeResourceActivity = new LinkedHashMap<>();
    for (ResourceAssignment a : assignments) {
      if (a.getResourceId() == null) continue;
      Resource resource = resourcesById.get(a.getResourceId());
      if (resource == null || resource.getResourceType() == null) continue;
      UUID typeId = resource.getResourceType().getId();
      byTypeResourceActivity
          .computeIfAbsent(typeId, k -> new LinkedHashMap<>())
          .computeIfAbsent(a.getResourceId(), k -> new LinkedHashMap<>())
          .computeIfAbsent(a.getActivityId(), k -> new ArrayList<>())
          .add(a);
    }

    List<ResourceTypeUsage> typeNodes = new ArrayList<>();
    for (Map.Entry<UUID, Map<UUID, Map<UUID, List<ResourceAssignment>>>> typeEntry : byTypeResourceActivity.entrySet()) {
      UUID typeId = typeEntry.getKey();
      ResourceType type = typesById.get(typeId);
      if (type == null) continue;

      List<ResourceUsage> resourceNodes = new ArrayList<>();
      for (Map.Entry<UUID, Map<UUID, List<ResourceAssignment>>> resEntry : typeEntry.getValue().entrySet()) {
        UUID resourceId = resEntry.getKey();
        Resource resource = resourcesById.get(resourceId);
        if (resource == null) continue;

        String resourceUnit = resourceUnit(resource, rolesById);
        Map<UUID, Map<String, Double>> actualsForResource =
            actualsByResourceActivity.getOrDefault(resourceId, Map.of());

        List<ActivityUsage> activityNodes = new ArrayList<>();
        for (Map.Entry<UUID, List<ResourceAssignment>> actEntry : resEntry.getValue().entrySet()) {
          UUID activityId = actEntry.getKey();
          Activity activity = activitiesById.get(activityId);
          Map<String, Double> plannedBucket = new TreeMap<>();
          for (ResourceAssignment a : actEntry.getValue()) {
            spreadAssignment(a, activity, calendarId).forEach((k, v) -> plannedBucket.merge(k, v, Double::sum));
          }
          Map<String, Double> actualBucket = new TreeMap<>(actualsForResource.getOrDefault(activityId, Map.of()));
          activityNodes.add(new ActivityUsage(
              activityId,
              activity != null ? activity.getCode() : null,
              activity != null ? activity.getName() : "(unknown activity)",
              plannedBucket,
              actualBucket));
        }

        activityNodes.sort(Comparator.comparing(
            ActivityUsage::activityName, Comparator.nullsLast(String::compareToIgnoreCase)));

        Map<String, Double> resourcePlanned = sumBuckets(activityNodes.stream().map(ActivityUsage::plannedByPeriod).toList());
        Map<String, Double> resourceActual = sumBuckets(activityNodes.stream().map(ActivityUsage::actualByPeriod).toList());
        resourceNodes.add(new ResourceUsage(
            resourceId,
            resource.getCode(),
            resource.getName(),
            resourceUnit,
            resourcePlanned,
            resourceActual,
            activityNodes));
      }

      resourceNodes.sort(Comparator.comparing(
          ResourceUsage::resourceName, Comparator.nullsLast(String::compareToIgnoreCase)));

      // Aggregate at the type level only when every resource shares the same unit.
      String typeUnit = uniformUnit(resourceNodes.stream().map(ResourceUsage::unit).toList());
      Map<String, Double> typePlanned = typeUnit == null
          ? null
          : sumBuckets(resourceNodes.stream().map(ResourceUsage::plannedByPeriod).toList());
      Map<String, Double> typeActual = typeUnit == null
          ? null
          : sumBuckets(resourceNodes.stream().map(ResourceUsage::actualByPeriod).toList());

      typeNodes.add(new ResourceTypeUsage(
          typeId,
          type.getCode(),
          type.getName(),
          typeUnit,
          typePlanned,
          typeActual,
          resourceNodes));
    }

    typeNodes.sort(Comparator.comparing(
        ResourceTypeUsage::resourceTypeName, Comparator.nullsLast(String::compareToIgnoreCase)));

    return new ResourceUsageTimePhasedResponse(periods, from, to, typeNodes);
  }

  // ────────────────────────── helpers ──────────────────────────

  private static String resourceUnit(Resource resource, Map<UUID, ResourceRole> rolesById) {
    if (resource.getRole() == null) return null;
    ResourceRole role = rolesById.get(resource.getRole().getId());
    return role == null ? null : role.getProductivityUnit();
  }

  /** Returns the single unit when all entries are non-null and equal; null otherwise. */
  private static String uniformUnit(List<String> units) {
    String first = null;
    for (String u : units) {
      if (u == null) return null;
      if (first == null) first = u;
      else if (!first.equals(u)) return null;
    }
    return first;
  }

  private static Map<String, Double> sumBuckets(List<Map<String, Double>> buckets) {
    Map<String, Double> total = new TreeMap<>();
    for (Map<String, Double> b : buckets) {
      if (b == null) continue;
      b.forEach((k, v) -> total.merge(k, v, Double::sum));
    }
    return total;
  }

  private static <T> Map<UUID, T> bulkLoad(java.util.stream.Stream<UUID> ids, java.util.function.Function<List<UUID>, Map<UUID, T>> loader) {
    List<UUID> uniqueIds = ids.distinct().toList();
    return uniqueIds.isEmpty() ? Map.of() : loader.apply(uniqueIds);
  }
}
