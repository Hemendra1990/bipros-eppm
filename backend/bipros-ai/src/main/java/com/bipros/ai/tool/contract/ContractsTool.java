package com.bipros.ai.tool.contract;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract administration queries. Operations: list, get_details, amendments, status, by_party.
 * <p>
 * "amendments" is mapped onto {@link VariationOrder} — the schema's term for change orders /
 * contract variations. "by_party" filters on {@code contractorName} (case-insensitive substring) —
 * the schema doesn't separate party_id from contractor_name on the contract row itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractsTool implements Tool {

  private final ContractRepository contractRepository;
  private final VariationOrderRepository voRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "contracts";
  }

  @Override
  public String description() {
    return "Use this for contract / package / contractor questions. Operations via op param: 'list' "
        + "(all contracts on the current project, with KPIs SPI/CPI, contract value, package code), "
        + "'get_details' (single contract by contract_id or contract_number, plus its variation "
        + "orders / amendments), 'amendments' (variation orders across the project, optionally for "
        + "a single contract — VO number, value, status, schedule impact), 'status' (count of "
        + "contracts grouped by ContractStatus DRAFT/AWARDED/EXECUTED/etc), 'by_party' (filter by "
        + "contractor_name substring — schema treats contractor_name as the party identifier). "
        + "Examples: 'list contracts', 'show details for AGRA-PKG-1A', 'how many variation orders', "
        + "'contracts with L&T'. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("list");
    opEnum.add("get_details");
    opEnum.add("amendments");
    opEnum.add("status");
    opEnum.add("by_party");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    props.set("op", opNode);
    props.set("contract_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "For op=get_details / amendments (filter to one contract)."));
    props.set("contract_number", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Contract number, e.g. CON-2025-001. Alternative to contract_id."));
    props.set("contractor_name", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Case-insensitive substring filter on contractor_name. For op=by_party."));
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
    if (projectId == null) return ToolResult.error("contracts needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required");

    return switch (op) {
      case "list" -> doList(projectId, null);
      case "by_party" -> doList(projectId, orNull(input.path("contractor_name").asText(null)));
      case "get_details" -> doDetails(input, projectId);
      case "amendments" -> doAmendments(input, projectId);
      case "status" -> doStatus(projectId);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doList(UUID projectId, String partyFilter) {
    List<Contract> all = contractRepository.findByProjectId(projectId);
    if (partyFilter != null) {
      String f = partyFilter.toLowerCase();
      all = all.stream().filter(c -> c.getContractorName() != null && c.getContractorName().toLowerCase().contains(f)).toList();
    }
    ArrayNode rows = objectMapper.createArrayNode();
    List<UUID> ids = new ArrayList<>();
    for (Contract c : all) {
      rows.add(toRow(c));
      ids.add(c.getId());
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("count", all.size());
    if (partyFilter != null) w.put("filtered_contractor", partyFilter);
    if (!ids.isEmpty()) ToolResult.attachLinks(w, Map.of("contract", ids));
    return ToolResult.ok(all.size() + " contract" + (all.size() == 1 ? "" : "s") + (partyFilter == null ? "" : " for " + partyFilter), w);
  }

  private ToolResult doDetails(JsonNode input, UUID projectId) {
    Contract c = resolveContract(input, projectId);
    if (c == null) return ToolResult.error("Provide contract_id or contract_number.");
    List<VariationOrder> vos = voRepository.findByContractId(c.getId());
    ObjectNode contract = toRow(c);
    ArrayNode voRows = objectMapper.createArrayNode();
    for (VariationOrder v : vos) voRows.add(toVoRow(v));
    ObjectNode w = objectMapper.createObjectNode();
    w.set("contract", contract);
    w.set("variation_orders", voRows);
    w.put("vo_count", vos.size());
    Map<String, List<UUID>> links = new java.util.HashMap<>();
    links.put("contract", List.of(c.getId()));
    if (!vos.isEmpty()) {
      List<UUID> voIds = new ArrayList<>();
      for (VariationOrder v : vos) voIds.add(v.getId());
      links.put("variation_order", voIds);
    }
    ToolResult.attachLinks(w, links);
    return ToolResult.ok(c.getContractNumber() + " — " + c.getContractorName() + " · " + vos.size() + " VOs", w);
  }

  private ToolResult doAmendments(JsonNode input, UUID projectId) {
    Contract single = resolveContract(input, projectId);
    List<Contract> contracts = single != null ? List.of(single) : contractRepository.findByProjectId(projectId);
    Map<UUID, Contract> contractById = new java.util.HashMap<>();
    for (Contract c : contracts) contractById.put(c.getId(), c);
    ArrayNode rows = objectMapper.createArrayNode();
    int total = 0;
    for (Contract c : contracts) {
      List<VariationOrder> vos = voRepository.findByContractId(c.getId());
      for (VariationOrder v : vos) {
        ObjectNode n = toVoRow(v);
        n.put("contract_id", c.getId().toString());
        n.put("contract_number", c.getContractNumber());
        n.put("contractor_name", c.getContractorName());
        rows.add(n);
        total++;
      }
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("count", total);
    if (single != null) w.put("filtered_contract", single.getContractNumber());
    return ToolResult.ok(total + " variation order" + (total == 1 ? "" : "s") + (single == null ? "" : " on " + single.getContractNumber()), w);
  }

  private ToolResult doStatus(UUID projectId) {
    List<Contract> all = contractRepository.findByProjectId(projectId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (ContractStatus s : ContractStatus.values()) counts.put(s.name(), 0);
    for (Contract c : all) {
      String s = c.getStatus() == null ? "UNSET" : c.getStatus().name();
      counts.merge(s, 1, Integer::sum);
    }
    ArrayNode rows = objectMapper.createArrayNode();
    for (var e : counts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("status", e.getKey());
      n.put("count", e.getValue());
      rows.add(n);
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("total_contracts", all.size());
    return ToolResult.ok(all.size() + " contracts grouped by status", w);
  }

  private ObjectNode toRow(Contract c) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("contract_id", c.getId().toString());
    n.put("contract_number", c.getContractNumber());
    n.put("loa_number", c.getLoaNumber());
    n.put("contractor_name", c.getContractorName());
    n.put("contractor_code", c.getContractorCode());
    n.put("contract_value", c.getContractValue() == null ? null : c.getContractValue().doubleValue());
    n.put("revised_value", c.getRevisedValue() == null ? null : c.getRevisedValue().doubleValue());
    n.put("status", c.getStatus() == null ? null : c.getStatus().name());
    n.put("contract_type", c.getContractType() == null ? null : c.getContractType().name());
    n.put("currency", c.getCurrency());
    n.put("loa_date", c.getLoaDate() == null ? null : c.getLoaDate().toString());
    n.put("ntp_date", c.getNtpDate() == null ? null : c.getNtpDate().toString());
    n.put("start_date", c.getStartDate() == null ? null : c.getStartDate().toString());
    n.put("completion_date", c.getCompletionDate() == null ? null : c.getCompletionDate().toString());
    n.put("revised_completion_date", c.getRevisedCompletionDate() == null ? null : c.getRevisedCompletionDate().toString());
    n.put("actual_completion_date", c.getActualCompletionDate() == null ? null : c.getActualCompletionDate().toString());
    n.put("wbs_package_code", c.getWbsPackageCode());
    n.put("spi", c.getSpi() == null ? null : c.getSpi().doubleValue());
    n.put("cpi", c.getCpi() == null ? null : c.getCpi().doubleValue());
    n.put("vo_numbers_issued", c.getVoNumbersIssued());
    n.put("vo_value_crores", c.getVoValueCrores() == null ? null : c.getVoValueCrores().doubleValue());
    n.put("performance_score", c.getPerformanceScore() == null ? null : c.getPerformanceScore().doubleValue());
    n.put("bg_expiry", c.getBgExpiry() == null ? null : c.getBgExpiry().toString());
    return n;
  }

  private ObjectNode toVoRow(VariationOrder v) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("vo_id", v.getId().toString());
    n.put("vo_number", v.getVoNumber());
    n.put("description", v.getDescription());
    n.put("vo_value", v.getVoValue() == null ? null : v.getVoValue().doubleValue());
    n.put("status", v.getStatus() == null ? null : v.getStatus().name());
    n.put("impact_on_budget", v.getImpactOnBudget() == null ? null : v.getImpactOnBudget().doubleValue());
    n.put("impact_on_schedule_days", v.getImpactOnScheduleDays());
    n.put("approved_by", v.getApprovedBy());
    n.put("approved_at", v.getApprovedAt() == null ? null : v.getApprovedAt().toString());
    return n;
  }

  private Contract resolveContract(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("contract_id").asText(null));
    if (idStr != null) {
      try {
        UUID id = UUID.fromString(idStr);
        return contractRepository.findById(id).filter(c -> projectId.equals(c.getProjectId())).orElse(null);
      } catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    String num = orNull(input.path("contract_number").asText(null));
    if (num != null) {
      return contractRepository.findByContractNumber(num).filter(c -> projectId.equals(c.getProjectId())).orElse(null);
    }
    return null;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
