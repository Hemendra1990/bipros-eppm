package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeRiskTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "analyze_risk";
    }

    @Override
    public String description() {
        return "Analyze project risks. Aggregates fact_risk_snapshot_daily by category, owner, response_type, risk_type, or rag. Returns counts, exposure totals, and average / max risk scores.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("period", objectMapper.createObjectNode().put("type", "string")
                .put("description", "month identifier like 2024-01 or 'current'"));

        ArrayNode groupByEnum = objectMapper.createArrayNode();
        groupByEnum.add("category");
        groupByEnum.add("owner");
        groupByEnum.add("response_type");
        groupByEnum.add("risk_type");
        groupByEnum.add("rag");
        ObjectNode groupBy = objectMapper.createObjectNode();
        groupBy.put("type", "string");
        groupBy.set("enum", groupByEnum);
        props.set("group_by", groupBy);

        ArrayNode typeFilterEnum = objectMapper.createArrayNode();
        typeFilterEnum.add("THREAT");
        typeFilterEnum.add("OPPORTUNITY");
        ObjectNode typeFilter = objectMapper.createObjectNode();
        typeFilter.put("type", "string");
        typeFilter.set("enum", typeFilterEnum);
        props.set("risk_type_filter", typeFilter);

        props.set("top_n", objectMapper.createObjectNode()
                .put("type", "integer").put("minimum", 1).put("maximum", 50));

        schema.set("properties", props);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        String period = input.path("period").asText("current");
        String groupBy = input.path("group_by").asText("category");
        String typeFilter = input.path("risk_type_filter").asText(null);
        int topN = input.path("top_n").asInt(10);

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();
        if (!"current".equals(period)) {
            try {
                from = LocalDate.parse(period + "-01");
                to = from.withDayOfMonth(from.lengthOfMonth());
            } catch (Exception ignored) {
            }
        }

        String groupExpr = switch (groupBy) {
            case "owner" -> "toString(f.owner_id)";
            case "response_type" -> "f.response_type";
            case "risk_type" -> "f.risk_type";
            case "rag" -> "f.rag";
            default -> "coalesce(d.category_name, '')";
        };

        StringBuilder where = new StringBuilder("WHERE f.project_id = :projectId AND f.date BETWEEN :from AND :to");
        if (typeFilter != null && !typeFilter.isBlank()) {
            where.append(" AND f.risk_type = :riskTypeFilter");
        }

        String sql = """
            SELECT %s as group_key,
                   countDistinct(f.risk_id) as count,
                   countDistinctIf(f.risk_id, f.status NOT IN ('CLOSED','RESOLVED')) as open_count,
                   countDistinctIf(f.risk_id, f.status IN ('CLOSED','RESOLVED')) as closed_count,
                   sum(f.pre_response_exposure_cost) as sum_pre_exposure_cost,
                   sum(f.post_response_exposure_cost) as sum_post_exposure_cost,
                   avg(f.risk_score) as avg_risk_score,
                   max(f.residual_risk_score) as max_residual_score,
                   sum(f.impact_days) as total_impact_days
            FROM bipros_analytics.fact_risk_snapshot_daily f
            LEFT JOIN bipros_analytics.dim_risk d ON d.risk_id = f.risk_id
            %s
            GROUP BY group_key
            ORDER BY sum_pre_exposure_cost DESC NULLS LAST
            LIMIT :topN
            """.formatted(groupExpr, where);

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", ctx.projectId());
        params.put("from", from);
        params.put("to", to);
        params.put("topN", topN);
        if (typeFilter != null && !typeFilter.isBlank()) {
            params.put("riskTypeFilter", typeFilter);
        }

        List<Map<String, Object>> rows = clickHouse.queryForList(sql, params);
        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }

        return ToolResult.table("Risk analysis by " + groupBy, arr,
                new String[]{"group_key", "count", "open_count", "closed_count",
                        "sum_pre_exposure_cost", "sum_post_exposure_cost",
                        "avg_risk_score", "max_residual_score", "total_impact_days"});
    }
}
