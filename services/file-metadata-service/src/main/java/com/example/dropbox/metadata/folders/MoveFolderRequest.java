package com.example.dropbox.metadata.folders;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveFolderRequest(
        @NotNull UUID targetParentFolderId
) {
}