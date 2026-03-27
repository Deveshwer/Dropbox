package com.example.dropbox.metadata.folders;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByParentFolderId(UUID parentFolderId);
    List<Folder> findByOwnerId(UUID ownerId);
}
