/**
 * StreamLive Schedule Balloon & Header Badge Manager
 * Fetches the active or next upcoming broadcast and displays a speech bubble balloon below the StreamLive button.
 * If no live or upcoming stream exists, the balloon remains hidden.
 */

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

const MONTH_NAMES_TR = [
    'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
    'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'
];

function formatScheduleTime(startTimeMillis) {
    const start = new Date(startTimeMillis);
    const now = new Date();

    const hours = String(start.getHours()).padStart(2, '0');
    const minutes = String(start.getMinutes()).padStart(2, '0');
    const timeStr = `${hours}.${minutes}`;

    const isToday = start.getDate() === now.getDate() &&
                    start.getMonth() === now.getMonth() &&
                    start.getFullYear() === now.getFullYear();

    const tomorrow = new Date(now);
    tomorrow.setDate(now.getDate() + 1);
    const isTomorrow = start.getDate() === tomorrow.getDate() &&
                      start.getMonth() === tomorrow.getMonth() &&
                      start.getFullYear() === tomorrow.getFullYear();

    if (isToday) {
        return `saat ${timeStr} da`;
    } else if (isTomorrow) {
        return `yarın saat ${timeStr} da`;
    } else {
        return `${start.getDate()} ${MONTH_NAMES_TR[start.getMonth()]} saat ${timeStr} da`;
    }
}

export function initStreamLiveScheduleBadge() {
    const balloon = document.getElementById('streamlive-schedule-balloon');
    const balloonText = document.getElementById('balloonText');
    const balloonLiveDot = document.getElementById('balloonLiveDot');
    const balloonIcon = document.getElementById('balloonIcon');
    const balloonClose = document.getElementById('balloonClose');
    const headerBtn = document.getElementById('streamlive-header-btn');

    if (!balloon || !balloonText) return;

    let currentDismissKey = null;

    if (balloonClose) {
        balloonClose.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            balloon.style.display = 'none';
            if (currentDismissKey) {
                sessionStorage.setItem('streamlive_schedule_dismissed', currentDismissKey);
            }
        });
    }

    function checkSchedule() {
        fetch('/api/streamlive/schedule')
            .then(res => {
                if (!res.ok || res.status === 204) {
                    throw new Error('No active schedule');
                }
                return res.json();
            })
            .then(data => {
                // Sadece halihazırda oynayan (LIVE) veya planlanmış (UPCOMING) yayın varsa göster
                if (!data || !data.title || (data.status !== 'LIVE' && data.status !== 'UPCOMING')) {
                    balloon.style.display = 'none';
                    if (headerBtn) headerBtn.classList.remove('has-live');
                    return;
                }

                const isLive = data.status === 'LIVE';
                currentDismissKey = data.id + '_' + data.status;
                const dismissedKey = sessionStorage.getItem('streamlive_schedule_dismissed');

                if (dismissedKey === currentDismissKey) {
                    balloon.style.display = 'none';
                    if (isLive && headerBtn) headerBtn.classList.add('has-live');
                    return;
                }

                const rawName = data.contentName || data.title;
                const name = escapeHtml(rawName);

                if (isLive) {
                    balloon.classList.add('is-live');
                    if (balloonLiveDot) balloonLiveDot.style.display = 'inline-block';
                    if (balloonIcon) balloonIcon.style.display = 'none';
                    // "saat 7 den icerik bitene kadar da Example su anda sinema odasinda yazsin"
                    balloonText.innerHTML = `<strong class="balloon-title">${name}</strong> <span class="balloon-desc">şu anda sinema odasında</span>`;
                    if (headerBtn) headerBtn.classList.add('has-live');
                } else if (data.status === 'UPCOMING') {
                    balloon.classList.remove('is-live');
                    if (headerBtn) headerBtn.classList.remove('has-live');
                    if (balloonLiveDot) balloonLiveDot.style.display = 'none';
                    if (balloonIcon) {
                        balloonIcon.className = 'fas fa-bullhorn balloon-icon';
                        balloonIcon.style.display = 'inline-block';
                    }
                    const timePhrase = formatScheduleTime(data.scheduledStartTime);
                    // "ornek veriyorum saat 06.00 ise orada Example saat 07.00 da sinema odasinda yazsin"
                    balloonText.innerHTML = `<strong class="balloon-title">${name}</strong> <span class="balloon-desc">${timePhrase} sinema odasında</span>`;
                }

                balloon.style.display = 'inline-flex';
            })
            .catch(() => {
                balloon.style.display = 'none';
                if (headerBtn) headerBtn.classList.remove('has-live');
            });
    }

    checkSchedule();
    // Re-check periodically every 60 seconds
    setInterval(checkSchedule, 60000);
}
