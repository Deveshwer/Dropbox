package com.example.dropbox.metadata.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "files")
@Getter
@Setter
public class FileRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
