package com.example.dropbox.metadata.files;

import com.example.dropbox.metadata.common.ResourceType;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.common.SyncAudienceService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final long DEFAULT_PART_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final String MULTIPART_UPLOAD_METHOD = "S3_MULTIPART_PARTS";
    private static final String PARTS_DOWNLOAD_METHOD = "S3_PRESIGNED_GET_PARTS";
    private static final String MANIFEST_STORAGE_PREFIX = "manifest:";
    private static final String SINGLE_OBJECT_DOWNLOAD_METHOD = "S3_PRESIGNED_GET";

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final FileVersionRepository fileVersionRepository;
    private final ShareRepository shareRepository;
    private final AuditEventService auditEventService;
    private final SyncAudienceService syncAudienceService;
    private final FileUploadSessionRepository fileUploadSessionRepository;
    private final FileUploadPartRepository fileUploadPartRepository;
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

        Set<UUID> syncAudience = syncAudienceService.resolveCurrentReadersForFile(fileId);

        file.setDeletedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        fileRecordRepository.save(file);

        auditEventService.recordEvent(
            "FILE_SOFT_DELETED",
            ResourceType.FILE.name(),
            file.getId(),
            userId,
            "name=" + file.getName(),
            syncAudience
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

        Set<UUID> syncAudience = syncAudienceService.resolveCurrentReadersForFile(fileId);
        int deletedVersionCount = deleteVersionObjectsFromStorage(fileId);

        file.setCurrentVersionId(null);
        file.setUpdatedAt(Instant.now());
        fileRecordRepository.saveAndFlush(file);

        fileUploadSessionRepository.deleteByFileId(fileId);
        fileVersionRepository.deleteByFileId(fileId);
        shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FILE.name(), fileId);
        fileRecordRepository.delete(file);

        auditEventService.recordEvent(
            "FILE_PERMANENTLY_DELETED",
            ResourceType.FILE.name(),
            file.getId(),
            userId,
            "name=" + file.getName() + ",deletedVersionCount=" + deletedVersionCount,
            syncAudience
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
            Set<UUID> syncAudience = syncAudienceService.resolveCurrentReadersForFile(file.getId());
            int deletedVersionCount = deleteVersionObjectsFromStorage(file.getId());

            file.setCurrentVersionId(null);
            file.setUpdatedAt(Instant.now());
            fileRecordRepository.saveAndFlush(file);

            fileUploadSessionRepository.deleteByFileId(file.getId());
            fileVersionRepository.deleteByFileId(file.getId());
            shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FILE.name(), file.getId());
            fileRecordRepository.delete(file);

            auditEventService.recordEvent(
                "FILE_PERMANENTLY_DELETED",
                ResourceType.FILE.name(),
                file.getId(),
                userId,
                "name=" + file.getName()
                        + ",source=emptyTrash"
                        + ",deletedVersionCount=" + deletedVersionCount,
                syncAudience
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

        if (isMultipartVersion(version)) {
            UUID sessionId = parseManifestSessionId(version.getStorageKey());
            List<FileUploadPart> parts = fileUploadPartRepository.findBySessionIdOrderByPartNumberAsc(sessionId);

            return new FileDownloadResponse(
                    file.getId(),
                    version.getId(),
                    file.getName(),
                    PARTS_DOWNLOAD_METHOD,
                    version.getStorageKey(),
                    version.getMimeType(),
                    version.getSizeBytes(),
                    version.getChecksum(),
                    null,
                    parts.stream()
                            .map(part -> toDownloadPartResponse(part, file.getName(), version.getMimeType()))
                            .toList()
            );
        }

        verifyObjectExists(version.getStorageKey());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(version.getStorageKey())
                .responseContentType(version.getMimeType())
                .responseContentDisposition("attachment; filename=\"" + file.getName() + "\"")
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
                SINGLE_OBJECT_DOWNLOAD_METHOD,
                version.getStorageKey(),
                version.getMimeType(),
                version.getSizeBytes(),
                version.getChecksum(),
                presignedRequest.url().toString(),
                List.of()
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

        String uploadPrefix = "users/" + userId
                + "/files/" + fileId
                + "/uploads/" + UUID.randomUUID();

        FileUploadSession session = new FileUploadSession();
        session.setId(UUID.randomUUID());
        session.setFileId(file.getId());
        session.setInitiatedBy(userId);
        session.setStorageKey(uploadPrefix);
        session.setFileName(request.fileName().trim());
        session.setMimeType(request.mimeType());
        session.setSizeBytes(request.sizeBytes());
        session.setStatus(FileUploadStatus.INITIATED.name());
        Instant now = Instant.now();
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(s3StorageProperties.uploadUrlExpiryMinutes() * 60));

        fileUploadSessionRepository.save(session);
        List<FileUploadPart> parts = createPendingParts(session);
        fileUploadPartRepository.saveAll(parts);

        auditEventService.recordEvent(
          "UPLOAD_INITIATED",
          ResourceType.FILE.name(),
          file.getId(),
          userId,
          "uploadSessionId=" + session.getId()
                  + ", storageKey=" + uploadPrefix
                  + ", mimeType=" + request.mimeType()
                  + ", sizeBytes=" + request.sizeBytes()
                  + ", expiresAt=" + session.getExpiresAt()
        );

        return new InitiateFileUploadResponse(
                file.getId(),
                session.getId(),
                MULTIPART_UPLOAD_METHOD,
                DEFAULT_PART_SIZE_BYTES,
                session.getSizeBytes(),
                parts.stream().map(part -> toUploadPartResponse(part, true, session.getMimeType())).toList(),
                session.getExpiresAt()
        );
    }

    public ResumeFileUploadResponse getUploadSession(UUID fileId, UUID sessionId, UUID userId) {
        FileUploadSession session = getUploadSessionForUser(fileId, sessionId, userId);

        return new ResumeFileUploadResponse(
                session.getFileId(),
                session.getId(),
                MULTIPART_UPLOAD_METHOD,
                DEFAULT_PART_SIZE_BYTES,
                session.getSizeBytes(),
                session.getMimeType(),
                session.getStatus(),
                fileUploadPartRepository.findBySessionIdOrderByPartNumberAsc(sessionId)
                        .stream()
                        .map(part -> toUploadPartResponse(
                                part,
                                FileUploadPartStatus.PENDING.name().equals(part.getStatus()),
                                session.getMimeType()
                        ))
                        .toList(),
                session.getExpiresAt()
        );
    }

    @Transactional
    public UploadPartResponse markPartUploaded(
            UUID fileId,
            UUID sessionId,
            Integer partNumber,
            MarkUploadPartUploadedRequest request,
            UUID userId
    ) {
        FileUploadSession session = getUploadSessionForUser(fileId, sessionId, userId);
        if (isExpired(session)) {
            throw new IllegalArgumentException("Upload session has expired");
        }

        FileUploadPart part = fileUploadPartRepository.findBySessionIdAndPartNumber(sessionId, partNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Upload part not found"));

        verifyUploadedObject(part.getStorageKey(), part.getSizeBytes(), session.getMimeType());

        part.setStatus(FileUploadPartStatus.UPLOADED.name());
        part.setChecksum(request == null ? null : request.checksum());
        part.setUploadedAt(Instant.now());
        fileUploadPartRepository.save(part);

        auditEventService.recordEvent(
                "UPLOAD_PART_UPLOADED",
                ResourceType.FILE.name(),
                fileId,
                userId,
                "uploadSessionId=" + sessionId
                        + ", partNumber=" + partNumber
                        + ", storageKey=" + part.getStorageKey()
        );

        return toUploadPartResponse(part, false, session.getMimeType());
    }

    private boolean isExpired(FileUploadSession session) {
      return session.getExpiresAt().isBefore(Instant.now());
    }

    @Transactional
    public FileVersionResponse completeUpload(UUID fileId, CompleteFileUploadRequest request, UUID userId) {
        FileUploadSession session = fileUploadSessionRepository
                .findByFileIdAndInitiatedByAndIdAndStatus(
                        fileId,
                        userId,
                        request.uploadSessionId(),
                        FileUploadStatus.INITIATED.name()
                )
                .orElseThrow(() -> new IllegalArgumentException("No initiated upload session found for this file and storageKey"));

        if (!session.getMimeType().equals(request.mimeType())) {
            throw new IllegalArgumentException("mimeType does not match initiated upload");
        }

        if (!session.getSizeBytes().equals(request.sizeBytes())) {
            throw new IllegalArgumentException("sizeBytes does not match initiated upload");
        }

        if (isExpired(session)) {
            throw new IllegalArgumentException("Upload session has expired");
        }

        List<FileUploadPart> parts = fileUploadPartRepository.findBySessionIdOrderByPartNumberAsc(session.getId());
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Upload session has no parts");
        }

        if (fileUploadPartRepository.existsBySessionIdAndStatus(session.getId(), FileUploadPartStatus.PENDING.name())) {
            throw new IllegalArgumentException("Cannot complete upload while parts are still pending");
        }

        CreateFileVersionRequest versionRequest = new CreateFileVersionRequest(
                request.status(),
                MANIFEST_STORAGE_PREFIX + session.getId(),
                request.sizeBytes(),
                request.mimeType(),
                request.checksum()
        );

        FileVersionResponse response = fileVersionService.create(fileId, versionRequest, userId);

        session.setStatus(FileUploadStatus.COMPLETED.name());
        session.setCompletedAt(Instant.now());
        fileUploadSessionRepository.save(session);

        auditEventService.recordEvent(
          "UPLOAD_COMPLETED",
          ResourceType.FILE.name(),
          fileId,
          userId,
          "versionId=" + response.id()
                  + ", versionNumber=" + response.versionNumber()
                  + ", uploadSessionId=" + session.getId()
                  + ", storageKey=" + response.storageKey()
        );

        return response;
    }

    private void deleteObjectFromStorage(String storageKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(s3StorageProperties.bucket())
                    .key(storageKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (S3Exception ex) {
            // Treat "already missing" as idempotent success.
            if (ex.statusCode() == 404 || "NoSuchKey".equals(ex.awsErrorDetails().errorCode())) {
                return;
            }
            throw ex;
        }
    }

    private int deleteVersionObjectsFromStorage(UUID fileId) {
        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberAsc(fileId);
        for (FileVersion version : versions) {
            if (isMultipartVersion(version)) {
                UUID sessionId = parseManifestSessionId(version.getStorageKey());
                fileUploadPartRepository.findBySessionIdOrderByPartNumberAsc(sessionId)
                        .forEach(part -> deleteObjectFromStorage(part.getStorageKey()));
            } else if (version.getStorageKey() != null && !version.getStorageKey().isBlank()) {
                deleteObjectFromStorage(version.getStorageKey());
            }
        }
        return versions.size();
    }

    private FileUploadSession getUploadSessionForUser(UUID fileId, UUID sessionId, UUID userId) {
        FileUploadSession session = fileUploadSessionRepository
                .findByFileIdAndInitiatedByAndId(
                        fileId,
                        userId,
                        sessionId
                )
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found"));

        if (!FileUploadStatus.INITIATED.name().equals(session.getStatus())) {
            throw new IllegalArgumentException("Upload session is not active");
        }

        return session;
    }

    private List<FileUploadPart> createPendingParts(FileUploadSession session) {
        List<FileUploadPart> parts = new ArrayList<>();
        long remainingBytes = session.getSizeBytes();
        int partNumber = 1;

        while (remainingBytes > 0) {
            long partSize = Math.min(DEFAULT_PART_SIZE_BYTES, remainingBytes);
            FileUploadPart part = new FileUploadPart();
            part.setId(UUID.randomUUID());
            part.setSessionId(session.getId());
            part.setPartNumber(partNumber);
            part.setStorageKey(session.getStorageKey() + "/parts/" + partNumber);
            part.setSizeBytes(partSize);
            part.setStatus(FileUploadPartStatus.PENDING.name());
            part.setCreatedAt(session.getCreatedAt());
            parts.add(part);

            remainingBytes -= partSize;
            partNumber++;
        }

        return parts;
    }

    private UploadPartResponse toUploadPartResponse(FileUploadPart part, boolean includeUploadUrl, String mimeType) {
        String uploadUrl = includeUploadUrl ? createPartUploadUrl(part, mimeType) : null;
        return new UploadPartResponse(
                part.getPartNumber(),
                part.getStorageKey(),
                part.getSizeBytes(),
                part.getStatus(),
                uploadUrl
        );
    }

    private String createPartUploadUrl(FileUploadPart part, String mimeType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(part.getStorageKey())
                .contentType(mimeType)
                .contentLength(part.getSizeBytes())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3StorageProperties.uploadUrlExpiryMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    private FileDownloadPartResponse toDownloadPartResponse(FileUploadPart part, String fileName, String mimeType) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(part.getStorageKey())
                .responseContentType(mimeType)
                .responseContentDisposition("attachment; filename=\"" + fileName + ".part" + part.getPartNumber() + "\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3StorageProperties.downloadUrlExpiryMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return new FileDownloadPartResponse(
                part.getPartNumber(),
                part.getStorageKey(),
                part.getSizeBytes(),
                presignedRequest.url().toString()
        );
    }

    private void verifyObjectExists(String storageKey) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(s3StorageProperties.bucket())
                .key(storageKey)
                .build();

        try {
            s3Client.headObject(headObjectRequest);
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Current file object not found in storage");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResourceNotFoundException("Current file object not found in storage");
            }
            throw ex;
        }
    }

    private boolean isMultipartVersion(FileVersion version) {
        return version.getStorageKey() != null && version.getStorageKey().startsWith(MANIFEST_STORAGE_PREFIX);
    }

    private UUID parseManifestSessionId(String storageKey) {
        return UUID.fromString(storageKey.substring(MANIFEST_STORAGE_PREFIX.length()));
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
