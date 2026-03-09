package com.demo.profile.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mirror of the {@code UserCreatedEvent} produced by the Identity Service.
 *
 * <p>Deserialized from the Kafka {@code user-created} topic.
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
