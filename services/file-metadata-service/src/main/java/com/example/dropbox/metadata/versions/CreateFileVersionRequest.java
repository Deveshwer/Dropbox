package com.example.dropbox.metadata.versions;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public record CreateFileVersionRequest(
        @NotNull String status,
        @NotBlank String storageKey,
        @NotNull Long sizeBytes,
        @NotBlank String mimeType,
        String checksum
) {
}