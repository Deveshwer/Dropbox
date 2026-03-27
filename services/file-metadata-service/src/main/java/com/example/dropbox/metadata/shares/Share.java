  package com.example.dropbox.metadata.shares;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import java.time.Instant;
  import java.util.UUID;
  import lombok.Getter;
  import lombok.Setter;

  @Entity
  @Table(name = "shares")
  @Getter
  @Setter
  public class Share {

      @Id
      private UUID id;

      @Column(name = "resource_type", nullable = false)
      private String resourceType;

      @Column(name = "resource_id", nullable = false)
      private UUID resourceId;

      @Column(name = "owner_id", nullable = false)
      private UUID ownerId;

      @Column(name = "shared_with_user_id", nullable = false)
      private UUID sharedWithUserId;

      @Column(nullable = false)
      private String permission;

      @Column(nullable = false)
      private String status;

      @Column(name = "expires_at")
      private Instant expiresAt;

      @Column(name = "created_at", nullable = false)
      private Instant createdAt;
  }