package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.dropbox.metadata.shares.PermissionService;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    private final PermissionService permissionService;

    private final MetadataEventPublisher metadataEventPublisher;

    public List<AuditEventResponse> listEventsForActor(UUID actorId) {
      return auditEventRepository.findByActorIdOrderByCreatedAtDesc(actorId)
              .stream()
              .map(this::toResponse)
              .toList();
    }

    private Map<String, Object> parseMetadata(String metadata) {
        Map<String, Object> parsed = new LinkedHashMap<>();

        if (metadata == null || metadata.isBlank()) {
            return parsed;
        }

        String[] entries = metadata.split(",");

        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                parsed.put(parts[0].trim(), parts[1].trim());
            }
        }

        return parsed;
    }

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

        if (metadataEventPublisher != null) {
            metadataEventPublisher.publish(new MetadataEventMessage(
                event.getId(),
                event.getEventType(),
                event.getResourceType(),
                event.getResourceId(),
                event.getActorId(),
                parseMetadata(event.getMetadata()),
                event.getCreatedAt()
            ));
        }
    }

    public List<AuditEventResponse> listEventsForResource(String resourceType, UUID resourceId, UUID userId) {
        if(ResourceType.FILE.name().equals(resourceType)) {
            if(!permissionService.canReadFile(resourceId, userId)) {
                throw new ForbiddenOperationException("You are not allowed to view audit events for this file");
            }
        }
        else if(ResourceType.FOLDER.name().equals(resourceType)) {
            if(!permissionService.canReadFolder(resourceId, userId)) {
                throw new ForbiddenOperationException("You are not allowed to view audit events for this folder");
            }
        }
        else {
            throw new IllegalArgumentException("Invalid resource type");
        }
        
        return auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                      resourceType,
                      resourceId
              )
              .stream()
              .map(this::toResponse)
              .toList();
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
              event.getId(),
              event.getEventType(),
              event.getResourceType(),
              event.getResourceId(),
              event.getActorId(),
              event.getMetadata(),
              event.getCreatedAt()
        );
    }
}
