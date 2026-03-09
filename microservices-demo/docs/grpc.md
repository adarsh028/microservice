# gRPC (Profile Service ↔ Notification Service)

Profile Service exposes a gRPC API; Notification Service calls it to fetch profile data when processing the `user-created` Kafka event.

---

## Contract: proto definition

**File:** [proto/profile.proto](../proto/profile.proto)

- **Package / Java:** `profile` → `com.demo.grpc.profile`.
- **Service:** `ProfileService`
  - **GetProfile(GetProfileRequest) → GetProfileResponse** – Used by Notification to get display name (and optional fields) for the welcome message.
  - **UpdateProfile(UpdateProfileRequest) → UpdateProfileResponse** – Defined in proto; not used by Notification in the current flow.

**Messages:**

- `GetProfileRequest`: `user_id` (string, UUID).
- `GetProfileResponse`: `user_id`, `name`, `bio`, `avatar_url`, `created_at`, `found` (bool).

---

## Server (Profile Service)

- **Port:** 9090 (separate from HTTP 8082).
- **Implementation:** [ProfileGrpcService](java-reference.md#profile-service) (`profile-service/.../grpc/ProfileGrpcService.java`).
  - Extends `ProfileServiceGrpc.ProfileServiceImplBase`.
  - Annotated with `@GrpcService` (grpc-spring-boot-starter registers it on the gRPC port).
  - **getProfile:** Loads `UserProfile` from `ProfileRepository` by userId; builds `GetProfileResponse` (name, bio, avatarUrl, etc., or `found=false` if missing).
  - **updateProfile:** Updates profile by userId; returns success/failure.

**Config (Profile):** `grpc.server.port: 9090` (in application.yml).

---

## Client (Notification Service)

- **Usage:** Only in the Kafka consumer when handling `user-created`: need the user’s name for the welcome message.
- **Component:** [ProfileServiceClient](java-reference.md#notification-service) (`notification-service/.../grpc/ProfileServiceClient.java`).
  - Injects `ProfileServiceGrpc.ProfileServiceBlockingStub` via `@GrpcClient("profile-service")`.
  - **getProfile(String userId):** Builds `GetProfileRequest`, calls `profileStub.getProfile(request)`, returns `GetProfileResponse`.
  - On exception (e.g. connection failure): logs and returns `GetProfileResponse.newBuilder().setFound(false).build()` so the consumer can fall back to email-based name.

**Config (Notification):** `grpc.client.profile-service.address` (e.g. `static://profile-service:9090`), `negotiation-type: plaintext` (no TLS in demo).

---

## Build and runtime

- **Compilation:** Both Profile and Notification use the same `proto/` (via repo-root or copied proto). Maven plugin `protobuf-maven-plugin` (and optionally `grpc-java`) generates Java classes into `com.demo.grpc.profile`.
- **Runtime:** Profile listens on 9090; Notification resolves `profile-service:9090` on the Docker network and uses a blocking stub so the Kafka consumer thread gets a synchronous response.

---

## Call flow (in this demo)

1. Notification’s Kafka consumer receives `UserCreatedEvent(userId, email, ...)`.
2. Consumer calls `profileServiceClient.getProfile(event.getUserId())`.
3. gRPC request goes to Profile Service (GetProfile).
4. Profile looks up user in `profile_db`, returns name (or empty if not found yet).
5. Notification uses the name (or email fallback) to build the welcome message and saves the notification.

See [flow.md](flow.md) and [kafka-events.md](kafka-events.md) for the full event flow.
