package com.bipros.analytics.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseTemplate {

    private final JdbcTemplate clickHouseJdbcTemplate;
    private final NamedParameterJdbcTemplate clickHouseNamedParameterJdbcTemplate;

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
        log.debug("ClickHouse query: {}", sql);
        return clickHouseJdbcTemplate.query(sql, rowMapper);
    }

    public <T> List<T> query(String sql, Map<String, Object> params, RowMapper<T> rowMapper) throws DataAccessException {
        log.debug("ClickHouse query: {} params={}", sql, params);
        return clickHouseNamedParameterJdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, Object> params) throws DataAccessException {
        log.debug("ClickHouse queryForList: {} params={}", sql, params);
        if (params == null || params.isEmpty()) {
            return clickHouseJdbcTemplate.queryForList(sql);
        }
        return clickHouseNamedParameterJdbcTemplate.queryForList(sql, params);
    }

    public int execute(String sql) throws DataAccessException {
        log.debug("ClickHouse execute: {}", sql);
        return clickHouseJdbcTemplate.update(sql);
    }

    public int execute(String sql, Map<String, Object> params) throws DataAccessException {
        log.debug("ClickHouse execute: {} params={}", sql, params);
        return clickHouseNamedParameterJdbcTemplate.update(sql, params);
    }

    public void batchInsert(String table, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Simple batch insert via NamedParameterJdbcTemplate batchUpdate
        // For production, consider async ClickHouse native batch insert
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        Map<String, Object> first = rows.get(0);
        String columns = String.join(", ", first.keySet());
        sql.append(columns).append(") VALUES (");
        String placeholders = String.join(", ", first.keySet().stream().map(k -> ":" + k).toList());
        sql.append(placeholders).append(")");

        @SuppressWarnings("unchecked")
        Map<String, Object>[] batch = rows.toArray(new Map[0]);
        clickHouseNamedParameterJdbcTemplate.batchUpdate(sql.toString(), batch);
        log.debug("ClickHouse batchInsert: {} rows into {}", rows.size(), table);
    }
}
