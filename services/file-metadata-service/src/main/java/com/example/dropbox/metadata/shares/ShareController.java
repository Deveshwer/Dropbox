
package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.users.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public ShareResponse createOrUpdateShare(
            @Valid @RequestBody CreateShareRequest request,
            @AuthenticationPrincipal User user
    ) {
        return shareService.createOrUpdateShare(request, user.getId());
    }
}