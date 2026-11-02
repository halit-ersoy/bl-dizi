window.handleActorAvatarError = function(img) { img.onerror = null; img.src = 'data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22 width=%22100%22 height=%22100%22%3E%3Ccircle cx=%2250%22 cy=%2250%22 r=%2250%22 fill=%22%231b1c24%22/%3E%3Ccircle cx=%2250%22 cy=%2238%22 r=%2214%22 fill=%22%23505465%22/%3E%3Cpath d=%22M25 80c0-13.8 11.2-25 25-25s25 11.2 25 25v6H25v-6z%22 fill=%22%23505465%22/%3E%3C/svg%3E'; };
// series.js - Dedicated Series & Programs Content Page

document.addEventListener('DOMContentLoaded', () => {
    initSeriesPage();
});

const defaultAvatarSvg = "data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22 width=%22100%22 height=%22100%22%3E%3Ccircle cx=%2250%22 cy=%2250%22 r=%2250%22 fill=%22%231b1c24%22/%3E%3Ccircle cx=%2250%22 cy=%2238%22 r=%2214%22 fill=%22%23505465%22/%3E%3Cpath d=%22M25 80c0-13.8 11.2-25 25-25s25 11.2 25 25v6H25v-6z%22 fill=%22%23505465%22/%3E%3C/svg%3E";
const defaultPosterSvg = "data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 450' fill='%23141418'%3E%3Crect width='100%25' height='100%25' fill='%23141418'/%3E%3Cg fill='%23252530' opacity='0.8'%3E%3Ccircle cx='150' cy='180' r='48'/%3E%3Cpath d='M80 300c0-38 31-70 70-70s70 32 70 70v15H80v-15z'/%3E%3C/g%3E%3Ctext x='50%25' y='360' text-anchor='middle' fill='%23555566' font-family='Montserrat,sans-serif' font-weight='700' font-size='16' letter-spacing='2'%3EWDI%3C/text%3E%3C/svg%3E";
const defaultEpisodeThumbSvg = "data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 640 360' fill='%23141418'%3E%3Crect width='100%25' height='100%25' fill='%23141418'/%3E%3Ccircle cx='320' cy='180' r='38' fill='%2322222a'/%3E%3Cpath d='M312 165l24 15-24 15z' fill='%2300ffa3'/%3E%3Ctext x='50%25' y='255' text-anchor='middle' fill='%23555566' font-family='Montserrat,sans-serif' font-weight='600' font-size='15'%3EBÖLÜM%3C/text%3E%3C/svg%3E";

let currentSeriesData = null;
let currentSelectedSeason = 1;

async function initSeriesPage() {
    const loadingScreen = document.getElementById('loading-screen');

    // 1. Resolve Identifier (slug or UUID) from URL
    const slug = extractSlugFromUrl();
    if (!slug) {
        showError("Geçersiz dizi bağlantısı.");
        if (loadingScreen) loadingScreen.style.display = 'none';
        return;
    }

    try {
        // 2. Fetch Series Full Details
        const res = await fetch(`/api/series/${encodeURIComponent(slug)}/details`);
        if (!res.ok) {
            throw new Error(`Dizi detayları alınamadı (HTTP ${res.status})`);
        }
        const data = await res.json();
        currentSeriesData = data;

        // 3. Render Hero & Metadata
        renderHeroSection(data);

        // 4. Render Tabs and Episode / Cast / Info views
        renderTabs(data);

        // 5. Setup Action Handlers (Share, Scroll, Modal)
        setupActionHandlers(data);

    } catch (err) {
        console.error("Series Load Error:", err);
        showError("Dizi bilgileri yüklenirken bir sorun oluştu. Lütfen daha sonra tekrar deneyiniz.");
    } finally {
        if (loadingScreen) {
            loadingScreen.style.opacity = '0';
            setTimeout(() => { loadingScreen.style.display = 'none'; }, 300);
        }
    }
}

function extractSlugFromUrl() {
    const path = window.location.pathname.replace(/^\/|\/$/g, '');
    const segments = path.split('/');
    if (segments.length >= 2 && (segments[0] === 'series' || segments[0] === 'dizi' || segments[0] === 'program')) {
        return segments[1];
    }
    if (segments.length === 1 && segments[0] !== '') {
        return segments[0];
    }
    const params = new URLSearchParams(window.location.search);
    return params.get('slug') || params.get('id');
}

