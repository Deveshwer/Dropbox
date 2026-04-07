package com.example.dropbox.metadata.shares;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final CachedPermissionResolver cachedPermissionResolver;

    private static final String NO_PERMISSION = "NONE";

    public boolean canReadFolder(UUID folderId, UUID userId) {
        return !NO_PERMISSION.equals(cachedPermissionResolver.getResolvedFolderPermission(folderId, userId));
    }

    public boolean canWriteFolder(UUID folderId, UUID userId) {
        return SharePermission.EDITOR.name().equals(cachedPermissionResolver.getResolvedFolderPermission(folderId, userId));
    }

    public boolean canReadFile(UUID fileId, UUID userId) {
        return !NO_PERMISSION.equals(cachedPermissionResolver.getResolvedFilePermission(fileId, userId));
    }

    public boolean canWriteFile(UUID fileId, UUID userId) {
        return SharePermission.EDITOR.name().equals(cachedPermissionResolver.getResolvedFilePermission(fileId, userId));
    }
}
