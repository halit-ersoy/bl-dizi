import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initWeeklyBest() {
    const grid = document.querySelector('.weekly-best-grid');
    const tvBtn = document.getElementById('weekly-tv-toggle');
    const movieBtn = document.getElementById('weekly-movies-toggle');

    if (!grid || !tvBtn || !movieBtn) return;

    let cache = { tv: null, movies: null };
    let currentMode = 'tv';

    // Load initial
    loadData('tv');

    tvBtn.addEventListener('click', () => {
        if (currentMode !== 'tv') switchMode('tv');
    });

    movieBtn.addEventListener('click', () => {
        if (currentMode !== 'movies') switchMode('movies');
    });

    function switchMode(mode) {
        currentMode = mode;
        tvBtn.classList.toggle('active', mode === 'tv');
        movieBtn.classList.toggle('active', mode === 'movies');

        grid.classList.add('fade-out');
        setTimeout(() => {
            loadData(mode);
        }, 200);
    }

    function loadData(mode) {
        if (cache[mode]) {
            render(cache[mode]);
            grid.classList.remove('fade-out');
            return;
        }

        fetch(`/api/weekly-best/${mode}`)
            .then(res => res.json())
            .then(data => {
                cache[mode] = data;
                render(data);
                grid.classList.remove('fade-out');
            })
            .catch(err => {
                console.error('Weekly Best error:', err);
                grid.classList.remove('fade-out');
            });
    }

    function render(items) {
        grid.innerHTML = '';
        if (!items || items.length === 0) {
            grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 40px;">İçerik bulunamadı.</div>';
            return;
        }

        items.forEach((item, index) => {
            const card = document.createElement('a');
            card.href = item.videoUrl || '#';
            card.className = 'card';

            const countryTag = item.country ? item.country.toUpperCase() : 'BL';

            card.innerHTML = `
                <div class="card-image-container img-skeleton">
                    <img src="${item.thumbnailUrl}" alt="${item.title}" loading="lazy">
                    <div class="card-play-overlay">
                        <div class="card-play-icon"><i class="fas fa-play"></i></div>
                    </div>
                    <span class="card-country-badge">${countryTag}</span>
                    <span class="weekly-rank-badge">${index + 1}</span>
                </div>
                <div class="card-content">
                    <div class="card-title">${item.title}</div>
                    <div class="card-info">${item.info || 'BL Dizi'}</div>
                </div>
            `;
            const img = card.querySelector('img');
            handleImageSkeleton(img);
            grid.appendChild(card);
        });
    }
}
