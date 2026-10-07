// contentDetails.js
import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initContentDetails(videoId) {
    const detailsToggle = document.querySelector('.details-toggle');
    const detailsContainer = document.querySelector('.content-details-container');

    detailsToggle?.addEventListener('click', () => {
        detailsToggle.classList.toggle('active');
        detailsContainer.classList.toggle('open');
    });

    if (videoId) loadContentDetails(videoId);
}

async function loadContentDetails(id) {
    try {
        const response = await fetch(`/api/video/details?id=${id}`);
        if (!response.ok) throw new Error('Details not found');
        const data = await response.json();

        // Update title and +18 badge
        const titleEl = document.getElementById('title');
        const contentTitleEl = document.getElementById('contentTitle');
        if (titleEl) {
            titleEl.textContent = data.title;
        }

        if (contentTitleEl) {
            const spans = contentTitleEl.querySelectorAll('span');
            contentTitleEl.textContent = data.title + ' ';
            spans.forEach(span => contentTitleEl.appendChild(span));
        }

        // Handle Adult Content Video Blur Overlay
        const adultOverlay = document.getElementById('adultOverlay');
        const videoWrapper = document.querySelector('.video-wrapper');
        const removeBlurBtn = document.getElementById('removeBlurBtn');

        if (data.adult && adultOverlay && videoWrapper) {
            videoWrapper.classList.add('blurred-adult');
            adultOverlay.classList.add('active');

            if (removeBlurBtn) {
                removeBlurBtn.addEventListener('click', () => {
                    videoWrapper.classList.remove('blurred-adult');
                    adultOverlay.classList.remove('active');
                    // Play video if possible
                    const videoPlayer = document.getElementById('videoPlayer');
                    if (videoPlayer) {
                        videoPlayer.play().catch(e => console.log('Auto-play prevented:', e));
                    }
                });
            }
        }

        // Update document title with SEO keyword
        document.title = `${data.title} İzle - Who Dat Idols?`;

        // SEO: Update Meta Description and Canonical
        if (data.plot) {
            const metaDescription = document.querySelector('meta[name="description"]');
            if (metaDescription) metaDescription.setAttribute('content', data.plot.substring(0, 160));
        }

        const canonicalLink = document.getElementById('canonicalLink');
        if (canonicalLink && data.slug) {
            // Fix: remove '/watch/' for root-level slugs
            canonicalLink.setAttribute('href', `https://whodatidols.com/${data.slug}`);
        }

        // SEO: Structured Data (JSON-LD)
        const structuredDataEl = document.getElementById('structuredData');
        if (structuredDataEl) {
            const sanitizeText = (text) => {
                if (!text) return undefined;
                return text
                    .replace(/[\u0000-\u001F\u007F]/g, ' ')
                    .replace(/\s+/g, ' ')
                    .trim()
                    .substring(0, 500);
            };

            const formatDuration = (dur) => {
                if (!dur) return undefined;
                const match = dur.match(/(\d+)/);
                return match ? `PT${match[1]}M` : undefined;
            };

            const baseUrl = 'https://whodatidols.com';
            const slug = data.slug || id;
            const contentUrl = `${baseUrl}/${slug}`;

            // Main content schema
            const contentSchema = {
                "@context": "https://schema.org",
                "@type": data.type === 'movie' ? "Movie" : "Episode",
                "name": data.title,
                "url": contentUrl,
                "description": sanitizeText(data.plot),
                "image": `${baseUrl}/media/image/${id}`,
                "datePublished": data.year ? String(data.year) : undefined,
                "duration": formatDuration(data.duration),
                "potentialAction": {
                    "@type": "WatchAction",
                    "target": contentUrl
                }
            };

            if (data.type === 'episode' && data.season) {
                contentSchema.partOfSeason = {
                    "@type": "CreativeWorkSeason",
                    "seasonNumber": data.season
                };
            }

            // BreadcrumbList
            const breadcrumbItems = [
                { "@type": "ListItem", "position": 1, "name": "Ana Sayfa", "item": baseUrl }
            ];

            if (data.type === 'movie') {
                breadcrumbItems.push({ "@type": "ListItem", "position": 2, "name": "Filmler", "item": `${baseUrl}/filmler` });
            } else {
                breadcrumbItems.push({ "@type": "ListItem", "position": 2, "name": "Diziler", "item": `${baseUrl}/diziler` });
            }

            breadcrumbItems.push({ "@type": "ListItem", "position": 3, "name": data.title, "item": contentUrl });

            const breadcrumbSchema = {
                "@context": "https://schema.org",
                "@type": "BreadcrumbList",
                "itemListElement": breadcrumbItems
            };

            structuredDataEl.textContent = JSON.stringify([contentSchema, breadcrumbSchema]);
        }

        const posterImg = document.getElementById('contentPoster');
        if (posterImg) {
            posterImg.src = `/media/image/${id}`;
            handleImageSkeleton(posterImg);
        }

        // Details metadata with stylish modern icons
        const yearEl = document.getElementById('releaseYear');
        if (yearEl) {
            if (data.year) {
                yearEl.innerHTML = `<i class="far fa-calendar-alt"></i> ${escapeHtml(data.year)}`;
                yearEl.style.display = 'inline-flex';
            } else {
                yearEl.style.display = 'none';
            }
        }

        const durationEl = document.getElementById('contentDuration');
        if (durationEl) {
            if (data.duration) {
                durationEl.innerHTML = `<i class="far fa-clock"></i> ${escapeHtml(data.duration)}`;
                durationEl.style.display = 'inline-flex';
            } else {
                durationEl.style.display = 'none';
            }
        }

        const langEl = document.getElementById('contentLanguage');
        if (langEl) {
            if (data.language) {
                langEl.innerHTML = `<i class="fas fa-language"></i> ${escapeHtml(data.language)}`;
                langEl.style.display = 'inline-flex';
            } else {
                langEl.style.display = 'none';
            }
        }

        const countryName = getCountryName(data.country);
        const countryEl = document.getElementById('contentCountry');
        if (countryEl) {
            if (countryName) {
                countryEl.innerHTML = `<i class="fas fa-globe-americas"></i> ${escapeHtml(countryName)}`;
                countryEl.style.display = 'inline-flex';
            } else {
                countryEl.style.display = 'none';
            }
        }

        const plotEl = document.getElementById('contentPlot');
        if (plotEl) {
            plotEl.textContent = data.plot || 'Açıklama bulunmuyor.';
        }

        // Season/Episode/Navigation Visibility (Only for episodes)
        const seasonSection = document.getElementById('seasonSection');
        const episodeNav = document.getElementById('episodeNav');
        const episodeDrawer = document.getElementById('episodeSection');

        if (data.type === 'episode') {
            if (seasonSection) seasonSection.style.display = 'block';
            if (episodeNav) episodeNav.style.display = 'flex';
            if (episodeDrawer) episodeDrawer.style.display = 'block';

            const seasonNumEl = document.getElementById('seasonNumber');
            if (seasonNumEl) {
                seasonNumEl.innerHTML = `<i class="fas fa-layer-group"></i> Sezon ${escapeHtml(data.season)}`;
            }

            const epNumEl = document.getElementById('episodeNumber');
            if (epNumEl) {
                epNumEl.innerHTML = `<i class="fas fa-play-circle"></i> Bölüm ${escapeHtml(data.episode)}`;
            }

            const totalEpisodesEl = document.getElementById('totalEpisodes');
            if (totalEpisodesEl) {
                if (data.finalStatus === 1) {
                    totalEpisodesEl.innerHTML = `<i class="fas fa-flag-checkered"></i> Final`;
                    totalEpisodesEl.style.display = 'inline-flex';
                } else if (data.finalStatus === 2) {
                    totalEpisodesEl.innerHTML = `<i class="fas fa-hourglass-half"></i> Sezon Finali`;
                    totalEpisodesEl.style.display = 'inline-flex';
                } else {
                    totalEpisodesEl.style.display = 'none';
                }
            }

        } else {
            if (seasonSection) seasonSection.style.display = 'none';
            if (episodeNav) episodeNav.style.display = 'none';
            if (episodeDrawer) episodeDrawer.style.display = 'none';
        }

        const genresEl = document.getElementById('genreTags');
        if (genresEl) {
            genresEl.innerHTML = '';
            if (data.genres) {
                data.genres.forEach(g => {
                    const span = document.createElement('span');
                    span.className = 'genre-tag';
                    span.textContent = g.trim();
                    genresEl.appendChild(span);
                });
            }
        }

        // Cast rendering - Responsive Grid without scrollbars
        const castList = document.getElementById('castList');
        if (castList) {
            castList.innerHTML = '';
            if (data.cast && data.cast.length > 0) {
                const defaultAvatarSvg = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='70' height='70' viewBox='0 0 24 24' fill='%23666'><path d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/></svg>";
                data.cast.forEach((actor, index) => {
                    const member = document.createElement('div');
                    member.className = 'cast-member';
                    member.style.animationDelay = `${(index + 1) * 0.05}s`;
                    member.setAttribute('role', 'button');
                    member.setAttribute('tabindex', '0');
                    member.setAttribute('title', `${actor.name || 'Oyuncu'} detaylarını görüntüle`);

                    // Attach click handler to open actor details modal
                    if (actor.actorId) {
                        const triggerModal = () => {
                            openActorModal(actor.actorId, actor.name, actor.photoUrl, actor.characterName);
                        };
                        member.addEventListener('click', triggerModal);
                        member.addEventListener('keydown', (e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                triggerModal();
                            }
                        });
                    }

                    const avatar = document.createElement('div');
                    avatar.className = 'cast-avatar';
                    const img = document.createElement('img');
                    img.src = actor.photoUrl || defaultAvatarSvg;
                    img.alt = actor.name || 'Oyuncu';
                    img.loading = 'lazy';
                    img.onerror = function () {
                        this.onerror = null;
                        this.src = defaultAvatarSvg;
                    };
                    avatar.appendChild(img);

                    const name = document.createElement('div');
                    name.className = 'cast-name';
                    name.textContent = actor.name || '';
                    name.title = actor.name || '';

                    const role = document.createElement('div');
                    role.className = 'cast-role';
                    role.textContent = actor.characterName || '';
                    role.title = actor.characterName || '';

                    member.appendChild(avatar);
                    member.appendChild(name);
                    if (actor.characterName) {
                        member.appendChild(role);
                    }
                    castList.appendChild(member);
                });
            } else {
                castList.innerHTML = '<p style="color:#888; font-size:0.92rem; padding: 10px 0;">Oyuncu bilgisi bulunamadı.</p>';
            }
        }

        // Broadcast seriesId for other modules (like listModal)
        if (data.seriesId) {
            document.body.dataset.seriesId = data.seriesId;
            const event = new CustomEvent('contentDetailsLoaded', { detail: { seriesId: data.seriesId } });
            document.dispatchEvent(event);
        }

    } catch (error) {
        console.error('Content details loading error:', error);
        const titleEl = document.getElementById('contentTitle');
        if (titleEl) titleEl.innerText = "Detaylar yüklenemedi";
    }
}

