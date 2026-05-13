package com.example.dropbox.metadata.versions;

import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(
        UUID id,
        UUID fileId,
        Long versionNumber,
        String status,
        String storageKey,
        Long sizeBytes,
        String mimeType,
        String checksum,
        UUID createdBy,
        Instant createdAt
) {
}