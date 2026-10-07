export function initSimilarContent(videoId) {
    const container = document.getElementById('recommendationCards');
    const prevBtn = document.getElementById('recPrevBtn');
    const nextBtn = document.getElementById('recNextBtn');

    if (!container) return;

    fetch(`/api/video/similar?id=${videoId}`)
        .then(response => response.json())
        .then(data => {
            renderRecommendations(data, container);
            setupCarouselNavigation(container, prevBtn, nextBtn);
        })
        .catch(error => {
            console.error('Error fetching similar content:', error);
            container.innerHTML = '<p class="error-text">Benzer içerikler yüklenemedi.</p>';
        });
}

function setupCarouselNavigation(container, prevBtn, nextBtn) {
    if (!prevBtn || !nextBtn) return;

    const updateArrows = () => {
        if (container.scrollLeft <= 0) {
            prevBtn.classList.add('disabled');
        } else {
            prevBtn.classList.remove('disabled');
        }

        if (container.scrollLeft + container.clientWidth >= container.scrollWidth - 10) {
            nextBtn.classList.add('disabled');
        } else {
            nextBtn.classList.remove('disabled');
        }
    };

    prevBtn.addEventListener('click', () => {
        const scrollAmount = container.clientWidth * 0.8;
        container.scrollBy({ left: -scrollAmount, behavior: 'smooth' });
    });

    nextBtn.addEventListener('click', () => {
        const scrollAmount = container.clientWidth * 0.8;
        container.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    });

    container.addEventListener('scroll', updateArrows);
    window.addEventListener('resize', updateArrows);

    // Initial check
    updateArrows();
}

function renderRecommendations(items, container) {
    if (!items || items.length === 0) {
        container.innerHTML = '<p class="empty-text">Henüz benzer bir içerik bulunmuyor.</p>';
        return;
    }

    const defaultPosterSvg = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='180' height='320' viewBox='0 0 180 320' fill='%231a1a20'><rect width='100%' height='100%' fill='%231a1a20'/><circle cx='90' cy='130' r='24' fill='%2333333e'/><rect x='50' y='170' width='80' height='40' rx='10' fill='%2333333e'/></svg>";

    container.innerHTML = '';
    items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'rec-card';
        const title = escapeHtml(item.Name || 'İçerik');
        const category = item.Category ? escapeHtml(item.Category.split(',')[0].trim()) : 'Detaylar';
        const url = `/${item.slug || item.ID}`;
        const posterUrl = `/media/image/${item.ID}`;

        card.innerHTML = `
            <a href="${url}" class="rec-link" title="${title} izle">
                <div class="rec-image img-skeleton">
                    <img src="${posterUrl}" alt="${title}" loading="lazy" onerror="this.onerror=null; this.src='${defaultPosterSvg}';">
                </div>
                <div class="rec-title" title="${title}">${title}</div>
                <div class="rec-meta">${category}</div>
            </a>
        `;
        container.appendChild(card);

        const img = card.querySelector('img');
        if (img) {
            img.onload = () => card.querySelector('.rec-image')?.classList.remove('img-skeleton');
            if (img.complete) {
                card.querySelector('.rec-image')?.classList.remove('img-skeleton');
            }
        }
    });
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
