package com.bipros.analytics.application.tool;

import java.util.List;
import java.util.Map;

/**
 * Generic shape returned by every tool. {@code sqlExecuted} is non-null only for the
 * {@code execute_sql} fallback (rendered as a "Show SQL" disclosure on the frontend).
 */
public record ToolResult(
        String narrative,
        List<String> columns,
        List<Map<String, Object>> rows,
        String sqlExecuted
) {
    public static ToolResult empty(String narrative) {
        return new ToolResult(narrative, List.of(), List.of(), null);
    }
}
