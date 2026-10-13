import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePushNotifications } from './usePushNotifications';
import * as pushService from '../services/pushService';
import * as authHook from './useAuth';
import { storage } from '../utils/storage';

vi.mock('../services/pushService', () => ({
  isPushSupported: vi.fn(),
  subscribeToPush: vi.fn(),
  getExistingSubscription: vi.fn(),
  registerPushSubscription: vi.fn(),
}));

vi.mock('./useAuth', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../utils/storage', () => ({
  storage: {
    getPushPromptDismissed: vi.fn(),
    setPushPromptDismissed: vi.fn(),
  },
}));

describe('usePushNotifications hook', () => {
  const originalNotification = window.Notification;

  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(authHook.useAuth).mockReturnValue({
      user: { id: 'user-1', name: 'Alice', email: 'alice@example.com', createdAt: '' },
      token: 'jwt-token',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    vi.mocked(pushService.isPushSupported).mockReturnValue(true);
    vi.mocked(pushService.getExistingSubscription).mockResolvedValue(null);
    vi.mocked(storage.getPushPromptDismissed).mockReturnValue(false);

    Object.defineProperty(window, 'Notification', {
      value: {
        permission: 'default',
        requestPermission: vi.fn(),
      },
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'Notification', {
      value: originalNotification,
      writable: true,
      configurable: true,
    });
  });

  it('initializes with default permission without requesting browser permission on mount', () => {
    const { result } = renderHook(() => usePushNotifications());

    expect(result.current.isSupported).toBe(true);
    expect(result.current.permission).toBe('default');
    expect(result.current.isSubscribed).toBe(false);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.isDismissed).toBe(false);

    // CRITICAL: Verify requestPermission was NEVER called on mount
    expect(window.Notification.requestPermission).not.toHaveBeenCalled();
  });

  it('calls Notification.requestPermission() only when enableNotifications is triggered with default permission', async () => {
    vi.mocked(window.Notification.requestPermission).mockResolvedValueOnce('granted');
    vi.mocked(pushService.subscribeToPush).mockResolvedValueOnce({
      id: 'sub-1',
      endpoint: 'https://push.example.com/sub',
      active: true,
      createdAt: '',
      updatedAt: '',
    });

    const { result } = renderHook(() => usePushNotifications());

    expect(window.Notification.requestPermission).not.toHaveBeenCalled();

    let success: boolean = false;
    await act(async () => {
      success = await result.current.enableNotifications();
    });

    expect(window.Notification.requestPermission).toHaveBeenCalledTimes(1);
    expect(pushService.subscribeToPush).toHaveBeenCalledTimes(1);
    expect(success).toBe(true);
    expect(result.current.isSubscribed).toBe(true);
    expect(result.current.permission).toBe('granted');
  });

  it('does NOT call requestPermission if permission is already granted', async () => {
    Object.defineProperty(window, 'Notification', {
      value: {
        permission: 'granted',
        requestPermission: vi.fn(),
      },
      writable: true,
      configurable: true,
    });

    vi.mocked(pushService.subscribeToPush).mockResolvedValueOnce({
      id: 'sub-1',
      endpoint: 'https://push.example.com/sub',
      active: true,
      createdAt: '',
      updatedAt: '',
    });

    const { result } = renderHook(() => usePushNotifications());

    await act(async () => {
      await result.current.enableNotifications();
    });

    expect(window.Notification.requestPermission).not.toHaveBeenCalled();
    expect(pushService.subscribeToPush).toHaveBeenCalledTimes(1);
    expect(result.current.isSubscribed).toBe(true);
  });

  it('does NOT call requestPermission if permission is denied', async () => {
    Object.defineProperty(window, 'Notification', {
      value: {
        permission: 'denied',
        requestPermission: vi.fn(),
      },
      writable: true,
      configurable: true,
    });

    const { result } = renderHook(() => usePushNotifications());

    let success: boolean = true;
    await act(async () => {
      success = await result.current.enableNotifications();
    });

    expect(window.Notification.requestPermission).not.toHaveBeenCalled();
    expect(pushService.subscribeToPush).not.toHaveBeenCalled();
    expect(success).toBe(false);
    expect(result.current.error).toContain('Notifications are blocked');
  });

  it('handles user denying permission in prompt without calling subscribeToPush', async () => {
    vi.mocked(window.Notification.requestPermission).mockResolvedValueOnce('denied');

    const { result } = renderHook(() => usePushNotifications());

    let success: boolean = true;
    await act(async () => {
      success = await result.current.enableNotifications();
    });

    expect(window.Notification.requestPermission).toHaveBeenCalledTimes(1);
    expect(pushService.subscribeToPush).not.toHaveBeenCalled();
    expect(success).toBe(false);
    expect(result.current.permission).toBe('denied');
    expect(result.current.error).toContain('Notification permission was denied');
  });

  it('handles push subscription failure gracefully with user-friendly error message', async () => {
    vi.mocked(window.Notification.requestPermission).mockResolvedValueOnce('granted');
    vi.mocked(pushService.subscribeToPush).mockRejectedValueOnce(
      new Error('Network error connecting to VAPID endpoint')
    );

    const { result } = renderHook(() => usePushNotifications());

    let success: boolean = true;
    await act(async () => {
      success = await result.current.enableNotifications();
    });

    expect(success).toBe(false);
    expect(result.current.isSubscribed).toBe(false);
    expect(result.current.error).toBe('Network error connecting to VAPID endpoint');
  });

  it('dismissPrompt sets isDismissed to true and persists to storage without revoking permissions', () => {
    const { result } = renderHook(() => usePushNotifications());

    act(() => {
      result.current.dismissPrompt();
    });

    expect(result.current.isDismissed).toBe(true);
    expect(storage.setPushPromptDismissed).toHaveBeenCalledWith('user-1', true);
  });

  it('enforces user isolation: dismissal for User A does not suppress prompt for User B', () => {
    vi.mocked(storage.getPushPromptDismissed).mockImplementation((userId?: string) => {
      return userId === 'user-A';
    });

    // Test with User A (dismissed = true)
    vi.mocked(authHook.useAuth).mockReturnValue({
      user: { id: 'user-A', name: 'Alice', email: 'alice@example.com', createdAt: '' },
      token: 'jwt-token-a',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    const { result: resultA } = renderHook(() => usePushNotifications());
    expect(resultA.current.isDismissed).toBe(true);

    // Switch to User B (dismissed = false)
    vi.mocked(authHook.useAuth).mockReturnValue({
      user: { id: 'user-B', name: 'Bob', email: 'bob@example.com', createdAt: '' },
      token: 'jwt-token-b',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    const { result: resultB } = renderHook(() => usePushNotifications());
    expect(resultB.current.isDismissed).toBe(false);
  });

  it('attempts self-healing re-subscription when existing subscription sync fails', async () => {
    Object.defineProperty(window, 'Notification', {
      value: {
        permission: 'granted',
      },
      writable: true,
      configurable: true,
    });

    const mockSub = { endpoint: 'https://push.test/sub-old' } as PushSubscription;
    vi.mocked(pushService.getExistingSubscription).mockResolvedValue(mockSub);
    // Initial sync fails (e.g. 409 from another user)
    vi.mocked(pushService.registerPushSubscription).mockRejectedValueOnce(
      new Error('Conflict: already registered')
    );
    // Self-healing subscribeToPush succeeds
    vi.mocked(pushService.subscribeToPush).mockResolvedValueOnce({
      id: 'sub-new',
      endpoint: 'https://push.test/sub-new',
      active: true,
      createdAt: '',
      updatedAt: '',
    });

    const { result } = renderHook(() => usePushNotifications());

    await act(async () => {
      // allow effect microtasks to resolve
      await Promise.resolve();
    });

    expect(pushService.registerPushSubscription).toHaveBeenCalledWith(mockSub);
    expect(pushService.subscribeToPush).toHaveBeenCalled();
    expect(result.current.isSubscribed).toBe(true);
  });
});
