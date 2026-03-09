# Kafka events

How the `user-created` topic is used: producer, payload, consumer groups, and idempotency.

---

## Topic

- **Name:** `user-created` (config key `kafka.topics.user-created` in each service).
- **Creation:** Identity Service creates it via [KafkaTopicConfig](java-reference.md#identity-service) (TopicBuilder): 3 partitions, 1 replica.
- **Producer:** Identity Service only.
- **Consumers:** Profile Service and Notification Service (separate consumer groups).

---

## Event payload: UserCreatedEvent

Same logical payload in all three services (each has its own class in its own package for serialisation):

| Field     | Type   | Description |
|----------|--------|-------------|
| userId   | String | UUID of the new user. |
| email    | String | User email. |
| createdAt| Instant| When the event was created. |

**Producer (Identity):** [UserCreatedEvent](java-reference.md#identity-service) in `com.demo.identity.event`, built in [AuthService](java-reference.md#identity-service) and sent by [EventPublisher](java-reference.md#identity-service) with **userId as Kafka message key** (for ordering per user).

**Consumers:** Profile uses [UserCreatedEvent](java-reference.md#profile-service) in `com.demo.profile.event`; Notification uses [UserCreatedEvent](java-reference.md#notification-service) in `com.demo.notification.event`. Both are JSON-deserialised from the same topic (no type headers; default type set in Spring Kafka config).

---

## Producer (Identity)

- **Class:** [EventPublisher](java-reference.md#identity-service) (`identity-service/.../service/EventPublisher.java`).
- **When:** Called from [AuthService](java-reference.md#identity-service) right after saving the user and before returning the signup response. **Blocking:** `kafkaTemplate.send(...).get()` so signup fails if Kafka is unreachable.
- **Serialisation:** Spring Kafka `JsonSerializer`; no type headers (`spring.json.add.type.headers: false`).

---

## Consumer: Profile Service

- **Group:** `profile-service-group` (config: `spring.kafka.consumer.group-id`).
- **Class:** [UserCreatedConsumer](java-reference.md#profile-service) (`profile-service/.../kafka/UserCreatedConsumer.java`).
- **Behaviour:**
  - Idempotency: if a profile already exists for the userId, skip.
  - Otherwise create a default [UserProfile](java-reference.md#profile-service): name from email local part, default bio, default avatar URL; save to `profile_db`.

---

## Consumer: Notification Service

- **Group:** `notification-service-group`.
- **Class:** [UserCreatedConsumer](java-reference.md#notification-service) (`notification-service/.../kafka/UserCreatedConsumer.java`).
- **Behaviour:**
  - Validate event (userId non-blank).
  - Call Profile Service via **gRPC** ([ProfileServiceClient](java-reference.md#notification-service)) to get profile (for display name).
  - Build welcome message (name or email fallback), create [Notification](java-reference.md#notification-service) with status SENT, save to `notification_db`.
  - On any exception: log and rethrow so the listener can retry / offset not committed.

---

## Ordering and at-least-once

- **Key:** userId ensures all events for the same user go to the same partition (ordering per user).
- **Delivery:** At-least-once; consumers must be idempotent (Profile: `existsByUserId` check; Notification: one notification per event, could be deduplicated by business key if needed).

See [flow.md](flow.md) for the full signup → Kafka → profile + notification flow.
