-- V6 Migration: Add push notifications toggle to user notification preferences
ALTER TABLE user_notification_preferences
ADD COLUMN push_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
