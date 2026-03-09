package com.demo.notification.kafka;

import com.demo.grpc.profile.GetProfileResponse;
import com.demo.notification.event.UserCreatedEvent;
import com.demo.notification.grpc.ProfileServiceClient;
import com.demo.notification.model.Notification;
import com.demo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kafka consumer that handles {@code user-created} events.
 *
 * <h3>Processing flow</h3>
 * <ol>
 *   <li>Receive Kafka event from {@code user-created} topic</li>
 *   <li>Call Profile Service via gRPC to fetch profile details</li>
 *   <li>Build a personalised welcome message</li>
 *   <li>Persist notification record in the local database</li>
 * </ol>
 *
 * <p>In a production system the {@link Notification.NotificationStatus#SENT} step
 * would involve calling an email/SMS provider; here we simply mark it {@code SENT}
 * immediately after persisting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final NotificationRepository notificationRepository;
    private final ProfileServiceClient   profileServiceClient;

    @KafkaListener(
        topics = "${kafka.topics.user-created}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onUserCreated(UserCreatedEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            log.error("Invalid UserCreatedEvent: event or userId is null/blank; skipping");
            throw new IllegalArgumentException("UserCreatedEvent must have non-blank userId");
        }
        log.info("Received UserCreatedEvent: userId={} email={}", event.getUserId(), event.getEmail());

        try {
            // ── Step 1: Fetch profile via gRPC ─────────────────────────────────
            GetProfileResponse profileResponse = profileServiceClient.getProfile(event.getUserId());

            // ── Step 2: Build notification message ────────────────────────────
            String displayName = resolveDisplayName(profileResponse, event.getEmail());
            String message     = buildWelcomeMessage(displayName);

            // ── Step 3: Persist notification ──────────────────────────────────
            Notification notification = Notification.builder()
                    .userId(UUID.fromString(event.getUserId()))
                    .message(message)
                    .status(Notification.NotificationStatus.SENT)   // In production: PENDING → send → SENT
                    .build();

            notificationRepository.save(notification);
            log.info("Welcome notification created for userId={} (notificationId={})",
                    event.getUserId(), notification.getId());
        } catch (Exception e) {
            log.error("Failed to process UserCreatedEvent userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
            throw e;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String resolveDisplayName(GetProfileResponse profile, String email) {
        if (profile != null && profile.getFound() && profile.getName() != null && !profile.getName().isBlank()) {
            return profile.getName();
        }
        // Fall back to the local part of the email address
        return email != null && email.contains("@")
                ? email.substring(0, email.indexOf('@'))
                : "User";
    }

    private String buildWelcomeMessage(String name) {
        return String.format(
            "Welcome to the platform, %s! 🎉  " +
            "Your account has been created successfully. " +
            "Explore your profile and start connecting with others.",
            name
        );
    }
}