function renderHeroSection(series) {
    const title = series.name || series.title || 'İsimsiz Yapım';
    document.title = `${title} - Tüm Bölümleri ve Oyuncuları - BL Dizi`;

    // Breadcrumb
    const crumbType = document.getElementById('crumb-type');
    const crumbTitle = document.getElementById('crumb-title');
    const seriesType = series.seriesType || 'Dizi';
    if (crumbType) {
        crumbType.textContent = seriesType === 'Program' ? 'Programlar' : 'Diziler';
        crumbType.href = seriesType === 'Program' ? '/programlar' : '/diziler';
    }
    if (crumbTitle) crumbTitle.textContent = title;

    // Title
    const titleEl = document.getElementById('series-title');
    if (titleEl) titleEl.textContent = title;

    // Backdrop & Poster
    const backdropEl = document.getElementById('series-backdrop');
    const posterEl = document.getElementById('series-poster');
    const posterUrl = series.posterUrl || `/media/image/${series.id}`;

    if (backdropEl) {
        backdropEl.style.backgroundImage = `url('${posterUrl}')`;
    }
    if (posterEl) {
        posterEl.src = posterUrl;
        posterEl.alt = title;
        posterEl.onerror = () => { posterEl.onerror = null; posterEl.src = defaultPosterSvg; };
    }

    // Status Badge
    const statusTextEl = document.getElementById('series-status-text');
    const statusDotEl = document.querySelector('.series-poster-badge .status-dot');
    const isCompleted = series.finalStatus === 1;
    if (statusTextEl) {
        statusTextEl.textContent = isCompleted ? 'Final Yaptı' : 'Devam Ediyor';
    }
    if (statusDotEl && !isCompleted) {
        statusDotEl.classList.add('ongoing');
    }

    // Badges Row
    const badgeType = document.getElementById('badge-type');
    const badgeEp = document.getElementById('badge-episodes');
    const badgeCountry = document.getElementById('badge-country');
    const badgeLang = document.getElementById('badge-language');

    const totalEps = (series.episodes && series.episodes.length) || series.totalEpisodes || 0;
    if (badgeType) badgeType.innerHTML = `<i class="fas fa-tv"></i> ${escapeHtml(seriesType)}`;
    if (badgeEp) badgeEp.innerHTML = `<i class="fas fa-film"></i> ${totalEps} Bölüm`;
    if (badgeCountry) {
        const countryName = formatCountry(series.country);
        badgeCountry.innerHTML = `<i class="fas fa-globe-asia"></i> ${escapeHtml(countryName)}`;
    }
    if (badgeLang) {
        badgeLang.innerHTML = `<i class="fas fa-language"></i> ${escapeHtml(series.language || 'Asya Dili')}`;
    }

    const badgeYear = document.getElementById('badge-year');
    const badgeYearVal = document.getElementById('badge-year-val');
    if (series.uploadDate && badgeYear && badgeYearVal) {
        const year = new Date(series.uploadDate).getFullYear();
        if (!isNaN(year) && year > 1900) {
            badgeYearVal.textContent = year;
            badgeYear.style.display = 'inline-flex';
        }
    }

    // Genre / Categories
    const genresContainer = document.getElementById('series-genres');
    if (genresContainer && series.category) {
        genresContainer.innerHTML = '';
        const genres = series.category.split(',').map(g => g.trim()).filter(Boolean);
        genres.forEach(genre => {
            const tag = document.createElement('span');
            tag.className = 'genre-tag';
            tag.textContent = genre;
            genresContainer.appendChild(tag);
        });
    }

    // Synopsis / Konusu
    const summaryEl = document.getElementById('series-summary');
    const toggleBtn = document.getElementById('btn-summary-toggle');
    const summaryText = series.summary && series.summary.trim() !== ''
        ? series.summary
        : `${title} dizisinin tüm bölümlerini Full HD Türkçe altyazılı olarak izleyebilirsiniz.`;

    if (summaryEl) {
        summaryEl.textContent = summaryText;
        if (summaryText.length > 200 && toggleBtn) {
            toggleBtn.style.display = 'inline-flex';
            toggleBtn.addEventListener('click', () => {
                const isExpanded = summaryEl.classList.toggle('expanded');
                toggleBtn.innerHTML = isExpanded
                    ? 'Daha Az Göster <i class="fas fa-chevron-up"></i>'
                    : 'Daha Fazla Göster <i class="fas fa-chevron-down"></i>';
            });
        }
    }

    // Primary CTA Button: 1. Bölümü İzle
    const btnWatch = document.getElementById('btn-start-watch');
    if (btnWatch) {
        if (series.firstEpisodeUrl) {
            btnWatch.href = series.firstEpisodeUrl;
            btnWatch.innerHTML = '<i class="fas fa-play"></i> <span>1. Bölümü İzle</span>';
        } else if (series.episodes && series.episodes.length > 0) {
            const firstEp = series.episodes[0];
            const epUrl = (firstEp.slug && firstEp.slug !== '') ? `/${firstEp.slug}` : `/${firstEp.id}`;
            btnWatch.href = epUrl;
            btnWatch.innerHTML = '<i class="fas fa-play"></i> <span>1. Bölümü İzle</span>';
        } else {
            btnWatch.style.opacity = '0.5';
            btnWatch.style.pointerEvents = 'none';
            btnWatch.innerHTML = '<i class="fas fa-clock"></i> <span>Bölümler Yakında</span>';
        }
    }
}

