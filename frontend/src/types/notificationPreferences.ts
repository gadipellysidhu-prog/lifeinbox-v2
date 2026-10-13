export interface NotificationPreferences {
  id: string;
  emailNotificationsEnabled: boolean;
  reminderEmailsEnabled: boolean;
  dailyDigestEnabled: boolean;
  dailyDigestTime: string;
  timezone: string;
  updatedAt: string;
  pushNotificationsEnabled: boolean;
}

export interface UpdateNotificationPreferencesRequest {
  emailNotificationsEnabled: boolean;
  reminderEmailsEnabled: boolean;
  dailyDigestEnabled: boolean;
  dailyDigestTime: string;
  timezone: string;
  pushNotificationsEnabled?: boolean;
}
