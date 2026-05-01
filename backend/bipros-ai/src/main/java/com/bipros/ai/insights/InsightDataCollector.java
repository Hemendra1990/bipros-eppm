package com.bipros.ai.insights;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface InsightDataCollector {
    JsonNode collect(UUID projectId);
    String tabKey();
    String promptInstructions();
}