function renderTabs(series) {
    const episodes = series.episodes || [];
    const cast = series.cast || [];

    // Tab Counters
    const countEpEl = document.getElementById('tab-count-episodes');
    const countCastEl = document.getElementById('tab-count-cast');
    if (countEpEl) countEpEl.textContent = episodes.length;
    if (countCastEl) countCastEl.textContent = cast.length;

    // 1. Render Episodes
    renderEpisodes(episodes, series);

    // 2. Render Cast
    renderCast(cast);

    // 3. Render Info
    renderInfo(series, episodes);

    // Tab switching event
    const tabButtons = document.querySelectorAll('.series-tabs-container .tab-item');
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            tabButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const targetId = btn.dataset.tab;
            document.querySelectorAll('.tab-panel').forEach(pane => {
                pane.classList.remove('active');
            });
            const targetPane = document.getElementById(targetId);
            if (targetPane) targetPane.classList.add('active');
        });
    });
}

function renderEpisodes(episodes, series) {
    const grid = document.getElementById('episodes-grid');
    const emptyEl = document.getElementById('episodes-empty');
    const switchContainer = document.getElementById('seasonSwitch');

    if (!grid) return;
    grid.innerHTML = '';

    if (!episodes || episodes.length === 0) {
        if (emptyEl) emptyEl.style.display = 'block';
        if (switchContainer) switchContainer.style.display = 'none';
        return;
    }
    if (emptyEl) emptyEl.style.display = 'none';

    // Group episodes by seasonNumber
    const seasonsMap = new Map();
    episodes.forEach(ep => {
        const sNum = ep.seasonNumber || 1;
        if (!seasonsMap.has(sNum)) {
            seasonsMap.set(sNum, []);
        }
        seasonsMap.get(sNum).push(ep);
    });

    const seasonNumbers = Array.from(seasonsMap.keys()).sort((a, b) => a - b);
    if (seasonNumbers.length === 0) seasonNumbers.push(1);
    currentSelectedSeason = seasonNumbers[0];

    // Build the dynamic-switch matching admin panel slider exactly
    if (switchContainer) {
        switchContainer.style.display = 'flex';
        switchContainer.innerHTML = '';

        seasonNumbers.forEach((sNum, idx) => {
            const inputId = `seasonRadio_${sNum}`;
            const radio = document.createElement('input');
            radio.type = 'radio';
            radio.id = inputId;
            radio.name = 'seasonRadioGroup';
            radio.value = sNum;
            if (sNum === currentSelectedSeason) radio.checked = true;

            const label = document.createElement('label');
            label.htmlFor = inputId;
            label.textContent = `${sNum}. Sezon`;

            radio.addEventListener('change', () => {
                if (radio.checked) {
                    currentSelectedSeason = sNum;
                    updateSwitchSlider(switchContainer);
                    displaySeasonEpisodes(seasonsMap.get(sNum) || [], series);
                }
            });

            switchContainer.appendChild(radio);
            switchContainer.appendChild(label);
        });

        const slider = document.createElement('div');
        slider.className = 'dynamic-slider';
        switchContainer.appendChild(slider);

        // Update slider position after elements are attached
        requestAnimationFrame(() => updateSwitchSlider(switchContainer));
        setTimeout(() => updateSwitchSlider(switchContainer), 80);
    }

    displaySeasonEpisodes(seasonsMap.get(currentSelectedSeason) || episodes, series);
}

