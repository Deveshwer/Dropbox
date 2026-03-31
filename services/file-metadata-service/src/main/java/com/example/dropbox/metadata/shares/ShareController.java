
package com.example.dropbox.metadata.shares;

import com.example.dropbox.metadata.users.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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

    @GetMapping
    public List<ShareResponse> listResourceShares(
            @RequestParam String resourceType,
            @RequestParam UUID resourceId,
            @AuthenticationPrincipal User user
    ) {
        return shareService.listResourceShares(resourceType, resourceId, user.getId());
    }

    @PatchMapping("/{shareId}/revoke")
    public ShareResponse revokeShare(
            @PathVariable UUID shareId,
            @AuthenticationPrincipal User user
    ) {
        return shareService.revokeShare(shareId, user.getId());
    }

    @GetMapping("/me")
    public List<ShareResponse> listMyShares(@AuthenticationPrincipal User user) {
      return shareService.listSharesForCurrentUser(user.getId());
    }
}