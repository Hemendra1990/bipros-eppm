package com.bipros.analytics.application.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers all {@code @AnalyticsTool}-annotated handler beans at boot and indexes them
 * by name. The orchestrator calls {@link #descriptors()} to populate the LLM system
 * prompt and {@link #get(String)} to look up the handler the LLM picked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolRegistry {

    private final List<AnalyticsToolHandler<?>> handlers;
    private final Map<String, AnalyticsToolHandler<?>> byName = new HashMap<>();
    private final Map<String, ToolDescriptor> descriptors = new HashMap<>();

    @PostConstruct
    void init() {
        for (AnalyticsToolHandler<?> h : handlers) {
            AnalyticsTool meta = h.getClass().getAnnotation(AnalyticsTool.class);
            if (meta == null) {
                throw new IllegalStateException("Handler " + h.getClass().getName()
                        + " missing @AnalyticsTool annotation");
            }
            if (!meta.name().equals(h.name())) {
                throw new IllegalStateException("Handler " + h.getClass().getName()
                        + " annotation name=" + meta.name()
                        + " does not match handler.name()=" + h.name());
            }
            byName.put(meta.name(), h);
            descriptors.put(meta.name(),
                    new ToolDescriptor(meta.name(), meta.description(), h.inputSchema()));
        }
        log.info("ToolRegistry initialized with {} tools: {}", byName.size(), byName.keySet());
    }

    public AnalyticsToolHandler<?> get(String name) {
        return byName.get(name);
    }

    public Collection<ToolDescriptor> descriptors() {
        return descriptors.values();
    }

    public boolean has(String name) {
        return byName.containsKey(name);
    }
}
