package com.bipros.ai.insights.kpi;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.charts.EChartsOptions;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.ResourceEquipmentDetailsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentKpiInsightsCollector implements InsightDataCollector {

  private static final int DEFAULT_LOOKBACK_DAYS = 30;

  private final EquipmentLogRepository equipmentLogRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceEquipmentDetailsRepository equipmentDetailsRepository;
  private final ObjectMapper objectMapper;

  @Override
  public JsonNode collect(UUID projectId) {
    LocalDate today = LocalDate.now();
    LocalDate from = today.minusDays(DEFAULT_LOOKBACK_DAYS);

    List<EquipmentLog> logs = equipmentLogRepository
        .findByProjectIdAndLogDateBetween(projectId, from, today);
    Map<UUID, Resource> resourcesById = resourceRepository
        .findAllById(logs.stream().map(EquipmentLog::getResourceId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));
    Map<UUID, ResourceEquipmentDetails> detailsById = equipmentDetailsRepository
        .findAllById(resourcesById.keySet()).stream()
        .collect(Collectors.toMap(ResourceEquipmentDetails::getResourceId, d -> d, (a, b) -> a));

    ObjectNode root = objectMapper.createObjectNode();
    root.put("projectId", projectId.toString());
    root.put("from", from.toString());
    root.put("to", today.toString());
    root.put("logRows", logs.size());
    root.put("equipmentTracked", resourcesById.size());

    Map<UUID, double[]> agg = new HashMap<>(); // [op, idle, breakdown, fuel]
    for (EquipmentLog l : logs) {
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[4]);
      if (l.getOperatingHours() != null) acc[0] += l.getOperatingHours();
      if (l.getIdleHours() != null) acc[1] += l.getIdleHours();
      if (l.getBreakdownHours() != null) acc[2] += l.getBreakdownHours();
      if (l.getFuelConsumed() != null) acc[3] += l.getFuelConsumed();
    }

    ArrayNode equipment = root.putArray("equipment");
    agg.entrySet().stream()
        .sorted((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1])) // sort by idle hours desc
        .limit(15)
        .forEach(e -> {
          Resource r = resourcesById.get(e.getKey());
          ResourceEquipmentDetails d = detailsById.get(e.getKey());
          double[] v = e.getValue();
          double total = v[0] + v[1] + v[2];
          double util = total > 0 ? v[0] / total : 0d;

          ObjectNode node = objectMapper.createObjectNode();
          node.put("resourceCode", r != null ? r.getCode() : "?");
          node.put("resourceName", r != null ? r.getName() : "Unknown");
          node.put("operatingHours", v[0]);
          node.put("idleHours", v[1]);
          node.put("breakdownHours", v[2]);
          node.put("fuelConsumed", v[3]);
          node.put("utilizationPct", util);
          if (d != null) {
            if (d.getOwnershipType() != null) node.put("ownershipType", d.getOwnershipType().name());
            if (d.getNextServiceDate() != null) node.put("nextServiceDate", d.getNextServiceDate().toString());
          }
          equipment.add(node);
        });

    LocalDate latest = logs.stream().map(EquipmentLog::getLogDate).max(java.util.Comparator.naturalOrder()).orElse(null);
    if (latest != null) {
      ArrayNode idleAlerts = root.putArray("idleAlertsLatestDay");
      logs.stream()
          .filter(l -> latest.equals(l.getLogDate()))
          .filter(l -> l.getIdleHours() != null && l.getIdleHours() > 2d)
          .forEach(l -> {
            Resource r = resourcesById.get(l.getResourceId());
            ObjectNode node = objectMapper.createObjectNode();
            node.put("resourceCode", r != null ? r.getCode() : "?");
            node.put("idleHours", l.getIdleHours());
            idleAlerts.add(node);
          });
      root.put("latestLogDate", latest.toString());
    }

    return root;
  }

  @Override
  public String tabKey() {
    return "equipment-kpi";
  }

  @Override
  public String promptInstructions() {
    return "Produce equipment-utilisation insights for site managers and PEs. Identify "
        + "machines with chronically high idle hours, anomalously high fuel-per-output, and "
        + "machines approaching their next service date. Recommend deployment changes, "
        + "service scheduling, or fuel-efficiency reviews. Tie recommendations to specific "
        + "equipment by resource code where possible.";
  }

  @Override
  public List<ChartSpec> charts(UUID projectId) {
    if (projectId == null) {
      return List.of(
          new ChartSpec("equipment-utilization", "Equipment Utilisation %", "bar", null, null),
          new ChartSpec("equipment-idle-hours", "Idle Hours by Machine", "bar", null, null)
      );
    }

    JsonNode root = collect(projectId);
    JsonNode equipment = root.get("equipment");
    if (equipment == null || !equipment.isArray()) return List.of();

    Map<String, BigDecimal> utilByMachine = new LinkedHashMap<>();
    Map<String, BigDecimal> idleByMachine = new LinkedHashMap<>();
    equipment.forEach(node -> {
      String code = node.get("resourceCode").asText();
      utilByMachine.put(code, BigDecimal.valueOf(node.get("utilizationPct").asDouble()));
      idleByMachine.put(code, BigDecimal.valueOf(node.get("idleHours").asDouble()));
    });

    return List.of(
        new ChartSpec("equipment-utilization", "Equipment Utilisation %", "bar",
            EChartsOptions.bar(objectMapper,
                new java.util.ArrayList<>(utilByMachine.keySet()),
                "Utilisation",
                new java.util.ArrayList<>(utilByMachine.values())),
            "Operating hours / total tracked hours"),
        new ChartSpec("equipment-idle-hours", "Idle Hours by Machine", "bar",
            EChartsOptions.bar(objectMapper,
                new java.util.ArrayList<>(idleByMachine.keySet()),
                "Idle Hours",
                new java.util.ArrayList<>(idleByMachine.values())),
            "Top 15 machines by cumulative idle hours")
    );
  }
}
