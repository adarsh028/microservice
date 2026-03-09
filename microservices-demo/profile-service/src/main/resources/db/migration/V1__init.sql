-- Profile Service – initial schema
-- Migration: V1__init.sql

CREATE TABLE IF NOT EXISTS user_profile (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL UNIQUE,
    name       VARCHAR(255),
    bio        TEXT,
    avatar_url VARCHAR(512),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_profile_user_id ON user_profile(user_id);
