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

    if (pathSegments.length === 1) {
        const slug = pathSegments[0];
        try {
            const res = await fetch(`/api/video/resolve-slug?slug=${slug}`);
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
    }

    if (!videoId) {
        document.querySelector('.watch-container').innerHTML = '<h2>Geçersiz bağlantı.</h2>';
        return;
    }

    // Increment view count on page open (works for both native player and external sources)
    incrementViewCount(videoId);

    // Record user watch history if authenticated
    recordWatchHistory(videoId);

    initVideoControls(videoId);
    initListModal(videoId);
    initCommentsSection(videoId);
    initContentDetails(videoId);
    initEpisodeSelection(videoId);
    initSimilarContent(videoId);

    // Recommendations items are static in HTML for now (placeholder)
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
        const token = localStorage.getItem('bldiziUserToken') || localStorage.getItem('wdiUserToken') || document.cookie.includes('wdiAuth');
        if (!token) return; // Only authenticated users have watch history
        await fetch(`/api/history/record?contentId=${encodeURIComponent(id)}`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch (err) {
        console.error('Watch history hatası:', err);
    }
}
