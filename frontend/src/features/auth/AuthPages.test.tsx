import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';
import { AuthContext, AuthContextType } from '../../context/AuthContext';

describe('Auth Pages', () => {
  const mockAuthContext: AuthContextType = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form and submits credentials', async () => {
    vi.mocked(mockAuthContext.login).mockResolvedValue({
      accessToken: 'jwt-123',
      user: { id: 'u1', name: 'John', email: 'john@example.com', createdAt: '2026-10-09T00:00:00Z' },
    });

    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'john@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'Password123!' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockAuthContext.login).toHaveBeenCalledWith({
        email: 'john@example.com',
        password: 'Password123!',
      });
    });
  });

  it('renders register form and submits new user data', async () => {
    vi.mocked(mockAuthContext.register).mockResolvedValue({
      accessToken: 'jwt-123',
      user: { id: 'u1', name: 'Alice', email: 'alice@example.com', createdAt: '2026-10-09T00:00:00Z' },
    });

    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <RegisterPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByRole('heading', { name: /create an account/i })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/full name/i), {
      target: { value: 'Alice Smith' },
    });
    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'alice@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'SecretPassword123!' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(mockAuthContext.register).toHaveBeenCalledWith({
        name: 'Alice Smith',
        email: 'alice@example.com',
        password: 'SecretPassword123!',
      });
    });
  });

  it('displays validation errors on invalid registration inputs', async () => {
    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <RegisterPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(screen.getByText(/name must be at least 2 characters/i)).toBeInTheDocument();
      expect(screen.getByText(/email is required/i)).toBeInTheDocument();
      expect(screen.getByText(/password must be at least 8 characters/i)).toBeInTheDocument();
    });
  });
});
