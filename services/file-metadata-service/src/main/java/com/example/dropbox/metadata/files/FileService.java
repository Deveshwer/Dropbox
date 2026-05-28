package com.example.dropbox.metadata.files;

import com.example.dropbox.metadata.common.ResourceType;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.shares.ShareRepository;
import com.example.dropbox.metadata.versions.CreateFileVersionRequest;
import com.example.dropbox.metadata.versions.FileVersion;
import com.example.dropbox.metadata.versions.FileVersionRepository;
import com.example.dropbox.metadata.versions.FileVersionResponse;
import com.example.dropbox.metadata.versions.FileVersionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.dropbox.metadata.shares.PermissionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import com.example.dropbox.metadata.common.AuditEventService;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final FileVersionRepository fileVersionRepository;
    private final ShareRepository shareRepository;
    private final AuditEventService auditEventService;
    private final FileUploadSessionRepository fileUploadSessionRepository;
    private final FileVersionService fileVersionService;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties s3StorageProperties;
    private final S3Client s3Client;


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

        auditEventService.recordEvent(
            "FILE_SOFT_DELETED",
            ResourceType.FILE.name(),
            file.getId(),
            userId,
            "name=" + file.getName()
        );
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

        auditEventService.recordEvent(
            "FILE_RESTORED",
            ResourceType.FILE.name(),
            saved.getId(),
            userId,
            "name=" + saved.getName()
        );
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

        auditEventService.recordEvent(
            "FILE_PERMANENTLY_DELETED",
            ResourceType.FILE.name(),
            file.getId(),
            userId,
            "name=" + file.getName()
        );
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
            auditEventService.recordEvent(
                "FILE_PERMANENTLY_DELETED",
                ResourceType.FILE.name(),
                file.getId(),
                userId,
                "name=" + file.getName() + ",source=emptyTrash"
        );
        }
    }

    private HeadObjectResponse verifyUploadedObject(String storageKey, Long expectedSizeBytes, String expectedMimeType) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(s3StorageProperties.bucket())
                    .key(storageKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);

            if (response.contentLength() == null || response.contentLength() != expectedSizeBytes.longValue()) {
                throw new IllegalArgumentException("Uploaded object size does not match expected size");
            }

            String actualContentType = response.contentType();
            if (actualContentType != null && !actualContentType.equals(expectedMimeType)) {
                throw new IllegalArgumentException("Uploaded object content type does not match expected mimeType");
            }

            return response;
        } catch (NoSuchKeyException ex) {
            throw new IllegalArgumentException("Uploaded object not found in storage");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IllegalArgumentException("Uploaded object not found in storage");
            }
            throw ex;
        }
    }

    public FileDownloadResponse getDownloadInfo(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (!permissionService.canReadFile(fileId, userId)) {
            throw new ForbiddenOperationException("User not allowed to access this file");
        }

        if (file.getCurrentVersionId() == null) {
            throw new ResourceNotFoundException("Current version not found");
        }

        FileVersion version = fileVersionRepository.findById(file.getCurrentVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Current version not found"));

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(version.getStorageKey())
                .responseContentType(version.getMimeType())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3StorageProperties.downloadUrlExpiryMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return new FileDownloadResponse(
                file.getId(),
                version.getId(),
                file.getName(),
                version.getStorageKey(),
                version.getMimeType(),
                version.getSizeBytes(),
                version.getChecksum(),
                presignedRequest.url().toString()
        );
    }

    public InitiateFileUploadResponse initiateUpload(UUID fileId, InitiateFileUploadRequest request, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getDeletedAt() != null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (!permissionService.canWriteFile(fileId, userId)) {
            throw new ForbiddenOperationException("User not allowed to upload a new version for this file");
        }

        if (request.sizeBytes() <= 0) {
            throw new IllegalArgumentException("sizeBytes must be greater than 0");
        }

        String safeFileName = request.fileName().trim().replaceAll("\\s+", "-");
        String storageKey = "users/" + userId
                + "/files/" + fileId
                + "/uploads/" + Instant.now().toEpochMilli()
                + "-" + safeFileName;

        FileUploadSession session = new FileUploadSession();
        session.setId(UUID.randomUUID());
        session.setFileId(file.getId());
        session.setInitiatedBy(userId);
        session.setStorageKey(storageKey);
        session.setFileName(request.fileName().trim());
        session.setMimeType(request.mimeType());
        session.setSizeBytes(request.sizeBytes());
        session.setStatus(FileUploadStatus.INITIATED.name());
        Instant now = Instant.now();
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(s3StorageProperties.uploadUrlExpiryMinutes() * 60));

        fileUploadSessionRepository.save(session);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(storageKey)
                .contentType(request.mimeType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3StorageProperties.uploadUrlExpiryMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new InitiateFileUploadResponse(
                file.getId(),
                storageKey,
                "S3_PRESIGNED_PUT",
                presignedRequest.url().toString(),
                session.getExpiresAt()
        );
    }

    private boolean isExpired(FileUploadSession session) {
      return session.getExpiresAt().isBefore(Instant.now());
    }

    @Transactional
    public FileVersionResponse completeUpload(UUID fileId, CompleteFileUploadRequest request, UUID userId) {
        FileUploadSession session = fileUploadSessionRepository
                .findByFileIdAndInitiatedByAndStorageKeyAndStatus(
                        fileId,
                        userId,
                        request.storageKey(),
                        FileUploadStatus.INITIATED.name()
                )
                .orElseThrow(() -> new IllegalArgumentException("No initiated upload session found for this file and storageKey"));

        if (!session.getMimeType().equals(request.mimeType())) {
            throw new IllegalArgumentException("mimeType does not match initiated upload");
        }

        if (!session.getSizeBytes().equals(request.sizeBytes())) {
            throw new IllegalArgumentException("sizeBytes does not match initiated upload");
        }

        verifyUploadedObject(request.storageKey(), request.sizeBytes(), request.mimeType());

        if (isExpired(session)) {
            throw new IllegalArgumentException("Upload session has expired");
        }

        CreateFileVersionRequest versionRequest = new CreateFileVersionRequest(
                request.status(),
                request.storageKey(),
                request.sizeBytes(),
                request.mimeType(),
                request.checksum()
        );

        FileVersionResponse response = fileVersionService.create(fileId, versionRequest, userId);

        session.setStatus(FileUploadStatus.COMPLETED.name());
        session.setCompletedAt(Instant.now());
        fileUploadSessionRepository.save(session);

        return response;
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
