/**
 * BL Dizi - Web Push Service Worker
 * Root scope Service Worker for handling RFC 8291 / 8292 standard Web Push notifications.
 */

self.addEventListener('install', (event) => {
    // Immediately activate new service worker
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    // Claim all clients immediately so push events can be handled without page refresh
    event.waitUntil(clients.claim());
});

self.addEventListener('push', (event) => {
    if (!event.data) {
        return;
    }

    let data;
    try {
        data = event.data.json();
    } catch (e) {
        data = {
            title: 'BL Dizi',
            body: event.data.text() || 'Yeni bir bildiriminiz var.',
            url: '/'
        };
    }

    const defaultLogo = '/elements/img/bldizi-logo-notif.png';
    const title = data.title || 'BL Dizi';
    const body = data.body || 'Yeni bir bildiriminiz var.';
    const icon = data.icon || defaultLogo;
    const badge = data.badge || defaultLogo;
    const targetUrl = data.url || '/';
    const tag = data.tag || ('bldizi_' + Date.now());
    const payloadData = data.data || {};

    event.waitUntil(
        (async () => {
            // Deduplication on active focused window:
            // If the user on THIS device is already actively focused on the sender's chat,
            // suppress the push notification for this client while leaving other devices alerted.
            const windowClients = await clients.matchAll({
                type: 'window',
                includeUncontrolled: true
            });

            if (payloadData.type === 'DIRECT_MESSAGE' && payloadData.sender) {
                const isChatOpenAndFocused = windowClients.some(client => {
                    try {
                        const url = new URL(client.url, self.location.origin);
                        const isMessagesPage = url.pathname === '/messages';
                        const isSameSender = url.searchParams.get('user') === payloadData.sender;
                        return client.focused && isMessagesPage && isSameSender;
                    } catch (e) {
                        return false;
                    }
                });

                if (isChatOpenAndFocused) {
                    return;
                }
            }

            const options = {
                body: body,
                icon: icon,
                badge: badge,
                tag: tag,
                renotify: true,
                vibrate: [200, 100, 200],
                data: {
                    url: targetUrl,
                    extra: payloadData
                }
            };

            return self.registration.showNotification(title, options);
        })()
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    const targetPathOrUrl = (event.notification.data && event.notification.data.url)
        ? event.notification.data.url
        : '/';

    event.waitUntil(
        (async () => {
            // Strict same-origin verification
            let targetUrl;
            try {
                targetUrl = new URL(targetPathOrUrl, self.location.origin);
            } catch (e) {
                targetUrl = new URL('/', self.location.origin);
            }

            if (targetUrl.origin !== self.location.origin) {
                targetUrl = new URL('/', self.location.origin);
            }

            const windowClients = await clients.matchAll({
                type: 'window',
                includeUncontrolled: true
            });

            // Focus an existing open window if available
            for (const client of windowClients) {
                try {
                    const clientUrl = new URL(client.url, self.location.origin);
                    if (clientUrl.origin === targetUrl.origin) {
                        if ('focus' in client) {
                            await client.focus();
                            if (client.url !== targetUrl.href) {
                                return client.navigate(targetUrl.href);
                            }
                            return;
                        }
                    }
                } catch (e) {
                    // Continue searching
                }
            }

            // Otherwise open a new window
            if (clients.openWindow) {
                return clients.openWindow(targetUrl.href);
            }
        })()
    );
});
