package com.example.dropbox.metadata.shares;

import java.time.Instant;
import java.util.UUID;

public record SharedWithMeItemResponse(
        UUID shareId,
        String resourceType,
        UUID resourceId,
        String resourceName,
        UUID ownerId,
        String permission,
        Instant expiresAt,
        Instant sharedAt,
        UUID parentFolderId,
        boolean resourceDeleted
) {
}
