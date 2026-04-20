package com.bipros.document.application.dto;

import org.springframework.core.io.Resource;

/** Bundle returned by the service layer for download endpoints. */
public record DocumentDownload(
        Resource resource,
        String fileName,
        String mimeType,
        long fileSize) {
}
