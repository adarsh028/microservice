-- Use VARCHAR for status so Hibernate @Enumerated(EnumType.STRING) works.
-- PostgreSQL custom enum required a cast; VARCHAR avoids that and is sufficient here.
ALTER TABLE notification
  ALTER COLUMN status TYPE VARCHAR(20) USING status::text,
  ALTER COLUMN status SET DEFAULT 'PENDING';
