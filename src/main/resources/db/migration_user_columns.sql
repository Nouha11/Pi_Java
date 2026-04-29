-- Run this on your nova_db database to add the profile_picture column
-- Safe to run multiple times (uses IF NOT EXISTS equivalent)
ALTER TABLE user ADD COLUMN IF NOT EXISTS profile_picture VARCHAR(500) NULL DEFAULT NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS totp_enabled TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255) NULL DEFAULT NULL;