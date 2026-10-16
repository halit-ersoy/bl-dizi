import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initLoadedEpisodesSection() {
    const carousel = document.getElementById('upcoming-episodes-carousel');
    const tvToggle = document.getElementById('upcoming-tv-toggle');
    const moviesToggle = document.getElementById('upcoming-movies-toggle');
    const toggleContainer = document.querySelector('.upcoming-episodes-toggle');

    if (!carousel) {
        console.error('Upcoming Episodes Section: Carousel element not found.');
        return;
    }

    let allUpcomingEpisodes = [];
    let currentMode = 'tv'; // 'tv' (Diziler) or 'movies' (Filmler)
    let countdownTimers = [];

    // Setup navigation buttons once
    setupCarouselNavigation();

    // Setup toggle buttons
    if (tvToggle && moviesToggle && toggleContainer) {
        tvToggle.addEventListener('click', () => {
            if (currentMode !== 'tv') {
                switchMode('tv');
            }
        });

        moviesToggle.addEventListener('click', () => {
            if (currentMode !== 'movies') {
                switchMode('movies');
            }
        });
    }

    // Fetch and initialize
    fetchUpcomingData().then(data => {
        allUpcomingEpisodes = Array.isArray(data) ? data : [];
        renderCurrentMode();
    });

    function switchMode(mode) {
        currentMode = mode;
        updateToggleState();

        carousel.classList.add('fade-out');
        setTimeout(() => {
            renderCurrentMode();
            carousel.classList.remove('fade-out');
        }, 200);
    }

    function updateToggleState() {
        if (!tvToggle || !moviesToggle || !toggleContainer) return;
        tvToggle.classList.toggle('active', currentMode === 'tv');
        moviesToggle.classList.toggle('active', currentMode === 'movies');
        toggleContainer.classList.toggle('movies-active', currentMode === 'movies');
    }

    function isTvType(type) {
        if (!type) return true;
        const t = String(type).trim().toLowerCase();
        return t === 'soapopera' || t === 'series' || t === 'tv' || t === 'soap_opera' || t === 'dizi';
    }

    function isMovieType(type) {
        if (!type) return false;
        const t = String(type).trim().toLowerCase();
        return t === 'movie' || t === 'film';
    }

    function renderCurrentMode() {
        const filtered = allUpcomingEpisodes.filter(item => {
            return currentMode === 'tv' ? isTvType(item.type) : isMovieType(item.type);
        });
        populateUpcomingCarousel(filtered);
    }

    function clearAllCountdowns() {
        countdownTimers.forEach(timer => clearInterval(timer));
        countdownTimers = [];
    }

    function fetchUpcomingData() {
        return fetch('/api/saved/loaded')
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Network response was not ok (status: ${response.status})`);
                }
                return response.json();
            })
            .catch(error => {
                console.error('Error fetching loaded episodes:', error);
                return [];
            });
    }

    function formatDateTime(dateString) {
        try {
            if (!dateString) return { date: '', time: '' };
            const date = new Date(dateString);
            if (isNaN(date.getTime())) {
                return { date: dateString, time: '' };
            }
            const formattedDate = date.toLocaleDateString('tr-TR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
            });
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            return {
                date: formattedDate,
                time: (hours === '00' && minutes === '00') ? '' : `${hours}:${minutes}`
            };
        } catch (error) {
            console.error('Error formatting date:', error);
            return { date: dateString, time: '' };
        }
    }

    function calculateTimeRemaining(targetDate) {
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

        return {
            isPast: false,
            days: days,
            hours: hours,
            minutes: minutes,
            seconds: seconds
        };
    }

    function getCountdownBadgeHtml(timeRemaining, episodeId) {
        if (timeRemaining.isPast) {
            return `<div class="upcoming-status-pill" id="${episodeId}"><i class="fas fa-bolt"></i> Yakında Yüklenecek</div>`;
        }
        const dStr = String(timeRemaining.days).padStart(2, '0');
        const hStr = String(timeRemaining.hours).padStart(2, '0');
        const mStr = String(timeRemaining.minutes).padStart(2, '0');
        const sStr = String(timeRemaining.seconds).padStart(2, '0');

        return `
            <div class="upcoming-countdown-pill" id="${episodeId}">
                <div class="upcoming-cd-item"><span class="upcoming-cd-val">${dStr}</span><span class="upcoming-cd-unit">g</span></div>
                <span class="upcoming-cd-colon">:</span>
                <div class="upcoming-cd-item"><span class="upcoming-cd-val">${hStr}</span><span class="upcoming-cd-unit">s</span></div>
                <span class="upcoming-cd-colon">:</span>
                <div class="upcoming-cd-item"><span class="upcoming-cd-val">${mStr}</span><span class="upcoming-cd-unit">d</span></div>
                <span class="upcoming-cd-colon">:</span>
                <div class="upcoming-cd-item"><span class="upcoming-cd-val">${sStr}</span><span class="upcoming-cd-unit">s</span></div>
            </div>
        `;
    }

    function populateUpcomingCarousel(episodes) {
        clearAllCountdowns();
        carousel.innerHTML = '';

        if (episodes && episodes.length > 0) {
            const now = Date.now();
            // Sort episodes: Future episodes soonest first, then past episodes
            const sortedEpisodes = [...episodes].sort((a, b) => {
                const timeA = new Date(a.datetime).getTime();
                const timeB = new Date(b.datetime).getTime();
                const isPastA = isNaN(timeA) || timeA <= now;
                const isPastB = isNaN(timeB) || timeB <= now;

                if (!isPastA && isPastB) return -1;
                if (isPastA && !isPastB) return 1;

                if (!isPastA && !isPastB) {
                    return timeA - timeB;
                }

                return timeB - timeA;
            });

            sortedEpisodes.forEach((episode, index) => {
                const dt = formatDateTime(episode.datetime);
                const episodeId = `upcoming-countdown-${currentMode}-${index}`;
                const timeRemaining = calculateTimeRemaining(episode.datetime);
                const badgeHtml = getCountdownBadgeHtml(timeRemaining, episodeId);
                const typeText = isMovieType(episode.type) ? 'Film' : 'Dizi';

                const card = document.createElement('div');
                card.className = 'upcoming-card';
                card.onclick = () => {
                    const path = `/${episode.slug || episode.upcomingId || episode.ID}`;
                    window.location.href = path;
                };

                card.innerHTML = `
                <div class="upcoming-card-image img-skeleton">
                    <span class="upcoming-type-tag">${typeText}</span>
                    <img src="/media/image/${episode.upcomingId || episode.ID}" alt="${episode.name || ''}" onerror="this.style.display='none'; this.parentElement.classList.add('no-image');">
                    <div class="upcoming-play-icon">
                        <i class="fas fa-play"></i>
                    </div>
                    <div class="upcoming-no-image-placeholder">
                        <i class="fas fa-film"></i>
                    </div>
                    <div class="upcoming-poster-overlay">
                        ${badgeHtml}
                    </div>
                </div>
                <div class="upcoming-card-content">
                    <h3 class="upcoming-card-title" title="${episode.name || ''}">${episode.name || ''}</h3>
                    <div class="upcoming-card-footer">
                        <span class="upcoming-card-date"><i class="far fa-calendar-alt"></i> ${dt.date}</span>
                        ${dt.time ? `<span class="upcoming-card-time"><i class="far fa-clock"></i> ${dt.time}</span>` : ''}
                    </div>
                </div>
                `;

                handleImageSkeleton(card.querySelector('img'));
                carousel.appendChild(card);
            });

            // Setup countdown timers for future episodes
            sortedEpisodes.forEach((episode, index) => {
                const timeRemaining = calculateTimeRemaining(episode.datetime);
                if (!timeRemaining.isPast) {
                    const countdownElement = document.getElementById(`upcoming-countdown-${currentMode}-${index}`);
                    if (countdownElement) {
                        const timer = setInterval(() => {
                            updateCountdown(countdownElement, episode.datetime);
                        }, 1000);
                        countdownTimers.push(timer);
                    }
                }
            });

            setTimeout(() => {
                carousel.style.opacity = '1';
            }, 50);
        } else {
            const categoryLabel = currentMode === 'tv' ? 'dizi' : 'film';
            carousel.innerHTML = `
                <div class="upcoming-no-data">
                    <div class="upcoming-no-data-icon">
                        <i class="far fa-clock"></i>
                    </div>
                    <div class="upcoming-no-data-text">
                        <h4>Yeni İçerikler Hazırlanıyor</h4>
                        <p>Şu anda planlanan ${categoryLabel} bulunmuyor. Yeni içerikler eklendiğinde burada listelenecektir.</p>
                    </div>
                </div>
            `;
            setTimeout(() => {
                carousel.style.opacity = '1';
            }, 50);
        }
    }

    function updateCountdown(element, targetDate) {
        const timeRemaining = calculateTimeRemaining(targetDate);

        if (timeRemaining.isPast) {
            element.className = 'upcoming-status-pill';
            element.innerHTML = '<i class="fas fa-bolt"></i> Yakında Yüklenecek';
            return;
        }

        const dStr = String(timeRemaining.days).padStart(2, '0');
        const hStr = String(timeRemaining.hours).padStart(2, '0');
        const mStr = String(timeRemaining.minutes).padStart(2, '0');
        const sStr = String(timeRemaining.seconds).padStart(2, '0');

        element.className = 'upcoming-countdown-pill';
        element.innerHTML = `
            <div class="upcoming-cd-item"><span class="upcoming-cd-val">${dStr}</span><span class="upcoming-cd-unit">g</span></div>
            <span class="upcoming-cd-colon">:</span>
            <div class="upcoming-cd-item"><span class="upcoming-cd-val">${hStr}</span><span class="upcoming-cd-unit">s</span></div>
            <span class="upcoming-cd-colon">:</span>
            <div class="upcoming-cd-item"><span class="upcoming-cd-val">${mStr}</span><span class="upcoming-cd-unit">d</span></div>
            <span class="upcoming-cd-colon">:</span>
            <div class="upcoming-cd-item"><span class="upcoming-cd-val">${sStr}</span><span class="upcoming-cd-unit">s</span></div>
        `;
    }

    function setupCarouselNavigation() {
        const prevBtn = document.querySelector('.upcoming-prev-btn');
        const nextBtn = document.querySelector('.upcoming-next-btn');

        if (prevBtn && nextBtn) {
            prevBtn.addEventListener('click', () => {
                carousel.scrollBy({ left: -600, behavior: 'smooth' });
            });

            nextBtn.addEventListener('click', () => {
                carousel.scrollBy({ left: 600, behavior: 'smooth' });
            });
        }
    }
}