/* browserNotification.js - Compatibility bridge for BLDizi Web Notifications & Web Push */

export function isBrowserNotificationSupported() {
    return 'Notification' in window;
}

export function getBrowserNotificationPermission() {
    return 'Notification' in window ? Notification.permission : 'unsupported';
}

export async function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        try {
            return await navigator.serviceWorker.register('/sw.js', { scope: '/' });
        } catch (err) {
            console.debug('ServiceWorker registration error:', err);
            return null;
        }
    }
    return null;
}

export async function subscribeToWebPush() {
    if (window.WebPushManager) {
        const res = await window.WebPushManager.subscribe();
        return res && res.success;
    }
    return false;
}

export async function unsubscribeFromWebPush() {
    if (window.WebPushManager) {
        const res = await window.WebPushManager.unsubscribe();
        return res && res.success;
    }
    return false;
}

export async function requestBrowserNotificationPermission() {
    if (!('Notification' in window)) return false;
    if (Notification.permission === 'granted') return true;
    if (Notification.permission === 'denied') return false;
    try {
        const permission = await Notification.requestPermission();
        return permission === 'granted';
    } catch (e) {
        return false;
    }
}

export function showBrowserNotification(title, options = {}) {
    if (!('Notification' in window)) return null;
    if (Notification.permission !== 'granted') return null;

    const defaultLogo = '/elements/img/bldizi-logo-notif.png';
    const finalOptions = {
        icon: defaultLogo,
        badge: defaultLogo,
        tag: options.tag || ('bldizi_' + Date.now()),
        renotify: true,
        silent: false,
        ...options
    };

    try {
        const notif = new Notification(title, finalOptions);
        notif.onclick = function (event) {
            event.preventDefault();
            try { window.focus(); } catch (err) {}
            if (typeof options.onClick === 'function') {
                options.onClick(event);
            } else if (options.url) {
                window.location.href = options.url;
            }
            notif.close();
        };
        return notif;
    } catch (e) {
        console.debug('showBrowserNotification error:', e);
        return null;
    }
}

export function checkAndPromptNotificationPermission() {}
