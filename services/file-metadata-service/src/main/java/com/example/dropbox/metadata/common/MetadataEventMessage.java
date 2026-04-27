package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;

public record MetadataEventMessage(
        UUID eventId,
        String eventType,
        String resourceType,
        UUID resourceId,
        UUID actorId,
        String metadata,
        Instant createdAt
) {
}
