/* messagingManager.js */

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
}

function getNotifiedMessages() {
    try {
        return JSON.parse(sessionStorage.getItem('wdiNotifiedMessages') || '[]');
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
        sessionStorage.setItem('wdiNotifiedMessages', JSON.stringify(notified));
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
            const myNickname = localStorage.getItem('wdiUserNickname');
            const unreadConversations = conversations.filter(c => !c.read && c.receiverNickname === myNickname);
            const currentUnreadCount = unreadConversations.length;

            const notifiedMessages = getNotifiedMessages();
            let triggeredCount = 0;

            if (currentUnreadCount > 0) {
                // Check each conversation for new activity
                unreadConversations.forEach((conv, index) => {
                    // Fingerprint includes content snippet to detect new messages within the same conversation
                    const fingerprint = `${conv.senderNickname}_${conv.timestamp}_${conv.content.substring(0, 10)}`;
                    const alreadyNotified = notifiedMessages.includes(fingerprint);

                    if (!alreadyNotified) {
                        // Condition: 
                        // 1. It's a new message during a session (polling)
                        // 2. OR it's a page refresh and we want to show the LATEST unread message (even if seen before in a previous view? 
                        // User said: "yeniledigimde de eger mesaj varsa gosterilsin" -> imply always show one if unread on load.

                        if (isInitialLoad) {
                            // On refresh, only trigger for the very latest one (index 0 usually if sorted by time)
                            if (index === 0) {
                                triggerNotification(conv);
                                triggeredCount++;
                            }
                            // Mark ALL existing unread as "seen" so they don't trigger again on next poll
                            addNotifiedMessage(fingerprint);
                        } else {
                            // During polling, trigger for every new message fingerprint
                            triggerNotification(conv);
                            addNotifiedMessage(fingerprint);
                            triggeredCount++;
                        }
                    } else if (isInitialLoad && index === 0 && triggeredCount === 0) {
                        // User specifically asked to show if message exists on refresh
                        // If it was already notified but it's the latest one on a fresh page load, we can show it again
                        triggerNotification(conv);
                        triggeredCount++;
                    }
                });

                badge.textContent = currentUnreadCount > 99 ? '99+' : currentUnreadCount;
                badge.style.display = 'flex';
            } else {
                badge.style.display = 'none';
            }

            // Sync unread count to session for general tracking
            sessionStorage.setItem('lastUnreadCount', currentUnreadCount.toString());
        })
        .catch(() => {
            badge.style.display = 'none';
        });
}

function triggerNotification(conv) {
    // 1. Play Sound
    playNotificationSound();

    // 2. Show Toast
    const isMessagesPage = window.location.pathname.includes('/messages');
    const activeChat = document.body.dataset.activeChat;

    // Show toast if:
    // - We are NOT on the messages page
    // - OR we ARE on the messages page but this message is from someone ELSE (not the active chat)
    if (!isMessagesPage || (activeChat && activeChat !== conv.senderNickname)) {
        showToast(conv);
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

function showToast(conv) {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.innerHTML = `
        <div class="toast-icon"><i class="fas fa-message"></i></div>
        <div class="toast-content">
            <div class="toast-title">${conv.senderNickname}</div>
            <div class="toast-message">${conv.content}</div>
        </div>
    `;

    toast.onclick = () => {
        window.location.href = '/messages';
    };

    container.appendChild(toast);

    // Auto remove after 4 seconds
    setTimeout(() => {
        toast.classList.add('hide');
        setTimeout(() => toast.remove(), 400);
    }, 4000);
}

let stompClient = null;
let socket = null;
let reconnectTimeout = null;

function connectWebSocket() {
    const nickname = localStorage.getItem('wdiUserNickname');
    if (!nickname) return;

    if (stompClient && stompClient.connected) return;

    socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Suppress STOMP console logs

    stompClient.connect({}, function (frame) {
        stompClient.subscribe('/topic/messages/' + nickname, function (messageOutput) {
            try {
                const data = JSON.parse(messageOutput.body);
                
                // Skip read receipt events for main notifications
                if (data.type === 'READ_EVENT') {
                    return;
                }

                // Play sound notification
                playNotificationSound();

                // Show desktop-like toast
                showToast(data);

                // Instantly sync the badge with database
                updateMessageBadge(false);
            } catch (e) {
                console.error("Error processing incoming WebSocket message:", e);
            }
        });
    }, function (error) {
        // Automatically reconnect after 5 seconds
        clearTimeout(reconnectTimeout);
        reconnectTimeout = setTimeout(connectWebSocket, 5000);
    });
}

