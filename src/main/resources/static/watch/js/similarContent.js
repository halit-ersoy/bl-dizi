import { handleImageSkeleton } from '../../elements/userLogged.js';

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
            container.innerHTML = '<p class=\"error-text\">Benzer içerikler yüklenemedi.</p>';
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
        container.innerHTML = '<p class=\"empty-text\">Henüz benzer bir içerik bulunmuyor.</p>';
        return;
    }

    container.innerHTML = '';
    items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'rec-card';
        const title = escapeHtml(item.Name || 'İçerik');
        const category = item.Category ? escapeHtml(item.Category.split(',')[0].trim()) : 'Detaylar';
        const url = `/${item.slug || item.ID}`;
        const posterUrl = `/media/image/${item.ID}?v=2`;

        card.innerHTML = `
            <a href="${url}" class="rec-link" title="${title} izle">
                <div class="rec-image img-skeleton">
                    <img src="${posterUrl}" alt="${title}" loading="lazy">
                </div>
                <div class="rec-title" title="${title}">${title}</div>
                <div class="rec-meta">${category}</div>
            </a>
        `;
        container.appendChild(card);

        const img = card.querySelector('img');
        if (img) {
            handleImageSkeleton(img);
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
