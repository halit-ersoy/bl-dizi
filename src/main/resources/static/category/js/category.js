// category.js - BL Dizi

document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname;
    let contentType = 'series';
    let pageTitle;

    const statusContainer = document.getElementById('filter-status-container');
    const pageTitleEl = document.getElementById('page-title');
    const navMovies = document.getElementById('nav-movies');
    const navSeries = document.getElementById('nav-series');

    // Footer year
    const yrEl = document.getElementById('currentYear');
    if (yrEl) {
        yrEl.textContent = String(new Date().getFullYear());
    }

    // Determine context based on URL
    if (path.includes('/filmler')) {
        contentType = 'movie';
        pageTitle = 'Filmler';
        if (statusContainer) statusContainer.style.display = 'none';
        if (navMovies) navMovies.classList.add('active');
    } else {
        contentType = 'series';
        pageTitle = 'Diziler';
        if (statusContainer) statusContainer.style.display = '';
        if (navSeries) navSeries.classList.add('active');
    }

    if (pageTitleEl) pageTitleEl.textContent = pageTitle;
    document.title = `${pageTitle} - BL Dizi`;

    // State
    const state = {
        subType: 'all', // 'all', 'bl', 'gl'
        categoryId: '',
        status: '',
        year: '',
        country: '',
        sort: 'newest',
        page: 1,
        size: 18,
        isLoading: false,
        hasMore: true
    };

    // Elements
    const els = {
        filterCat: document.getElementById('filter-category'),
        filterStatus: document.getElementById('filter-status'),
        filterYear: document.getElementById('filter-year'),
        filterCountry: document.getElementById('filter-country'),
        filterSort: document.getElementById('filter-sort'),
        resetBtn: document.getElementById('reset-filters-btn'),
        emptyResetBtn: document.getElementById('empty-state-reset'),
        grid: document.getElementById('results-grid'),
        loading: document.getElementById('loading-more'),
        emptyState: document.getElementById('empty-state'),
        loadMoreBtn: document.getElementById('load-more-btn'),
        paginationControls: document.getElementById('pagination-controls'),
        countBadge: document.getElementById('results-count-badge'),
        subtypePills: document.querySelectorAll('.subtype-pill')
    };

    // Initialize
    async function init() {
        parseUrlParams();
        updateSubtypePillsUI();
        await fetchFilterOptions();
        await loadContent(true);
        setupEventListeners();

        // Optional: Calendar modal integration
        try {
            const calendarLink = document.getElementById('nav-calendar');
            if (calendarLink) {
                const calendarModule = await import('../../homepage/js/calendar.js');
                if (calendarModule && calendarModule.initCalendar) {
                    calendarModule.initCalendar();
                }
            }
        } catch {
            // Calendar is optional
        }
    }

    function parseUrlParams() {
        const params = new URLSearchParams(window.location.search);

        if (params.has('subType')) {
            const val = params.get('subType').toLowerCase();
            if (['all', 'bl', 'gl'].includes(val)) {
                state.subType = val;
            }
        }

        if (params.has('categoryId')) {
            state.categoryId = params.get('categoryId');
            if (els.filterCat) els.filterCat.value = state.categoryId;
        }

        if (params.has('country')) {
            state.country = params.get('country');
            if (els.filterCountry) els.filterCountry.value = state.country;
        }

        if (params.has('year')) {
            state.year = params.get('year');
            if (els.filterYear) els.filterYear.value = state.year;
        }

        if (params.has('status') && els.filterStatus) {
            state.status = params.get('status');
            els.filterStatus.value = state.status;
        }

        if (params.has('sort') && els.filterSort) {
            state.sort = params.get('sort');
            els.filterSort.value = state.sort;
        }
    }

    function updateSubtypePillsUI() {
        els.subtypePills.forEach(pill => {
            if (pill.dataset.subtype === state.subType) {
                pill.classList.add('active');
            } else {
                pill.classList.remove('active');
            }
        });
    }

    function syncUrlParams() {
        const params = new URLSearchParams();
        if (state.subType && state.subType !== 'all') params.set('subType', state.subType);
        if (state.categoryId) params.set('categoryId', state.categoryId);
        if (state.status !== '') params.set('status', state.status);
        if (state.year) params.set('year', state.year);
        if (state.country) params.set('country', state.country);
        if (state.sort && state.sort !== 'newest') params.set('sort', state.sort);

        const newRelativePathQuery = window.location.pathname + (params.toString() ? '?' + params.toString() : '');
        window.history.replaceState(null, '', newRelativePathQuery);
    }

    async function fetchFilterOptions() {
        try {
            const res = await fetch(`/api/filters/options?type=${contentType}`);
            if (!res.ok) {
                console.warn('Filtre seçenekleri alınamadı:', res.status);
                return;
            }
            const data = await res.json();

            // Populate Categories
            if (data.categories && els.filterCat) {
                data.categories.forEach(cat => {
                    const opt = document.createElement('option');
                    opt.value = String(cat.id);
                    opt.textContent = cat.name;
                    if (state.categoryId && String(state.categoryId) === String(cat.id)) {
                        opt.selected = true;
                    }
                    els.filterCat.appendChild(opt);
                });
            }

            // Populate Years
            if (data.years && els.filterYear) {
                data.years.forEach(year => {
                    const opt = document.createElement('option');
                    opt.value = String(year);
                    opt.textContent = String(year);
                    if (state.year && String(state.year) === String(year)) {
                        opt.selected = true;
                    }
                    els.filterYear.appendChild(opt);
                });
            }

            // Populate Countries
            if (data.countries && els.filterCountry) {
                data.countries.forEach(c => {
                    const opt = document.createElement('option');
                    opt.value = c.code;
                    opt.textContent = c.name;
                    if (state.country && state.country.toLowerCase() === c.code.toLowerCase()) {
                        opt.selected = true;
                    }
                    els.filterCountry.appendChild(opt);
                });
            }

        } catch (error) {
            console.error('Filter options error:', error);
        }
    }

    async function loadContent(reset = false) {
        if (state.isLoading) return;
        if (reset) {
            state.page = 1;
            state.hasMore = true;
            els.grid.innerHTML = '';
        }

        if (!state.hasMore) return;

        state.isLoading = true;
        els.loading.style.display = 'flex';
        els.emptyState.style.display = 'none';
        els.paginationControls.style.display = 'none';

        try {
            const endpoint = contentType === 'movie' ? '/api/movies/filter' : '/api/series/filter';
            const params = new URLSearchParams({
                page: String(state.page),
                size: String(state.size),
                sort: state.sort
            });

            if (state.subType) params.append('subType', state.subType);
            if (state.categoryId) params.append('categoryId', state.categoryId);
            if (state.status !== '') params.append('finalStatus', state.status);
            if (state.year) params.append('year', state.year);
            if (state.country) params.append('country', state.country);

            const url = `${endpoint}?${params.toString()}`;
            const res = await fetch(url);
            if (!res.ok) {
                console.warn('İçerik yüklenirken hata oluştu:', res.status);
                if (reset) els.emptyState.style.display = 'block';
                return;
            }

            const data = await res.json();

            renderCards(data.content || []);

            // Total element count badge
            if (els.countBadge) {
                const total = data.totalElements || 0;
                els.countBadge.textContent = `${total} İçerik`;
                els.countBadge.style.display = 'inline-block';
            }

            state.hasMore = state.page < data.totalPages;

            if (reset && (!data.content || data.content.length === 0)) {
                els.emptyState.style.display = 'block';
            } else if (state.hasMore) {
                els.paginationControls.style.display = 'flex';
            }

            state.page++;
        } catch (error) {
            console.error('Error loading content:', error);
            if (reset) els.emptyState.style.display = 'block';
        } finally {
            state.isLoading = false;
            els.loading.style.display = 'none';
        }
    }

    function renderCards(items) {
        items.forEach(item => {
            const card = document.createElement('a');
            card.href = item.videoUrl || '#';
            card.className = 'media-card';

            const isGl = (item.info && item.info.toLowerCase().includes('gl')) ||
                         (item.title && item.title.toLowerCase().includes('gl'));

            const tagHtml = isGl 
                ? `<span class="card-type-tag tag-gl">GL</span>`
                : `<span class="card-type-tag tag-bl">BL</span>`;

            let badgesHTML = '';
            if (item.info) {
                badgesHTML = `<div class="card-info">${escapeHtml(item.info)}</div>`;
            }

            card.innerHTML = `
                ${tagHtml}
                <img src="${escapeHtml(item.thumbnailUrl)}" alt="${escapeHtml(item.title)}" loading="lazy">
                <div class="card-overlay">
                    ${badgesHTML}
                    <div class="card-title">${escapeHtml(item.title)}</div>
                </div>
            `;

            const img = card.querySelector('img');
            if (img) {
                img.addEventListener('error', () => {
                    card.classList.add('no-image');
                    img.style.display = 'none';
                });
            }

            els.grid.appendChild(card);
        });
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function setupEventListeners() {
        // Subtype pills (Tümü, BL, GL)
        els.subtypePills.forEach(pill => {
            pill.addEventListener('click', () => {
                const selected = pill.dataset.subtype;
                if (state.subType === selected) return;
                state.subType = selected;
                updateSubtypePillsUI();
                syncUrlParams();
                loadContent(true);
            });
        });

        const handleFilterChange = () => {
            state.categoryId = els.filterCat ? els.filterCat.value : '';
            state.status = els.filterStatus ? els.filterStatus.value : '';
            state.year = els.filterYear ? els.filterYear.value : '';
            state.country = els.filterCountry ? els.filterCountry.value : '';
            state.sort = els.filterSort ? els.filterSort.value : 'newest';
            syncUrlParams();
            loadContent(true);
        };

        if (els.filterCat) els.filterCat.addEventListener('change', handleFilterChange);
        if (els.filterStatus) els.filterStatus.addEventListener('change', handleFilterChange);
        if (els.filterYear) els.filterYear.addEventListener('change', handleFilterChange);
        if (els.filterCountry) els.filterCountry.addEventListener('change', handleFilterChange);
        if (els.filterSort) els.filterSort.addEventListener('change', handleFilterChange);

        const handleReset = () => {
            state.subType = 'all';
            updateSubtypePillsUI();

            if (els.filterCat) els.filterCat.value = '';
            if (els.filterStatus) els.filterStatus.value = '';
            if (els.filterYear) els.filterYear.value = '';
            if (els.filterCountry) els.filterCountry.value = '';
            if (els.filterSort) els.filterSort.value = 'newest';

            handleFilterChange();
        };

        if (els.resetBtn) els.resetBtn.addEventListener('click', handleReset);
        if (els.emptyResetBtn) els.emptyResetBtn.addEventListener('click', handleReset);

        if (els.loadMoreBtn) {
            els.loadMoreBtn.addEventListener('click', () => {
                loadContent(false);
            });
        }
    }

    // Start
    init();
});
