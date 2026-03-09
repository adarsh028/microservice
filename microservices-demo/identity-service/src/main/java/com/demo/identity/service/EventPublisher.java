package com.demo.identity.service;

import com.demo.identity.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Publishes domain events to Kafka topics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.user-created}")
    private String userCreatedTopic;

    /**
     * Emits a {@link UserCreatedEvent} on the {@code user-created} topic.
     * The userId is used as the Kafka message key to guarantee ordering
     * for a given user across partitions.
     *
     * <p>Blocks until the send completes so that signup only succeeds when
     * the event is in Kafka (Profile and Notification services can then consume it).
     */
    public void publishUserCreated(UserCreatedEvent event) {
        try {
            SendResult<String, Object> result =
                    kafkaTemplate.send(userCreatedTopic, event.getUserId(), event).get();
            log.info("UserCreatedEvent published userId={} partition={} offset={}",
                    event.getUserId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception ex) {
            log.error("Failed to publish UserCreatedEvent for userId={}: {}",
                    event.getUserId(), ex.getMessage());
            throw new RuntimeException("Failed to publish user-created event; check Kafka connectivity", ex);
        }
    }
}
