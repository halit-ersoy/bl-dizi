import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initNewSeriesSection() {
    const viewAllBtn = document.querySelector('.new-series .view-all');
    const newSeriesAllSection = document.getElementById('new-series-all');
    const newSeriesAllGrid = document.querySelector('.new-series-all-grid');
    const loadMoreBtn = document.getElementById('load-more-new-series');
    const closeAllBtn = document.querySelector('#new-series-all .close-all-btn');
    const newSeriesCarousel = document.querySelector('.new-series .carousel');

    if (!viewAllBtn || !newSeriesAllSection || !newSeriesAllGrid || !loadMoreBtn || !closeAllBtn || !newSeriesCarousel) {
        console.error('New Series Section: Gerekli elementler bulunamadı.');
        return;
    }

    let currentPage = 1;
    let currentTotalPages = 1;
    const itemsPerPage = 20;

    async function fetchSeries(page = 1, size = 20) {
        try {
            const response = await fetch(`/api/series/recent?page=${page}&size=${size}`);
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return await response.json(); // Beklenen format: { content: [...], totalPages: X }
        } catch (error) {
            console.error('Series Section - Hata:', error);
            return { content: [], totalPages: 0 };
        }
    }

    function createSeriesItemHTML(item, index) {
        return `
      <a href="${item.videoUrl}" class="card new-series-item" style="animation-delay: ${index * 0.05}s">
        <div class="card-image-container img-skeleton">
          <img src="${item.thumbnailUrl}" alt="${item.title}">
          <div class="play-icon"><i class="fas fa-play"></i></div>
        </div>
        <div class="card-content">
          <p class="card-title">${item.title}</p>
          <p class="card-info">${item.info}</p>
        </div>
      </a>
    `;
    }

    async function loadMoreItems() {
        const data = await fetchSeries(currentPage, itemsPerPage);
        const seriesItems = data.content || [];
        currentTotalPages = data.totalPages || 1;

        const fragment = document.createDocumentFragment();
        seriesItems.forEach((item, index) => {
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = createSeriesItemHTML(item, index);
            const card = tempDiv.firstElementChild;
            handleImageSkeleton(card.querySelector('img'));
            fragment.appendChild(card);
        });

        newSeriesAllGrid.appendChild(fragment);

        if (currentPage >= currentTotalPages) {
            loadMoreBtn.classList.add('hidden');
        } else {
            loadMoreBtn.classList.remove('hidden');
        }
        currentPage++;
    }

    function populateCarousel(seriesList) {
        newSeriesCarousel.innerHTML = '';
        seriesList.forEach(seriesItem => {
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = `
                <a href="${seriesItem.videoUrl}" class="card">
                    <div class="card-image-container img-skeleton">
                        <img src="${seriesItem.thumbnailUrl}" alt="${seriesItem.title}">
                        <div class="play-icon"><i class="fas fa-play"></i></div>
                    </div>
                    <div class="card-content">
                        <p class="card-title">${seriesItem.title}</p>
                        <p class="card-info">${seriesItem.info}</p>
                    </div>
                </a>
            `;
            const card = tempDiv.firstElementChild;
            handleImageSkeleton(card.querySelector('img'));
            newSeriesCarousel.appendChild(card);
        });
    }

    // Başlangıçta carousel dolduruluyor
    // Sadece görünür olduğunda yükle (Lazy Load)
    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(async entry => {
            if (entry.isIntersecting) {
                obs.disconnect(); // Sadece bir kere yükle
                const initialData = await fetchSeries(1, 14);
                if (initialData && initialData.content) {
                    populateCarousel(initialData.content);
                }
            }
        });
    }, { rootMargin: "200px" });

    if (newSeriesCarousel) {
        observer.observe(newSeriesCarousel);
    }

    viewAllBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        newSeriesAllGrid.innerHTML = '';
        currentPage = 1;
        newSeriesAllSection.classList.remove('hidden');
        newSeriesAllSection.scrollIntoView({ behavior: 'smooth' });

        // İlk sayfayı yükle
        await loadMoreItems();
    });

    loadMoreBtn.addEventListener('click', async () => {
        await loadMoreItems();
    });

    closeAllBtn.addEventListener('click', () => {
        newSeriesAllSection.classList.add('hidden');
    });
}
