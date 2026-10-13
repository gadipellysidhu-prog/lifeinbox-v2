import apiClient from './axios';
import { CreateReminderRequest, Reminder, UpdateReminderRequest } from '../types/reminder';

export interface GetRemindersParams {
  filter?: string;
  search?: string;
}

export const reminderApi = {
  async getReminders(params?: GetRemindersParams): Promise<Reminder[]> {
    const response = await apiClient.get<Reminder[]>('/api/reminders', {
      params: {
        filter: params?.filter || 'all',
        ...(params?.search ? { search: params.search } : {}),
      },
    });
    return response.data;
  },

  async getReminderById(id: string): Promise<Reminder> {
    const response = await apiClient.get<Reminder>(`/api/reminders/${id}`);
    return response.data;
  },

  async createReminder(data: CreateReminderRequest): Promise<Reminder> {
    const response = await apiClient.post<Reminder>('/api/reminders', data);
    return response.data;
  },

  async updateReminder(id: string, data: UpdateReminderRequest): Promise<Reminder> {
    const response = await apiClient.put<Reminder>(`/api/reminders/${id}`, data);
    return response.data;
  },

  async completeReminder(id: string): Promise<Reminder> {
    const response = await apiClient.patch<Reminder>(`/api/reminders/${id}/complete`);
    return response.data;
  },

  async incompleteReminder(id: string): Promise<Reminder> {
    const response = await apiClient.patch<Reminder>(`/api/reminders/${id}/incomplete`);
    return response.data;
  },

  async deleteReminder(id: string): Promise<void> {
    await apiClient.delete(`/api/reminders/${id}`);
  },
};
