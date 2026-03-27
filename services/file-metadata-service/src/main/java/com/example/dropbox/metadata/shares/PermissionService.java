package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String RESOURCE_TYPE_FILE = "FILE";
    private static final String RESOURCE_TYPE_FOLDER = "FOLDER";
    private static final String PERMISSION_VIEWER = "VIEWER";
    private static final String PERMISSION_EDITOR = "EDITOR";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ShareRepository shareRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    public boolean canReadFolder(UUID folderId, UUID userId) {
        return resolveFolderPermission(folderId, userId) != null;
    }

    public boolean canWriteFolder(UUID folderId, UUID userId) {
        String permission = resolveFolderPermission(folderId, userId);
        return PERMISSION_EDITOR.equals(permission);
    }

    public boolean canReadFile(UUID fileId, UUID userId) {
        return resolveFilePermission(fileId, userId) != null;
    }

    public boolean canWriteFile(UUID fileId, UUID userId) {
        String permission = resolveFilePermission(fileId, userId);
        return PERMISSION_EDITOR.equals(permission);
    }

    private String resolveFolderPermission(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getOwnerId().equals(userId)) {
            return PERMISSION_EDITOR;
        }

        Optional<Share> directShare = shareRepository
                .findByResourceTypeAndResourceIdAndSharedWithUserId(
                        RESOURCE_TYPE_FOLDER,
                        folderId,
                        userId
                );

        if (directShare.isPresent() && isActiveAndNotExpired(directShare.get())) {
            return directShare.get().getPermission();
        }

        UUID currentParentId = folder.getParentFolderId();

        while (currentParentId != null) {
            Folder parentFolder = folderRepository.findById(currentParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

            if (parentFolder.getOwnerId().equals(userId)) {
                return PERMISSION_EDITOR;
            }

            Optional<Share> ancestorShare = shareRepository
                    .findByResourceTypeAndResourceIdAndSharedWithUserId(
                            RESOURCE_TYPE_FOLDER,
                            currentParentId,
                            userId
                    );

            if (ancestorShare.isPresent() && isActiveAndNotExpired(ancestorShare.get())) {
                return ancestorShare.get().getPermission();
            }

            currentParentId = parentFolder.getParentFolderId();
        }

        return null;
    }

    private String resolveFilePermission(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getOwnerId().equals(userId)) {
            return PERMISSION_EDITOR;
        }

        Optional<Share> directShare = shareRepository
                .findByResourceTypeAndResourceIdAndSharedWithUserId(
                        RESOURCE_TYPE_FILE,
                        fileId,
                        userId
                );

        if (directShare.isPresent() && isActiveAndNotExpired(directShare.get())) {
            return directShare.get().getPermission();
        }

        return resolveFolderPermission(file.getFolderId(), userId);
    }

    private boolean isActiveAndNotExpired(Share share) {
        if (!STATUS_ACTIVE.equals(share.getStatus())) {
            return false;
        }

        Instant expiresAt = share.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
