package com.example.dropbox.metadata.folders;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByParentFolderId(UUID parentFolderId);
    List<Folder> findByOwnerId(UUID ownerId);
    List<Folder> findByOwnerIdAndDeletedAtIsNotNull(UUID ownerId);
    Page<Folder> findByParentFolderIdAndDeletedAtIsNull(UUID parentFolderId, Pageable pageable);

    Page<Folder> findByParentFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID parentFolderId,
            String name,
            Pageable pageable
    );

    List<Folder> findByParentFolderIdAndDeletedAtIsNull(UUID parentFolderId);

    List<Folder> findByParentFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID parentFolderId,
            String name
    );
}
