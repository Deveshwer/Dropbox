package com.example.dropbox.metadata.shares;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        String resourceType,
        UUID resourceId,
        UUID ownerId,
        UUID sharedWithUserId,
        String permission,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
}