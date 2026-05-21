package com.bipros.hds.infrastructure.docling;

import com.bipros.hds.config.HdsProperties;
import com.bipros.hds.infrastructure.docling.dto.DoclingBlock;
import com.bipros.hds.infrastructure.docling.dto.DoclingResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls docling-serve's /v1/convert/file endpoint and converts the markdown content
 * it returns into the synthetic DoclingBlock list the chunker consumes.
 *
 * <p>docling-serve's response shape is {@code {document: {md_content, json_content, ...},
 * status, processing_time, ...}}. The richer structure is in {@code json_content}, but
 * for v1 we parse {@code md_content} (markdown) into heading + paragraph blocks.
 * Section numbers and page numbers come from the markdown headings when present.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DoclingClient {

    private final HdsProperties props;
    private WebClient webClient;

    private WebClient client() {
        if (webClient == null) {
            webClient = WebClient.builder()
                .baseUrl(props.getDocling().getUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
        }
        return webClient;
    }

    public DoclingResponse parse(byte[] pdfBytes, String fileName) {
        log.info("Submitting PDF to Docling: name={}, size={} bytes", fileName, pdfBytes.length);
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("files", new ByteArrayResource(pdfBytes) {
            @Override public String getFilename() { return fileName; }
        }).contentType(MediaType.APPLICATION_PDF);
        mb.part("to_formats", "md");
        mb.part("do_ocr", "false");
        mb.part("do_table_structure", "true");

        Duration timeout = Duration.ofMinutes(props.getDocling().getTimeoutMinutes());
        JsonNode raw = client().post()
            .uri("/v1/convert/file")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(mb.build()))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block(timeout);

        if (raw == null) {
            throw new IllegalStateException("Docling returned null response");
        }
        String md = raw.path("document").path("md_content").asText("");
        if (md.isBlank()) {
            log.warn("Docling returned empty md_content; status={}", raw.path("status").asText());
        }

        DoclingResponse resp = new DoclingResponse();
        resp.setStatus(raw.path("status").asText("ok"));
        resp.setBlocks(markdownToBlocks(md));
        // We don't have page-level metadata from md_content; the chunker handles -1 → 1 fallback.
        resp.setPages(1);
        log.info("Docling parse complete: status={}, blocks={}", resp.getStatus(), resp.getBlocks().size());
        return resp;
    }

    /**
     * Converts a markdown document into a flat list of DoclingBlock entries.
     *  - Lines starting with `#` become heading blocks (level = number of `#`).
     *  - Other non-blank line groups become paragraph blocks.
     *  - Markdown tables (`| ... |`) become a single table block (a contiguous run).
     */
    private List<DoclingBlock> markdownToBlocks(String md) {
        List<DoclingBlock> out = new ArrayList<>();
        if (md == null || md.isBlank()) return out;
        String[] lines = md.split("\n", -1);

        StringBuilder paragraph = new StringBuilder();
        StringBuilder table = new StringBuilder();
        boolean inTable = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flushParagraph(paragraph, out);
                if (!inTable) inTable = true;
                table.append(line).append("\n");
                continue;
            } else if (inTable) {
                DoclingBlock tb = new DoclingBlock();
                tb.setType("table");
                tb.setMarkdown(table.toString().trim());
                tb.setPage(1);
                out.add(tb);
                table.setLength(0);
                inTable = false;
            }

            if (trimmed.startsWith("#")) {
                flushParagraph(paragraph, out);
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                String headingText = trimmed.substring(level).trim();
                DoclingBlock hb = new DoclingBlock();
                hb.setType("heading");
                hb.setLevel(level);
                hb.setText(headingText);
                hb.setPage(1);
                // Best-effort section number — leading "1.2.3 ..." pattern
                String sectionNum = leadingSectionNumber(headingText);
                hb.setSectionNumber(sectionNum);
                out.add(hb);
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, out);
            } else {
                if (paragraph.length() > 0) paragraph.append("\n");
                paragraph.append(line);
            }
        }
        if (inTable) {
            DoclingBlock tb = new DoclingBlock();
            tb.setType("table");
            tb.setMarkdown(table.toString().trim());
            tb.setPage(1);
            out.add(tb);
        }
        flushParagraph(paragraph, out);
        return out;
    }

    private void flushParagraph(StringBuilder paragraph, List<DoclingBlock> out) {
        if (paragraph.length() == 0) return;
        DoclingBlock pb = new DoclingBlock();
        pb.setType("paragraph");
        pb.setText(paragraph.toString().trim());
        pb.setPage(1);
        out.add(pb);
        paragraph.setLength(0);
    }

    private String leadingSectionNumber(String heading) {
        // Match "1", "1.2", "1.2.3", optionally followed by a space.
        int i = 0;
        while (i < heading.length() && (Character.isDigit(heading.charAt(i)) || heading.charAt(i) == '.')) {
            i++;
        }
        if (i == 0) return null;
        String prefix = heading.substring(0, i).replaceAll("\\.$", "");
        return prefix.isEmpty() ? null : prefix;
    }
}
