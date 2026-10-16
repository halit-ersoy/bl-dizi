import { initVideoControls } from './videoControls.js';
import { initListModal } from './listModal.js';
import { initCommentsSection } from './comments.js';
import { initContentDetails } from './contentDetails.js';
import { initEpisodeSelection } from './episodeSelection.js';
import { initSimilarContent } from './similarContent.js';
import { handleImageSkeleton } from '../../elements/userLogged.js';

document.addEventListener('DOMContentLoaded', async () => {
    const pathSegments = window.location.pathname.split('/').filter(Boolean);
    let videoId = null;

    if (pathSegments.length === 1 && pathSegments[0] !== 'watch') {
        const slug = pathSegments[0];
        try {
            const res = await fetch(`/api/video/resolve-slug?slug=${encodeURIComponent(slug)}`);
            if (res.ok) {
                const data = await res.json();
                videoId = data.id;
            } else {
                console.error("Slug not found:", slug);
                document.querySelector('.watch-container').innerHTML = '<h2>İçerik bulunamadı.</h2>';
                return;
            }
        } catch (e) {
            console.error("Error resolving slug:", e);
        }
    } else {
        const urlParams = new URLSearchParams(window.location.search);
        videoId = urlParams.get('id') || urlParams.get('slug');
    }

    if (!videoId) {
        document.querySelector('.watch-container').innerHTML = '<h2>Geçersiz bağlantı.</h2>';
        return;
    }

    // Check if content is upcoming
    let detailsData = null;
    try {
        const dRes = await fetch(`/api/video/details?id=${videoId}`);
        if (dRes.ok) {
            detailsData = await dRes.json();
        }
    } catch (err) {
        console.error("Error fetching video details:", err);
    }

    if (detailsData && detailsData.isUpcoming) {
        setupUpcomingWatch(detailsData);
        initListModal(videoId);
        initCommentsSection(videoId);
        initContentDetails(videoId);
        initSimilarContent(videoId);

        // Auto-expand content details section for upcoming content
        const detailsToggle = document.querySelector('.details-toggle');
        const detailsContainer = document.querySelector('.content-details-container');
        if (detailsToggle && detailsContainer) {
            detailsToggle.classList.add('active');
            detailsContainer.classList.add('open');
        }

        document.querySelectorAll('.rec-card .rec-image img').forEach(img => {
            handleImageSkeleton(img);
        });
        return;
    }

    // Standard video playback
    incrementViewCount(videoId);
    recordWatchHistory(videoId);

    initVideoControls(videoId);
    initListModal(videoId);
    initCommentsSection(videoId);
    initContentDetails(videoId);
    initEpisodeSelection(videoId);
    initSimilarContent(videoId);

    document.querySelectorAll('.rec-card .rec-image img').forEach(img => {
        handleImageSkeleton(img);
    });
});

async function incrementViewCount(id) {
    try {
        const res = await fetch(`/api/video/increment-view?id=${id}`, { method: 'POST' });
        if (!res.ok) console.error('View count artırılamadı');
    } catch (err) {
        console.error('View count hatası:', err);
    }
}

