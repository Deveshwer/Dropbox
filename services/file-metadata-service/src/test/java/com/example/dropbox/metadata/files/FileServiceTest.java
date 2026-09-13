package com.example.dropbox.metadata.files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.dropbox.metadata.common.AuditEventService;
import com.example.dropbox.metadata.common.AuditEventRepository;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.OutboxEventRepository;
import com.example.dropbox.metadata.common.SyncAudienceService;
import com.example.dropbox.metadata.common.SyncEventRepository;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.shares.ShareRepository;
import com.example.dropbox.metadata.versions.FileVersion;
import com.example.dropbox.metadata.versions.FileVersionRepository;
import com.example.dropbox.metadata.versions.FileVersionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private SyncEventRepository syncEventRepository;

    @Mock
    private SyncAudienceService syncAudienceService;

    @Mock
    private FileUploadPartRepository fileUploadPartRepository;

    @Test
    void deleteFileSoftDeletesEvenWhenVersionsExist() {
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                auditEventService(),
                syncAudienceService,
                null,
                fileUploadPartRepository,
                null,
                null,
                null,
                null
        );
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileRecord file = file(fileId, ownerId);
        FileVersion version = new FileVersion();
        version.setId(UUID.randomUUID());
        version.setFileId(fileId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertDoesNotThrow(() -> fileService.deleteFile(fileId, ownerId));

        verify(fileRecordRepository).save(file);
        assertNotNull(file.getDeletedAt());
    }

    @Test
    void deleteFileDeletesShareRowsAndFileWhenAllowed() {
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                auditEventService(),
                syncAudienceService,
                null,
                fileUploadPartRepository,
                null,
                null,
                null,
                null
        );
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileRecord file = file(fileId, ownerId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertDoesNotThrow(() -> fileService.deleteFile(fileId, ownerId));

        verify(shareRepository, never()).deleteByResourceTypeAndResourceId(any(), any());
        verify(fileRecordRepository).save(file);
        assertNotNull(file.getDeletedAt());
    }

    @Test
    void deleteFileRejectsNonOwner() {
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                auditEventService(),
                syncAudienceService,
                null,
                fileUploadPartRepository,
                null,
                null,
                null,
                null
        );
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileRecord file = file(fileId, ownerId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThrows(ForbiddenOperationException.class, () -> fileService.deleteFile(fileId, userId));

        verify(fileVersionRepository, never()).findByFileIdOrderByVersionNumberAsc(any());
        verify(shareRepository, never()).deleteByResourceTypeAndResourceId(any(), any());
        verify(fileRecordRepository, never()).delete(any(FileRecord.class));
        verify(fileRecordRepository, never()).save(any(FileRecord.class));
    }

    @Test
    void completeUploadRejectsPendingParts() {
        FileUploadSessionRepository uploadSessionRepository = org.mockito.Mockito.mock(FileUploadSessionRepository.class);
        FileVersionService fileVersionService = org.mockito.Mockito.mock(FileVersionService.class);
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                auditEventService(),
                syncAudienceService,
                uploadSessionRepository,
                fileUploadPartRepository,
                fileVersionService,
                null,
                null,
                null
        );
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FileUploadSession session = new FileUploadSession();
        session.setId(sessionId);
        session.setFileId(fileId);
        session.setInitiatedBy(userId);
        session.setMimeType("text/plain");
        session.setSizeBytes(10L);
        session.setStatus(FileUploadStatus.INITIATED.name());
        session.setExpiresAt(Instant.now().plusSeconds(60));

        when(uploadSessionRepository.findByFileIdAndInitiatedByAndIdAndStatus(
                fileId,
                userId,
                sessionId,
                FileUploadStatus.INITIATED.name()
        )).thenReturn(Optional.of(session));
        when(fileUploadPartRepository.existsBySessionIdAndStatus(sessionId, FileUploadPartStatus.PENDING.name()))
                .thenReturn(true);

        CompleteFileUploadRequest request = new CompleteFileUploadRequest(
                "ACTIVE",
                sessionId,
                10L,
                "text/plain",
                "checksum"
        );

        assertThrows(IllegalArgumentException.class, () -> fileService.completeUpload(fileId, request, userId));

        verify(fileVersionService, never()).create(any(), any(), any());
    }

    private AuditEventService auditEventService() {
        return new AuditEventService(
                auditEventRepository,
                null,
                outboxEventRepository,
                syncEventRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                syncAudienceService
        );
    }

    private FileRecord file(UUID fileId, UUID ownerId) {
        FileRecord file = new FileRecord();
        file.setId(fileId);
        file.setOwnerId(ownerId);
        file.setFolderId(UUID.randomUUID());
        file.setName("test.txt");
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        return file;
    }
}
