import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { AuthContext, AuthContextType } from '../../context/AuthContext';
import { reminderApi } from '../../api/reminderApi';
import { Reminder } from '../../types/reminder';

vi.mock('../../api/reminderApi');

const mockUser = {
  id: 'u1',
  name: 'Sidharth',
  email: 'sid@example.com',
  createdAt: '2026-10-09T00:00:00Z',
};

const mockAuthContext: AuthContextType = {
  user: mockUser,
  token: 'jwt-token',
  isAuthenticated: true,
  isLoading: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  refreshUser: vi.fn(),
};

const mockReminders: Reminder[] = [
  {
    id: 'r-1',
    title: 'File Income Tax',
    notes: 'Use Form 1040',
    dueAt: '2026-10-15T12:00:00Z',
    priority: 'HIGH',
    category: 'Finance',
    status: 'PENDING',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    completedAt: null,
  },
  {
    id: 'r-2',
    title: 'Doctor Appointment',
    notes: 'Routine checkup',
    dueAt: '2026-10-20T09:00:00Z',
    priority: 'MEDIUM',
    category: 'Health',
    status: 'COMPLETED',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    completedAt: '2026-10-02T10:00:00Z',
  },
];

describe('DashboardPage component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(reminderApi.getReminders).mockResolvedValue(mockReminders);
  });

  it('renders dashboard with user greeting and reminders list', async () => {
    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Hello, Sidharth/i)).toBeInTheDocument();
      expect(screen.getByText('File Income Tax')).toBeInTheDocument();
      expect(screen.getByText('Doctor Appointment')).toBeInTheDocument();
    });
  });

  it('opens and closes create reminder modal', async () => {
    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('File Income Tax')).toBeInTheDocument();
    });

    const newBtn = screen.getByRole('button', { name: /new reminder/i });
    fireEvent.click(newBtn);

    expect(screen.getByRole('heading', { name: /create new reminder/i })).toBeInTheDocument();

    const cancelBtn = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelBtn);

    expect(screen.queryByRole('heading', { name: /create new reminder/i })).not.toBeInTheDocument();
  });

  it('triggers complete toggle and delete actions', async () => {
    vi.mocked(reminderApi.completeReminder).mockResolvedValue({
      ...mockReminders[0],
      status: 'COMPLETED',
      completedAt: '2026-10-09T10:00:00Z',
    });
    vi.mocked(reminderApi.deleteReminder).mockResolvedValue();

    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('File Income Tax')).toBeInTheDocument();
    });

    // Toggle complete
    const toggleButtons = screen.getAllByRole('button', { name: /mark as completed/i });
    fireEvent.click(toggleButtons[0]);

    await waitFor(() => {
      expect(reminderApi.completeReminder).toHaveBeenCalledWith('r-1');
    });

    // Click delete icon and confirm delete
    const deleteButtons = screen.getAllByRole('button', { name: /delete reminder/i });
    fireEvent.click(deleteButtons[0]);

    const confirmDeleteBtn = screen.getByRole('button', { name: /^delete$/i });
    fireEvent.click(confirmDeleteBtn);

    await waitFor(() => {
      expect(reminderApi.deleteReminder).toHaveBeenCalledWith('r-1');
    });
  });
});

