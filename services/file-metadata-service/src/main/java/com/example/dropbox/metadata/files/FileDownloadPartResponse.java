package com.example.dropbox.metadata.files;

public record FileDownloadPartResponse(
        Integer partNumber,
        String storageKey,
        Long sizeBytes,
        String downloadUrl
) {
}
