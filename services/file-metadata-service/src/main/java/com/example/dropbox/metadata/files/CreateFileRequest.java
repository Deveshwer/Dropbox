package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateFileRequest(
        @NotBlank String name,
        @NotNull UUID folderId
) {
}