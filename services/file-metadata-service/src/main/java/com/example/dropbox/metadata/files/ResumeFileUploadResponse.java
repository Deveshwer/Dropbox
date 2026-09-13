package com.example.dropbox.metadata.files;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeFileUploadResponse(
        UUID fileId,
        UUID uploadSessionId,
        String uploadMethod,
        Long partSizeBytes,
        Long sizeBytes,
        String mimeType,
        String status,
        List<UploadPartResponse> parts,
        Instant expiresAt
) {
}
