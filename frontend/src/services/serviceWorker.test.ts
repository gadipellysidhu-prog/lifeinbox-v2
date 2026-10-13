import { describe, it, expect, vi, beforeEach } from 'vitest';
import fs from 'fs';
import path from 'path';

describe('Service Worker (public/sw.js) push notification display & click handling', () => {
  let listeners: Record<string, (event: any) => void>;
  let mockRegistration: { showNotification: any; scope?: string };
  let mockClients: { claim: any; matchAll: any; openWindow: any };
  let mockSelf: any;

  let swExports: any = {};

  function loadServiceWorker(customSelfOverrides?: Partial<any>) {
    listeners = {};
    mockRegistration = {
      showNotification: vi.fn().mockResolvedValue(undefined),
      scope: 'https://lifeinbox.app/',
    };
    mockClients = {
      claim: vi.fn().mockResolvedValue(undefined),
      matchAll: vi.fn().mockResolvedValue([]),
      openWindow: vi.fn().mockResolvedValue({}),
    };
    mockSelf = {
      location: { origin: 'https://lifeinbox.app' },
      addEventListener: vi.fn((event: string, fn: any) => {
        listeners[event] = fn;
      }),
      skipWaiting: vi.fn(),
      registration: mockRegistration,
      clients: mockClients,
      ...customSelfOverrides,
    };

    const swPath = path.resolve(process.cwd(), 'public/sw.js');
    const swCode = fs.readFileSync(swPath, 'utf-8');

    // Execute service worker script in mock environment with module support
    const mockModule = { exports: {} };
    const runner = new Function('self', 'module', swCode);
    runner(mockSelf, mockModule);
    swExports = mockModule.exports;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    loadServiceWorker();
  });

  it('registers install, activate, push, and notificationclick listeners on startup', () => {
    expect(listeners['install']).toBeDefined();
    expect(listeners['activate']).toBeDefined();
    expect(listeners['push']).toBeDefined();
    expect(listeners['notificationclick']).toBeDefined();
  });

  it('handles install by invoking self.skipWaiting()', () => {
    const event = {};
    listeners['install'](event);
    expect(mockSelf.skipWaiting).toHaveBeenCalled();
  });

  it('handles activate by claiming clients via event.waitUntil()', () => {
    const waitUntilSpy = vi.fn();
    listeners['activate']({ waitUntil: waitUntilSpy });
    expect(waitUntilSpy).toHaveBeenCalled();
    expect(mockClients.claim).toHaveBeenCalled();
  });

  describe('Push event payload parsing & notification display', () => {
    it('extracts title, body, and data from valid JSON payload and calls showNotification()', () => {
      const waitUntilSpy = vi.fn();
      const payloadData = { reminderId: '123', route: '/dashboard' };
      const pushEvent = {
        data: {
          json: () => ({
            title: 'LifeInbox Reminder',
            body: 'Your doctor appointment is in 15 minutes',
            data: payloadData,
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(waitUntilSpy).toHaveBeenCalledTimes(1);
      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox Reminder',
        expect.objectContaining({
          body: 'Your doctor appointment is in 15 minutes',
          data: payloadData,
          tag: 'reminder-123',
          silent: false,
          renotify: true,
        })
      );
    });

    it('uses default title "LifeInbox" when title is missing or blank', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            body: 'You have a new update',
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox',
        expect.objectContaining({
          body: 'You have a new update',
        })
      );
    });

    it('uses default body "You have a new notification" when body is missing or blank', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            title: 'Custom Title',
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'Custom Title',
        expect.objectContaining({
          body: 'You have a new notification',
        })
      );
    });

    it('filters out "[object Object]", "undefined", or "null" strings from title and body', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            title: '[object Object]',
            body: 'null',
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox',
        expect.objectContaining({
          body: 'You have a new notification',
        })
      );
    });

    it('preserves complete backend data payload for future click routing', () => {
      const waitUntilSpy = vi.fn();
      const complexData = {
        reminderId: 'task-999',
        action: 'OPEN_REMINDER',
        timestamp: 1770000000,
        nested: { key: 'value' },
      };
      const pushEvent = {
        data: {
          json: () => ({
            title: 'Task Due',
            body: 'Review tax report',
            data: complexData,
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'Task Due',
        expect.objectContaining({
          data: complexData,
        })
      );
    });

    it('handles empty event.data gracefully without crashing', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: null,
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(waitUntilSpy).toHaveBeenCalledTimes(1);
      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox',
        expect.objectContaining({
          body: 'You have a new notification',
          tag: 'lifeinbox-notification',
          silent: false,
          renotify: true,
          data: {},
        })
      );
    });

    it('handles invalid JSON gracefully by falling back to text payload', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => {
            throw new Error('Unexpected token in JSON');
          },
          text: () => 'Plain text push message body',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox',
        expect.objectContaining({
          body: 'Plain text push message body',
          silent: false,
        })
      );
    });

    it('handles completely malformed or throwing data payload without crashing worker', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => {
            throw new Error('Fatal JSON parse error');
          },
          text: () => {
            throw new Error('Fatal text stream error');
          },
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(waitUntilSpy).toHaveBeenCalledTimes(1);
      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'LifeInbox',
        expect.objectContaining({
          body: 'You have a new notification',
          silent: false,
        })
      );
    });

    it('ensures silent is always false to allow native OS/browser sound playback', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            title: 'Sound Test',
            body: 'Ensure sound is enabled',
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'Sound Test',
        expect.objectContaining({
          silent: false,
        })
      );
    });

    it('uses custom data.tag when present in data object', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            title: 'Tagged Notification',
            body: 'Testing custom tag',
            data: { tag: 'daily-digest-group' },
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'Tagged Notification',
        expect.objectContaining({
          tag: 'daily-digest-group',
        })
      );
    });

    it('bounds oversized title (>256 chars), body (>1024 chars), and tag (>128 chars) safely', () => {
      const waitUntilSpy = vi.fn();
      const longTitle = 'T'.repeat(300);
      const longBody = 'B'.repeat(1200);
      const longTag = 'tag-'.repeat(50); // 200 chars

      const pushEvent = {
        data: {
          json: () => ({
            title: longTitle,
            body: longBody,
            data: { tag: longTag },
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        expect.stringMatching(/^T{253}\.\.\.$/),
        expect.objectContaining({
          body: expect.stringMatching(/^B{1021}\.\.\.$/),
          tag: longTag.substring(0, 128),
        })
      );
    });

    it('applies icon and badge when provided in payload', () => {
      const waitUntilSpy = vi.fn();
      const pushEvent = {
        data: {
          json: () => ({
            title: 'Icon Test',
            body: 'Testing icon',
            icon: '/custom-icon.png',
            badge: '/custom-badge.png',
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        'Icon Test',
        expect.objectContaining({
          icon: '/custom-icon.png',
          badge: '/custom-badge.png',
        })
      );
    });

    it('bounds excessively large title and body strings to prevent resource exhaustion', () => {
      const waitUntilSpy = vi.fn();
      const longTitle = 'T'.repeat(300);
      const longBody = 'B'.repeat(2000);
      const pushEvent = {
        data: {
          json: () => ({
            title: longTitle,
            body: longBody,
            data: { tag: 'G'.repeat(200) },
          }),
          text: () => '',
        },
        waitUntil: waitUntilSpy,
      };

      listeners['push'](pushEvent);

      expect(mockRegistration.showNotification).toHaveBeenCalledWith(
        longTitle.substring(0, 253) + '...',
        expect.objectContaining({
          body: longBody.substring(0, 1021) + '...',
          tag: 'G'.repeat(128),
        })
      );
    });
  });

  describe('Notification click handling (Milestone 8)', () => {
    it('closes the notification when clicked and uses event.waitUntil()', async () => {
      const closeSpy = vi.fn();
      let capturedPromise: Promise<any> | null = null;
      const waitUntilSpy = vi.fn((p) => {
        capturedPromise = p;
      });

      const clickEvent = {
        notification: {
          close: closeSpy,
          data: { route: '/dashboard' },
        },
        waitUntil: waitUntilSpy,
      };

      listeners['notificationclick'](clickEvent);

      expect(closeSpy).toHaveBeenCalledTimes(1);
      expect(waitUntilSpy).toHaveBeenCalledTimes(1);
      await capturedPromise;
    });

    it('detects existing window client, navigates to target URL, and focuses it without opening new tab', async () => {
      const navigateSpy = vi.fn().mockResolvedValue(undefined);
      const focusSpy = vi.fn().mockResolvedValue(undefined);
      const existingClient = {
        url: 'https://lifeinbox.app/',
        navigate: navigateSpy,
        focus: focusSpy,
      };

      mockClients.matchAll.mockResolvedValueOnce([existingClient]);

      let capturedPromise: Promise<any> | null = null;
      const clickEvent = {
        notification: {
          close: vi.fn(),
          data: { route: '/dashboard' },
        },
        waitUntil: (p: any) => {
          capturedPromise = p;
        },
      };

      listeners['notificationclick'](clickEvent);
      await capturedPromise;

      expect(mockClients.matchAll).toHaveBeenCalledWith({
        type: 'window',
        includeUncontrolled: true,
      });
      expect(navigateSpy).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      expect(focusSpy).toHaveBeenCalledTimes(1);
      expect(mockClients.openWindow).not.toHaveBeenCalled();
    });

    it('does not re-navigate existing client if already on target URL, but focuses it', async () => {
      const navigateSpy = vi.fn();
      const focusSpy = vi.fn().mockResolvedValue(undefined);
      const existingClient = {
        url: 'https://lifeinbox.app/dashboard',
        navigate: navigateSpy,
        focus: focusSpy,
      };

      mockClients.matchAll.mockResolvedValueOnce([existingClient]);

      let capturedPromise: Promise<any> | null = null;
      const clickEvent = {
        notification: {
          close: vi.fn(),
          data: { route: '/dashboard' },
        },
        waitUntil: (p: any) => {
          capturedPromise = p;
        },
      };

      listeners['notificationclick'](clickEvent);
      await capturedPromise;

      expect(navigateSpy).not.toHaveBeenCalled();
      expect(focusSpy).toHaveBeenCalledTimes(1);
      expect(mockClients.openWindow).not.toHaveBeenCalled();
    });

    it('opens new window with target URL when no matching client exists', async () => {
      mockClients.matchAll.mockResolvedValueOnce([]);

      let capturedPromise: Promise<any> | null = null;
      const clickEvent = {
        notification: {
          close: vi.fn(),
          data: { route: '/dashboard' },
        },
        waitUntil: (p: any) => {
          capturedPromise = p;
        },
      };

      listeners['notificationclick'](clickEvent);
      await capturedPromise;

      expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
    });

    describe('Target URL resolution & fallback', () => {
      it('falls back to /dashboard when notification data has no route', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: {},
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('falls back to /dashboard when notification data contains reminderId without route', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { reminderId: 'reminder-abc-123' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('falls back to /dashboard when route is blank or non-string', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '   ' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });
    });

    describe('Security against open redirect attacks', () => {
      it('rejects external HTTPS URL and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: 'https://malicious-site.example/phish' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects external HTTP URL and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: 'http://malicious-site.example' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects protocol-relative URL ("//evil.example") and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '//evil.example/login' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects javascript: URL and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: 'javascript:alert(document.cookie)' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects data: URL and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: 'data:text/html,<script>alert(1)</script>' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects URLs containing backslashes (e.g. "/\\\\evil.com" or "/\\evil.com") and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/\\evil.example/login' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects URLs containing control characters or null bytes and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/dashboard\r\nSet-Cookie:malicious=1' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('rejects candidate routes exceeding maximum length (>512 chars) and falls back to /dashboard', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/' + 'a'.repeat(600) },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });
    });

    describe('Resilience and error handling', () => {
      it('handles null notification object without throwing', async () => {
        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: null,
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        expect(() => {
          listeners['notificationclick'](clickEvent);
        }).not.toThrow();

        await capturedPromise;
        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('handles malformed notification data gracefully without crashing', async () => {
        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: 'malformed-string-instead-of-object',
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        expect(() => {
          listeners['notificationclick'](clickEvent);
        }).not.toThrow();

        await capturedPromise;
        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('handles client navigation failure gracefully and proceeds to focus', async () => {
        const focusSpy = vi.fn().mockResolvedValue(undefined);
        const existingClient = {
          url: 'https://lifeinbox.app/',
          navigate: vi.fn().mockRejectedValue(new Error('Navigation blocked')),
          focus: focusSpy,
        };

        mockClients.matchAll.mockResolvedValueOnce([existingClient]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/dashboard' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(focusSpy).toHaveBeenCalledTimes(1);
      });

      it('falls back to openWindow when client focus fails', async () => {
        const existingClient = {
          url: 'https://lifeinbox.app/',
          navigate: vi.fn().mockResolvedValue(undefined),
          focus: vi.fn().mockRejectedValue(new Error('Focus rejected by OS')),
        };

        mockClients.matchAll.mockResolvedValueOnce([existingClient]);

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/dashboard' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        listeners['notificationclick'](clickEvent);
        await capturedPromise;

        expect(mockClients.openWindow).toHaveBeenCalledWith('https://lifeinbox.app/dashboard');
      });

      it('handles openWindow failure without throwing or crashing worker', async () => {
        mockClients.matchAll.mockResolvedValueOnce([]);
        mockClients.openWindow.mockRejectedValueOnce(new Error('Popup blocked by browser'));

        let capturedPromise: Promise<any> | null = null;
        const clickEvent = {
          notification: {
            close: vi.fn(),
            data: { route: '/dashboard' },
          },
          waitUntil: (p: any) => {
            capturedPromise = p;
          },
        };

        expect(() => {
          listeners['notificationclick'](clickEvent);
        }).not.toThrow();

        await capturedPromise;
      });
    });
  });

  describe('GitHub Pages and custom BASE_URL subpath support', () => {
    const ghPagesScope = 'https://gadipellysidhu-prog.github.io/lifeinbox-v2/';
    const ghPagesOrigin = 'https://gadipellysidhu-prog.github.io';

    beforeEach(() => {
      loadServiceWorker({
        location: { origin: ghPagesOrigin },
        registration: {
          showNotification: vi.fn().mockResolvedValue(undefined),
          scope: ghPagesScope,
        },
      });
    });

    it('extracts correct scopeBasePath from self.registration.scope', () => {
      expect(swExports.getScopeBasePath()).toBe('/lifeinbox-v2/');
    });

    it('resolves notification target URL prefixed with scopeBasePath for relative routes', () => {
      const url = swExports.resolveNotificationTargetUrl({ route: '/dashboard' }, ghPagesOrigin);
      expect(url).toBe('https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard');
    });

    it('does not duplicate scopeBasePath if candidate route already includes it', () => {
      const url = swExports.resolveNotificationTargetUrl(
        { route: '/lifeinbox-v2/reminders/456' },
        ghPagesOrigin
      );
      expect(url).toBe('https://gadipellysidhu-prog.github.io/lifeinbox-v2/reminders/456');
    });

    it('falls back to scoped /dashboard when route is absent or only reminderId is present', () => {
      const fallbackUrl1 = swExports.resolveNotificationTargetUrl({}, ghPagesOrigin);
      expect(fallbackUrl1).toBe('https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard');

      const fallbackUrl2 = swExports.resolveNotificationTargetUrl(
        { reminderId: 'rem-789' },
        ghPagesOrigin
      );
      expect(fallbackUrl2).toBe('https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard');
    });

    it('rejects external URLs and returns scoped fallback', () => {
      const maliciousUrl = swExports.resolveNotificationTargetUrl(
        { route: 'https://attacker.example.com/steal' },
        ghPagesOrigin
      );
      expect(maliciousUrl).toBe('https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard');
    });

    it('prefixes root-relative icon and badge with scopeBasePath in parsePushPayload', () => {
      const eventData = {
        json: () => ({
          title: 'Scoped Notification',
          body: 'Testing icons',
          icon: '/favicon.ico',
          badge: '/badge.png',
        }),
      };

      const { options } = swExports.parsePushPayload(eventData);
      expect(options.icon).toBe('/lifeinbox-v2/favicon.ico');
      expect(options.badge).toBe('/lifeinbox-v2/badge.png');
    });

    it('does not duplicate prefix if icon already begins with scopeBasePath', () => {
      const eventData = {
        json: () => ({
          title: 'Scoped Notification',
          body: 'Testing icon',
          icon: '/lifeinbox-v2/favicon.ico',
        }),
      };

      const { options } = swExports.parsePushPayload(eventData);
      expect(options.icon).toBe('/lifeinbox-v2/favicon.ico');
    });

    it('navigates and opens window with scoped URL on notificationclick event', async () => {
      mockClients.matchAll.mockResolvedValueOnce([]);

      let capturedPromise: Promise<any> | null = null;
      const clickEvent = {
        notification: {
          close: vi.fn(),
          data: { route: '/dashboard' },
        },
        waitUntil: (p: any) => {
          capturedPromise = p;
        },
      };

      listeners['notificationclick'](clickEvent);
      await capturedPromise;

      expect(mockClients.openWindow).toHaveBeenCalledWith(
        'https://gadipellysidhu-prog.github.io/lifeinbox-v2/dashboard'
      );
    });
  });
});

