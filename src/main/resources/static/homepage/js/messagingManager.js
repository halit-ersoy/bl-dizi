/* messagingManager.js */

import {
    showBrowserNotification,
    registerServiceWorker,
    subscribeToWebPush
} from '../../elements/browserNotification.js';

let audioContext = null;

export function initMessagingManager() {
    // 1. Audio Unlocking
    const unlockAudio = () => {
        if (!audioContext) {
            audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (audioContext.state === 'suspended') {
            audioContext.resume();
        }
        document.removeEventListener('click', unlockAudio);
        document.removeEventListener('keydown', unlockAudio);
        document.removeEventListener('touchstart', unlockAudio);
    };
    document.addEventListener('click', unlockAudio);
    document.addEventListener('keydown', unlockAudio);
    document.addEventListener('touchstart', unlockAudio);

    // Initial sync
    updateMessageBadge(true);

    // Connect to WebSocket for real-time notifications
    connectWebSocket();
    registerServiceWorker();

    // Auto-sync Web Push subscription if notifications are allowed
    if ('Notification' in window && Notification.permission === 'granted') {
        const pref = localStorage.getItem('bldiziNotificationsEnabled');
        if (pref !== 'false') {
            subscribeToWebPush();
        }
    }
}

function getNotifiedMessages() {
    try {
        return JSON.parse(sessionStorage.getItem('bldiziNotifiedMessages') || '[]');
    } catch (e) {
        return [];
    }
}

function addNotifiedMessage(fingerprint) {
    const notified = getNotifiedMessages();
    if (!notified.includes(fingerprint)) {
        notified.push(fingerprint);
        // Keep only last 50 to avoid storage bloat
        if (notified.length > 50) notified.shift();
        sessionStorage.setItem('bldiziNotifiedMessages', JSON.stringify(notified));
    }
}

export function updateMessageBadge(isInitialLoad = false) {
    const badge = document.getElementById('message-badge');
    if (!badge) return;

    fetch('/api/messages/conversations')
        .then(res => {
            if (!res.ok) throw new Error('Not logged in');
            return res.json();
        })
        .then(conversations => {
            const myNickname = localStorage.getItem('bldiziUserNickname');
            const unreadConversations = conversations.filter(c => !c.read && c.receiverNickname === myNickname);
            const currentUnreadCount = unreadConversations.length;

            const notifiedMessages = getNotifiedMessages();
            let triggeredCount = 0;

            if (currentUnreadCount > 0) {
                // Check each conversation for new activity
                unreadConversations.forEach((conv, index) => {
                    const fingerprint = `${conv.senderNickname}_${conv.timestamp}_${conv.content.substring(0, 10)}`;
                    const alreadyNotified = notifiedMessages.includes(fingerprint);

                    if (!alreadyNotified) {
                        if (isInitialLoad) {
                            if (index === 0) {
                                triggerNotification(conv);
                                triggeredCount++;
                            }
                            addNotifiedMessage(fingerprint);
                        } else {
                            triggerNotification(conv);
                            addNotifiedMessage(fingerprint);
                            triggeredCount++;
                        }
                    } else if (isInitialLoad && index === 0 && triggeredCount === 0) {
                        triggerNotification(conv);
                        triggeredCount++;
                    }
                });

                badge.textContent = currentUnreadCount > 99 ? '99+' : currentUnreadCount;
                badge.style.display = 'flex';
            } else {
                badge.style.display = 'none';
            }

            sessionStorage.setItem('lastUnreadCount', currentUnreadCount.toString());
        })
        .catch(() => {
            badge.style.display = 'none';
        });
}

function triggerNotification(conv) {
    playNotificationSound();

    const isMessagesPage = window.location.pathname.includes('/messages');
    const activeChat = document.body.dataset.activeChat;

    if (!isMessagesPage || (activeChat && activeChat !== conv.senderNickname)) {
        showToast({
            title: conv.senderNickname,
            content: conv.content,
            iconClass: 'fas fa-message',
            url: `/messages?user=${encodeURIComponent(conv.senderNickname)}`
        });
    }

    // Native browser notification for incoming message
    const isCurrentActiveChat = isMessagesPage && activeChat === conv.senderNickname && !document.hidden;
    if (!isCurrentActiveChat) {
        showBrowserNotification(conv.senderNickname, {
            body: conv.content,
            icon: '/elements/img/bldizi-logo-notif.png',
            tag: `msg_${conv.senderNickname}`,
            url: `/messages?user=${encodeURIComponent(conv.senderNickname)}`
        });
    }
}

function playNotificationSound() {
    try {
        if (!audioContext) {
            audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (audioContext.state === 'suspended') {
            audioContext.resume();
        }

        const playTone = (freq, startTime, duration) => {
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();
            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);
            oscillator.type = 'sine';
            oscillator.frequency.setValueAtTime(freq, startTime);
            gainNode.gain.setValueAtTime(0, startTime);
            gainNode.gain.linearRampToValueAtTime(0.2, startTime + 0.02);
            gainNode.gain.linearRampToValueAtTime(0, startTime + duration);
            oscillator.start(startTime);
            oscillator.stop(startTime + duration);
        };

        const now = audioContext.currentTime;
        playTone(660, now, 0.15);      // E5
        playTone(880, now + 0.1, 0.2); // A5
    } catch (e) {
        console.debug("Audio play failed:", e);
    }
}

function showToast(info) {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const title = info.title || info.senderNickname || 'Yeni Bildirim';
    const message = info.message || info.content || '';
    const iconClass = info.iconClass || 'fas fa-message';
    const targetUrl = info.url || '/messages';

    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.innerHTML = `
        <div class="toast-icon"><i class="${iconClass}"></i></div>
        <div class="toast-content">
            <div class="toast-title">${title}</div>
            <div class="toast-message">${message}</div>
        </div>
    `;

    toast.onclick = () => {
        window.location.href = targetUrl;
        toast.remove();
    };

    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('hide');
        setTimeout(() => toast.remove(), 400);
    }, 4500);
}

