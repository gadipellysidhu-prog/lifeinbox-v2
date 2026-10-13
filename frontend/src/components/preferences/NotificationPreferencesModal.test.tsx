import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { NotificationPreferencesModal } from './NotificationPreferencesModal';
import { useNotificationPreferences } from '../../hooks/useNotificationPreferences';
import * as pushService from '../../services/pushService';

vi.mock('../../hooks/useNotificationPreferences');
vi.mock('../../hooks/usePushNotifications', () => ({
  usePushNotifications: vi.fn(() => ({
    isSupported: true,
    permission: 'granted',
    isSubscribed: true,
    isLoading: false,
    error: null,
    isDismissed: false,
    enableNotifications: vi.fn().mockResolvedValue(true),
    dismissPrompt: vi.fn(),
    resetDismissal: vi.fn(),
    clearError: vi.fn(),
  })),
}));
vi.mock('../../services/pushService', () => ({
  isPushSupported: vi.fn(() => true),
}));
vi.mock('../../api/notificationPreferencesApi', () => ({
  notificationPreferencesApi: {
    sendTestEmail: vi.fn().mockResolvedValue({ message: 'Test reminder email sent!' }),
    sendTestDigest: vi.fn().mockResolvedValue({ message: 'Test daily digest sent!' }),
  },
}));
import { notificationPreferencesApi } from '../../api/notificationPreferencesApi';

describe('NotificationPreferencesModal component', () => {
  const mockUpdatePreferences = vi.fn();
  const mockClearMessages = vi.fn();
  const mockRefetch = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(pushService.isPushSupported).mockReturnValue(true);
    vi.mocked(useNotificationPreferences).mockReturnValue({
      preferences: {
        id: 'pref-1',
        emailNotificationsEnabled: true,
        reminderEmailsEnabled: true,
        pushNotificationsEnabled: true,
        dailyDigestEnabled: true,
        dailyDigestTime: '08:00:00',
        timezone: 'Asia/Kolkata',
        updatedAt: '2026-10-10T12:00:00Z',
      },
      isLoading: false,
      isSaving: false,
      error: null,
      successMessage: null,
      clearMessages: mockClearMessages,
      refetch: mockRefetch,
      updatePreferences: mockUpdatePreferences,
    });
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <NotificationPreferencesModal isOpen={false} onClose={mockOnClose} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders preference form controls when open', () => {
    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    expect(screen.getByRole('heading', { name: /notification settings/i })).toBeInTheDocument();
    expect(screen.getByText('Email Notifications')).toBeInTheDocument();
    expect(screen.getByText('Scheduled Reminder Emails')).toBeInTheDocument();
    expect(screen.getByText('Browser Push Notifications')).toBeInTheDocument();
    expect(screen.getByText('Daily Morning Digest')).toBeInTheDocument();
    expect(screen.getByLabelText(/user timezone/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save preferences/i })).toBeInTheDocument();
  });

  it('submits updated preferences when save button is clicked', async () => {
    mockUpdatePreferences.mockResolvedValueOnce({
      id: 'pref-1',
      emailNotificationsEnabled: true,
      reminderEmailsEnabled: true,
      pushNotificationsEnabled: true,
      dailyDigestEnabled: true,
      dailyDigestTime: '09:00:00',
      timezone: 'America/New_York',
      updatedAt: '2026-10-10T12:05:00Z',
    });

    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    // Change timezone
    fireEvent.change(screen.getByLabelText(/user timezone/i), {
      target: { value: 'America/New_York' },
    });

    fireEvent.click(screen.getByRole('button', { name: /save preferences/i }));

    await waitFor(() => {
      expect(mockUpdatePreferences).toHaveBeenCalledWith({
        emailNotificationsEnabled: true,
        reminderEmailsEnabled: true,
        pushNotificationsEnabled: true,
        dailyDigestEnabled: true,
        dailyDigestTime: '08:00:00',
        timezone: 'America/New_York',
      });
    });
  });

  it('submits pushNotificationsEnabled = false when toggle is turned off', async () => {
    mockUpdatePreferences.mockResolvedValueOnce({
      id: 'pref-1',
      emailNotificationsEnabled: true,
      reminderEmailsEnabled: true,
      pushNotificationsEnabled: false,
      dailyDigestEnabled: true,
      dailyDigestTime: '08:00:00',
      timezone: 'Asia/Kolkata',
      updatedAt: '2026-10-10T12:05:00Z',
    });

    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    const pushToggle = screen.getByLabelText('Browser Push Notifications');
    expect(pushToggle).toBeChecked();

    fireEvent.click(pushToggle);
    expect(pushToggle).not.toBeChecked();

    fireEvent.click(screen.getByRole('button', { name: /save preferences/i }));

    await waitFor(() => {
      expect(mockUpdatePreferences).toHaveBeenCalledWith({
        emailNotificationsEnabled: true,
        reminderEmailsEnabled: true,
        pushNotificationsEnabled: false,
        dailyDigestEnabled: true,
        dailyDigestTime: '08:00:00',
        timezone: 'Asia/Kolkata',
      });
    });
  });

  it('displays message when push notifications are unsupported in browser', () => {
    vi.mocked(pushService.isPushSupported).mockReturnValue(false);

    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    expect(
      screen.getByText(/browser notifications are not supported in this browser/i)
    ).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', () => {
    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    fireEvent.click(screen.getByRole('button', { name: /close settings/i }));
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('triggers sendTestEmail when Send Test Reminder Email button is clicked', async () => {
    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    const testEmailBtn = screen.getByRole('button', { name: /send test reminder email/i });
    expect(testEmailBtn).toBeInTheDocument();

    fireEvent.click(testEmailBtn);

    await waitFor(() => {
      expect(notificationPreferencesApi.sendTestEmail).toHaveBeenCalledTimes(1);
      expect(screen.getByText('Test reminder email sent!')).toBeInTheDocument();
    });
  });

  it('triggers sendTestDigest when Send Test Daily Digest button is clicked', async () => {
    render(<NotificationPreferencesModal isOpen={true} onClose={mockOnClose} />);

    const testDigestBtn = screen.getByRole('button', { name: /send test daily digest/i });
    expect(testDigestBtn).toBeInTheDocument();

    fireEvent.click(testDigestBtn);

    await waitFor(() => {
      expect(notificationPreferencesApi.sendTestDigest).toHaveBeenCalledTimes(1);
      expect(screen.getByText('Test daily digest sent!')).toBeInTheDocument();
    });
  });
});
