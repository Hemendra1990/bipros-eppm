package com.bipros.ai.document;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Hardens Apache POI's global defaults against malicious workbooks
 * (zip bombs, XML entity expansion, oversized cells).
 *
 * Settings here are JVM-global because POI exposes these as static knobs;
 * scoping per-call would require a custom POI fork. Picking conservative
 * limits up front means every consumer in the app (this AI extractor, the
 * import-export module, anything else) inherits the same protections.
 */
@Configuration
@Slf4j
public class PoiSafetyConfig {

    /** Reject any single zip entry whose compressed-to-uncompressed ratio is below this.
     *  0.01 ⇒ refuse anything that inflates more than 100×. */
    private static final double MIN_INFLATE_RATIO = 0.01d;

    /** Hard cap on a single zip entry uncompressed size. 50 MB. */
    private static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;

    /** Hard cap on extracted text per workbook. 20 MB. */
    private static final long MAX_TEXT_SIZE = 20L * 1024 * 1024;

    /** Cap on a single byte[] allocation inside POI. */
    private static final int MAX_BYTE_ARRAY = 64 * 1024 * 1024;

    @PostConstruct
    public void apply() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_SIZE);
        IOUtils.setByteArrayMaxOverride(MAX_BYTE_ARRAY);
        log.info("POI safety limits applied: minInflateRatio={}, maxEntry={} MB, maxText={} MB, maxByteArray={} MB",
                MIN_INFLATE_RATIO,
                MAX_ENTRY_SIZE / 1024 / 1024,
                MAX_TEXT_SIZE / 1024 / 1024,
                MAX_BYTE_ARRAY / 1024 / 1024);
    }
}
