import { pushApi } from '../api/pushApi';
import {
  PushSubscriptionRequest,
  PushSubscriptionResponse,
} from '../types/push';

/**
 * Checks if the current browser environment supports the Web Push API.
 */
export function isPushSupported(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false;
  }
  return Boolean(
    'serviceWorker' in navigator &&
      navigator.serviceWorker &&
      'PushManager' in window &&
      window.PushManager &&
      'Notification' in window &&
      window.Notification
  );
}

/**
 * Converts a base64url encoded VAPID public key to a Uint8Array suitable for applicationServerKey.
 */
export function urlBase64ToUint8Array(base64String: string): Uint8Array {
  if (!base64String || typeof base64String !== 'string' || base64String.trim() === '') {
    throw new Error('Invalid base64 string provided');
  }

  const trimmed = base64String.trim();
  const padding = '='.repeat((4 - (trimmed.length % 4)) % 4);
  const base64 = (trimmed + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  try {
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);

    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  } catch (error) {
    throw new Error(
      `Failed to decode base64 string: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

/**
 * Resolves the service worker registration scope based on Vite's BASE_URL.
 * Supports GitHub Pages subpaths (e.g. '/lifeinbox-v2/') and root development ('/').
 */
export function getServiceWorkerScope(): string {
  const base = import.meta.env.BASE_URL || '/';
  let normalized = base;
  if (!normalized.startsWith('/') && !/^https?:\/\//i.test(normalized)) {
    normalized = `/${normalized}`;
  }
  return normalized.endsWith('/') ? normalized : `${normalized}/`;
}

/**
 * Resolves the service worker script URL based on Vite's BASE_URL.
 * In production GitHub Pages: '/lifeinbox-v2/sw.js'
 * In local development: '/sw.js'
 */
export function getServiceWorkerUrl(): string {
  const scope = getServiceWorkerScope();
  return `${scope}sw.js`;
}

/**
 * Registers the service worker at the given path and scope.
 * Uses BASE_URL-aware defaults for both path and scope.
 * Returns the ServiceWorkerRegistration or null on failure without throwing.
 */
export async function registerPushServiceWorker(
  swPath: string = getServiceWorkerUrl(),
  options?: RegistrationOptions
): Promise<ServiceWorkerRegistration | null> {
  if (
    typeof window === 'undefined' ||
    typeof navigator === 'undefined' ||
    !('serviceWorker' in navigator)
  ) {
    console.warn('[Push] Service workers are not supported in this environment.');
    return null;
  }

  const scope = options?.scope ?? getServiceWorkerScope();

  try {
    const registration = await navigator.serviceWorker.register(swPath, {
      ...options,
      scope,
    });
    return registration;
  } catch (error) {
    console.error('[Push] Failed to register service worker at', swPath, error);
    return null;
  }
}

/**
 * Safely serializes a browser PushSubscription into the backend PushSubscriptionRequest format.
 */
export function serializePushSubscription(
  subscription: PushSubscription
): PushSubscriptionRequest {
  if (!subscription) {
    throw new Error('PushSubscription is required');
  }

  const json = typeof subscription.toJSON === 'function' ? subscription.toJSON() : null;
  const endpoint = subscription.endpoint || json?.endpoint;
  const p256dh = json?.keys?.p256dh;
  const auth = json?.keys?.auth;

  if (!endpoint || typeof endpoint !== 'string' || endpoint.trim() === '') {
    throw new Error('Invalid PushSubscription: endpoint is missing or empty');
  }

  if (!p256dh || typeof p256dh !== 'string' || p256dh.trim() === '') {
    throw new Error('Invalid PushSubscription: keys.p256dh is missing or empty');
  }

  if (!auth || typeof auth !== 'string' || auth.trim() === '') {
    throw new Error('Invalid PushSubscription: keys.auth is missing or empty');
  }

  return {
    endpoint: endpoint.trim(),
    keys: {
      p256dh: p256dh.trim(),
      auth: auth.trim(),
    },
  };
}

/**
 * Retrieves the VAPID public key from the backend.
 */
export async function getVapidPublicKey(): Promise<string> {
  return await pushApi.getPublicKey();
}

/**
 * Registers a push subscription with the backend.
 */
export async function registerPushSubscription(
  subscription: PushSubscription | PushSubscriptionRequest
): Promise<PushSubscriptionResponse> {
  const payload =
    'keys' in subscription && 'endpoint' in subscription && typeof subscription.endpoint === 'string'
      ? (subscription as PushSubscriptionRequest)
      : serializePushSubscription(subscription as PushSubscription);

  return await pushApi.registerSubscription(payload);
}

/**
 * Deactivates a push subscription on the backend by endpoint.
 */
export async function deactivatePushSubscription(endpoint: string): Promise<void> {
  if (!endpoint || typeof endpoint !== 'string' || endpoint.trim() === '') {
    throw new Error('Endpoint is required to deactivate push subscription');
  }
  await pushApi.deleteSubscription({ endpoint: endpoint.trim() });
}

/**
 * Returns the current browser PushSubscription if one exists, or null.
 */
export async function getExistingSubscription(): Promise<PushSubscription | null> {
  if (!isPushSupported()) {
    return null;
  }

  try {
    const scope = getServiceWorkerScope();
    const registration =
      (await navigator.serviceWorker.getRegistration(scope)) ||
      (await navigator.serviceWorker.getRegistration()) ||
      (await navigator.serviceWorker.ready);
    if (!registration) {
      return null;
    }
    return await registration.pushManager.getSubscription();
  } catch (error) {
    console.error('[Push] Failed to check existing subscription:', error);
    return null;
  }
}

/**
 * Subscribes the browser to Web Push using the VAPID public key and registers with the backend.
 * NOTE: Does NOT call Notification.requestPermission(). Permission must already be granted.
 */
export async function subscribeToPush(): Promise<PushSubscriptionResponse | null> {
  if (!isPushSupported()) {
    throw new Error('Web Push is not supported in this browser.');
  }

  if (Notification.permission !== 'granted') {
    throw new Error(
      `Notification permission is not granted. Current permission state: ${Notification.permission}`
    );
  }

  const scope = getServiceWorkerScope();
  let registration: ServiceWorkerRegistration | null | undefined =
    (await navigator.serviceWorker.getRegistration(scope)) ||
    (await navigator.serviceWorker.getRegistration());
  if (!registration) {
    registration = await registerPushServiceWorker();
  }

  if (!registration) {
    throw new Error('Failed to obtain service worker registration for push subscription.');
  }

  const publicKey = await getVapidPublicKey();
  const applicationServerKey = urlBase64ToUint8Array(publicKey);

  let existingSub: PushSubscription | null = null;
  try {
    existingSub = await registration.pushManager.getSubscription();
  } catch {
    // ignore
  }

  if (existingSub) {
    try {
      const serialized = serializePushSubscription(existingSub);
      return await pushApi.registerSubscription(serialized);
    } catch (err) {
      console.warn(
        '[Push] Existing subscription could not be registered with current account. Unsubscribing to generate a fresh subscription...',
        err
      );
      try {
        await existingSub.unsubscribe();
      } catch {
        // ignore
      }
    }
  }

  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey,
  });

  const serialized = serializePushSubscription(subscription);
  return await pushApi.registerSubscription(serialized);
}

/**
 * Deactivates the subscription on the backend and unsubscribes the browser PushSubscription.
 */
export async function unsubscribeFromPush(): Promise<boolean> {
  if (!isPushSupported()) {
    return false;
  }

  try {
    const scope = getServiceWorkerScope();
    const registration =
      (await navigator.serviceWorker.getRegistration(scope)) ||
      (await navigator.serviceWorker.getRegistration());
    if (!registration) {
      return false;
    }

    const subscription = await registration.pushManager.getSubscription();
    if (!subscription) {
      return false;
    }

    // First deactivate on backend
    await deactivatePushSubscription(subscription.endpoint);

    // Then unsubscribe in browser
    return await subscription.unsubscribe();
  } catch (error) {
    console.error('[Push] Failed to unsubscribe from push:', error);
    return false;
  }
}
