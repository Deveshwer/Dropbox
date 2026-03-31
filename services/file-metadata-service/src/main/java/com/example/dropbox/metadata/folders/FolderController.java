package com.example.dropbox.metadata.folders;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.dropbox.metadata.users.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    @PostMapping
    public FolderResponse create(@Valid @RequestBody CreateFolderRequest request, @AuthenticationPrincipal User user) {
        return folderService.create(request, user.getId());
    }

    @GetMapping("/{folderId}/children")
    public FolderChildrenResponse getChildren(@PathVariable UUID folderId, @AuthenticationPrincipal User user) {
        return folderService.getChildren(folderId, user.getId());
    }

    @GetMapping("/{folderId}")
    public FolderResponse getFolder(@PathVariable UUID folderId, @AuthenticationPrincipal User user) {
        return folderService.getFolder(folderId, user.getId());
    }
}