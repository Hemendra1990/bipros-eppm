package com.bipros.hds.application.ingestion;

import com.bipros.hds.config.HdsProperties;
import com.bipros.hds.domain.HdsIngestionJob;
import com.bipros.hds.domain.HdsVersion;
import com.bipros.hds.domain.enums.HdsIngestionStage;
import com.bipros.hds.domain.enums.HdsVersionStatus;
import com.bipros.hds.domain.repo.HdsIngestionJobRepository;
import com.bipros.hds.domain.repo.HdsVersionRepository;
import com.bipros.hds.application.library.VersionStatusListener;
import com.bipros.hds.infrastructure.docling.DoclingClient;
import com.bipros.hds.infrastructure.docling.dto.DoclingResponse;
import com.bipros.hds.infrastructure.retrieval.HybridSearchRepository;
import com.bipros.hds.infrastructure.retrieval.HybridSearchRepository.ChunkInsert;
import com.bipros.hds.infrastructure.storage.HdsStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final HdsProperties props;
    private final HdsVersionRepository versionRepo;
    private final HdsIngestionJobRepository jobRepo;
    private final HdsStorageService storage;
    private final DoclingClient docling;
    private final ChunkingService chunking;
    private final EmbeddingService embedding;
    private final HybridSearchRepository hybridRepo;
    private final ProgressStreamRegistry progress;
    private final VersionStatusListener versionStatusListener;

    /** Runs the full pipeline from the job's current stage to COMPLETE or FAILED. Blocking. */
    public void run(HdsIngestionJob job) {
        HdsVersion version = versionRepo.findById(job.getHdsVersionId())
            .orElseThrow(() -> new IllegalStateException("Version not found: " + job.getHdsVersionId()));

        try {
            DoclingResponse parsed = null;
            List<PreChunk> chunks = null;
            List<float[]> embeddings = null;

            // PARSING
            if (resumeFrom(job, HdsIngestionStage.PARSING)) {
                advance(job, version, HdsIngestionStage.PARSING, 0, "Parsing PDF…");
                byte[] pdf = storage.download(version.getStorageKey()).readAllBytes();
                parsed = docling.parse(pdf, version.getFileName());
                if (parsed.getPages() != null && version.getPageCount() == null) {
                    version.setPageCount(parsed.getPages());
                    versionRepo.save(version);
                }
                advance(job, version, HdsIngestionStage.PARSING, 60, "Parsed " + parsed.getPages() + " pages");
            }

            // CHUNKING
            if (resumeFrom(job, HdsIngestionStage.CHUNKING)) {
                if (parsed == null) {
                    // Resumed mid-pipeline — re-parse (Docling is idempotent on the same bytes)
                    byte[] pdf = storage.download(version.getStorageKey()).readAllBytes();
                    parsed = docling.parse(pdf, version.getFileName());
                }
                advance(job, version, HdsIngestionStage.CHUNKING, 65, "Chunking…");
                chunks = chunking.chunk(parsed);
                advance(job, version, HdsIngestionStage.CHUNKING, 70, "Built " + chunks.size() + " chunks");
            }

            // EMBEDDING
            if (resumeFrom(job, HdsIngestionStage.EMBEDDING)) {
                if (chunks == null) {
                    if (parsed == null) {
                        byte[] pdf = storage.download(version.getStorageKey()).readAllBytes();
                        parsed = docling.parse(pdf, version.getFileName());
                    }
                    chunks = chunking.chunk(parsed);
                }
                advance(job, version, HdsIngestionStage.EMBEDDING, 70, "Embedding…");
                List<String> texts = new ArrayList<>(chunks.size());
                for (PreChunk pc : chunks) texts.add(pc.content());
                embeddings = embedding.embedAll(texts, (done, total) -> {
                    int pct = 70 + (int) Math.round(29.0 * done / Math.max(total, 1));
                    advance(job, version, HdsIngestionStage.EMBEDDING, pct, "Embedded " + done + "/" + total);
                });
            }

            // INDEXING
            if (resumeFrom(job, HdsIngestionStage.INDEXING)) {
                if (chunks == null || embeddings == null) {
                    // Resumed mid-INDEXING: re-run embedding (cheap to redo at our scale).
                    if (parsed == null) {
                        byte[] pdf = storage.download(version.getStorageKey()).readAllBytes();
                        parsed = docling.parse(pdf, version.getFileName());
                    }
                    if (chunks == null) chunks = chunking.chunk(parsed);
                    if (embeddings == null) {
                        List<String> texts = new ArrayList<>(chunks.size());
                        for (PreChunk pc : chunks) texts.add(pc.content());
                        embeddings = embedding.embedAll(texts, null);
                    }
                }
                advance(job, version, HdsIngestionStage.INDEXING, 99, "Indexing…");
                List<ChunkInsert> inserts = new ArrayList<>(chunks.size());
                for (PreChunk pc : chunks) {
                    inserts.add(new ChunkInsert(version.getId(), pc.chunkIndex(),
                        pc.pageStart(), pc.pageEnd(), pc.sectionPath(), pc.sectionNumber(),
                        pc.chunkType(), pc.content(), pc.contentTokens()));
                }
                hybridRepo.insertChunks(inserts, embeddings);
                version.setChunkCount(chunks.size());
                version.setStatus(HdsVersionStatus.INDEXED);
                version.setIndexedAt(Instant.now());
                versionRepo.save(version);

                job.setStage(HdsIngestionStage.COMPLETE);
                job.setProgressPct(100);
                job.setCompletedAt(Instant.now());
                jobRepo.save(job);
                progress.publish(new IngestionProgressEvent(version.getId(), "COMPLETE", 100, "Indexed " + chunks.size() + " chunks"));
                versionStatusListener.onIndexedOrFailed(version);
            }
        } catch (Exception e) {
            log.error("Ingestion failed: versionId={}", version.getId(), e);
            job.setStage(HdsIngestionStage.FAILED);
            job.setErrorMessage(e.getMessage() == null ? e.toString() : e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
            version.setStatus(HdsVersionStatus.FAILED);
            version.setIndexingError(job.getErrorMessage());
            versionRepo.save(version);
            progress.publish(new IngestionProgressEvent(version.getId(), "FAILED", job.getProgressPct(), job.getErrorMessage()));
            versionStatusListener.onIndexedOrFailed(version);
            throw new RuntimeException(e);
        }
    }

    private boolean resumeFrom(HdsIngestionJob job, HdsIngestionStage stage) {
        // We need to run a stage if the current job stage is at or before it (and not COMPLETE/FAILED).
        if (job.getStage() == HdsIngestionStage.COMPLETE || job.getStage() == HdsIngestionStage.FAILED) return false;
        return job.getStage().ordinal() <= stage.ordinal();
    }

    @Transactional
    public void advance(HdsIngestionJob job, HdsVersion version, HdsIngestionStage stage, int pct, String msg) {
        job.setStage(stage);
        job.setProgressPct(pct);
        job.setLastHeartbeatAt(Instant.now());
        jobRepo.save(job);

        version.setStatus(switch (stage) {
            case PARSING -> HdsVersionStatus.PARSING;
            case CHUNKING -> HdsVersionStatus.CHUNKING;
            case EMBEDDING, INDEXING -> HdsVersionStatus.EMBEDDING;
            case COMPLETE -> HdsVersionStatus.INDEXED;
            case FAILED -> HdsVersionStatus.FAILED;
        });
        version.setIndexingProgressPct(pct);
        versionRepo.save(version);

        progress.publish(new IngestionProgressEvent(version.getId(), stage.name(), pct, msg));
    }
}
