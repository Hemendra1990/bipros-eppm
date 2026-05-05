package com.bipros.ai.tool.gis;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.gis.domain.model.GisLayer;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.GisLayerRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.project.domain.model.Stretch;
import com.bipros.project.domain.repository.StretchRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spatial / chainage queries over the project corridor. Operations:
 * chainage_lookup, by_stretch, geometry, activities_at_chainage.
 * <p>
 * The geometry op exposes WBS polygons + GIS layers (the only fully-modelled
 * geometry rows in bipros-gis). We deliberately do NOT serialize the raw
 * PostGIS Polygon — only metadata (centroid, area, layer) — to avoid blowing
 * out the LLM context window with WKT/GeoJSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GisQueryTool implements Tool {

  private final ActivityRepository activityRepository;
  private final StretchRepository stretchRepository;
  private final WbsPolygonRepository polygonRepository;
  private final GisLayerRepository layerRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "gis_query";
  }

  @Override
  public String description() {
    return "Use this for chainage / spatial lookups along the project corridor. Operations via op "
        + "param: 'chainage_lookup' (given a chainage value in metres, list activities and stretches "
        + "whose chainage band covers it), 'activities_at_chainage' (alias), 'by_stretch' (list "
        + "activities whose chainage band intersects a named stretch, identified by stretch_code "
        + "or stretch_id), 'geometry' (GIS layers + WBS polygon metadata for the project — centroid, "
        + "area; raw geometry is omitted to keep the response compact). Examples: 'what's happening "
        + "at km 145+500', 'show activities in stretch STR-003', 'list GIS layers'. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("chainage_lookup");
    opEnum.add("by_stretch");
    opEnum.add("geometry");
    opEnum.add("activities_at_chainage");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    props.set("op", opNode);
    props.set("chainage_m", objectMapper.createObjectNode().put("type", "integer")
        .put("description", "Chainage in metres. Required for chainage_lookup / activities_at_chainage."));
    props.set("stretch_code", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Stretch code, e.g. STR-003. For op=by_stretch."));
    props.set("stretch_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Stretch UUID. For op=by_stretch."));
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
    if (projectId == null) return ToolResult.error("gis_query needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required");

    return switch (op) {
      case "chainage_lookup", "activities_at_chainage" -> doChainageLookup(input, projectId);
      case "by_stretch" -> doByStretch(input, projectId);
      case "geometry" -> doGeometry(projectId);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doChainageLookup(JsonNode input, UUID projectId) {
    if (input.path("chainage_m").isMissingNode() || input.path("chainage_m").isNull()) {
      return ToolResult.error("chainage_m is required (in metres).");
    }
    long ch = input.path("chainage_m").asLong();
    List<Activity> acts = activityRepository.findByProjectId(projectId);
    ArrayNode actRows = objectMapper.createArrayNode();
    List<UUID> actIds = new ArrayList<>();
    for (Activity a : acts) {
      Long from = a.getChainageFromM();
      Long toM = a.getChainageToM();
      if (from == null || toM == null) continue;
      if (from <= ch && ch <= toM) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("activity_id", a.getId().toString());
        n.put("activity_code", a.getCode());
        n.put("activity_name", a.getName());
        n.put("chainage_from_m", from);
        n.put("chainage_to_m", toM);
        n.put("status", a.getStatus() == null ? null : a.getStatus().name());
        n.put("percent_complete", a.getPercentComplete());
        actRows.add(n);
        actIds.add(a.getId());
      }
    }
    List<Stretch> stretches = stretchRepository.findByProjectIdOrderByFromChainageM(projectId);
    ArrayNode strRows = objectMapper.createArrayNode();
    List<UUID> strIds = new ArrayList<>();
    for (Stretch s : stretches) {
      if (s.getFromChainageM() == null || s.getToChainageM() == null) continue;
      if (s.getFromChainageM() <= ch && ch <= s.getToChainageM()) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("stretch_id", s.getId().toString());
        n.put("stretch_code", s.getStretchCode());
        n.put("name", s.getName());
        n.put("from_chainage_m", s.getFromChainageM());
        n.put("to_chainage_m", s.getToChainageM());
        n.put("status", s.getStatus() == null ? null : s.getStatus().name());
        n.put("milestone_name", s.getMilestoneName());
        strRows.add(n);
        strIds.add(s.getId());
      }
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.put("chainage_m", ch);
    w.set("activities", actRows);
    w.set("stretches", strRows);
    w.put("activity_count", actRows.size());
    w.put("stretch_count", strRows.size());
    java.util.HashMap<String, List<UUID>> links = new java.util.HashMap<>();
    if (!actIds.isEmpty()) links.put("activity", actIds);
    if (!strIds.isEmpty()) links.put("stretch", strIds);
    ToolResult.attachLinks(w, links);
    return ToolResult.ok(actRows.size() + " activities + " + strRows.size() + " stretches at chainage " + ch + "m", w);
  }

  private ToolResult doByStretch(JsonNode input, UUID projectId) {
    Stretch stretch = resolveStretch(input, projectId);
    if (stretch == null) return ToolResult.error("Provide stretch_code or stretch_id.");
    if (stretch.getFromChainageM() == null || stretch.getToChainageM() == null) {
      return ToolResult.error("Stretch has no chainage band.");
    }
    long from = stretch.getFromChainageM();
    long toM = stretch.getToChainageM();
    List<Activity> acts = activityRepository.findByProjectId(projectId);
    ArrayNode rows = objectMapper.createArrayNode();
    List<UUID> ids = new ArrayList<>();
    for (Activity a : acts) {
      Long af = a.getChainageFromM();
      Long at = a.getChainageToM();
      if (af == null || at == null) continue;
      // overlap test: [af,at] ∩ [from,toM] != ∅
      if (at < from || af > toM) continue;
      ObjectNode n = objectMapper.createObjectNode();
      n.put("activity_id", a.getId().toString());
      n.put("activity_code", a.getCode());
      n.put("activity_name", a.getName());
      n.put("chainage_from_m", af);
      n.put("chainage_to_m", at);
      n.put("status", a.getStatus() == null ? null : a.getStatus().name());
      n.put("percent_complete", a.getPercentComplete());
      rows.add(n);
      ids.add(a.getId());
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("stretch_id", stretch.getId().toString());
    w.put("stretch_code", stretch.getStretchCode());
    w.put("from_chainage_m", from);
    w.put("to_chainage_m", toM);
    w.put("activity_count", rows.size());
    if (!ids.isEmpty()) ToolResult.attachLinks(w, Map.of("activity", ids, "stretch", List.of(stretch.getId())));
    return ToolResult.ok(rows.size() + " activities in stretch " + stretch.getStretchCode(), w);
  }

  private ToolResult doGeometry(UUID projectId) {
    List<GisLayer> layers = layerRepository.findByProjectIdOrderBySortOrder(projectId);
    List<WbsPolygon> polys = polygonRepository.findByProjectId(projectId);
    ArrayNode layerRows = objectMapper.createArrayNode();
    for (GisLayer l : layers) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("layer_id", l.getId().toString());
      n.put("layer_name", l.getLayerName());
      n.put("layer_type", l.getLayerType() == null ? null : l.getLayerType().name());
      n.put("description", l.getDescription());
      n.put("is_visible", l.getIsVisible());
      n.put("opacity", l.getOpacity());
      layerRows.add(n);
    }
    ArrayNode polyRows = objectMapper.createArrayNode();
    for (WbsPolygon p : polys) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("polygon_id", p.getId().toString());
      n.put("wbs_node_id", p.getWbsNodeId() == null ? null : p.getWbsNodeId().toString());
      n.put("wbs_code", p.getWbsCode());
      n.put("wbs_name", p.getWbsName());
      n.put("layer_id", p.getLayerId() == null ? null : p.getLayerId().toString());
      n.put("center_lat", p.getCenterLatitude());
      n.put("center_lon", p.getCenterLongitude());
      n.put("area_sq_m", p.getAreaInSqMeters());
      polyRows.add(n);
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("layers", layerRows);
    w.set("polygons", polyRows);
    w.put("layer_count", layers.size());
    w.put("polygon_count", polys.size());
    w.put("note", "Raw polygon geometry omitted; centroids + area returned.");
    return ToolResult.ok(layers.size() + " GIS layers, " + polys.size() + " WBS polygons", w);
  }

  private Stretch resolveStretch(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("stretch_id").asText(null));
    if (idStr != null) {
      try {
        UUID id = UUID.fromString(idStr);
        return stretchRepository.findById(id).filter(s -> projectId.equals(s.getProjectId())).orElse(null);
      } catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    String code = orNull(input.path("stretch_code").asText(null));
    if (code != null) {
      List<Stretch> all = stretchRepository.findByProjectIdOrderByFromChainageM(projectId);
      for (Stretch s : all) if (code.equalsIgnoreCase(s.getStretchCode())) return s;
    }
    return null;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
