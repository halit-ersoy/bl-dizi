import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initFeaturedContent() {
    const featuredGrid = document.querySelector('.featured-grid');
    const movieToggle = document.getElementById('featured-movies-toggle');
    const tvToggle = document.getElementById('featured-tv-toggle');
    const toggleContainer = document.querySelector('.featured-toggle');
    const paginationContainer = document.getElementById('featured-pagination');

    if (!featuredGrid || !paginationContainer) return;
    if (!movieToggle || !tvToggle || !toggleContainer) {
        console.error('Featured Content Section: Gerekli elementler bulunamadı.');
        return;
    }

    let currentMode = 'tv';
    let currentPage = 1;
    let itemsPerPage = window.innerWidth <= 1024 ? 10 : 20;
    let isLoading = false;

    const countryNames = {
        kr: 'Güney Kore',
        jp: 'Japonya',
        th: 'Tayland',
        cn: 'Çin',
        tw: 'Tayvan',
        ph: 'Filipinler',
        id: 'Endonezya',
        my: 'Malezya',
        sg: 'Singapur',
        be: 'Belçika',
        us: 'USA',
        ch: 'İsviçre',
        hk: 'Hong Kong',
        ca: 'Kanada',
        es: 'İspanya',
        in: 'Hindistan',
        vn: 'Vietnam',
        kh: 'Kamboçya',
        ee: 'Estonya',
        de: 'Almanya',
        fr: 'Fransa',
        hr: 'Hırvatistan',
        nl: 'Hollanda',
        gb: 'UK',
        at: 'Avusturya',
        it: 'İtalya',
        pl: 'Polonya',
        ar: 'Arjantin',
        br: 'Brezilya',
        ie: 'İrlanda'
    };

    updateToggleState();
    loadPage(1);

    movieToggle.addEventListener('click', () => {
        if (currentMode !== 'movies' && !isLoading) {
            switchMode('movies');
        }
    });

    tvToggle.addEventListener('click', () => {
        if (currentMode !== 'tv' && !isLoading) {
            switchMode('tv');
        }
    });

    function switchMode(mode) {
        isLoading = true;
        currentMode = mode;
        currentPage = 1;
        updateToggleState();
        loadPage(1);
    }

    function updateToggleState() {
        movieToggle.classList.toggle('active', currentMode === 'movies');
        tvToggle.classList.toggle('active', currentMode === 'tv');
        toggleContainer.classList.toggle('movies-active', currentMode === 'movies');
    }

    async function loadPage(page) {
        isLoading = true;
        currentPage = page;
        itemsPerPage = window.innerWidth <= 1024 ? 10 : 20;
        console.log(`[Featured] Loading page ${page}, itemsPerPage: ${itemsPerPage} (innerWidth: ${window.innerWidth})`);

        // Match Weekly Best transition: add fade-out and wait
        featuredGrid.classList.add('fade-out');

        setTimeout(async () => {
            let skeletonHtml = '';
            // Match skeleton count to itemsPerPage
            for (let i = 0; i < itemsPerPage; i++) {
                skeletonHtml += `
                    <div class="featured-item" style="pointer-events: none; opacity: 1;">
                        <div class="item-thumb img-skeleton" style="width: 60px; height: 80px; border-radius: 8px;"></div>
                        <div class="item-details" style="display: flex; flex-direction: column; justify-content: center; gap: 8px; width: 100%;">
                            <div class="img-skeleton" style="width: 80%; height: 14px; border-radius: 4px;"></div>
                            <div class="img-skeleton" style="width: 50%; height: 12px; border-radius: 4px;"></div>
                        </div>
                    </div>
                `;
            }
            featuredGrid.innerHTML = skeletonHtml;
            featuredGrid.classList.remove('fade-out');
            featuredGrid.classList.add('fade-in');

            try {
                const url = `/api/featured-content/${currentMode}?page=${page}&size=${itemsPerPage}`;
                const response = await fetch(url);
                if (!response.ok) throw new Error('Veri çekilemedi.');

                const data = await response.json();
                // Ensure we only render the requested amount, even if API over-returns
                const displayItems = data.content ? data.content.slice(0, itemsPerPage) : [];
                renderFeaturedGrid(displayItems, currentMode);
                setupPagination(data.totalPages);

            } catch (err) {
                console.error('Error fetching featured content:', err);
                featuredGrid.innerHTML = '<div class="error-message" style="grid-column: 1 / -1;">Veri yüklenemedi.</div>';
                paginationContainer.innerHTML = '';
                featuredGrid.classList.remove('fade-out');
                featuredGrid.classList.add('fade-in');
            } finally {
                isLoading = false;
            }
        }, 300);
    }

    function renderFeaturedGrid(data, type) {
        featuredGrid.innerHTML = '';

        if (data && data.length > 0) {
            data.forEach(item => {
                const el = document.createElement('div');
                el.className = 'featured-item';
                let specialBadgeHtml = '';
                if (type === 'tv') {
                    if (item.season === 1 && item.episode === 1) {
                        let badgeText = (item.seriesType === 'Program') ? 'Yeni Program' : 'Yeni Dizi';
                        specialBadgeHtml = `<span class="badge new-series-badge">${badgeText}</span>`;
                    } else if (item.season > 1 && item.episode === 1) {
                        specialBadgeHtml = '<span class="badge new-series-badge">Yeni Sezon</span>';
                    }
                }

                el.innerHTML = `
                    <div class="item-thumb img-skeleton">
                        <img src="${item.image}" alt="${item.title}">
                    </div>
                    ${item.finalStatus === 1 ? '<span class="badge final-badge">Final</span>' : ''}
                    ${item.finalStatus === 2 ? '<span class="badge final-badge season-final">Sezon Finali</span>' : ''}
                    ${specialBadgeHtml}
                    <div class="item-details">
                        <div class="item-header">
                            <div class="title-container">
                                <span class="title">${item.title}</span>
                            </div>
                        </div>
                        <div class="item-info">
                            ${type === 'movies'
                        ? '<span>Film</span>'
                        : `<span>Sezon ${item.season} Bölüm ${item.episode}</span>`
                    }
                            <span
                                class="country-flag ${item.country}"
                                title="${countryNames[item.country] || ''}"
                            ></span>
                            ${item.isNew ? '<span class="new-episode">• Yeni</span>' : ''}
                        </div>
                    </div>
                `;
                handleImageSkeleton(el.querySelector('img'));
                el.addEventListener('click', () => {
                    window.location.href = `/${item.id}`;
                });
                featuredGrid.appendChild(el);
            });

            setTimeout(() => {
                featuredGrid.classList.remove('fade-out');
                featuredGrid.classList.add('fade-in');
                setTimeout(() => {
                    featuredGrid.classList.remove('fade-in');
                }, 500);
            }, 100);
        } else {
            featuredGrid.innerHTML = '<div class="error-message" style="grid-column: 1 / -1;">İçerik bulunamadı.</div>';
        }
    }

    function setupPagination(totalPages) {
        paginationContainer.innerHTML = '';
        if (!totalPages || totalPages <= 1) return;

        const createButton = (page, isActive = false) => {
            const btn = document.createElement('button');
            btn.className = `pagination-btn ${isActive ? 'active' : ''}`;
            btn.innerText = page;
            btn.addEventListener('click', () => {
                if (page !== currentPage && !isLoading) loadPage(page);
            });
            return btn;
        };

        const createEllipsis = () => {
            const span = document.createElement('span');
            span.className = 'pagination-ellipsis';
            span.innerText = '...';
            span.style.color = '#fff';
            span.style.padding = '0 5px';
            return span;
        };

        const range = 2;
        let pagesToShow = [];

        pagesToShow.push(1);
        for (let i = currentPage - range; i <= currentPage + range; i++) {
            if (i > 1 && i < totalPages) {
                pagesToShow.push(i);
            }
        }
        if (totalPages > 1) {
            pagesToShow.push(totalPages);
        }

        pagesToShow = [...new Set(pagesToShow)].sort((a, b) => a - b);

        let lastPageAdded = 0;
        for (const page of pagesToShow) {
            if (lastPageAdded > 0 && page - lastPageAdded > 1) {
                paginationContainer.appendChild(createEllipsis());
            }
            paginationContainer.appendChild(createButton(page, page === currentPage));
            lastPageAdded = page;
        }
        window.addEventListener('resize', () => {
            const newLimit = window.innerWidth <= 1024 ? 10 : 20;
            if (newLimit !== itemsPerPage && !isLoading) {
                console.log(`[Featured] Resize detected, new itemsPerPage: ${newLimit}`);
                loadPage(1);
            }
        });
    }
}
