import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { usePushNotifications } from '../../hooks/usePushNotifications';
import { Button } from '../common/Button';
import {
  Bell,
  BellOff,
  CheckCircle2,
  AlertCircle,
  X,
  Sparkles,
} from 'lucide-react';

export const NotificationPermissionPrompt: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const {
    isSupported,
    permission,
    isSubscribed,
    isLoading,
    error,
    isDismissed,
    enableNotifications,
    dismissPrompt,
    clearError,
  } = usePushNotifications();

  const [justEnabled, setJustEnabled] = useState(false);

  // Never render for unauthenticated users, unsupported browsers, or dismissed prompts
  if (!isAuthenticated || !user || !isSupported || isDismissed) {
    return null;
  }

  // If already subscribed and not just enabled, don't show the prompt banner
  if (isSubscribed && !justEnabled) {
    return null;
  }

  const handleEnable = async () => {
    clearError();
    const success = await enableNotifications();
    if (success) {
      setJustEnabled(true);
    }
  };

  // State: Successfully enabled
  if (justEnabled) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mb-6 p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-sm flex items-center justify-between gap-4 animate-fadeIn shadow-lg shadow-emerald-950/20"
      >
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
            <CheckCircle2 className="w-5 h-5" />
          </div>
          <div>
            <p className="font-semibold text-emerald-200">Notifications Enabled!</p>
            <p className="text-xs text-emerald-400/90 mt-0.5">
              You will now receive browser alerts when your scheduled reminders are due.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            setJustEnabled(false);
            dismissPrompt();
          }}
          className="text-emerald-400 hover:text-emerald-200 p-1.5 rounded-lg hover:bg-emerald-900/40 transition-colors"
          aria-label="Dismiss notification confirmation"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    );
  }

  // State: Permission denied in browser settings or subscription error
  if (permission === 'denied' || error) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className="mb-6 p-4 rounded-2xl bg-amber-950/40 border border-amber-500/30 text-amber-300 text-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4 animate-fadeIn shadow-lg shadow-amber-950/20"
      >
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-xl bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400 shrink-0 mt-0.5 sm:mt-0">
            {permission === 'denied' ? (
              <BellOff className="w-5 h-5" />
            ) : (
              <AlertCircle className="w-5 h-5" />
            )}
          </div>
          <div>
            <p className="font-semibold text-amber-200">
              {permission === 'denied'
                ? 'Browser Notifications Blocked'
                : 'Unable to Enable Notifications'}
            </p>
            <p className="text-xs text-amber-400/90 mt-0.5">
              {error ||
                'Notifications are blocked in your browser settings. To receive reminder alerts, allow notifications in your browser site permissions.'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2 self-end sm:self-center">
          {permission !== 'denied' && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleEnable}
              disabled={isLoading}
              className="text-amber-200 border-amber-600/40 hover:bg-amber-900/30"
            >
              Try Again
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={dismissPrompt}
            className="text-amber-300 hover:text-amber-100"
          >
            Dismiss
          </Button>
        </div>
      </div>
    );
  }

  // State: Default friendly invitation to enable browser notifications
  return (
    <div
      role="region"
      aria-label="Browser Notification Setup"
      className="mb-6 p-4 sm:p-5 rounded-2xl bg-gradient-to-r from-indigo-950/50 via-slate-900 to-slate-950 border border-indigo-500/30 shadow-xl shadow-indigo-950/20 flex flex-col sm:flex-row sm:items-center justify-between gap-4 animate-fadeIn"
    >
      <div className="flex items-start gap-3.5">
        <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shrink-0 mt-0.5 sm:mt-0 shadow-inner">
          <Bell className="w-5 h-5" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold text-slate-100">
              Stay on top of your reminders
            </h3>
            <span className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
              <Sparkles className="w-2.5 h-2.5" />
              Web Push
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1 max-w-xl leading-relaxed">
            Get browser notifications when your LifeInbox reminders are due, even when the tab is in the background.
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2.5 self-end sm:self-center shrink-0">
        <Button
          variant="ghost"
          size="sm"
          onClick={dismissPrompt}
          disabled={isLoading}
          className="text-slate-400 hover:text-slate-200"
        >
          Not Now
        </Button>
        <Button
          variant="primary"
          size="sm"
          onClick={handleEnable}
          isLoading={isLoading}
          className="shadow-md shadow-indigo-600/25"
        >
          Enable Notifications
        </Button>
      </div>
    </div>
  );
};

export default NotificationPermissionPrompt;
