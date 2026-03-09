# Java file reference

Which Java file contains which relevant code, per service. Use this with [flow.md](flow.md) to trace business and technical flows.

---

## Identity Service

| File | Role / relevant code |
|------|----------------------|
| **IdentityServiceApplication.java** | Spring Boot entry point. |
| **config/RsaKeyProvider.java** | Loads or generates RSA key-pair (PEM); builds Nimbus `RSAKey` (public only) for JWKS. Used by JwtService and JwksController. |
| **config/SecurityConfig.java** | Permits `/auth/**`, `/.well-known/**`, `/actuator/health`; no JWT required. |
| **config/KafkaTopicConfig.java** | Defines Kafka topic `user-created` (3 partitions, 1 replica). |
| **controller/AuthController.java** | `POST /auth/signup`, `POST /auth/login`; delegates to AuthService. |
| **controller/JwksController.java** | `GET /.well-known/jwks.json`; returns JWKSet from RsaKeyProvider (public key only). |
| **service/AuthService.java** | Signup: validate, hash password, save user, build UserCreatedEvent, call EventPublisher, generate JWT, return. Login: validate credentials, generate JWT. |
| **service/JwtService.java** | Builds JWT claims (sub=userId, email, roles, iss, iat, exp), signs with RSA private key (RsaKeyProvider), returns serialised JWT. |
| **service/EventPublisher.java** | `publishUserCreated(UserCreatedEvent)`: sends event to Kafka topic `user-created` with userId as key; **blocks** on send (`.get()`). |
| **model/AppUser.java** | JPA entity: id, email, password, roles, createdAt. |
| **repository/UserRepository.java** | JPA repository; `existsByEmail`, `findByEmail`. |
| **event/UserCreatedEvent.java** | Kafka payload: userId, email, createdAt (Identity’s copy). |
| **dto/SignupRequest.java**, **LoginRequest.java**, **AuthResponse.java** | Request/response DTOs for auth API. |
| **exception/GlobalExceptionHandler.java** | Centralised exception → HTTP response. |

---

## Profile Service

| File | Role / relevant code |
|------|----------------------|
| **ProfileServiceApplication.java** | Spring Boot entry point. |
| **config/SecurityConfig.java** | Permits `/actuator/health`; all other paths require authentication. Registers JwtAuthenticationFilter. |
| **security/JwksVerifier.java** | Fetches JWKS from Identity (`security.jwks-uri`), builds Nimbus JWT processor for RS256; scheduled refresh; on verify failure refreshes and retries once. |
| **security/JwtAuthenticationFilter.java** | Reads `Authorization: Bearer <token>`, calls JwksVerifier.verify(token), sets SecurityContext with userId (subject) and roles. |
| **controller/ProfileController.java** | `GET /profiles/me`, `PUT /profiles/me`, `GET /profiles/{userId}`; uses `@AuthenticationPrincipal String userId`. |
| **grpc/ProfileGrpcService.java** | gRPC server: GetProfile (load from ProfileRepository, return GetProfileResponse), UpdateProfile (update and return success). |
| **kafka/UserCreatedConsumer.java** | Listens to `user-created`; idempotency by userId; creates default UserProfile (name from email, default bio/avatar), saves to profile_db. |
| **model/UserProfile.java** | JPA entity: id, userId, name, bio, avatarUrl, createdAt, updatedAt. |
| **repository/ProfileRepository.java** | JPA repository; `findByUserId`, `existsByUserId`. |
| **event/UserCreatedEvent.java** | Kafka payload DTO (Profile’s copy for deserialisation). |
| **exception/GlobalExceptionHandler.java** | Centralised exception handling. |

---

## Notification Service

| File | Role / relevant code |
|------|----------------------|
| **NotificationServiceApplication.java** | Spring Boot entry point. |
| **config/SecurityConfig.java** | Permits `/actuator/health`; all other paths require authentication. Registers JwtAuthenticationFilter. |
| **security/JwksVerifier.java** | Same role as Profile: fetch JWKS, verify JWT, scheduled refresh, retry on failure. |
| **security/JwtAuthenticationFilter.java** | Same as Profile: Bearer token → JwksVerifier → SecurityContext (userId, roles). |
| **controller/NotificationController.java** | `GET /notifications/me`; returns notifications for current user (userId from SecurityContext). |
| **grpc/ProfileServiceClient.java** | gRPC client: `getProfile(userId)` calls Profile Service’s GetProfile; on exception returns response with found=false. |
| **kafka/UserCreatedConsumer.java** | Listens to `user-created`; validates event; calls ProfileServiceClient.getProfile; builds welcome message; saves Notification (status SENT); logs and rethrows on error. |
| **model/Notification.java** | JPA entity: id, userId, message, status (enum), createdAt. |
| **repository/NotificationRepository.java** | JPA repository; `findByUserId`, `findByStatus`. |
| **event/UserCreatedEvent.java** | Kafka payload DTO (Notification’s copy for deserialisation). |
| **exception/GlobalExceptionHandler.java** | Centralised exception handling (if used). |

---

## Shared / generated

| Location | Role |
|----------|------|
| **proto/profile.proto** | Defines ProfileService (GetProfile, UpdateProfile) and messages. Generated Java is in `com.demo.grpc.profile` (used by Profile and Notification). |

---

## Quick lookup by concern

| Concern | Where |
|--------|--------|
| JWT creation | Identity: JwtService, RsaKeyProvider. |
| JWKS endpoint | Identity: JwksController, RsaKeyProvider. |
| JWT verification | Profile & Notification: JwksVerifier, JwtAuthenticationFilter. |
| Signup / login API | Identity: AuthController, AuthService. |
| Publish user-created | Identity: EventPublisher, AuthService. |
| Kafka topic creation | Identity: KafkaTopicConfig. |
| Consume user-created (profile) | Profile: UserCreatedConsumer. |
| Consume user-created (notification) | Notification: UserCreatedConsumer. |
| gRPC server (Profile) | Profile: ProfileGrpcService. |
| gRPC client (call Profile) | Notification: ProfileServiceClient. |

For end-to-end flow see [flow.md](flow.md). For architecture, JWT/JWKS, gRPC, and Kafka details see [architecture.md](architecture.md), [jwt-jwks.md](jwt-jwks.md), [grpc.md](grpc.md), and [kafka-events.md](kafka-events.md).
