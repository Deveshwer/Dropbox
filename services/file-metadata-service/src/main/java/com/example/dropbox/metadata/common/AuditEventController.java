package com.example.dropbox.metadata.common;

import com.example.dropbox.metadata.users.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEventService;

    @GetMapping("/me")
    public List<AuditEventResponse> listMyEvents(@AuthenticationPrincipal User user) {
        return auditEventService.listEventsForActor(user.getId());
    }

    @GetMapping("/resources/{resourceType}/{resourceId}")
    public List<AuditEventResponse> listResourceEvents(
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            @AuthenticationPrincipal User user
    ) {
        return auditEventService.listEventsForResource(resourceType, resourceId, user.getId());
    }
}