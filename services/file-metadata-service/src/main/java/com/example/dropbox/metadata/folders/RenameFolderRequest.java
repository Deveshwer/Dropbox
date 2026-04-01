package com.example.dropbox.metadata.folders;

import jakarta.validation.constraints.NotBlank;

public record RenameFolderRequest(
        @NotBlank String name
) {
}