import { User } from '../types/auth';

const TOKEN_KEY = 'lifeinbox_token';
const USER_KEY = 'lifeinbox_user';

export const storage = {
  getToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },

  setToken(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch (e) {
      console.error('Failed to save token to localStorage', e);
    }
  },

  removeToken(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch (e) {
      console.error('Failed to remove token from localStorage', e);
    }
  },

  getUser(): User | null {
    try {
      const data = localStorage.getItem(USER_KEY);
      return data ? (JSON.parse(data) as User) : null;
    } catch {
      return null;
    }
  },

  setUser(user: User): void {
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    } catch (e) {
      console.error('Failed to save user to localStorage', e);
    }
  },

  removeUser(): void {
    try {
      localStorage.removeItem(USER_KEY);
    } catch (e) {
      console.error('Failed to remove user from localStorage', e);
    }
  },

  clearAuth(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch (e) {
      console.error('Failed to clear auth from localStorage', e);
    }
  },

  getPushPromptDismissed(userId?: string): boolean {
    try {
      const key = userId ? `lifeinbox_push_dismissed_${userId}` : 'lifeinbox_push_dismissed';
      return localStorage.getItem(key) === 'true';
    } catch {
      return false;
    }
  },

  setPushPromptDismissed(userId?: string, dismissed: boolean = true): void {
    try {
      const key = userId ? `lifeinbox_push_dismissed_${userId}` : 'lifeinbox_push_dismissed';
      if (dismissed) {
        localStorage.setItem(key, 'true');
      } else {
        localStorage.removeItem(key);
      }
    } catch (e) {
      console.error('Failed to save push dismissal preference to localStorage', e);
    }
  },
};
