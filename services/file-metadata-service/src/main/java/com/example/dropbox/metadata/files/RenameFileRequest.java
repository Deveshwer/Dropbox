package com.example.dropbox.metadata.files;

import jakarta.validation.constraints.NotBlank;

public record RenameFileRequest(
        @NotBlank String name
) {
}