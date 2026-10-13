import { test, expect } from '@playwright/test';

test.describe('LifeInbox Web Push End-to-End Workflow', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept backend API calls with deterministic test responses
    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'test-e2e-jwt-token',
          user: {
            id: 'e2e-user-1',
            name: 'Sarah Connor',
            email: 'sarah@example.com',
            createdAt: '2026-10-11T12:00:00Z',
          },
        }),
      });
    });

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'e2e-user-1',
          name: 'Sarah Connor',
          email: 'sarah@example.com',
          createdAt: '2026-10-11T12:00:00Z',
        }),
      });
    });

    await page.route('**/api/reminders**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'rem-1',
            title: 'Prepare defense report',
            description: 'Check security safeguards',
            dueAt: new Date(Date.now() + 3600000).toISOString(),
            priority: 'HIGH',
            status: 'PENDING',
            createdAt: new Date().toISOString(),
          },
        ]),
      });
    });

    await page.route('**/api/push/public-key', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicKey: 'BEl62iUYgUivxIkv69yViEuiBIa-M9-26GoSlTk2PRtv5bVn5GCuh6h3BQ9gkUidrgrgWv9T2VOyzU02079675g',
          enabled: true,
        }),
      });
    });

    await page.route('**/api/push/subscriptions', async (route) => {
      if (route.request().method() === 'POST') {
        const payload = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'sub-e2e-1',
            endpoint: payload.endpoint || 'https://fcm.googleapis.com/fcm/send/e2e-token',
            active: true,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        });
      } else {
        await route.fulfill({ status: 204 });
      }
    });

    await page.route('**/api/notification-preferences**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          pushNotificationsEnabled: true,
          emailNotificationsEnabled: true,
          reminderEmailsEnabled: true,
          dailyDigestEnabled: false,
          dailyDigestTime: '08:00:00',
          timezone: 'UTC',
        }),
      });
    });

    // Provide mocked browser Notification and PushManager APIs for deterministic E2E execution
    await page.addInitScript(() => {
      let currentPermission = 'default';

      const mockPushSubscription = {
        endpoint: 'https://fcm.googleapis.com/fcm/send/mock-e2e-token',
        toJSON: () => ({
          endpoint: 'https://fcm.googleapis.com/fcm/send/mock-e2e-token',
          keys: {
            p256dh: 'BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QT9Ps0vE',
            auth: 'tBHItJI5svbpez7KI4CCXg',
          },
        }),
        unsubscribe: async () => true,
      };

      const mockRegistration = {
        scope: '/',
        active: { state: 'activated' },
        pushManager: {
          getSubscription: async () => null,
          subscribe: async () => mockPushSubscription,
        },
        showNotification: async (title: string, options: any) => {},
      };

      if (!('Notification' in window)) {
        (window as any).Notification = {};
      }

      Object.defineProperty(window.Notification, 'permission', {
        get: () => currentPermission,
        configurable: true,
      });

      window.Notification.requestPermission = async () => {
        currentPermission = 'granted';
        return 'granted';
      };

      if (navigator.serviceWorker) {
        navigator.serviceWorker.register = async () => mockRegistration as any;
        navigator.serviceWorker.getRegistration = async () => mockRegistration as any;
      }
    });
  });

  test('User logs in, navigates to dashboard, and views reminder dashboard', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('h1')).toContainText('Welcome back');

    await page.fill('input[type="email"]', 'sarah@example.com');
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');

    // Should navigate to dashboard
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('h1')).toContainText('All Reminders');
    await expect(page.getByText('Hello, Sarah Connor.')).toBeVisible();
    await expect(page.getByText('Prepare defense report')).toBeVisible();
  });

  test('Notification permission prompt appears on dashboard and user can enable push notifications', async ({ page }) => {
    // Inject auth state directly to test authenticated dashboard view
    await page.addInitScript(() => {
      localStorage.setItem('lifeinbox_token', 'test-e2e-jwt-token');
      localStorage.setItem(
        'lifeinbox_user',
        JSON.stringify({
          id: 'e2e-user-1',
          name: 'Sarah Connor',
          email: 'sarah@example.com',
          createdAt: '2026-10-11T12:00:00Z',
        })
      );
    });

    await page.goto('/dashboard');

    // Prompt banner should be visible
    const promptBanner = page.getByText('Stay on top of your reminders');
    await expect(promptBanner).toBeVisible();

    const enableButton = page.getByRole('button', { name: /Enable Notifications/i });
    await expect(enableButton).toBeVisible();

    // Click Enable Notifications
    await enableButton.click();

    // Once granted and subscribed, the invitation banner automatically dismisses
    await expect(page.getByText('Stay on top of your reminders')).not.toBeVisible();
  });

  test('User can dismiss the permission prompt and dismissal is persisted', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('lifeinbox_token', 'test-e2e-jwt-token');
      localStorage.setItem(
        'lifeinbox_user',
        JSON.stringify({
          id: 'e2e-user-1',
          name: 'Sarah Connor',
          email: 'sarah@example.com',
          createdAt: '2026-10-11T12:00:00Z',
        })
      );
    });

    await page.goto('/dashboard');

    const promptBanner = page.getByText('Stay on top of your reminders');
    await expect(promptBanner).toBeVisible();

    // Click "Not Now" button
    const notNowButton = page.getByRole('button', { name: /Not Now/i });
    await notNowButton.click();

    // Banner should disappear immediately
    await expect(promptBanner).not.toBeVisible();

    // Reloading page preserves dismissal for this user
    await page.reload();
    await expect(page.getByText('Stay on top of your reminders')).not.toBeVisible();
  });

  test('Notification click routing resolves internal routes and prevents open redirects in browser context', async ({ page }) => {
    await page.goto('/dashboard');

    const routingResults = await page.evaluate(async () => {
      // Fetch the actual production sw.js script served by Vite
      const response = await fetch('/sw.js');
      const swCode = await response.text();

      // Execute in isolated function context to access resolveNotificationTargetUrl
      const fn = new Function(`
        ${swCode};
        return {
          resolveNotificationTargetUrl: resolveNotificationTargetUrl
        };
      `);
      const { resolveNotificationTargetUrl } = fn();
      const origin = window.location.origin;

      return {
        validDashboard: resolveNotificationTargetUrl({ route: '/dashboard' }, origin),
        reminderRoute: resolveNotificationTargetUrl({ reminderId: '123' }, origin),
        externalHttps: resolveNotificationTargetUrl({ route: 'https://evil.example/phish' }, origin),
        protocolRelative: resolveNotificationTargetUrl({ route: '//evil.example' }, origin),
        backslashBypass: resolveNotificationTargetUrl({ route: '/\\evil.example' }, origin),
        javascriptScheme: resolveNotificationTargetUrl({ route: 'javascript:alert(1)' }, origin),
      };
    });

    const expectedDashboardUrl = `${new URL(page.url()).origin}/dashboard`;

    expect(routingResults.validDashboard).toBe(expectedDashboardUrl);
    expect(routingResults.reminderRoute).toBe(expectedDashboardUrl);
    expect(routingResults.externalHttps).toBe(expectedDashboardUrl);
    expect(routingResults.protocolRelative).toBe(expectedDashboardUrl);
    expect(routingResults.backslashBypass).toBe(expectedDashboardUrl);
    expect(routingResults.javascriptScheme).toBe(expectedDashboardUrl);
  });
});
