package com.demo.identity.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 *
 * <p>Spring Kafka auto-creates the topic when the broker is reachable
 * and the topic does not yet exist.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.user-created}")
    private String userCreatedTopic;

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name(userCreatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
