package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public void recordEvent(
            String eventType,
            String resourceType,
            UUID resourceId,
            UUID actorId,
            String metadata
    ) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setActorId(actorId);
        event.setMetadata(metadata);
        event.setCreatedAt(Instant.now());

        auditEventRepository.save(event);
    }
}