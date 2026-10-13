import { useCallback, useEffect, useState } from 'react';
import { notificationPreferencesApi } from '../api/notificationPreferencesApi';
import { getApiErrorMessage } from '../api/axios';
import {
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
} from '../types/notificationPreferences';

export interface UseNotificationPreferencesReturn {
  preferences: NotificationPreferences | null;
  isLoading: boolean;
  isSaving: boolean;
  error: string | null;
  successMessage: string | null;
  clearMessages: () => void;
  refetch: () => Promise<void>;
  updatePreferences: (
    data: UpdateNotificationPreferencesRequest
  ) => Promise<NotificationPreferences>;
}

export function useNotificationPreferences(): UseNotificationPreferencesReturn {
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const fetchPreferences = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await notificationPreferencesApi.getPreferences();
      setPreferences(data);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPreferences();
  }, [fetchPreferences]);

  const update = async (
    data: UpdateNotificationPreferencesRequest
  ): Promise<NotificationPreferences> => {
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await notificationPreferencesApi.updatePreferences(data);
      setPreferences(updated);
      setSuccessMessage('Notification preferences updated successfully');
      return updated;
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw err;
    } finally {
      setIsSaving(false);
    }
  };

  const clearMessages = () => {
    setError(null);
    setSuccessMessage(null);
  };

  return {
    preferences,
    isLoading,
    isSaving,
    error,
    successMessage,
    clearMessages,
    refetch: fetchPreferences,
    updatePreferences: update,
  };
}
