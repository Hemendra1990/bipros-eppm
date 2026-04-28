package com.bipros.analytics.application.tool;

import com.fasterxml.jackson.databind.JsonNode;

/** Serializable description sent to the LLM in the system prompt. */
public record ToolDescriptor(String name, String description, JsonNode inputSchema) {}