function updateSwitchSlider(container) {
    if (!container) return;
    const checkedRadio = container.querySelector('input[type="radio"]:checked');
    const slider = container.querySelector('.dynamic-slider');
    if (checkedRadio && slider) {
        const label = container.querySelector(`label[for="${checkedRadio.id}"]`);
        if (label) {
            slider.style.width = label.offsetWidth + 'px';
            slider.style.left = label.offsetLeft + 'px';
        }
    }
}

window.addEventListener('resize', () => {
    updateSwitchSlider(document.getElementById('seasonSwitch'));
});

function displaySeasonEpisodes(seasonEpisodes, series) {
    const grid = document.getElementById('episodes-grid');
    const infoCountEl = document.getElementById('episodes-info-count');
    if (!grid) return;
    grid.innerHTML = '';

    if (infoCountEl) {
        infoCountEl.textContent = `${seasonEpisodes.length} Bölüm listeleniyor`;
    }

    const seriesFallbackThumb = `/media/image/${series.id}`;

    seasonEpisodes.forEach(ep => {
        const epSlug = (ep.slug && ep.slug.trim() !== '') ? ep.slug : ep.id;
        const watchUrl = `/${epSlug}`;
        const epNum = ep.episodeNumber || 1;
        const epLabel = `${epNum}. Bölüm`;
        const epName = (ep.name && ep.name.trim() !== '' && !ep.name.toLowerCase().includes('sezon'))
            ? ep.name
            : epLabel;
        const epThumb = `/media/image/${ep.id}`;

        const card = document.createElement('a');
        card.href = watchUrl;
        card.className = 'episode-card';
        card.title = `${series.name || ''} - ${epLabel} İzle`;

        card.innerHTML = `
            <div class="episode-thumb-wrap">
                <img src="${epThumb}" alt="${escapeHtml(epName)}" loading="lazy"
                     onerror="if(!this.dataset.fallbackTried && '${seriesFallbackThumb}' && '${seriesFallbackThumb}' !== '${epThumb}'){ this.dataset.fallbackTried='true'; this.src='${seriesFallbackThumb}'; } else { this.onerror=null; this.src='${defaultEpisodeThumbSvg}'; }">
                <div class="episode-play-overlay">
                    <div class="play-circle-btn"><i class="fas fa-play"></i></div>
                </div>
                <div class="episode-meta-badges">
                    <span class="badge-ep-num"><i class="fas fa-play" style="font-size:0.62rem; margin-right:4px;"></i>${ep.episodeNumber || 1}. Bölüm</span>
                    ${ep.duration > 0 ? `<span class="badge-ep-duration"><i class="far fa-clock"></i> ${ep.duration} dk</span>` : ''}
                </div>
            </div>
            <div class="episode-card-body">
                <h4 class="episode-card-title">${escapeHtml(epName)}</h4>
                <div class="episode-card-sub">
                    <i class="fas fa-circle-play" style="color: var(--primary-color);"></i>
                    <span>Şimdi İzle</span>
                </div>
            </div>
        `;

        grid.appendChild(card);
    });
}

function renderCast(cast) {
    const grid = document.getElementById('cast-grid');
    const emptyEl = document.getElementById('cast-empty');
    if (!grid) return;
    grid.innerHTML = '';

    if (!cast || cast.length === 0) {
        if (emptyEl) emptyEl.style.display = 'block';
        return;
    }
    if (emptyEl) emptyEl.style.display = 'none';

    cast.forEach(actor => {
        const card = document.createElement('div');
        card.className = 'cast-card';
        card.title = `${actor.name || 'Oyuncu'} detaylarını incele`;

        const photoUrl = actor.photoUrl || (actor.actorId ? `/media/actor/${actor.actorId}` : defaultAvatarSvg);
        const role = actor.characterName ? cleanCharacterName(actor.characterName, actor.name) : '';

        card.innerHTML = `
            <div class="cast-avatar-wrap">
                <img src="${photoUrl}" alt="" loading="lazy" onerror="window.handleActorAvatarError(this)">
            </div>
            <div class="cast-actor-name">${escapeHtml(actor.name || 'Oyuncu')}</div>
            ${role ? `<div class="cast-character-name">${escapeHtml(role)}</div>` : ''}
            <div class="cast-profile-pill"><i class="fas fa-info-circle"></i> Profili İncele</div>
        `;

        card.addEventListener('click', () => {
            openActorModal(actor.actorId, actor.name, photoUrl, actor.characterName, actor.tmdbId);
        });

        grid.appendChild(card);
    });
}

