package com.bipros.api.dprreport;

import com.bipros.ai.voice.SpeechToTextService;
import com.bipros.project.domain.model.DprVoiceNote;
import com.bipros.project.domain.repository.DprVoiceNoteRepository;
import com.bipros.project.infrastructure.storage.VoiceNoteStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Transcribe-once helper for DPR voice notes consumed by the DPR report agent.
 *
 * <p>Runs in its OWN read-write transaction ({@code REQUIRES_NEW}) rather than plain
 * {@code @Transactional}: the caller, {@link DprReportSnapshotCollector#collect}, is
 * {@code @Transactional(readOnly = true)}. A read-only transaction can't persist the transcript,
 * and even if it could, nesting would tie the transcript writes to the collector's (read-only)
 * transaction lifecycle. {@code REQUIRES_NEW} gives this method its own transaction so persisting
 * transcripts works and the transcribe-once caching commits independently of the collector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DprVoiceNoteTranscriber {
    private static final String HINT =
        "Daily Progress Report voice note for a road construction project. Terms: chainage, manpower, mason, "
        + "helper, excavator, roller, grader, dozer, tipper, cement, aggregate, cubic meter, BOQ, day shift, night shift.";

    private final DprVoiceNoteRepository voiceRepo;
    private final VoiceNoteStorage storage;
    private final SpeechToTextService stt;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> transcribeForDprs(Collection<UUID> dprIds) {
        if (dprIds == null || dprIds.isEmpty()) return List.of();
        // 1. transcribe any missing
        for (DprVoiceNote note : voiceRepo.findByDprIdInAndTranscriptIsNull(dprIds)) {
            try (InputStream in = storage.openFull(note.getStorageKey())) {
                byte[] bytes = in.readAllBytes();
                String text = stt.transcribe(bytes, note.getFileName(), note.getMimeType(), HINT);
                note.setTranscript(text);
                note.setTranscribedAt(Instant.now());
                voiceRepo.save(note);
            } catch (Exception e) {
                log.warn("[DprVoiceNoteTranscriber] failed note={}: {}", note.getId(), e.getMessage());
            }
        }
        // 2. return all transcripts for these DPRs
        List<String> out = new ArrayList<>();
        for (DprVoiceNote n : voiceRepo.findByDprIdIn(dprIds)) {
            if (n.getTranscript() != null && !n.getTranscript().isBlank()) {
                out.add(n.getTranscript().trim());
            }
        }
        return out;
    }
}
