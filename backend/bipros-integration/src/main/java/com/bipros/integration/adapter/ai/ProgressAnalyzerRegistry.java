package com.bipros.integration.adapter.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves {@link ProgressAnalyzer} beans by their provider id. */
@Component
@Slf4j
public class ProgressAnalyzerRegistry {

    private final Map<String, ProgressAnalyzer> byProvider = new HashMap<>();

    public ProgressAnalyzerRegistry(List<ProgressAnalyzer> analyzers) {
        for (ProgressAnalyzer a : analyzers) byProvider.put(a.providerId(), a);
        log.info("[AI] registered analyzers: {}", byProvider.keySet());
    }

    public Optional<ProgressAnalyzer> find(String providerId) {
        return Optional.ofNullable(byProvider.get(providerId));
    }

    public Optional<ProgressAnalyzer> preferred() {
        if (byProvider.containsKey("claude-vision")) return Optional.of(byProvider.get("claude-vision"));
        return byProvider.values().stream().findFirst();
    }
}
