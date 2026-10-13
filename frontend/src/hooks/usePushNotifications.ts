import { useState, useEffect, useCallback } from 'react';
import { useAuth } from './useAuth';
import {
  isPushSupported,
  subscribeToPush,
  getExistingSubscription,
  registerPushSubscription,
} from '../services/pushService';
import { storage } from '../utils/storage';

export type PushPermissionState = NotificationPermission | 'unsupported';

export interface UsePushNotificationsReturn {
  isSupported: boolean;
  permission: PushPermissionState;
  isSubscribed: boolean;
  isLoading: boolean;
  error: string | null;
  isDismissed: boolean;
  enableNotifications: () => Promise<boolean>;
  dismissPrompt: () => void;
  resetDismissal: () => void;
  clearError: () => void;
}

export function usePushNotifications(): UsePushNotificationsReturn {
  const { user, isAuthenticated } = useAuth();

  const [isSupported] = useState<boolean>(() => isPushSupported());
  const [permission, setPermission] = useState<PushPermissionState>(() => {
    if (!isPushSupported() || typeof Notification === 'undefined') {
      return 'unsupported';
    }
    return Notification.permission;
  });
  const [isSubscribed, setIsSubscribed] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isDismissed, setIsDismissed] = useState<boolean>(() => {
    return user ? storage.getPushPromptDismissed(user.id) : false;
  });

  // Sync dismissal preference if user changes
  useEffect(() => {
    if (user) {
      setIsDismissed(storage.getPushPromptDismissed(user.id));
    } else {
      setIsDismissed(false);
    }
  }, [user]);

  // Check existing subscription and auto-subscribe / sync with backend if permission is granted
  useEffect(() => {
    let isMounted = true;

    async function checkSubscription() {
      if (
        isAuthenticated &&
        isSupported &&
        typeof Notification !== 'undefined' &&
        Notification.permission === 'granted'
      ) {
        try {
          const sub = await getExistingSubscription();
          if (sub) {
            try {
              await registerPushSubscription(sub);
              if (isMounted) {
                setIsSubscribed(true);
                setPermission('granted');
              }
            } catch (syncErr) {
              console.warn('[Push] Existing subscription could not sync with backend. Attempting self-healing re-subscription...', syncErr);
              try {
                await subscribeToPush();
                if (isMounted) {
                  setIsSubscribed(true);
                  setPermission('granted');
                }
              } catch (reSubErr) {
                console.warn('[Push] Self-healing re-subscription failed:', reSubErr);
                if (isMounted) {
                  setIsSubscribed(false);
                  setPermission('granted');
                }
              }
            }
          } else {
            if (isMounted) {
              setIsSubscribed(false);
              setPermission('granted');
            }
          }
        } catch {
          // Non-critical background check failure
        }
      }
    }

    checkSubscription();

    return () => {
      isMounted = false;
    };
  }, [isAuthenticated, isSupported]);

  const dismissPrompt = useCallback(() => {
    setIsDismissed(true);
    if (user) {
      storage.setPushPromptDismissed(user.id, true);
    }
  }, [user]);

  const resetDismissal = useCallback(() => {
    setIsDismissed(false);
    if (user) {
      storage.setPushPromptDismissed(user.id, false);
    }
  }, [user]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  /**
   * Enables notifications. MUST ONLY BE CALLED FROM A REAL USER GESTURE.
   */
  const enableNotifications = useCallback(async (): Promise<boolean> => {
    if (isLoading) {
      return false; // Prevent concurrent activation attempts
    }

    if (!isSupported) {
      setError('Browser push notifications are not supported on this device/browser.');
      return false;
    }

    setIsLoading(true);
    setError(null);

    try {
      let currentPermission: PushPermissionState =
        typeof Notification !== 'undefined' ? Notification.permission : 'unsupported';

      if (currentPermission === 'denied') {
        setPermission('denied');
        setError(
          'Notifications are blocked in your browser settings. Please allow notifications for this site to continue.'
        );
        return false;
      }

      // ONLY request browser permission if currently in 'default' state
      if (currentPermission === 'default') {
        const requestedPermission = await Notification.requestPermission();
        currentPermission = requestedPermission;
        setPermission(requestedPermission);

        if (requestedPermission !== 'granted') {
          if (requestedPermission === 'denied') {
            setError(
              'Notification permission was denied. You can update this anytime in your browser settings.'
            );
          }
          return false;
        }
      }

      // If granted (either previously or just now), proceed with push subscription registration
      if (currentPermission === 'granted') {
        if (!isSubscribed) {
          await subscribeToPush();
          setIsSubscribed(true);
        }
        setPermission('granted');
        return true;
      }

      return false;
    } catch (err: unknown) {
      console.error('[Push] Failed to enable notifications:', err);
      if (err instanceof Error) {
        setError(err.message || 'Failed to enable browser notifications. Please try again.');
      } else {
        setError('Failed to enable browser notifications. Please try again.');
      }
      return false;
    } finally {
      setIsLoading(false);
    }
  }, [isLoading, isSupported]);

  return {
    isSupported,
    permission,
    isSubscribed,
    isLoading,
    error,
    isDismissed,
    enableNotifications,
    dismissPrompt,
    resetDismissal,
    clearError,
  };
}
