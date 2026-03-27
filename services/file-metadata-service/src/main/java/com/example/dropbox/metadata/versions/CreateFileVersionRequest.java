package com.example.dropbox.metadata.versions;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateFileVersionRequest(
        @NotNull String status
) {
}