package com.example.dropbox.metadata.files;

import java.util.List;
import java.util.UUID;

public record FileDownloadResponse(
        UUID fileId,
        UUID versionId,
        String fileName,
        String downloadMethod,
        String storageKey,
        String mimeType,
        Long sizeBytes,
        String checksum,
        String downloadUrl,
        List<FileDownloadPartResponse> parts
) {
}
