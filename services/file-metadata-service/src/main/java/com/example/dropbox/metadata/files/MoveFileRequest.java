package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveFileRequest(
        @NotNull UUID targetFolderId
) {
}