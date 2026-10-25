/* browserNotification.js - Modern Web Notifications & Web Push helper */

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

export function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding)
        .replace(/-/g, '+')
        .replace(/_/g, '/');

    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);

    for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
}

export async function subscribeToWebPush() {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
        return false;
    }
    if (Notification.permission !== 'granted') {
        return false;
    }

    try {
        await registerServiceWorker();
        const reg = await navigator.serviceWorker.ready;
        if (!reg) return false;

        const res = await fetch('/api/push/public-key');
        if (!res.ok) return false;
        const data = await res.json();
        const publicKey = data.publicKey;
        if (!publicKey) return false;

        const convertedKey = urlBase64ToUint8Array(publicKey);

        // Ensure any existing stale subscription is cleaned up to avoid applicationServerKey mismatch
        let subscription = await reg.pushManager.getSubscription();
        if (subscription) {
            try {
                await subscription.unsubscribe();
            } catch (e) {
                console.debug('Error unsubscribing existing push subscription:', e);
            }
        }

        subscription = await reg.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: convertedKey
        });

        const subJson = subscription.toJSON();
        const nickname = localStorage.getItem('bldiziUserNickname') || localStorage.getItem('bldiziUserNickname') || '';

        const saveRes = await fetch('/api/push/subscribe', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                nickname: nickname,
                endpoint: subJson.endpoint,
                keys: subJson.keys
            })
        });

        const saveJson = await saveRes.json();
        console.log('Web Push subscription registered:', saveJson);
        return saveRes.ok;
    } catch (err) {
        console.warn('Web Push subscription failed:', err);
        return false;
    }
}

export async function unsubscribeFromWebPush() {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
    try {
        const reg = await navigator.serviceWorker.ready;
        const sub = await reg.pushManager.getSubscription();
        if (sub) {
            await fetch('/api/push/unsubscribe', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ endpoint: sub.endpoint })
            });
            await sub.unsubscribe();
        }
    } catch (err) {
        console.debug('Web Push unsubscribe error:', err);
    }
}

export async function requestBrowserNotificationPermission() {
    if (!('Notification' in window)) {
        return false;
    }
    if (Notification.permission === 'granted') {
        return true;
    }
    if (Notification.permission === 'denied') {
        return false;
    }
    try {
        const permission = await Notification.requestPermission();
        return permission === 'granted';
    } catch (e) {
        console.warn('Notification permission request error:', e);
        return false;
    }
}

export function showBrowserNotification(title, options = {}) {
    if (!('Notification' in window)) return null;
    if (Notification.permission !== 'granted') return null;

    // Respect user settings preference (Ayarlar -> Tercihler -> Anlık Bildirimler)
    const pref = localStorage.getItem('bldiziNotificationsEnabled');
    if (pref === 'false') return null;

    const defaultLogo = '/elements/img/bldizi-logo-notif.png';

    const defaultOptions = {
        icon: defaultLogo,
        badge: defaultLogo,
        tag: options.tag || ('wdi_' + Date.now()),
        renotify: true,
        silent: false
    };

    const finalOptions = { ...defaultOptions, ...options };
    if (!finalOptions.icon || finalOptions.icon === '/favicon.svg') {
        finalOptions.icon = defaultLogo;
    }
    if (!finalOptions.badge || finalOptions.badge === '/favicon.svg') {
        finalOptions.badge = defaultLogo;
    }

    try {
        const notif = new Notification(title, finalOptions);

        notif.onclick = function (event) {
            event.preventDefault();
            try {
                window.focus();
            } catch (err) {}

            if (typeof options.onClick === 'function') {
                options.onClick(event);
            } else if (options.url) {
                window.location.href = options.url;
            }
            notif.close();
        };

        return notif;
    } catch (e) {
        console.debug('Native Notification constructor error:', e);
        return null;
    }
}

export function checkAndPromptNotificationPermission() {
    // Disabled: İzin istekleri ana ekranda sorulmaz; sadece Kullanıcı Ayarları sayfasından yönetilir.
}
