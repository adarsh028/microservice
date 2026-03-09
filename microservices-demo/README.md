# Microservices Demo

A complete, production-like demonstration of a **Java / Spring Boot** event-driven
microservices architecture using:

| Concern | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Authentication | Spring Security + JWT (RSA RS256) |
| Key distribution | JWKS (JSON Web Key Set) |
| Async messaging | Apache Kafka |
| Synchronous RPC | gRPC + Protocol Buffers |
| Database | PostgreSQL (one per service) |
| Schema migration | Flyway |
| Containers | Docker + Docker Compose |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          Client (curl / browser)                │
└──────────┬────────────────────────────┬────────────────────────┘
           │ REST                       │ REST (JWT)
           ▼                            ▼
┌─────────────────────┐    ┌──────────────────────┐   ┌───────────────────────┐
│  Identity Service   │    │   Profile Service     │   │ Notification Service  │
│  (port 8081)        │    │   (port 8082 / 9090)  │   │  (port 8083)          │
│                     │    │                       │   │                       │
│  POST /auth/signup  │    │  GET  /profiles/me    │   │  GET /notifications/me│
│  POST /auth/login   │    │  PUT  /profiles/me    │   │                       │
│  GET  /.well-known/ │    │  GET  /profiles/{id}  │   │                       │
│       jwks.json     │    │                       │   │                       │
│                     │    │  gRPC server :9090    │◄──┤  gRPC client          │
│  identity_db        │    │  profile_db           │   │  notification_db      │
└────────┬────────────┘    └──────────┬────────────┘   └──────────┬────────────┘
         │                            │                            │
         │     Kafka topic: user-created                          │
         └────────────────────────────┴────────────────────────────┘
                                Kafka (port 9092)
```

### Event flow

```
Client ──POST /auth/signup──► Identity Service
                                     │
                              save user to identity_db
                                     │
                              emit  user-created  event
                                     │
                  ┌──────────────────┴──────────────────┐
                  ▼                                       ▼
          Profile Service                      Notification Service
      creates default profile             calls Profile Service via gRPC
      in profile_db                       builds welcome message
                                          saves to notification_db
```

---

## Project Structure

```
microservices-demo/
├── docker-compose.yml
├── proto/
│   └── profile.proto                    ← shared Protobuf definition
│
├── identity-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/identity/
│       │   ├── IdentityServiceApplication.java
│       │   ├── config/
│       │   │   ├── RsaKeyProvider.java  ← loads/generates RSA key-pair
│       │   │   ├── SecurityConfig.java
│       │   │   └── KafkaTopicConfig.java
│       │   ├── controller/
│       │   │   ├── AuthController.java  ← POST /auth/signup, /auth/login
│       │   │   └── JwksController.java  ← GET /.well-known/jwks.json
│       │   ├── service/
│       │   │   ├── AuthService.java
│       │   │   ├── JwtService.java      ← RSA-signed JWT creation
│       │   │   └── EventPublisher.java  ← Kafka producer
│       │   ├── model/AppUser.java
│       │   ├── repository/UserRepository.java
│       │   ├── dto/
│       │   ├── event/UserCreatedEvent.java
│       │   └── exception/GlobalExceptionHandler.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__init.sql
│
├── profile-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/profile/
│       │   ├── ProfileServiceApplication.java
│       │   ├── config/SecurityConfig.java
│       │   ├── security/
│       │   │   ├── JwksVerifier.java         ← fetches + caches JWKS
│       │   │   └── JwtAuthenticationFilter.java
│       │   ├── grpc/ProfileGrpcService.java  ← gRPC server
│       │   ├── kafka/UserCreatedConsumer.java ← Kafka consumer
│       │   ├── controller/ProfileController.java
│       │   ├── model/UserProfile.java
│       │   ├── repository/ProfileRepository.java
│       │   ├── event/UserCreatedEvent.java
│       │   └── exception/GlobalExceptionHandler.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__init.sql
│
└── notification-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── java/com/demo/notification/
        │   ├── NotificationServiceApplication.java
        │   ├── config/SecurityConfig.java
        │   ├── security/
        │   │   ├── JwksVerifier.java
        │   │   └── JwtAuthenticationFilter.java
        │   ├── grpc/ProfileServiceClient.java  ← gRPC client
        │   ├── kafka/UserCreatedConsumer.java   ← Kafka consumer + gRPC call
        │   ├── controller/NotificationController.java
        │   ├── model/Notification.java
        │   ├── repository/NotificationRepository.java
        │   └── event/UserCreatedEvent.java
        └── resources/
            ├── application.yml
            └── db/migration/V1__init.sql
```

---

## Quick Start

### Prerequisites

- Docker Desktop ≥ 24
- Docker Compose v2
- (Optional) `curl`, `jq`

### 1. Build and start everything

```bash
cd microservices-demo

