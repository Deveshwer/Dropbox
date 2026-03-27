package com.example.dropbox.metadata.folders;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentFolderId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
}
