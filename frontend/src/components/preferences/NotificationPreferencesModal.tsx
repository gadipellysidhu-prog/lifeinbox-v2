import React, { useState, useEffect } from 'react';
import { useNotificationPreferences } from '../../hooks/useNotificationPreferences';
import { usePushNotifications } from '../../hooks/usePushNotifications';
import { pushApi } from '../../api/pushApi';
import { notificationPreferencesApi } from '../../api/notificationPreferencesApi';
import { getApiErrorMessage } from '../../api/axios';
import { isPushSupported } from '../../services/pushService';
import { Button } from '../common/Button';
import { Spinner } from '../common/Spinner';
import {
  Bell,
  CheckCircle2,
  Clock,
  Globe,
  Mail,
  ShieldAlert,
  Smartphone,
  Sparkles,
  X,
} from 'lucide-react';

export interface NotificationPreferencesModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const COMMON_TIMEZONES = [
  'UTC',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Toronto',
  'Australia/Sydney',
  'Pacific/Auckland',
];

export const NotificationPreferencesModal: React.FC<NotificationPreferencesModalProps> = ({
  isOpen,
  onClose,
}) => {
  const {
    preferences,
    isLoading,
    isSaving,
    error,
    successMessage,
    clearMessages,
    updatePreferences,
  } = useNotificationPreferences();

  const {
    isSupported: isPushDeviceSupported,
    permission: pushPermission,
    isSubscribed: isPushSubscribed,
    isLoading: isPushLoading,
    error: pushError,
    enableNotifications,
  } = usePushNotifications();

  const [emailNotificationsEnabled, setEmailNotificationsEnabled] = useState(true);
  const [reminderEmailsEnabled, setReminderEmailsEnabled] = useState(true);
  const [pushNotificationsEnabled, setPushNotificationsEnabled] = useState(true);
  const [dailyDigestEnabled, setDailyDigestEnabled] = useState(true);
  const [dailyDigestTime, setDailyDigestTime] = useState('08:00');
  const [timezone, setTimezone] = useState('UTC');
  const [customTimezone, setCustomTimezone] = useState('');
  const [isSendingTestPush, setIsSendingTestPush] = useState(false);
  const [testPushStatus, setTestPushStatus] = useState<string | null>(null);

  const [isSendingTestEmail, setIsSendingTestEmail] = useState(false);
  const [testEmailStatus, setTestEmailStatus] = useState<string | null>(null);
  const [testEmailError, setTestEmailError] = useState<string | null>(null);

  const [isSendingTestDigest, setIsSendingTestDigest] = useState(false);
  const [testDigestStatus, setTestDigestStatus] = useState<string | null>(null);
  const [testDigestError, setTestDigestError] = useState<string | null>(null);

  const handleSendTestEmail = async () => {
    setIsSendingTestEmail(true);
    setTestEmailStatus(null);
    setTestEmailError(null);
    try {
      const res = await notificationPreferencesApi.sendTestEmail();
      setTestEmailStatus(res.message || 'Test reminder email sent! Check your inbox and spam folder.');
      setTimeout(() => setTestEmailStatus(null), 6000);
    } catch (err) {
      setTestEmailError(getApiErrorMessage(err));
      setTimeout(() => setTestEmailError(null), 6000);
    } finally {
      setIsSendingTestEmail(false);
    }
  };

  const handleSendTestDigest = async () => {
    setIsSendingTestDigest(true);
    setTestDigestStatus(null);
    setTestDigestError(null);
    try {
      const res = await notificationPreferencesApi.sendTestDigest();
      setTestDigestStatus(res.message || 'Test daily digest email sent! Check your inbox and spam folder.');
      setTimeout(() => setTestDigestStatus(null), 6000);
    } catch (err) {
      setTestDigestError(getApiErrorMessage(err));
      setTimeout(() => setTestDigestError(null), 6000);
    } finally {
      setIsSendingTestDigest(false);
    }
  };

  const handleTogglePush = async (checked: boolean) => {
    setPushNotificationsEnabled(checked);
    if (checked && isPushDeviceSupported && (!isPushSubscribed || pushPermission !== 'granted')) {
      await enableNotifications();
    }
  };

  const handleEnableDevicePush = async () => {
    setTestPushStatus(null);
    const success = await enableNotifications();
    if (success) {
      setPushNotificationsEnabled(true);
    }
  };

  const handleSendTestPush = async () => {
    setIsSendingTestPush(true);
    setTestPushStatus(null);
    try {
      // 1. Show immediate desktop notification via active Service Worker
      try {
        if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
          const reg = await navigator.serviceWorker.ready;
          if (reg && typeof reg.showNotification === 'function') {
            const baseUrl = import.meta.env.BASE_URL || '/';
            const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
            await reg.showNotification('LifeInbox Desktop Alert', {
              body: 'Desktop notifications are active and working perfectly!',
              icon: `${normalizedBase}favicon.ico`,
              tag: `lifeinbox-test-${Date.now()}`,
              renotify: true,
              silent: false,
            } as NotificationOptions);
          }
        }
      } catch (localErr) {
        console.warn('[Push] Direct service worker notification test notice:', localErr);
      }

      // 2. Dispatch push notification via backend
      const result = await pushApi.sendTestNotification();
      if (result && result.attempted === 0) {
        setTestPushStatus('Syncing device with server...');
        const subscribed = await enableNotifications();
        if (subscribed) {
          await pushApi.sendTestNotification();
          setTestPushStatus('Device registered! Test notification sent to desktop banner.');
        } else {
          setTestPushStatus('Device could not sync with server. Please click "Enable on This Device".');
        }
      } else {
        setTestPushStatus('Test notification sent! Check your desktop banner.');
      }
      setTimeout(() => setTestPushStatus(null), 5000);
    } catch {
      setTestPushStatus('Failed to send test notification. Please verify browser notification permissions.');
      setTimeout(() => setTestPushStatus(null), 5000);
    } finally {
      setIsSendingTestPush(false);
    }
  };

  useEffect(() => {
    if (preferences) {
      setEmailNotificationsEnabled(preferences.emailNotificationsEnabled);
      setReminderEmailsEnabled(preferences.reminderEmailsEnabled);
      setPushNotificationsEnabled(preferences.pushNotificationsEnabled ?? true);
      setDailyDigestEnabled(preferences.dailyDigestEnabled);
      // Format dailyDigestTime to HH:mm (strip seconds if present)
      const timeStr = preferences.dailyDigestTime ? preferences.dailyDigestTime.slice(0, 5) : '08:00';
      setDailyDigestTime(timeStr);
      setTimezone(preferences.timezone || 'UTC');
      if (!COMMON_TIMEZONES.includes(preferences.timezone)) {
        setCustomTimezone(preferences.timezone);
      }
    }
  }, [preferences]);

  if (!isOpen) return null;

  const handleDetectTimezone = () => {
    try {
      const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
      if (detected) {
        setTimezone(detected);
        if (!COMMON_TIMEZONES.includes(detected)) {
          setCustomTimezone(detected);
        }
      }
    } catch {
      // Fallback
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    clearMessages();

    // Ensure HH:mm:ss format for backend LocalTime
    const formattedTime = dailyDigestTime.length === 5 ? `${dailyDigestTime}:00` : dailyDigestTime;

    const chosenZone = timezone === 'custom' ? customTimezone.trim() : timezone;

    try {
      await updatePreferences({
        emailNotificationsEnabled,
        reminderEmailsEnabled,
        pushNotificationsEnabled,
        dailyDigestEnabled,
        dailyDigestTime: formattedTime,
        timezone: chosenZone || 'UTC',
      });
    } catch {
      // Error handled by hook
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fadeIn">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-slate-800 flex items-center justify-between bg-slate-900/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-inner">
              <Bell className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-100">Notification Settings</h2>
              <p className="text-xs text-slate-400">Configure email alerts & morning digest</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-200 p-1.5 rounded-lg hover:bg-slate-800 transition-colors"
            aria-label="Close settings"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Form */}
        <form onSubmit={handleSave} className="p-6 overflow-y-auto flex-1 flex flex-col gap-6">
          {/* Status Banners */}
          {error && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-2.5"
            >
              <ShieldAlert className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {successMessage && (
            <div
              role="status"
              className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-start gap-2.5"
            >
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
              <span>{successMessage}</span>
            </div>
          )}

          {isLoading && !preferences ? (
            <div className="py-12 flex flex-col items-center justify-center gap-3 text-slate-400">
              <Spinner size="md" />
              <p className="text-xs">Loading notification preferences...</p>
            </div>
          ) : (
            <>
              {/* Master Email Notifications Toggle */}
              <div className="flex items-center justify-between p-4 rounded-xl bg-slate-950/60 border border-slate-800">
                <div className="flex items-start gap-3">
                  <Mail className="w-5 h-5 text-indigo-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="text-sm font-semibold text-slate-100">
                      Email Notifications
                    </span>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Master switch for all email notifications from LifeInbox
                    </p>
                  </div>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    className="sr-only peer"
                    checked={emailNotificationsEnabled}
                    onChange={(e) => setEmailNotificationsEnabled(e.target.checked)}
                  />
                  <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
                </label>
              </div>

              {/* Scheduled Reminder Emails Toggle */}
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-start gap-3">
                    <Bell className="w-5 h-5 text-violet-400 shrink-0 mt-0.5" />
                    <div>
                      <span className="text-sm font-semibold text-slate-100">
                        Scheduled Reminder Emails
                      </span>
                      <p className="text-xs text-slate-400 mt-0.5">
                        Receive an email alert when each individual reminder reaches its scheduled time
                      </p>
                    </div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      className="sr-only peer"
                      disabled={!emailNotificationsEnabled}
                      checked={emailNotificationsEnabled && reminderEmailsEnabled}
                      onChange={(e) => setReminderEmailsEnabled(e.target.checked)}
                    />
                    <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600 peer-disabled:opacity-50"></div>
                  </label>
                </div>

                {emailNotificationsEnabled && reminderEmailsEnabled && (
                  <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between">
                    <span className="text-xs text-slate-400">Verify email reminder delivery</span>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      isLoading={isSendingTestEmail}
                      onClick={handleSendTestEmail}
                    >
                      Send Test Reminder Email
                    </Button>
                  </div>
                )}
                {testEmailStatus && (
                  <p className="text-xs text-emerald-400 animate-fadeIn">{testEmailStatus}</p>
                )}
                {testEmailError && (
                  <p className="text-xs text-rose-400 animate-fadeIn">{testEmailError}</p>
                )}
              </div>

              {/* Browser Push Notifications Section */}
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-start gap-3">
                    <Smartphone className="w-5 h-5 text-sky-400 shrink-0 mt-0.5" />
                    <div>
                      <span className="text-sm font-semibold text-slate-100">
                        Browser Push Notifications
                      </span>
                      <p className="text-xs text-slate-400 mt-0.5">
                        Receive desktop popups when your LifeInbox reminders become due.
                      </p>
                    </div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      aria-label="Browser Push Notifications"
                      className="sr-only peer"
                      checked={pushNotificationsEnabled}
                      onChange={(e) => handleTogglePush(e.target.checked)}
                    />
                    <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
                  </label>
                </div>

                {/* Status & Action Controls */}
                {pushNotificationsEnabled && (
                  <div className="pt-2 border-t border-slate-800/80 flex flex-col gap-2">
                    {(!isPushDeviceSupported || !isPushSupported()) && (
                      <p className="text-xs text-amber-400 flex items-center gap-1.5">
                        <ShieldAlert className="w-4 h-4 shrink-0" />
                        <span>Browser notifications are not supported in this browser.</span>
                      </p>
                    )}

                    {isPushDeviceSupported && pushPermission === 'denied' && (
                      <div className="p-2.5 rounded-lg bg-amber-950/30 border border-amber-500/20 text-xs text-amber-300">
                        <p className="font-medium flex items-center gap-1.5">
                          <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0" />
                          <span>Notifications are blocked in your browser</span>
                        </p>
                        <p className="text-[11px] text-slate-400 mt-1">
                          Click the site settings/lock icon in your browser address bar next to the URL, set Notifications to "Allow", and reload this page.
                        </p>
                      </div>
                    )}

                    {isPushDeviceSupported && pushPermission !== 'denied' && !isPushSubscribed && (
                      <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-900/60 border border-slate-800">
                        <div className="text-xs text-slate-300">
                          <p className="font-medium">Device Not Subscribed</p>
                          <p className="text-[11px] text-slate-400">Click below to enable desktop alerts on this browser.</p>
                        </div>
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          isLoading={isPushLoading}
                          onClick={handleEnableDevicePush}
                        >
                          Enable on This Device
                        </Button>
                      </div>
                    )}

                    {isPushDeviceSupported && isPushSubscribed && (
                      <div className="flex items-center justify-between p-2.5 rounded-lg bg-emerald-950/30 border border-emerald-500/20">
                        <div className="flex items-center gap-2">
                          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                          <span className="text-xs font-medium text-emerald-300">
                            Desktop alerts active on this device
                          </span>
                        </div>
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          isLoading={isSendingTestPush}
                          onClick={handleSendTestPush}
                        >
                          Send Test Notification
                        </Button>
                      </div>
                    )}

                    {testPushStatus && (
                      <p className="text-xs text-indigo-400 animate-fadeIn">
                        {testPushStatus}
                      </p>
                    )}
                    {pushError && (
                      <p className="text-xs text-rose-400 animate-fadeIn">
                        {pushError}
                      </p>
                    )}
                  </div>
                )}
              </div>

              {/* Daily Morning Digest Section */}
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-start gap-3">
                    <Sparkles className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                    <div>
                      <span className="text-sm font-semibold text-slate-100">
                        Daily Morning Digest
                      </span>
                      <p className="text-xs text-slate-400 mt-0.5">
                        A single morning email summarizing today's tasks, upcoming deadlines, and overdue items
                      </p>
                    </div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      className="sr-only peer"
                      disabled={!emailNotificationsEnabled}
                      checked={emailNotificationsEnabled && dailyDigestEnabled}
                      onChange={(e) => setDailyDigestEnabled(e.target.checked)}
                    />
                    <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600 peer-disabled:opacity-50"></div>
                  </label>
                </div>

                {/* Digest Time Picker & Test Digest */}
                {emailNotificationsEnabled && dailyDigestEnabled && (
                  <>
                    <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between">
                      <label
                        htmlFor="digestTime"
                        className="text-xs font-medium text-slate-300 flex items-center gap-1.5"
                      >
                        <Clock className="w-4 h-4 text-slate-400" />
                        <span>Preferred Digest Time</span>
                      </label>
                      <input
                        id="digestTime"
                        type="time"
                        value={dailyDigestTime}
                        onChange={(e) => setDailyDigestTime(e.target.value)}
                        className="bg-slate-900 border border-slate-700 text-slate-100 text-sm rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                      />
                    </div>

                    <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between">
                      <span className="text-xs text-slate-400">Preview today's summary in your email</span>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        isLoading={isSendingTestDigest}
                        onClick={handleSendTestDigest}
                      >
                        Send Test Daily Digest
                      </Button>
                    </div>

                    {testDigestStatus && (
                      <p className="text-xs text-emerald-400 animate-fadeIn">{testDigestStatus}</p>
                    )}
                    {testDigestError && (
                      <p className="text-xs text-rose-400 animate-fadeIn">{testDigestError}</p>
                    )}
                  </>
                )}
              </div>

              {/* Timezone Setting */}
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <label
                    htmlFor="timezoneSelect"
                    className="text-sm font-semibold text-slate-100 flex items-center gap-2"
                  >
                    <Globe className="w-4 h-4 text-indigo-400" />
                    <span>User Timezone</span>
                  </label>
                  <button
                    type="button"
                    onClick={handleDetectTimezone}
                    className="text-xs text-indigo-400 hover:text-indigo-300 font-medium underline flex items-center gap-1"
                  >
                    Detect My Timezone
                  </button>
                </div>
                <p className="text-xs text-slate-400">
                  Used to schedule your daily morning digest and display reminder due times accurately
                </p>

                <select
                  id="timezoneSelect"
                  value={COMMON_TIMEZONES.includes(timezone) ? timezone : 'custom'}
                  onChange={(e) => {
                    const val = e.target.value;
                    if (val === 'custom') {
                      setTimezone('custom');
                    } else {
                      setTimezone(val);
                      setCustomTimezone('');
                    }
                  }}
                  className="w-full bg-slate-900 border border-slate-700 text-slate-100 text-sm rounded-lg px-3.5 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                >
                  {COMMON_TIMEZONES.map((tz) => (
                    <option key={tz} value={tz}>
                      {tz}
                    </option>
                  ))}
                  <option value="custom">Other / Custom Timezone...</option>
                </select>

                {(!COMMON_TIMEZONES.includes(timezone) || timezone === 'custom') && (
                  <input
                    type="text"
                    placeholder="Enter IANA timezone (e.g. Asia/Kolkata)"
                    value={customTimezone || timezone}
                    onChange={(e) => {
                      setCustomTimezone(e.target.value);
                      setTimezone(e.target.value);
                    }}
                    className="w-full bg-slate-900 border border-slate-700 text-slate-100 text-sm rounded-lg px-3.5 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                  />
                )}
              </div>
            </>
          )}

          {/* Footer Actions */}
          <div className="pt-4 border-t border-slate-800 flex items-center justify-end gap-3">
            <Button variant="ghost" type="button" onClick={onClose} disabled={isSaving}>
              Close
            </Button>
            <Button
              variant="primary"
              type="submit"
              isLoading={isSaving}
              disabled={isLoading}
              className="shadow-md shadow-indigo-600/20"
            >
              Save Preferences
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
