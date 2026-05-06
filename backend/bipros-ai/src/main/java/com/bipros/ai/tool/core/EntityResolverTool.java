package com.bipros.ai.tool.core;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.manpower.ManpowerMaster;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Fuzzy name → UUID resolver. The first call most cross-entity questions need:
 * the user says "the foundation activity" or "Foreman John" and the LLM has to
 * turn that into a UUID before any other tool can use it.
 *
 * <p>Strategy: pure JVM Levenshtein/substring scoring against project-scoped
 * candidate sets. The candidate sets are small enough (a few hundred to a few
 * thousand per project) that scanning in memory is faster than orchestrating a
 * Postgres trigram round-trip.
 *
 * <p>Returns top-k ranked alternates so the LLM can disambiguate or pick the
 * highest-confidence match. Each alternate carries a {@code score} (0..100;
 * 100 = exact code/name hit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityResolverTool implements Tool {

  private static final int DEFAULT_TOP_K = 5;
  private static final int MAX_TOP_K = 25;
  private static final int MIN_SCORE = 35;

  private final ProjectRepository projectRepository;
  private final ActivityRepository activityRepository;
  private final ResourceRepository resourceRepository;
  private final ManpowerMasterRepository manpowerRepository;
  private final WbsNodeRepository wbsRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "resolve_entity";
  }

  @Override
  public String description() {
    return "Fuzzy resolve a free-text query (a person name, a partial activity code, "
        + "a WBS label, a project nickname) to a list of candidate UUIDs ranked by match "
        + "confidence. ALWAYS call this FIRST when the user's question mentions an entity "
        + "by name and you don't already have its ID — saves a discovery round. "
        + "Examples: \"Who reports to John Pillai?\" → resolve_entity(query=\"John Pillai\", "
        + "kind=\"supervisor\"); \"DPRs for the foundation activity\" → "
        + "resolve_entity(query=\"foundation\", kind=\"activity\"); \"variance on WBS 1.3\" → "
        + "resolve_entity(query=\"1.3\", kind=\"wbs\"). "
        + "Set kind=\"auto\" to search every entity type. Returns up to top_k matches each "
        + "with a score (0–100; 100 = exact code/name hit). Project-scoped — supervisor / "
        + "activity / wbs / resource searches require a current project. Project search works "
        + "across the user's accessible projects.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "query",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put(
                "description",
                "Free-text identifier the user used. Anything works: full name, last name, "
                    + "partial code, label, nickname. Case- and whitespace-tolerant."));
    ArrayNode kindEnum = objectMapper.createArrayNode();
    kindEnum.add("auto");
    kindEnum.add("activity");
    kindEnum.add("resource");
    kindEnum.add("supervisor");
    kindEnum.add("wbs");
    kindEnum.add("project");
    ObjectNode kindNode = objectMapper.createObjectNode();
    kindNode.put("type", "string");
    kindNode.set("enum", kindEnum);
    kindNode.put("default", "auto");
    kindNode.put(
        "description",
        "Which entity type to search. Use \"supervisor\" when the user is asking who-reports-to "
            + "or about a foreman / manager — it matches against ManpowerMaster fields and "
            + "filters by Resource.parent_id IS NULL OR has subordinates. Use \"auto\" only "
            + "when the user's intent is genuinely ambiguous.");
    props.set("kind", kindNode);
    props.set(
        "top_k",
        objectMapper
            .createObjectNode()
            .put("type", "integer")
            .put("minimum", 1)
            .put("maximum", MAX_TOP_K)
            .put("default", DEFAULT_TOP_K)
            .put("description", "How many candidates to return (default 5)."));
    ArrayNode required = objectMapper.createArrayNode();
    required.add("query");
    schema.set("required", required);
    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    String query = input.path("query").asText("").trim();
    if (query.isEmpty()) {
      return ToolResult.error("Provide a `query` (a name, code, or label).");
    }
    String kind = input.path("kind").asText("auto").toLowerCase();
    int topK = Math.max(1, Math.min(MAX_TOP_K, input.path("top_k").asInt(DEFAULT_TOP_K)));

    UUID projectId = ctx.projectId();
    boolean admin = "ADMIN".equals(ctx.role());

    List<Match> matches = new ArrayList<>();

    if ("auto".equals(kind) || "project".equals(kind)) {
      collectProjectMatches(query, ctx, admin, matches);
    }
    if (("auto".equals(kind) || "activity".equals(kind)) && projectId != null) {
      enforceScope(projectId, ctx, admin);
      collectActivityMatches(query, projectId, matches);
    }
    if (("auto".equals(kind) || "resource".equals(kind) || "supervisor".equals(kind))
        && projectId != null) {
      enforceScope(projectId, ctx, admin);
      collectResourceMatches(query, "supervisor".equals(kind), matches);
    }
    if (("auto".equals(kind) || "wbs".equals(kind)) && projectId != null) {
      enforceScope(projectId, ctx, admin);
      collectWbsMatches(query, projectId, matches);
    }

    matches.sort(Comparator.comparingInt(Match::score).reversed());
    if (matches.size() > topK) matches = matches.subList(0, topK);

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("query", query);
    wrapper.put("kind", kind);
    ArrayNode rows = objectMapper.createArrayNode();
    for (Match m : matches) {
      ObjectNode row = objectMapper.createObjectNode();
      row.put("kind", m.kind);
      row.put("id", m.id.toString());
      row.put("code", m.code);
      row.put("name", m.name);
      if (m.extra != null) row.put("extra", m.extra);
      row.put("score", m.score);
      rows.add(row);
    }
    wrapper.set("matches", rows);
    wrapper.put("count", matches.size());

    if (matches.isEmpty()) {
      return ToolResult.ok(
          "No \"" + query + "\" matches found"
              + (projectId != null
                  ? " in this project (" + kind + ")."
                  : " (" + kind + "). Pick a project first if you're looking up activities, "
                      + "resources, or WBS items."),
          wrapper);
    }

    String topLabel = matches.get(0).code != null ? matches.get(0).code : matches.get(0).name;
    String summary =
        matches.size() == 1
            ? "Resolved \"" + query + "\" → " + matches.get(0).kind + ": " + topLabel
            : matches.size() + " candidates for \"" + query + "\". Top: " + topLabel;
    return ToolResult.ok(summary, wrapper);
  }

  private void enforceScope(UUID projectId, AiContext ctx, boolean admin) {
    if (!admin
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
  }

  private void collectProjectMatches(
      String query, AiContext ctx, boolean admin, List<Match> out) {
    List<Project> candidates;
    if (admin) {
      candidates = projectRepository.findAllByArchivedAtIsNull();
    } else if (ctx.scopedProjectIds() != null && !ctx.scopedProjectIds().isEmpty()) {
      candidates = projectRepository.findAllById(ctx.scopedProjectIds());
    } else if (ctx.projectId() != null) {
      candidates = projectRepository.findById(ctx.projectId()).map(List::of).orElse(List.of());
    } else {
      candidates = List.of();
    }
    for (Project p : candidates) {
      int score = bestScore(query, p.getCode(), p.getName());
      if (score >= MIN_SCORE) {
        out.add(new Match("project", p.getId(), p.getCode(), p.getName(), null, score));
      }
    }
  }

  private void collectActivityMatches(String query, UUID projectId, List<Match> out) {
    List<Activity> activities = activityRepository.findByProjectId(projectId);
    for (Activity a : activities) {
      int score = bestScore(query, a.getCode(), a.getName());
      if (score >= MIN_SCORE) {
        String extra = a.getStatus() != null ? a.getStatus().name() : null;
        out.add(new Match("activity", a.getId(), a.getCode(), a.getName(), extra, score));
      }
    }
  }

  private void collectResourceMatches(
      String query, boolean supervisorOnly, List<Match> out) {
    List<Resource> all = resourceRepository.findAll();
    for (Resource r : all) {
      String roleName = r.getRole() != null ? r.getRole().getName() : null;
      String typeCategory =
          r.getResourceType() != null ? r.getResourceType().getCode() : null;
      String fullName = null;
      ManpowerMaster m = manpowerRepository.findById(r.getId()).orElse(null);
      if (m != null) fullName = m.getFullName();

      int score =
          maxOf(
              bestScore(query, r.getCode(), r.getName()),
              fullName == null ? 0 : score(query, fullName),
              roleName == null ? 0 : score(query, roleName));
      if (score < MIN_SCORE) continue;

      if (supervisorOnly) {
        boolean isSupervisor = isLikelySupervisor(r, m);
        if (!isSupervisor) continue;
      }

      String extra =
          (roleName != null ? roleName : "")
              + (fullName != null ? " · " + fullName : "")
              + (typeCategory != null ? " · " + typeCategory : "");
      String displayName = fullName != null && !fullName.isBlank() ? fullName : r.getName();
      out.add(
          new Match(
              supervisorOnly ? "supervisor" : "resource",
              r.getId(),
              r.getCode(),
              displayName,
              extra.isBlank() ? null : extra,
              score));
    }
  }

  private boolean isLikelySupervisor(Resource r, ManpowerMaster m) {
    String roleName = r.getRole() != null ? r.getRole().getName() : null;
    String designation = m != null ? m.getDesignation() : null;
    String haystack =
        ((roleName == null ? "" : roleName) + " " + (designation == null ? "" : designation))
            .toLowerCase();
    if (haystack.contains("supervisor")
        || haystack.contains("foreman")
        || haystack.contains("manager")
        || haystack.contains("engineer")
        || haystack.contains("incharge")
        || haystack.contains("lead")) {
      return true;
    }
    if (r.getParentId() == null) return true;
    if (!resourceRepository.findByParentId(r.getId()).isEmpty()) return true;
    if (m != null && !manpowerRepository.findByReportingManagerId(r.getId()).isEmpty()) return true;
    return false;
  }

  private void collectWbsMatches(String query, UUID projectId, List<Match> out) {
    List<WbsNode> nodes = wbsRepository.findByProjectIdOrderBySortOrder(projectId);
    for (WbsNode n : nodes) {
      int score = bestScore(query, n.getCode(), n.getName());
      if (score >= MIN_SCORE) {
        out.add(new Match("wbs", n.getId(), n.getCode(), n.getName(), null, score));
      }
    }
  }

  private static int bestScore(String query, String code, String name) {
    int s1 = code == null ? 0 : score(query, code);
    int s2 = name == null ? 0 : score(query, name);
    return Math.max(s1, s2);
  }

  private static int maxOf(int a, int b, int c) {
    return Math.max(a, Math.max(b, c));
  }

  /**
   * 0..100 score blending exact / substring / Levenshtein distance. Designed to
   * be friendly to the LLM: 100 = exact (case-insensitive) match; 80+ = near-perfect;
   * 50+ = useful candidate; below {@link #MIN_SCORE} = noise (filtered out).
   */
  private static int score(String query, String haystack) {
    if (query == null || haystack == null) return 0;
    String q = norm(query);
    String h = norm(haystack);
    if (q.isEmpty() || h.isEmpty()) return 0;
    if (q.equals(h)) return 100;
    if (h.startsWith(q)) return 90;
    if (h.contains(q)) return 75;
    if (q.contains(h)) return 70;
    int dist = levenshtein(q, h);
    int max = Math.max(q.length(), h.length());
    int sim = (int) Math.round(100.0 * (1.0 - (double) dist / Math.max(1, max)));
    return Math.max(0, Math.min(74, sim));
  }

  private static String norm(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
  }

  private static int levenshtein(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] curr = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) prev[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      curr[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[b.length()];
  }

  private record Match(String kind, UUID id, String code, String name, String extra, int score) {}
}
