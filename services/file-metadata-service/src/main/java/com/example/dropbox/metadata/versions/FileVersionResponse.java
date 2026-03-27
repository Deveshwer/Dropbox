package com.example.dropbox.metadata.versions;

import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(
        UUID id,
        UUID fileId,
        Long versionNumber,
        String status,
        UUID createdBy,
        Instant createdAt
) {
}