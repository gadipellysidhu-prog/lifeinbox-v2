import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { AuthContext, AuthContextType } from '../context/AuthContext';

describe('ProtectedRoute component', () => {
  const baseAuthContext: AuthContextType = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  };

  it('renders loading spinner while session restoration is pending', () => {
    const loadingContext: AuthContextType = {
      ...baseAuthContext,
      isLoading: true,
    };

    render(
      <AuthContext.Provider value={loadingContext}>
        <MemoryRouter initialEntries={['/dashboard']}>
          <ProtectedRoute>
            <div>Secret Content</div>
          </ProtectedRoute>
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByText(/authenticating session/i)).toBeInTheDocument();
    expect(screen.queryByText('Secret Content')).not.toBeInTheDocument();
  });

  it('redirects unauthenticated user to /login', () => {
    const unauthenticatedContext: AuthContextType = {
      ...baseAuthContext,
      isLoading: false,
      isAuthenticated: false,
    };

    render(
      <AuthContext.Provider value={unauthenticatedContext}>
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Secret Content</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Login Page Target</div>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByText('Login Page Target')).toBeInTheDocument();
    expect(screen.queryByText('Secret Content')).not.toBeInTheDocument();
  });

  it('renders protected children for authenticated user', () => {
    const authenticatedContext: AuthContextType = {
      ...baseAuthContext,
      isLoading: false,
      isAuthenticated: true,
      user: {
        id: 'u1',
        name: 'Jane Doe',
        email: 'jane@example.com',
        createdAt: '2026-10-09T00:00:00Z',
      },
    };

    render(
      <AuthContext.Provider value={authenticatedContext}>
        <MemoryRouter initialEntries={['/dashboard']}>
          <ProtectedRoute>
            <div>Protected Dashboard Content</div>
          </ProtectedRoute>
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByText('Protected Dashboard Content')).toBeInTheDocument();
  });
});
