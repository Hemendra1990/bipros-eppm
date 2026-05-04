package com.bipros.ai.insights;

import com.bipros.ai.insights.dto.ChartSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public interface InsightDataCollector {
    JsonNode collect(UUID projectId);
    String tabKey();
    String promptInstructions();

    default List<ChartSpec> charts(UUID projectId) {
        return List.of();
    }

    default String chartAwarePromptInstructions() {
        List<ChartSpec> chartList = charts(null);
        if (chartList.isEmpty()) {
            return promptInstructions();
        }
        String ids = chartList.stream()
                .map(c -> "\"" + c.id() + "\"")
                .collect(Collectors.joining(", "));
        return promptInstructions()
                + "\n\nThe user already sees these chart IDs on screen: [" + ids + "]. "
                + "Write your `mdx` narrative as 1–3 short sentences that reference these charts "
                + "using <Chart id=\"id-here\"/> and add inline <Kpi.../> or <Note.../> components. "
                + "Keep prose minimal; the charts carry the data.";
    }
}
