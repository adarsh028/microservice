-- Notification Service – initial schema
-- Migration: V1__init.sql
-- PostgreSQL does not support CREATE TYPE IF NOT EXISTS for ENUMs; use DO block.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notification_status') THEN
        CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS notification (
    id         UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID                NOT NULL,
    message    TEXT                NOT NULL,
    status     notification_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_user_id  ON notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status   ON notification(status);
