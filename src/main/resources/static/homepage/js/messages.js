/* messages.js */
import { showReportModal } from '../../elements/premium-modals.js';


document.addEventListener('DOMContentLoaded', () => {
    const userSearchInput = document.getElementById('user-search-input');
    const userSearchResults = document.getElementById('user-search-results');
    const conversationsList = document.getElementById('conversations-list');
    const chatArea = document.getElementById('chat-area');

    let currentReceiver = null;
    let currentReceiverRole = null;
    let chatInterval = null;
    let lastConversationsJson = null;
    let lastMessagesCount = 0;

    let stompClient = null;
    let socket = null;
    let reconnectTimeout = null;

    const popularEmojis = [
        '😀', '😂', '😍', '😊', '🥰', '😎', '😜', '🤔', '🙄', '😴',
        '😭', '😱', '😡', '👍', '👎', '❤️', '🔥', '✨', '🙌', '👏',
        '🎉', '🎈', '🎂', '🥳', '🥺', '🤡', '💀', '💩', '👻', '👾',
        '🚀', '💯', '🙏', '💪', '👀', '🌟'
    ];

    function isOnlyEmojis(text) {
        // Regex for matching emojis: covers standard emojis, variations, and modifiers
        const emojiRegex = /(\u00a9|\u00ae|[\u2000-\u3300]|\ud83c[\ud000-\udfff]|\ud83d[\ud000-\udfff]|\ud83e[\ud000-\udfff])/g;
        const emojis = text.match(emojiRegex);
        if (!emojis) return { only: false, count: 0 };

        const cleaned = text.replace(emojiRegex, '').trim();
        return { only: cleaned.length === 0, count: emojis.length };
    }

    function getRoleBadge(role) {
        if (role === 'ADMIN') {
            return '<span class="admin-badge"><i class="fas fa-shield-alt"></i> ADMİN</span>';
        } else if (role === 'SUPER_ADMIN') {
            return '<span class="super-admin-badge"><i class="fas fa-crown"></i> SUPER ADMİN</span>';
        } else if (role === 'KURUCU') {
            return '<span class="founder-badge"><i class="fas fa-crown"></i> KURUCU</span>';
        } else if (role === 'GELISTIRICI') {
            return '<span class="developer-badge"><i class="fas fa-code"></i> GELİŞTİRİCİ</span>';
        } else if (role === 'CEVIRMEN') {
            return '<span class="translator-badge"><i class="fas fa-language"></i> ÇEVİRMEN</span>';
        } else if (role === 'USER' || !role) {
            return '';
        }
        return '';
    }

    // 1. Initial Load
    fetchConversations();

    // Global interval to refresh conversations list (sidebar) - kept as a slow fallback
    setInterval(fetchConversations, 20000);

    // Connect to WebSocket for real-time messaging
    connectWebSocket();

    // 2. User Search Logic
    let searchTimeout = null;
    userSearchInput.addEventListener('input', () => {
        clearTimeout(searchTimeout);
        const query = userSearchInput.value.trim();

        if (query.length < 2) {
            userSearchResults.classList.remove('active');
            return;
        }

        searchTimeout = setTimeout(() => {
            fetch(`/api/users/search-messaging?q=${query}`)
                .then(res => res.json())
                .then(users => {
                    renderSearchResults(users);
                });
        }, 300);
    });

    function renderSearchResults(users) {
        if (!users || users.length === 0) {
            userSearchResults.innerHTML = '<div style="padding: 10px; font-size: 0.8rem; color: #888;">Sonuç bulunamadı.</div>';
        } else {
            userSearchResults.innerHTML = users.map(user => {
                const initial = user.nickname[0].toUpperCase();
                const avatarUrl = `/media/profile/${user.id}`;
                return `
                <div class="search-item" data-nickname="${user.nickname}" data-role="${user.role || ''}" data-id="${user.id}" data-verified="${user.isVerified || false}">
                    <div class="avatar" style="position: relative; overflow: hidden; color: white;">
                        ${initial}
                        <img src="${avatarUrl}" onerror="this.style.display='none'" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">
                    </div>
                    <div class="info">
                        <div style="display:flex; align-items:center; gap:5px;">
                            <div class="nickname">${user.nickname}</div>
                            ${user.isVerified ? '<i class="fas fa-check-circle" style="color: #1DA1F2; font-size: 0.8rem;" title="Doğrulanmış Hesap"></i>' : ''}
                            ${getRoleBadge(user.role)}
                        </div>
                        <div class="name" style="font-size: 0.75rem; color: #888;">${user.name} ${user.surname}</div>
                    </div>
                </div>
            `;
            }).join('');

            userSearchResults.querySelectorAll('.search-item').forEach(item => {
                item.addEventListener('click', () => {
                    const nickname = item.dataset.nickname;
                    const role = item.dataset.role;
                    const verified = item.dataset.verified === 'true';
                    startConversation(nickname, role, item.dataset.id, verified);
                    userSearchResults.classList.remove('active');
                    userSearchInput.value = '';
                });
            });
        }
        userSearchResults.classList.add('active');
    }

    // Close search on click outside
    document.addEventListener('click', (e) => {
        if (!userSearchInput.contains(e.target) && !userSearchResults.contains(e.target)) {
            userSearchResults.classList.remove('active');
        }
    });

    // 3. Conversations Logic
    function fetchConversations() {
        fetch('/api/messages/conversations')
            .then(res => res.json())
            .then(conversations => {
                renderConversations(conversations);
            });
    }

    function renderConversations(conversations) {
        const conversationsJson = JSON.stringify(conversations);
        if (conversationsJson === lastConversationsJson) return;
        lastConversationsJson = conversationsJson;

        if (!conversations || conversations.length === 0) {
            conversationsList.innerHTML = '<div class="chat-empty-state" style="padding: 20px;"><p style="font-size: 0.9rem;">Henüz bir sohbet yok.</p></div>';
            return;
        }

        const myNickname = localStorage.getItem('wdiUserNickname');

        conversationsList.innerHTML = conversations.map(c => {
            const isSentByMe = c.senderNickname === myNickname;
            const otherUser = isSentByMe ? c.receiverNickname : c.senderNickname;
            const otherUserId = isSentByMe ? c.receiverId : c.senderId;
            const isActive = otherUser === currentReceiver;
            const isUnread = !c.read && c.receiverNickname === myNickname;
            const time = formatTime(new Date(c.timestamp));

            let statusIcon = '';
            if (isSentByMe) {
                if (c.read) {
                    statusIcon = '<i class="fas fa-check-double message-status-tick read" style="font-size: 0.7rem; margin-right: 3px;"></i>';
                } else if (c.delivered) {
                    statusIcon = '<i class="fas fa-check-double message-status-tick" style="font-size: 0.7rem; margin-right: 3px;"></i>';
                } else {
                    statusIcon = '<i class="fas fa-check message-status-tick" style="font-size: 0.7rem; margin-right: 3px;"></i>';
                }
            }

            const initial = otherUser[0].toUpperCase();
            const avatarUrl = `/media/profile/${otherUserId}`;

            return `
                <div class="conversation-item ${isActive ? 'active' : ''} ${isUnread ? 'unread' : ''}" data-nickname="${otherUser}" data-id="${otherUserId}" data-verified="${isSentByMe ? c.receiverVerified : c.senderVerified}">
                    <div class="avatar" style="position: relative; overflow: hidden; color: white;">
                        ${initial}
                        <img src="${avatarUrl}" onerror="this.style.display='none'" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">
                    </div>
                    <div class="conversation-info">
                        <div class="conversation-top">
                            <div style="display: flex; align-items: center; gap: 4px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1;">
                                <span class="nickname" style="flex-shrink: 0;">${otherUser}</span>
                                ${(isSentByMe ? c.receiverVerified : c.senderVerified) ? '<i class="fas fa-check-circle" style="color: #1DA1F2; font-size: 0.75rem;" title="Doğrulanmış Hesap"></i>' : ''}
                                ${getRoleBadge(isSentByMe ? c.receiverRole : c.senderRole)}
                            </div>
                            <span class="time" style="flex-shrink: 0; margin-left: 8px;">${time}</span>
                        </div>
                        <div class="last-message">${statusIcon}${c.content}</div>
                    </div>
                </div>
            `;
        }).join('');

        conversationsList.querySelectorAll('.conversation-item').forEach(item => {
            item.addEventListener('click', () => {
                const nickname = item.dataset.nickname;
                const conv = conversations.find(c => (c.senderNickname === myNickname ? c.receiverNickname : c.senderNickname) === nickname);
                const role = conv ? (conv.senderNickname === myNickname ? conv.receiverRole : conv.senderRole) : null;
                const verified = conv ? (conv.senderNickname === myNickname ? conv.receiverVerified : conv.senderVerified) : false;
                const otherId = item.dataset.id;

                // Only start if it's a different person to avoid flash
                if (currentReceiver !== nickname) {
                    startConversation(nickname, role, otherId, verified);
                }
            });
        });
    }

    // 4. Chat Logic
    function startConversation(nickname, role = null, receiverId = null, verified = false) {
        currentReceiver = nickname;
        currentReceiverRole = role;

        // Expose active chat partner globally for the notification manager
        document.body.dataset.activeChat = nickname;

        const initial = nickname[0].toUpperCase();
        const avatarUrl = receiverId ? `/media/profile/${receiverId}` : '';

        // Update UI
        chatArea.innerHTML = `
            <div class="chat-header">
                <div class="receiver-avatar" style="position: relative; overflow: hidden; color: white;">
                    ${initial}
                    ${avatarUrl ? `<img src="${avatarUrl}" onerror="this.style.display='none'" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">` : ''}
                </div>
                <div class="receiver-info">
                    <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                        <h3 style="margin: 0; font-size: 1.1rem;">${nickname}</h3>
                        ${verified ? '<i class="fas fa-check-circle" style="color: #1DA1F2;" title="Doğrulanmış Hesap"></i>' : ''}
                        ${getRoleBadge(role)}
                    </div>
                </div>
                <div class="chat-header-actions" id="chat-actions">
                    <button class="action-btn block-btn" id="block-btn" title="Kullanıcıyı Engelle">
                        <i class="fas fa-ban"></i>
                    </button>
                    <button class="action-btn report-btn" id="report-btn" title="Kullanıcıyı Bildir">
                        <i class="fas fa-flag"></i>
                    </button>
                </div>
            </div>
            <div class="chat-messages" id="chat-messages-container">
                <div style="text-align:center; padding: 20px; color: #888;">Yükleniyor...</div>
            </div>
            <div class="chat-input-area" id="chat-input-area">
                <div id="emoji-picker" class="emoji-picker">
                    ${popularEmojis.map(emoji => `<div class="emoji-item">${emoji}</div>`).join('')}
                </div>
                <div class="chat-input-wrapper">
                    <button class="emoji-btn" id="emoji-btn-toggle" title="Emoji">
                        <i class="far fa-smile"></i>
                    </button>
                    <textarea id="message-input" placeholder="Bir mesaj yazın..." rows="1" maxlength="400"></textarea>
                    <div class="char-counter"><span id="char-count">0</span> / 400</div>
                    <button class="send-btn" id="send-message-btn" disabled>
                        <i class="fas fa-paper-plane"></i>
                    </button>
                </div>
            </div>
        `;

        updateBlockStatus(nickname);

        const blockBtn = document.getElementById('block-btn');
        const reportBtn = document.getElementById('report-btn');

        blockBtn.addEventListener('click', () => toggleBlock(nickname));
        reportBtn.addEventListener('click', () => showReportModalHelper(nickname));

        renderConversations([]); // Refresh sidebar highlights
        fetchConversations();

        loadChatHistory(nickname);

        // Send WebSocket read notification to mark previous messages as read
        sendReadNotification(nickname);

        // Active chat is now handled in real-time by WebSocket. 
        // We only maintain a slow 20s recovery loop in the background.
        clearInterval(chatInterval);
        chatInterval = setInterval(() => {
            if (currentReceiver === nickname) {
                loadChatHistory(nickname, true);
                fetchConversations();
            }
        }, 20000);

        // Setup input event
        const input = document.getElementById('message-input');
        const sendBtn = document.getElementById('send-message-btn');
        const emojiBtn = document.getElementById('emoji-btn-toggle');
        const picker = document.getElementById('emoji-picker');

        emojiBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            picker.classList.toggle('active');
        });

        document.addEventListener('click', (e) => {
            if (picker && !picker.contains(e.target) && !emojiBtn.contains(e.target)) {
                picker.classList.remove('active');
            }
        });

        picker.querySelectorAll('.emoji-item').forEach(item => {
            item.addEventListener('click', () => {
                const start = input.selectionStart;
                const end = input.selectionEnd;
                const text = input.value;
                input.value = text.substring(0, start) + item.innerText + text.substring(end);
                input.focus();
                input.selectionStart = input.selectionEnd = start + item.innerText.length;

                // Trigger input event for auto-resize and button enable
                input.dispatchEvent(new Event('input'));
            });
        });

        input.addEventListener('input', () => {
            const val = input.value;
            document.getElementById('char-count').innerText = val.length;
            sendBtn.disabled = !val.trim() || val.length > 400;
            // Auto resize textarea
            input.style.height = 'auto';
            input.style.height = (input.scrollHeight) + 'px';
        });

        sendBtn.addEventListener('click', () => {
            picker.classList.remove('active');
            sendMessage(nickname, input.value.trim());
        });
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                picker.classList.remove('active');
                sendBtn.click();
            }
        });
    }

    function loadChatHistory(nickname, silent = false) {
        fetch(`/api/messages/history/${nickname}`)
            .then(res => res.json())
            .then(messages => {
                const container = document.getElementById('chat-messages-container');
                if (!container) return;

                const myNickname = localStorage.getItem('wdiUserNickname');
                const wasAtBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 100;

                if (silent && messages.length === lastMessagesCount) {
                    // Update only ticks/status if count is same
                    messages.forEach(m => {
                        const msgElement = container.querySelector(`[data-id="${m.id}"]`);
                        if (msgElement) {
                            const footer = msgElement.querySelector('.message-footer');
                            const isSent = m.senderNickname === myNickname;
                            if (isSent && footer) {
                                let statusIcon = '';
                                if (m.read) statusIcon = '<i class="fas fa-check-double message-status-tick read"></i>';
                                else if (m.delivered) statusIcon = '<i class="fas fa-check-double message-status-tick"></i>';
                                else statusIcon = '<i class="fas fa-check message-status-tick"></i>';

                                const tickElement = footer.querySelector('.message-status-tick');
                                if (tickElement) tickElement.outerHTML = statusIcon;
                            }
                        }
                    });
                    return;
                }

                lastMessagesCount = messages.length;

                container.innerHTML = messages.map(m => {
                    const isSent = m.senderNickname === myNickname;

                    let statusIcon = '';
                    if (isSent) {
                        if (m.read) {
                            statusIcon = '<i class="fas fa-check-double message-status-tick read"></i>';
                        } else if (m.delivered) {
                            statusIcon = '<i class="fas fa-check-double message-status-tick"></i>';
                        } else {
                            statusIcon = '<i class="fas fa-check message-status-tick"></i>';
                        }
                    }

                    const emojiInfo = isOnlyEmojis(m.content);
                    let bubbleClass = isSent ? 'sent' : 'received';
                    if (emojiInfo.only && emojiInfo.count <= 2) {
                        bubbleClass += ' only-emoji';
                        if (emojiInfo.count === 1) bubbleClass += ' large';
                        else if (emojiInfo.count === 2) bubbleClass += ' medium';
                    }

                    return `
                        <div class="message-bubble ${bubbleClass}" data-id="${m.id}">
                            ${m.content}
                            <div class="message-footer">
                                <span class="message-time">${formatTime(new Date(m.timestamp))}</span>
                                ${isSent ? statusIcon : ''}
                            </div>
                        </div>
                    `;
                }).join('');

                if (!silent || wasAtBottom) {
                    container.scrollTop = container.scrollHeight;
                }
            });
    }

    function sendMessage(nickname, content) {
        if (!content) return;

        const sendBtn = document.getElementById('send-message-btn');
        const input = document.getElementById('message-input');

        sendBtn.disabled = true;

        if (stompClient && stompClient.connected) {
            try {
                stompClient.send("/app/chat.send", {}, JSON.stringify({
                    receiverNickname: nickname,
                    content: content
                }));
                // Snappy UI clearing
                input.value = '';
                input.style.height = 'auto';
            } catch (e) {
                console.error("Error sending WebSocket message, trying HTTP fallback:", e);
                sendViaHttp(nickname, content, sendBtn, input);
            }
        } else {
            sendViaHttp(nickname, content, sendBtn, input);
        }
    }

    function sendViaHttp(nickname, content, sendBtn, input) {
        fetch('/api/messages/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ receiverNickname: nickname, content: content })
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    input.value = '';
                    input.style.height = 'auto';
                    loadChatHistory(nickname);
                    fetchConversations();
                } else {
                    alert("Mesaj gönderilemedi: " + data.message);
                    sendBtn.disabled = false;
                }
            })
            .catch(err => {
                alert("Mesaj gönderilemedi (Ağ Hatası)");
                sendBtn.disabled = false;
            });
    }

    function formatTime(date) {
        const now = new Date();
        const isToday = date.toDateString() === now.toDateString();

        if (isToday) {
            return date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
        } else {
            return date.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' });
        }
    }

    /* Safety & Blocking */
    function updateBlockStatus(nickname) {
        fetch(`/api/messages/block-status/${nickname}`)
            .then(res => res.json())
            .then(status => {
                const blockBtn = document.getElementById('block-btn');
                const chatInputArea = document.getElementById('chat-input-area');
                if (!blockBtn || !chatInputArea) return;

                if (status.blockedByMe) {
                    blockBtn.innerHTML = '<i class="fas fa-user-slash"></i>';
                    blockBtn.classList.add('active');
                    blockBtn.title = "Engeli Kaldır";
                } else {
                    blockBtn.innerHTML = '<i class="fas fa-ban"></i>';
                    blockBtn.classList.remove('active');
                    blockBtn.title = "Kullanıcıyı Engelle";
                }

                if (status.blockedByMe || status.blockedByThem) {
                    chatInputArea.innerHTML = `
                        <div class="blocked-message">
                            ${status.blockedByMe ? 'Bu kullanıcıyı engellediniz.' : 'Bu kullanıcı sizi engelledi.'}
                            Mesaj gönderemezsiniz.
                        </div>
                    `;
                }
            });
    }

    function toggleBlock(nickname) {
        const btn = document.getElementById('block-btn');
        const isBlocked = btn.classList.contains('active');
        const endpoint = isBlocked ? '/api/messages/unblock' : '/api/messages/block';
        const msg = isBlocked ? "Engeli kaldırmak istiyor musunuz?" : "Bu kullanıcıyı engellemek istiyor musunuz? Birbirinize mesaj gönderemezsiniz.";
        const verified = btn.dataset.senderVerified === 'true'; // This is a bit hacky, but let's just re-fetch for simplicity or pass it

        if (!confirm(msg)) return;

        fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nickname })
        }).then(res => res.json()).then(data => {
            if (data.success) {
                startConversation(nickname);
            }
        });
    }

    function showReportModalHelper(nickname) {
        showReportModal(nickname, 'Chat', (reason) => {
            return fetch('/api/user/report', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ nickname, reason, context: 'Chat' })
            }).then(res => res.ok);
        });
    }

    /* WebSocket Helpers for Real-time Messaging */
    function connectWebSocket() {
        const nickname = localStorage.getItem('wdiUserNickname');
        if (!nickname) return;

        if (stompClient && stompClient.connected) return;

        socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;

        stompClient.connect({}, function (frame) {
            stompClient.subscribe('/topic/messages/' + nickname, function (messageOutput) {
                try {
                    const data = JSON.parse(messageOutput.body);
                    handleIncomingWebSocketMessage(data);
                } catch (e) {
                    console.error("Error processing incoming WebSocket message:", e);
                }
            });
        }, function (error) {
            clearTimeout(reconnectTimeout);
            reconnectTimeout = setTimeout(connectWebSocket, 5000);
        });
    }

    function sendReadNotification(otherNickname) {
        if (stompClient && stompClient.connected && otherNickname) {
            stompClient.send("/app/chat.read", {}, JSON.stringify({
                otherNickname: otherNickname
            }));
        }
    }

    function handleIncomingWebSocketMessage(data) {
        const myNickname = localStorage.getItem('wdiUserNickname');

        if (data.type === 'READ_EVENT') {
            if (data.readBy === currentReceiver) {
                const ticks = document.querySelectorAll('.message-bubble.sent .message-status-tick');
                ticks.forEach(tick => {
                    tick.className = 'fas fa-check-double message-status-tick read';
                });
            }
            return;
        }

        // Check if message belongs to the active conversation
        const isForActiveChat = (data.senderNickname === currentReceiver || data.receiverNickname === currentReceiver);

        if (isForActiveChat) {
            appendIncomingMessage(data);
            if (data.senderNickname === currentReceiver) {
                playNotificationSound();
                sendReadNotification(currentReceiver);
            }
        } else if (data.senderNickname !== myNickname) {
            playNotificationSound();
            showToast(data);
        }

        // Instantly refresh sidebar to show last message & unread state
        fetchConversations();
    }

    function appendIncomingMessage(m) {
        const container = document.getElementById('chat-messages-container');
        if (!container) return;

        // Skip if message already exists
        if (container.querySelector(`[data-id="${m.id}"]`)) return;

        const myNickname = localStorage.getItem('wdiUserNickname');
        const isSent = m.senderNickname === myNickname;

        let statusIcon = '';
        if (isSent) {
            if (m.read) {
                statusIcon = '<i class="fas fa-check-double message-status-tick read"></i>';
            } else if (m.delivered) {
                statusIcon = '<i class="fas fa-check-double message-status-tick"></i>';
            } else {
                statusIcon = '<i class="fas fa-check message-status-tick"></i>';
            }
        }

        const emojiInfo = isOnlyEmojis(m.content);
        let bubbleClass = isSent ? 'sent' : 'received';
        if (emojiInfo.only && emojiInfo.count <= 2) {
            bubbleClass += ' only-emoji';
            if (emojiInfo.count === 1) bubbleClass += ' large';
            else if (emojiInfo.count === 2) bubbleClass += ' medium';
        }

        const html = `
            <div class="message-bubble ${bubbleClass}" data-id="${m.id}">
                ${m.content}
                <div class="message-footer">
                    <span class="message-time">${formatTime(new Date(m.timestamp))}</span>
                    ${isSent ? statusIcon : ''}
                </div>
            </div>
        `;

        container.insertAdjacentHTML('beforeend', html);
        container.scrollTop = container.scrollHeight;
    }

    let audioContext = null;
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
            startConversation(conv.senderNickname);
            toast.remove();
        };

        container.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('hide');
            setTimeout(() => toast.remove(), 400);
        }, 4000);
    }
});
