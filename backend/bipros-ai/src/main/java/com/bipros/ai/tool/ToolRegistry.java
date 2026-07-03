package com.bipros.ai.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ToolRegistry {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    // OpenAI rejects tool names that don't match this pattern, so fail fast at boot
    // rather than at the first chat request that lands on the offending tool.
    private static final Pattern VALID_TOOL_NAME = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(Collection<Tool> toolBeans) {
        for (Tool t : toolBeans) {
            String name = t.name();
            if (name == null || !VALID_TOOL_NAME.matcher(name).matches()) {
                throw new IllegalStateException(
                        "Tool " + t.getClass().getName() + " has invalid name '" + name
                                + "'. Tool names must match ^[a-zA-Z0-9_-]+$ (OpenAI tool name regex).");
            }
            tools.put(name, t);
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
        return toolsForProfile(profileCode, null);
    }

    /**
     * Role-aware overload. In addition to the {@code SYSTEM_ADMIN} profile, a
     * caller with the {@code ADMIN} role is treated as a superuser and sees
     * every tool — mirroring how tool <em>execution</em> already bypasses the
     * project-scope check for {@code ADMIN}. This keeps an admin from silently
     * losing role-restricted tools when their profile does not resolve (e.g. the
     * seeded admin authenticates with a username principal, not a UUID).
     */
    public List<Tool> toolsForProfile(String profileCode, String role) {
        if (isSuperuser(profileCode, role)) {
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
        return isAllowed(toolName, profileCode, null);
    }

    /** Role-aware overload of {@link #isAllowed(String, String)}. */
    public boolean isAllowed(String toolName, String profileCode, String role) {
        Tool t = tools.get(toolName);
        if (t == null) return true;
        if (isSuperuser(profileCode, role)) return true;
        return t.allowedRoles().isEmpty()
                || (profileCode != null && t.allowedRoles().contains(profileCode));
    }

    private static boolean isSuperuser(String profileCode, String role) {
        return SYSTEM_ADMIN.equals(profileCode) || "ADMIN".equals(role);
    }
}
