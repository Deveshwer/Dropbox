package com.example.dropbox.metadata.files;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}