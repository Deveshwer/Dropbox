package com.example.dropbox.metadata.folders;
import java.time.Instant;
import java.util.*;

public record SearchResultItem(
        UUID id,
        String name,
        String resourceType,
        UUID parentFolderId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
}

