import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ReminderForm } from './ReminderForm';
import { Reminder } from '../../types/reminder';

const mockReminder: Reminder = {
  id: 'rem-1',
  title: 'Pay Annual Property Tax',
  notes: 'County portal online bill pay',
  dueAt: '2026-11-15T18:00:00.000Z',
  priority: 'HIGH',
  category: 'Taxes',
  status: 'PENDING',
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  completedAt: null,
};

describe('ReminderForm component', () => {
  it('renders create mode with empty/default fields', () => {
    render(<ReminderForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('heading', { name: /create new reminder/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/title/i)).toHaveValue('');
    expect(screen.getByLabelText(/notes/i)).toHaveValue('');
    expect(screen.getByLabelText(/category/i)).toHaveValue('');
  });

  it('renders edit mode with pre-filled fields', () => {
    render(<ReminderForm initialData={mockReminder} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('heading', { name: /edit reminder/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/title/i)).toHaveValue('Pay Annual Property Tax');
    expect(screen.getByLabelText(/notes/i)).toHaveValue('County portal online bill pay');
    expect(screen.getByLabelText(/category/i)).toHaveValue('Taxes');
  });

  it('validates title requirement and shows error', async () => {
    const onSubmit = vi.fn();
    render(<ReminderForm onSubmit={onSubmit} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/title/i), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: /create reminder/i }));

    await waitFor(() => {
      expect(screen.getByText(/title is required/i)).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('converts local datetime input to UTC ISO string on submit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<ReminderForm onSubmit={onSubmit} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/title/i), {
      target: { value: 'Inspect HVAC filters' },
    });
    fireEvent.change(screen.getByLabelText(/notes/i), {
      target: { value: 'Buy 20x25x1 MERV 11' },
    });
    fireEvent.change(screen.getByLabelText(/category/i), {
      target: { value: 'Maintenance' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create reminder/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalled();
      const payload = onSubmit.mock.calls[0][0];
      expect(payload.title).toBe('Inspect HVAC filters');
      expect(payload.notes).toBe('Buy 20x25x1 MERV 11');
      expect(payload.category).toBe('Maintenance');
      expect(payload.priority).toBe('MEDIUM');
      expect(typeof payload.dueAt).toBe('string');
      expect(new Date(payload.dueAt).toISOString()).toBe(payload.dueAt);
    });
  });
});
