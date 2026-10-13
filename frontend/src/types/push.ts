export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface PushSubscriptionKeys {
  p256dh: string;
  auth: string;
}

export interface PushSubscriptionRequest {
  endpoint: string;
  keys: PushSubscriptionKeys;
}

export interface PushSubscriptionDeleteRequest {
  endpoint: string;
}

export interface PushSubscriptionResponse {
  id: string;
  endpoint: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PushDispatchResult {
  attempted: number;
  successful: number;
  failed: number;
  retried: number;
  deactivated: number;
}

