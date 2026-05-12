package com.example.dropbox.metadata.files;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface FileRecordRepository extends JpaRepository<FileRecord, UUID> {
    List<FileRecord> findByFolderId(UUID folderId);
    List<FileRecord> findByOwnerId(UUID ownerId);
    List<FileRecord> findByOwnerIdAndDeletedAtIsNotNull(UUID ownerId);

    Page<FileRecord> findByFolderIdAndDeletedAtIsNull(UUID folderId, Pageable pageable);

    Page<FileRecord> findByFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID folderId,
            String name,
            Pageable pageable
    );

    List<FileRecord> findByFolderIdAndDeletedAtIsNull(UUID folderId);

    List<FileRecord> findByFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID folderId,
            String name
    );
}
