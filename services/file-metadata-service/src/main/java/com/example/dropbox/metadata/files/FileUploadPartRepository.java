package com.example.dropbox.metadata.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadPartRepository extends JpaRepository<FileUploadPart, UUID> {

    List<FileUploadPart> findBySessionIdOrderByPartNumberAsc(UUID sessionId);

    Optional<FileUploadPart> findBySessionIdAndPartNumber(UUID sessionId, Integer partNumber);

    boolean existsBySessionIdAndStatus(UUID sessionId, String status);
}
