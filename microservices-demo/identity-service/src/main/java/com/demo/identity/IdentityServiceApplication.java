package com.demo.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Identity Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>User signup / login</li>
 *   <li>RSA-signed JWT issuance</li>
 *   <li>JWKS endpoint for downstream services</li>
 *   <li>Kafka event emission on user-created</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
