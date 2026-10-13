import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useReminders } from '../../hooks/useReminders';
import { CreateReminderRequest, Reminder, ReminderFilterType } from '../../types/reminder';
import { ReminderCard } from '../../components/reminders/ReminderCard';
import { ReminderFilters } from '../../components/reminders/ReminderFilters';
import { ReminderForm } from '../../components/reminders/ReminderForm';
import { Button } from '../../components/common/Button';
import { Spinner } from '../../components/common/Spinner';
import { NotificationPermissionPrompt } from '../../components/notifications/NotificationPermissionPrompt';
import {
  AlertCircle,
  CheckCircle2,
  Inbox,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react';

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const {
    reminders,
    counts,
    isLoading,
    error,
    filter,
    search,
    setFilter,
    setSearch,
    refetch,
    createReminder,
    updateReminder,
    completeReminder,
    incompleteReminder,
    deleteReminder,
  } = useReminders();

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingReminder, setEditingReminder] = useState<Reminder | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const handleOpenCreate = () => {
    setEditingReminder(null);
    setActionError(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (reminder: Reminder) => {
    setEditingReminder(reminder);
    setActionError(null);
    setIsFormOpen(true);
  };

  const handleCloseForm = () => {
    setIsFormOpen(false);
    setEditingReminder(null);
    setActionError(null);
  };

  const handleFormSubmit = async (data: CreateReminderRequest) => {
    setIsSubmitting(true);
    setActionError(null);
    try {
      if (editingReminder) {
        await updateReminder(editingReminder.id, data);
      } else {
        await createReminder(data);
      }
      handleCloseForm();
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message);
      } else {
        setActionError('Failed to save reminder');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleComplete = async (id: string, currentStatus: string) => {
    setActionError(null);
    try {
      if (currentStatus === 'COMPLETED') {
        await incompleteReminder(id);
      } else {
        await completeReminder(id);
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update status');
      }
    }
  };

  const handleDelete = async (id: string) => {
    setActionError(null);
    try {
      await deleteReminder(id);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message);
      } else {
        setActionError('Failed to delete reminder');
      }
    }
  };

  const filterTitles: Record<ReminderFilterType, string> = {
    all: 'All Reminders',
    today: "Today's Focus",
    upcoming: 'Upcoming Tasks',
    overdue: 'Overdue Attention',
    completed: 'Completed Archive',
  };

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      {/* Top Header & Greeting */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8 pb-6 border-b border-slate-800">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight">
            {filterTitles[filter]}
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            {user?.name ? `Hello, ${user.name}. ` : ''}
            Manage your obligations, life milestones, and recurring chores.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isLoading}
            aria-label="Refresh reminders"
            className="text-slate-300"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </Button>

          <Button
            variant="primary"
            size="md"
            leftIcon={<Plus className="w-4 h-4" />}
            onClick={handleOpenCreate}
            className="shadow-md shadow-indigo-600/20"
          >
            New Reminder
          </Button>
        </div>
      </div>

      {/* Browser Push Notification Permission Prompt */}
      <NotificationPermissionPrompt />

      {/* Global / Action Error Banner */}
      {(error || actionError) && (
        <div
          role="alert"
          className="mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-start justify-between gap-3 animate-fadeIn"
        >
          <div className="flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">{error || actionError}</p>
              {error && (
                <button
                  type="button"
                  onClick={() => refetch()}
                  className="mt-1 text-xs text-rose-400 hover:text-rose-300 underline font-medium"
                >
                  Click here to retry
                </button>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={() => setActionError(null)}
            className="text-rose-400 hover:text-rose-300 text-xs font-semibold px-2 py-1 rounded"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="mb-6">
        <ReminderFilters
          activeFilter={filter}
          counts={counts}
          search={search}
          onFilterChange={(newFilter) => setFilter(newFilter)}
          onSearchChange={(newSearch) => setSearch(newSearch)}
        />
      </div>

      {/* Reminder Form Modal Overlay */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fadeIn">
          <div className="w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <ReminderForm
              initialData={editingReminder}
              onSubmit={handleFormSubmit}
              onCancel={handleCloseForm}
              isLoading={isSubmitting}
            />
          </div>
        </div>
      )}

      {/* Main Content Area */}
      <div className="mt-6">
        {isLoading && reminders.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
            <Spinner size="lg" />
            <p className="text-sm font-medium">Loading reminders...</p>
          </div>
        ) : reminders.length === 0 ? (
          <div className="bg-slate-900/40 border border-dashed border-slate-800 rounded-2xl p-12 text-center flex flex-col items-center justify-center">
            <div className="w-16 h-16 rounded-2xl bg-slate-800/80 flex items-center justify-center text-slate-400 mb-4 border border-slate-700/50">
              {search ? (
                <Search className="w-8 h-8 text-slate-500" />
              ) : filter === 'completed' ? (
                <CheckCircle2 className="w-8 h-8 text-emerald-500" />
              ) : (
                <Inbox className="w-8 h-8 text-indigo-400" />
              )}
            </div>

            <h3 className="text-lg font-bold text-slate-200">
              {search
                ? `No reminders matching "${search}"`
                : filter === 'today'
                  ? 'No reminders due today'
                  : filter === 'upcoming'
                    ? 'No upcoming reminders scheduled'
                    : filter === 'overdue'
                      ? 'Great job! No overdue reminders'
                      : filter === 'completed'
                        ? 'No completed reminders yet'
                        : 'Your inbox is empty'}
            </h3>

            <p className="text-sm text-slate-400 max-w-sm mt-1 mb-6">
              {search
                ? 'Try searching with a different keyword or clear the search filter.'
                : filter === 'overdue'
                  ? 'You are completely caught up on all scheduled obligations.'
                  : 'Capture your chores, warranties, tax deadlines, and life tasks before you forget.'}
            </p>

            {search ? (
              <Button variant="outline" size="sm" onClick={() => setSearch('')}>
                Clear Search
              </Button>
            ) : (
              <Button
                variant="primary"
                size="md"
                leftIcon={<Plus className="w-4 h-4" />}
                onClick={handleOpenCreate}
              >
                Create a Reminder
              </Button>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {reminders.map((reminder) => (
              <ReminderCard
                key={reminder.id}
                reminder={reminder}
                onEdit={handleOpenEdit}
                onDelete={handleDelete}
                onToggleComplete={handleToggleComplete}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default DashboardPage;
