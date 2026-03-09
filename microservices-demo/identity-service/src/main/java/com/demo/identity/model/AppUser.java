package com.demo.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent representation of a registered user.
 *
 * <p>Only authentication data is stored here; profile data lives in the
 * Profile Service database.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt-hashed password – never stored as plain text. */
    @Column(nullable = false)
    private String password;

    /**
     * Comma-separated list of Spring Security granted authorities,
     * e.g. {@code "ROLE_USER"} or {@code "ROLE_USER,ROLE_ADMIN"}.
     */
    @Column(nullable = false)
    private String roles;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
