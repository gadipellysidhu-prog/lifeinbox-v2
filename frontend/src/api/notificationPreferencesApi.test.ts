import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from './axios';
import { notificationPreferencesApi } from './notificationPreferencesApi';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}));

describe('notificationPreferencesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getPreferences calls GET /api/notification-preferences and returns preferences with pushNotificationsEnabled', async () => {
    const mockPreferences = {
      id: 'pref-123',
      emailNotificationsEnabled: true,
      reminderEmailsEnabled: true,
      dailyDigestEnabled: true,
      dailyDigestTime: '08:00:00',
      timezone: 'Asia/Kolkata',
      updatedAt: '2026-10-11T12:00:00Z',
      pushNotificationsEnabled: true,
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockPreferences });

    const result = await notificationPreferencesApi.getPreferences();

    expect(apiClient.get).toHaveBeenCalledWith('/api/notification-preferences');
    expect(result).toEqual(mockPreferences);
    expect(result.pushNotificationsEnabled).toBe(true);
  });

  it('updatePreferences calls PUT /api/notification-preferences with pushNotificationsEnabled and returns updated preferences', async () => {
    const updatePayload = {
      emailNotificationsEnabled: true,
      reminderEmailsEnabled: true,
      dailyDigestEnabled: false,
      dailyDigestTime: '09:00:00',
      timezone: 'UTC',
      pushNotificationsEnabled: false,
    };

    const mockResponse = {
      id: 'pref-123',
      ...updatePayload,
      updatedAt: '2026-10-11T12:30:00Z',
    };

    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await notificationPreferencesApi.updatePreferences(updatePayload);

    expect(apiClient.put).toHaveBeenCalledWith('/api/notification-preferences', updatePayload);
    expect(result.pushNotificationsEnabled).toBe(false);
  });

  it('sendTestEmail calls POST /api/notification-preferences/test-email and returns message', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { message: 'Test reminder email sent successfully' },
    });

    const result = await notificationPreferencesApi.sendTestEmail();

    expect(apiClient.post).toHaveBeenCalledWith('/api/notification-preferences/test-email');
    expect(result.message).toBe('Test reminder email sent successfully');
  });

  it('sendTestDigest calls POST /api/notification-preferences/test-digest and returns message', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { message: 'Test daily digest email sent successfully' },
    });

    const result = await notificationPreferencesApi.sendTestDigest();

    expect(apiClient.post).toHaveBeenCalledWith('/api/notification-preferences/test-digest');
    expect(result.message).toBe('Test daily digest email sent successfully');
  });
});
