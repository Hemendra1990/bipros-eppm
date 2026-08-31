package com.bipros.ai.agent.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor backing parallel agent execution within a pipeline stage.
 *
 * <p>Mirrors {@code DbsRecomputeConfig}: a bounded pool (size 4) kept well below the Hikari max so
 * agents always get a DB connection. {@code AgentPipelineRunner} submits one task per agent in a
 * stage and joins before advancing to the next stage. Kept small — agent runs are I/O + one LLM
 * narration each, and the reactive pipelines are short.
 */
@Configuration
public class AgentExecutorConfig {

    @Bean("agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1_000);
        ex.setThreadNamePrefix("agent-task-");
        ex.initialize();
        return ex;
    }
}
