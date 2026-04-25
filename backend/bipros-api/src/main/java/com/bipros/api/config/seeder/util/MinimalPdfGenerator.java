package com.bipros.api.config.seeder.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled single-page A4 PDF emitter for seeder stub documents. Avoids a runtime
 * dependency on PDFBox/iText. Produces ~700-1200 byte valid PDF/1.4 files containing one
 * Helvetica text block — enough for the document register and document-viewer to render
 * without crashing.
 *
 * <p>Why hand-rolled: the road-project demo seeder needs ~25 stub PDFs at boot. Pulling in
 * PDFBox just to render placeholder text would add ~5 MB and a stack of native dependencies
 * for what is fundamentally fixed-shape content. The PDF spec's structural minimum is
 * 8 indirect objects (Catalog → Pages → Page → Resources/Font → Contents → xref → trailer),
 * which is small enough to emit with string concatenation.
 */
public final class MinimalPdfGenerator {

    private MinimalPdfGenerator() {}

    /**
     * Renders a stub PDF with the supplied metadata. All parameters are optional;
     * nulls are skipped. The watermark line at the top makes it visually obvious this is
     * not real engineering content.
     */
    public static byte[] render(
            String docNumber,
            String title,
            String category,
            String specReference,
            String projectName,
            String approvedBy,
            String remarks) {

        List<String> lines = new ArrayList<>();
        lines.add("[Sample seed data - non-engineering content]");
        lines.add("");
        if (docNumber != null) lines.add("Document No: " + sanitize(docNumber));
        if (title != null) lines.add("Title: " + sanitize(title));
        if (category != null) lines.add("Category: " + sanitize(category));
        if (specReference != null) lines.add("Reference: " + sanitize(specReference));
        if (projectName != null) lines.add("Project: " + sanitize(projectName));
        if (approvedBy != null) lines.add("Approved by: " + sanitize(approvedBy));
        if (remarks != null) lines.add("Remarks: " + sanitize(remarks));

        return assemble(lines);
    }

    private static byte[] assemble(List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 12 Tf\n");
        // A4 page size: 595 x 842 pts. Start near top-left margin.
        int y = 800;
        for (String line : lines) {
            content.append("1 0 0 1 50 ").append(y).append(" Tm\n");
            content.append("(").append(escape(line)).append(") Tj\n");
            y -= 18;
        }
        content.append("ET\n");
        byte[] contentStream = content.toString().getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        write(out, "%PDF-1.4\n%âãÏÓ\n");

        offsets.add(out.size());
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets.add(out.size());
        write(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets.add(out.size());
        write(out,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                        + " /Resources << /Font << /F1 5 0 R >> >>"
                        + " /Contents 4 0 R >>\nendobj\n");

        offsets.add(out.size());
        String streamHeader = "4 0 obj\n<< /Length " + contentStream.length + " >>\nstream\n";
        write(out, streamHeader);
        try {
            out.write(contentStream);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream write failed", e);
        }
        write(out, "\nendstream\nendobj\n");

        offsets.add(out.size());
        write(out,
                "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica"
                        + " /Encoding /WinAnsiEncoding >>\nendobj\n");

        int xrefStart = out.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(offsets.size() + 1).append('\n');
        xref.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            xref.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, xref.toString());

        write(out,
                "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\n"
                        + "startxref\n" + xrefStart + "\n%%EOF");

        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        try {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream write failed", e);
        }
    }

    /** Strip non-ASCII to keep the embedded text 7-bit clean (the font is WinAnsi). */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) sb.append(c);
            else sb.append(' ');
        }
        return sb.toString();
    }

    /** Escape PDF string literal special characters. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
