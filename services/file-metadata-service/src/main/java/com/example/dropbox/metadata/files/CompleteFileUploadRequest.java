package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompleteFileUploadRequest(
        @NotBlank String status,
        @NotBlank String storageKey,
        @NotNull Long sizeBytes,
        @NotBlank String mimeType,
        String checksum
) {
}