# Build all images and start infrastructure + services
docker compose up --build
```

This command:
1. Builds all three service images
2. Starts Zookeeper, Kafka, and PostgreSQL
3. Creates the three databases (`identity_db`, `profile_db`, `notification_db`)
4. Starts the three microservices

> **First boot note:** The Identity Service generates a fresh RSA key-pair and
> stores the PEM files in the `identity_keys` Docker volume.  The Profile and
> Notification services fetch the JWKS from `http://identity-service:8081/.well-known/jwks.json`
> on startup and cache the public key.

### 2. Verify services are healthy

After starting the stack, wait 30–45 seconds for all services to pass their healthchecks, then run:

#### Check container status

```bash
docker compose ps
```

All application services should show `(healthy)` in the STATUS column:

- `identity-service` – Up (healthy)
- `profile-service` – Up (healthy)
- `notification-service` – Up (healthy)

Infrastructure (postgres, zookeeper, kafka) should also be healthy. The `init-db` container will show `Exited (0)` after creating the databases.

#### Verify health endpoints (Actuator)

Each Java service exposes Spring Boot Actuator’s health endpoint at `/actuator/health`. You can verify them with:

```bash
# Identity Service (port 8081)
curl -s http://localhost:8081/actuator/health | jq .

# Profile Service (port 8082)
curl -s http://localhost:8082/actuator/health | jq .

# Notification Service (port 8083)
curl -s http://localhost:8083/actuator/health | jq .
```

Expected response (each service returns `"status":"UP"` and may include components such as `db`, `diskSpace`, `ping`; notification-service also includes `grpcChannel`):

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL", ... } },
    "diskSpace": { "status": "UP", ... },
    "ping": { "status": "UP" }
  }
}
```

If any service is not healthy, check logs with:

```bash
docker compose logs <service-name>
```

For a clean run from scratch:

```bash
docker compose down && docker compose up -d --build
```

Then wait ~30–45 seconds before running the verification steps above.

---

## API Reference & curl Examples

### Identity Service (port 8081)

#### Sign up a new user

```bash
curl -s -X POST http://localhost:8081/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}' | jq .
```

**Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImRlbW8ta2V5LTEiLCJ0eXAiOiJKV1QifQ...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alice@example.com"
}
```

> **Kafka side-effect:** A `user-created` event is emitted.
> Profile Service and Notification Service consume it and create records.

---

#### Log in

```bash
curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}' | jq .
```

---

#### Fetch the JWKS (public keys)

```bash
curl -s http://localhost:8081/.well-known/jwks.json | jq .
```

**Response:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "demo-key-1",
      "n": "0vx7agoebGcQSuuPiLJXZptN9...",
      "e": "AQAB"
    }
  ]
}
```

---

### Profile Service (port 8082)

> All requests require `Authorization: Bearer <token>`.
> Save your token first:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}' | jq -r .accessToken)
```

#### Get my profile

```bash
curl -s http://localhost:8082/profiles/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Response (200 OK):**
```json
{
  "id": "...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "alice",
  "bio": "Hello, I am new here!",
  "avatarUrl": "https://www.gravatar.com/avatar/?d=identicon&s=200",
  "createdAt": "2026-03-09T10:00:00Z",
  "updatedAt": "2026-03-09T10:00:00Z"
}
```

#### Update my profile

```bash
curl -s -X PUT http://localhost:8082/profiles/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Smith","bio":"Spring Boot enthusiast","avatarUrl":"https://example.com/alice.png"}' \
  | jq .
```

#### Get a profile by userId

```bash
USER_ID="550e8400-e29b-41d4-a716-446655440000"
curl -s "http://localhost:8082/profiles/${USER_ID}" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Notification Service (port 8083)

Notifications are **in-app only**: they are persisted in `notification_db` and returned via the API. There is no outbound delivery (no email, SMS, or push). When a user is created, the service builds a welcome message, stores it with status `SENT`, and the user retrieves it with `GET /notifications/me`. In production you would add a step to call an email/SMS provider before marking as `SENT`.

#### Get my notifications

```bash
curl -s http://localhost:8083/notifications/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Response (200 OK):**
```json
[
  {
    "id": "...",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "message": "Welcome to the platform, alice! 🎉 Your account has been created successfully...",
    "status": "SENT",
    "createdAt": "2026-03-09T10:00:01Z"
  }
]
```

---

## End-to-End Demo Script

Run the complete signup → verify profile → check notification flow:

```bash
#!/bin/bash
set -e

BASE_IDENTITY=http://localhost:8081
BASE_PROFILE=http://localhost:8082
BASE_NOTIFY=http://localhost:8083
EMAIL="demo_$(date +%s)@example.com"
PASSWORD="password123"

