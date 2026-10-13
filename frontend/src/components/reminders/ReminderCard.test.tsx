import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ReminderCard } from './ReminderCard';
import { Reminder } from '../../types/reminder';

const mockReminder: Reminder = {
  id: 'rem-1',
  title: 'Renew Passport',
  notes: 'Check photo requirements before submission',
  dueAt: '2026-12-31T15:00:00Z',
  priority: 'HIGH',
  category: 'Government',
  status: 'PENDING',
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
  completedAt: null,
};

describe('ReminderCard component', () => {
  it('renders reminder title, notes, priority, category, and date', () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn().mockResolvedValue(undefined);
    const onToggleComplete = vi.fn().mockResolvedValue(undefined);

    render(
      <ReminderCard
        reminder={mockReminder}
        onEdit={onEdit}
        onDelete={onDelete}
        onToggleComplete={onToggleComplete}
      />
    );

    expect(screen.getByText('Renew Passport')).toBeInTheDocument();
    expect(screen.getByText('Check photo requirements before submission')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('Government')).toBeInTheDocument();
  });

  it('calls onToggleComplete when checkbox button is clicked', async () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn().mockResolvedValue(undefined);
    const onToggleComplete = vi.fn().mockResolvedValue(undefined);

    render(
      <ReminderCard
        reminder={mockReminder}
        onEdit={onEdit}
        onDelete={onDelete}
        onToggleComplete={onToggleComplete}
      />
    );

    const toggleBtn = screen.getByRole('button', { name: /mark as completed/i });
    await act(async () => {
      fireEvent.click(toggleBtn);
    });

    expect(onToggleComplete).toHaveBeenCalledWith('rem-1', 'PENDING');
  });

  it('calls onEdit when edit button is clicked', () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn().mockResolvedValue(undefined);
    const onToggleComplete = vi.fn().mockResolvedValue(undefined);

    render(
      <ReminderCard
        reminder={mockReminder}
        onEdit={onEdit}
        onDelete={onDelete}
        onToggleComplete={onToggleComplete}
      />
    );

    const editBtn = screen.getByRole('button', { name: /edit reminder/i });
    fireEvent.click(editBtn);

    expect(onEdit).toHaveBeenCalledWith(mockReminder);
  });

  it('shows confirmation before executing delete', async () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn().mockResolvedValue(undefined);
    const onToggleComplete = vi.fn().mockResolvedValue(undefined);

    render(
      <ReminderCard
        reminder={mockReminder}
        onEdit={onEdit}
        onDelete={onDelete}
        onToggleComplete={onToggleComplete}
      />
    );

    const deleteBtn = screen.getByRole('button', { name: /delete reminder/i });
    await act(async () => {
      fireEvent.click(deleteBtn);
    });

    const confirmDeleteBtn = screen.getByRole('button', { name: /^delete$/i });
    await act(async () => {
      fireEvent.click(confirmDeleteBtn);
    });

    expect(onDelete).toHaveBeenCalledWith('rem-1');
  });
});
