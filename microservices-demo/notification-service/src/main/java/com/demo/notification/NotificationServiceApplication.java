package com.demo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Notification Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Own PostgreSQL database (notification_db)</li>
 *   <li>Kafka consumer – reacts to {@code user-created} events</li>
 *   <li>gRPC client – calls Profile Service to enrich notifications</li>
 *   <li>JWT verification via JWKS from Identity Service</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