function renderInfo(series, episodes) {
    const titleEl = document.getElementById('info-val-title');
    const typeEl = document.getElementById('info-val-type');
    const genresEl = document.getElementById('info-val-genres');
    const countryEl = document.getElementById('info-val-country');
    const langEl = document.getElementById('info-val-language');
    const statusEl = document.getElementById('info-val-status');
    const epEl = document.getElementById('info-val-episodes');

    if (titleEl) titleEl.textContent = series.name || series.title || '-';
    if (typeEl) typeEl.textContent = series.seriesType || 'Dizi';
    if (genresEl) genresEl.textContent = series.category || 'Belirtilmemiş';
    if (countryEl) countryEl.textContent = formatCountry(series.country);
    if (langEl) langEl.textContent = series.language || 'Orijinal';
    if (statusEl) statusEl.textContent = series.finalStatus === 1 ? 'Final Yaptı' : 'Devam Ediyor';
    if (epEl) epEl.textContent = `${episodes.length} Bölüm`;
}

function setupActionHandlers(series) {
    // Scroll to episodes button
    const btnScroll = document.getElementById('btn-scroll-episodes');
    if (btnScroll) {
        btnScroll.addEventListener('click', () => {
            const tabsNav = document.getElementById('series-tabs-nav');
            if (tabsNav) {
                tabsNav.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            // Switch to episodes tab
            const epTabBtn = document.querySelector('.tab-item[data-tab="tab-episodes"]');
            if (epTabBtn) epTabBtn.click();
        });
    }

    // Share / Copy Link button
    const btnShare = document.getElementById('btn-share');
    if (btnShare) {
        btnShare.addEventListener('click', () => {
            const url = window.location.href;
            if (navigator.clipboard) {
                navigator.clipboard.writeText(url).then(() => {
                    showToast("Dizi bağlantısı panoya kopyalandı!");
                }).catch(() => {
                    fallbackCopy(url);
                });
            } else {
                fallbackCopy(url);
            }
        });
    }

    // Modal Close button
    const modalCloseBtn = document.getElementById('actor-modal-close');
    const modalOverlay = document.getElementById('actor-modal');
    if (modalCloseBtn && modalOverlay) {
        modalCloseBtn.addEventListener('click', closeActorModal);
        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) closeActorModal();
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modalOverlay.classList.contains('active')) {
                closeActorModal();
            }
        });
    }
}

// --- Actor Details Modal ---
async function openActorModal(actorId, fallbackName, fallbackPhoto, contextCharacter, tmdbId) {
    const overlay = document.getElementById('actor-modal');
    const modalBody = document.getElementById('actor-modal-body');
    if (!overlay || !modalBody) return;

    modalBody.innerHTML = `
        <div class="actor-header-row">
            <div class="actor-modal-photo-wrap">
                <img src="${fallbackPhoto || defaultAvatarSvg}" alt="${escapeHtml(fallbackName || 'Oyuncu')}">
            </div>
            <div class="actor-header-details">
                <h2>${escapeHtml(fallbackName || 'Oyuncu')}</h2>
                <div class="actor-badges">
                    <span class="actor-badge primary"><i class="fas fa-circle-notch fa-spin"></i> Bilgiler alınıyor...</span>
                </div>
            </div>
        </div>
        <div class="actor-modal-loading">
            <i class="fas fa-circle-notch fa-spin"></i>
            <span>Oyuncu detayları ve yapımları yükleniyor...</span>
        </div>
    `;

    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');

    try {
        let endpoint = '';
        if (actorId) {
            endpoint = `/api/actor/${actorId}`;
        } else {
            const params = new URLSearchParams();
            if (fallbackName) params.append('name', fallbackName);
            if (tmdbId) params.append('tmdbId', tmdbId);
            if (fallbackPhoto) params.append('photoUrl', fallbackPhoto);
            endpoint = `/api/actor/resolve?${params.toString()}`;
        }

        const res = await fetch(endpoint);
        if (!res.ok) throw new Error('Oyuncu detayları getirilemedi');
        const actor = await res.json();
        renderActorModalContent(modalBody, actor, fallbackName, fallbackPhoto, contextCharacter);
    } catch (e) {
        console.error('Actor fetch error:', e);
        modalBody.innerHTML = `
            <div class="actor-header-row">
                <div class="actor-modal-photo-wrap">
                    <img src="${fallbackPhoto || defaultAvatarSvg}" alt="${escapeHtml(fallbackName || 'Oyuncu')}">
                </div>
                <div class="actor-header-details">
                    <h2>${escapeHtml(fallbackName || 'Oyuncu')}</h2>
                    <div class="actor-badges">
                        <span class="actor-badge primary"><i class="fas fa-user"></i> Oyuncu</span>
                    </div>
                </div>
            </div>
            <div class="actor-empty-prod" style="margin-top: 20px;">
                Oyuncu detayları şu anda yüklenemedi. Lütfen daha sonra tekrar deneyiniz.
            </div>
        `;
    }
}

