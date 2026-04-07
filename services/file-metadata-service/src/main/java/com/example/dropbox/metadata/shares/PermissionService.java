package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.common.ResourceType;
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
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ShareRepository shareRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    private static final String NO_PERMISSION = "NONE";

    public boolean canReadFolder(UUID folderId, UUID userId) {
        return !NO_PERMISSION.equals(getResolvedFolderPermission(folderId, userId));
    }

    public boolean canWriteFolder(UUID folderId, UUID userId) {
        return SharePermission.EDITOR.name().equals(getResolvedFolderPermission(folderId, userId));
    }

    public boolean canReadFile(UUID fileId, UUID userId) {
        return !NO_PERMISSION.equals(getResolvedFilePermission(fileId, userId));
    }

    public boolean canWriteFile(UUID fileId, UUID userId) {
        return SharePermission.EDITOR.name().equals(getResolvedFilePermission(fileId, userId));
    }

    @Cacheable(value = "folderPermissions", key = "#folderId.toString() + ':' + #userId.toString()")
    public String getResolvedFolderPermission(UUID folderId, UUID userId) {
        return computeFolderPermission(folderId, userId);
    }

    @Cacheable(value = "filePermissions", key = "#fileId.toString() + ':' + #userId.toString()")
    public String getResolvedFilePermission(UUID fileId, UUID userId) {
        return computeFilePermission(fileId, userId);
    }

    private String computeFolderPermission(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getOwnerId().equals(userId)) {
            return SharePermission.EDITOR.name();
        }

        Optional<Share> directShare = shareRepository
                .findByResourceTypeAndResourceIdAndSharedWithUserId(
                        ResourceType.FOLDER.name(),
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
                return SharePermission.EDITOR.name();
            }

            Optional<Share> ancestorShare = shareRepository
                    .findByResourceTypeAndResourceIdAndSharedWithUserId(
                            ResourceType.FOLDER.name(),
                            currentParentId,
                            userId
                    );

            if (ancestorShare.isPresent() && isActiveAndNotExpired(ancestorShare.get())) {
                return ancestorShare.get().getPermission();
            }

            currentParentId = parentFolder.getParentFolderId();
        }

        return NO_PERMISSION;
    }

    private String computeFilePermission(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getOwnerId().equals(userId)) {
            return SharePermission.EDITOR.name();
        }

        Optional<Share> directShare = shareRepository
                .findByResourceTypeAndResourceIdAndSharedWithUserId(
                        ResourceType.FILE.name(),
                        fileId,
                        userId
                );

        if (directShare.isPresent() && isActiveAndNotExpired(directShare.get())) {
            return directShare.get().getPermission();
        }

        return getResolvedFolderPermission(file.getFolderId(), userId);
    }

    private boolean isActiveAndNotExpired(Share share) {
        if (!ShareStatus.ACTIVE.name().equals(share.getStatus())) {
            return false;
        }

        Instant expiresAt = share.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
