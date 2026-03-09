# Project Flow – Business and Technical

This document describes the **microservices-demo** project in detail: business flows, technical flows, and how each part fits together. For file-level reference see [java-reference.md](java-reference.md). For deeper dives see the sub-docs linked below.

---

## Table of contents

1. [Business flow](#1-business-flow)
2. [Technical flow (signup → profile → notification)](#2-technical-flow-signup--profile--notification)
3. [Authentication and JWKS](#3-authentication-and-jwks)
4. [gRPC (Profile ↔ Notification)](#4-grpc-profile--notification)
5. [Kafka (user-created event)](#5-kafka-user-created-event)
6. [Document index](#6-document-index)

---

## 1. Business flow

### What the system does

- **Identity Service** – Users sign up and log in. The system issues a **JWT** so the user can call other services.
- **Profile Service** – Each user has a **profile** (name, bio, avatar). It is created when the user signs up and can be updated later. It is also used to personalise notifications.
- **Notification Service** – When a user is created, a **welcome notification** is stored and the user can list their notifications via the API. Notifications are **in-app only** (no email/SMS).

### Main user journey

```
1. User signs up (POST /auth/signup)
   → Identity creates the user and returns a JWT.

2. In the background (no user action):
   → Identity publishes a "user-created" event to Kafka.
   → Profile Service consumes it and creates a default profile.
   → Notification Service consumes it, fetches the profile via gRPC, builds a welcome message, and saves a notification.

3. User calls GET /profiles/me and GET /notifications/me with the JWT
   → They see their profile and their welcome notification.
```

So the **business flow** is: **Sign up → (async) profile creation + welcome notification → user reads profile and notifications**.

---

## 2. Technical flow (signup → profile → notification)

### Step-by-step

| Step | Who | What happens | Relevant code |
|------|-----|--------------|---------------|
| 1 | Client | `POST /auth/signup` with email + password | [AuthController](java-reference.md#identity-service) |
| 2 | Identity | Validate, hash password, save user in `identity_db` | [AuthService](java-reference.md#identity-service), [UserRepository](java-reference.md#identity-service) |
| 3 | Identity | Build `UserCreatedEvent` (userId, email, createdAt), publish to Kafka topic `user-created` (blocks until send succeeds) | [EventPublisher](java-reference.md#identity-service), [AuthService](java-reference.md#identity-service) |
| 4 | Identity | Generate JWT (signed with RSA private key), return 201 + token | [JwtService](java-reference.md#identity-service) |
| 5 | Profile | Kafka consumer receives event; idempotency check; create default profile in `profile_db` | [UserCreatedConsumer](java-reference.md#profile-service) (Kafka) |
| 6 | Notification | Kafka consumer receives same event; call Profile Service via **gRPC** to get profile (name); build welcome message; save notification in `notification_db` | [UserCreatedConsumer](java-reference.md#notification-service) (Kafka), [ProfileServiceClient](java-reference.md#notification-service) (gRPC) |
| 7 | Client | `GET /profiles/me` and `GET /notifications/me` with `Authorization: Bearer <JWT>` | [ProfileController](java-reference.md#profile-service), [NotificationController](java-reference.md#notification-service) |
| 8 | Profile / Notification | Extract Bearer token, verify JWT using **JWKS** from Identity, set `userId` in SecurityContext; return data for that user | [JwtAuthenticationFilter](java-reference.md#profile-service), [JwksVerifier](java-reference.md#profile-service) (same pattern in Notification) |

### Data flow diagram

```
                    Client
                      │
    POST /auth/signup │
                      ▼
              ┌───────────────┐
              │   Identity    │  save user → identity_db
              │   Service     │  publish user-created → Kafka
              │   (8081)      │  return JWT
              └───────┬───────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
         ▼            ▼            │
   user-created   user-created     │
   (Kafka)        (Kafka)          │
         │            │            │
         ▼            ▼            │
  ┌─────────────┐  ┌─────────────────────┐
  │  Profile    │  │  Notification       │
  │  Service    │  │  Service            │
  │  (8082)     │  │  (8083)             │
  │  profile_db │  │  gRPC ─────────────►│  Profile (GetProfile)
  └─────────────┘  │  notification_db    │
                   └─────────────────────┘
         │                    │
         │  GET /profiles/me  │  GET /notifications/me
         │  Bearer JWT       │  Bearer JWT
         ▼                    ▼
              Client (uses JWT for both)
```

---

## 3. Authentication and JWKS

### Roles

- **Identity Service** – Only service that **issues** JWTs. It holds the **RSA private key** and signs tokens. It **does not** validate JWTs for incoming requests (signup/login and JWKS are public).
- **Profile and Notification** – They **never** see the private key. They **verify** JWTs using the **public key** obtained from Identity’s **JWKS** endpoint.

### JWKS flow

1. Identity exposes **public key** at `GET /.well-known/jwks.json` (see [JwksController](java-reference.md#identity-service), [RsaKeyProvider](java-reference.md#identity-service)).
2. Profile and Notification load this URL at startup and on a schedule; they build a **JWT verifier** from the JWKS (see [JwksVerifier](java-reference.md#profile-service)).
3. For each request with `Authorization: Bearer <token>`, a filter parses the token and calls the verifier; on success it sets the **subject (userId)** and roles in Spring Security context (see [JwtAuthenticationFilter](java-reference.md#profile-service)).

Detailed flow, key formats, and rotation: **[docs/jwt-jwks.md](jwt-jwks.md)**.

---

## 4. gRPC (Profile ↔ Notification)

### Purpose

Notification Service needs the user’s **display name** (and optionally other profile fields) to build the welcome message. It gets this by calling Profile Service over **gRPC** (no REST, no shared DB).

### Contract

- **Proto:** [proto/profile.proto](../proto/profile.proto) defines `ProfileService` with:
  - `GetProfile(GetProfileRequest) → GetProfileResponse`
  - `UpdateProfile(...) → UpdateProfileResponse`
- **Server:** Profile Service implements this on **port 9090** (see [ProfileGrpcService](java-reference.md#profile-service)).
- **Client:** Notification Service uses a **blocking stub** to call `GetProfile` from the Kafka consumer (see [ProfileServiceClient](java-reference.md#notification-service)).

### When it’s used

Only in the **Notification** Kafka consumer: when a `user-created` event is received, Notification calls `GetProfile(userId)`; if the profile exists it uses the name, otherwise it falls back to the email local part.

Details and config: **[docs/grpc.md](grpc.md)**.

---

## 5. Kafka (user-created event)

### Topic and producer

- **Topic:** `user-created` (created by Identity with 3 partitions via [KafkaTopicConfig](java-reference.md#identity-service)).
- **Producer:** Identity’s [EventPublisher](java-reference.md#identity-service) sends a **UserCreatedEvent** (userId, email, createdAt) with the userId as the Kafka key. Signup **waits** for the send to succeed (blocking).

### Consumers (two independent groups)

| Consumer group              | Service    | Action |
|----------------------------|------------|--------|
| `profile-service-group`    | Profile    | Create default profile in `profile_db` (idempotent by userId). |
| `notification-service-group` | Notification | Call Profile via gRPC, build welcome message, save notification in `notification_db`. |

Event payload and Java classes: **[docs/kafka-events.md](kafka-events.md)**.

---

## 6. Document index

| Document | Contents |
|----------|----------|
| **[flow.md](flow.md)** (this file) | End-to-end business and technical flow; where JWKS, gRPC, and Kafka fit in. |
| **[architecture.md](architecture.md)** | High-level architecture, services, ports, and infrastructure. |
| **[jwt-jwks.md](jwt-jwks.md)** | JWT structure, JWKS endpoint, verification, and key handling. |
| **[grpc.md](grpc.md)** | Proto definition, server (Profile), client (Notification), and configuration. |
| **[kafka-events.md](kafka-events.md)** | Topic, event payload, consumer groups, and idempotency. |
| **[java-reference.md](java-reference.md)** | Which Java file contains which relevant code (per service). |
