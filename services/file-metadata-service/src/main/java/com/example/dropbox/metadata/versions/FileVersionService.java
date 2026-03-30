package com.example.dropbox.metadata.versions;

import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.dropbox.metadata.shares.PermissionService;

@Service
@RequiredArgsConstructor
public class FileVersionService {
    private final FileVersionRepository fileVersionRepository;
    private final FileRecordRepository fileRecordRepository;
    private final PermissionService permissionService;

    @Transactional
    public FileVersionResponse create(UUID fileId, CreateFileVersionRequest request, UUID createdBy) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!permissionService.canWriteFile(fileId, createdBy)) {
                throw new ForbiddenOperationException("User not allowed to create a version for this file.");
        }

        long nextVersionNumber = fileVersionRepository
                .findTopByFileIdOrderByVersionNumberDesc(fileId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1L);

        FileVersion version = new FileVersion();
        version.setId(UUID.randomUUID());
        version.setFileId(fileId);
        version.setVersionNumber(nextVersionNumber);
        version.setStatus(request.status());
        version.setCreatedBy(createdBy);
        version.setCreatedAt(Instant.now());

        FileVersion savedVersion = fileVersionRepository.save(version);

        file.setCurrentVersionId(savedVersion.getId());
        file.setUpdatedAt(Instant.now());
        fileRecordRepository.save(file);

        return toResponse(savedVersion);
    }

    public List<FileVersionResponse> getVersions(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!permissionService.canReadFile(fileId, userId)) {
                throw new ForbiddenOperationException("User not allowed to access versions of this file.");
        }

        return fileVersionRepository.findByFileIdOrderByVersionNumberAsc(fileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private FileVersionResponse toResponse(FileVersion version) {
        return new FileVersionResponse(
                version.getId(),
                version.getFileId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }
}
