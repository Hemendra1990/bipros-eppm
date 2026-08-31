package com.bipros.ai.voice.dpr;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Body of a {@link DprVoiceFillController} call. The controller composes this from a multipart
 * request: {@code audio} part (binary), {@code state} part (JSON of the current form state),
 * {@code history} part (JSON array of prior turns), and an optional {@code dprId} part once the
 * DPR row has been saved (so the assistant can also caption already-uploaded photos).
 *
 * @param state   current form state, schema-loose so the FE can extend without backend change
 * @param history prior turns of this voice session, frontend-managed
 * @param dprId   present once the DPR has been saved; null on first turn for a new DPR
 */
public record DprVoiceFillRequest(
    JsonNode state,
    List<DprVoiceTurn> history,
    String dprId
) {
}
