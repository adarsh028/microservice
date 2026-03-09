package com.demo.profile.controller;

import com.demo.profile.model.UserProfile;
import com.demo.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for profile HTTP endpoints.
 *
 * <p>All endpoints require a valid JWT (enforced by {@code SecurityConfig}).
 *
 * <pre>
 * GET  /profiles/{userId}  – fetch a profile
 * GET  /profiles/me        – fetch the caller's own profile
 * </pre>
 */
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileRepository profileRepository;

    /**
     * Returns the authenticated user's own profile.
     * The {@code @AuthenticationPrincipal} resolves to the JWT {@code sub} claim
     * (userId string) set by {@link com.demo.profile.security.JwtAuthenticationFilter}.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal String userId) {
        return profileRepository.findByUserId(UUID.fromString(userId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns a specific user's profile by userId.
     * Any authenticated user can read any public profile.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        try {
            return profileRepository.findByUserId(UUID.fromString(userId))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid userId format"));
        }
    }

    /**
     * Updates the authenticated user's own profile.
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal String userId,
                                             @RequestBody Map<String, String> body) {
        return profileRepository.findByUserId(UUID.fromString(userId))
                .map(profile -> {
                    if (body.containsKey("name"))      profile.setName(body.get("name"));
                    if (body.containsKey("bio"))        profile.setBio(body.get("bio"));
                    if (body.containsKey("avatarUrl"))  profile.setAvatarUrl(body.get("avatarUrl"));
                    return ResponseEntity.ok(profileRepository.save(profile));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
