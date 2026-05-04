package com.bipros.ai.document;

import com.bipros.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentTextExtractorRouter {

    private final List<DocumentTextExtractor> extractors;

    public DocumentTextExtractor.ExtractedText extract(Path file, String mimeType, String originalFileName) {
        for (DocumentTextExtractor ex : extractors) {
            if (ex.supports(mimeType, originalFileName)) {
                return ex.extract(file, mimeType, originalFileName);
            }
        }
        throw new BusinessRuleException("UNSUPPORTED_DOCUMENT_TYPE",
                "Unsupported document type. Supported: PDF, Excel (.xlsx, .xls).");
    }
}
