// calendar.js - Dedicated Calendar Page Logic
document.addEventListener('DOMContentLoaded', () => {
    initCalendarPage();
});

const defaultPosterSvg = "data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 450' fill='%23141418'%3E%3Crect width='100%25' height='100%25' fill='%23141418'/%3E%3Cg fill='%23252530' opacity='0.8'%3E%3Ccircle cx='150' cy='180' r='48'/%3E%3Cpath d='M80 300c0-38 31-70 70-70s70 32 70 70v15H80v-15z'/%3E%3C/g%3E%3Ctext x='50%25' y='360' text-anchor='middle' fill='%23555566' font-family='Montserrat,sans-serif' font-weight='700' font-size='16' letter-spacing='2'%3EWDI%3C/text%3E%3C/svg%3E";

const dayNames = {
    'monday': 'Pazartesi',
    'tuesday': 'Salı',
    'wednesday': 'Çarşamba',
    'thursday': 'Perşembe',
    'friday': 'Cuma',
    'saturday': 'Cumartesi',
    'sunday': 'Pazar'
};

const daysOrder = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
const daysShort = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

let rawCalendarData = null;
let processedCalendarData = {
    monday: [], tuesday: [], wednesday: [], thursday: [], friday: [], saturday: [], sunday: []
};
let currentActiveDay = getTodayDayName();

async function initCalendarPage() {
    setupSiteBranding();
    setupTodayBanner();

    // Immediately select and highlight today's tab on page load
    currentActiveDay = getTodayDayName();
    highlightActiveTab();
    renderCalendarContent();

    // Fetch and render live data
    await loadCalendarEvents();

    setupFilterControls();
    setupModalHandlers();
}

function setupSiteBranding() {
    const isBlDizi = window.location.hostname.includes('bldizi') || window.location.port === '8082';
    if (isBlDizi) {
        document.title = "Yayın Takvimi - BL Dizi";
        const logo = document.getElementById('site-logo');
        if (logo) logo.textContent = "BL DİZİ";
        const loaderBrand = document.getElementById('loader-brand');
        if (loaderBrand) loaderBrand.textContent = "BL";
    }
}

function setupTodayBanner() {
    const todayDateEl = document.getElementById('today-text-date');
    if (!todayDateEl) return;

    const now = new Date();
    const trLocaleDate = now.toLocaleDateString('tr-TR', {
        weekday: 'long',
        day: 'numeric',
        month: 'long'
    });

    todayDateEl.textContent = `Bugün: ${trLocaleDate}`;
}

async function loadCalendarEvents() {
    const loadingScreen = document.getElementById('loading-screen');
    const isBlDizi = window.location.hostname.includes('bldizi') || window.location.port === '8082';
    const platformParam = isBlDizi ? 'bldizi' : 'whodatidols';

    try {
        const response = await fetch(`/api/calendar?platform=${platformParam}`);
        if (!response.ok) throw new Error("Takvim verisi alınamadı");
        rawCalendarData = await response.json();

        processCalendarData(rawCalendarData);
        updateDayCounters();

        // Keep currentActiveDay as today or currently selected day
        if (!currentActiveDay) {
            currentActiveDay = getTodayDayName();
        }

        renderCalendarContent();
        highlightActiveTab();

    } catch (err) {
        console.error("Calendar Load Error:", err);
        const container = document.getElementById('calendarContent');
        if (container) {
            container.innerHTML = `
                <div class="calendar-empty-state">
                    <i class="fas fa-exclamation-circle"></i>
                    <h3>Takvim yüklenirken bir sorun oluştu</h3>
                    <p>Lütfen sayfayı yenileyiniz veya daha sonra tekrar deneyiniz.</p>
                </div>
            `;
        }
    } finally {
        if (loadingScreen) {
            loadingScreen.style.opacity = '0';
            setTimeout(() => { loadingScreen.style.display = 'none'; }, 300);
        }
    }
}

