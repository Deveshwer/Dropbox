package com.example.dropbox.metadata.common;

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

    private final SyncEventRepository syncEventRepository;

    public SyncBootstrapResponse bootstrap(Long cursor, Integer limit, UUID userId) {
        long safeCursor = cursor == null ? 0L : cursor;
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<SyncEvent> events = syncEventRepository.findByUserIdAndCursorGreaterThanOrderByCursorAsc(
                userId,
                safeCursor,
                PageRequest.of(0, safeLimit)
        );

        long nextCursor = events.isEmpty()
                ? safeCursor
                : events.get(events.size() - 1).getCursor();

        boolean hasMore = syncEventRepository.existsByUserIdAndCursorGreaterThan(userId, nextCursor);

        return new SyncBootstrapResponse(
                events.stream().map(this::toResponse).toList(),
                nextCursor,
                hasMore
        );
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
