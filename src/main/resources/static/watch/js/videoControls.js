// videoControls.js
export function initVideoControls(videoId) {
    const SKIP_SECONDS = 10;
    let isAdPlaying = false;
    let activeHls = null;
    const videoPlayer = document.getElementById('videoPlayer');
    const videoSource = document.getElementById('videoSource');
    const volumeControl = document.getElementById('volumeControl');
    const backwardBtn = document.getElementById('backwardButton');
    const forwardBtn = document.getElementById('forwardButton');
    const fullscreenBtn = document.getElementById('fullscreenButton');
    const playerContainer = document.getElementById('playerContainer');
    const playPauseWrapper = document.querySelector('.video-wrapper');
    const titleEl = document.getElementById('title');
    const infoEl = document.getElementById('videoInfo');
    const prevEpisodeBtn = document.getElementById('prevEpisode');
    const nextEpisodeBtn = document.getElementById('nextEpisode');

    if (!videoPlayer || !videoSource || !volumeControl || !playPauseWrapper) {
        console.error('Gerekli video oynatıcı elementleri bulunamadı.');
        return;
    }

    hideNativeVolumeControls();
    const playButton = createPlayPauseButton();
    setupVolumeControl();
    setupSkipButtons();
    setupFullscreenToggle();
    setupProgressBar();
    setupPlaybackSpeed();
    setupKeyboardShortcuts();
    syncVolumeOnMetadata();
    setupViewCount();
    setupPlaybackEvents();
    setupErrorHandling();
    setupInactivityTimer();
    // loadVideo(videoId); // Moved to initializeContent
    // loadSources(videoId); // Moved to initializeContent
    // setupEpisodeNavigation(videoId); // Handled by episodeSelection.js
    disableNativeShortcutsOnVideo();

    initializeContent(videoId);

    // --- Fonksiyonlar ---

    function hideNativeVolumeControls() {
        const style = document.createElement('style');
        style.textContent = `
      video::-webkit-media-controls-volume-slider,
      video::-webkit-media-controls-mute-button {
        display: none !important;
      }
    `;
        document.head.appendChild(style);
    }

    function createPlayPauseButton() {
        const btn = document.createElement('div');
        btn.className = 'play-pause-button';
        btn.innerHTML = '<i class="fas fa-play"></i>';
        playPauseWrapper.appendChild(btn);

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            videoPlayer.paused ? videoPlayer.play() : videoPlayer.pause();
            updatePlayIcon();
        });

        // Add click listener to the whole video wrapper
        videoPlayer.addEventListener('click', () => {
            videoPlayer.paused ? videoPlayer.play() : videoPlayer.pause();
            updatePlayIcon();
        });

        return btn;
    }

    function updatePlayIcon() {
        playButton.innerHTML = videoPlayer.paused
            ? '<i class="fas fa-play"></i>'
            : '<i class="fas fa-pause"></i>';
    }

    function setupVolumeControl() {
        volumeControl.addEventListener('input', () => {
            videoPlayer.volume = volumeControl.value;
            updateVolumeIcon(volumeControl.value);
            updateVolumeSliderBackground();
        });
    }

    function updateVolumeIcon(volume) {
        const icon = volumeControl.previousElementSibling;
        if (!icon) return;
        icon.className = volume == 0
            ? 'fas fa-volume-mute'
            : volume < 0.5
                ? 'fas fa-volume-down'
                : 'fas fa-volume-up';
    }

    function updateVolumeSliderBackground() {
        const pct = volumeControl.value * 100;
        volumeControl.style.background = `
      linear-gradient(to right,
        var(--primary-color) 0%, var(--primary-color) ${pct}%,
        #555 ${pct}%, #555 100%)
    `;
    }

    function setupSkipButtons() {
        backwardBtn?.addEventListener('click', () => skipVideo(-SKIP_SECONDS));
        forwardBtn?.addEventListener('click', () => skipVideo(SKIP_SECONDS));
    }

    function setupFullscreenToggle() {
        if (!fullscreenBtn || !playerContainer) return;

        fullscreenBtn.addEventListener('click', toggleFullscreen);

        // Listen for fullscreen change events (standard and vendor-prefixed)
        document.addEventListener('fullscreenchange', updateFullscreenIcon);
        document.addEventListener('webkitfullscreenchange', updateFullscreenIcon);
        document.addEventListener('mozfullscreenchange', updateFullscreenIcon);
        document.addEventListener('MSFullscreenChange', updateFullscreenIcon);

        function isMobileDevice() {
            const uaMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
            const touchCoarse = window.matchMedia && (window.matchMedia('(pointer: coarse)').matches || window.matchMedia('(max-width: 991px)').matches);
            const hasTouch = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);
            return uaMobile || (touchCoarse && hasTouch);
        }

        async function lockLandscape() {
            try {
                if (screen.orientation && typeof screen.orientation.lock === 'function') {
                    await screen.orientation.lock('landscape');
                } else if (screen.lockOrientation) {
                    screen.lockOrientation('landscape');
                } else if (screen.mozLockOrientation) {
                    screen.mozLockOrientation('landscape');
                } else if (screen.msLockOrientation) {
                    screen.msLockOrientation('landscape');
                }
            } catch (err) {
                console.log('Orientation lock to landscape:', err.message || err);
            }
        }

        function unlockOrientation() {
            try {
                if (screen.orientation && typeof screen.orientation.unlock === 'function') {
                    screen.orientation.unlock();
                } else if (screen.unlockOrientation) {
                    screen.unlockOrientation();
                } else if (screen.mozUnlockOrientation) {
                    screen.mozUnlockOrientation();
                } else if (screen.msUnlockOrientation) {
                    screen.msUnlockOrientation();
                }
            } catch (err) {
                console.log('Orientation unlock:', err.message || err);
            }
        }

        function toggleFullscreen() {
            if (!document.fullscreenElement &&    // standard
                !document.webkitFullscreenElement && // chrome, safari and opera
                !document.mozFullScreenElement &&    // firefox
                !document.msFullscreenElement) {     // ie/edge

                // Enter fullscreen
                if (playerContainer.requestFullscreen) {
                    playerContainer.requestFullscreen();
                } else if (playerContainer.webkitRequestFullscreen) {
                    playerContainer.webkitRequestFullscreen();
                } else if (playerContainer.mozRequestFullScreen) {
                    playerContainer.mozRequestFullScreen();
                } else if (playerContainer.msRequestFullscreen) {
                    playerContainer.msRequestFullscreen();
                } else if (videoPlayer.webkitEnterFullscreen) {
                    // Fallback for iOS/iPhone where requestFullscreen on elements is not supported
                    videoPlayer.webkitEnterFullscreen();
                }
                if (isMobileDevice()) {
                    lockLandscape();
                }
            } else {
                // Exit fullscreen
                if (document.exitFullscreen) {
                    document.exitFullscreen();
                } else if (document.webkitExitFullscreen) {
                    document.webkitExitFullscreen();
                } else if (document.mozCancelFullScreen) {
                    document.mozCancelFullScreen();
                } else if (document.msExitFullscreen) {
                    document.msExitFullscreen();
                }
                unlockOrientation();
            }
        }

        function updateFullscreenIcon() {
            const isFullscreen = !!(document.fullscreenElement ||
                document.webkitFullscreenElement ||
                document.mozFullScreenElement ||
                document.msFullscreenElement);

            if (isFullscreen) {
                fullscreenBtn.innerHTML = '<i class="fas fa-compress"></i>';
                fullscreenBtn.title = "Tam Ekran Çık";
                playerContainer.classList.add('fullscreen-mode');
                if (isMobileDevice()) {
                    lockLandscape();
                }
            } else {
                fullscreenBtn.innerHTML = '<i class="fas fa-expand"></i>';
                fullscreenBtn.title = "Tam Ekran";
                playerContainer.classList.remove('fullscreen-mode');
                unlockOrientation();
            }
        }
    }

    function setupProgressBar() {
        const progressContainer = document.getElementById('progressContainer');
        const progressBar = document.getElementById('progressBar');
        const timeDisplay = document.getElementById('timeDisplay');

        if (!progressContainer || !progressBar) return;
        const formatTime = (seconds) => {
            if (isNaN(seconds)) return "00:00";
            const mins = Math.floor(seconds / 60);
            const secs = Math.floor(seconds % 60);
            return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        };

        const updateProgress = () => {
            const percentage = (videoPlayer.currentTime / videoPlayer.duration) * 100;
            progressBar.style.width = (percentage || 0) + '%';
            if (timeDisplay) {
                timeDisplay.innerText = `${formatTime(videoPlayer.currentTime)} / ${formatTime(videoPlayer.duration)}`;
            }
        };

        videoPlayer.addEventListener('timeupdate', updateProgress);
        videoPlayer.addEventListener('loadedmetadata', updateProgress);

        progressContainer.addEventListener('click', (e) => {
            if (isAdPlaying) return;
            const rect = progressContainer.getBoundingClientRect();
            const pos = (e.clientX - rect.left) / rect.width;
            videoPlayer.currentTime = pos * videoPlayer.duration;
        });

        // Optional: Dragging functionality
        let isDragging = false;
        progressContainer.addEventListener('mousedown', () => {
            if (isAdPlaying) return;
            isDragging = true;
        });
        window.addEventListener('mouseup', () => isDragging = false);
        window.addEventListener('mousemove', (e) => {
            if (isDragging && !isAdPlaying) {
                const rect = progressContainer.getBoundingClientRect();
                let pos = (e.clientX - rect.left) / rect.width;
                pos = Math.max(0, Math.min(1, pos));
                videoPlayer.currentTime = pos * videoPlayer.duration;
            }
        });
    }

    function setupPlaybackSpeed() {
        const speedButton = document.getElementById('speedButton');
        const speedOptions = document.querySelectorAll('.speed-option');
        const speedOptionsContainer = document.querySelector('.speed-options');

        if (!speedButton) return;

        speedOptions.forEach(option => {
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                const speed = parseFloat(option.getAttribute('data-speed'));
                videoPlayer.playbackRate = speed;
                speedButton.innerText = speed + 'x';

                // Update active state
                speedOptions.forEach(opt => opt.classList.remove('active'));
                option.classList.add('active');

                // Close menu after selection
                if (speedOptionsContainer) {
                    speedOptionsContainer.style.opacity = '0';
                    speedOptionsContainer.style.visibility = 'hidden';

                    // Reset visibility after a short delay so hover can work again
                    setTimeout(() => {
                        speedOptionsContainer.style.opacity = '';
                        speedOptionsContainer.style.visibility = '';
                    }, 500);
                }
            });
        });

        // Set default active
        speedOptions.forEach(opt => {
            if (opt.getAttribute('data-speed') === "1") {
                opt.classList.add('active');
            }
        });
    }

    function skipVideo(sec) {
        if (isAdPlaying) return;
        if (isNaN(videoPlayer.duration)) return;
        videoPlayer.currentTime = Math.max(0,
            Math.min(videoPlayer.duration, videoPlayer.currentTime + sec)
        );
    }

    function setupKeyboardShortcuts() {
        window.addEventListener('keydown', e => {
            if (isAdPlaying) return;
            const tag = document.activeElement.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA') return;
            if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
                e.preventDefault();
                skipVideo(e.key === 'ArrowLeft' ? -SKIP_SECONDS : SKIP_SECONDS);
            }
        });
    }

    function syncVolumeOnMetadata() {
        videoPlayer.addEventListener('loadedmetadata', () => {
            setTimeout(() => {
                volumeControl.value = videoPlayer.volume;
                updateVolumeIcon(videoPlayer.volume);
                updateVolumeSliderBackground();
            }, 100);
        });
    }

    function setupViewCount() {
        // Update play icon when video starts playing
        videoPlayer.addEventListener('play', () => {
            updatePlayIcon();
        });
    }

    function setupPlaybackEvents() {
        videoPlayer.addEventListener('playing', () => {
            videoPlayer.classList.add('playing');
            playPauseWrapper.classList.add('loaded');
        });
        videoPlayer.addEventListener('pause', updatePlayIcon);

        // Skeleton logic
        videoPlayer.addEventListener('waiting', () => {
            playPauseWrapper.classList.remove('loaded');
        });
        videoPlayer.addEventListener('canplay', () => {
            playPauseWrapper.classList.add('loaded');
        });
        videoPlayer.addEventListener('loadstart', () => {
            playPauseWrapper.classList.remove('loaded');
        });
    }

    function setupErrorHandling() {
        videoPlayer.addEventListener('error', () => {
            if (isAdPlaying) {
                // Ad error recovery is handled gracefully in playAd
                return;
            }
            playPauseWrapper.classList.add('loaded'); // Hata durumunda skeleton'ı kaldır ki mesaj görünsün
            if (titleEl) titleEl.innerText = 'Video yüklenirken hata oluştu.';
            if (infoEl) infoEl.innerText = 'Video bulunamadı veya oynatılamıyor.';
        });
    }

    function loadVideo(id) {
        // Stop current playback, destroy previous HLS instance and reset player
        videoPlayer.pause();
        if (activeHls) {
            activeHls.destroy();
            activeHls = null;
        }
        videoPlayer.removeAttribute('src');
        videoPlayer.src = "";
        videoPlayer.load();

        if (!id) {
            playPauseWrapper.classList.add('loaded'); // ID yoksa skeleton'ı kaldır
            if (titleEl) titleEl.innerText = 'Video bulunamadı';
            if (infoEl) infoEl.innerText = 'Geçerli bir ID girilmedi.';
            videoPlayer.style.display = 'none';
            return;
        }

        const hlsUrl = `/media/video/${id}/playlist.m3u8`;
        const mp4Url = `/media/video/${id}`;

        if (Hls.isSupported()) {
            activeHls = new Hls({
                maxBufferLength: 30, // 30 seconds buffer
                maxMaxBufferLength: 60,
            });
            activeHls.loadSource(hlsUrl);
            activeHls.attachMedia(videoPlayer);
            activeHls.on(Hls.Events.MANIFEST_PARSED, () => {
                // HLS successfully loaded and attached
                console.log('HLS loaded successfully for id:', id);
                videoPlayer.play().catch(e => console.log("Auto-play blocked:", e));
            });
            activeHls.on(Hls.Events.ERROR, (event, data) => {
                if (data.fatal) {
                    console.warn('HLS fatal error, falling back to MP4:', data.type, data.details);
                    if (activeHls) {
                        activeHls.destroy();
                        activeHls = null;
                    }
                    fallbackToMp4(mp4Url);
                }
            });
        } else if (videoPlayer.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari)
            videoPlayer.src = hlsUrl;
            const onMetadata = () => {
                videoPlayer.removeEventListener('loadedmetadata', onMetadata);
                videoPlayer.play().catch(e => console.log("Auto-play blocked:", e));
            };
            videoPlayer.addEventListener('loadedmetadata', onMetadata);
        } else {
            fallbackToMp4(mp4Url);
        }
    }

    function fallbackToMp4(url) {
        if (activeHls) {
            activeHls.destroy();
            activeHls = null;
        }
        videoPlayer.removeAttribute('src');
        videoPlayer.src = url;
        videoSource.src = url;
        videoPlayer.load();
        const onMetadata = () => {
            videoPlayer.removeEventListener('loadedmetadata', onMetadata);
            videoPlayer.play().catch(e => console.log("Auto-play blocked:", e));
        };
        videoPlayer.addEventListener('loadedmetadata', onMetadata);
    }

    function disableNativeShortcutsOnVideo() {
        videoPlayer.addEventListener('keydown', e => {
            if (['ArrowLeft', 'ArrowRight'].includes(e.key)) {
                e.preventDefault();
                e.stopPropagation();
            }
        }, true);
    }

    async function checkMainSourceAvailability(id) {
        try {
            // Check if the HLS playlist exists
            const response = await fetch(`/media/video/${id}/playlist.m3u8`, { method: 'HEAD' });
            return response.ok;
        } catch (error) {
            console.warn("Main source check failed:", error);
            return false;
        }
    }

    async function initializeContent(id) {
        if (!id) {
            loadVideo(id); // Handle no ID case inside loadVideo
            return;
        }

        // Configurable ad check (probability handled dynamically by backend)
        try {
            const adRes = await fetch('/api/video/commercial/random');
            if (adRes.ok && adRes.status === 200) {
                const adData = await adRes.json();
                if (adData && adData.id) {
                    playAd(adData.id, id, adData);
                    return;
                }
            }
        } catch (err) {
            console.warn("Failed to check or play ad:", err);
        }

        let hasMainSource = await checkMainSourceAvailability(id);

        // If main source check fails (e.g. 404), maybe we still want to try loading external sources
        await loadSources(id, hasMainSource);
    }

    function playAd(adId, originalId, adData) {
        isAdPlaying = true;
        
        // Hide source switcher container during ad
        const switcherContainer = document.querySelector('.source-switcher-container');
        if (switcherContainer) {
            switcherContainer.style.display = 'none';
        }

        // Create player ad overlay (contains "Reklam" badge and animated link buttons to its right)
        const adOverlay = createPlayerAdOverlay(adData);
        
        // Append to player wrapper
        const wrapper = document.querySelector('.video-wrapper');
        if (wrapper && adOverlay) {
            wrapper.appendChild(adOverlay);
        }

        let adFinished = false;
        const cleanupAndResumeMain = async () => {
            if (adFinished) return;
            adFinished = true;

            videoPlayer.removeEventListener('ended', onAdEnded);
            videoPlayer.removeEventListener('error', onAdError);

            // Clean up ad state
            isAdPlaying = false;
            if (adOverlay && adOverlay.parentNode) {
                adOverlay.parentNode.removeChild(adOverlay);
            }
            if (switcherContainer) {
                switcherContainer.style.display = '';
            }

            // Load original video content
            let hasMainSource = await checkMainSourceAvailability(originalId);
            await loadSources(originalId, hasMainSource);
        };

        const onAdEnded = () => {
            cleanupAndResumeMain();
        };

        const onAdError = (e) => {
            console.warn("Ad encountered playback error, transitioning to original content:", e);
            cleanupAndResumeMain();
        };

        videoPlayer.addEventListener('ended', onAdEnded);
        videoPlayer.addEventListener('error', onAdError, { once: true });

        // Load and play the ad video via HLS
        loadVideo(adId);
    }

    // --- REKLAM ROZETI & ANIMASYONLU BAGLANTI BUTONLARI YONETIMI ---
    const AD_CTA_TYPE_ICONS = {
        appstore: { icon: 'fab fa-apple', class: 'btn-cta-appstore', label: "App Store'dan İndir" },
        googleplay: { icon: 'fab fa-google-play', class: 'btn-cta-googleplay', label: "Google Play'den İndir" },
        website: { icon: 'fas fa-globe', class: 'btn-cta-website', label: 'Siteyi Ziyaret Et' },
        download: { icon: 'fas fa-download', class: 'btn-cta-download', label: 'Hemen İndir' },
        instagram: { icon: 'fab fa-instagram', class: 'btn-cta-instagram', label: "Instagram'da İncele" },
        youtube: { icon: 'fab fa-youtube', class: 'btn-cta-youtube', label: "YouTube'da İzle" },
        discord: { icon: 'fab fa-discord', class: 'btn-cta-discord', label: "Discord'a Katıl" },
        telegram: { icon: 'fab fa-telegram', class: 'btn-cta-telegram', label: "Telegram'a Katıl" },
        twitter: { icon: 'fab fa-x-twitter', class: 'btn-cta-twitter', label: "X'te Takip Et" },
        shop: { icon: 'fas fa-shopping-bag', class: 'btn-cta-shop', label: 'Satın Al / İncele' },
        custom: { icon: 'fas fa-external-link-alt', class: 'btn-cta-custom', label: 'Detaylı Bilgi' }
    };

    function createPlayerAdOverlay(adData) {
        const overlay = document.createElement('div');
        overlay.id = 'playerAdOverlay';
        overlay.className = 'player-ad-overlay';

        // Reklam Rozeti
        const badgeTag = document.createElement('div');
        badgeTag.className = 'ad-badge-tag';
        badgeTag.innerHTML = '<span class="ad-tag-pulse"></span><span>Reklam</span>';
        overlay.appendChild(badgeTag);

        // Reklam Rozetinin Sağına Gelen Butonlar Konteyneri
        let links = [];
        if (adData && adData.linkButtons) {
            try {
                links = typeof adData.linkButtons === 'string' ? JSON.parse(adData.linkButtons) : adData.linkButtons;
            } catch (e) {
                console.warn('Could not parse ad link buttons:', e);
            }
        }

        if (Array.isArray(links) && links.length > 0) {
            const linksWrap = document.createElement('div');
            linksWrap.className = 'ad-badge-links-wrap';

            links.forEach((link, idx) => {
                if (!link || !link.url) return;
                const typeKey = (link.type || 'website').toLowerCase();
                const config = AD_CTA_TYPE_ICONS[typeKey] || AD_CTA_TYPE_ICONS.custom;
                const btnText = link.title && link.title.trim() ? link.title.trim() : config.label;

                const a = document.createElement('a');
                a.href = link.url;
                a.target = '_blank';
                a.rel = 'noopener noreferrer';
                a.title = btnText;
                a.setAttribute('aria-label', btnText);
                a.className = `ad-overlay-btn ${config.class}`;
                // Staggered slide-in animation from the right of the "Reklam" badge
                a.style.animationDelay = `${0.12 + (idx * 0.1)}s`;
                a.innerHTML = `
                    <span class="ad-btn-icon"><i class="${config.icon}"></i></span>
                    <span class="ad-btn-label">${escapeHtmlCta(btnText)}</span>
                    <i class="fas fa-arrow-up-right-from-square ad-btn-arrow"></i>
                `;
                a.addEventListener('click', (e) => {
                    // Linke tıklandığında oynat/duraklat tetiklenmesin
                    e.stopPropagation();
                });
                linksWrap.appendChild(a);
            });

            if (linksWrap.children.length > 0) {
                overlay.appendChild(linksWrap);
            }
        }

        return overlay;
    }

    function escapeHtmlCta(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    async function loadSources(id, hasMainSource) {
        const switcher = document.getElementById('sourceSwitcher');
        if (!switcher) return;

        switcher.innerHTML = '';

        if (hasMainSource) {
            switcher.innerHTML = `
                <button class="source-btn active" id="btn-hls">
                    <i class="fas fa-bolt"></i> Ana Kaynak (HLS)
                </button>
            `;
            document.getElementById('btn-hls').onclick = () => switchToHls();
            loadVideo(id); // Initialize main player
        } else {
            // Hide main video player if no main source
            videoPlayer.style.display = 'none';
        }

        try {
            console.log("DEBUG: Loading sources for contentId:", id);
            const res = await fetch(`/media/video/${id}/sources`);
            const sources = await res.json();
            console.log("DEBUG: Sources received:", sources);

            if (sources.length === 0 && !hasMainSource) {
                console.log("DEBUG: No external sources found and no main source.");
                if (titleEl) titleEl.innerText = 'Video kaynağı bulunamadı.';
                if (infoEl) infoEl.innerText = 'Bu içerik için henüz bir video kaynağı eklenmemiş.';
            }

            sources.forEach(src => {
                const btn = document.createElement('button');
                btn.className = 'source-btn';
                btn.innerHTML = `<i class="fas fa-external-link-alt"></i> ${src.sourceName}`;
                btn.onclick = () => switchToSource(src, btn);
                switcher.appendChild(btn);
            });

            // Auto-switch logic if no main source
            if (!hasMainSource && sources.length > 0) {
                // Determine which button to click (should be the first one we just added)
                // Since we empty the switcher, and if !hasMainSource we didn't add the HLS button,
                // the first child should be the first external source.
                const firstExternalBtn = switcher.firstElementChild;
                if (firstExternalBtn) {
                    firstExternalBtn.click();
                }
            }

        } catch (err) {
            console.error('Kaynaklar yüklenemedi:', err);
        }
    }

    function switchToHls() {
        const iframeContainer = document.getElementById('iframePlayerContainer');
        iframeContainer.style.display = 'none';
        iframeContainer.innerHTML = '';
        videoPlayer.style.display = 'block';

        // Show custom controls and play button
        const controls = document.querySelector('.video-controls');
        if (controls) controls.style.display = ''; // Revert to CSS default (flex)

        const playBtn = document.querySelector('.play-pause-button');
        if (playBtn) playBtn.style.display = ''; // Revert to CSS default

        document.querySelectorAll('.source-btn').forEach(b => b.classList.remove('active'));
        document.getElementById('btn-hls').classList.add('active');
    }

    function switchToSource(source, btn) {
        videoPlayer.pause();
        if (activeHls) {
            activeHls.destroy();
            activeHls = null;
        }
        videoPlayer.src = "";
        videoPlayer.removeAttribute('src');
        videoPlayer.load();

        videoPlayer.style.display = 'none';

        // Force hide skeleton for external sources
        playPauseWrapper.classList.add('loaded');

        // Hide custom controls and play button
        const controls = document.querySelector('.video-controls');
        if (controls) controls.style.display = 'none';

        const playBtn = document.querySelector('.play-pause-button');
        if (playBtn) playBtn.style.display = 'none';

        const iframeContainer = document.getElementById('iframePlayerContainer');
        iframeContainer.style.display = 'block';
        // Ensure iframe container is above everything else just in case
        iframeContainer.style.zIndex = '10';

        let finalUrl = source.sourceUrl.trim();
        // If it's a full iframe tag, extract the src
        if (finalUrl.toLowerCase().startsWith('<iframe')) {
            const match = finalUrl.match(/src=["']([^"']+)["']/i);
            if (match && match[1]) {
                finalUrl = match[1];
            }
        }

        iframeContainer.innerHTML = `
            <iframe src="${finalUrl}" 
                    style="width:100%; height:100%; border:none;" 
                    frameborder="0"
                    allowfullscreen 
                    webkitallowfullscreen
                    mozallowfullscreen
                    allow="autoplay; encrypted-media; fullscreen; picture-in-picture; screen-wake-lock; accelerometer; gyroscope">
            </iframe>`;

        document.querySelectorAll('.source-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
    }

    function setupInactivityTimer() {
        let timer;
        const INACTIVITY_DELAY = 3000; // 3 seconds

        const showControls = () => {
            playerContainer.classList.remove('hide-controls');
            clearTimeout(timer);
            if (!videoPlayer.paused) {
                timer = setTimeout(() => {
                    playerContainer.classList.add('hide-controls');
                }, INACTIVITY_DELAY);
            }
        };

        const resetTimer = () => {
            showControls();
        };

        // UI events that should show controls
        playerContainer.addEventListener('mousemove', resetTimer);
        playerContainer.addEventListener('mousedown', resetTimer);
        playerContainer.addEventListener('touchstart', resetTimer);

        // Video state events
        videoPlayer.addEventListener('play', showControls);
        videoPlayer.addEventListener('playing', showControls);
        videoPlayer.addEventListener('pause', () => {
            playerContainer.classList.remove('hide-controls');
            clearTimeout(timer);
        });

        // Start timer if already playing
        if (!videoPlayer.paused) {
            showControls();
        }
    }
}
