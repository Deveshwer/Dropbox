package com.example.dropbox.metadata.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataEventPublisher {

    private final KafkaTemplate<String, MetadataEventMessage> kafkaTemplate;

    @Value("${app.kafka.topics.metadata-events}")
    private String metadataEventsTopic;

    public void publish(MetadataEventMessage event) {
        try {
            kafkaTemplate.send(
                    metadataEventsTopic,
                    event.resourceId().toString(),
                    event
            );

            log.info(
                    "Published metadata event: eventType={}, resourceType={}, resourceId={}",
                    event.eventType(),
                    event.resourceType(),
                    event.resourceId()
            );
        } catch (Exception ex) {
            log.warn(
                    "Failed to publish metadata event: eventType={}, resourceType={}, resourceId={}",
                    event.eventType(),
                    event.resourceType(),
                    event.resourceId(),
                    ex
            );
        }
    }
}

/*
Explanation:

• Yes. Here is what each Kafka part is doing and why.

  ## 1. Dependency

  In pom.xml:

  <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
  </dependency>

  This gives you Spring’s Kafka support:

  - KafkaTemplate
  - producer configuration
  - serializers
  - Kafka listener support later if you add consumers

  For your current step, the main thing you need is:

  KafkaTemplate<K, V>

  That is the object used to publish messages to Kafka.

  ———

  ## 2. Kafka config in application.yml

  spring:
    kafka:
      bootstrap-servers: localhost:9092
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  ### bootstrap-servers

  This tells your app where Kafka is running.

  For local setup:

  localhost:9092

  means:

  - Kafka broker is on your machine
  - listening on port 9092

  ———

  ### key-serializer

  Kafka stores messages as bytes.

  Your producer sends:

  - a key
  - a value

  The key here is:

  event.resourceId().toString()

  That is a string.

  So Kafka needs to convert Java String into bytes.
  That is what StringSerializer does.

  ———

  ### value-serializer

  Your event payload is a Java object:

  MetadataEventMessage

  Kafka cannot send Java objects directly.
  It must serialize them into bytes.

  You chose:

  JsonSerializer

  So Spring converts your Java record into JSON before publishing.

  That is the right choice because:

  - easy to inspect
  - easy for other services to consume
  - language-agnostic

  ———

  ## 3. Topic config under app

  app:
    kafka:
      topics:
        metadata-events: metadata.events

  This is your own app-level config, not Spring’s built-in Kafka config.

  Why do this?
  Instead of hardcoding:

  "metadata.events"

  inside code, you keep topic names configurable.

  That is better because:

  - easier to rename later
  - different envs can use different topic names
  - avoids magic strings in code

  ———

  ## 4. MetadataEventMessage

  public record MetadataEventMessage(
          UUID eventId,
          String eventType,
          String resourceType,
          UUID resourceId,
          UUID actorId,
          String metadata,
          Instant createdAt
  ) {
  }

  This is the Kafka message payload.

  Think of it as the event contract consumers receive.

  ### Why these fields?

  #### eventId

  Unique identifier for this event.
  Useful for:

  - tracing
  - debugging
  - deduplication later

  #### eventType

  Tells consumers what happened.

  Examples:

  - FILE_CREATED
  - FOLDER_SOFT_DELETED
  - SHARE_REVOKED

  This is the most important field.

  #### resourceType

  What kind of thing was affected:

  - FILE
  - FOLDER
  - SHARE

  #### resourceId

  Which exact resource was affected.

  #### actorId

  Which user triggered it.

  #### metadata

  Extra context.
  For example:

  - name=notes.txt
  - name=my-folder,source=emptyTrash

  #### createdAt

  When the event occurred.

  ———

  ## 5. MetadataEventPublisher

  This class is the Kafka producer wrapper.

  ### Constructor dependencies

  private final KafkaTemplate<String, MetadataEventMessage> kafkaTemplate;

  This means:

  - Kafka key type = String
  - Kafka value type = MetadataEventMessage

  Spring creates this bean using your Kafka config.

  ———

  ### Topic injection

  @Value("${app.kafka.topics.metadata-events}")
  private String metadataEventsTopic;

  This reads:

  app.kafka.topics.metadata-events

  from application.yml.

  So your code does not hardcode topic names.

  ———

  ### Publish method

  public void publish(MetadataEventMessage event) {
      try {
          kafkaTemplate.send(
                  metadataEventsTopic,
                  event.resourceId().toString(),
                  event
          );

          log.info(
                  "Published metadata event: eventType={}, resourceType={}, resourceId={}",
                  event.eventType(),
                  event.resourceType(),
                  event.resourceId()
          );
      } catch (Exception ex) {
          log.warn(
                  "Failed to publish metadata event: eventType={}, resourceType={}, resourceId={}",
                  event.eventType(),
                  event.resourceType(),
                  event.resourceId(),
                  ex
          );
      }
  }

  Let’s break that down.

  ———

  ### kafkaTemplate.send(...)

  This is the actual publish call.

  Arguments are:

  #### 1. topic

  metadataEventsTopic

  Example:

  metadata.events

  #### 2. key

  event.resourceId().toString()

  #### 3. value

  event

  which is your MetadataEventMessage

  ———

  ## Why use resourceId as the key?

  Kafka uses the key to decide partition placement.

  If two events have the same key, they usually go to the same partition.

  That matters because Kafka preserves order within a partition.

  So if you publish:

  - FILE_CREATED
  - FILE_SOFT_DELETED
  - FILE_RESTORED

  for the same file, using the same resourceId key helps preserve their order for that resource.

  That is a good first choice.

  ———

  ## Why wrap send in try/catch?

  Because you decided for v1:

  - DB audit save is primary
  - Kafka publish is secondary
  - user request should not fail just because Kafka is temporarily down

  So:

  ### if Kafka works

  - publish event
  - log success

  ### if Kafka fails

  - log warning
  - do not break API request

  That is an explicit design choice.

  ———

  ## 6. Publishing from AuditEventService

  You already have:

  auditEventRepository.save(event);

  Then you add:

  metadataEventPublisher.publish(new MetadataEventMessage(
          event.getId(),
          event.getEventType(),
          event.getResourceType(),
          event.getResourceId(),
          event.getActorId(),
          event.getMetadata(),
          event.getCreatedAt()
  ));

  ### Why publish here?

  Because AuditEventService.recordEvent(...) is already your central event creation point.

  That means every place that records an audit event automatically also publishes a Kafka event.

  This is good because:

  - no duplication across FileService, FolderService, ShareService
  - one place for event publication logic
  - easy to evolve later

  ———

  ## Flow end to end

  Suppose user soft deletes a file.

  ### In FileService.deleteFile(...)

  you do:

  1. mark file deleted
  2. save file
  3. call:

  auditEventService.recordEvent(...)

  ### Then in AuditEventService.recordEvent(...)

  you do:

  1. build AuditEvent
  2. save to DB
  3. build MetadataEventMessage
  4. publish to Kafka

  ### Result

  You now have:

  - durable DB audit history
  - async Kafka event stream

  Both from one action.

  ———

  ## Why this helps your architecture

  Now future services do not need direct integration with metadata-service.

  Example:

  ### Notification service

  Consumes:

  - SHARE_CREATED_OR_UPDATED
  - SHARE_REVOKED

  ### Search indexing service

  Consumes:

  - FILE_CREATED
  - FILE_RESTORED
  - FILE_PERMANENTLY_DELETED

  ### Storage cleanup service

  Consumes:

  - FILE_PERMANENTLY_DELETED

  So metadata-service just says:

  > “This happened.”

  Kafka lets other services react independently.

  ———

  ## Main limitation of this design

  This approach is simple, but not perfectly reliable.

  Possible scenario:

  1. DB save succeeds
  2. Kafka publish fails

  Then:

  - audit row exists
  - Kafka event is missing

  That is why in stronger systems people use the outbox pattern.

  But for your current stage, this direct publish approach is reasonable and much simpler.

  ———

  ## Short summary

  The Kafka code is doing this:

  1. define a JSON event payload (MetadataEventMessage)
  2. configure Kafka producer in Spring
  3. create a small publisher service (MetadataEventPublisher)
  4. publish every audit event to topic metadata.events
  5. key events by resourceId for per-resource ordering
  6. do not fail user requests if Kafka publish fails

  If you want, next I can explain specifically:

  - why KafkaTemplate<String, MetadataEventMessage> looks the way it does
  - or how the JSON will actually look on the topic.

*/