package com.example.dropbox.metadata.files;

import java.time.Instant;
import java.util.UUID;

public record FileSummary(
        UUID id,
        String name,
        UUID folderId,
        UUID ownerId,
        UUID currentVersionId,
        Instant createdAt,
        Instant updatedAt
) {
}