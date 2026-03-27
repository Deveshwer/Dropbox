package com.example.dropbox.metadata.shares;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, UUID> {

    Optional<Share> findByResourceTypeAndResourceIdAndSharedWithUserId(
            String resourceType,
            UUID resourceId,
            UUID sharedWithUserId
    );

    List<Share> findBySharedWithUserIdAndStatus(
            UUID sharedWithUserId,
            String status
    );

    List<Share> findByOwnerIdAndStatus(
            UUID ownerId,
            String status
    );

    List<Share> findByResourceTypeAndResourceIdAndStatus(
            String resourceType,
            UUID resourceId,
            String status
    );

    List<Share> findBySharedWithUserIdAndStatusAndExpiresAtAfterOrExpiresAtIsNull(
            UUID sharedWithUserId,
            String status,
            Instant now
    );
}