async function recordWatchHistory(id) {
    try {
        const token = localStorage.getItem('wdiUserToken');
        if (!token) return;
        await fetch(`/api/history/record?contentId=${encodeURIComponent(id)}`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch (err) {
        console.error('Watch history hatası:', err);
    }
}

function setupUpcomingWatch(data) {
    const titleEl = document.getElementById('title');
    const contentTitleEl = document.getElementById('contentTitle');
    const infoEl = document.getElementById('videoInfo');
    const switcherContainer = document.querySelector('.source-switcher-container');
    const episodeSection = document.getElementById('episodeSection');
    const videoControls = document.querySelector('.video-controls');
    const videoPlayer = document.getElementById('videoPlayer');
    const videoWrapper = document.querySelector('.video-wrapper');
    const videoSkeleton = document.querySelector('.video-skeleton');
    const adultOverlay = document.getElementById('adultOverlay');

    // Hide standard playback elements
    if (switcherContainer) switcherContainer.style.setProperty('display', 'none', 'important');
    if (episodeSection) episodeSection.style.setProperty('display', 'none', 'important');
    if (videoControls) videoControls.style.setProperty('display', 'none', 'important');
    if (videoPlayer) videoPlayer.style.setProperty('display', 'none', 'important');
    if (videoSkeleton) videoSkeleton.style.setProperty('display', 'none', 'important');
    if (adultOverlay) adultOverlay.style.setProperty('display', 'none', 'important');
    if (videoWrapper) videoWrapper.classList.add('loaded');

    // Setup Titles
    const typeText = (data.category && data.category.toLowerCase() === 'movie') ? 'Film' : 'Dizi';
    const dt = formatUpcomingDateTime(data.datetime);

    if (titleEl) titleEl.textContent = data.title;
    if (contentTitleEl) contentTitleEl.textContent = data.title;
    if (infoEl) {
        infoEl.innerHTML = `<span style="background: rgba(255,255,255,0.1); padding: 4px 10px; border-radius: 6px; font-weight: 700; color: #fff; margin-right: 8px;">${typeText}</span> <span style="color: rgba(255,255,255,0.7);"><i class="far fa-calendar-alt"></i> Yayın Tarihi: ${dt.date} ${dt.time ? '• ' + dt.time : ''}</span>`;
    }

    document.title = `${data.title} - Yakında`;

    // Overlay Elements
    const overlay = document.getElementById('upcomingPlayerOverlay');
    if (!overlay) return;

    overlay.style.display = 'flex';

    const bgEl = document.getElementById('upcomingPlayerBg');
    if (bgEl) {
        bgEl.style.backgroundImage = `url('/media/image/${data.id}')`;
    }

    const titleDisplay = document.getElementById('upcomingPlayerTitle');
    if (titleDisplay) titleDisplay.textContent = data.title;

    const catEl = document.getElementById('upcomingPlayerCategory');
    if (catEl) catEl.textContent = typeText.toUpperCase();

    const dateEl = document.getElementById('upcomingPlayerDate');
    if (dateEl) dateEl.innerHTML = `<i class="far fa-calendar-alt"></i> ${dt.date}`;

    const timeEl = document.getElementById('upcomingPlayerTime');
    if (timeEl) {
        timeEl.innerHTML = dt.time ? `<i class="far fa-clock"></i> ${dt.time}` : '';
        timeEl.style.display = dt.time ? 'inline-flex' : 'none';
    }

    const timerContainer = document.getElementById('upcomingPlayerTimer');
    const expiredContainer = document.getElementById('upcomingPlayerExpired');
    const badgeText = document.getElementById('upcomingBadgeText');

    function updatePlayerTimer() {
        const remaining = calculateUpcomingRemaining(data.datetime);
        if (remaining.isPast) {
            if (timerContainer) timerContainer.style.display = 'none';
            if (expiredContainer) expiredContainer.style.display = 'flex';
            if (badgeText) badgeText.textContent = 'YAKINDA';
            return true;
        }

        if (timerContainer) timerContainer.style.display = 'flex';
        if (expiredContainer) expiredContainer.style.display = 'none';
        if (badgeText) badgeText.textContent = 'YAYINA KALAN SÜRE';

        const dEl = document.getElementById('playerDays');
        const hEl = document.getElementById('playerHours');
        const mEl = document.getElementById('playerMinutes');
        const sEl = document.getElementById('playerSeconds');

        if (dEl) dEl.textContent = String(remaining.days).padStart(2, '0');
        if (hEl) hEl.textContent = String(remaining.hours).padStart(2, '0');
        if (mEl) mEl.textContent = String(remaining.minutes).padStart(2, '0');
        if (sEl) sEl.textContent = String(remaining.seconds).padStart(2, '0');

        return false;
    }

    const isFinished = updatePlayerTimer();
    if (!isFinished) {
        const interval = setInterval(() => {
            const finishedNow = updatePlayerTimer();
            if (finishedNow) {
                clearInterval(interval);
            }
        }, 1000);
    }
}

function calculateUpcomingRemaining(targetDate) {
    if (!targetDate) return { isPast: true, days: 0, hours: 0, minutes: 0, seconds: 0 };
    const now = Date.now();
    const target = new Date(targetDate).getTime();
    const difference = target - now;

    if (isNaN(difference) || difference <= 0) {
        return { isPast: true, days: 0, hours: 0, minutes: 0, seconds: 0 };
    }

    const days = Math.floor(difference / (1000 * 60 * 60 * 24));
    const hours = Math.floor((difference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((difference % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((difference % (1000 * 60)) / 1000);

    return { isPast: false, days, hours, minutes, seconds };
}

function formatUpcomingDateTime(dateString) {
    try {
        if (!dateString) return { date: '', time: '' };
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return { date: dateString, time: '' };
        const formattedDate = date.toLocaleDateString('tr-TR', {
            day: '2-digit',
            month: 'long',
            year: 'numeric'
        });
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return {
            date: formattedDate,
            time: (hours === '00' && minutes === '00') ? '' : `${hours}:${minutes}`
        };
    } catch (e) {
        return { date: dateString, time: '' };
    }
}
