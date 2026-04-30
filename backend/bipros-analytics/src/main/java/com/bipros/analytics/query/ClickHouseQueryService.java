package com.bipros.analytics.query;

import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.common.security.ProjectAccessGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseQueryService {

    private final ClickHouseTemplate clickHouse;
    private final SqlGuard sqlGuard;
    private final ProjectAccessGuard projectAccess;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryResult runGuarded(String sql, List<UUID> scopedProjectIds, Integer rowLimit) {
        sqlGuard.validate(sql, scopedProjectIds.stream().map(UUID::toString).toList());

        int limit = rowLimit != null ? Math.min(rowLimit, 5000) : 500;
        String limitedSql = sql;
        if (!sql.toLowerCase().contains("limit")) {
            limitedSql = sql + " LIMIT " + limit;
        }

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = clickHouse.queryForList(limitedSql, Map.of());
        long latency = System.currentTimeMillis() - start;

        boolean truncated = rows.size() >= limit;

        ArrayNode rowArray = objectMapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode obj = objectMapper.createObjectNode();
            row.forEach((k, v) -> obj.set(k, objectMapper.valueToTree(v)));
            rowArray.add(obj);
        }

        return new QueryResult(rowArray, rows.size(), truncated, latency);
    }

    public record QueryResult(ArrayNode rows, int rowCount, boolean truncated, long latencyMs) {
    }
}
