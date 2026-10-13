// LifeInbox Service Worker for Web Push Notifications

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

/**
 * Safely parses push event payload and builds notification options.
 * Handles valid JSON, string text, empty data, and malformed inputs.
 */
function parsePushPayload(eventData) {
  let title = 'LifeInbox';
  let body = 'You have a new notification';
  let data = {};
  let icon = undefined;
  let badge = undefined;
  let customTag = undefined;

  if (eventData) {
    let json = null;
    try {
      json = eventData.json();
    } catch {
      try {
        const text = eventData.text();
        if (typeof text === 'string' && text.trim() !== '') {
          body = text.trim();
        }
      } catch {
        // Fall back to default body
      }
    }

    if (json && typeof json === 'object') {
      if (
        typeof json.title === 'string' &&
        json.title.trim() !== '' &&
        json.title !== 'undefined' &&
        json.title !== 'null' &&
        json.title !== '[object Object]'
      ) {
        title = json.title.trim();
        if (title.length > 256) {
          title = title.substring(0, 253) + '...';
        }
      }

      if (
        typeof json.body === 'string' &&
        json.body.trim() !== '' &&
        json.body !== 'undefined' &&
        json.body !== 'null' &&
        json.body !== '[object Object]'
      ) {
        body = json.body.trim();
        if (body.length > 1024) {
          body = body.substring(0, 1021) + '...';
        }
      }

      if (json.data && typeof json.data === 'object' && json.data !== null) {
        data = json.data;
        if (typeof data.tag === 'string' && data.tag.trim() !== '') {
          customTag = data.tag.trim().substring(0, 128);
        } else if (data.reminderId) {
          customTag = ('reminder-' + String(data.reminderId)).substring(0, 128);
        }
      }

      if (typeof json.icon === 'string' && json.icon.trim() !== '') {
        icon = json.icon.trim();
        if (icon.startsWith('/') && !icon.startsWith('//')) {
          const scopeBasePath = getScopeBasePath();
          if (scopeBasePath !== '/' && !icon.startsWith(scopeBasePath)) {
            icon = `${scopeBasePath.replace(/\/+$/, '')}${icon}`;
          }
        }
      }

      if (typeof json.badge === 'string' && json.badge.trim() !== '') {
        badge = json.badge.trim();
        if (badge.startsWith('/') && !badge.startsWith('//')) {
          const scopeBasePath = getScopeBasePath();
          if (scopeBasePath !== '/' && !badge.startsWith(scopeBasePath)) {
            badge = `${scopeBasePath.replace(/\/+$/, '')}${badge}`;
          }
        }
      }
    }
  }

  const options = {
    body: body,
    tag: customTag || 'lifeinbox-notification',
    data: data,
    silent: false,
    renotify: true,
  };

  if (icon) {
    options.icon = icon;
  } else {
    const scopeBasePath = getScopeBasePath();
    options.icon = `${scopeBasePath.replace(/\/+$/, '')}/favicon.ico`;
  }
  if (badge) {
    options.badge = badge;
  }

  return { title, options };
}

self.addEventListener('push', (event) => {
  try {
    const eventData = event ? event.data : null;
    const { title, options } = parsePushPayload(eventData);

    if (event && typeof event.waitUntil === 'function') {
      event.waitUntil(self.registration.showNotification(title, options));
    } else {
      self.registration.showNotification(title, options);
    }
  } catch (error) {
    // Resilient fallback to prevent service worker unhandled crash
    if (event && typeof event.waitUntil === 'function') {
      event.waitUntil(
        self.registration.showNotification('LifeInbox', {
          body: 'You have a new notification',
          tag: 'lifeinbox-notification',
          silent: false,
          renotify: true,
          data: {},
        })
      );
    }
  }
});

/**
 * Extracts the base pathname from the registered service worker scope.
 * For example:
 * - On GitHub Pages: 'https://gadipellysidhu-prog.github.io/lifeinbox-v2/' -> '/lifeinbox-v2/'
 * - Locally: 'http://localhost:5173/' -> '/'
 */
function getScopeBasePath() {
  if (self.registration && typeof self.registration.scope === 'string' && self.registration.scope.trim() !== '') {
    try {
      const scopeUrl = new URL(self.registration.scope);
      return scopeUrl.pathname.endsWith('/') ? scopeUrl.pathname : scopeUrl.pathname + '/';
    } catch {
      // Fallback
    }
  }
  return '/';
}

/**
 * Safely resolves an internal application path from notification data.
 * Protects against open redirect attacks (rejects external URLs, javascript:, data:, //, backslashes, etc.).
 * Automatically prefixes the service worker scope base path if needed (e.g. for GitHub Pages).
 */
