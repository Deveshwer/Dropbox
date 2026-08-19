package com.example.dropbox.metadata.common;

import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.shares.Share;
import com.example.dropbox.metadata.shares.ShareRepository;
import com.example.dropbox.metadata.shares.ShareStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncAudienceService {

    private final ShareRepository shareRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    public Set<UUID> resolveCurrentReadersForFile(UUID fileId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        LinkedHashSet<UUID> audience = new LinkedHashSet<>();
        audience.add(file.getOwnerId());
        addActiveShareRecipients(ResourceType.FILE.name(), fileId, audience);
        audience.addAll(resolveCurrentReadersForFolder(file.getFolderId()));
        return audience;
    }

    public Set<UUID> resolveCurrentReadersForFolder(UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        LinkedHashSet<UUID> audience = new LinkedHashSet<>();
        while (folder != null) {
            audience.add(folder.getOwnerId());
            addActiveShareRecipients(ResourceType.FOLDER.name(), folder.getId(), audience);

            UUID parentId = folder.getParentFolderId();
            folder = parentId == null ? null : folderRepository.findById(parentId)
                                               .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        }
        return audience;
    }

    private void addActiveShareRecipients(String resourceType, UUID resourceId, Set<UUID> audience) {
        shareRepository.findByResourceTypeAndResourceIdAndStatus(resourceType, resourceId, ShareStatus.ACTIVE.name())
                .stream()
                .filter(this::isNotExpired)
                .map(Share::getSharedWithUserId)
                .forEach(audience::add);
    }

    private boolean isNotExpired(Share share) {
        Instant expiresAt = share.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
