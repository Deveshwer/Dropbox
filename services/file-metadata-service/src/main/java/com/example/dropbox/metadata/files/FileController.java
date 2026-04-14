package com.example.dropbox.metadata.files;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;
import com.example.dropbox.metadata.users.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping
    public FileResponse create(@Valid @RequestBody CreateFileRequest request, @AuthenticationPrincipal User user) {
        return fileService.create(request, user.getId());
    }

    @GetMapping("/{fileId}")
    public FileResponse getFile(@PathVariable UUID fileId, @AuthenticationPrincipal User user) {
        return fileService.getFile(fileId, user.getId());
    }

    @PatchMapping("/{fileId}/rename")
    public FileResponse renameFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody RenameFileRequest request,
            @AuthenticationPrincipal User user
    ) {
        return fileService.renameFile(fileId, request, user.getId());
    }

    @PatchMapping("/{fileId}/move")
    public FileResponse moveFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody MoveFileRequest request,
            @AuthenticationPrincipal User user
    ) {
        return fileService.moveFile(fileId, request, user.getId());
    }

    @DeleteMapping("/{fileId}")
    public void deleteFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal User user
    ) {
        fileService.deleteFile(fileId, user.getId());
    }

    @PatchMapping("/{fileId}/restore")
    public FileResponse restoreFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal User user
    ) {
        return fileService.restoreFile(fileId, user.getId());
    }

    @GetMapping("/trash")
    public List<FileResponse> listDeletedFiles(@AuthenticationPrincipal User user) {
        return fileService.listDeletedFiles(user.getId());
    }

    @DeleteMapping("/{fileId}/permanent")
    public void permanentlyDeleteFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal User user
    ) {
        fileService.permanentlyDeleteFile(fileId, user.getId());
    }

    @DeleteMapping("/trash")
    public void emptyTrash(@AuthenticationPrincipal User user) {
        fileService.emptyTrash(user.getId());
    }
}
