package com.bipros.ai.tool.calendar;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarException;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarExceptionRepository;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.calendar.domain.repository.CalendarWorkWeekRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Calendar / working-day queries. Operations: working_days, holidays, by_resource.
 * <p>
 * working-day algorithm: derives day_type per date by combining the calendar's CalendarWorkWeek
 * pattern with explicit CalendarException overrides. Pure-Java; no scheduler engine call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarTool implements Tool {

  private final CalendarRepository calendarRepository;
  private final CalendarExceptionRepository exceptionRepository;
  private final CalendarWorkWeekRepository workWeekRepository;
  private final ResourceRepository resourceRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "calendar";
  }

  @Override
  public String description() {
    return "Use this for calendar / working-day questions. Operations via op param: 'working_days' "
        + "(count working days in a date range using a chosen calendar's work-week + exceptions; "
        + "if calendar_id is omitted, falls back to the project's default calendar), 'holidays' "
        + "(list non-working exceptions in a date range), 'by_resource' (which calendar a "
        + "Resource is assigned to, with the calendar's hours/day and standard work-week). "
        + "Examples: 'how many working days between Jan 1 and Mar 31', 'list this calendar's "
        + "holidays this quarter', 'what calendar is RES-EX-005 on'. Project-scoped where the "
        + "calendar belongs to a project; a global ADMIN may also query installation-wide calendars.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("working_days");
    opEnum.add("holidays");
    opEnum.add("by_resource");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    props.set("op", opNode);
    props.set("calendar_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Calendar UUID. For working_days/holidays; if omitted, falls back to the project's default."));
    props.set("date_from", objectMapper.createObjectNode().put("type", "string").put("format", "date"));
    props.set("date_to", objectMapper.createObjectNode().put("type", "string").put("format", "date"));
    props.set("resource_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Resource UUID. For op=by_resource."));
    props.set("resource_code", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Resource short code as alternative to resource_id."));
    schema.set("properties", props);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("op");
    schema.set("required", required);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null && !"ADMIN".equals(ctx.role())) {
      return ToolResult.error("calendar needs a project in scope (or ADMIN role).");
    }
    if (projectId != null && !"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required");

    return switch (op) {
      case "working_days" -> doWorkingDays(input, projectId, ctx.role());
      case "holidays" -> doHolidays(input, projectId, ctx.role());
      case "by_resource" -> doByResource(input);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doWorkingDays(JsonNode input, UUID projectId, String role) {
    LocalDate to = parseDate(input.path("date_to").asText(null), LocalDate.now().plusDays(30));
    LocalDate from = parseDate(input.path("date_from").asText(null), LocalDate.now());
    if (from.isAfter(to)) { LocalDate t = from; from = to; to = t; }
    Calendar cal = resolveCalendar(input, projectId, role);
    if (cal == null) return ToolResult.error("Could not resolve calendar — pass calendar_id or ensure the project has a default calendar.");

    Map<DayOfWeek, DayType> weekPattern = loadWeekPattern(cal.getId());
    Map<LocalDate, DayType> exceptionsByDate = loadExceptions(cal.getId(), from, to);

    int workingDays = 0;
    int nonWorkingDays = 0;
    int holidayDays = 0;
    LocalDate cursor = from;
    while (!cursor.isAfter(to)) {
      DayType resolved = exceptionsByDate.get(cursor);
      if (resolved == null) resolved = weekPattern.getOrDefault(cursor.getDayOfWeek(), DayType.WORKING);
      switch (resolved) {
        case WORKING, EXCEPTION_WORKING -> workingDays++;
        case NON_WORKING, EXCEPTION_NON_WORKING -> {
          nonWorkingDays++;
          if (exceptionsByDate.containsKey(cursor)) holidayDays++;
        }
      }
      cursor = cursor.plusDays(1);
    }
    long total = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;

    ObjectNode w = objectMapper.createObjectNode();
    w.put("calendar_id", cal.getId().toString());
    w.put("calendar_code", cal.getCode());
    w.put("calendar_name", cal.getName());
    w.put("hours_per_day", cal.getStandardWorkHoursPerDay());
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("total_days", total);
    w.put("working_days", workingDays);
    w.put("non_working_days", nonWorkingDays);
    w.put("holidays_in_range", holidayDays);
    return ToolResult.ok(workingDays + " working days (out of " + total + ") on " + cal.getName(), w);
  }

  private ToolResult doHolidays(JsonNode input, UUID projectId, String role) {
    LocalDate to = parseDate(input.path("date_to").asText(null), LocalDate.now().plusDays(365));
    LocalDate from = parseDate(input.path("date_from").asText(null), LocalDate.now());
    if (from.isAfter(to)) { LocalDate t = from; from = to; to = t; }
    Calendar cal = resolveCalendar(input, projectId, role);
    if (cal == null) return ToolResult.error("Could not resolve calendar.");
    List<CalendarException> excs = exceptionRepository.findByCalendarIdAndExceptionDateBetween(cal.getId(), from, to);
    ArrayNode rows = objectMapper.createArrayNode();
    int nonWorking = 0;
    int extraWorking = 0;
    for (CalendarException e : excs) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("date", e.getExceptionDate() == null ? null : e.getExceptionDate().toString());
      n.put("name", e.getName());
      n.put("day_type", e.getDayType() == null ? null : e.getDayType().name());
      n.put("total_work_hours", e.getTotalWorkHours());
      rows.add(n);
      if (e.getDayType() == DayType.EXCEPTION_NON_WORKING || e.getDayType() == DayType.NON_WORKING) nonWorking++;
      if (e.getDayType() == DayType.EXCEPTION_WORKING) extraWorking++;
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("calendar_id", cal.getId().toString());
    w.put("calendar_name", cal.getName());
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("count", excs.size());
    w.put("non_working_count", nonWorking);
    w.put("extra_working_count", extraWorking);
    return ToolResult.ok(excs.size() + " calendar exceptions on " + cal.getName(), w);
  }

  private ToolResult doByResource(JsonNode input) {
    Resource resource = resolveResource(input);
    if (resource == null) return ToolResult.error("Provide resource_id or resource_code.");
    UUID calId = resource.getCalendarId();
    Calendar cal = calId == null ? null : calendarRepository.findById(calId).orElse(null);
    ObjectNode resOut = objectMapper.createObjectNode();
    resOut.put("resource_id", resource.getId().toString());
    resOut.put("resource_code", resource.getCode());
    resOut.put("resource_name", resource.getName());
    resOut.put("calendar_id", calId == null ? null : calId.toString());
    ObjectNode w = objectMapper.createObjectNode();
    w.set("resource", resOut);
    if (cal != null) {
      ObjectNode calNode = objectMapper.createObjectNode();
      calNode.put("calendar_id", cal.getId().toString());
      calNode.put("code", cal.getCode());
      calNode.put("name", cal.getName());
      calNode.put("type", cal.getCalendarType() == null ? null : cal.getCalendarType().name());
      calNode.put("hours_per_day", cal.getStandardWorkHoursPerDay());
      calNode.put("days_per_week", cal.getStandardWorkDaysPerWeek());
      calNode.put("is_default", cal.getIsDefault());
      w.set("calendar", calNode);
      ArrayNode pattern = objectMapper.createArrayNode();
      List<CalendarWorkWeek> ww = workWeekRepository.findByCalendarId(cal.getId());
      for (CalendarWorkWeek d : ww) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("day_of_week", d.getDayOfWeek() == null ? null : d.getDayOfWeek().name());
        n.put("day_type", d.getDayType() == null ? null : d.getDayType().name());
        n.put("total_work_hours", d.getTotalWorkHours());
        pattern.add(n);
      }
      w.set("work_week", pattern);
    }
    return ToolResult.ok("Resource " + resource.getCode() + (cal == null ? " has no calendar" : " on calendar " + cal.getName()), w);
  }

  private Calendar resolveCalendar(JsonNode input, UUID projectId, String role) {
    String idStr = orNull(input.path("calendar_id").asText(null));
    if (idStr != null) {
      try {
        UUID id = UUID.fromString(idStr);
        Optional<Calendar> opt = calendarRepository.findById(id);
        if (opt.isEmpty()) return null;
        Calendar c = opt.get();
        if (!"ADMIN".equals(role) && c.getProjectId() != null && !c.getProjectId().equals(projectId)) {
          return null; // out of scope
        }
        return c;
      } catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    if (projectId != null) {
      List<Calendar> projectCals = calendarRepository.findByProjectId(projectId);
      for (Calendar c : projectCals) if (Boolean.TRUE.equals(c.getIsDefault())) return c;
      if (!projectCals.isEmpty()) return projectCals.get(0);
    }
    return null;
  }

  private Resource resolveResource(JsonNode input) {
    String idStr = orNull(input.path("resource_id").asText(null));
    if (idStr != null) {
      try { return resourceRepository.findById(UUID.fromString(idStr)).orElse(null); }
      catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    String code = orNull(input.path("resource_code").asText(null));
    if (code != null) return resourceRepository.findByCode(code).orElse(null);
    return null;
  }

  private Map<DayOfWeek, DayType> loadWeekPattern(UUID calendarId) {
    Map<DayOfWeek, DayType> m = new HashMap<>();
    List<CalendarWorkWeek> rows = workWeekRepository.findByCalendarId(calendarId);
    for (CalendarWorkWeek r : rows) {
      if (r.getDayOfWeek() != null && r.getDayType() != null) m.put(r.getDayOfWeek(), r.getDayType());
    }
    if (m.isEmpty()) {
      // Fall back to standard 5-day week (Mon-Fri working, Sat-Sun non-working).
      Set<DayOfWeek> weekend = new HashSet<>();
      weekend.add(DayOfWeek.SATURDAY);
      weekend.add(DayOfWeek.SUNDAY);
      for (DayOfWeek d : DayOfWeek.values()) {
        m.put(d, weekend.contains(d) ? DayType.NON_WORKING : DayType.WORKING);
      }
    }
    return m;
  }

  private Map<LocalDate, DayType> loadExceptions(UUID calendarId, LocalDate from, LocalDate to) {
    List<CalendarException> excs = exceptionRepository.findByCalendarIdAndExceptionDateBetween(calendarId, from, to);
    Map<LocalDate, DayType> m = new HashMap<>();
    for (CalendarException e : excs) {
      if (e.getExceptionDate() != null && e.getDayType() != null) m.put(e.getExceptionDate(), e.getDayType());
    }
    return m;
  }

  private static LocalDate parseDate(String s, LocalDate fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return LocalDate.parse(s.trim()); } catch (Exception e) { return fallback; }
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
