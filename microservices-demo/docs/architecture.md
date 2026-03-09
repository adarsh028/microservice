# Architecture

High-level architecture of the microservices-demo: services, ports, data stores, and communication patterns.

---

## Services overview

| Service | Port(s) | Role | Database |
|--------|---------|------|----------|
| **Identity Service** | 8081 | Auth (signup/login), JWT issuance, JWKS | identity_db |
| **Profile Service** | 8082 (HTTP), 9090 (gRPC) | User profiles, gRPC server for Profile API | profile_db |
| **Notification Service** | 8083 | In-app notifications (welcome message on signup) | notification_db |

---

## Infrastructure (Docker Compose)

| Component | Port | Role |
|-----------|------|------|
| **PostgreSQL** | 5432 | Single instance; one database per app (identity_db, profile_db, notification_db). |
| **Zookeeper** | 2181 | Used by Kafka. |
| **Kafka** | 9092 (host), 29092 (internal) | Message broker for `user-created` events. |
| **init-db** | — | One-shot: creates the three databases if they don’t exist. |

---

## Network and dependencies

```
                    ┌─────────────────────────────────────────┐
                    │              backend network             │
                    │                                         │
  Client            │  identity-service   profile-service     │
  (host)            │       :8081             :8082 :9090    │
     │              │          │                   ▲    ▲     │
     │  REST        │          │ user-created      │    │     │
     ├──────────────┼──────────┼───────────────────┼────┼─────┤
     │              │          │     Kafka         │    │     │
     │              │          ▼                   │    │     │
     │              │       kafka:29092            │    │     │
     │              │          │                   │    │     │
     │              │          ├───────────────────┼────┘     │
     │              │          │                   │ gRPC     │
     │              │          ▼                   │          │
     │              │  notification-service       │          │
     │              │       :8083 ─────────────────┘          │
     │              │                                         │
     │              │  postgres:5432  (identity_db,           │
     │              │  profile_db, notification_db)           │
     └──────────────┴─────────────────────────────────────────┘
```

- **Client** talks to Identity (8081), Profile (8082), Notification (8083) from the host via published ports.
- **Identity** publishes to Kafka; **Profile** and **Notification** consume from Kafka (each has its own consumer group).
- **Notification** calls **Profile** via gRPC on 9090 (container name `profile-service`).
- **Profile** and **Notification** fetch JWKS from **Identity** (`http://identity-service:8081/.well-known/jwks.json`).
- All apps use **PostgreSQL** on `postgres:5432` with their own database.

---

## Security model

- **Identity:** `/auth/**` and `/.well-known/jwks.json` are public; no JWT required.
- **Profile / Notification:** All API endpoints except `/actuator/health` require `Authorization: Bearer <JWT>`. JWT is verified using JWKS; the **subject (userId)** is used as the current user.

---

## Data isolation

- No shared tables. Each service has its own DB and schema (Flyway migrations).
- Cross-service data:
  - **Kafka:** event payloads (e.g. userId, email).
  - **gRPC:** Profile Service returns profile data to Notification Service.
  - **JWT:** Identity encodes userId (and email, roles) for others to trust.

See [flow.md](flow.md) for the full signup → profile → notification flow and [java-reference.md](java-reference.md) for the code that implements it.
