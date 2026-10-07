export function initSearch() {
    const searchInput = document.getElementById('search-input');
    const dropdown = document.getElementById('search-results');
    if (!searchInput || !dropdown) return;

    let debounceTimer = null;

    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.trim();
        if (debounceTimer) clearTimeout(debounceTimer);

        if (query.length < 2) {
            dropdown.classList.remove('active');
            dropdown.innerHTML = '';
            return;
        }

        debounceTimer = setTimeout(() => {
            fetch(`/api/search?q=${encodeURIComponent(query)}`)
                .then(res => res.json())
                .then(results => {
                    renderResults(results);
                })
                .catch(err => console.error('Search error:', err));
        }, 250);
    });

    function renderResults(results) {
        if (!results || results.length === 0) {
            dropdown.innerHTML = '<div style="padding: 12px; text-align: center; color: var(--text-muted); font-size: 0.8rem;">Sonuç bulunamadı</div>';
            dropdown.classList.add('active');
            return;
        }

        dropdown.innerHTML = '';
        results.forEach(item => {
            const row = document.createElement('a');
            const target = item.slug ? `/${item.slug}` : `/${item.id}`;
            row.href = target;
            row.className = 'search-item';

            row.innerHTML = `
                <div class="search-item-thumb-box">
                    <img src="${item.thumbnailUrl}" class="search-item-thumb" alt="${item.name}" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="search-item-icon-fallback" style="display:none;"><i class="fas fa-film"></i></div>
                </div>
                <div class="search-item-details">
                    <span class="search-item-title">${item.name}</span>
                    <span class="search-item-cat">${item.category || (item.type === 'movie' ? 'Film' : 'Dizi')}</span>
                </div>
            `;
            dropdown.appendChild(row);
        });

        dropdown.classList.add('active');
    }

    // Close on click outside
    document.addEventListener('click', (e) => {
        if (!searchInput.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.classList.remove('active');
        }
    });
}