function processCalendarData(data) {
    processedCalendarData = {
        monday: [], tuesday: [], wednesday: [], thursday: [], friday: [], saturday: [], sunday: []
    };

    if (!data) return;

    Object.keys(data).forEach(dbDay => {
        const shows = data[dbDay] || [];
        shows.forEach(show => {
            const rawTime = show.time || '20:00';
            const [hours, minutes] = rawTime.split(':').map(Number);

            const dbDayIdx = daysShort.indexOf(dbDay.toLowerCase());
            if (dbDayIdx === -1) return;

            // Database time is assumed in Turkey Time (UTC+3)
            const trDate = new Date();
            const currentDay = trDate.getDay();
            const distance = dbDayIdx - currentDay;
            trDate.setDate(trDate.getDate() + distance);
            trDate.setHours((hours || 20) - 3, minutes || 0, 0, 0); // Convert to UTC

            const localDayName = daysShort[trDate.getDay()];
            const localHours = String(trDate.getHours()).padStart(2, '0');
            const localMinutes = String(trDate.getMinutes()).padStart(2, '0');
            const localTimeStr = `${localHours}:${localMinutes}`;

            if (processedCalendarData[localDayName]) {
                processedCalendarData[localDayName].push({
                    ...show,
                    time: localTimeStr,
                    rawTime: rawTime,
                    utcAirDate: trDate
                });
            }
        });
    });

    // Sort shows by local time within each day
    daysOrder.forEach(day => {
        processedCalendarData[day].sort((a, b) => (a.time || '').localeCompare(b.time || ''));
    });
}

function updateDayCounters() {
    daysOrder.forEach(day => {
        const count = (processedCalendarData[day] || []).length;
        const el = document.getElementById(`count-${day}`);
        if (el) {
            if (count > 0) {
                el.textContent = count;
                el.style.display = 'inline-flex';
            } else {
                el.textContent = '';
                el.style.display = 'none';
            }
        }
    });

    // Mark today's tab with indicator
    const todayDay = getTodayDayName();
    const todayTab = document.querySelector(`.day-tab[data-day="${todayDay}"]`);
    if (todayTab) {
        todayTab.classList.add('is-today');
        todayTab.setAttribute('title', 'Bugün');
    }
}

function getTodayDayName() {
    const todayIdx = new Date().getDay();
    return daysShort[todayIdx];
}

function setupFilterControls() {
    // Day Tabs
    const tabs = document.querySelectorAll('.day-tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            currentActiveDay = tab.dataset.day;
            highlightActiveTab();
            renderCalendarContent();
        });
    });
}

function highlightActiveTab() {
    const tabs = document.querySelectorAll('.day-tab');
    tabs.forEach(t => {
        if (t.dataset.day === currentActiveDay) {
            t.classList.add('active');
        } else {
            t.classList.remove('active');
        }
    });
}

function renderCalendarContent() {
    const container = document.getElementById('calendarContent');
    if (!container) return;

    container.innerHTML = '';

    const todayDayName = getTodayDayName();
    const dayKey = currentActiveDay || todayDayName;
    const dayName = dayNames[dayKey] || 'Seçilen Gün';
    const shows = processedCalendarData[dayKey] || [];

    if (shows.length === 0) {
        container.innerHTML = `
            <div class="calendar-empty-state">
                <i class="far fa-calendar-alt"></i>
                <h3>${dayName} günü için yayın akışı bulunmamaktadır</h3>
                <p>Bu gün için henüz yayınlanacak içerik planlanmamış.</p>
            </div>
        `;
        return;
    }

    const dayBlock = document.createElement('div');
    dayBlock.className = 'day-block';

    const isToday = dayKey === todayDayName;

    dayBlock.innerHTML = `
        <div class="day-block-header">
            <h2>
                <span>${dayName}</span>
                ${isToday ? '<span class="badge-today-pill">BUGÜN</span>' : ''}
            </h2>
            <span class="day-shows-count">${shows.length} Yapım</span>
        </div>
        <div class="shows-grid">
            ${shows.map(show => createShowCardHtml(show, isToday)).join('')}
        </div>
    `;

    container.appendChild(dayBlock);

    // Attach card event listeners
    attachCardListeners();
}

