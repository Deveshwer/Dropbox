package com.example.dropbox.metadata.folders;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateFolderRequest(
        @NotBlank String name,
        UUID parentFolderId
) {
}
