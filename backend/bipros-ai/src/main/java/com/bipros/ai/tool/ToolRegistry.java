package com.bipros.ai.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolRegistry {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(Collection<Tool> toolBeans) {
        for (Tool t : toolBeans) {
            tools.put(t.name(), t);
        }
    }

    @PostConstruct
    public void init() {
        log.info("ToolRegistry loaded {} tools: {}", tools.size(), tools.keySet());
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /**
     * Returns the tools visible to the given profile. Tools with an empty
     * {@link Tool#allowedRoles()} are always included. SYSTEM_ADMIN sees every tool.
     */
    public List<Tool> toolsForProfile(String profileCode) {
        if (SYSTEM_ADMIN.equals(profileCode)) {
            return List.copyOf(tools.values());
        }
        return tools.values().stream()
                .filter(t -> t.allowedRoles().isEmpty()
                        || (profileCode != null && t.allowedRoles().contains(profileCode)))
                .toList();
    }

    /**
     * Defense-in-depth check used by the orchestrator before executing a
     * tool the LLM picked. Unknown tool names return {@code true} so the
     * existing "Unknown tool" error path in the orchestrator still fires.
     */
    public boolean isAllowed(String toolName, String profileCode) {
        Tool t = tools.get(toolName);
        if (t == null) return true;
        if (SYSTEM_ADMIN.equals(profileCode)) return true;
        return t.allowedRoles().isEmpty()
                || (profileCode != null && t.allowedRoles().contains(profileCode));
    }
}
