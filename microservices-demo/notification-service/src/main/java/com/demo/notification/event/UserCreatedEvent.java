package com.demo.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mirror of the {@code UserCreatedEvent} produced by the Identity Service.
 * Deserialized from Kafka topic {@code user-created}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {

    private String  userId;
    private String  email;
    private Instant createdAt;
}
