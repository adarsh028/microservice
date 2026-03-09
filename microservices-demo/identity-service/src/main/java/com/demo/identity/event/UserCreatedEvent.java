package com.demo.identity.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event emitted by the Identity Service on the {@code user-created} topic.
 *
 * <p>Consumers:
 * <ul>
 *   <li>Profile Service – creates a default profile</li>
 *   <li>Notification Service – sends a welcome notification</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {

    /** UUID string of the newly created user. */
    private String userId;

    private String  email;
    private Instant createdAt;
}
