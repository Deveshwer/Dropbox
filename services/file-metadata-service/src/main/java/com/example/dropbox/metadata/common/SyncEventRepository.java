package com.example.dropbox.metadata.common;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {
    List<SyncEvent> findByUserIdAndCursorGreaterThanOrderByCursorAsc(UUID userId, Long cursor, Pageable pageable);
    boolean existsByUserIdAndCursorGreaterThan(UUID userId, Long cursor);
}
