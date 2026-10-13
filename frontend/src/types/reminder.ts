export type ReminderPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export type ReminderStatus = 'PENDING' | 'COMPLETED';

export type ReminderFilterType = 'all' | 'today' | 'upcoming' | 'overdue' | 'completed';

export interface Reminder {
  id: string;
  title: string;
  notes?: string | null;
  dueAt: string;
  priority: ReminderPriority;
  category?: string | null;
  status: ReminderStatus;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}

export interface CreateReminderRequest {
  title: string;
  notes?: string | null;
  dueAt: string;
  priority: ReminderPriority;
  category?: string | null;
}

export interface UpdateReminderRequest {
  title: string;
  notes?: string | null;
  dueAt: string;
  priority: ReminderPriority;
  category?: string | null;
}

export interface ReminderFilterCounts {
  all: number;
  today: number;
  upcoming: number;
  overdue: number;
  completed: number;
}
