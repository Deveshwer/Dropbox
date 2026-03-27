package com.example.dropbox.metadata.files;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRecordRepository extends JpaRepository<FileRecord, UUID> {
    List<FileRecord> findByFolderId(UUID folderId);
    List<FileRecord> findByOwnerId(UUID ownerId);
}
