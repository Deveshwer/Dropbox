package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

public record MetadataEventMessage(
        UUID eventId,
        String eventType,
        String resourceType,
        UUID resourceId,
        UUID actorId,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
