package com.example.dropbox.metadata.files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dropbox.metadata.common.AuditEventService;
import com.example.dropbox.metadata.common.AuditEventRepository;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.shares.ShareRepository;
import com.example.dropbox.metadata.versions.FileVersion;
import com.example.dropbox.metadata.versions.FileVersionRepository;
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

    @Test
    void deleteFileThrowsWhenVersionsExist() {
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                new AuditEventService(auditEventRepository, null, null)
        );
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileRecord file = file(fileId, ownerId);
        FileVersion version = new FileVersion();
        version.setId(UUID.randomUUID());
        version.setFileId(fileId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdOrderByVersionNumberAsc(fileId)).thenReturn(List.of(version));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.deleteFile(fileId, ownerId)
        );

        assertEquals("File cannot be deleted because versions exist", ex.getMessage());
        verify(shareRepository, never()).deleteByResourceTypeAndResourceId(any(), any());
        verify(fileRecordRepository, never()).delete(any(FileRecord.class));
    }

    @Test
    void deleteFileDeletesShareRowsAndFileWhenAllowed() {
        FileService fileService = new FileService(
                fileRecordRepository,
                folderRepository,
                null,
                fileVersionRepository,
                shareRepository,
                new AuditEventService(auditEventRepository, null, null)
        );
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileRecord file = file(fileId, ownerId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdOrderByVersionNumberAsc(fileId)).thenReturn(List.of());

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
                new AuditEventService(auditEventRepository, null, null)
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
