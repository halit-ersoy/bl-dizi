/* sw.js - Web Push & Background Notification Service Worker for BLDizi */

self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(clients.claim());
});

self.addEventListener('push', (event) => {
    if (!event.data) return;

    let payload = {};
    try {
        payload = event.data.json();
    } catch (e) {
        payload = { title: 'BLDizi', body: event.data.text() };
    }

    const title = payload.title || 'BLDizi';
    const options = {
        body: payload.body || payload.message || '',
        icon: payload.icon || '/elements/img/bldizi-logo-notif.png',
        badge: payload.badge || '/elements/img/bldizi-logo-notif.png',
        data: {
            url: payload.url || '/'
        },
        tag: payload.tag || ('bldizi_push_' + Date.now()),
        renotify: true,
        vibrate: [200, 100, 200]
    };

    event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    const targetUrl = (event.notification.data && event.notification.data.url)
        ? event.notification.data.url
        : '/';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            for (let i = 0; i < clientList.length; i++) {
                const client = clientList[i];
                if ('focus' in client) {
                    client.focus();
                    if ('navigate' in client && targetUrl !== '/') {
                        client.navigate(targetUrl);
                    }
                    return;
                }
            }
            if (clients.openWindow) {
                return clients.openWindow(targetUrl);
            }
        })
    );
});
