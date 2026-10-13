import { describe, it, expect, beforeEach } from 'vitest';
import { storage } from './storage';
import { User } from '../types/auth';

describe('storage utility', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('stores and retrieves JWT token with lifeinbox_token key', () => {
    expect(storage.getToken()).toBeNull();
    storage.setToken('test-jwt-token-123');
    expect(storage.getToken()).toBe('test-jwt-token-123');
    expect(localStorage.getItem('lifeinbox_token')).toBe('test-jwt-token-123');
  });

  it('removes token successfully', () => {
    storage.setToken('test-jwt-token-123');
    storage.removeToken();
    expect(storage.getToken()).toBeNull();
  });

  it('stores and retrieves user object with lifeinbox_user key', () => {
    const mockUser: User = {
      id: '123e4567-e89b-12d3-a456-426614174000',
      name: 'John Doe',
      email: 'john@example.com',
      createdAt: '2026-10-09T12:00:00Z',
    };

    expect(storage.getUser()).toBeNull();
    storage.setUser(mockUser);
    expect(storage.getUser()).toEqual(mockUser);
    expect(localStorage.getItem('lifeinbox_user')).toBe(JSON.stringify(mockUser));
  });

  it('clears all authentication data', () => {
    storage.setToken('test-jwt-token-123');
    storage.setUser({
      id: '123e4567-e89b-12d3-a456-426614174000',
      name: 'John Doe',
      email: 'john@example.com',
      createdAt: '2026-10-09T12:00:00Z',
    });

    storage.clearAuth();
    expect(storage.getToken()).toBeNull();
    expect(storage.getUser()).toBeNull();
  });

  it('stores and retrieves push prompt dismissal state per user', () => {
    expect(storage.getPushPromptDismissed('user-1')).toBe(false);
    storage.setPushPromptDismissed('user-1', true);
    expect(storage.getPushPromptDismissed('user-1')).toBe(true);
    expect(storage.getPushPromptDismissed('user-2')).toBe(false);

    storage.setPushPromptDismissed('user-1', false);
    expect(storage.getPushPromptDismissed('user-1')).toBe(false);
  });
});
