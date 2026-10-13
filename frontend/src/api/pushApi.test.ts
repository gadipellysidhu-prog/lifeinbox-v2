import { describe, it, expect, vi, beforeEach } from 'vitest';
import { pushApi } from './pushApi';
import apiClient from './axios';

vi.mock('./axios', () => {
  return {
    default: {
      get: vi.fn(),
      post: vi.fn(),
      delete: vi.fn(),
    },
  };
});

describe('pushApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getPublicKey calls GET /api/push/public-key and returns publicKey', async () => {
    const mockPublicKey = 'BEl62iUYgUivxIkv69yViEuiBIa';
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { publicKey: mockPublicKey },
    });

    const result = await pushApi.getPublicKey();

    expect(apiClient.get).toHaveBeenCalledWith('/api/push/public-key');
    expect(result).toBe(mockPublicKey);
  });

  it('registerSubscription calls POST /api/push/subscriptions with payload', async () => {
    const payload = {
      endpoint: 'https://push.example.com/sub/123',
      keys: {
        p256dh: 'BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QT9Ps0vE',
        auth: 'tBHItJI5svbpez7KI4CCXg',
      },
    };
    const responseData = {
      id: 'sub-uuid-1',
      endpoint: payload.endpoint,
      active: true,
      createdAt: '2026-10-11T12:00:00Z',
      updatedAt: '2026-10-11T12:00:00Z',
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: responseData,
    });

    const result = await pushApi.registerSubscription(payload);

    expect(apiClient.post).toHaveBeenCalledWith('/api/push/subscriptions', payload);
    expect(result).toEqual(responseData);
  });

  it('deleteSubscription calls DELETE /api/push/subscriptions with data body', async () => {
    const payload = {
      endpoint: 'https://push.example.com/sub/123',
    };
    vi.mocked(apiClient.delete).mockResolvedValueOnce({ status: 200 });

    await pushApi.deleteSubscription(payload);

    expect(apiClient.delete).toHaveBeenCalledWith('/api/push/subscriptions', {
      data: payload,
    });
  });

  it('sendTestNotification calls POST /api/push/test and returns result', async () => {
    const mockResult = {
      attempted: 1,
      successful: 1,
      failed: 0,
      retried: 0,
      deactivated: 0,
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: mockResult,
    });

    const result = await pushApi.sendTestNotification();

    expect(apiClient.post).toHaveBeenCalledWith('/api/push/test');
    expect(result).toEqual(mockResult);
  });
});
