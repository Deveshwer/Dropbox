package com.example.dropbox.metadata.common;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    List<AuditEvent> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType,
            UUID resourceId
    );
}