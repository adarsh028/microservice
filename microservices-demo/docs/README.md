# Documentation

Detailed documentation for the **microservices-demo** project.

---

## Start here

- **[flow.md](flow.md)** – Business and technical flow: signup → Kafka → profile & notification, plus how JWT/JWKS, gRPC, and Kafka fit in. Best entry point.
- **[java-reference.md](java-reference.md)** – Which Java file does what (per service).

---

## Sub-docs

| Doc | Contents |
|-----|----------|
| [architecture.md](architecture.md) | Services, ports, infrastructure, network, security model. |
| [jwt-jwks.md](jwt-jwks.md) | JWT structure, JWKS endpoint, verification, key lifecycle. |
| [grpc.md](grpc.md) | Proto, Profile gRPC server, Notification gRPC client. |
| [kafka-events.md](kafka-events.md) | Topic, event payload, consumer groups, idempotency. |

---

For quick start, API examples, and running with Docker see the main [../README.md](../README.md).
