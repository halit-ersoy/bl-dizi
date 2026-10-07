import { initHeaderScroll } from './headerScroll.js?v=2';
import { initHeroCarousel } from './heroCarousel.js?v=3';
import { initContentCarousels } from './contentCarousel.js?v=2';
import { initNewSoapOperasSection } from './newSoapOperasSection.js?v=2';
import { initNewMoviesSection } from './newMoviesSection.js?v=2';
import { initWeeklyBestSection } from './weeklyBestSection.js?v=2';
import { initLogin } from './login.js?v=2';
import { initRegister } from './register.js?v=2';
import { initForgotPass } from './forgot-pass.js?v=2';
import { initFeaturedContent } from "./featuredContent.js?v=2";
import { initLoadedEpisodesSection } from "./loadedEpisodesSection.js?v=2";
import { initHeaderInteractions, initSearchExpansion } from "./searchExpansion.js?v=2";
import { initCalendar } from "./calendar.js?v=2";
import { initNotifications } from "./notifications.js?v=2";
import { initFeedback } from "./feedback.js?v=2";
import { initMessagingManager } from "./messagingManager.js?v=2";
import { initAiSearch } from "./aiSearch.js?v=1";

window.addEventListener('load', () => {
    const loadingScreen = document.getElementById('loading-screen');
    if (loadingScreen) {
        loadingScreen.classList.add('fade-out');
        setTimeout(() => {
            loadingScreen.remove();
        }, 800);
    }
});

document.addEventListener('DOMContentLoaded', () => {
    initHeaderScroll();
    initSearchExpansion();
    initHeaderInteractions();
    initHeroCarousel();
    initContentCarousels();
    initFeaturedContent();
    initNewSoapOperasSection();
    initNewMoviesSection();
    initWeeklyBestSection();
    initLoadedEpisodesSection();
    initLogin();
    initRegister();
    initForgotPass();
    initCalendar();
    initNotifications();
    initFeedback();
    initMessagingManager();
    initAnnouncement();
    initAiSearch();
});

function initAnnouncement() {
    const bar = document.getElementById('announcement-bar');
    const textSpan = document.getElementById('announcement-text');

    if (!bar || !textSpan) return;

    const cachedData = sessionStorage.getItem('announcementData');
    if (cachedData) {
        try {
            const data = JSON.parse(cachedData);
            if (data.active && data.text) {
                textSpan.textContent = data.text;
                bar.style.display = 'block';
                return;
            }
        } catch (e) {
            console.error('Cached announcement parse error:', e);
        }
    }

    fetch('/api/announcements/active')
        .then(res => {
            if (!res.ok) throw new Error('No active announcement');
            return res.json();
        })
        .then(data => {
            if (data && data.text) {
                textSpan.textContent = data.text;
                bar.style.display = 'block';
                sessionStorage.setItem('announcementData', JSON.stringify({ active: true, text: data.text }));
            } else {
                bar.style.display = 'none';
                sessionStorage.setItem('announcementData', JSON.stringify({ active: false }));
            }
        })
        .catch(err => {
            bar.style.display = 'none';
            sessionStorage.setItem('announcementData', JSON.stringify({ active: false }));
        });
}
