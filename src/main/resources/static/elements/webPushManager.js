/**
 * BL Dizi - Web Push Manager
 * Manages standard Web Push lifecycle, permissions, subscriptions, and user preferences.
 */

window.WebPushManager = (function () {
    let swRegistration = null;

    function isSupported() {
        const hasSw = ('serviceWorker' in navigator);
        const hasPush = ('PushManager' in window);
        const hasNotif = ('Notification' in window);
        const isSecure = (window.isSecureContext !== false);

        if (!hasSw || !hasPush || !hasNotif) {
            console.warn('Web Push feature check failed:', {
                serviceWorker: hasSw,
                pushManager: hasPush,
                notification: hasNotif,
                isSecureContext: isSecure,
                origin: window.location.origin
            });
        }
        return hasSw && hasPush && hasNotif;
    }

    function isIosNeedsPwa() {
        const ua = window.navigator.userAgent;
        const isIOS = /iPad|iPhone|iPod/.test(ua) && !window.MSStream;
        const isStandalone = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
        return isIOS && !isStandalone;
    }

    function urlBase64ToUint8Array(base64String) {
        const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
        const base64 = (base64String + padding)
            .replace(/\-/g, '+')
            .replace(/_/g, '/');

        const rawData = window.atob(base64);
        const outputArray = new Uint8Array(rawData.length);

        for (let i = 0; i < rawData.length; ++i) {
            outputArray[i] = rawData.charCodeAt(i);
        }
        return outputArray;
    }

    async function getRegistration(autoCreate = false) {
        if (swRegistration) {
            return swRegistration;
        }
        if (!isSupported()) {
            return null;
        }
        try {
            let reg = await navigator.serviceWorker.getRegistration();
            if (!reg) {
                reg = await navigator.serviceWorker.getRegistration('/');
            }
            if (!reg) {
                const regs = await navigator.serviceWorker.getRegistrations();
                if (regs && regs.length > 0) {
                    reg = regs[0];
                }
            }
            if (!reg && autoCreate) {
                reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
            }
            if (reg) {
                swRegistration = reg;
            }
            return reg;
        } catch (e) {
            console.warn('Service Worker registration error:', e);
            return null;
        }
    }

    async function getExistingSubscription() {
        try {
            if (!isSupported() || Notification.permission !== 'granted') {
                return null;
            }
            const reg = await getRegistration(false);
            if (!reg || !reg.pushManager) return null;
            return await reg.pushManager.getSubscription();
        } catch (e) {
            console.warn('Error fetching push subscription:', e);
            return null;
        }
    }

    async function checkSubscriptionStatus() {
        if (!isSupported()) {
            return { supported: false, status: 'unsupported' };
        }
        if (Notification.permission === 'denied') {
            return { supported: true, permission: 'denied', status: 'denied' };
        }
        if (Notification.permission === 'default') {
            return { supported: true, permission: 'default', status: 'unsubscribed' };
        }

        // Permission is 'granted', check if there is an active subscription
        try {
            const sub = await getExistingSubscription();
            if (sub) {
                return { supported: true, permission: 'granted', status: 'subscribed', subscription: sub };
            }
        } catch (e) {
            console.warn('Subscription check error:', e);
        }
        return { supported: true, permission: 'granted', status: 'unsubscribed' };
    }

    async function getStatus() {
        const res = await checkSubscriptionStatus();
        return res.status.toUpperCase();
    }

    async function fetchVapidPublicKey() {
        const response = await fetch('/api/push/public-key');
        if (!response.ok) {
            throw new Error('VAPID public key alınamadı (HTTP ' + response.status + ').');
        }
        const data = await response.json();
        if (!data.publicKey) {
            throw new Error('VAPID public key sunucuda tanımlı değil.');
        }
        return data.publicKey;
    }

    async function subscribe() {
        if (!isSupported()) {
            return { success: false, message: 'Tarayıcınız Web Push bildirimlerini desteklemiyor.' };
        }

        if (Notification.permission === 'denied') {
            return { success: false, message: 'Bildirim izni tarayıcı ayarlarından engellenmiş. Lütfen adres çubuğundaki kilit simgesinden izin verin.' };
        }

        try {
            // Explicitly request permission directly upon user click (preserves user gesture activation)
            let permission = Notification.permission;
            if (permission !== 'granted') {
                try {
                    permission = await Notification.requestPermission();
                } catch (permErr) {
                    permission = await new Promise(resolve => Notification.requestPermission(resolve));
                }
            }

            if (permission !== 'granted') {
                return { success: false, message: 'Bildirim izni verilmedi veya reddedildi.' };
            }

            // Register Service Worker
            let reg;
            try {
                reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
            } catch (swErr) {
                const msg = swErr.message || '';
                if (msg.toLowerCase().includes('ssl') || msg.toLowerCase().includes('certificate')) {
                    throw new Error('Yerel SSL sertifikası hatası: Chrome\'un localhost Service Worker\'ına izin vermesi için adres çubuğuna chrome://flags/#allow-insecure-localhost yazıp Enabled yapmalısınız.');
                }
                throw swErr;
            }
            swRegistration = reg;

            // Wait for service worker ready
            const readyPromise = navigator.serviceWorker.ready;
            const timeoutPromise = new Promise(resolve => setTimeout(resolve, 3000));
            await Promise.race([readyPromise, timeoutPromise]);

            const publicKey = await fetchVapidPublicKey();
            const convertedKey = urlBase64ToUint8Array(publicKey);

            let subscription = await reg.pushManager.getSubscription();
            if (!subscription) {
                subscription = await reg.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: convertedKey
                });
            }

            const subJson = subscription.toJSON();
            let p256dh = subJson.keys ? subJson.keys.p256dh : null;
            let auth = subJson.keys ? subJson.keys.auth : null;

            if (!p256dh && subscription.getKey) {
                const rawKey = subscription.getKey('p256dh');
                if (rawKey) {
                    p256dh = btoa(String.fromCharCode.apply(null, new Uint8Array(rawKey)))
                        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                }
            }
            if (!auth && subscription.getKey) {
                const rawAuth = subscription.getKey('auth');
                if (rawAuth) {
                    auth = btoa(String.fromCharCode.apply(null, new Uint8Array(rawAuth)))
                        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                }
            }

            const payload = {
                endpoint: subJson.endpoint || subscription.endpoint,
                keys: {
                    p256dh: p256dh,
                    auth: auth
                },
                p256dh: p256dh,
                auth: auth,
                userAgent: navigator.userAgent
            };

            const res = await fetch('/api/push/subscribe', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                return { success: false, message: errData.message || 'Sunucuya abonelik kaydedilemedi.' };
            }

            return { success: true, subscription: subscription };
        } catch (e) {
            console.error('Subscription error:', e);
            let userMsg = e.message || 'Abonelik oluşturulamadı.';
            if (userMsg.toLowerCase().includes('ssl') || userMsg.toLowerCase().includes('certificate')) {
                userMsg = 'Yerel SSL sertifikası hatası: Chrome\'un localhost Service Worker\'ına izin vermesi için adres çubuğuna chrome://flags/#allow-insecure-localhost yazıp Enabled yapmalısınız.';
            }
            return { success: false, message: userMsg };
        }
    }

    async function unsubscribe() {
        try {
            const sub = await getExistingSubscription();
            if (sub) {
                const endpoint = sub.endpoint;
                await fetch('/api/push/unsubscribe', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ endpoint: endpoint })
                }).catch(() => {});

                await sub.unsubscribe().catch(() => {});
            }
            return { success: true };
        } catch (e) {
            console.warn('Unsubscribe error:', e);
            return { success: false, message: e.message };
        }
    }

    async function getPreferences() {
        try {
            const res = await fetch('/api/push/preferences');
            if (res.ok) {
                return await res.json();
            }
        } catch (e) {
            console.warn('Failed to fetch push preferences:', e);
        }
        return {
            allowMessagePush: true,
            allowDirectMessagePush: true,
            allowCommentPush: true,
            allowCommentReplyPush: true,
            allowMessagePreview: false,
            showContentPreview: false
        };
    }

    async function updatePreferences(prefs) {
        try {
            const res = await fetch('/api/push/preferences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(prefs)
            });
            if (res.ok) {
                return { success: true };
            }
        } catch (e) {
            console.error('Failed to update push preferences:', e);
        }
        return { success: false };
    }

    async function syncSubscriptionIfActive() {
        if (!isSupported() || Notification.permission !== 'granted') {
            return;
        }
        try {
            const sub = await getExistingSubscription();
            if (sub) {
                const subJson = sub.toJSON();
                let p256dh = subJson.keys ? subJson.keys.p256dh : null;
                let auth = subJson.keys ? subJson.keys.auth : null;

                if (!p256dh && sub.getKey) {
                    const rawKey = sub.getKey('p256dh');
                    if (rawKey) {
                        p256dh = btoa(String.fromCharCode.apply(null, new Uint8Array(rawKey)))
                            .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                    }
                }
                if (!auth && sub.getKey) {
                    const rawAuth = sub.getKey('auth');
                    if (rawAuth) {
                        auth = btoa(String.fromCharCode.apply(null, new Uint8Array(rawAuth)))
                            .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                    }
                }

                await fetch('/api/push/subscribe', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        endpoint: subJson.endpoint || sub.endpoint,
                        keys: {
                            p256dh: p256dh,
                            auth: auth
                        },
                        p256dh: p256dh,
                        auth: auth,
                        userAgent: navigator.userAgent
                    })
                });
            }
        } catch (e) {
            // Silently ignore sync failures
        }
    }

    return {
        isSupported,
        isIosNeedsPwa,
        checkSubscriptionStatus,
        getStatus,
        getExistingSubscription,
        subscribe,
        unsubscribe,
        getPreferences,
        updatePreferences,
        syncSubscriptionIfActive
    };
})();

var WebPushManager = window.WebPushManager;

// Auto-sync existing subscription when page loads and user is logged in
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        if (window.WebPushManager) window.WebPushManager.syncSubscriptionIfActive();
    });
} else {
    if (window.WebPushManager) window.WebPushManager.syncSubscriptionIfActive();
}
