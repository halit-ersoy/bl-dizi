import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initHeroCarousel() {
    // Elements and state for carousel
    const heroSection = document.querySelector('.hero');
    const heroVideosContainer = document.querySelector('.hero-videos');
    const prevHeroBtn = document.querySelector('.prev-hero');
    const nextHeroBtn = document.querySelector('.next-hero');
    const heroIndicatorsContainer = document.querySelector('.hero-indicators');
    const stopTrailerBtn = document.getElementById('stopTrailerBtn');

    // Carousel state
    let currentHeroIndex = 0;
    let heroInterval = null;
    let currentTrailerButton = null; // Store reference to the current trailer button
    let heroList = [];
    let activeHlsInstances = {}; // Store active Hls.js instances keyed by slide index
    const autoPlayDuration = 30000; // Default 30 seconds for videos

    // Fetch hero data from API with platform=bldizi parameter
    async function fetchHeroData() {
        try {
            const response = await fetch('/api/hero/videos?platform=bldizi');
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return await response.json();
        } catch (error) {
            console.error('Hero Section - Error:', error);
            return [];
        }
    }

    // Generate HTML for hero elements
    function createHeroElements(heroVideos) {
        const containers = [];
        const indicators = [];

        heroVideos.forEach((hero, index) => {
            // Create container element
            const container = document.createElement('div');
            container.className = `hero-video-container ${index === 0 ? 'active' : ''}`;
            container.dataset.index = index;

            const category = hero.category ? hero.category.replace(/,$/, '') : 'BL Dizi';
            const typeText = (hero.type || 'BL DİZİ').toUpperCase().replace('SOAPOPERA', 'DİZİ').replace('MOVIE', 'FİLM');

            const isImageValue = hero.isImage !== undefined ? hero.isImage : 
                                 (hero.IsImage !== undefined ? hero.IsImage : 
                                 (hero.ISIMAGE !== undefined ? hero.ISIMAGE : 
                                 (hero.isimage !== undefined ? hero.isimage : true)));
            const isImage = isImageValue === true || isImageValue === 1 || isImageValue === "1" || isImageValue === "true";

            const heroId = hero.ID || hero.id;
            const refId = hero.ReferenceId || hero.referenceId || heroId;
            const heroName = hero.name || hero.title || 'İsimsiz İçerik';
            const heroSlug = hero.slug || hero.Slug || '';
            const heroSummary = hero._content || hero.summary || 'İçerik açıklaması bulunmuyor';
            const targetUrl = heroSlug ? (heroSlug.startsWith('/') ? heroSlug : '/' + heroSlug) : (refId ? '/' + refId : '#');

            const mediaHtml = isImage ? `
                <div class="img-skeleton">
                    <img class="hero-bg" src="/media/image/${heroId}?v=2" alt="${heroName}" onerror="if (this.src.indexOf('${refId}') === -1) this.src='/media/image/${refId}?v=2';">
                </div>
            ` : `
                <video class="hero-bg-video" data-hero-id="${heroId}" data-index="${index}" muted playsinline loop>
                    <div class="img-skeleton">
                        <img class="hero-bg" src="/media/image/${heroId}?v=2" alt="${heroName}" onerror="if (this.src.indexOf('${refId}') === -1) this.src='/media/image/${refId}?v=2';">
                    </div>
                </video>
            `;

            const trailerButtonHtml = !isImage ? `
                <button class="glow-button trailer-btn" data-index="${index}">
                    <i class="fas fa-film"></i> FRAGMANI İZLE
                </button>
            ` : '';

            // Generate container HTML
            container.innerHTML = `
                ${mediaHtml}
                <div class="hero-overlay"></div>
                <div class="hero-content animate-fade-in">
                    <div class="hero-badge animate-slide-up">${typeText}</div>
                    <h1 class="animate-slide-up" style="cursor: pointer;" onclick="window.location.href='${targetUrl}'">${heroName}</h1>
                    <p class="animate-slide-up">${heroSummary}</p>
                    <div class="hero-info animate-slide-up">
                        <div class="hero-stat"><i class="fas fa-film"></i> ${category}</div>
                    </div>
                    <div class="hero-buttons animate-slide-up">
                        <button onclick="window.location.href='${targetUrl}'" class="pulse-button">
                            <i class="fas fa-play"></i> ŞİMDİ İZLE
                        </button>
                        ${trailerButtonHtml}
                    </div>
                </div>
            `;
            containers.push(container);

            // Create indicator element
            const indicator = document.createElement('span');
            indicator.className = `hero-indicator ${index === 0 ? 'active' : ''}`;
            indicator.dataset.index = index;
            indicators.push(indicator);
        });

        return { containers, indicators };
    }

    // Initialize hero section with data from database
    async function initHeroDataSection() {
        try {
            // Fetch hero videos from API
            const heroVideos = await fetchHeroData();

            if (!heroVideos || heroVideos.length === 0) {
                console.error('No hero videos found');
                return;
            }

            heroList = heroVideos;
            console.log('[Hero Autoplay] Hero List loaded:', heroList);

            // Clear existing content
            heroVideosContainer.innerHTML = '';
            heroIndicatorsContainer.innerHTML = '';

            // Create hero elements
            const { containers, indicators } = createHeroElements(heroVideos);

            // Add containers to DOM
            containers.forEach(container => {
                heroVideosContainer.appendChild(container);
                // Handle skeleton for the background image
                const img = container.querySelector('.hero-bg');
                handleImageSkeleton(img);
            });

            // Add indicators to DOM
            indicators.forEach(indicator => {
                heroIndicatorsContainer.appendChild(indicator);
            });

            // Setup event listeners after elements are created
            setupEventListeners();
            showHeroSlide(0); // Trigger HLS initialization and autoplay for slide 0

        } catch (error) {
            console.error('Failed to initialize hero section:', error);
        }
    }

    // Carousel functions
    function showHeroSlide(index) {
        const heroContainers = document.querySelectorAll('.hero-video-container');
        const heroIndicators = document.querySelectorAll('.hero-indicator');

        // Handle index wrapping
        if (index < 0) index = heroContainers.length - 1;
        if (index >= heroContainers.length) index = 0;

        // Update current index
        currentHeroIndex = index;

        // Remove active class from all containers and destroy videos
        heroContainers.forEach((container, idx) => {
            container.classList.remove('active');
            const video = container.querySelector('video');
            if (video) {
                video.pause();
                
                // Destroy HLS instance for inactive slides to save resources
                if (activeHlsInstances[idx]) {
                    console.log(`[Hero Autoplay] Destroying HLS instance for slide ${idx}`);
                    activeHlsInstances[idx].destroy();
                    delete activeHlsInstances[idx];
                }
                video.src = '';
                video.removeAttribute('src');
                video.dataset.hlsInitialized = 'false';
                video.muted = true;
            }

            // Reset any trailer buttons that might have been changed
            resetTrailerButtons(container);
        });

        // Remove active class from all indicators
        heroIndicators.forEach(indicator => {
            indicator.classList.remove('active');
        });

        // Add active class to current container and indicator
        heroContainers[index].classList.add('active');
        heroIndicators[index].classList.add('active');

        // Play the video for current slide using HLS / MP4
        const currentVideo = heroContainers[index].querySelector('video');
        if (currentVideo) {
            const heroId = currentVideo.dataset.heroId;
            const playlistUrl = `/media/video/${heroId}/playlist.m3u8`;
            const mp4Url = `/media/video/${heroId}`;

            if (currentVideo.dataset.hlsInitialized !== 'true') {
                console.log(`[Hero Autoplay] Initializing video for slide ${index}, heroId: ${heroId}`);
                if (window.Hls && Hls.isSupported()) {
                    const hls = new Hls({
                        maxMaxBufferLength: 10,
                        enableWorker: true
                    });
                    hls.loadSource(playlistUrl);
                    hls.attachMedia(currentVideo);
                    hls.on(Hls.Events.MANIFEST_PARSED, () => {
                        currentVideo.play().catch(e => {
                            if (e.name !== 'AbortError') console.warn('HLS autoplay failed:', e);
                        });
                    });
                    hls.on(Hls.Events.ERROR, (event, data) => {
                        if (data.fatal) {
                            console.warn('[Hero Video] HLS error, falling back to MP4:', data);
                            hls.destroy();
                            delete activeHlsInstances[index];
                            currentVideo.src = mp4Url;
                            currentVideo.play().catch(e => {
                                if (e.name !== 'AbortError') console.warn('MP4 fallback autoplay failed:', e);
                            });
                        }
                    });
                    activeHlsInstances[index] = hls;
                } else if (currentVideo.canPlayType('application/vnd.apple.mpegurl')) {
                    currentVideo.src = playlistUrl;
                    currentVideo.play().catch(e => {
                        if (e.name !== 'AbortError') console.warn('Native HLS autoplay failed, trying MP4:', e);
                        currentVideo.src = mp4Url;
                        currentVideo.play().catch(err => {});
                    });
                } else {
                    currentVideo.src = mp4Url;
                    currentVideo.play().catch(e => {});
                }
                currentVideo.dataset.hlsInitialized = 'true';
            } else {
                currentVideo.play().catch(e => {});
            }
        }

        // Reset auto-play timer
        resetAutoPlay();
    }

    function startAutoPlay() {
        if (heroInterval) clearTimeout(heroInterval);

        const currentHero = heroList[currentHeroIndex];
        let duration = autoPlayDuration; // 30 seconds for videos

        if (currentHero) {
            const isImageValue = currentHero.isImage !== undefined ? currentHero.isImage : 
                                 (currentHero.IsImage !== undefined ? currentHero.IsImage : 
                                 (currentHero.ISIMAGE !== undefined ? currentHero.ISIMAGE : 
                                 (currentHero.isimage !== undefined ? currentHero.isimage : true)));
            const isImage = isImageValue === true || isImageValue === 1 || isImageValue === "1" || isImageValue === "true";

            if (isImage) {
                duration = 8000; // 8 seconds for images
            }
            console.log(`[Hero Autoplay] Slide Index: ${currentHeroIndex}, Name: ${currentHero.name || currentHero.title}, Duration: ${duration}ms, isImage: ${isImage}`);
        } else {
            console.log(`[Hero Autoplay] Slide Index: ${currentHeroIndex} - currentHero is undefined!`);
        }

        heroInterval = setTimeout(() => {
            showHeroSlide(currentHeroIndex + 1);
        }, duration);
    }

    function resetAutoPlay() {
        if (heroInterval) clearTimeout(heroInterval);
        startAutoPlay();
    }

    function resetTrailerButtons(container) {
        // Find the trailer button in this container
        const trailerBtn = container.querySelector('.trailer-btn');
        if (trailerBtn) {
            // Reset button text and icon to original state
            trailerBtn.innerHTML = '<i class="fas fa-film"></i> FRAGMANI İZLE';
            trailerBtn.classList.remove('watching-trailer');
        }
    }

    function setupEventListeners() {
        const heroIndicators = document.querySelectorAll('.hero-indicator');

        // Navigation buttons
        prevHeroBtn.addEventListener('click', () => {
            showHeroSlide(currentHeroIndex - 1);
        });

        nextHeroBtn.addEventListener('click', () => {
            showHeroSlide(currentHeroIndex + 1);
        });

        // Swipe support for mobile
        let touchStartX = 0;
        let touchEndX = 0;

        heroSection.addEventListener('touchstart', (e) => {
            touchStartX = e.changedTouches[0].screenX;
        }, { passive: true });

        heroSection.addEventListener('touchend', (e) => {
            touchEndX = e.changedTouches[0].screenX;
            handleSwipe();
        }, { passive: true });

        function handleSwipe() {
            const swipeThreshold = 50;
            if (touchEndX < touchStartX - swipeThreshold) {
                // Swiped left -> next slide
                showHeroSlide(currentHeroIndex + 1);
            } else if (touchEndX > touchStartX + swipeThreshold) {
                // Swiped right -> previous slide
                showHeroSlide(currentHeroIndex - 1);
            }
        }

        // Indicator clicks
        heroIndicators.forEach(indicator => {
            indicator.addEventListener('click', () => {
                const index = parseInt(indicator.dataset.index);
                showHeroSlide(index);
            });
        });

        // Trailer buttons
        document.querySelectorAll('.trailer-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                // If button is currently in "stop" mode, call stopTrailer
                if (btn.classList.contains('watching-trailer')) {
                    stopTrailer();
                } else {
                    // Otherwise play the trailer
                    const index = parseInt(btn.dataset.index);
                    playTrailer(index, btn);
                }
            });
        });
    }

    function playTrailer(index, button) {
        const heroContainers = document.querySelectorAll('.hero-video-container');
        const video = heroContainers[index].querySelector('video');

        if (video) {
            // Store reference to the current button
            currentTrailerButton = button;

            // Change button to "STOP" mode
            button.innerHTML = '<i class="fas fa-pause"></i> İZLEMEYİ DURDUR';
            button.classList.add('watching-trailer');

            // Unmute and play video without resetting position
            video.muted = false;
            video.play();

            // Pause autoplay while trailer is playing
            clearTimeout(heroInterval);
        }
    }

    function stopTrailer() {
        const activeContainer = document.querySelector('.hero-video-container.active');
        if (!activeContainer) return;
        const video = activeContainer.querySelector('video');

        if (video) {
            // Mute video but don't reset position
            video.muted = true;
            video.play();

            // Reset the trailer button
            if (currentTrailerButton) {
                currentTrailerButton.innerHTML = '<i class="fas fa-film"></i> FRAGMANI İZLE';
                currentTrailerButton.classList.remove('watching-trailer');
                currentTrailerButton = null;
            }

            // Resume autoplay
            startAutoPlay();
        }
    }

    // Initialize hero section with data
    initHeroDataSection();
}
