package com.example.dropbox.metadata.versions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    List<FileVersion> findByFileIdOrderByVersionNumberAsc(UUID fileId);
    Optional<FileVersion> findByFileIdAndVersionNumber(UUID fileId, Long versionNumber);
    Optional<FileVersion> findTopByFileIdOrderByVersionNumberDesc(UUID fileId);
}
