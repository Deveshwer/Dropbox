package com.example.dropbox.metadata.shares;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<Share> findByResourceTypeAndResourceId(
        String resourceType,
        UUID resourceId
    );
    
    List<Share> findByResourceTypeAndResourceIdAndStatus(
            String resourceType,
            UUID resourceId,
            String status
    );

    @Modifying
    @Query("delete from Share s where s.resourceType = :resourceType and s.resourceId = :resourceId")
    void deleteByResourceTypeAndResourceId(
            @Param("resourceType") String resourceType,
            @Param("resourceId") UUID resourceId
    );

    List<Share> findBySharedWithUserIdAndStatusAndExpiresAtAfterOrExpiresAtIsNull(
            UUID sharedWithUserId,
            String status,
            Instant now
    );
}
