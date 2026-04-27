package com.example.dropbox.metadata.shares;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionComputationServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Test
    void computeFolderPermissionUsesNearestSharedAncestor() {
        PermissionComputationService permissionComputationService =
                new PermissionComputationService(shareRepository, folderRepository, fileRecordRepository);
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, ownerId, parentId)));
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(folder(parentId, ownerId, rootId)));
        when(shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId("FOLDER", folderId, userId))
                .thenReturn(Optional.empty());
        when(shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId("FOLDER", parentId, userId))
                .thenReturn(Optional.of(share("EDITOR", ShareStatus.ACTIVE.name(), null)));

        assertEquals("EDITOR", permissionComputationService.computeFolderPermission(folderId, userId));
    }

    @Test
    void computeFilePermissionDirectViewerShareOverridesInheritedFolderEditorShare() {
        PermissionComputationService permissionComputationService =
                new PermissionComputationService(shareRepository, folderRepository, fileRecordRepository);
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        FileRecord file = new FileRecord();
        file.setId(fileId);
        file.setOwnerId(ownerId);
        file.setFolderId(folderId);

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId("FILE", fileId, userId))
                .thenReturn(Optional.of(share("VIEWER", ShareStatus.ACTIVE.name(), null)));

        assertEquals("VIEWER", permissionComputationService.computeFilePermission(fileId, userId));
    }

    @Test
    void computeFolderPermissionExpiredDirectShareDoesNotGrantAccess() {
        PermissionComputationService permissionComputationService =
                new PermissionComputationService(shareRepository, folderRepository, fileRecordRepository);
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, ownerId, null)));
        when(shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId("FOLDER", folderId, userId))
                .thenReturn(Optional.of(share("EDITOR", ShareStatus.ACTIVE.name(), Instant.now().minusSeconds(60))));

        assertEquals("NONE", permissionComputationService.computeFolderPermission(folderId, userId));
    }

    @Test
    void permissionServiceUsesCachedResolverResult() {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CachedPermissionResolver cachedPermissionResolver = new CachedPermissionResolver(null) {
            @Override
            public String getResolvedFolderPermission(UUID ignoredFolderId, UUID ignoredUserId) {
                return "NONE";
            }
        };
        PermissionService permissionService = new PermissionService(cachedPermissionResolver);

        assertFalse(permissionService.canReadFolder(folderId, userId));
    }

    private Folder folder(UUID id, UUID ownerId, UUID parentFolderId) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setOwnerId(ownerId);
        folder.setParentFolderId(parentFolderId);
        return folder;
    }

    private Share share(String permission, String status, Instant expiresAt) {
        Share share = new Share();
        share.setPermission(permission);
        share.setStatus(status);
        share.setExpiresAt(expiresAt);
        return share;
    }
}
