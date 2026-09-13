package com.example.dropbox.metadata.files;

public record UploadPartResponse(
        Integer partNumber,
        String storageKey,
        Long sizeBytes,
        String status,
        String uploadUrl
) {
}
