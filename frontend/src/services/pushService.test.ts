import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  isPushSupported,
  urlBase64ToUint8Array,
  registerPushServiceWorker,
  serializePushSubscription,
  registerPushSubscription,
  deactivatePushSubscription,
  getExistingSubscription,
  subscribeToPush,
  unsubscribeFromPush,
  getServiceWorkerScope,
  getServiceWorkerUrl,
} from './pushService';
import { pushApi } from '../api/pushApi';

vi.mock('../api/pushApi', () => ({
  pushApi: {
    getPublicKey: vi.fn(),
    registerSubscription: vi.fn(),
    deleteSubscription: vi.fn(),
  },
}));

describe('pushService', () => {
  const originalServiceWorker = (navigator as unknown as { serviceWorker?: unknown }).serviceWorker;
  const originalNotification = window.Notification;
  const originalPushManager = window.PushManager;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    // Restore global properties if altered
    Object.defineProperty(navigator, 'serviceWorker', {
      value: originalServiceWorker,
      writable: true,
      configurable: true,
    });
    Object.defineProperty(window, 'Notification', {
      value: originalNotification,
      writable: true,
      configurable: true,
    });
    Object.defineProperty(window, 'PushManager', {
      value: originalPushManager,
      writable: true,
      configurable: true,
    });
  });

  describe('isPushSupported', () => {
    it('returns true when serviceWorker, PushManager, and Notification exist', () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {},
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      expect(isPushSupported()).toBe(true);
    });

    it('returns false when serviceWorker is not supported', () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: undefined,
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      expect(isPushSupported()).toBe(false);
    });

    it('returns false when PushManager is missing', () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {},
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: undefined,
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      expect(isPushSupported()).toBe(false);
    });

    it('returns false when Notification is missing', () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {},
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: undefined,
        configurable: true,
      });

      expect(isPushSupported()).toBe(false);
    });
  });

  describe('urlBase64ToUint8Array', () => {
    it('converts valid standard base64 correctly', () => {
      // "hello" in base64 is "aGVsbG8="
      const result = urlBase64ToUint8Array('aGVsbG8=');
      const text = String.fromCharCode(...result);
      expect(text).toBe('hello');
    });

    it('handles URL-safe characters (- and _) and missing padding', () => {
      // Test URL safe encoding where - replaces + and _ replaces /
      // Binary [251, 239] in standard base64 is ++8=, in base64url is --8
      const urlSafe = '--8';
      const result = urlBase64ToUint8Array(urlSafe);
      expect(result).toBeInstanceOf(Uint8Array);
      expect(result.length).toBe(2);
      expect(result[0]).toBe(251);
      expect(result[1]).toBe(239);
    });

    it('handles unpadded base64 string', () => {
      // "any car" -> "YW55IGNhcg==" without padding is "YW55IGNhcg"
      const result = urlBase64ToUint8Array('YW55IGNhcg');
      const text = String.fromCharCode(...result);
      expect(text).toBe('any car');
    });

    it('rejects empty, null, or invalid input', () => {
      expect(() => urlBase64ToUint8Array('')).toThrow('Invalid base64 string provided');
      expect(() => urlBase64ToUint8Array('   ')).toThrow('Invalid base64 string provided');
      // Invalid characters that fail atob
      expect(() => urlBase64ToUint8Array('!!!invalid base64!!!')).toThrow(
        /Failed to decode base64 string/
      );
    });
  });

  describe('serializePushSubscription', () => {
    it('converts a valid PushSubscription to the backend request format', () => {
      const mockSub = {
        endpoint: 'https://fcm.googleapis.com/fcm/send/test-token',
        toJSON: () => ({
          endpoint: 'https://fcm.googleapis.com/fcm/send/test-token',
          keys: {
            p256dh: 'BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QT9Ps0vE',
            auth: 'tBHItJI5svbpez7KI4CCXg',
          },
        }),
      } as unknown as PushSubscription;

      const serialized = serializePushSubscription(mockSub);

      expect(serialized).toEqual({
        endpoint: 'https://fcm.googleapis.com/fcm/send/test-token',
        keys: {
          p256dh: 'BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QT9Ps0vE',
          auth: 'tBHItJI5svbpez7KI4CCXg',
        },
      });
    });

    it('throws when endpoint is missing or blank', () => {
      const mockSub = {
        endpoint: '',
        toJSON: () => ({
          endpoint: '',
          keys: { p256dh: 'p256dh-val', auth: 'auth-val' },
        }),
      } as unknown as PushSubscription;

      expect(() => serializePushSubscription(mockSub)).toThrow(
        /Invalid PushSubscription: endpoint is missing or empty/
      );
    });

    it('throws when p256dh is missing or blank', () => {
      const mockSub = {
        endpoint: 'https://push.example.com',
        toJSON: () => ({
          endpoint: 'https://push.example.com',
          keys: { p256dh: '', auth: 'auth-val' },
        }),
      } as unknown as PushSubscription;

      expect(() => serializePushSubscription(mockSub)).toThrow(
        /Invalid PushSubscription: keys.p256dh is missing or empty/
      );
    });

    it('throws when auth key is missing or blank', () => {
      const mockSub = {
        endpoint: 'https://push.example.com',
        toJSON: () => ({
          endpoint: 'https://push.example.com',
          keys: { p256dh: 'p256dh-val' },
        }),
      } as unknown as PushSubscription;

      expect(() => serializePushSubscription(mockSub)).toThrow(
        /Invalid PushSubscription: keys.auth is missing or empty/
      );
    });
  });

  describe('getServiceWorkerScope & getServiceWorkerUrl', () => {
    it('returns BASE_URL normalized with trailing slash for scope', () => {
      const scope = getServiceWorkerScope();
      expect(scope).toBe('/lifeinbox-v2/');
      expect(scope.endsWith('/')).toBe(true);
    });

    it('returns BASE_URL joined with sw.js for service worker URL', () => {
      const url = getServiceWorkerUrl();
      expect(url).toBe('/lifeinbox-v2/sw.js');
    });
  });

  describe('registerPushServiceWorker', () => {
    it('registers with default BASE_URL path and scope when called without arguments', async () => {
      const mockRegistration = { scope: '/lifeinbox-v2/' } as ServiceWorkerRegistration;
      const registerSpy = vi.fn().mockResolvedValue(mockRegistration);
      Object.defineProperty(navigator, 'serviceWorker', {
        value: { register: registerSpy },
        configurable: true,
      });

      const result = await registerPushServiceWorker();

      expect(registerSpy).toHaveBeenCalledWith('/lifeinbox-v2/sw.js', {
        scope: '/lifeinbox-v2/',
      });
      expect(result).toBe(mockRegistration);
    });

    it('registers custom swPath with specified options override', async () => {
      const mockRegistration = { scope: '/custom/' } as ServiceWorkerRegistration;
      const registerSpy = vi.fn().mockResolvedValue(mockRegistration);
      Object.defineProperty(navigator, 'serviceWorker', {
        value: { register: registerSpy },
        configurable: true,
      });

      const result = await registerPushServiceWorker('/custom/sw.js', { scope: '/custom/' });

      expect(registerSpy).toHaveBeenCalledWith('/custom/sw.js', { scope: '/custom/' });
      expect(result).toBe(mockRegistration);
    });

    it('handles registration failure gracefully without throwing', async () => {
      const registerSpy = vi.fn().mockRejectedValue(new Error('SW failed'));
      Object.defineProperty(navigator, 'serviceWorker', {
        value: { register: registerSpy },
        configurable: true,
      });

      const result = await registerPushServiceWorker();

      expect(result).toBeNull();
    });

    it('returns null if serviceWorker is missing from navigator', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: undefined,
        configurable: true,
      });

      const result = await registerPushServiceWorker();

      expect(result).toBeNull();
    });
  });

  describe('registerPushSubscription & deactivatePushSubscription', () => {
    it('registerPushSubscription delegates to pushApi.registerSubscription', async () => {
      const payload = {
        endpoint: 'https://push.example.com/endpoint1',
        keys: { p256dh: 'pKey', auth: 'aKey' },
      };
      const mockResponse = {
        id: 'id-1',
        endpoint: payload.endpoint,
        active: true,
        createdAt: '2026-10-11T00:00:00Z',
        updatedAt: '2026-10-11T00:00:00Z',
      };
      vi.mocked(pushApi.registerSubscription).mockResolvedValueOnce(mockResponse);

      const result = await registerPushSubscription(payload);

      expect(pushApi.registerSubscription).toHaveBeenCalledWith(payload);
      expect(result).toEqual(mockResponse);
    });

    it('deactivatePushSubscription calls pushApi.deleteSubscription with endpoint', async () => {
      vi.mocked(pushApi.deleteSubscription).mockResolvedValueOnce();

      await deactivatePushSubscription('https://push.example.com/endpoint1');

      expect(pushApi.deleteSubscription).toHaveBeenCalledWith({
        endpoint: 'https://push.example.com/endpoint1',
      });
    });

    it('deactivatePushSubscription throws if endpoint is blank', async () => {
      await expect(deactivatePushSubscription('')).rejects.toThrow('Endpoint is required');
    });
  });

  describe('subscribeToPush', () => {
    it('throws if Web Push is not supported in environment', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: undefined,
        configurable: true,
      });

      await expect(subscribeToPush()).rejects.toThrow(
        'Web Push is not supported in this browser.'
      );
    });

    it('throws if Notification.permission is not granted without prompting', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {},
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: { permission: 'default' },
        configurable: true,
      });

      await expect(subscribeToPush()).rejects.toThrow(
        /Notification permission is not granted. Current permission state: default/
      );
    });

    it('subscribes successfully when permission is already granted', async () => {
      const mockSub = {
        endpoint: 'https://push.test/sub-1',
        toJSON: () => ({
          endpoint: 'https://push.test/sub-1',
          keys: { p256dh: 'p256dh-token', auth: 'auth-token' },
        }),
      };
      const pushManagerMock = {
        getSubscription: vi.fn().mockResolvedValue(null),
        subscribe: vi.fn().mockResolvedValue(mockSub),
      };
      const regMock = {
        pushManager: pushManagerMock,
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          register: vi.fn().mockResolvedValue(regMock),
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: { permission: 'granted' },
        configurable: true,
      });

      vi.mocked(pushApi.getPublicKey).mockResolvedValueOnce('aGVsbG8=');
      vi.mocked(pushApi.registerSubscription).mockResolvedValueOnce({
        id: 'sub-id',
        endpoint: 'https://push.test/sub-1',
        active: true,
        createdAt: '2026-10-11T00:00:00Z',
        updatedAt: '2026-10-11T00:00:00Z',
      });

      const res = await subscribeToPush();

      expect(pushManagerMock.subscribe).toHaveBeenCalledWith(
        expect.objectContaining({ userVisibleOnly: true })
      );
      expect(pushApi.registerSubscription).toHaveBeenCalledWith({
        endpoint: 'https://push.test/sub-1',
        keys: { p256dh: 'p256dh-token', auth: 'auth-token' },
      });
      expect(res?.id).toBe('sub-id');
    });

    it('unsubscribes existing subscription and creates a fresh one if existing subscription fails backend registration', async () => {
      const oldSubUnsubscribe = vi.fn().mockResolvedValue(true);
      const oldSub = {
        endpoint: 'https://push.test/old-sub',
        toJSON: () => ({
          endpoint: 'https://push.test/old-sub',
          keys: { p256dh: 'old-p256dh', auth: 'old-auth' },
        }),
        unsubscribe: oldSubUnsubscribe,
      };
      const freshSub = {
        endpoint: 'https://push.test/fresh-sub',
        toJSON: () => ({
          endpoint: 'https://push.test/fresh-sub',
          keys: { p256dh: 'fresh-p256dh', auth: 'fresh-auth' },
        }),
      };

      const pushManagerMock = {
        getSubscription: vi.fn().mockResolvedValue(oldSub),
        subscribe: vi.fn().mockResolvedValue(freshSub),
      };
      const regMock = {
        pushManager: pushManagerMock,
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          register: vi.fn().mockResolvedValue(regMock),
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: { permission: 'granted' },
        configurable: true,
      });

      vi.mocked(pushApi.getPublicKey).mockResolvedValueOnce('aGVsbG8=');
      // First attempt with oldSub fails (e.g. 409 from another user)
      vi.mocked(pushApi.registerSubscription).mockRejectedValueOnce(
        new Error('Push subscription endpoint is already registered by another account')
      );
      // Second attempt with freshSub succeeds
      vi.mocked(pushApi.registerSubscription).mockResolvedValueOnce({
        id: 'fresh-sub-id',
        endpoint: 'https://push.test/fresh-sub',
        active: true,
        createdAt: '2026-10-11T00:00:00Z',
        updatedAt: '2026-10-11T00:00:00Z',
      });

      const res = await subscribeToPush();

      expect(oldSubUnsubscribe).toHaveBeenCalledTimes(1);
      expect(pushManagerMock.subscribe).toHaveBeenCalledWith(
        expect.objectContaining({ userVisibleOnly: true })
      );
      expect(res?.id).toBe('fresh-sub-id');
    });
  });

  describe('unsubscribeFromPush', () => {
    it('deactivates subscription on backend and unsubscribes locally', async () => {
      const unsubscribeSpy = vi.fn().mockResolvedValue(true);
      const subMock = {
        endpoint: 'https://push.test/sub-1',
        unsubscribe: unsubscribeSpy,
      };
      const regMock = {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(subMock),
        },
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      vi.mocked(pushApi.deleteSubscription).mockResolvedValueOnce();

      const result = await unsubscribeFromPush();

      expect(pushApi.deleteSubscription).toHaveBeenCalledWith({
        endpoint: 'https://push.test/sub-1',
      });
      expect(unsubscribeSpy).toHaveBeenCalled();
      expect(result).toBe(true);
    });

    it('returns false if no subscription exists', async () => {
      const regMock = {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(null),
        },
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      const result = await unsubscribeFromPush();

      expect(result).toBe(false);
      expect(pushApi.deleteSubscription).not.toHaveBeenCalled();
    });

    it('returns false when push is not supported in the browser', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: undefined,
        configurable: true,
      });

      const result = await unsubscribeFromPush();
      expect(result).toBe(false);
    });
  });

  describe('getExistingSubscription', () => {
    it('returns null when push is not supported in the browser', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: undefined,
        configurable: true,
      });

      const result = await getExistingSubscription();
      expect(result).toBeNull();
    });

    it('returns null when service worker registration is null', async () => {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          getRegistration: vi.fn().mockResolvedValue(null),
          ready: Promise.resolve(null),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      const result = await getExistingSubscription();
      expect(result).toBeNull();
    });

    it('returns active subscription when pushManager.getSubscription() resolves', async () => {
      const mockSub = { endpoint: 'https://push.example/sub' };
      const regMock = {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(mockSub),
        },
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      const result = await getExistingSubscription();
      expect(result).toBe(mockSub as any);
    });

    it('returns null and catches error gracefully when getSubscription() throws', async () => {
      const regMock = {
        pushManager: {
          getSubscription: vi.fn().mockRejectedValue(new Error('Permission denied by system')),
        },
      };

      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          getRegistration: vi.fn().mockResolvedValue(regMock),
        },
        configurable: true,
      });
      Object.defineProperty(window, 'PushManager', {
        value: class {},
        configurable: true,
      });
      Object.defineProperty(window, 'Notification', {
        value: class {},
        configurable: true,
      });

      const result = await getExistingSubscription();
      expect(result).toBeNull();
    });
  });
});