function renderActorModalContent(modalBody, actor, fallbackName, fallbackPhoto, contextCharacter) {
    const name = actor.name || fallbackName || 'Oyuncu';
    const photo = actor.photoUrl || fallbackPhoto || defaultAvatarSvg;
    const knownFor = actor.knownFor || 'Oyuncu';
    const birthInfo = formatBirthDate(actor.birthday, actor.deathday);
    const placeOfBirth = actor.placeOfBirth;

    const badgesHtml = [
        `<span class="actor-badge primary"><i class="fas fa-film"></i> ${escapeHtml(knownFor)}</span>`
    ];

    if (birthInfo) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-birthday-cake"></i> ${escapeHtml(birthInfo)}</span>`);
    }

    if (placeOfBirth) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-map-marker-alt"></i> ${escapeHtml(placeOfBirth)}</span>`);
    }

    const cleanContext = cleanCharacterName(contextCharacter, name);
    if (cleanContext) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-mask"></i> Bu Yapımda: ${escapeHtml(cleanContext)}</span>`);
    }

    const bioHtml = actor.biography
        ? `<div class="actor-bio-text">${escapeHtml(actor.biography)}</div>`
        : `<div class="actor-bio-text empty">Bu oyuncu için henüz biyografi bilgisi eklenmedi.</div>`;

    const prods = actor.productions || [];
    let prodsHtml = '';
    if (prods.length > 0) {
        prodsHtml = `
            <div class="actor-productions-grid">
                ${prods.map(p => {
                    const isFilm = p.type === 'movie';
                    const badgeClass = isFilm ? 'film' : 'dizi';
                    const badgeText = isFilm ? 'Film' : (p.seriesType || 'Dizi');
                    const linkUrl = `/${p.slug || p.id}`;
                    const title = p.name || 'İsimsiz Yapım';
                    const year = p.releaseYear || '';
                    const role = cleanCharacterName(p.characterName, title);
                    const category = p.category || '';
                    const poster = p.posterUrl || `/media/image/${p.id}`;

                    return `
                        <a href="${linkUrl}" class="actor-prod-card" title="${escapeHtml(title)} izle">
                            <div class="actor-prod-poster">
                                <span class="actor-prod-badge ${badgeClass}">${badgeText}</span>
                                ${year ? `<span class="actor-prod-year">${year}</span>` : ''}
                                <img src="${poster}" alt="${escapeHtml(title)}" loading="lazy"
                                     onerror="this.onerror=null; this.src='${defaultPosterSvg}'">
                            </div>
                            <div class="actor-prod-info">
                                <div class="actor-prod-title">${escapeHtml(title)}</div>
                                ${role ? `<div class="actor-prod-role"><i class="fas fa-user-tag" style="font-size:0.7rem; margin-right:4px;"></i>${escapeHtml(role)}</div>` : ''}
                                ${category ? `<div class="actor-prod-category">${escapeHtml(category)}</div>` : ''}
                            </div>
                        </a>
                    `;
                }).join('')}
            </div>
        `;
    } else {
        prodsHtml = `<div class="actor-empty-prod">Platformumuzda bu oyuncunun yer aldığı başka bir yapım henüz bulunmuyor.</div>`;
    }

    modalBody.innerHTML = `
        <div class="actor-header-row">
            <div class="actor-modal-photo-wrap">
                <img src="${photo}" alt="" onerror="window.handleActorAvatarError(this)">
            </div>
            <div class="actor-header-details">
                <h2>${escapeHtml(name)}</h2>
                <div class="actor-badges">
                    ${badgesHtml.join('')}
                </div>
            </div>
        </div>

        <div class="actor-modal-section-title">
            <i class="fas fa-book-open"></i> Biyografi
        </div>
        ${bioHtml}

        <div class="actor-modal-section-title">
            <i class="fas fa-tv"></i> Platformda Yer Aldığı Yapımlar
            <span class="count-badge">${prods.length} Yapım</span>
        </div>
        ${prodsHtml}
    `;
}

