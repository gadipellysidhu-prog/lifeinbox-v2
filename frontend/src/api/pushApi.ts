import apiClient from './axios';
import {
  VapidPublicKeyResponse,
  PushSubscriptionRequest,
  PushSubscriptionDeleteRequest,
  PushSubscriptionResponse,
  PushDispatchResult,
} from '../types/push';

export const pushApi = {
  async getPublicKey(): Promise<string> {
    const response = await apiClient.get<VapidPublicKeyResponse>('/api/push/public-key');
    return response.data.publicKey;
  },

  async registerSubscription(
    data: PushSubscriptionRequest
  ): Promise<PushSubscriptionResponse> {
    const response = await apiClient.post<PushSubscriptionResponse>(
      '/api/push/subscriptions',
      data
    );
    return response.data;
  },

  async deleteSubscription(data: PushSubscriptionDeleteRequest): Promise<void> {
    await apiClient.delete('/api/push/subscriptions', { data });
  },

  async sendTestNotification(): Promise<PushDispatchResult> {
    const response = await apiClient.post<PushDispatchResult>('/api/push/test');
    return response.data;
  },
};
