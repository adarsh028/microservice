package com.demo.identity.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Base64;

/**
 * Manages the RSA key-pair used for JWT signing and JWKS publication.
 *
 * <p>On startup the component either:
 * <ol>
 *   <li>Loads existing PEM files from the paths configured in application.yml, or</li>
 *   <li>Generates a fresh 2048-bit RSA key-pair and writes PEM files to disk.</li>
 * </ol>
 *
 * <p>In production you should mount the private-key as a Kubernetes Secret or
 * Docker secret and set {@code jwt.private-key-path} accordingly.
 */
@Slf4j
@Getter
@Component
public class RsaKeyProvider {

    @Value("${jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${jwt.public-key-path}")
    private String publicKeyPath;

    @Value("${jwt.key-id}")
    private String keyId;

    private RSAPrivateKey privateKey;
    private RSAPublicKey  publicKey;
    private RSAKey        jwkPublicKey;   // Nimbus representation (no private material)

    @PostConstruct
    public void init() throws Exception {
        Path privPath = Paths.get(privateKeyPath);
        Path pubPath  = Paths.get(publicKeyPath);

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            log.info("Loading RSA key-pair from disk: {}", privPath.toAbsolutePath());
            privateKey = loadPrivateKey(privPath);
            publicKey  = loadPublicKey(pubPath);
        } else {
            log.warn("RSA key files not found – generating a new key-pair (development only)");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair kp = gen.generateKeyPair();
            privateKey = (RSAPrivateKey) kp.getPrivate();
            publicKey  = (RSAPublicKey)  kp.getPublic();
            persistKeyPair(privPath, pubPath);
        }

        jwkPublicKey = new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        log.info("RSA key-pair ready. Key ID: {}", keyId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private RSAPrivateKey loadPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(Path path) throws Exception {
        String pem = Files.readString(path)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private void persistKeyPair(Path privPath, Path pubPath) throws Exception {
        Files.createDirectories(privPath.getParent() == null ? Paths.get(".") : privPath.getParent());

        // Private key – PKCS#8 DER → PEM
        byte[] privBytes = privateKey.getEncoded();
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privBytes)
                + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(privPath, privPem);

        // Public key – X.509 DER → PEM
        byte[] pubBytes = publicKey.getEncoded();
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pubBytes)
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(pubPath, pubPem);

        log.info("RSA key-pair written to {} and {}", privPath.toAbsolutePath(), pubPath.toAbsolutePath());
    }
}
