package com.example.dropbox.metadata.files;

import com.example.dropbox.metadata.common.ResourceType;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.shares.ShareRepository;
import com.example.dropbox.metadata.versions.FileVersionRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.dropbox.metadata.shares.PermissionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import com.example.dropbox.metadata.common.AuditEventService;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final FileVersionRepository fileVersionRepository;
    private final ShareRepository shareRepository;
    private final AuditEventService auditEventService;

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
        auditEventService.recordEvent(
            "FILE_CREATED",
            "FILE",
            saved.getId(),
            ownerId,
            "name=" + saved.getName()
        );
        return toResponse(saved);
    }

    public FileResponse getFile(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (!permissionService.canReadFile(fileId, userId)) {
            throw new ForbiddenOperationException("User not allowed to access this file");
        }

        return toResponse(file);
    }

    public FileResponse renameFile(UUID fileId, RenameFileRequest request, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (!file.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("User not allowed to rename this file");
        }

        file.setName(request.name());
        file.setUpdatedAt(Instant.now());

        FileRecord saved = fileRecordRepository.save(file);
        return toResponse(saved);
    }

    public List<FileResponse> listDeletedFiles(UUID userId) {
      return fileRecordRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)
              .stream()
              .map(this::toResponse)
              .toList();
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    public FileResponse moveFile(UUID fileId, MoveFileRequest request, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        Folder targetFolder = folderRepository.findById(request.targetFolderId())
                .orElseThrow(() -> new ResourceNotFoundException("Target folder not found"));

        if (!file.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("User not allowed to move this file");
        }

        if (!permissionService.canWriteFolder(targetFolder.getId(), userId)) {
            throw new ForbiddenOperationException("User not allowed to move file into target folder");
        }

        file.setFolderId(targetFolder.getId());
        file.setUpdatedAt(Instant.now());

        FileRecord saved = fileRecordRepository.save(file);
        return toResponse(saved);
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void deleteFile(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (!file.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("User not allowed to delete this file");
        }

        boolean hasVersions = !fileVersionRepository.findByFileIdOrderByVersionNumberAsc(fileId).isEmpty();
        if (hasVersions) {
            throw new IllegalArgumentException("File cannot be deleted because versions exist");
        }

        // shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FILE.name(), fileId);
        // fileRecordRepository.delete(file);

        file.setDeletedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        fileRecordRepository.save(file);
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    public FileResponse restoreFile(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!file.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("User not allowed to restore this file");
        }

        if (file.getDeletedAt() == null) {
            throw new IllegalArgumentException("File is not deleted");
        }

        file.setDeletedAt(null);
        file.setUpdatedAt(Instant.now());

        FileRecord saved = fileRecordRepository.save(file);
        return toResponse(saved);
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void permanentlyDeleteFile(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!file.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("User not allowed to permanently delete this file");
        }

        if (file.getDeletedAt() == null) {
            throw new IllegalArgumentException("File is not deleted");
        }

        shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FILE.name(), fileId);
        fileRecordRepository.delete(file);
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void emptyTrash(UUID userId) {
        List<FileRecord> deletedFiles = fileRecordRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);
        for (FileRecord file : deletedFiles) {
            shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FILE.name(), file.getId());
            fileRecordRepository.delete(file);
        }
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
