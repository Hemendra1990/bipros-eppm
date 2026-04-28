package com.bipros.analytics.application.tool;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an analytics tool the orchestrator can dispatch. The class must also
 * implement {@link AnalyticsToolHandler}. Meta-annotated with {@code @Component} so
 * Spring auto-discovers handlers; the {@link ToolRegistry} indexes them by {@link #name()}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AnalyticsTool {

    /** Tool name as exposed to the LLM. Must match {@link AnalyticsToolHandler#name()}. */
    String name();

    /** Short human-readable description sent to the LLM in the system prompt. */
    String description();
}