let stompClient = null;
let socket = null;
let reconnectTimeout = null;

function connectWebSocket() {
    const nickname = localStorage.getItem('bldiziUserNickname');
    if (!nickname) return;

    if (stompClient && stompClient.connected) return;

    socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Suppress STOMP console logs

    stompClient.connect({}, function (frame) {
        // 1. Subscribe to Chat Messages
        stompClient.subscribe('/topic/messages/' + nickname, function (messageOutput) {
            try {
                const data = JSON.parse(messageOutput.body);
                
                // Skip read receipt events for main notifications
                if (data.type === 'READ_EVENT') {
                    return;
                }

                // If sent by me, ignore notification
                if (data.senderNickname === nickname) {
                    return;
                }

                playNotificationSound();

                const isMessagesPage = window.location.pathname.includes('/messages');
                const activeChat = document.body.dataset.activeChat;
                const isCurrentActiveChat = isMessagesPage && activeChat === data.senderNickname && !document.hidden;

                // Show toast if not in current active chat
                if (!isCurrentActiveChat) {
                    showToast({
                        title: data.senderNickname,
                        content: data.content,
                        iconClass: 'fas fa-message',
                        url: `/messages?user=${encodeURIComponent(data.senderNickname)}`
                    });

                    // Native browser notification
                    showBrowserNotification(data.senderNickname, {
                        body: data.content,
                        icon: '/elements/img/bldizi-logo-notif.png',
                        tag: `msg_${data.senderNickname}`,
                        url: `/messages?user=${encodeURIComponent(data.senderNickname)}`
                    });
                }

                // Instantly sync the badge with database
                updateMessageBadge(false);
            } catch (e) {
                console.error("Error processing incoming WebSocket message:", e);
            }
        });

        // 2. Subscribe to User-Specific Notifications (e.g., Comment Replies)
        stompClient.subscribe('/topic/notifications/' + nickname, function (notifOutput) {
            try {
                const notifData = JSON.parse(notifOutput.body);

                if (notifData.type === 'COMMENT_REPLY') {
                    playNotificationSound();

                    const targetUrl = notifData.url || (notifData.contentSlug ? `/${notifData.contentSlug}#comments` : '/');

                    // Show in-app toast
                    showToast({
                        title: notifData.title || 'Yorumunuza Yanıt Geldi',
                        content: notifData.message || notifData.text || '',
                        iconClass: 'fas fa-reply',
                        url: targetUrl
                    });

                    // Update notification bell badge in real-time
                    const notifBadge = document.getElementById('notification-badge');
                    if (notifBadge) {
                        const current = parseInt(notifBadge.textContent || '0', 10);
                        notifBadge.textContent = current + 1;
                        notifBadge.style.display = 'flex';
                    }

                    // Refresh dropdown UI if initialized
                    if (typeof window.bldiziRefreshNotifications === 'function') {
                        window.bldiziRefreshNotifications();
                    }

                    // Native browser notification for comment reply
                    showBrowserNotification(notifData.title || 'Yorumunuza Yanıt Geldi', {
                        body: notifData.message || notifData.text || '',
                        icon: '/elements/img/bldizi-logo-notif.png',
                        tag: `reply_${Date.now()}`,
                        url: targetUrl
                    });
                }
            } catch (e) {
                console.error("Error processing incoming notification WebSocket event:", e);
            }
        });
    }, function (error) {
        // Automatically reconnect after 5 seconds
        clearTimeout(reconnectTimeout);
        reconnectTimeout = setTimeout(connectWebSocket, 5000);
    });
}
