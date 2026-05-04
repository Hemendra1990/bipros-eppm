package com.bipros.ai.document;

import com.bipros.common.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Sniffs a file's actual content type from its bytes (Tika magic) rather
 * than trusting browser-supplied {@code Content-Type} or the filename
 * extension — both of which are user-controlled and trivially spoofable.
 *
 * Returns the detected MIME if it sits in our allowlist; throws a clean
 * {@link BusinessRuleException} otherwise so a malicious upload never
 * reaches PDFBox / POI.
 */
@Service
@Slf4j
public class MimeTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String XLS = "application/vnd.ms-excel";

    private static final Set<String> ALLOWED = Set.of(PDF, XLSX, XLS);

    private Detector detector;

    @PostConstruct
    void init() {
        this.detector = TikaConfig.getDefaultConfig().getDetector();
    }

    /**
     * @return the detected MIME, guaranteed to be in {@link #ALLOWED}.
     * @throws BusinessRuleException if detection fails or the detected type
     *         isn't a supported document type.
     */
    public String detectAndValidate(Path file, String originalFileName) {
        Metadata metadata = new Metadata();
        if (originalFileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFileName);
        }

        String detected;
        try (TikaInputStream in = TikaInputStream.get(file)) {
            detected = detector.detect(in, metadata).toString();
        } catch (IOException e) {
            log.warn("Tika detection failed for {}: {}", originalFileName, e.getMessage());
            throw new BusinessRuleException("DOCUMENT_PARSE_FAILED",
                    "Could not read uploaded file.");
        }

        if (!ALLOWED.contains(detected)) {
            log.info("Rejected upload {} — detected MIME {} not in allowlist", originalFileName, detected);
            throw new BusinessRuleException("UNSUPPORTED_DOCUMENT_TYPE",
                    "Unsupported document type (" + detected + "). Allowed: PDF, Excel (.xlsx, .xls).");
        }
        return detected;
    }
}
