package com.bipros.ai.job;

import com.bipros.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * CRUD + state-transition surface around {@link WbsAiJob}.
 *
 * <p>Each progress write is in {@link Propagation#REQUIRES_NEW} so the
 * polling endpoint always sees the latest state — the worker's outer
 * transaction (if any) does not hide intermediate writes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbsAiJobService {

    private final WbsAiJobRepository repository;

    @Transactional
    public WbsAiJob createPending(WbsAiJob seed) {
        seed.setStatus(WbsAiJobStatus.PENDING);
        return repository.save(seed);
    }

    @Transactional(readOnly = true)
    public WbsAiJob getOrThrow(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new BusinessRuleException("JOB_NOT_FOUND", "Job not found: " + jobId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID jobId) {
        WbsAiJob j = getOrThrow(jobId);
        j.setStatus(WbsAiJobStatus.RUNNING);
        j.setStartedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID jobId, String stage, int pct) {
        WbsAiJob j = getOrThrow(jobId);
        j.setProgressStage(stage);
        j.setProgressPct(pct);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(UUID jobId, String resultJson, Integer inputTokens,
                          Integer outputTokens, String model) {
        WbsAiJob j = getOrThrow(jobId);
        j.setStatus(WbsAiJobStatus.DONE);
        j.setProgressStage("DONE");
        j.setProgressPct(100);
        j.setResultJson(resultJson);
        j.setInputTokens(inputTokens);
        j.setOutputTokens(outputTokens);
        j.setModel(model);
        j.setCompletedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String code, String message) {
        WbsAiJob j = getOrThrow(jobId);
        j.setStatus(WbsAiJobStatus.FAILED);
        j.setErrorCode(code);
        j.setErrorMessage(message);
        j.setCompletedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(UUID jobId) {
        WbsAiJob j = getOrThrow(jobId);
        j.setStatus(WbsAiJobStatus.CANCELLED);
        j.setCompletedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestCancel(UUID jobId) {
        WbsAiJob j = getOrThrow(jobId);
        // Cooperative cancel: the worker checks this flag at progress checkpoints
        // and aborts. An in-flight HTTP call to the LLM cannot be aborted server-side,
        // but the result is dropped on completion.
        j.setCancelRequested(true);
    }

    @Transactional(readOnly = true)
    public boolean isCancelRequested(UUID jobId) {
        return repository.findById(jobId).map(WbsAiJob::isCancelRequested).orElse(false);
    }
}
