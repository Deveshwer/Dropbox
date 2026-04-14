package com.example.dropbox.metadata.folders;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.dropbox.metadata.users.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;

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

    @PatchMapping("/{folderId}/rename")
    public FolderResponse renameFolder(@PathVariable UUID folderId, @Valid @RequestBody RenameFolderRequest request,
        @AuthenticationPrincipal User user) {
        return folderService.renameFolder(folderId, request, user.getId());
    }

    @PatchMapping("/{folderId}/move")
    public FolderResponse moveFolder(
            @PathVariable UUID folderId,
            @Valid @RequestBody MoveFolderRequest request,
            @AuthenticationPrincipal User user
    ) {
        return folderService.moveFolder(folderId, request, user.getId());
    }

    @DeleteMapping("/{folderId}")
    public void deleteFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal User user
    ) {
        folderService.deleteFolder(folderId, user.getId());
    }

    @PatchMapping("/{folderId}/restore")
    public FolderResponse restoreFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal User user
    ) {
        return folderService.restoreFolder(folderId, user.getId());
    }

    @GetMapping("/trash")
    public List<FolderResponse> listDeletedFolders(@AuthenticationPrincipal User user) {
      return folderService.listDeletedFolders(user.getId());
    }

    @DeleteMapping("/{folderId}/permanent")
    public void permanentlyDeleteFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal User user
    ) {
        folderService.permanentlyDeleteFolder(folderId, user.getId());
    }

    @DeleteMapping("/trash")
    public void emptyTrash(@AuthenticationPrincipal User user) {
        folderService.emptyTrash(user.getId());
    }

}
