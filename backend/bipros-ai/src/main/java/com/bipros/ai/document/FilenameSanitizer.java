package com.bipros.ai.document;

import java.util.regex.Pattern;

/**
 * Normalises a user-supplied filename before it ever reaches a log line, an
 * audit record, or — most importantly — an LLM prompt. The original filename
 * comes from the multipart upload header and is fully user-controlled, so a
 * crafted name can include newlines, prompt-injection sigils, or absurd
 * length. We strip those and cap to a sane length, preserving a safe-looking
 * extension hint for downstream readability.
 */
public final class FilenameSanitizer {

    private static final int MAX_LENGTH = 100;
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\\r\\n\\t<>{}\\[\\]\\\\`\"']");
    private static final Pattern PATH_SEP = Pattern.compile("[/\\\\]");

    private FilenameSanitizer() {}

    public static String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return "(unnamed)";
        }
        // Drop any path components a malicious client may have sent.
        String name = PATH_SEP.split(original)[PATH_SEP.split(original).length - 1];
        // Strip control chars, brackets, prompt-injection sigils.
        name = UNSAFE.matcher(name).replaceAll("");
        name = name.strip();
        if (name.isEmpty()) {
            return "(unnamed)";
        }
        if (name.length() > MAX_LENGTH) {
            // Try to keep the extension if there is one, so logs and prompts
            // still show "long-doc.pdf" rather than "long-doc-trun".
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot >= name.length() - 6) {
                String ext = name.substring(dot);
                int keep = MAX_LENGTH - ext.length();
                name = name.substring(0, Math.max(1, keep)) + ext;
            } else {
                name = name.substring(0, MAX_LENGTH);
            }
        }
        return name;
    }
}