echo "=== 1. Sign up ==="
SIGNUP=$(curl -s -X POST $BASE_IDENTITY/auth/signup \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
echo $SIGNUP | jq .
TOKEN=$(echo $SIGNUP | jq -r .accessToken)

echo ""
echo "=== 2. Fetch JWKS (public key) ==="
curl -s $BASE_IDENTITY/.well-known/jwks.json | jq '{kid: .keys[0].kid, alg: .keys[0].alg}'

echo ""
echo "=== 3. Get my profile (JWT verified by Profile Service via JWKS) ==="
sleep 2   # allow Kafka consumer to process
curl -s $BASE_PROFILE/profiles/me \
  -H "Authorization: Bearer $TOKEN" | jq .

echo ""
echo "=== 4. Get my notifications (welcome message built using gRPC call to Profile Service) ==="
curl -s $BASE_NOTIFY/notifications/me \
  -H "Authorization: Bearer $TOKEN" | jq .

echo ""
echo "=== Done! ==="
```

---

## Key Design Decisions

### JWT & JWKS

```
Identity Service                    Profile / Notification Service
──────────────────                  ──────────────────────────────
Generates RSA key-pair              GET /.well-known/jwks.json
Signs JWT with private key          Extract public key by kid
Publishes public key at /jwks.json  Verify JWT signature
                                    Check exp, iss claims
```

- The **private key never leaves** the Identity Service.
- Downstream services cache the JWKS and refresh every 5 minutes.
- On key-rotation, a failed verification triggers an immediate cache refresh.

### Kafka (At-Least-Once Delivery)

- Producer: `KafkaTemplate` with async callbacks for error logging.
- Consumer: Both Profile and Notification services use **idempotency checks**
  (e.g., `existsByUserId`) to safely handle duplicate events.

### gRPC

- The shared `profile.proto` in `/proto` is compiled by both Profile Service
  (server) and Notification Service (client) using the `protobuf-maven-plugin`.
- gRPC runs on a dedicated port (9090), completely separate from the HTTP API.
- The Notification Service uses a **blocking stub** inside the Kafka consumer thread.

### Database Isolation

Each service has its **own PostgreSQL database** (`identity_db`, `profile_db`,
`notification_db`). There are no cross-service JOINs or shared tables.
Cross-service data access happens exclusively via gRPC or Kafka events.

---

## Troubleshooting

### `GET /notifications/me` returns empty `[]`

The welcome notification is created when a **new user signs up**: Identity publishes a `user-created` event to Kafka, and Notification Service consumes it and persists a row in `notification_db`. If you see no notifications:

1. **Create the user after the stack is fully up** – Run `make up` (or `docker compose up -d`), wait ~30–45s until all services are healthy, then sign up via `POST /auth/signup`. Signup now blocks until the event is published to Kafka.
2. **Check Docker logs** to see where the flow stops:
   - **Identity:** `docker compose logs identity-service` (or `make logs SVC=identity-service`). Look for `UserCreatedEvent published userId=... partition=... offset=...`. If you see `Failed to publish UserCreatedEvent` or a Kafka exception, Identity cannot reach Kafka.
   - **Notification:** `docker compose logs notification-service`. Look for `Received UserCreatedEvent: userId=...` then `Welcome notification created for userId=...`. If you see `Received` but not `Welcome notification created`, look for `Failed to process UserCreatedEvent` and the exception (e.g. gRPC or DB error).
3. **Confirm databases** – `make db-check` verifies that `notification_db` exists. The table is created by Flyway on first run.

### Signup fails with "Failed to publish user-created event"

Kafka is unreachable from Identity Service. Ensure Kafka and Zookeeper are healthy (`docker compose ps`), and that the Identity container uses `KAFKA_BOOTSTRAP=kafka:29092` (set in `docker-compose.yml`).

---

## Stopping the Stack

```bash
docker compose down

# Remove volumes too (wipes databases and RSA keys)
docker compose down -v
```

---

## Further Improvements (Production Checklist)

- [ ] Replace self-generated RSA keys with **Vault / AWS KMS / Kubernetes Secrets**
- [ ] Add **refresh tokens** with a Redis token store
- [ ] Enable **Kafka SSL/SASL** authentication
- [ ] Enable **TLS on gRPC** (mutual TLS for internal service comms)
- [ ] Add **Prometheus metrics** to Spring Boot Actuator (health is already exposed)
- [ ] Implement **circuit breaker** (Resilience4j) around gRPC calls
- [ ] Replace single Kafka broker with a **3-broker cluster** (replication-factor=3)
- [ ] Add **dead-letter topics** for failed Kafka messages
- [ ] Implement **distributed tracing** (OpenTelemetry + Jaeger)
- [ ] Add **API Gateway** (Spring Cloud Gateway) as the single entry point
