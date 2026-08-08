package com.bipros.ai.voice.dpr;

import com.bipros.common.dto.ApiResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Voice-driven DPR form-fill. The frontend records audio in the Add/Edit drawer, then POSTs the
 * blob along with the form's current state and the running session history. We transcribe, call
 * an LLM with a structured-output schema, and return either a patch of fields to merge or a
 * follow-up question.
 *
 * <p>Sessions are stateless on the server — the FE accumulates history and replays it on each
 * call. This keeps the endpoint horizontally scalable and avoids leaking transcripts into
 * persistence (transcripts are ephemeral by default; see PR2 plan, "Open items").
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/dpr/voice-fill")
@RequiredArgsConstructor
@Slf4j
public class DprVoiceFillController {

  private final DprVoiceFillService service;
  private final ObjectMapper objectMapper;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'DPR.UPDATE')")
  public ResponseEntity<ApiResponse<DprVoiceFillResponse>> fill(
      @PathVariable UUID projectId,
      @RequestPart(value = "audio", required = false) MultipartFile audio,
      @RequestPart(value = "text", required = false) String text,
      @RequestPart("state") String stateJson,
      @RequestPart(value = "history", required = false) String historyJson,
      @RequestPart(value = "dprId", required = false) String dprId) throws IOException {

    JsonNode state = objectMapper.readTree(stateJson);
    List<DprVoiceTurn> history = historyJson == null || historyJson.isBlank()
        ? List.of()
        : objectMapper.readValue(historyJson, new TypeReference<>() {});

    // Exactly one input: a recording OR typed chat text (client workbook, Web sheet row 9).
    boolean hasAudio = audio != null && !audio.isEmpty();
    boolean hasText = text != null && !text.isBlank();
    if (hasAudio && hasText) {
      throw new BusinessRuleException("VOICE_INPUT_AMBIGUOUS",
          "Send either an audio recording or typed text, not both");
    }
    if (!hasAudio && !hasText) {
      throw new BusinessRuleException("VOICE_AUDIO_EMPTY",
          "Provide an audio recording or typed text");
    }

    log.info("POST /v1/projects/{}/dpr/voice-fill - {}={}, history={} turn(s), dprId={}",
        projectId, hasAudio ? "audio" : "text",
        hasAudio ? audio.getSize() + " bytes" : text.length() + " chars", history.size(), dprId);

    DprVoiceFillRequest request = new DprVoiceFillRequest(state, history, dprId);
    DprVoiceFillResponse response = hasAudio
        ? service.fill(projectId, audio, request)
        : service.fillFromText(projectId, text.trim(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
