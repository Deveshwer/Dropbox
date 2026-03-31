package com.example.dropbox.metadata.files;

import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.dropbox.metadata.shares.PermissionService;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;

    public FileResponse create(CreateFileRequest request, UUID ownerId) {
        Folder folder = folderRepository.findById(request.folderId())
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (!permissionService.canWriteFolder(folder.getId(), ownerId)) {
            throw new ForbiddenOperationException("User not allowed to create a file under this folder");
        }

        FileRecord file = new FileRecord();
        file.setId(UUID.randomUUID());
        file.setName(request.name());
        file.setFolderId(request.folderId());
        file.setOwnerId(ownerId);
        file.setCurrentVersionId(null);
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());

        FileRecord saved = fileRecordRepository.save(file);
        return toResponse(saved);
    }

    public FileResponse getFile(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!permissionService.canReadFile(fileId, userId)) {
            throw new ForbiddenOperationException("User not allowed to access this file");
        }

        return toResponse(file);
    }

    private FileResponse toResponse(FileRecord file) {
        return new FileResponse(
                file.getId(),
                file.getName(),
                file.getFolderId(),
                file.getOwnerId(),
                file.getCurrentVersionId(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
