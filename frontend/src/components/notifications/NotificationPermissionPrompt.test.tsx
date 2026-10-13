import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationPermissionPrompt } from './NotificationPermissionPrompt';
import * as authHook from '../../hooks/useAuth';
import * as pushNotificationsHook from '../../hooks/usePushNotifications';

vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../../hooks/usePushNotifications', () => ({
  usePushNotifications: vi.fn(),
}));

describe('NotificationPermissionPrompt component', () => {
  const defaultAuth = {
    user: { id: 'u-1', name: 'Bob', email: 'bob@example.com', createdAt: '' },
    token: 'jwt-token',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  };

  const defaultPushState = {
    isSupported: true,
    permission: 'default' as NotificationPermission | 'unsupported',
    isSubscribed: false,
    isLoading: false,
    error: null,
    isDismissed: false,
    enableNotifications: vi.fn().mockResolvedValue(true),
    dismissPrompt: vi.fn(),
    resetDismissal: vi.fn(),
    clearError: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authHook.useAuth).mockReturnValue(defaultAuth);
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue(defaultPushState);
  });

  it('does NOT render for unauthenticated / logged-out users', () => {
    vi.mocked(authHook.useAuth).mockReturnValue({
      ...defaultAuth,
      isAuthenticated: false,
      user: null,
    });

    const { container } = render(<NotificationPermissionPrompt />);
    expect(container.firstChild).toBeNull();
  });

  it('does NOT render if browser does not support push', () => {
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      isSupported: false,
      permission: 'unsupported',
    });

    const { container } = render(<NotificationPermissionPrompt />);
    expect(container.firstChild).toBeNull();
  });

  it('does NOT render if user previously dismissed the prompt', () => {
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      isDismissed: true,
    });

    const { container } = render(<NotificationPermissionPrompt />);
    expect(container.firstChild).toBeNull();
  });

  it('does NOT render if user is already subscribed', () => {
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      isSubscribed: true,
      permission: 'granted',
    });

    const { container } = render(<NotificationPermissionPrompt />);
    expect(container.firstChild).toBeNull();
  });

  it('renders friendly banner for authenticated user when permission is default', () => {
    render(<NotificationPermissionPrompt />);

    expect(
      screen.getByText('Stay on top of your reminders')
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Get browser notifications when your LifeInbox reminders are due/)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Enable Notifications/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Not Now/i })
    ).toBeInTheDocument();
  });

  it('clicking "Not Now" dismisses the prompt', async () => {
    const user = userEvent.setup();
    const dismissSpy = vi.fn();
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      dismissPrompt: dismissSpy,
    });

    render(<NotificationPermissionPrompt />);

    const notNowButton = screen.getByRole('button', { name: /Not Now/i });
    await user.click(notNowButton);

    expect(dismissSpy).toHaveBeenCalledTimes(1);
  });

  it('clicking "Enable Notifications" triggers enableNotifications()', async () => {
    const user = userEvent.setup();
    const enableSpy = vi.fn().mockResolvedValue(true);
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      enableNotifications: enableSpy,
    });

    render(<NotificationPermissionPrompt />);

    const enableButton = screen.getByRole('button', { name: /Enable Notifications/i });
    await user.click(enableButton);

    expect(enableSpy).toHaveBeenCalledTimes(1);
  });

  it('renders blocked state when permission is denied', () => {
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      permission: 'denied',
    });

    render(<NotificationPermissionPrompt />);

    expect(
      screen.getByText('Browser Notifications Blocked')
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Notifications are blocked in your browser settings/)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Dismiss/i })
    ).toBeInTheDocument();
  });

  it('renders error state with retry option when subscription fails', async () => {
    const user = userEvent.setup();
    const enableSpy = vi.fn();
    vi.mocked(pushNotificationsHook.usePushNotifications).mockReturnValue({
      ...defaultPushState,
      error: 'Failed to connect to push service',
      enableNotifications: enableSpy,
    });

    render(<NotificationPermissionPrompt />);

    expect(
      screen.getByText('Unable to Enable Notifications')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Failed to connect to push service')
    ).toBeInTheDocument();

    const retryButton = screen.getByRole('button', { name: /Try Again/i });
    await user.click(retryButton);

    expect(enableSpy).toHaveBeenCalledTimes(1);
  });
});
