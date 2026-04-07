package com.example.dropbox.metadata.shares;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CachedPermissionResolver {

    private final PermissionComputationService permissionComputationService;

    @Cacheable(value = "folderPermissions", key = "#folderId.toString() + ':' + #userId.toString()")
    public String getResolvedFolderPermission(UUID folderId, UUID userId) {
        return permissionComputationService.computeFolderPermission(folderId, userId);
    }

    @Cacheable(value = "filePermissions", key = "#fileId.toString() + ':' + #userId.toString()")
    public String getResolvedFilePermission(UUID fileId, UUID userId) {
        return permissionComputationService.computeFilePermission(fileId, userId);
    }
}