function resolveNotificationTargetUrl(data, origin) {
  const scopeBasePath = getScopeBasePath();
  const effectiveOrigin = origin || (self.location && self.location.origin) || '';

  // Helper to ensure path includes scopeBasePath
  function prefixScope(path) {
    if (!path || typeof path !== 'string') return scopeBasePath;
    const cleanPath = path.startsWith('/') ? path : '/' + path;
    if (scopeBasePath === '/' || cleanPath.startsWith(scopeBasePath)) {
      return cleanPath;
    }
    return `${scopeBasePath.replace(/\/+$/, '')}${cleanPath}`;
  }

  const fallbackPath = prefixScope('/dashboard');
  const defaultUrl = new URL(fallbackPath, effectiveOrigin).href;

  if (!data || typeof data !== 'object') {
    return defaultUrl;
  }

  let candidateRoute = data.route;

  // Case B: If reminderId is provided without an explicit route, navigate to the dashboard
  if ((!candidateRoute || typeof candidateRoute !== 'string') && data.reminderId) {
    candidateRoute = fallbackPath;
  }

  if (typeof candidateRoute !== 'string' || candidateRoute.trim() === '') {
    return defaultUrl;
  }

  candidateRoute = candidateRoute.trim();

  // Security checks:
  // 1. Must NOT be excessively long
  if (candidateRoute.length > 512) {
    return defaultUrl;
  }

  // 2. Must NOT contain backslashes (\) to prevent parser bypasses e.g. /\evil.com
  if (candidateRoute.includes('\\')) {
    return defaultUrl;
  }

  // 3. Must NOT contain control characters or null bytes
  if (/[\x00-\x1F\x7F]/.test(candidateRoute)) {
    return defaultUrl;
  }

  // 4. Must NOT start with protocol-relative "//"
  if (candidateRoute.startsWith('//')) {
    return defaultUrl;
  }

  // 5. Must NOT contain scheme e.g. http:, https:, javascript:, data:
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(candidateRoute)) {
    return defaultUrl;
  }

  // 6. Must be a relative path starting with "/"
  if (!candidateRoute.startsWith('/')) {
    return defaultUrl;
  }

  // Prefix scope base path if needed (e.g. /dashboard -> /lifeinbox-v2/dashboard)
  const scopedRoute = prefixScope(candidateRoute);

  try {
    const resolved = new URL(scopedRoute, effectiveOrigin);
    // 7. Must match origin strictly
    if (resolved.origin !== effectiveOrigin) {
      return defaultUrl;
    }
    return resolved.href;
  } catch {
    return defaultUrl;
  }
}

/**
 * Handles notification click event.
 * Closes notification, detects existing window clients, navigates/focuses existing window or opens new tab.
 */
async function handleNotificationClick(event) {
  // 1. Safely close notification
  if (event && event.notification && typeof event.notification.close === 'function') {
    try {
      event.notification.close();
    } catch {
      // Ignore notification close failure
    }
  }

  const origin = (self.location && self.location.origin) || '';
  const notificationData = (event && event.notification && event.notification.data) || null;
  const targetUrl = resolveNotificationTargetUrl(notificationData, origin);

  // 2. Search for existing window clients on same origin to focus/navigate
  try {
    let windowClients = [];
    if (self.clients && typeof self.clients.matchAll === 'function') {
      windowClients = await self.clients.matchAll({
        type: 'window',
        includeUncontrolled: true,
      });
    }

    let matchingClient = null;
    const scopeBasePath = getScopeBasePath();
    for (const client of windowClients) {
      if (client && client.url) {
        try {
          const clientUrl = new URL(client.url);
          if (clientUrl.origin === origin) {
            matchingClient = client;
            // Prefer client running within the app scope
            if (scopeBasePath === '/' || clientUrl.pathname.startsWith(scopeBasePath)) {
              break;
            }
          }
        } catch {
          // Continue scanning
        }
      }
    }

    if (matchingClient) {
      // If client is already open, navigate if necessary and focus
      if (typeof matchingClient.navigate === 'function' && matchingClient.url !== targetUrl) {
        try {
          await matchingClient.navigate(targetUrl);
        } catch {
          // If navigate throws or fails, proceed to focus
        }
      }

      if (typeof matchingClient.focus === 'function') {
        try {
          await matchingClient.focus();
          return;
        } catch {
          // Fall through to openWindow if focus fails
        }
      } else {
        return;
      }
    }

    // 3. If no matching client was found or focus failed, open new window
    if (self.clients && typeof self.clients.openWindow === 'function') {
      await self.clients.openWindow(targetUrl);
    }
  } catch (error) {
    // Top-level fallback
    try {
      if (self.clients && typeof self.clients.openWindow === 'function') {
        await self.clients.openWindow(targetUrl);
      }
    } catch {
      // Worker never crashes
    }
  }
}

self.addEventListener('notificationclick', (event) => {
  if (event && typeof event.waitUntil === 'function') {
    event.waitUntil(handleNotificationClick(event));
  } else {
    handleNotificationClick(event);
  }
});

// Export helpers for unit testing when evaluated in test runners
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    parsePushPayload,
    resolveNotificationTargetUrl,
    handleNotificationClick,
    getScopeBasePath,
  };
}
