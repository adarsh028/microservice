package com.demo.notification.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Identical JWKS verifier to the one used by the Profile Service.
 *
 * <p>Both Profile and Notification services verify JWTs independently by
 * fetching the JWKS from the Identity Service.  This ensures that no private
 * key material leaves the Identity Service.
 *
 * @see com.demo.profile.security.JwksVerifier (Profile Service – same pattern)
 */
@Slf4j
@Component
public class JwksVerifier {

    @Value("${security.jwks-uri}")
    private String jwksUri;

    @Value("${security.jwks-refresh-interval:300}")
    private long refreshIntervalSeconds;

    private final AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processorRef =
            new AtomicReference<>();

    @PostConstruct
    public void init() {
        refreshKeys();
    }

    @Scheduled(fixedDelayString = "#{${security.jwks-refresh-interval:300} * 1000}")
    public void scheduledRefresh() {
        log.debug("Scheduled JWKS refresh triggered");
        refreshKeys();
    }

    public synchronized void refreshKeys() {
        try {
            log.info("Fetching JWKS from {}", jwksUri);
            JWKSet jwkSet = JWKSet.load(new URL(jwksUri));

            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));

            processorRef.set(processor);
            log.info("JWKS refreshed. Keys loaded: {}", jwkSet.getKeys().size());
        } catch (Exception e) {
            log.error("Failed to refresh JWKS: {}", e.getMessage());
        }
    }

    public JWTClaimsSet verify(String token) throws Exception {
        ConfigurableJWTProcessor<SecurityContext> processor = processorRef.get();
        if (processor == null) {
            throw new IllegalStateException("JWKS not yet loaded");
        }
        try {
            return processor.process(token, null);
        } catch (com.nimbusds.jose.proc.BadJOSEException ex) {
            log.warn("JWT verification failed ({}), refreshing JWKS and retrying…", ex.getMessage());
            refreshKeys();
            return processorRef.get().process(token, null);
        }
    }
}
