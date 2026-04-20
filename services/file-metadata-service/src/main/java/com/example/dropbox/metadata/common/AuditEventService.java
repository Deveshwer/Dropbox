package com.example.dropbox.metadata.common;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.dropbox.metadata.shares.PermissionService;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    private final PermissionService permissionService;

    public List<AuditEventResponse> listEventsForActor(UUID actorId) {
      return auditEventRepository.findByActorIdOrderByCreatedAtDesc(actorId)
              .stream()
              .map(this::toResponse)
              .toList();
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