package com.example.dropbox.metadata.versions;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.example.dropbox.metadata.users.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/files/{fileId}/versions")
@RequiredArgsConstructor
public class FileVersionController {

    private final FileVersionService fileVersionService;

    @PostMapping
    public FileVersionResponse create(
            @PathVariable UUID fileId,
            @Valid @RequestBody CreateFileVersionRequest request,
            @AuthenticationPrincipal User user
    ) {
        return fileVersionService.create(fileId, request, user.getId());
    }

    @GetMapping
    public List<FileVersionResponse> getVersions(@PathVariable UUID fileId, @AuthenticationPrincipal User user) {
        return fileVersionService.getVersions(fileId, user.getId());
    }
}