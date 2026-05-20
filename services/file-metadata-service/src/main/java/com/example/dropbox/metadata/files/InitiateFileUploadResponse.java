package com.example.dropbox.metadata.files;

import java.time.Instant;
import java.util.UUID;

public record InitiateFileUploadResponse(
        UUID fileId,
        String storageKey,
        String uploadMethod,
        String uploadUrl,
        Instant expiresAt
) {
}
