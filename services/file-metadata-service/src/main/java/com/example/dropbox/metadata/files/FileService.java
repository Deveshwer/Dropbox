package com.example.dropbox.metadata.files;

import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;

    public FileResponse create(CreateFileRequest request, UUID ownerId) {
        Folder folder = folderRepository.findById(request.folderId())
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (!folder.getOwnerId().equals(ownerId)) {
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
