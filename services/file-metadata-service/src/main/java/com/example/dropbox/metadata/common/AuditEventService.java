package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.dropbox.metadata.shares.PermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    private final PermissionService permissionService;

    private final OutboxEventRepository outboxEventRepository;

    private final SyncEventRepository syncEventRepository;

    private final ObjectMapper objectMapper;

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

    @Transactional
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

        MetadataEventMessage eventMessage = new MetadataEventMessage(
          event.getId(),
          event.getEventType(),
          event.getResourceType(),
          event.getResourceId(),
          event.getActorId(),
          parseMetadata(event.getMetadata()),
          event.getCreatedAt()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventMessage);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox event payload", ex);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setEventType(event.getEventType());
        outboxEvent.setResourceType(event.getResourceType());
        outboxEvent.setResourceId(event.getResourceId());
        outboxEvent.setActorId(event.getActorId());
        outboxEvent.setPayload(payload);
        outboxEvent.setCreatedAt(event.getCreatedAt());
        outboxEvent.setPublishedAt(null);

        outboxEventRepository.save(outboxEvent);

        if (isSyncEligibleResource(event.getResourceType())) {
            SyncEvent syncEvent = new SyncEvent();
            syncEvent.setEventId(event.getId());
            syncEvent.setEventType(event.getEventType());
            syncEvent.setResourceType(event.getResourceType());
            syncEvent.setResourceId(event.getResourceId());
            syncEvent.setActorId(event.getActorId());
            syncEvent.setMetadata(event.getMetadata());
            syncEvent.setCreatedAt(event.getCreatedAt());

            syncEventRepository.save(syncEvent);
        }


        // if (metadataEventPublisher != null) {
        //     metadataEventPublisher.publish(new MetadataEventMessage(
        //         event.getId(),
        //         event.getEventType(),
        //         event.getResourceType(),
        //         event.getResourceId(),
        //         event.getActorId(),
        //         parseMetadata(event.getMetadata()),
        //         event.getCreatedAt()
        //     ));
        // }
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

    private boolean isSyncEligibleResource(String resourceType) {
        return ResourceType.FILE.name().equals(resourceType)
                || ResourceType.FOLDER.name().equals(resourceType);
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
