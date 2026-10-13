-- V3 Migration: Add user notification preferences
CREATE TABLE user_notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_emails_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_digest_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_digest_time TIME NOT NULL DEFAULT '08:00:00',
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_notification_preferences_user_id ON user_notification_preferences(user_id);
CREATE INDEX idx_user_notification_preferences_digest ON user_notification_preferences(daily_digest_enabled, daily_digest_time);

-- Seed default preferences for existing users
INSERT INTO user_notification_preferences (user_id, email_notifications_enabled, reminder_emails_enabled, daily_digest_enabled, daily_digest_time, timezone, created_at, updated_at)
SELECT id, TRUE, TRUE, TRUE, '08:00:00', 'UTC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users
ON CONFLICT (user_id) DO NOTHING;
