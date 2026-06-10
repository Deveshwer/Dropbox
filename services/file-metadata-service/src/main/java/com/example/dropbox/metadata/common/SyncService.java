package com.example.dropbox.metadata.common;

import com.example.dropbox.metadata.shares.PermissionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int MAX_LIMIT = 200;
    private static final int SCAN_MULTIPLIER = 3;
    private static final int MAX_SCAN_ROUNDS = 5;

    private final SyncEventRepository syncEventRepository;
    private final PermissionService permissionService;

    public SyncBootstrapResponse bootstrap(Long cursor, Integer limit, UUID userId) {
        long safeCursor = cursor == null ? 0L : cursor;
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<SyncEventResponse> visibleEvents = new ArrayList<>();
        long lastScannedCursor = safeCursor;
        int batchSize = Math.min(MAX_LIMIT, safeLimit * SCAN_MULTIPLIER);

        for (int round = 0; round < MAX_SCAN_ROUNDS && visibleEvents.size() < safeLimit; round++) {
            List<SyncEvent> batch = syncEventRepository.findByCursorGreaterThanOrderByCursorAsc(
                    lastScannedCursor,
                    PageRequest.of(0, batchSize)
            );

            if (batch.isEmpty()) {
                break;
            }

            for (SyncEvent event : batch) {
                lastScannedCursor = event.getCursor();

                if (canUserSeeEvent(event, userId)) {
                    visibleEvents.add(toResponse(event));
                    if (visibleEvents.size() == safeLimit) {
                        break;
                    }
                }
            }

            if (batch.size() < batchSize) {
                break;
            }
        }

        boolean hasMore = syncEventRepository.existsByCursorGreaterThan(lastScannedCursor);

        return new SyncBootstrapResponse(
                visibleEvents,
                lastScannedCursor,
                hasMore
        );
    }

    private boolean canUserSeeEvent(SyncEvent event, UUID userId) {
        if (ResourceType.FILE.name().equals(event.getResourceType())) {
            return permissionService.canReadFile(event.getResourceId(), userId);
        }

        if (ResourceType.FOLDER.name().equals(event.getResourceType())) {
            return permissionService.canReadFolder(event.getResourceId(), userId);
        }

        return false;
    }

    private Map<String, Object> parseMetadata(String metadata) {
        Map<String, Object> parsed = new LinkedHashMap<>();

        if (metadata == null || metadata.isBlank()) {
            return parsed;
        }

        String[] entries = metadata.split(",");

        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                parsed.put(parts[0].trim(), parts[1].trim());
            }
        }

        return parsed;
    }

    private SyncEventResponse toResponse(SyncEvent event) {
        return new SyncEventResponse(
                event.getCursor(),
                event.getEventId(),
                event.getEventType(),
                event.getResourceType(),
                event.getResourceId(),
                event.getActorId(),
                parseMetadata(event.getMetadata()),
                event.getCreatedAt()
        );
    }
}