function createShowCardHtml(show, isToday) {
    const poster = show.posterUrl || defaultPosterSvg;
    const time = show.time || '20:00';
    const channel = show.channel || '';
    const episode = show.episode || 'Yeni Bölüm';
    const categories = show.category || '';
    const summary = show.summary || '';
    const slug = show.slug || '';
    const platform = show.platform || (show.isBl ? 'bldizi' : 'whodatidols');

    const platformBadge = platform === 'bldizi' 
        ? '<span class="badge-platform bldizi">BL DİZİ</span>' 
        : (platform === 'whodatidols' ? '<span class="badge-platform whodatidols">WDI</span>' : '');

    const hasSlug = Boolean(slug);
    const watchUrl = hasSlug ? `/${slug.replace(/^\/+/, '')}` : (show.seriesId ? `/series/${show.seriesId}` : null);

    const safeShowJson = encodeURIComponent(JSON.stringify(show));

    return `
        <div class="show-card" data-show="${safeShowJson}">
            <div class="card-poster-wrapper">
                <img src="${poster}" alt="${escapeHtml(show.title)}" class="card-poster-img" loading="lazy" onerror="this.onerror=null;this.src='${defaultPosterSvg}'">
                <div class="poster-overlay-gradient"></div>
                <div class="badge-time">
                    <i class="far fa-clock"></i>
                    <span>${time}</span>
                </div>
                ${channel ? `<div class="badge-channel">${escapeHtml(channel)}</div>` : ''}
                ${platformBadge}
            </div>
            <div class="card-body">
                <div class="card-episode-tag">${escapeHtml(episode)}</div>
                <h3 class="card-title" title="${escapeHtml(show.title)}">${escapeHtml(show.title)}</h3>
                ${categories ? `<div class="card-categories">${escapeHtml(categories)}</div>` : ''}
                ${summary ? `<div class="card-summary">${escapeHtml(summary)}</div>` : ''}
                
                <div class="card-actions">
                    ${watchUrl ? `
                        <a href="${watchUrl}" class="btn-card-action primary">
                            <i class="fas fa-play"></i> İzle
                        </a>
                    ` : ''}
                    <button type="button" class="btn-card-action secondary btn-open-modal" data-show="${safeShowJson}">
                        <i class="fas fa-info-circle"></i> Detaylar
                    </button>
                </div>
            </div>
        </div>
    `;
}

function attachCardListeners() {
    document.querySelectorAll('.btn-open-modal').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            try {
                const show = JSON.parse(decodeURIComponent(btn.dataset.show));
                openShowModal(show);
            } catch (err) {
                console.error("Modal data parse error:", err);
            }
        });
    });
}

function openShowModal(show) {
    const modal = document.getElementById('showDetailsModal');
    const content = document.getElementById('modalBodyContent');
    if (!modal || !content) return;

    const poster = show.posterUrl || defaultPosterSvg;
    const time = show.time || '20:00';
    const dayName = dayNames[(show.dayOfWeek || '').toLowerCase()] || '';
    const channel = show.channel || 'Bilinmiyor';
    const episode = show.episode || 'Yeni Bölüm';
    const categories = show.category || '';
    const summary = show.summary || 'Bu yapım için henüz detaylı özet girilmemiştir.';
    const watchUrl = show.slug ? `/${show.slug.replace(/^\/+/, '')}` : (show.seriesId ? `/series/${show.seriesId}` : null);

    content.innerHTML = `
        <div class="modal-show-detail">
            <div class="modal-poster-col">
                <img src="${poster}" alt="${escapeHtml(show.title)}" onerror="this.onerror=null;this.src='${defaultPosterSvg}'">
            </div>
            <div class="modal-info-col">
                <h2 class="modal-show-title">${escapeHtml(show.title)}</h2>
                <div class="modal-meta-row">
                    <span class="modal-meta-pill highlight"><i class="far fa-clock"></i> ${dayName} • ${time}</span>
                    <span class="modal-meta-pill"><i class="fas fa-tv"></i> ${escapeHtml(channel)}</span>
                    <span class="modal-meta-pill">${escapeHtml(episode)}</span>
                    ${categories ? `<span class="modal-meta-pill">${escapeHtml(categories)}</span>` : ''}
                </div>

                <div class="modal-summary-box">
                    <h4>Hakkında</h4>
                    <p>${escapeHtml(summary)}</p>
                </div>

                ${watchUrl ? `
                    <div style="margin-top: auto;">
                        <a href="${watchUrl}" class="btn-card-action primary" style="width: 100%; padding: 12px;">
                            <i class="fas fa-play"></i> Dizi Sayfasına Git / İzle
                        </a>
                    </div>
                ` : ''}
            </div>
        </div>
    `;

    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden';
}

function setupModalHandlers() {
    const modal = document.getElementById('showDetailsModal');
    const closeBtn = document.getElementById('closeModalBtn');
    const backdrop = document.getElementById('closeModalBackdrop');

    const closeModal = () => {
        if (modal) modal.style.display = 'none';
        document.body.style.overflow = '';
    };

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (backdrop) backdrop.addEventListener('click', closeModal);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal && modal.style.display === 'flex') {
            closeModal();
        }
    });
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
