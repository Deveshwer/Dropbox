package com.example.dropbox.metadata.folders;
import java.util.*;

public record SearchResponse(
        List<SearchResultItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
