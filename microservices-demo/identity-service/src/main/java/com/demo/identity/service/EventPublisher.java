package com.demo.identity.service;

import com.demo.identity.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

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
     */
    public void publishUserCreated(UserCreatedEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(userCreatedTopic, event.getUserId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish UserCreatedEvent for userId={}: {}",
                        event.getUserId(), ex.getMessage());
            } else {
                log.info("UserCreatedEvent published userId={} partition={} offset={}",
                        event.getUserId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
