package com.example.dropbox.metadata.folders;

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
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.shares.ShareRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRecordRepository fileRecordRepository;

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

    @Test
    void deleteFolderDeletesShareRowsAndFolderWhenEmpty() {
        FolderService folderService = new FolderService(
                folderRepository,
                fileRecordRepository,
                null,
                shareRepository,
                auditEventService(),
                null,
                syncAudienceService
        );
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder folder = folder(folderId, ownerId, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findByParentFolderId(folderId)).thenReturn(List.of());
        when(fileRecordRepository.findByFolderId(folderId)).thenReturn(List.of());

        assertDoesNotThrow(() -> folderService.deleteFolder(folderId, ownerId));

        verify(shareRepository, never()).deleteByResourceTypeAndResourceId(any(), any());
        verify(folderRepository).save(folder);
        assertNotNull(folder.getDeletedAt());
    }

    @Test
    void deleteFolderSoftDeletesSubtreeRecursively() {
        FolderService folderService = new FolderService(
                folderRepository,
                fileRecordRepository,
                null,
                shareRepository,
                auditEventService(),
                null,
                syncAudienceService
        );
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder folder = folder(folderId, ownerId, null);
        Folder child = folder(UUID.randomUUID(), ownerId, folderId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findByParentFolderId(folderId)).thenReturn(List.of(child));
        when(folderRepository.findByParentFolderId(child.getId())).thenReturn(List.of());
        when(fileRecordRepository.findByFolderId(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> folderService.deleteFolder(folderId, ownerId));

        verify(folderRepository).save(folder);
        verify(folderRepository).save(child);
        assertNotNull(folder.getDeletedAt());
        assertNotNull(child.getDeletedAt());
    }

    @Test
    void deleteFolderRejectsNonOwner() {
        FolderService folderService = new FolderService(
                folderRepository,
                fileRecordRepository,
                null,
                shareRepository,
                auditEventService(),
                null,
                syncAudienceService
        );
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Folder folder = folder(folderId, ownerId, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));

        assertThrows(ForbiddenOperationException.class, () -> folderService.deleteFolder(folderId, userId));

        verify(folderRepository, never()).findByParentFolderId(any());
        verify(fileRecordRepository, never()).findByFolderId(any());
        verify(shareRepository, never()).deleteByResourceTypeAndResourceId(any(), any());
        verify(folderRepository, never()).save(any(Folder.class));
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

    private Folder folder(UUID id, UUID ownerId, UUID parentFolderId) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setOwnerId(ownerId);
        folder.setParentFolderId(parentFolderId);
        folder.setName("folder");
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return folder;
    }
}
