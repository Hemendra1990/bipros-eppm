package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Base class for tools that operate within a project scope.
 * Automatically validates project access before execution.
 */
public abstract class ProjectScopedTool implements Tool {

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID pid = ctx.projectId();
        if (pid != null && !"ADMIN".equals(ctx.role()) && !ctx.scopedProjectIds().contains(pid)) {
            throw new AccessDeniedException("project not in user scope");
        }
        return doExecute(input, ctx);
    }

    protected abstract ToolResult doExecute(JsonNode input, AiContext ctx);
}
