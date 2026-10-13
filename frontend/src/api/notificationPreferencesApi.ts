import apiClient from './axios';
import {
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
} from '../types/notificationPreferences';

export const notificationPreferencesApi = {
  async getPreferences(): Promise<NotificationPreferences> {
    const response = await apiClient.get<NotificationPreferences>(
      '/api/notification-preferences'
    );
    return response.data;
  },

  async updatePreferences(
    data: UpdateNotificationPreferencesRequest
  ): Promise<NotificationPreferences> {
    const response = await apiClient.put<NotificationPreferences>(
      '/api/notification-preferences',
      data
    );
    return response.data;
  },

  async sendTestEmail(): Promise<{ message: string }> {
    const response = await apiClient.post<{ message: string }>(
      '/api/notification-preferences/test-email'
    );
    return response.data;
  },

  async sendTestDigest(): Promise<{ message: string }> {
    const response = await apiClient.post<{ message: string }>(
      '/api/notification-preferences/test-digest'
    );
    return response.data;
  },
};
