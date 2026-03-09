# JWT and JWKS

How JWTs are created, how the public key is exposed via JWKS, and how Profile and Notification services verify tokens.

---

## Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **Identity Service** | Owns RSA key-pair; **signs** JWTs; exposes **public key** via JWKS. Does **not** verify JWTs on incoming requests. |
| **Profile Service** | Fetches JWKS; **verifies** JWT on `Authorization: Bearer`; uses `sub` as userId. |
| **Notification Service** | Same as Profile: fetch JWKS, verify JWT, use `sub` as userId. |

---

## JWT structure (issued by Identity)

- **Algorithm:** RS256 (RSA signature).
- **Header:** `alg: RS256`, `kid: <keyId>` (e.g. `demo-key-1`), `typ: JWT`.
- **Payload (claims):**
  - `sub` – userId (UUID string).
  - `email` – user email.
  - `roles` – e.g. `["ROLE_USER"]`.
  - `iss` – issuer (e.g. `identity-service`).
  - `iat`, `exp` – issued at and expiration (seconds).
  - `jti` – unique token id.

**Relevant code:** [JwtService](java-reference.md#identity-service) builds and signs the JWT using [RsaKeyProvider](java-reference.md#identity-service) (private key and keyId).

---

## RSA key lifecycle (Identity)

1. **Startup** – [RsaKeyProvider](java-reference.md#identity-service) (`identity-service/.../config/RsaKeyProvider.java`):
   - If PEM files exist at `jwt.private-key-path` and `jwt.public-key-path`, **load** them.
   - Otherwise **generate** a 2048-bit RSA key-pair and **write** PEM files (e.g. in Docker volume `identity_keys`).
2. **JWK for JWKS** – Builds a Nimbus `RSAKey` from the **public key only** (keyId, use=sig, alg=RS256) and exposes it via the JWKS endpoint.
3. **Signing** – [JwtService](java-reference.md#identity-service) uses the **private key** from `RsaKeyProvider` to sign the JWT (Nimbus `RSASSASigner`).

---

## JWKS endpoint (Identity)

- **URL:** `GET /.well-known/jwks.json`
- **Handler:** [JwksController](java-reference.md#identity-service) (`identity-service/.../controller/JwksController.java`).
- **Behaviour:** Returns a JSON object with a `keys` array containing the **public** RSA key in JWK format (no private material). Used by Profile and Notification to verify JWTs.

Example shape:

```json
{
  "keys": [{
    "kty": "RSA",
    "use": "sig",
    "alg": "RS256",
    "kid": "demo-key-1",
    "n": "...",
    "e": "AQAB"
  }]
}
```

---

## Verification (Profile and Notification)

1. **JWKS loading** – [JwksVerifier](java-reference.md#profile-service) (same pattern in both services):
   - **Startup:** Fetches `security.jwks-uri` (e.g. `http://identity-service:8081/.well-known/jwks.json`) and builds a Nimbus `ConfigurableJWTProcessor` with a key selector for RS256.
   - **Scheduled:** Refreshes JWKS every `security.jwks-refresh-interval` seconds (e.g. 300).
   - **On verification failure:** If verification fails (e.g. unknown key), refreshes JWKS and retries once (supports key rotation).

2. **Per-request** – [JwtAuthenticationFilter](java-reference.md#profile-service):
   - Reads `Authorization: Bearer <token>`.
   - Calls `JwksVerifier.verify(token)` to get `JWTClaimsSet`.
   - Extracts `sub` as userId and `roles` as authorities.
   - Sets `UsernamePasswordAuthenticationToken(userId, null, authorities)` in `SecurityContextHolder`.
   - Controllers use `@AuthenticationPrincipal String userId` to get the current user.

Same filter and verifier pattern exist in **Notification** ([JwksVerifier](java-reference.md#notification-service), [JwtAuthenticationFilter](java-reference.md#notification-service)).

---

## Configuration (relevant keys)

- **Identity:** `jwt.private-key-path`, `jwt.public-key-path`, `jwt.key-id`, `jwt.issuer`, `jwt.expiration-ms`.
- **Profile / Notification:** `security.jwks-uri`, `security.jwks-refresh-interval`.

See [flow.md](flow.md) for where JWKS fits in the overall request flow.
