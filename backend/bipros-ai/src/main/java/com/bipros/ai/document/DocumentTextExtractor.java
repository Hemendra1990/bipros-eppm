package com.bipros.ai.document;

import java.nio.file.Path;

public interface DocumentTextExtractor {

    boolean supports(String mimeType, String originalFileName);

    /**
     * Extract plaintext from a file already spilled to disk.
     *
     * @param file        path to the file on disk (caller manages lifecycle)
     * @param mimeType    detected MIME type (Tika-sniffed, not browser-supplied)
     * @param originalFileName user-supplied filename (already sanitized)
     */
    ExtractedText extract(Path file, String mimeType, String originalFileName);

    /** Hard cap on extracted text. ~50k tokens for typical English text. */
    int MAX_CHARS = 200_000;

    record ExtractedText(String text, boolean truncated) {
        public static ExtractedText cap(String raw) {
            if (raw.length() <= MAX_CHARS) {
                return new ExtractedText(raw, false);
            }
            String capped = raw.substring(0, MAX_CHARS)
                    + "\n\n[NOTE: Document was truncated to " + MAX_CHARS
                    + " characters; tail content omitted.]";
            return new ExtractedText(capped, true);
        }
    }
}
