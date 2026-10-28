/**
 * StreamLive - Real-time Synchronized Cinema & Live Chat
 * Who Dat Idols
 */

(function () {
    'use strict';

    // State Variables
    let currentStream = null;
    let currentUser = null;
    let stompClient = null;
    let rawSocket = null;
    let connectedStreamId = null;
    let isConnecting = false;
    let reconnectTimeout = null;
    let renderedMessageIds = new Set();
    let hlsPlayer = null;
    let countdownInterval = null;
    let timeSyncInterval = null;
    let chatCheckInterval = null;
    let chatSyncInterval = null;
    let slowModeCooldownTimer = null;
    let serverTimeOffset = 0; // serverTime - Date.now()
    let isUserScrubbing = false;
    let currentViewerCount = 0;
    let isChatOpen = false;

    // DOM Elements
    const elements = {
        headerTitle: document.getElementById('headerStreamTitle'),
        headerBrandBadge: document.getElementById('headerBrandBadge'),
        headerLiveDot: document.getElementById('headerLiveDot'),
        headerViewerCount: document.getElementById('headerViewerCount'),
        playerWrapper: document.getElementById('playerWrapper'),
        playerAmbientGlow: document.getElementById('playerAmbientGlow'),
        loadingScreen: document.getElementById('loadingScreen'),
        noStreamScreen: document.getElementById('noStreamScreen'),
        upcomingScreen: document.getElementById('upcomingScreen'),
        upcomingBackdrop: document.getElementById('upcomingBackdrop'),
        upcomingTitle: document.getElementById('upcomingTitle'),
        upcomingTimeLabel: document.getElementById('upcomingTimeLabel'),
        cdDays: document.getElementById('cdDays'),
        cdHours: document.getElementById('cdHours'),
        cdMinutes: document.getElementById('cdMinutes'),
        cdSeconds: document.getElementById('cdSeconds'),
        endedScreen: document.getElementById('endedScreen'),
        endedDesc: document.getElementById('endedDesc'),
        videoContainer: document.getElementById('videoContainer'),
        video: document.getElementById('streamVideo'),
        bigPlayBtn: document.getElementById('bigPlayBtn'),
        btnPlayPause: document.getElementById('btnPlayPause'),
        btnBackward: document.getElementById('btnBackward'),
        btnForward: document.getElementById('btnForward'),
        btnGoLive: document.getElementById('btnGoLive'),
        currentTime: document.getElementById('currentTime'),
        totalDurationTime: document.getElementById('totalDurationTime'),
        progressContainer: document.getElementById('progressContainer'),
        progressTrack: document.getElementById('progressTrack'),
        progressBuffered: document.getElementById('progressBuffered'),
        progressPlayed: document.getElementById('progressPlayed'),
        progressThumb: document.getElementById('progressThumb'),
        scrubTooltip: document.getElementById('scrubTooltip'),
        btnVolume: document.getElementById('btnVolume'),
        volumeSlider: document.getElementById('volumeSlider'),
        btnFullscreen: document.getElementById('btnFullscreen'),
        streamInfoCard: document.getElementById('streamInfoCard'),
        infoContentType: document.getElementById('infoContentType'),
        infoStatusTag: document.getElementById('infoStatusTag'),
        infoTitle: document.getElementById('infoTitle'),
        infoDescription: document.getElementById('infoDescription'),
        btnToggleDesc: document.getElementById('btnToggleDesc'),
        chatLiveDot: document.getElementById('chatLiveDot'),
        chatStatusText: document.getElementById('chatStatusText'),
        chatMessages: document.getElementById('chatMessages'),
        chatForm: document.getElementById('chatForm'),
        chatInput: document.getElementById('chatInput'),
        btnSendChat: document.getElementById('btnSendChat'),
        charCount: document.getElementById('charCount'),
        chatLockedNotice: document.getElementById('chatLockedNotice'),
        chatLockedText: document.getElementById('chatLockedText'),
        chatGuestNotice: document.getElementById('chatGuestNotice')
    };

    /**
     * Update the top-left brand badge & viewer counter state
     * Kisi sayaci sadece sohbet acikken gorunur; sohbet kapaliyken veya aktif yayin yokken 'StreamLive' yazar.
     */
    function updateViewerCountDisplay() {
        if (!elements.headerViewerCount) return;

        if (isChatOpen) {
            elements.headerViewerCount.textContent = `${currentViewerCount} Kişi`;
            if (elements.headerLiveDot) {
                elements.headerLiveDot.style.display = 'inline-block';
            }
        } else {
            elements.headerViewerCount.textContent = 'StreamLive';
            if (elements.headerLiveDot) {
                elements.headerLiveDot.style.display = 'none';
            }
        }
    }

    /**
     * Helper to get synchronized server timestamp in milliseconds
     */
    function getServerNow() {
        return Date.now() + serverTimeOffset;
    }

    /**
     * Format seconds to MM:SS or HH:MM:SS
     */
    function formatTime(totalSeconds) {
        if (isNaN(totalSeconds) || totalSeconds < 0) totalSeconds = 0;
        totalSeconds = Math.floor(totalSeconds);
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;

        const pad = (n) => (n < 10 ? '0' + n : n);

        if (hours > 0) {
            return `${hours}:${pad(minutes)}:${pad(seconds)}`;
        }
        return `${pad(minutes)}:${pad(seconds)}`;
    }

    /**
     * Determine stream ID from URL pathname (/streamlive/{id}) or fetch current
     */
    function getStreamIdFromUrl() {
        const path = window.location.pathname;
        const match = path.match(/\/streamlive\/([a-fA-F0-9\-]+)/);
        if (match) {
            try {
                window.history.replaceState({}, document.title, '/streamlive');
            } catch (e) {}
            return match[1];
        }
        return null;
    }

    /**
     * Initialize StreamLive
     */
    async function init() {
        setupMobileInfoToggle();
        await checkAuthStatus();
        setupChatInputEvents();
        setupVideoControls();
        await loadStreamData();
        startChatStatusPoller();
    }

    /**
     * Setup Mobile Info Description Dropdown Toggle
     */
    function setupMobileInfoToggle() {
        const toggle = function (e) {
            if (elements.streamInfoCard) {
                elements.streamInfoCard.classList.toggle('expanded');
            }
        };

        if (elements.btnToggleDesc) {
            elements.btnToggleDesc.addEventListener('click', function (e) {
                e.stopPropagation();
                toggle(e);
            });
        }

        const infoHeader = document.querySelector('.stream-info-header');
        if (infoHeader) {
            infoHeader.addEventListener('click', toggle);
        }
    }

    /**
     * Fetch User Authentication Status
     */
    async function checkAuthStatus() {
        try {
            const res = await fetch('/api/streamlive/auth-status', { credentials: 'same-origin' });
            if (res.ok) {
                const data = await res.json();
                if (data.isLoggedIn) {
                    currentUser = {
                        userId: data.userId,
                        nickname: data.nickname || 'Kullanıcı',
                        role: data.role
                    };
                    return;
                }
            }
        } catch (e) {
            console.error('Auth check error:', e);
        }
        currentUser = null;
    }

    /**
     * Fetch Stream Live Information
     */
    async function loadStreamData() {
        const streamId = getStreamIdFromUrl();
        const endpoint = streamId ? `/api/streamlive/${streamId}` : '/api/streamlive/current';

        try {
            const res = await fetch(endpoint);
            if (!res.ok) {
                showNoStream();
                return;
            }

            const data = await res.json();
            currentStream = data;

            // Compute server-client time offset
            if (data.serverTime) {
                serverTimeOffset = data.serverTime - Date.now();
            }

            // Update Header and Info
            if (elements.headerTitle) {
                elements.headerTitle.textContent = data.title || 'StreamLive Canlı Sinema';
            }
            if (data.viewerCount !== undefined) {
                currentViewerCount = data.viewerCount;
            }
            updateViewerCountDisplay();
            document.title = `${data.title || 'Canlı Sinema'} - StreamLive | Who Dat Idols`;

            updateStreamUI();
            updateChatAccessUI();
            connectWebSocket(data.id);

        } catch (e) {
            console.error('Stream load error:', e);
            showNoStream();
        }
    }

    /**
     * Switch Screens based on Stream Status
     */
    function updateStreamUI() {
        if (!currentStream) {
            showNoStream();
            return;
        }

        const now = getServerNow();
        const startTime = currentStream.scheduledStartTime;
        const endTime = currentStream.endTime;

        // Hide all screens initially
        hideAllScreens();

        if (now < startTime) {
            // UPCOMING SCREEN
            showUpcomingScreen();
        } else if (now >= startTime && now <= endTime) {
            // LIVE STREAM SCREEN
            showLiveScreen();
        } else {
            // ENDED SCREEN
            showEndedScreen();
        }

        // Populate Info Card
        if (elements.streamInfoCard) {
            elements.streamInfoCard.style.display = 'block';
            if (elements.infoTitle) elements.infoTitle.textContent = currentStream.title || currentStream.contentName || 'Canlı Yayın';
            if (elements.infoContentType) elements.infoContentType.textContent = currentStream.contentType === 'MOVIE' ? 'FİLM' : 'DİZİ BÖLÜMÜ';
            if (elements.infoDescription) elements.infoDescription.textContent = currentStream.description || (currentStream.contentName + ' eş zamanlı canlı yayını.');
        }
    }

    function hideAllScreens() {
        if (elements.loadingScreen) elements.loadingScreen.style.display = 'none';
        if (elements.noStreamScreen) elements.noStreamScreen.style.display = 'none';
        if (elements.upcomingScreen) elements.upcomingScreen.style.display = 'none';
        if (elements.endedScreen) elements.endedScreen.style.display = 'none';
        if (elements.videoContainer) elements.videoContainer.style.display = 'none';
    }

    function showNoStream() {
        isChatOpen = false;
        currentViewerCount = 0;
        updateViewerCountDisplay();
        hideAllScreens();
        if (elements.noStreamScreen) elements.noStreamScreen.style.display = 'flex';
        if (elements.streamInfoCard) elements.streamInfoCard.style.display = 'none';
        if (elements.chatLiveDot) elements.chatLiveDot.className = 'live-dot dot-waiting';
        if (elements.chatStatusText) elements.chatStatusText.textContent = 'Yayın Yok';
        showChatLocked('Şu anda aktif bir yayın bulunmadığı için canlı sohbet kapalıdır.');
    }

    /**
     * Handle Chat Lock / Open State (10 Minutes Rule)
     */
    function updateChatAccessUI() {
        if (!currentStream) {
            isChatOpen = false;
            updateViewerCountDisplay();
            showChatLocked('Şu anda aktif bir yayın bulunmadığı için canlı sohbet kapalıdır.');
            return;
        }

        const now = getServerNow();
        const start = currentStream.scheduledStartTime;
        const end = currentStream.endTime;
        const tenMinutesBeforeStart = start - (10 * 60 * 1000);
        const oneHourAfterEnd = end + (60 * 60 * 1000);

        if (now < tenMinutesBeforeStart) {
            // Chat is locked until 10 mins before start
            isChatOpen = false;
            updateViewerCountDisplay();
            const diffMs = tenMinutesBeforeStart - now;
            const diffMin = Math.ceil(diffMs / 60000);
            if (elements.chatLiveDot) elements.chatLiveDot.className = 'live-dot dot-waiting';
            if (elements.chatStatusText) elements.chatStatusText.textContent = `${diffMin} dk sonra açılır`;
            showChatLocked(`Canlı sohbet yayının başlamasına 10 dakika kala açılacaktır (${diffMin} dakika kaldı).`);
        } else if (now <= oneHourAfterEnd) {
            // Chat is OPEN!
            isChatOpen = true;
            updateViewerCountDisplay();
            if (elements.chatLiveDot) elements.chatLiveDot.className = 'live-dot';
            if (elements.chatStatusText) {
                if (now > end) {
                    elements.chatStatusText.textContent = 'Açık (Yayın Sonu)';
                } else {
                    elements.chatStatusText.textContent = 'Canlı';
                }
            }
            showChatUnlocked();
        } else {
            // Chat is CLOSED permanently after 1 hr post-stream
            isChatOpen = false;
            updateViewerCountDisplay();
            if (elements.chatLiveDot) elements.chatLiveDot.className = 'live-dot dot-waiting';
            if (elements.chatStatusText) elements.chatStatusText.textContent = 'Kapatıldı';
            showChatLocked('Yayın ve sohbet süresi sona ermiştir.');
        }
    }

    function showChatLocked(message) {
        if (elements.chatForm) elements.chatForm.style.display = 'none';
        if (elements.chatGuestNotice) elements.chatGuestNotice.style.display = 'none';
        if (elements.chatLockedNotice) {
            elements.chatLockedNotice.style.display = 'block';
            if (elements.chatLockedText) elements.chatLockedText.textContent = message;
        }
    }

    function showChatUnlocked() {
        if (elements.chatLockedNotice) elements.chatLockedNotice.style.display = 'none';
        if (currentUser) {
            if (elements.chatForm) elements.chatForm.style.display = 'flex';
            if (elements.chatGuestNotice) elements.chatGuestNotice.style.display = 'none';
        } else {
            if (elements.chatForm) elements.chatForm.style.display = 'none';
            if (elements.chatGuestNotice) elements.chatGuestNotice.style.display = 'block';
        }
    }

    function startChatStatusPoller() {
        if (chatCheckInterval) clearInterval(chatCheckInterval);
        chatCheckInterval = setInterval(function () {
            if (currentStream) {
                updateChatAccessUI();
            }
        }, 5000);
    }

    /**
     * Handle Upcoming Countdown Screen
     */
    function showUpcomingScreen() {
        if (elements.upcomingScreen) elements.upcomingScreen.style.display = 'flex';
        if (elements.upcomingTitle) elements.upcomingTitle.textContent = currentStream.title || currentStream.contentName;

        if (currentStream.posterUrl && elements.upcomingBackdrop) {
            elements.upcomingBackdrop.style.backgroundImage = `url('${currentStream.posterUrl}')`;
        }

        const startDate = new Date(currentStream.scheduledStartTime);
        if (elements.upcomingTimeLabel) {
            elements.upcomingTimeLabel.textContent = `Yayın Saati: ${startDate.toLocaleDateString('tr-TR')} ${startDate.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}`;
        }

        if (elements.infoStatusTag) {
            elements.infoStatusTag.style.display = 'none';
        }
        startCountdownTimer();
    }

    function startCountdownTimer() {
        if (countdownInterval) clearInterval(countdownInterval);

        function tick() {
            const now = getServerNow();
            const diffMs = currentStream.scheduledStartTime - now;

            if (diffMs <= 0) {
                clearInterval(countdownInterval);
                loadStreamData();
                return;
            }

            const totalSec = Math.floor(diffMs / 1000);
            const days = Math.floor(totalSec / 86400);
            const hours = Math.floor((totalSec % 86400) / 3600);
            const minutes = Math.floor((totalSec % 3600) / 60);
            const seconds = totalSec % 60;

            const pad = (n) => (n < 10 ? '0' + n : n);

            if (elements.cdDays) elements.cdDays.textContent = pad(days);
            if (elements.cdHours) elements.cdHours.textContent = pad(hours);
            if (elements.cdMinutes) elements.cdMinutes.textContent = pad(minutes);
            if (elements.cdSeconds) elements.cdSeconds.textContent = pad(seconds);
        }

        tick();
        countdownInterval = setInterval(tick, 1000);
    }

    /**
     * Handle Live Video Player Screen
     */
    function showLiveScreen() {
        if (countdownInterval) clearInterval(countdownInterval);

        if (elements.videoContainer) elements.videoContainer.style.display = 'block';
        if (elements.infoStatusTag) {
            elements.infoStatusTag.style.display = 'inline-block';
            elements.infoStatusTag.textContent = 'CANLI YAYIN';
            elements.infoStatusTag.className = 'stream-tag tag-status';
        }

        initHlsPlayer();
        startTimeSyncTracker();
    }

    function showEndedScreen() {
        if (countdownInterval) clearInterval(countdownInterval);
        if (timeSyncInterval) clearInterval(timeSyncInterval);

        if (hlsPlayer) {
            hlsPlayer.destroy();
            hlsPlayer = null;
        }

        if (elements.endedScreen) elements.endedScreen.style.display = 'flex';
        if (elements.infoStatusTag) {
            elements.infoStatusTag.style.display = 'none';
        }
    }

    /**
     * Calculate current live edge in seconds
     */
    function getCurrentLiveEdge() {
        if (!currentStream) return 0;
        const now = getServerNow();
        const start = currentStream.scheduledStartTime;
        let totalDuration = 0;
        if (currentStream.durationSeconds && currentStream.durationSeconds > 0) {
            totalDuration = currentStream.durationSeconds;
        } else if (currentStream.durationMinutes && currentStream.durationMinutes > 0) {
            totalDuration = currentStream.durationMinutes * 60;
        }
        const elapsed = Math.floor((now - start) / 1000);
        if (totalDuration > 0) {
            return Math.min(Math.max(0, elapsed), totalDuration);
        }
        return Math.max(0, elapsed);
    }

    /**
     * Initialize HLS Player with Timeshift DVR capability
     */
    function initHlsPlayer() {
        const video = elements.video;
        if (!video || !currentStream) return;
        const videoSrc = currentStream.videoUrl;

        const liveEdge = getCurrentLiveEdge();

        if (Hls.isSupported()) {
            if (hlsPlayer) {
                hlsPlayer.destroy();
            }

            hlsPlayer = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                backBufferLength: 90
            });

            hlsPlayer.loadSource(videoSrc);
            hlsPlayer.attachMedia(video);

            hlsPlayer.on(Hls.Events.MANIFEST_PARSED, function () {
                // Seek to the live edge position immediately
                video.currentTime = liveEdge;
                video.play().catch(function (e) {
                    console.log('Autoplay muted attempt due to policy:', e);
                    video.muted = true;
                    video.play().catch(err => console.log('Autoplay prevented:', err));
                });
            });

            hlsPlayer.on(Hls.Events.LEVEL_LOADED, function (event, data) {
                if (data && data.details && data.details.totalduration) {
                    if (!currentStream.durationSeconds || currentStream.durationSeconds === 0) {
                        currentStream.durationSeconds = Math.round(data.details.totalduration);
                    }
                }
            });

            hlsPlayer.on(Hls.Events.ERROR, function (event, data) {
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            hlsPlayer.startLoad();
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            hlsPlayer.recoverMediaError();
                            break;
                        default:
                            hlsPlayer.destroy();
                            break;
                    }
                }
            });

        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = videoSrc;
            video.addEventListener('loadedmetadata', function () {
                video.currentTime = liveEdge;
                video.play().catch(err => console.log('Autoplay error:', err));
            });
        }
    }

    /**
     * Time Sync Tracker & DVR Restraint
     * Updates:
     * - Current seconds / elapsed time on the left side (#currentTime)
     * - Total stream duration on the right side (#totalDurationTime)
     */
    function startTimeSyncTracker() {
        if (timeSyncInterval) clearInterval(timeSyncInterval);

        timeSyncInterval = setInterval(function () {
            if (!currentStream || !elements.video) return;

            const now = getServerNow();
            if (now > currentStream.endTime) {
                showEndedScreen();
                return;
            }

            const liveEdge = getCurrentLiveEdge();
            const curTime = elements.video.currentTime || 0;

            // Determine Total Duration
            let totalDurationSec = 0;
            if (elements.video && elements.video.duration && !isNaN(elements.video.duration) && isFinite(elements.video.duration) && elements.video.duration > 0) {
                totalDurationSec = elements.video.duration;
            } else if (currentStream && currentStream.durationSeconds && currentStream.durationSeconds > 0) {
                totalDurationSec = currentStream.durationSeconds;
            } else if (currentStream && currentStream.durationMinutes && currentStream.durationMinutes > 0) {
                totalDurationSec = currentStream.durationMinutes * 60;
            } else {
                totalDurationSec = liveEdge;
            }

            // End Check: trigger immediately if video has finished or reached end of duration
            if (elements.video.ended || (totalDurationSec > 0 && curTime >= totalDurationSec - 0.5 && liveEdge >= totalDurationSec)) {
                showEndedScreen();
                return;
            }

            // DVR ENFORCEMENT: If user currentTime > liveEdge, clamp it
            if (curTime > liveEdge + 0.5) {
                elements.video.currentTime = liveEdge;
            }

            // Update displays: Left side current seconds / time, Right side total duration
            if (elements.currentTime) {
                elements.currentTime.textContent = formatTime(curTime);
            }
            if (elements.totalDurationTime) {
                elements.totalDurationTime.textContent = formatTime(totalDurationSec);
            }

            // Update progress bar position (relative to liveEdge for timeshift playback)
            if (!isUserScrubbing && elements.progressPlayed && elements.progressThumb) {
                const percent = liveEdge > 0 ? (curTime / liveEdge) * 100 : 0;
                const clamped = Math.min(100, Math.max(0, percent));
                elements.progressPlayed.style.width = clamped + '%';
                elements.progressThumb.style.left = clamped + '%';
            }

            // Check synchronization lag
            if (elements.btnGoLive) {
                const lag = liveEdge - curTime;
                const liveTextEl = elements.btnGoLive.querySelector('.live-text');
                if (lag <= 6) {
                    elements.btnGoLive.className = 'btn-go-live is-live';
                    if (liveTextEl) liveTextEl.textContent = 'CANLI';
                } else {
                    elements.btnGoLive.className = 'btn-go-live needs-sync';
                    if (liveTextEl) liveTextEl.textContent = 'CANLIYA GEÇ';
                }
            }

            updateBufferDisplay(liveEdge);

        }, 1000);
    }

    function updateBufferDisplay(liveEdge) {
        const video = elements.video;
        if (!video || !elements.progressBuffered) return;
        if (video.buffered && video.buffered.length > 0 && liveEdge > 0) {
            const bufferedEnd = video.buffered.end(video.buffered.length - 1);
            const bufPercent = (Math.min(bufferedEnd, liveEdge) / liveEdge) * 100;
            elements.progressBuffered.style.width = Math.min(100, bufPercent) + '%';
        }
    }

    /**
     * Video Controls Handlers
     */
    function setupVideoControls() {
        const video = elements.video;
        if (!video) return;

        function togglePlay() {
            if (video.paused) {
                video.play();
            } else {
                video.pause();
            }
        }

        if (elements.btnPlayPause) elements.btnPlayPause.addEventListener('click', togglePlay);
        if (elements.bigPlayBtn) elements.bigPlayBtn.addEventListener('click', togglePlay);

        video.addEventListener('play', function () {
            if (elements.videoContainer) elements.videoContainer.classList.remove('paused');
            if (elements.btnPlayPause) elements.btnPlayPause.innerHTML = '<i class="fas fa-pause"></i>';
        });

        video.addEventListener('pause', function () {
            if (elements.videoContainer) elements.videoContainer.classList.add('paused');
            if (elements.btnPlayPause) elements.btnPlayPause.innerHTML = '<i class="fas fa-play"></i>';
        });

        video.addEventListener('click', togglePlay);

        // 10s Backward / Forward
        if (elements.btnBackward) {
            elements.btnBackward.addEventListener('click', function (e) {
                e.stopPropagation();
                video.currentTime = Math.max(0, video.currentTime - 10);
            });
        }

        if (elements.btnForward) {
            elements.btnForward.addEventListener('click', function (e) {
                e.stopPropagation();
                const liveEdge = getCurrentLiveEdge();
                video.currentTime = Math.min(liveEdge, video.currentTime + 10);
            });
        }

        // "CANLIYA GEÇ" Button
        if (elements.btnGoLive) {
            elements.btnGoLive.addEventListener('click', function (e) {
                e.stopPropagation();
                const liveEdge = getCurrentLiveEdge();
                video.currentTime = liveEdge;
                if (video.paused) {
                    video.play();
                }
            });
        }

        // Scrubber Event Handlers (Mouse & Touch compatible)
        function getEventClientX(e) {
            if (e.touches && e.touches.length > 0) return e.touches[0].clientX;
            if (e.changedTouches && e.changedTouches.length > 0) return e.changedTouches[0].clientX;
            return e.clientX || 0;
        }

        function handleScrub(e) {
            const liveEdge = getCurrentLiveEdge();
            if (liveEdge <= 0 || !elements.progressTrack) return;

            const rect = elements.progressTrack.getBoundingClientRect();
            const clientX = getEventClientX(e);
            const clickX = Math.max(0, Math.min(clientX - rect.left, rect.width));
            const ratio = clickX / rect.width;
            const targetTime = Math.min(ratio * liveEdge, liveEdge);

            video.currentTime = targetTime;
            if (elements.currentTime) elements.currentTime.textContent = formatTime(targetTime);
            if (elements.progressPlayed) elements.progressPlayed.style.width = (ratio * 100) + '%';
            if (elements.progressThumb) elements.progressThumb.style.left = (ratio * 100) + '%';
        }

        if (elements.progressContainer) {
            // Mouse events
            elements.progressContainer.addEventListener('mousedown', function (e) {
                isUserScrubbing = true;
                handleScrub(e);

                function onMouseMove(moveEvent) {
                    if (isUserScrubbing) {
                        handleScrub(moveEvent);
                    }
                }

                function onMouseUp() {
                    isUserScrubbing = false;
                    window.removeEventListener('mousemove', onMouseMove);
                    window.removeEventListener('mouseup', onMouseUp);
                }

                window.addEventListener('mousemove', onMouseMove);
                window.addEventListener('mouseup', onMouseUp);
            });

            // Touch events for mobile scrubbing
            elements.progressContainer.addEventListener('touchstart', function (e) {
                isUserScrubbing = true;
                handleScrub(e);

                function onTouchMove(moveEvent) {
                    if (isUserScrubbing) {
                        if (moveEvent.cancelable) moveEvent.preventDefault();
                        handleScrub(moveEvent);
                    }
                }

                function onTouchEnd() {
                    isUserScrubbing = false;
                    window.removeEventListener('touchmove', onTouchMove);
                    window.removeEventListener('touchend', onTouchEnd);
                    window.removeEventListener('touchcancel', onTouchEnd);
                }

                window.addEventListener('touchmove', onTouchMove, { passive: false });
                window.addEventListener('touchend', onTouchEnd);
                window.addEventListener('touchcancel', onTouchEnd);
            }, { passive: true });

            elements.progressContainer.addEventListener('mousemove', function (e) {
                const liveEdge = getCurrentLiveEdge();
                if (liveEdge <= 0 || !elements.scrubTooltip) return;

                const rect = elements.progressTrack.getBoundingClientRect();
                const hoverX = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
                const hoverTime = (hoverX / rect.width) * liveEdge;

                elements.scrubTooltip.style.left = hoverX + 'px';
                elements.scrubTooltip.textContent = formatTime(hoverTime);
            });
        }

        // Volume
        if (elements.volumeSlider) {
            elements.volumeSlider.addEventListener('input', function (e) {
                video.volume = parseFloat(e.target.value);
                video.muted = video.volume === 0;
                updateVolumeIcon();
            });
        }

        if (elements.btnVolume) {
            elements.btnVolume.addEventListener('click', function (e) {
                e.stopPropagation();
                video.muted = !video.muted;
                if (elements.volumeSlider) {
                    if (video.muted) {
                        elements.volumeSlider.value = 0;
                    } else {
                        elements.volumeSlider.value = video.volume || 1;
                    }
                }
                updateVolumeIcon();
            });
        }

        video.addEventListener('volumechange', function () {
            if (elements.volumeSlider) {
                elements.volumeSlider.value = video.muted ? 0 : video.volume;
            }
            updateVolumeIcon();
        });

        function updateVolumeIcon() {
            if (!elements.btnVolume) return;
            if (video.muted || video.volume === 0) {
                elements.btnVolume.innerHTML = '<i class="fas fa-volume-mute"></i>';
            } else if (video.volume < 0.5) {
                elements.btnVolume.innerHTML = '<i class="fas fa-volume-down"></i>';
            } else {
                elements.btnVolume.innerHTML = '<i class="fas fa-volume-up"></i>';
            }
        }

        // Fullscreen
        if (elements.btnFullscreen && elements.playerWrapper) {
            elements.btnFullscreen.addEventListener('click', function (e) {
                e.stopPropagation();
                if (!document.fullscreenElement) {
                    elements.playerWrapper.requestFullscreen().catch(err => console.log('Fullscreen error:', err));
                } else {
                    document.exitFullscreen().catch(err => console.log('Exit fullscreen error:', err));
                }
            });
        }
    }

    /**
     * Disconnect WebSocket safely and clean up state
     */
    function disconnectWebSocket() {
        if (reconnectTimeout) {
            clearTimeout(reconnectTimeout);
            reconnectTimeout = null;
        }
        if (chatSyncInterval) {
            clearInterval(chatSyncInterval);
            chatSyncInterval = null;
        }
        if (stompClient) {
            try {
                if (stompClient.connected) {
                    stompClient.disconnect();
                }
            } catch (e) {
                console.warn('Error disconnecting STOMP:', e);
            }
            stompClient = null;
        }
        if (rawSocket) {
            try {
                rawSocket.close();
            } catch (e) {
                console.warn('Error closing raw socket:', e);
            }
            rawSocket = null;
        }
        connectedStreamId = null;
        isConnecting = false;
    }

    /**
     * Live Chat WebSocket (SockJS + STOMP)
     */
    function connectWebSocket(streamId) {
        if (!streamId) return;

        // If already connected to this stream, DO NOT create another connection!
        if (stompClient && stompClient.connected && connectedStreamId === streamId) {
            return;
        }

        // If connection in progress for this stream, avoid duplicate simultaneous attempt
        if (isConnecting && connectedStreamId === streamId) {
            return;
        }

        disconnectWebSocket();

        isConnecting = true;
        connectedStreamId = streamId;

        // Load chat history once for this stream
        loadChatHistory(streamId);

        // Continuous sync fallback: Every 3s, sync latest messages (deduplicated by message ID)
        if (chatSyncInterval) clearInterval(chatSyncInterval);
        chatSyncInterval = setInterval(async function () {
            if (!currentStream || !connectedStreamId) return;
            try {
                const res = await fetch('/api/streamlive/' + connectedStreamId + '/chat/history?limit=25');
                if (res.ok) {
                    const list = await res.json();
                    if (Array.isArray(list) && list.length > 0) {
                        list.forEach(msg => appendChatMessage(msg, true));
                    }
                }
            } catch (e) {
                // Ignore transient network errors
            }
        }, 3000);

        try {
            rawSocket = new SockJS('/ws');
            stompClient = Stomp.over(rawSocket);
            stompClient.debug = null;

            stompClient.connect({}, function () {
                isConnecting = false;

                // Real-time viewer count subscription
                stompClient.subscribe('/topic/streamlive/' + streamId + '/viewers', function (response) {
                    try {
                        const data = JSON.parse(response.body);
                        const count = (data && data.viewerCount !== undefined) ? data.viewerCount : Number(response.body);
                        currentViewerCount = count;
                        updateViewerCountDisplay();
                    } catch (e) {
                        console.error('Viewer count parse error:', e);
                    }
                });

                stompClient.subscribe('/topic/streamlive/' + streamId + '/chat', function (response) {
                    try {
                        const msg = JSON.parse(response.body);
                        appendChatMessage(msg);
                    } catch (e) {
                        console.error('Chat parse error:', e);
                    }
                });

                stompClient.subscribe('/user/queue/streamlive/errors', function (response) {
                    try {
                        const err = JSON.parse(response.body);
                        alert(err.error || 'Mesaj gönderilemedi.');
                    } catch (e) {}
                });

            }, function (error) {
                isConnecting = false;
                console.warn('STOMP connection failed or closed, reconnecting in 5s...', error);
                if (reconnectTimeout) clearTimeout(reconnectTimeout);
                reconnectTimeout = setTimeout(() => {
                    if (currentStream && currentStream.id === streamId) {
                        connectWebSocket(streamId);
                    }
                }, 5000);
            });

        } catch (e) {
            isConnecting = false;
            console.error('WebSocket init error:', e);
        }
    }

    /**
     * Load recent chat history via REST API
     */
    async function loadChatHistory(streamId) {
        try {
            const res = await fetch(`/api/streamlive/${streamId}/chat/history?limit=50`);
            if (res.ok) {
                const list = await res.json();
                if (Array.isArray(list)) {
                    if (elements.chatMessages) {
                        elements.chatMessages.innerHTML = '';
                    }
                    renderedMessageIds.clear();
                    list.forEach(msg => appendChatMessage(msg, false));
                    scrollChatToBottom(true);
                }
            }
        } catch (e) {
            console.error('History fetch error:', e);
        }
    }

    /**
     * Append a message item into the chat container
     */
    function appendChatMessage(msg, shouldScroll = true) {
        if (!elements.chatMessages) return;

        // Deduplication: prevent duplicate messages from ever being rendered twice
        if (msg.id) {
            if (renderedMessageIds.has(msg.id)) {
                return;
            }
            renderedMessageIds.add(msg.id);
        }

        const item = document.createElement('div');
        if (msg.id) {
            item.setAttribute('data-msg-id', msg.id);
        }
        const isMe = currentUser && currentUser.userId === msg.userId;
        item.className = 'chat-msg-item' + (isMe ? ' my-message' : '');

        const sentTime = msg.sentAt || msg.sentAtMillis;
        const timeStr = sentTime ? new Date(sentTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '';

        const userDiv = document.createElement('span');
        userDiv.className = 'chat-msg-user';
        userDiv.textContent = msg.nickname || 'Kullanıcı';

        const isVerified = msg.verified === true || msg.isVerified === true || msg.verified === 'true';
        if (isVerified) {
            const badge = document.createElement('i');
            badge.className = 'fas fa-check-circle verified-badge';
            badge.title = 'Doğrulanmış Hesap';
            userDiv.appendChild(badge);
        }

        const timeSpan = document.createElement('span');
        timeSpan.className = 'chat-msg-time';
        timeSpan.textContent = timeStr;

        const headerDiv = document.createElement('div');
        headerDiv.className = 'chat-msg-header';
        headerDiv.appendChild(userDiv);
        headerDiv.appendChild(timeSpan);

        const textDiv = document.createElement('div');
        textDiv.className = 'chat-msg-text';
        textDiv.textContent = msg.message;

        item.appendChild(headerDiv);
        item.appendChild(textDiv);

        elements.chatMessages.appendChild(item);

        if (shouldScroll) {
            scrollChatToBottom();
        }
    }

    function scrollChatToBottom() {
        if (elements.chatMessages) {
            elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
        }
    }

    /**
     * Chat Input Events (200 char limit & 10s slow mode)
     */
    function setupChatInputEvents() {
        const input = elements.chatInput;
        const charCount = elements.charCount;
        if (!input || !charCount || !elements.chatForm) return;

        input.addEventListener('input', function () {
            const len = input.value.length;
            charCount.textContent = len;
            if (len >= 200) {
                charCount.style.color = '#ff5252';
            } else {
                charCount.style.color = '';
            }
        });

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (elements.btnSendChat && elements.btnSendChat.disabled) return;
                elements.chatForm.dispatchEvent(new Event('submit'));
            }
        });

        elements.chatForm.addEventListener('submit', function (e) {
            e.preventDefault();
            if (elements.btnSendChat && elements.btnSendChat.disabled) return;
            if (!currentUser) {
                alert('Mesaj göndermek için giriş yapmalısınız.');
                return;
            }

            if (!currentStream) {
                alert('Aktif bir yayın bulunmuyor.');
                return;
            }

            // Client-side 10-minute check
            const now = getServerNow();
            const tenMinutesBeforeStart = currentStream.scheduledStartTime - (10 * 60 * 1000);
            if (now < tenMinutesBeforeStart) {
                alert('Canlı sohbet yayının başlamasına 10 dakika kala açılacaktır.');
                return;
            }

            const oneHourAfterEnd = currentStream.endTime + (60 * 60 * 1000);
            if (now > oneHourAfterEnd) {
                alert('Yayın ve sohbet süresi sona erdi.');
                return;
            }

            const text = input.value.trim();
            if (!text) return;

            if (text.length > 200) {
                alert('Mesajınız en fazla 200 karakter olabilir.');
                return;
            }

            if (!stompClient || !stompClient.connected) {
                alert('Sohbet sunucusuna henüz bağlanılamadı. Lütfen bekleyin.');
                return;
            }

            // Temporarily disable button to prevent double-click submissions
            if (elements.btnSendChat) elements.btnSendChat.disabled = true;

            stompClient.send(
                '/app/streamlive/' + currentStream.id + '/chat.send',
                {},
                JSON.stringify({ message: text })
            );

            input.value = '';
            charCount.textContent = '0';

            startSlowModeCooldown(10);
        });
    }

    /**
     * Start Slow Mode Cooldown Timer (10 seconds)
     * Displays countdown directly inside send button slot without altering button shape
     */
    function startSlowModeCooldown(seconds) {
        if (!elements.btnSendChat) return;
        if (slowModeCooldownTimer) clearInterval(slowModeCooldownTimer);

        let remaining = seconds;
        elements.btnSendChat.disabled = true;
        elements.btnSendChat.innerHTML = `<span class="cooldown-counter">${remaining}</span>`;

        slowModeCooldownTimer = setInterval(function () {
            remaining--;
            if (remaining <= 0) {
                clearInterval(slowModeCooldownTimer);
                slowModeCooldownTimer = null;
                elements.btnSendChat.disabled = false;
                elements.btnSendChat.innerHTML = '<i class="fas fa-paper-plane"></i>';
            } else {
                elements.btnSendChat.innerHTML = `<span class="cooldown-counter">${remaining}</span>`;
            }
        }, 1000);
    }

    window.addEventListener('beforeunload', function () {
        disconnectWebSocket();
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
