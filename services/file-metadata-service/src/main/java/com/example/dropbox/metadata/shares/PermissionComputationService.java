package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.common.ResourceType;
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
public class PermissionComputationService {

    private static final String NO_PERMISSION = "NONE";

    private final ShareRepository shareRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    public String computeFolderPermission(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getOwnerId().equals(userId)) {
            return SharePermission.EDITOR.name();
        }

        Optional<Share> directShare = shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId(
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

            Optional<Share> ancestorShare = shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId(
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

    public String computeFilePermission(UUID fileId, UUID userId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getOwnerId().equals(userId)) {
            return SharePermission.EDITOR.name();
        }

        Optional<Share> directShare = shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId(
                ResourceType.FILE.name(),
                fileId,
                userId
        );

        if (directShare.isPresent() && isActiveAndNotExpired(directShare.get())) {
            return directShare.get().getPermission();
        }

        return computeFolderPermission(file.getFolderId(), userId);
    }

    private boolean isActiveAndNotExpired(Share share) {
        if (!ShareStatus.ACTIVE.name().equals(share.getStatus())) {
            return false;
        }

        Instant expiresAt = share.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
