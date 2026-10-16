import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initLoadedEpisodesSection() {
    const carousel = document.getElementById('upcoming-episodes-carousel');

    // To track and clear timers
    let countdownTimers = [];

    if (!carousel) {
        console.error('Upcoming Episodes Section: Carousel element not found.');
        return;
    }

    // Fetch and populate upcoming episodes
    fetchUpcomingData().then(data => {
        populateUpcomingCarousel(data);
    });

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

    function formatDate(dateString) {
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('tr-TR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
            });
        } catch (error) {
            console.error('Error formatting date:', error);
            return dateString;
        }
    }

    function calculateTimeRemaining(targetDate) {
        const now = new Date().getTime();
        const target = new Date(targetDate).getTime();
        const difference = target - now;

        // If the date is in the past
        if (difference <= 0) {
            return { isPast: true };
        }

        // Calculate time units
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

    function populateUpcomingCarousel(episodes) {
        clearAllCountdowns();
        carousel.innerHTML = '';

        if (episodes && episodes.length > 0) {
            // Sort episodes: future episodes from furthest to nearest, then past episodes
            const sortedEpisodes = [...episodes].sort((a, b) => {
                const aTime = calculateTimeRemaining(a.datetime);
                const bTime = calculateTimeRemaining(b.datetime);

                // If one is past and one is future, prioritize future
                if (aTime.isPast && !bTime.isPast) return 1;
                if (!aTime.isPast && bTime.isPast) return -1;

                // For future dates, sort by furthest first
                if (!aTime.isPast && !bTime.isPast) {
                    return new Date(b.datetime).getTime() - new Date(a.datetime).getTime();
                }

                // Keep original order for past dates
                return 0;
            });

            sortedEpisodes.forEach((episode, index) => {
                const formattedDate = formatDate(episode.datetime);
                const episodeId = `upcoming-countdown-${index}`;
                const timeRemaining = calculateTimeRemaining(episode.datetime);

                let countdownHtml;
                if (timeRemaining.isPast) {
                    countdownHtml = `<div class="upcoming-countdown">Çok Yakında</div>`;
                } else {
                    countdownHtml = `
                <div class="upcoming-countdown" id="${episodeId}">
                    <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.days}</span>gün</span>
                    <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.hours}</span>saat</span>
                    <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.minutes}</span>dk</span>
                    <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.seconds}</span>sn</span>
                </div>
            `;
                }

                const card = document.createElement('div');
                card.className = 'upcoming-card';
                card.onclick = () => {
                    const path = `/${episode.slug || episode.ID}`;
                    window.location.href = path;
                };
                card.innerHTML = `
                <div class="upcoming-card-image img-skeleton">
                    <img src="/media/image/${episode.ID}" alt="${episode.name}" onerror="this.style.display='none'; this.parentElement.classList.add('no-image');">
                    <div class="upcoming-play-icon">
                        <i class="fas fa-play"></i>
                    </div>
                    <div class="upcoming-no-image-placeholder">
                        <i class="fas fa-film"></i>
                    </div>
                </div>
                <div class="upcoming-card-content">
                    <h3 class="upcoming-card-title">${episode.name}</h3>
                    ${countdownHtml}
                </div>
                `;
                handleImageSkeleton(card.querySelector('img'));
                carousel.appendChild(card);
            });

            // Setup countdown timers for future episodes
            sortedEpisodes.forEach((episode, index) => {
                const timeRemaining = calculateTimeRemaining(episode.datetime);
                if (!timeRemaining.isPast) {
                    const countdownElement = document.getElementById(`upcoming-countdown-${index}`);
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
            }, 100);

            // Add navigation functionality
            setupCarouselNavigation();
        } else {
            carousel.innerHTML = `
                <div class="upcoming-no-data">
                    <div class="upcoming-no-data-icon">
                        <i class="far fa-clock"></i>
                    </div>
                    <div class="upcoming-no-data-text">
                        <h4>Yeni İçerikler Hazırlanıyor</h4>
                        <p>Ekibimiz sizin için en güncel bölümleri hazırlamak üzere arka planda çalışmaya devam ediyor.</p>
                    </div>
                </div>
            `;
            setTimeout(() => {
                carousel.style.opacity = '1';
            }, 100);
        }
    }

    function updateCountdown(element, targetDate) {
        const timeRemaining = calculateTimeRemaining(targetDate);

        if (timeRemaining.isPast) {
            element.innerHTML = 'Çok Yakında';
            return;
        }

        element.innerHTML = `
            <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.days}</span>gün</span>
            <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.hours}</span>saat</span>
            <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.minutes}</span>dk</span>
            <span class="upcoming-countdown-unit"><span class="upcoming-countdown-value">${timeRemaining.seconds}</span>sn</span>
        `;
    }

    function setupCarouselNavigation() {
        const prevBtn = document.querySelector('.upcoming-prev-btn');
        const nextBtn = document.querySelector('.upcoming-next-btn');

        if (prevBtn && nextBtn) {
            prevBtn.addEventListener('click', () => {
                carousel.scrollBy({ left: -800, behavior: 'smooth' });
            });

            nextBtn.addEventListener('click', () => {
                carousel.scrollBy({ left: 800, behavior: 'smooth' });
            });
        }
    }
}
