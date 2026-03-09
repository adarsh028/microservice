package com.demo.identity.controller;

import com.demo.identity.config.RsaKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the JSON Web Key Set (JWKS) so downstream services can verify JWTs
 * without sharing the private key.
 *
 * <p>Endpoint: {@code GET /.well-known/jwks.json}
 *
 * <p>Example response:
 * <pre>
 * {
 *   "keys": [
 *     {
 *       "kty": "RSA",
 *       "use": "sig",
 *       "alg": "RS256",
 *       "kid": "demo-key-1",
 *       "n":   "...",
 *       "e":   "AQAB"
 *     }
 *   ]
 * }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    @GetMapping(value = "/.well-known/jwks.json",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        // Build a JWKSet that contains ONLY the public key (no private material)
        JWKSet publicKeySet = new JWKSet(rsaKeyProvider.getJwkPublicKey());
        return ResponseEntity.ok(publicKeySet.toString());
    }
}
