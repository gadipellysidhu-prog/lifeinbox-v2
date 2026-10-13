import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { apiClient, getApiErrorMessage } from './axios';
import { storage } from '../utils/storage';

describe('apiClient and axios configuration', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    storage.clearAuth();
    delete (window as unknown as { location?: unknown }).location;
    window.location = {
      ...originalLocation,
      pathname: '/lifeinbox-v2/dashboard',
      href: 'https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard',
    } as unknown as Location;
  });

  afterEach(() => {
    window.location = originalLocation;
  });

  it('attaches Bearer token to request headers if token is in storage', async () => {
    storage.setToken('test-jwt-token');

    // Run the request interceptor directly
    // @ts-expect-error accessing internal axios handlers for unit test
    const requestInterceptor = apiClient.interceptors.request.handlers[0];
    const config: InternalAxiosRequestConfig = {
      headers: new axios.AxiosHeaders(),
    };

    const updatedConfig = await requestInterceptor.fulfilled(config);
    expect(updatedConfig.headers.Authorization).toBe('Bearer test-jwt-token');
  });

  it('redirects to BASE_URL login on 401 when on protected page', async () => {
    storage.setToken('test-jwt-token');
    window.location.pathname = `${import.meta.env.BASE_URL}dashboard`;
    window.location.href = `https://gadipellysidhu-prog.github.io${import.meta.env.BASE_URL}dashboard`;

    // @ts-expect-error accessing internal axios handlers for unit test
    const responseInterceptor = apiClient.interceptors.response.handlers[0];

    const error = {
      response: {
        status: 401,
        data: { message: 'Unauthorized' },
      },
      isAxiosError: true,
    } as AxiosError;

    await expect(responseInterceptor.rejected?.(error)).rejects.toBeDefined();

    expect(storage.getToken()).toBeNull();
    expect(window.location.href).toBe(`${import.meta.env.BASE_URL}login`);
  });

  it('does NOT redirect on 401 if already on BASE_URL login page', async () => {
    const initialHref = `https://gadipellysidhu-prog.github.io${import.meta.env.BASE_URL}login`;
    window.location.pathname = `${import.meta.env.BASE_URL}login`;
    window.location.href = initialHref;

    // @ts-expect-error accessing internal axios handlers for unit test
    const responseInterceptor = apiClient.interceptors.response.handlers[0];

    const error = {
      response: {
        status: 401,
        data: { message: 'Unauthorized' },
      },
      isAxiosError: true,
    } as AxiosError;

    await expect(responseInterceptor.rejected?.(error)).rejects.toBeDefined();

    // href should remain unchanged because user is already on login
    expect(window.location.href).toBe(initialHref);
  });

  it('does NOT redirect on 401 if already on BASE_URL register page', async () => {
    const initialHref = `https://gadipellysidhu-prog.github.io${import.meta.env.BASE_URL}register`;
    window.location.pathname = `${import.meta.env.BASE_URL}register`;
    window.location.href = initialHref;

    // @ts-expect-error accessing internal axios handlers for unit test
    const responseInterceptor = apiClient.interceptors.response.handlers[0];

    const error = {
      response: {
        status: 401,
        data: { message: 'Unauthorized' },
      },
      isAxiosError: true,
    } as AxiosError;

    await expect(responseInterceptor.rejected?.(error)).rejects.toBeDefined();

    expect(window.location.href).toBe(initialHref);
  });
});

describe('getApiErrorMessage', () => {
  it('extracts detail field from ApiError response', () => {
    const error = new AxiosError('Error message');
    error.response = {
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {} as InternalAxiosRequestConfig,
      data: { detail: 'Custom validation failed' },
    };

    expect(getApiErrorMessage(error)).toBe('Custom validation failed');
  });

  it('returns appropriate message for 401 status', () => {
    const error = new AxiosError('Unauthorized');
    error.response = {
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config: {} as InternalAxiosRequestConfig,
      data: {},
    };

    expect(getApiErrorMessage(error)).toBe('Invalid email or password');
  });

  it('handles standard Error instances', () => {
    const error = new Error('Generic error occurred');
    expect(getApiErrorMessage(error)).toBe('Generic error occurred');
  });
});
