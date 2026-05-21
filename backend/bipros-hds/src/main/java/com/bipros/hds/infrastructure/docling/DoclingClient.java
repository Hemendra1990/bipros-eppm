package com.bipros.hds.infrastructure.docling;

import com.bipros.hds.config.HdsProperties;
import com.bipros.hds.infrastructure.docling.dto.DoclingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

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
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))  // 64 MB JSON ceiling
                .build();
        }
        return webClient;
    }

    /**
     * Synchronous: blocks until Docling returns the parsed structure.
     * For 1GB PDFs this may take 15-30 minutes - the caller (IngestionWorker) is on a long-lived thread.
     */
    public DoclingResponse parse(byte[] pdfBytes, String fileName) {
        log.info("Submitting PDF to Docling: name={}, size={} bytes", fileName, pdfBytes.length);
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", new ByteArrayResource(pdfBytes) {
            @Override public String getFilename() { return fileName; }
        }).contentType(MediaType.APPLICATION_PDF);

        Duration timeout = Duration.ofMinutes(props.getDocling().getTimeoutMinutes());
        DoclingResponse resp = client().post()
            .uri("/v1/convert")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(mb.build()))
            .retrieve()
            .bodyToMono(DoclingResponse.class)
            .block(timeout);

        if (resp == null) {
            throw new IllegalStateException("Docling returned null response");
        }
        log.info("Docling parse complete: pages={}, blocks={}",
            resp.getPages(), resp.getBlocks() == null ? 0 : resp.getBlocks().size());
        return resp;
    }
}
