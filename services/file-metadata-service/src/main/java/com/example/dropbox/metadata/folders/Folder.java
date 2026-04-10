package com.example.dropbox.metadata.folders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

 /* The flow becomes:

  - controller accepts CreateFolderRequest
  - service creates Folder
  - service maps entity -> FolderResponse
  - controller returns DTO
  
  That is your first real business flow.

  ## Why not pass Folder entity directly into controller

  Because controllers should deal with API contracts, not persistence objects.

  Good boundary:

  - controller <-> DTO
  - service <-> entity/repository
  
   */

@Entity
@Table(name = "folders")
@Getter
@Setter
public class Folder {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
