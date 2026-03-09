package com.demo.profile.kafka;

import com.demo.profile.event.UserCreatedEvent;
import com.demo.profile.model.UserProfile;
import com.demo.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kafka consumer that reacts to {@code user-created} events and creates a
 * default profile for each new user.
 *
 * <p>Consumer group: {@code profile-service-group}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final ProfileRepository profileRepository;

    /**
     * Handles a {@code user-created} event.
     *
     * <p>Idempotent: if a profile already exists for the userId the event is
     * silently skipped (handles Kafka at-least-once delivery).
     */
    @KafkaListener(
        topics = "${kafka.topics.user-created}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent: userId={} email={}", event.getUserId(), event.getEmail());

        UUID userId = UUID.fromString(event.getUserId());

        // Idempotency check
        if (profileRepository.existsByUserId(userId)) {
            log.warn("Profile already exists for userId={} – skipping duplicate event", userId);
            return;
        }

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .name(extractNameFromEmail(event.getEmail()))
                .bio("Hello, I am new here!")
                .avatarUrl(generateDefaultAvatar(event.getEmail()))
                .build();

        profileRepository.save(profile);
        log.info("Default profile created for userId={}", userId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Derives a display name from the local part of the email address. */
    private String extractNameFromEmail(String email) {
        if (email == null || !email.contains("@")) return "New User";
        return email.substring(0, email.indexOf('@'));
    }

    /** Returns a Gravatar URL based on the email hash. */
    private String generateDefaultAvatar(String email) {
        return "https://www.gravatar.com/avatar/?d=identicon&s=200";
    }
}
