package com.example.dropbox.metadata.shares;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateShareRequest(
        @NotBlank String resourceType,
        @NotNull UUID resourceId,
        @NotNull UUID sharedWithUserId,
        @NotBlank String permission,
        Instant expiresAt
) {
}