function closeActorModal() {
    const overlay = document.getElementById('actor-modal');
    if (overlay) {
        overlay.classList.remove('active');
        overlay.setAttribute('aria-hidden', 'true');
    }
}

// --- Helpers ---
function formatBirthDate(birthStr, deathStr) {
    if (!birthStr) return null;
    const months = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran', 'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];
    try {
        const parts = birthStr.split('-');
        if (parts.length === 3) {
            const year = parseInt(parts[0], 10);
            const month = parseInt(parts[1], 10) - 1;
            const day = parseInt(parts[2], 10);

            let age = null;
            if (!deathStr) {
                const today = new Date();
                age = today.getFullYear() - year;
                const mDiff = today.getMonth() - month;
                if (mDiff < 0 || (mDiff === 0 && today.getDate() < day)) {
                    age--;
                }
            }

            const formatted = `${day} ${months[month] || ''} ${year}`;
            return age !== null && age > 0 ? `${formatted} (${age} yaşında)` : formatted;
        }
    } catch (e) {}
    return birthStr;
}

function cleanCharacterName(role, contextTitle) {
    if (!role || typeof role !== 'string') return '';
    let clean = role.trim();
    if (contextTitle) {
        clean = clean.replace(new RegExp('^' + escapeRegex(contextTitle) + '\\s*[-:]\\s*', 'i'), '');
    }
    return clean;
}

function escapeRegex(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.toString().replace(/[&<>"']/g, m => map[m]);
}

function formatCountry(country) {
    if (!country) return 'Asya';
    const c = country.trim().toUpperCase();
    switch (c) {
        case 'KR': return 'Güney Kore';
        case 'JP': return 'Japonya';
        case 'CN': return 'Çin';
        case 'TH': return 'Tayland';
        case 'TW': return 'Tayvan';
        case 'PH': return 'Filipinler';
        default: return country;
    }
}

function showToast(msg) {
    const toast = document.getElementById('series-toast');
    const toastMsg = document.getElementById('toast-message');
    if (toast && toastMsg) {
        toastMsg.textContent = msg;
        toast.classList.add('show');
        setTimeout(() => { toast.classList.remove('show'); }, 3000);
    }
}

function fallbackCopy(text) {
    const tempInput = document.createElement('input');
    tempInput.value = text;
    document.body.appendChild(tempInput);
    tempInput.select();
    try {
        document.execCommand('copy');
        showToast("Dizi bağlantısı panoya kopyalandı!");
    } catch (e) {
        alert("Bağlantı: " + text);
    }
    document.body.removeChild(tempInput);
}

function showError(msg) {
    const container = document.getElementById('series-main-container');
    if (container) {
        container.innerHTML = `
            <div style="max-width: 600px; margin: 80px auto; text-align: center; padding: 40px 20px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08); border-radius: 18px;">
                <i class="fas fa-circle-exclamation" style="font-size: 3rem; color: #ff5252; margin-bottom: 18px;"></i>
                <h2 style="margin: 0 0 10px 0; color: #fff;">İçerik Bulunamadı</h2>
                <p style="color: rgba(255,255,255,0.7); margin-bottom: 24px;">${escapeHtml(msg)}</p>
                <a href="/" class="btn-action primary" style="display: inline-flex; width: auto; margin: 0 auto;">Ana Sayfaya Dön</a>
            </div>
        `;
    }
}
