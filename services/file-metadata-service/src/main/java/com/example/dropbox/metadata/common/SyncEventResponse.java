package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncEventResponse(
        Long cursor,
        UUID eventId,
        String eventType,
        String resourceType,
        UUID resourceId,
        UUID actorId,
        Map<String, Object> metadata,
        Instant createdAt
) {
}