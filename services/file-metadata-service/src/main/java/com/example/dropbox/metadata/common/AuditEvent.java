package com.example.dropbox.metadata.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}