package com.demo.profile.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches the JWKS from the Identity Service, caches the public keys,
 * and provides JWT verification.
 *
 * <h3>Caching strategy</h3>
 * <ul>
 *   <li>Keys are loaded on startup via {@link #refreshKeys()}</li>
 *   <li>A scheduled task refreshes the cache every
 *       {@code security.jwks-refresh-interval} seconds</li>
 *   <li>If verification fails with an unknown key-id the cache is refreshed
 *       immediately (handles key rotation)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwksVerifier {

    @Value("${security.jwks-uri}")
    private String jwksUri;

    @Value("${security.jwks-refresh-interval:300}")
    private long refreshIntervalSeconds;

    /**
     * Holds the currently cached Nimbus JWT processor.
     * AtomicReference enables lock-free swap on refresh.
     */
    private final AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processorRef =
            new AtomicReference<>();

    // ── Initialisation & refresh ───────────────────────────────────────────

    @PostConstruct
    public void init() {
        refreshKeys();
    }

    /**
     * Scheduled JWKS refresh – fires every {@code security.jwks-refresh-interval} seconds.
     * Spring's fixed-delay uses milliseconds so we multiply by 1000.
     */
    @Scheduled(fixedDelayString = "#{${security.jwks-refresh-interval:300} * 1000}")
    public void scheduledRefresh() {
        log.debug("Scheduled JWKS refresh triggered");
        refreshKeys();
    }

    /**
     * Fetches the JWKS endpoint and rebuilds the JWT processor.
     */
    public synchronized void refreshKeys() {
        try {
            log.info("Fetching JWKS from {}", jwksUri);
            JWKSet jwkSet = JWKSet.load(new URL(jwksUri));

            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));

            processorRef.set(processor);
            log.info("JWKS refreshed successfully. Keys loaded: {}", jwkSet.getKeys().size());

        } catch (Exception e) {
            log.error("Failed to refresh JWKS from {}: {}", jwksUri, e.getMessage());
            // Keep the old processor if refresh fails (graceful degradation)
        }
    }

    // ── Verification ───────────────────────────────────────────────────────

    /**
     * Verifies a JWT string and returns its claims.
     *
     * @param token Bearer token (without "Bearer " prefix)
     * @return verified {@link JWTClaimsSet}
     * @throws Exception if the token is invalid, expired, or cannot be verified
     */
    public JWTClaimsSet verify(String token) throws Exception {
        ConfigurableJWTProcessor<SecurityContext> processor = processorRef.get();
        if (processor == null) {
            throw new IllegalStateException("JWKS not yet loaded");
        }
        try {
            return processor.process(token, null);
        } catch (com.nimbusds.jose.proc.BadJOSEException ex) {
            // Possibly a key rotation – refresh and retry once
            log.warn("JWT verification failed ({}), refreshing JWKS and retrying…", ex.getMessage());
            refreshKeys();
            processor = processorRef.get();
            return processor.process(token, null);
        }
    }
}
