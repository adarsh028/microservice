package com.demo.notification.controller;

import com.demo.notification.model.Notification;
import com.demo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for notification HTTP endpoints.
 *
 * <pre>
 * GET /notifications/me  – list all notifications for the authenticated user
 * </pre>
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * Returns all notifications for the authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<List<Notification>> getMyNotifications(
            @AuthenticationPrincipal String userId) {
        List<Notification> notifications =
                notificationRepository.findByUserId(UUID.fromString(userId));
        return ResponseEntity.ok(notifications);
    }
}
