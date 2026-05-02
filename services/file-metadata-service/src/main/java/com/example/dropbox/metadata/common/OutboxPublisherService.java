package com.example.dropbox.metadata.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final MetadataEventPublisher metadataEventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishUnpublishedEvents() {
        List<OutboxEvent> unpublishedEvents = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent outboxEvent : unpublishedEvents) {
            try {
                MetadataEventMessage eventMessage =
                        objectMapper.readValue(outboxEvent.getPayload(), MetadataEventMessage.class);

                metadataEventPublisher.publish(eventMessage);

                outboxEvent.setPublishedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);
            } catch (JsonProcessingException ex) {
                log.error(
                        "Failed to deserialize outbox event payload: outboxEventId={}",
                        outboxEvent.getId(),
                        ex
                );
            } catch (Exception ex) {
                log.warn(
                        "Failed to publish outbox event: outboxEventId={}",
                        outboxEvent.getId(),
                        ex
                );
            }
        }
    }
}