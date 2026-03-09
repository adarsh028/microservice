-- Notification Service – initial schema
-- Migration: V1__init.sql

CREATE TYPE IF NOT EXISTS notification_status AS ENUM ('PENDING', 'SENT', 'FAILED');

CREATE TABLE IF NOT EXISTS notification (
    id         UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID                NOT NULL,
    message    TEXT                NOT NULL,
    status     notification_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_user_id  ON notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status   ON notification(status);
