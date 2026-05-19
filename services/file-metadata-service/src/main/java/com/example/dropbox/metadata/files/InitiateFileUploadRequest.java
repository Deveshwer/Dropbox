package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitiateFileUploadRequest(
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @NotNull Long sizeBytes
) {
}
