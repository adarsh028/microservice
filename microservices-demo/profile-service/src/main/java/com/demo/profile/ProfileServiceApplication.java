package com.demo.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Profile Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Own PostgreSQL database (profile_db)</li>
 *   <li>Kafka consumer – reacts to {@code user-created} events</li>
 *   <li>gRPC server – {@code GetProfile} / {@code UpdateProfile}</li>
 *   <li>JWT verification via JWKS from Identity Service</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}
