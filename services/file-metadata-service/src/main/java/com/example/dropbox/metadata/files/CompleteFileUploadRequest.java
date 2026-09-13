package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompleteFileUploadRequest(
        @NotBlank String status,
        @NotNull UUID uploadSessionId,
        @NotNull Long sizeBytes,
        @NotBlank String mimeType,
        String checksum
) {
}
