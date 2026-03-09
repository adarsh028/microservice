package com.demo.identity.service;

import com.demo.identity.dto.AuthResponse;
import com.demo.identity.dto.LoginRequest;
import com.demo.identity.dto.SignupRequest;
import com.demo.identity.event.UserCreatedEvent;
import com.demo.identity.model.AppUser;
import com.demo.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core authentication business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;
    private final EventPublisher  eventPublisher;

    // ── Signup ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * <ol>
     *   <li>Validates uniqueness of e-mail</li>
     *   <li>Hashes password with BCrypt</li>
     *   <li>Persists {@link AppUser}</li>
     *   <li>Emits {@code user-created} Kafka event</li>
     * </ol>
     */
    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("E-mail already registered: " + req.getEmail());
        }

        AppUser user = AppUser.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .roles("ROLE_USER")
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={} email={}", user.getId(), user.getEmail());

        // Emit Kafka event so Profile and Notification services can react
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .createdAt(Instant.now())
                .build();
        eventPublisher.publishUserCreated(event);

        String token = jwtService.generateToken(
                user.getId().toString(), user.getEmail(), user.getRoles());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .build();
    }

    // ── Login ──────────────────────────────────────────────────────────────

    /**
     * Authenticates an existing user and returns a fresh JWT.
     */
    public AuthResponse login(LoginRequest req) {
        AppUser user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        log.info("User logged in: id={} email={}", user.getId(), user.getEmail());

        String token = jwtService.generateToken(
                user.getId().toString(), user.getEmail(), user.getRoles());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .userId(user.getId().toString())
                .email(user.getEmail())
                .build();
    }
}
