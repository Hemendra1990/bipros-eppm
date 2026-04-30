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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastCompletionTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "forecast_completion";
    }

    @Override
    public String description() {
        return "Forecast project completion date and EAC based on EVM trend. Uses CPI/SPI over last 30 days.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        ArrayNode enumValues = objectMapper.createArrayNode();
        enumValues.add("cpi");
        enumValues.add("sci");
        enumValues.add("manual");
        ObjectNode methodNode = objectMapper.createObjectNode();
        methodNode.put("type", "string");
        methodNode.set("enum", enumValues);
        methodNode.put("default", "cpi");
        props.set("method", methodNode);
        ObjectNode confNode = objectMapper.createObjectNode();
        confNode.put("type", "number");
        confNode.put("minimum", 0.5);
        confNode.put("maximum", 0.99);
        confNode.put("default", 0.8);
        props.set("confidence", confNode);
        schema.set("properties", props);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        String method = input.path("method").asText("cpi");

        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();

        String sql = """
            SELECT avg(cpi) as avg_cpi, avg(spi) as avg_spi,
                   sum(ev) as total_ev, sum(ac) as total_ac, sum(pv) as total_pv,
                   max(bac) as bac
            FROM bipros_analytics.fact_evm_daily
            WHERE project_id = :projectId AND date BETWEEN :from AND :to
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", ctx.projectId());
        params.put("from", from);
        params.put("to", to);

        List<Map<String, Object>> rows = clickHouse.queryForList(sql, params);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        Double avgCpi = toDouble(row.get("avg_cpi"));
        Double avgSpi = toDouble(row.get("avg_spi"));
        BigDecimal totalEv = toBigDecimal(row.get("total_ev"));
        BigDecimal totalAc = toBigDecimal(row.get("total_ac"));
        BigDecimal bac = toBigDecimal(row.get("bac"));

        BigDecimal eac = BigDecimal.ZERO;
        String explanation = "No EVM data available for forecasting.";

        if (avgCpi != null && avgCpi > 0 && bac.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cpiBd = BigDecimal.valueOf(avgCpi);
            if ("cpi".equals(method)) {
                eac = totalAc.add(bac.subtract(totalEv).divide(cpiBd, 2, RoundingMode.HALF_UP));
                explanation = "EAC = AC + (BAC - EV) / CPI. Based on last 30 days trend.";
            } else if ("sci".equals(method) && avgSpi != null && avgSpi > 0) {
                BigDecimal sci = cpiBd.multiply(BigDecimal.valueOf(avgSpi));
                eac = totalAc.add(bac.subtract(totalEv).divide(sci, 2, RoundingMode.HALF_UP));
                explanation = "EAC = AC + (BAC - EV) / (CPI * SPI). Based on last 30 days trend.";
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("method", method);
        result.put("avg_cpi", avgCpi);
        result.put("avg_spi", avgSpi);
        result.put("bac", bac.doubleValue());
        result.put("eac", eac.doubleValue());
        result.put("total_ev", totalEv.doubleValue());
        result.put("total_ac", totalAc.doubleValue());
        result.put("explanation", explanation);

        return ToolResult.ok(explanation, result);
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }
}
