export function initNotifications() {
    const notificationsBtn = document.getElementById('notifications-btn');
    const notificationsDropdown = document.getElementById('notifications-dropdown');
    const notificationBadge = document.getElementById('notification-badge');
    const notificationsList = document.getElementById('notifications-list');

    if (!notificationsBtn || !notificationsDropdown) return;

    let currentNotifications = [];

    // Namespace viewed notifications per logged-in user
    const myNickname = localStorage.getItem('wdiUserNickname') || 'anonymous';
    const storageKey = `wdi_viewed_notifications_${myNickname}`;

    function getViewedNotificationIds() {
        try {
            return JSON.parse(localStorage.getItem(storageKey) || '[]');
        } catch (e) {
            return [];
        }
    }

    function markAllLoadedAsViewed(notifications) {
        const viewedIds = getViewedNotificationIds();
        let updated = false;
        notifications.forEach(n => {
            if (n.id && !viewedIds.includes(n.id)) {
                viewedIds.push(n.id);
                updated = true;
            }
        });
        if (updated) {
            localStorage.setItem(storageKey, JSON.stringify(viewedIds));
        }
    }

    // Fetch notifications on load
    fetchNotifications();

    // Toggle dropdown
    notificationsBtn.addEventListener('click', (e) => {
        // Prevent closing when clicking inside the dropdown
        if (e.target.closest('.notifications-dropdown')) return;

        const isOpening = !notificationsBtn.classList.contains('active');
        if (isOpening) {
            markAllLoadedAsViewed(currentNotifications);
            notificationBadge.style.display = 'none';
            // Instantly clear unread visual highlight from items
            const unreadItems = notificationsList.querySelectorAll('.notification-item.unread');
            unreadItems.forEach(item => item.classList.remove('unread'));
        }

        notificationsBtn.classList.toggle('active');
        notificationsDropdown.classList.toggle('active');
    });

    // Close on click outside
    document.addEventListener('click', (e) => {
        if (!notificationsBtn.contains(e.target)) {
            notificationsBtn.classList.remove('active');
            notificationsDropdown.classList.remove('active');
        }
    });

    async function fetchNotifications() {
        try {
            const response = await fetch('/api/notifications?limit=10');
            const notifications = await response.json();

            updateNotificationUI(notifications);
        } catch (error) {
            console.error('Bildirimler yüklenemedi:', error);
        }
    }

    function updateNotificationUI(notifications) {
        currentNotifications = notifications || [];

        if (currentNotifications.length === 0) {
            notificationsList.innerHTML = '<div class="notification-empty">Henüz bildirim yok.</div>';
            notificationBadge.style.display = 'none';
            return;
        }

        const viewedIds = getViewedNotificationIds();
        const unreadCount = currentNotifications.filter(n => !viewedIds.includes(n.id)).length;
        
        const isDropdownOpen = notificationsDropdown.classList.contains('active');

        if (isDropdownOpen) {
            markAllLoadedAsViewed(currentNotifications);
            notificationBadge.style.display = 'none';
        } else {
            if (unreadCount > 0) {
                notificationBadge.textContent = unreadCount;
                notificationBadge.style.display = 'flex';
            } else {
                notificationBadge.style.display = 'none';
            }
        }

        notificationsList.innerHTML = '';
        currentNotifications.forEach(notification => {
            const item = document.createElement('div');
            const isUnread = !viewedIds.includes(notification.id);
            item.className = 'notification-item' + (isUnread ? ' unread' : '');

            const timeStr = formatTime(new Date(notification.createdAt));
            const icon = notification.type === 'Movie' ? 'fas fa-film' : 'fas fa-tv';
            const link = `/${notification.slug || notification.contentId}`;

            item.innerHTML = `
                <div class="notification-icon">
                    <i class="${icon}"></i>
                </div>
                <div class="notification-content">
                    <div class="notification-title">${notification.title}</div>
                    <div class="notification-message">${notification.message}</div>
                    <div class="notification-time">${timeStr}</div>
                </div>
            `;

            item.addEventListener('click', () => {
                // Instantly record click
                if (notification.id) {
                    const ids = getViewedNotificationIds();
                    if (!ids.includes(notification.id)) {
                        ids.push(notification.id);
                        localStorage.setItem(storageKey, JSON.stringify(ids));
                    }
                }
                window.location.href = link;
            });

            notificationsList.appendChild(item);
        });
    }

    function formatTime(date) {
        const now = new Date();
        const diffInMs = now - date;
        const diffInMins = Math.floor(diffInMs / (1000 * 60));
        const diffInHours = Math.floor(diffInMs / (1000 * 60 * 60));
        const diffInDays = Math.floor(diffInMs / (1000 * 60 * 60 * 24));

        if (diffInMins < 1) return 'Az önce';
        if (diffInMins < 60) return `${diffInMins} dakika önce`;
        if (diffInHours < 24) return `${diffInHours} saat önce`;
        if (diffInDays < 7) return `${diffInDays} gün önce`;

        return date.toLocaleDateString('tr-TR');
    }
}
