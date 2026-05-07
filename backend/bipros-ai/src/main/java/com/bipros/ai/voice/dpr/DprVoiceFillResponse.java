package com.bipros.ai.voice.dpr;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Result of a voice form-fill turn.
 *
 * @param transcript        what the user just said, as transcribed by Whisper
 * @param patch             a partial form state to merge on the client. May include header fields
 *                          AND row appends for {@code manpower}, {@code equipment}, {@code materials}.
 *                          The shape mirrors {@code DprBaseFields} on the FE.
 * @param photoCaptions     optional caption updates keyed by photo id; only meaningful when the
 *                          request carried a {@code dprId} and there are uncaptioned photos.
 * @param followUpQuestion  if non-null, the assistant has a clarifying question for the user
 * @param complete          true when the assistant believes no more input is needed
 * @param assistantTurn     the assistant's textual turn — append this to the FE-managed history
 */
public record DprVoiceFillResponse(
    String transcript,
    JsonNode patch,
    List<PhotoCaption> photoCaptions,
    String followUpQuestion,
    boolean complete,
    DprVoiceTurn assistantTurn
) {
  public record PhotoCaption(String photoId, String caption) {}
}
