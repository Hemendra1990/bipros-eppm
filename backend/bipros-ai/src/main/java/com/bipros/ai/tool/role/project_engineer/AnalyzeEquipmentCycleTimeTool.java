package com.bipros.ai.tool.role.project_engineer;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class AnalyzeEquipmentCycleTimeTool extends ProjectScopedTool {

    private final ObjectMapper objectMapper;

    @Override public String name() { return "analyze_equipment_cycle_time"; }

    @Override public String description() {
        return "Analyse equipment cycle times (excavator-dumper pairs, etc.). Currently NOT supported "
                + "because per-cycle start/end timestamps are not yet captured on EquipmentLog. The tool "
                + "responds with a structured data_unavailable explaining what would be needed.";
    }

    @Override public JsonNode inputSchema() {
        ObjectNode s = objectMapper.createObjectNode(); s.put("type", "object");
        s.set("properties", objectMapper.createObjectNode());
        return s;
    }

    @Override public Set<String> allowedRoles() {
        return Set.of("PROJECT_ENGINEER", "PROJECT_MANAGER", "SITE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason",
                "Equipment cycle start/end timestamps are not yet captured anywhere in the system.");
        payload.put("what_would_be_needed",
                "Add cycle_start_at and cycle_end_at to EquipmentLog (or introduce a new CycleEvent entity) "
                        + "and emit those fields on DPR equipment lines.");
        payload.put("closest_available", "analyze_machine_idle_time");
        return ToolResult.ok(
                "Equipment cycle-time analysis isn't supported yet — the system doesn't yet capture per-cycle timestamps.",
                payload);
    }
}
