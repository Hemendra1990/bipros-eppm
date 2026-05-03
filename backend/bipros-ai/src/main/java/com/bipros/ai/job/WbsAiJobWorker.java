package com.bipros.ai.job;

import com.bipros.ai.document.FilenameSanitizer;
import com.bipros.ai.document.MimeTypeDetector;
import com.bipros.ai.wbs.WbsAiGenerationService;
import com.bipros.ai.wbs.dto.WbsAiGenerateFromDocumentRequest;
import com.bipros.ai.wbs.dto.WbsAiGenerationResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Runs one WBS-from-document job on the bounded {@code wbsAiExecutor} pool.
 *
 * <p>Lifecycle:
 * <pre>
 *   PENDING -> RUNNING -> DONE
 *                       \-> FAILED
 *                       \-> CANCELLED
 * </pre>
 *
 * <p>Each progress / status write is committed in its own transaction (see
 * {@link WbsAiJobService}) so the polling endpoint observes intermediate state.
 * The worker checks {@code cancelRequested} at every stage boundary and aborts
 * cooperatively; in-flight HTTP calls to OpenAI cannot be cancelled server-side,
 * but their results are dropped on completion.
 */
@Component
@Slf4j
public class WbsAiJobWorker {

    private final ThreadPoolTaskExecutor wbsAiExecutor;
    private final WbsAiJobService jobService;
    private final WbsAiGenerationService generationService;
    private final MimeTypeDetector mimeTypeDetector;
    private final ObjectMapper objectMapper;

    public WbsAiJobWorker(
            @Qualifier("wbsAiExecutor") ThreadPoolTaskExecutor wbsAiExecutor,
            WbsAiJobService jobService,
            WbsAiGenerationService generationService,
            MimeTypeDetector mimeTypeDetector,
            ObjectMapper objectMapper) {
        this.wbsAiExecutor = wbsAiExecutor;
        this.jobService = jobService;
        this.generationService = generationService;
        this.mimeTypeDetector = mimeTypeDetector;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a job for async execution. Throws
     * {@link java.util.concurrent.RejectedExecutionException} if the queue is
     * full — the controller translates that to 429 JOB_QUEUE_FULL.
     */
    public void submit(UUID jobId, Path tempFile, String safeFileName,
                        WbsAiGenerateFromDocumentRequest metadata) {
        wbsAiExecutor.execute(() -> run(jobId, tempFile, safeFileName, metadata));
    }

    private void run(UUID jobId, Path tempFile, String safeFileName,
                      WbsAiGenerateFromDocumentRequest metadata) {
        try {
            jobService.markRunning(jobId);
            jobService.updateProgress(jobId, "READING", 10);
            if (jobService.isCancelRequested(jobId)) {
                jobService.markCancelled(jobId);
                return;
            }

            String detectedMime = mimeTypeDetector.detectAndValidate(tempFile, safeFileName);

            jobService.updateProgress(jobId, "ANALYZING", 30);
            if (jobService.isCancelRequested(jobId)) {
                jobService.markCancelled(jobId);
                return;
            }

            WbsAiJob job = jobService.getOrThrow(jobId);
            UUID projectId = job.getProjectId();

            WbsAiGenerationResponse response = generationService.generateFromDocument(
                    projectId, metadata, tempFile, detectedMime, safeFileName);

            // Cancel arrived during the LLM call: drop the result.
            if (jobService.isCancelRequested(jobId)) {
                jobService.markCancelled(jobId);
                return;
            }

            jobService.updateProgress(jobId, "MERGING", 80);
            String resultJson = objectMapper.writeValueAsString(response);
            jobService.markDone(jobId, resultJson, /* tokens populated in Phase D */ null, null, null);
        } catch (BusinessRuleException e) {
            log.warn("Job {} failed: {} — {}", jobId, e.getRuleCode(), e.getMessage());
            jobService.markFailed(jobId, e.getRuleCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Job {} failed unexpectedly", jobId, e);
            jobService.markFailed(jobId, "AI_GENERATION_FAILED", e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
            }
        }
    }
}
