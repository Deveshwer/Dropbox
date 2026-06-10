package com.example.dropbox.metadata.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {
    List<SyncEvent> findByCursorGreaterThanOrderByCursorAsc(Long cursor, Pageable pageable);
    boolean existsByCursorGreaterThan(Long cursor);
}