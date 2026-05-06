package com.bipros.ai.tool.material;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialCategory;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.model.MaterialSource;
import com.bipros.resource.domain.model.MaterialSourceType;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import com.bipros.resource.domain.repository.MaterialSourceRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materials cluster tool — sources, catalogue, consumption, reconciliation,
 * stock register, GRNs. Action-typed via {@code op}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialsTool implements Tool {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final MaterialSourceRepository sourceRepository;
  private final MaterialRepository materialRepository;
  private final MaterialConsumptionLogRepository consumptionRepository;
  private final MaterialReconciliationRepository reconciliationRepository;
  private final MaterialStockRepository stockRepository;
  private final GoodsReceiptNoteRepository grnRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "materials";
  }

  @Override
  public String description() {
    return "Use this when the user asks anything about materials — supplier sources, catalogue "
        + "(MAT-* items), daily consumption logs, monthly reconciliation, the live stock "
        + "register, or GRNs (goods receipt notes). One tool, multiple ops via the `op` param: "
        + "`sources` (approved borrow areas / quarries / depots, optional source_type filter; "
        + "rolls up by source type), `catalogue` (Material masters, optional category), "
        + "`consumption` (MaterialConsumptionLog rows, date range + optional resource_id), "
        + "`reconciliation` (MaterialReconciliation rows, optional period), `stock_register` "
        + "(MaterialStock rows for the project), `grns` (GoodsReceiptNote rows, optional date "
        + "range / supplier / material_id). Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();

    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("sources");
    opEnum.add("catalogue");
    opEnum.add("consumption");
    opEnum.add("reconciliation");
    opEnum.add("stock_register");
    opEnum.add("grns");
    ObjectNode op = objectMapper.createObjectNode();
    op.put("type", "string");
    op.set("enum", opEnum);
    op.put("description", "Which sub-query to run. Required.");
    props.set("op", op);

    ArrayNode srcEnum = objectMapper.createArrayNode();
    for (MaterialSourceType t : MaterialSourceType.values()) srcEnum.add(t.name());
    ObjectNode srcType = objectMapper.createObjectNode();
    srcType.put("type", "string");
    srcType.set("enum", srcEnum);
    srcType.put("description", "Filter for `sources` (BORROW_AREA / QUARRY / BITUMEN_DEPOT / CEMENT_SOURCE).");
    props.set("source_type", srcType);

    ArrayNode catEnum = objectMapper.createArrayNode();
    for (MaterialCategory c : MaterialCategory.values()) catEnum.add(c.name());
    ObjectNode cat = objectMapper.createObjectNode();
    cat.put("type", "string");
    cat.set("enum", catEnum);
    cat.put("description", "Filter for `catalogue` (e.g. CEMENT, AGGREGATE, STEEL).");
    props.set("category", cat);

    props.set("date_from", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Used by `consumption` and `grns`."));
    props.set("date_to", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Used by `consumption` and `grns`."));
    props.set("period", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Reconciliation period token (e.g. `2026-04`). Used by `reconciliation`."));
    props.set("resource_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Filter consumption rows by the material/resource id."));
    props.set("material_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Filter GRNs by material_id."));
    props.set("supplier_organisation_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Filter GRNs by supplier organisation_id."));
    props.set("limit", objectMapper.createObjectNode()
        .put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT));

    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) {
      return ToolResult.error("materials needs a project in scope. Pick a project, then re-ask.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = orNull(input.path("op").asText(null));
    if (op == null) {
      return ToolResult.error("materials requires `op` ∈ "
          + "{sources, catalogue, consumption, reconciliation, stock_register, grns}.");
    }
    int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

    return switch (op.toLowerCase()) {
      case "sources" -> doSources(input, projectId, limit);
      case "catalogue" -> doCatalogue(input, projectId, limit);
      case "consumption" -> doConsumption(input, projectId, limit);
      case "reconciliation" -> doReconciliation(input, projectId, limit);
      case "stock_register" -> doStockRegister(projectId, limit);
      case "grns" -> doGrns(input, projectId, limit);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doSources(JsonNode input, UUID projectId, int limit) {
    MaterialSourceType typeFilter = parseEnum(input.path("source_type").asText(null), MaterialSourceType.class);
    List<MaterialSource> all = typeFilter != null
        ? sourceRepository.findByProjectIdAndSourceType(projectId, typeFilter)
        : sourceRepository.findByProjectId(projectId);
    int matched = all.size();
    if (all.size() > limit) all = all.subList(0, limit);

    Map<MaterialSourceType, Long> byType = new LinkedHashMap<>();
    ArrayNode rows = objectMapper.createArrayNode();
    for (MaterialSource s : all) {
      byType.merge(s.getSourceType(), 1L, Long::sum);
      ObjectNode n = objectMapper.createObjectNode();
      n.put("source_id", s.getId() == null ? null : s.getId().toString());
      n.put("source_code", s.getSourceCode());
      n.put("name", s.getName());
      n.put("source_type", s.getSourceType() == null ? null : s.getSourceType().name());
      n.put("village", s.getVillage());
      n.put("district", s.getDistrict());
      n.put("state", s.getState());
      n.put("distance_km", s.getDistanceKm() == null ? null : s.getDistanceKm().doubleValue());
      n.put("approved_quantity", s.getApprovedQuantity() == null ? null : s.getApprovedQuantity().doubleValue());
      n.put("approved_quantity_unit", s.getApprovedQuantityUnit());
      n.put("cbr_average_percent", s.getCbrAveragePercent() == null ? null : s.getCbrAveragePercent().doubleValue());
      n.put("mdd_gcc", s.getMddGcc() == null ? null : s.getMddGcc().doubleValue());
      n.put("lab_test_status", s.getLabTestStatus() == null ? null : s.getLabTestStatus().name());
      rows.add(n);
    }
    ArrayNode summary = objectMapper.createArrayNode();
    for (Map.Entry<MaterialSourceType, Long> e : byType.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("source_type", e.getKey() == null ? null : e.getKey().name());
      n.put("count", e.getValue());
      summary.add(n);
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.set("by_source_type", summary);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    return ToolResult.ok(String.format("%d material source%s.", matched, matched == 1 ? "" : "s"), wrapper);
  }

  private ToolResult doCatalogue(JsonNode input, UUID projectId, int limit) {
    MaterialCategory catFilter = parseEnum(input.path("category").asText(null), MaterialCategory.class);
    List<Material> all = catFilter != null
        ? materialRepository.findByProjectIdAndCategory(projectId, catFilter)
        : materialRepository.findByProjectId(projectId);
    int matched = all.size();
    if (all.size() > limit) all = all.subList(0, limit);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> materialLinks = new HashSet<>();
    for (Material m : all) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("material_id", m.getId() == null ? null : m.getId().toString());
      n.put("code", m.getCode());
      n.put("name", m.getName());
      n.put("category", m.getCategory() == null ? null : m.getCategory().name());
      n.put("unit", m.getUnit());
      n.put("specification_grade", m.getSpecificationGrade());
      n.put("min_stock_level", m.getMinStockLevel() == null ? null : m.getMinStockLevel().doubleValue());
      n.put("reorder_quantity", m.getReorderQuantity() == null ? null : m.getReorderQuantity().doubleValue());
      n.put("lead_time_days", m.getLeadTimeDays());
      n.put("storage_location", m.getStorageLocation());
      n.put("approved_supplier_id", m.getApprovedSupplierId() == null ? null : m.getApprovedSupplierId().toString());
      n.put("status", m.getStatus() == null ? null : m.getStatus().name());
      rows.add(n);
      materialLinks.add(m.getId());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    if (catFilter != null) wrapper.put("category_filter", catFilter.name());
    ToolResult.attachLinks(wrapper, Map.of("material", new ArrayList<>(materialLinks)));
    return ToolResult.ok(String.format("%d material catalogue entr%s.",
        matched, matched == 1 ? "y" : "ies"), wrapper);
  }

  private ToolResult doConsumption(JsonNode input, UUID projectId, int limit) {
    LocalDate dateTo = parseDate(input.path("date_to").asText(null), LocalDate.now());
    LocalDate dateFrom = parseDate(input.path("date_from").asText(null), dateTo.minusDays(30));
    if (dateFrom.isAfter(dateTo)) { LocalDate t = dateFrom; dateFrom = dateTo; dateTo = t; }
    UUID resourceFilter = parseUuid(input.path("resource_id").asText(null));

    List<MaterialConsumptionLog> base =
        consumptionRepository.findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(
            projectId, dateFrom, dateTo);
    List<MaterialConsumptionLog> filtered = new ArrayList<>();
    for (MaterialConsumptionLog c : base) {
      if (resourceFilter != null && (c.getResourceId() == null || !resourceFilter.equals(c.getResourceId()))) continue;
      filtered.add(c);
    }
    int matched = filtered.size();
    BigDecimal totalConsumed = BigDecimal.ZERO;
    for (MaterialConsumptionLog c : filtered) {
      if (c.getConsumed() != null) totalConsumed = totalConsumed.add(c.getConsumed());
    }
    if (filtered.size() > limit) filtered = filtered.subList(0, limit);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> resourceLinks = new HashSet<>();
    for (MaterialConsumptionLog c : filtered) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("consumption_id", c.getId() == null ? null : c.getId().toString());
      n.put("log_date", c.getLogDate() == null ? null : c.getLogDate().toString());
      n.put("resource_id", c.getResourceId() == null ? null : c.getResourceId().toString());
      n.put("material_name", c.getMaterialName());
      n.put("unit", c.getUnit());
      n.put("opening_stock", c.getOpeningStock() == null ? null : c.getOpeningStock().doubleValue());
      n.put("received", c.getReceived() == null ? null : c.getReceived().doubleValue());
      n.put("consumed", c.getConsumed() == null ? null : c.getConsumed().doubleValue());
      n.put("closing_stock", c.getClosingStock() == null ? null : c.getClosingStock().doubleValue());
      n.put("wastage_percent", c.getWastagePercent() == null ? null : c.getWastagePercent().doubleValue());
      n.put("issued_by", c.getIssuedBy());
      n.put("received_by", c.getReceivedBy());
      n.put("wbs_node_id", c.getWbsNodeId() == null ? null : c.getWbsNodeId().toString());
      n.put("remarks", c.getRemarks());
      rows.add(n);
      if (c.getResourceId() != null) resourceLinks.add(c.getResourceId());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    wrapper.put("total_consumed", totalConsumed.doubleValue());
    ToolResult.attachLinks(wrapper, Map.of("resource", new ArrayList<>(resourceLinks)));
    return ToolResult.ok(String.format(
        "%d consumption row%s between %s and %s, total consumed %s.",
        matched, matched == 1 ? "" : "s", dateFrom, dateTo, totalConsumed), wrapper);
  }

  private ToolResult doReconciliation(JsonNode input, UUID projectId, int limit) {
    String period = orNull(input.path("period").asText(null));
    List<MaterialReconciliation> all = period != null
        ? reconciliationRepository.findByProjectIdAndPeriod(projectId, period)
        : reconciliationRepository.findByProjectId(projectId);
    int matched = all.size();
    if (all.size() > limit) all = all.subList(0, limit);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> resourceLinks = new HashSet<>();
    double totalConsumed = 0;
    double totalWastage = 0;
    for (MaterialReconciliation r : all) {
      if (r.getConsumed() != null) totalConsumed += r.getConsumed();
      if (r.getWastage() != null) totalWastage += r.getWastage();
      ObjectNode n = objectMapper.createObjectNode();
      n.put("reconciliation_id", r.getId() == null ? null : r.getId().toString());
      n.put("resource_id", r.getResourceId() == null ? null : r.getResourceId().toString());
      n.put("wbs_node_id", r.getWbsNodeId() == null ? null : r.getWbsNodeId().toString());
      n.put("period", r.getPeriod());
      n.put("opening_balance", r.getOpeningBalance());
      n.put("received", r.getReceived());
      n.put("consumed", r.getConsumed());
      n.put("wastage", r.getWastage());
      n.put("closing_balance", r.getClosingBalance());
      n.put("unit", r.getUnit());
      n.put("remarks", r.getRemarks());
      rows.add(n);
      if (r.getResourceId() != null) resourceLinks.add(r.getResourceId());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    if (period != null) wrapper.put("period", period);
    wrapper.put("total_consumed", totalConsumed);
    wrapper.put("total_wastage", totalWastage);
    ToolResult.attachLinks(wrapper, Map.of("resource", new ArrayList<>(resourceLinks)));
    return ToolResult.ok(String.format("%d reconciliation row%s%s.", matched,
        matched == 1 ? "" : "s", period != null ? " for period " + period : ""), wrapper);
  }

  private ToolResult doStockRegister(UUID projectId, int limit) {
    List<MaterialStock> all = stockRepository.findByProjectId(projectId);
    int matched = all.size();
    if (all.size() > limit) all = all.subList(0, limit);

    Set<UUID> materialIds = new HashSet<>();
    for (MaterialStock s : all) if (s.getMaterialId() != null) materialIds.add(s.getMaterialId());
    Map<UUID, Material> materialById = new HashMap<>();
    if (!materialIds.isEmpty()) {
      materialRepository.findAllById(materialIds).forEach(m -> materialById.put(m.getId(), m));
    }

    ArrayNode rows = objectMapper.createArrayNode();
    BigDecimal totalValue = BigDecimal.ZERO;
    for (MaterialStock s : all) {
      Material m = s.getMaterialId() == null ? null : materialById.get(s.getMaterialId());
      ObjectNode n = objectMapper.createObjectNode();
      n.put("stock_id", s.getId() == null ? null : s.getId().toString());
      n.put("material_id", s.getMaterialId() == null ? null : s.getMaterialId().toString());
      n.put("material_code", m == null ? null : m.getCode());
      n.put("material_name", m == null ? null : m.getName());
      n.put("category", m == null || m.getCategory() == null ? null : m.getCategory().name());
      n.put("unit", m == null ? null : m.getUnit());
      n.put("opening_stock", s.getOpeningStock() == null ? null : s.getOpeningStock().doubleValue());
      n.put("received_month", s.getReceivedMonth() == null ? null : s.getReceivedMonth().doubleValue());
      n.put("issued_month", s.getIssuedMonth() == null ? null : s.getIssuedMonth().doubleValue());
      n.put("current_stock", s.getCurrentStock() == null ? null : s.getCurrentStock().doubleValue());
      n.put("cumulative_consumed", s.getCumulativeConsumed() == null ? null : s.getCumulativeConsumed().doubleValue());
      n.put("wastage_percent", s.getWastagePercent() == null ? null : s.getWastagePercent().doubleValue());
      n.put("stock_value", s.getStockValue() == null ? null : s.getStockValue().doubleValue());
      n.put("stock_status_tag", s.getStockStatusTag() == null ? null : s.getStockStatusTag().name());
      n.put("min_stock_level", m == null || m.getMinStockLevel() == null ? null : m.getMinStockLevel().doubleValue());
      n.put("last_issue_date", s.getLastIssueDate() == null ? null : s.getLastIssueDate().toString());
      rows.add(n);
      if (s.getStockValue() != null) totalValue = totalValue.add(s.getStockValue());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    wrapper.put("total_stock_value", totalValue.doubleValue());
    return ToolResult.ok(String.format("%d stock register row%s, total value %s.",
        matched, matched == 1 ? "" : "s", totalValue), wrapper);
  }

  private ToolResult doGrns(JsonNode input, UUID projectId, int limit) {
    LocalDate dateTo = parseDate(input.path("date_to").asText(null), null);
    LocalDate dateFrom = parseDate(input.path("date_from").asText(null), null);
    UUID materialFilter = parseUuid(input.path("material_id").asText(null));
    UUID supplierFilter = parseUuid(input.path("supplier_organisation_id").asText(null));

    List<GoodsReceiptNote> base;
    if (dateFrom != null && dateTo != null) {
      if (dateFrom.isAfter(dateTo)) { LocalDate t = dateFrom; dateFrom = dateTo; dateTo = t; }
      base = grnRepository.findByProjectIdAndReceivedDateBetween(projectId, dateFrom, dateTo);
    } else {
      base = grnRepository.findByProjectIdOrderByReceivedDateDesc(projectId);
    }
    List<GoodsReceiptNote> filtered = new ArrayList<>();
    for (GoodsReceiptNote g : base) {
      if (materialFilter != null && !materialFilter.equals(g.getMaterialId())) continue;
      if (supplierFilter != null && !supplierFilter.equals(g.getSupplierOrganisationId())) continue;
      filtered.add(g);
    }
    int matched = filtered.size();
    BigDecimal totalQty = BigDecimal.ZERO;
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (GoodsReceiptNote g : filtered) {
      if (g.getQuantity() != null) totalQty = totalQty.add(g.getQuantity());
      if (g.getAmount() != null) totalAmount = totalAmount.add(g.getAmount());
    }
    if (filtered.size() > limit) filtered = filtered.subList(0, limit);

    Set<UUID> matIds = new HashSet<>();
    for (GoodsReceiptNote g : filtered) if (g.getMaterialId() != null) matIds.add(g.getMaterialId());
    Map<UUID, Material> matById = new HashMap<>();
    if (!matIds.isEmpty()) materialRepository.findAllById(matIds).forEach(m -> matById.put(m.getId(), m));

    ArrayNode rows = objectMapper.createArrayNode();
    for (GoodsReceiptNote g : filtered) {
      Material m = g.getMaterialId() == null ? null : matById.get(g.getMaterialId());
      ObjectNode n = objectMapper.createObjectNode();
      n.put("grn_id", g.getId() == null ? null : g.getId().toString());
      n.put("grn_number", g.getGrnNumber());
      n.put("received_date", g.getReceivedDate() == null ? null : g.getReceivedDate().toString());
      n.put("material_id", g.getMaterialId() == null ? null : g.getMaterialId().toString());
      n.put("material_code", m == null ? null : m.getCode());
      n.put("material_name", m == null ? null : m.getName());
      n.put("quantity", g.getQuantity() == null ? null : g.getQuantity().doubleValue());
      n.put("unit_rate", g.getUnitRate() == null ? null : g.getUnitRate().doubleValue());
      n.put("amount", g.getAmount() == null ? null : g.getAmount().doubleValue());
      n.put("supplier_organisation_id",
          g.getSupplierOrganisationId() == null ? null : g.getSupplierOrganisationId().toString());
      n.put("po_number", g.getPoNumber());
      n.put("vehicle_number", g.getVehicleNumber());
      n.put("accepted_quantity", g.getAcceptedQuantity() == null ? null : g.getAcceptedQuantity().doubleValue());
      n.put("rejected_quantity", g.getRejectedQuantity() == null ? null : g.getRejectedQuantity().doubleValue());
      n.put("remarks", g.getRemarks());
      rows.add(n);
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    wrapper.put("total_quantity", totalQty.doubleValue());
    wrapper.put("total_amount", totalAmount.doubleValue());
    if (dateFrom != null) wrapper.put("date_from", dateFrom.toString());
    if (dateTo != null) wrapper.put("date_to", dateTo.toString());
    return ToolResult.ok(String.format("%d GRN%s, total qty %s, total amount %s.",
        matched, matched == 1 ? "" : "s", totalQty, totalAmount), wrapper);
  }

  // --- helpers -------------------------------------------------------------

  private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static LocalDate parseDate(String raw, LocalDate fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return LocalDate.parse(raw.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
