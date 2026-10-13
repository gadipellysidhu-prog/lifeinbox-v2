import axios, { AxiosError } from 'axios';
import { storage } from '../utils/storage';
import { ApiError } from '../types/auth';

const baseURL = import.meta.env.VITE_API_URL || '';

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = storage.getToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      storage.clearAuth();
      // Only redirect if not already on login or register
      const baseUrl = import.meta.env.BASE_URL || '/';
      const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
      const loginPath = `${normalizedBase}login`;
      const registerPath = `${normalizedBase}register`;

      if (
        typeof window !== 'undefined' &&
        !window.location.pathname.startsWith(loginPath) &&
        !window.location.pathname.startsWith('/login') &&
        !window.location.pathname.startsWith(registerPath) &&
        !window.location.pathname.startsWith('/register')
      ) {
        window.location.href = `${import.meta.env.BASE_URL}login`;
      }
    }
    return Promise.reject(error);
  }
);

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiError>;
    const apiError = axiosError.response?.data;

    if (apiError) {
      if (apiError.detail) {
        return apiError.detail;
      }
      if (apiError.errors && Object.keys(apiError.errors).length > 0) {
        const firstKey = Object.keys(apiError.errors)[0];
        return `${firstKey}: ${apiError.errors[firstKey]}`;
      }
      if (apiError.title) {
        return apiError.title;
      }
    }

    if (axiosError.response?.status === 401) {
      return 'Invalid email or password';
    }
    if (axiosError.response?.status === 403) {
      return 'Access denied';
    }
    if (axiosError.response?.status === 404) {
      return 'Requested resource was not found';
    }
    if (axiosError.response?.status === 409) {
      return 'An account with this email already exists';
    }
    if (axiosError.message) {
      return axiosError.message;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'An unexpected error occurred. Please try again.';
}

export default apiClient;
