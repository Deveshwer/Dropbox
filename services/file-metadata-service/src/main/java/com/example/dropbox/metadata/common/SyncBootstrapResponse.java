package com.example.dropbox.metadata.common;

import java.util.List;

public record SyncBootstrapResponse(
        List<SyncEventResponse> events,
        Long nextCursor,
        boolean hasMore
) {
}