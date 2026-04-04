package com.bipros.document.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentVersionRequest(
    @NotBlank(message = "File name is required")
    String fileName,

    @NotBlank(message = "File path is required")
    String filePath,

    @NotNull(message = "File size is required")
    Long fileSize,

    String changeDescription
) {
}
