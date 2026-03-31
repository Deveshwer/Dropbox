package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.common.ResourceType;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.folders.Folder;
import com.example.dropbox.metadata.folders.FolderRepository;
import com.example.dropbox.metadata.users.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;

    @Transactional // If anything fails in the function, rollback completely(function should be atomic)
    public ShareResponse createOrUpdateShare(CreateShareRequest request, UUID ownerId) {
        ResourceType resourceType = parseResourceType(request.resourceType());
        SharePermission permission = parsePermission(request.permission());
        validateExpiry(request.expiresAt());

        if (ownerId.equals(request.sharedWithUserId())) {
            throw new IllegalArgumentException("Owner cannot share a resource with themselves");
        }

        userRepository.findById(request.sharedWithUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found"));

        validateOwnership(resourceType, request.resourceId(), ownerId);

        Share share = shareRepository.findByResourceTypeAndResourceIdAndSharedWithUserId(
                        resourceType.name(),
                        request.resourceId(),
                        request.sharedWithUserId()
                )
                .orElseGet(Share::new);

        if (share.getId() == null) {
            share.setId(UUID.randomUUID());
            share.setCreatedAt(Instant.now());
            share.setResourceType(resourceType.name());
            share.setResourceId(request.resourceId());
            share.setOwnerId(ownerId);
            share.setSharedWithUserId(request.sharedWithUserId());
        }

        share.setPermission(permission.name());
        share.setStatus(ShareStatus.ACTIVE.name());
        share.setExpiresAt(request.expiresAt());

        Share saved = shareRepository.save(share);
        return toResponse(saved);
    }

    private ResourceType parseResourceType(String resourceType) {
        try {
            return ResourceType.valueOf(resourceType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid resource type");
        }
    }

    private SharePermission parsePermission(String permission) {
        try {
            return SharePermission.valueOf(permission);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid permission");
        }
    }

    private void validateExpiry(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Expiry must be in the future");
        }
    }

    private void validateOwnership(ResourceType resourceType, UUID resourceId, UUID ownerId) {
        if (ResourceType.FOLDER.equals(resourceType)) {
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

    public List<ShareResponse> listResourceShares(String resourceType, UUID resourceId, UUID ownerId) {
    ResourceType parsedResourceType = parseResourceType(resourceType);
    validateOwnership(parsedResourceType, resourceId, ownerId);

    return shareRepository.findByResourceTypeAndResourceId(parsedResourceType.name(), resourceId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ShareResponse revokeShare(UUID shareId, UUID ownerId) {
        Share share = shareRepository.findById(shareId)
            .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (!share.getOwnerId().equals(ownerId)) {
            throw new ForbiddenOperationException("You are not allowed to revoke this share");
        }

        share.setStatus(ShareStatus.REVOKED.name());
        Share saved = shareRepository.save(share);

        return toResponse(saved);
    }

    public List<ShareResponse> listSharesForCurrentUser(UUID userId) {
    return shareRepository.findBySharedWithUserIdAndStatus(userId, ShareStatus.ACTIVE.name())
            .stream()
            .filter(this::isNotExpired)
            .map(this::toResponse)
            .toList();
    }

    private boolean isNotExpired(Share share) {
        Instant expiresAt = share.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
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
