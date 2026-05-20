package com.example.dropbox.metadata.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        String bucket,
        String region,
        long uploadUrlExpiryMinutes
) {
}