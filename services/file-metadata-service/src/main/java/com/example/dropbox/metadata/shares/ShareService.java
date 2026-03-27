package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.users.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShareService {

    private static final String RESOURCE_TYPE_FILE = "FILE";
    private static final String RESOURCE_TYPE_FOLDER = "FOLDER";
    private static final String PERMISSION_VIEWER = "VIEWER";
    private static final String PERMISSION_EDITOR = "EDITOR";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    @Transactional
    public ShareResponse createOrUpdateShare(CreateShareRequest request, UUID ownerId) {
        validateResourceType(request.resourceType());
        validatePermission(request.permission());
        validateExpiry(request.expiresAt());

        if (ownerId.equals(request.sharedWithUserId())) {
            throw new IllegalArgumentException("Owner cannot share a resource with themselves");
        }

        userRepository.findById(request.sharedWithUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found"));

        validateOwnership(request.resourceType(), request.resourceId(), ownerId);

        Share share = shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId(
                        request.resourceType(),
                        request.resourceId(),
                        request.sharedWithUserId()
                )
                .orElseGet(Share::new);

        if (share.getId() == null) {
            share.setId(UUID.randomUUID());
            share.setCreatedAt(Instant.now());
            share.setResourceType(request.resourceType());
            share.setResourceId(request.resourceId());
            share.setOwnerId(ownerId);
            share.setSharedWithUserId(request.sharedWithUserId());
        }

        share.setPermission(request.permission());
        share.setStatus(STATUS_ACTIVE);
        share.setExpiresAt(request.expiresAt());

        Share saved = shareRepository.save(share);
        return toResponse(saved);
    }

    private void validateResourceType(String resourceType) {
        if (!RESOURCE_TYPE_FILE.equals(resourceType) && !RESOURCE_TYPE_FOLDER.equals(resourceType)) {
            throw new IllegalArgumentException("Invalid resource type");
        }
    }

    private void validatePermission(String permission) {
        if (!PERMISSION_VIEWER.equals(permission) && !PERMISSION_EDITOR.equals(permission)) {
            throw new IllegalArgumentException("Invalid permission");
        }
    }

    private void validateExpiry(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Expiry must be in the future");
        }
    }

    private void validateOwnership(String resourceType, UUID resourceId, UUID ownerId) {
        if (RESOURCE_TYPE_FOLDER.equals(resourceType)) {
            Folder folder = folderRepository.findById(resourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

            if (!folder.getOwnerId().equals(ownerId)) {
                throw new ForbiddenOperationException("You are not allowed to share this folder");
            }

            return;
        }

        FileRecord file = fileRecordRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (!file.getOwnerId().equals(ownerId)) {
            throw new ForbiddenOperationException("You are not allowed to share this file");
        }
    }

    private ShareResponse toResponse(Share share) {
        return new ShareResponse(
                share.getId(),
                share.getResourceType(),
                share.getResourceId(),
                share.getOwnerId(),
                share.getSharedWithUserId(),
                share.getPermission(),
                share.getStatus(),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }
}