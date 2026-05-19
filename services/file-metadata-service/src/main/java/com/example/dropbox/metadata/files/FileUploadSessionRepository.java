package com.example.dropbox.metadata.files;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadSessionRepository extends JpaRepository<FileUploadSession, UUID> {

    Optional<FileUploadSession> findTopByFileIdAndInitiatedByAndStatusOrderByCreatedAtDesc(
            UUID fileId,
            UUID initiatedBy,
            String status
    );

    Optional<FileUploadSession> findByFileIdAndInitiatedByAndStorageKeyAndStatus(
            UUID fileId,
            UUID initiatedBy,
            String storageKey,
            String status
    );
}