// ---------------------------------------------------------------------------
// Actor Details Modal & Interaction
// ---------------------------------------------------------------------------
function getOrCreateActorModal() {
    let overlay = document.getElementById('actorModal');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'actorModal';
        overlay.className = 'actor-modal-overlay';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.setAttribute('aria-hidden', 'true');

        overlay.innerHTML = `
            <div class="actor-modal-dialog">
                <button type="button" class="actor-modal-close" aria-label="Kapat">
                    <i class="fas fa-times"></i>
                </button>
                <div class="actor-modal-body" id="actorModalBody"></div>
            </div>
        `;

        document.body.appendChild(overlay);

        const closeBtn = overlay.querySelector('.actor-modal-close');
        const closeModal = () => {
            overlay.classList.remove('active');
            overlay.setAttribute('aria-hidden', 'true');
            document.removeEventListener('keydown', handleKeydown);
        };

        const handleKeydown = (e) => {
            if (e.key === 'Escape') closeModal();
        };

        closeBtn.addEventListener('click', closeModal);
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) closeModal();
        });
        overlay._closeModal = closeModal;
        overlay._handleKeydown = handleKeydown;
    }
    return overlay;
}

async function openActorModal(actorId, fallbackName, fallbackPhoto, contextCharacter) {
    const overlay = getOrCreateActorModal();
    const modalBody = overlay.querySelector('#actorModalBody');
    const defaultAvatarSvg = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='70' height='70' viewBox='0 0 24 24' fill='%23666'><path d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/></svg>";

    // Render instant skeleton header while network request completes
    modalBody.innerHTML = `
        <div class="actor-header-row">
            <div class="actor-modal-photo-wrap">
                <img src="${fallbackPhoto || defaultAvatarSvg}" alt="${escapeHtml(fallbackName || 'Oyuncu')}"
                     onerror="this.onerror=null; this.src='${defaultAvatarSvg}'">
            </div>
            <div class="actor-header-details">
                <h2>${escapeHtml(fallbackName || 'Oyuncu')}</h2>
                <div class="actor-badges">
                    <span class="actor-badge primary"><i class="fas fa-user"></i> Oyuncu</span>
                    ${contextCharacter ? `<span class="actor-badge"><i class="fas fa-mask"></i> Bu Yapımda: ${escapeHtml(contextCharacter)}</span>` : ''}
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
    document.addEventListener('keydown', overlay._handleKeydown);

    try {
        const res = await fetch(`/api/actor/${actorId}`);
        if (!res.ok) {
            throw new Error('Actor info could not be retrieved');
        }
        const actor = await res.json();
        renderActorModalContent(modalBody, actor, fallbackName, fallbackPhoto, contextCharacter, defaultAvatarSvg);
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

function renderActorModalContent(modalBody, actor, fallbackName, fallbackPhoto, contextCharacter, defaultAvatarSvg) {
    const name = actor.name || fallbackName || 'Oyuncu';
    const photo = actor.photoUrl || fallbackPhoto || defaultAvatarSvg;
    const knownFor = actor.knownFor || 'Oyuncu';
    const birthInfo = formatBirthDate(actor.birthday, actor.deathday);
    const placeOfBirth = actor.placeOfBirth;

    // Badges array
    const badgesHtml = [
        `<span class="actor-badge primary"><i class="fas fa-film"></i> ${escapeHtml(knownFor)}</span>`
    ];

    if (birthInfo) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-birthday-cake"></i> ${escapeHtml(birthInfo)}</span>`);
    }

    if (placeOfBirth) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-map-marker-alt"></i> ${escapeHtml(placeOfBirth)}</span>`);
    }

    if (contextCharacter) {
        badgesHtml.push(`<span class="actor-badge"><i class="fas fa-mask"></i> Bu Yapımda: ${escapeHtml(contextCharacter)}</span>`);
    }

    // Bio - NO nested scrollbar
    const bioHtml = actor.biography
        ? `<div class="actor-bio-text">${escapeHtml(actor.biography)}</div>`
        : `<div class="actor-bio-text empty">Bu oyuncu için henüz biyografi bilgisi eklenmedi.</div>`;

    // Productions
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
                    const role = p.characterName ? `${p.characterName}` : '';
                    const category = p.category || '';
                    const poster = p.posterUrl || `/media/image/${p.id}`;

                    return `
                        <a href="${linkUrl}" class="actor-prod-card" title="${escapeHtml(title)} izle">
                            <div class="actor-prod-poster">
                                <span class="actor-prod-badge ${badgeClass}">${badgeText}</span>
                                ${year ? `<span class="actor-prod-year">${year}</span>` : ''}
                                <img src="${poster}" alt="${escapeHtml(title)}" loading="lazy"
                                     onerror="this.onerror=null; this.src='/placeholder.jpg'">
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
                <img src="${photo}" alt="${escapeHtml(name)}"
                     onerror="this.onerror=null; this.src='${defaultAvatarSvg}'">
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

function formatBirthDate(birthStr, deathStr) {
    if (!birthStr) return null;
    const months = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran', 'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];
    try {
        const parts = birthStr.split('-');
        if (parts.length < 3) return birthStr;
        const year = parseInt(parts[0], 10);
        const monthIndex = parseInt(parts[1], 10) - 1;
        const day = parseInt(parts[2], 10);
        const formattedDate = `${day} ${months[monthIndex] || ''} ${year}`;

        if (deathStr) {
            const dParts = deathStr.split('-');
            const dYear = parseInt(dParts[0], 10);
            const ageAtDeath = dYear - year;
            return `${formattedDate} (${ageAtDeath > 0 ? ageAtDeath + ' yaşında vefat etti' : ''})`;
        } else {
            const today = new Date();
            let age = today.getFullYear() - year;
            const m = today.getMonth() - monthIndex;
            if (m < 0 || (m === 0 && today.getDate() < day)) {
                age--;
            }
            return `${formattedDate} (${age} yaşında)`;
        }
    } catch (e) {
        return birthStr;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function getCountryName(code) {
    if (!code) return null;
    const countries = {
        'kr': 'Güney Kore',
        'jp': 'Japonya',
        'th': 'Tayland',
        'cn': 'Çin',
        'tw': 'Tayvan',
        'ph': 'Filipinler',
        'id': 'Endonezya',
        'my': 'Malezya',
        'sg': 'Singapur',
        'be': 'Belçika',
        'us': 'USA',
        'ch': 'İsviçre',
        'hk': 'Hong Kong',
        'ca': 'Kanada',
        'es': 'İspanya',
        'in': 'Hindistan',
        'vn': 'Vietnam',
        'kh': 'Kamboçya',
        'ee': 'Estonya',
        'de': 'Almanya',
        'fr': 'Fransa',
        'hr': 'Hırvatistan',
        'nl': 'Hollanda',
        'gb': 'UK',
        'at': 'Avusturya',
        'it': 'İtalya',
        'pl': 'Polonya',
        'ar': 'Arjantin',
        'br': 'Brezilya',
        'ie': 'İrlanda'
    };
    return countries[code.toLowerCase()] || code;
}
