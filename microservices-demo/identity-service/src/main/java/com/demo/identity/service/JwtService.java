package com.demo.identity.service;

import com.demo.identity.config.RsaKeyProvider;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for creating RSA-signed JWT access tokens.
 *
 * <h3>Token structure</h3>
 * <pre>
 * Header : { "alg": "RS256", "kid": "&lt;keyId&gt;", "typ": "JWT" }
 * Payload: {
 *   "sub"       : "&lt;userId UUID&gt;",
 *   "email"     : "&lt;email&gt;",
 *   "roles"     : ["ROLE_USER"],
 *   "iss"       : "identity-service",
 *   "iat"       : &lt;epoch seconds&gt;,
 *   "exp"       : &lt;epoch seconds&gt;
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final RsaKeyProvider rsaKeyProvider;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.issuer}")
    private String issuer;

    /**
     * Creates and signs a JWT for the given user.
     *
     * @param userId UUID string
     * @param email  user e-mail
     * @param roles  comma-separated role string, e.g. {@code "ROLE_USER"}
     * @return serialised JWT string
     */
    public String generateToken(String userId, String email, String roles) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusMillis(expirationMs);

            // ── Build claims ────────────────────────────────────────────────
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("email", email)
                    .claim("roles", List.of(roles.split(",")))
                    .issuer(issuer)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            // ── Sign with RSA private key ────────────────────────────────────
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKeyProvider.getKeyId())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(rsaKeyProvider.getPrivateKey()));

            String token = signedJWT.serialize();
            log.debug("JWT issued for userId={} exp={}", userId, exp);
            return token;

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
