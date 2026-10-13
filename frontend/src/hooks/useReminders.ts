import { useCallback, useEffect, useState } from 'react';
import { reminderApi } from '../api/reminderApi';
import { getApiErrorMessage } from '../api/axios';
import {
  CreateReminderRequest,
  Reminder,
  ReminderFilterCounts,
  ReminderFilterType,
  UpdateReminderRequest,
} from '../types/reminder';
import { isPast, isToday, parseISO } from 'date-fns';

export interface UseRemindersReturn {
  reminders: Reminder[];
  counts: ReminderFilterCounts;
  isLoading: boolean;
  error: string | null;
  filter: ReminderFilterType;
  search: string;
  setFilter: (filter: ReminderFilterType) => void;
  setSearch: (search: string) => void;
  refetch: () => Promise<void>;
  createReminder: (data: CreateReminderRequest) => Promise<Reminder>;
  updateReminder: (id: string, data: UpdateReminderRequest) => Promise<Reminder>;
  completeReminder: (id: string) => Promise<Reminder>;
  incompleteReminder: (id: string) => Promise<Reminder>;
  deleteReminder: (id: string) => Promise<void>;
}

export function computeFilterCounts(allReminders: Reminder[]): ReminderFilterCounts {
  let todayCount = 0;
  let upcomingCount = 0;
  let overdueCount = 0;
  let completedCount = 0;

  for (const r of allReminders) {
    if (r.status === 'COMPLETED') {
      completedCount++;
      continue;
    }

    const dueDate = parseISO(r.dueAt);
    if (isToday(dueDate)) {
      todayCount++;
    }
    if (isPast(dueDate)) {
      overdueCount++;
    } else {
      upcomingCount++;
    }
  }

  return {
    all: allReminders.length,
    today: todayCount,
    upcoming: upcomingCount,
    overdue: overdueCount,
    completed: completedCount,
  };
}

export const useReminders = (
  initialFilter: ReminderFilterType = 'all',
  initialSearch: string = ''
): UseRemindersReturn => {
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [filter, setFilter] = useState<ReminderFilterType>(initialFilter);
  const [search, setSearch] = useState<string>(initialSearch);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [counts, setCounts] = useState<ReminderFilterCounts>({
    all: 0,
    today: 0,
    upcoming: 0,
    overdue: 0,
    completed: 0,
  });

  const fetchReminders = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // Fetch filtered/searched reminders from backend
      const data = await reminderApi.getReminders({
        filter: filter === 'all' ? 'all' : filter,
        search: search.trim() ? search.trim() : undefined,
      });
      setReminders(data);

      // If we are currently filtered or searching, fetch all in background to keep badges accurate
      if (filter !== 'all' || search.trim() !== '') {
        try {
          const allData = await reminderApi.getReminders({ filter: 'all' });
          setCounts(computeFilterCounts(allData));
        } catch {
          // Fallback to computing on current subset if all fails
          setCounts(computeFilterCounts(data));
        }
      } else {
        setCounts(computeFilterCounts(data));
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [filter, search]);

  useEffect(() => {
    fetchReminders();
  }, [fetchReminders]);

  const createReminder = async (data: CreateReminderRequest): Promise<Reminder> => {
    try {
      const created = await reminderApi.createReminder(data);
      await fetchReminders();
      return created;
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  const updateReminder = async (id: string, data: UpdateReminderRequest): Promise<Reminder> => {
    try {
      const updated = await reminderApi.updateReminder(id, data);
      await fetchReminders();
      return updated;
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  const completeReminder = async (id: string): Promise<Reminder> => {
    try {
      const completed = await reminderApi.completeReminder(id);
      await fetchReminders();
      return completed;
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  const incompleteReminder = async (id: string): Promise<Reminder> => {
    try {
      const incomplete = await reminderApi.incompleteReminder(id);
      await fetchReminders();
      return incomplete;
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  const deleteReminder = async (id: string): Promise<void> => {
    try {
      await reminderApi.deleteReminder(id);
      await fetchReminders();
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  return {
    reminders,
    counts,
    isLoading,
    error,
    filter,
    search,
    setFilter,
    setSearch,
    refetch: fetchReminders,
    createReminder,
    updateReminder,
    completeReminder,
    incompleteReminder,
    deleteReminder,
  };
};

export default useReminders;
