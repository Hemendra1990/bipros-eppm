package com.bipros.ai.job;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for AI generation jobs. Sized so that:
 * <ul>
 *   <li>up to 8 jobs run concurrently — matches typical OpenAI per-account
 *       request concurrency without saturating the LLM provider.</li>
 *   <li>up to 32 are queued — gives a small reserve for traffic spikes.</li>
 *   <li>requests beyond that get a clean 429 from the controller, not a
 *       silent thread starvation.</li>
 * </ul>
 * Tomcat threads are no longer parked during the LLM call: the controller
 * submits, returns 202, and is back in the pool within milliseconds.
 */
@Configuration
public class WbsAiAsyncConfig {

    @Bean(name = "wbsAiExecutor")
    public ThreadPoolTaskExecutor wbsAiExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(32);
        exec.setThreadNamePrefix("wbs-ai-");
        // AbortPolicy raises RejectedExecutionException; the controller catches
        // it and returns 429 JOB_QUEUE_FULL so the user gets actionable feedback.
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Don't wait on shutdown: the worker writes progress to the DB; if the
        // app stops mid-job the row stays in RUNNING and a future cleanup
        // pass (Phase D) reaps it.
        exec.setWaitForTasksToCompleteOnShutdown(false);
        exec.initialize();
        return exec;
    }

    /** Marker re-exported for callers that want to recognize a queue-full rejection. */
    public static boolean isQueueFull(Throwable t) {
        return t instanceof RejectedExecutionException;
    }
}
