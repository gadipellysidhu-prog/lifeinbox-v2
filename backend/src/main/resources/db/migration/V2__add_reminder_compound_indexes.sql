-- V2 Migration: Add compound performance indexes for reminders queries and filtering
CREATE INDEX IF NOT EXISTS idx_reminders_user_due_at ON reminders(user_id, due_at);
CREATE INDEX IF NOT EXISTS idx_reminders_user_status_due_at ON reminders(user_id, status, due_at);
