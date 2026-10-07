import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initNewSoapOperasSection() {
    const viewAllBtn = document.querySelector('.new-soap-operas .view-all');
    const newSoapOperasAllSection = document.getElementById('new-soap-operas-all');
    const newSoapOperasAllGrid = document.querySelector('.new-soap-operas-all-grid');
    const closeAllBtn = document.querySelector('#new-soap-operas-all .close-all-btn');
    const newSoapOperasCarousel = document.querySelector('.new-soap-operas .carousel');

    if (!viewAllBtn || !newSoapOperasAllSection || !newSoapOperasAllGrid || !closeAllBtn || !newSoapOperasCarousel) {
        console.error('New Soap Operas Section: Gerekli elementler bulunamadı.');
        return;
    }

    let isSearchActive = false;
    let currentSearchTerm = "";
    let currentPage = 1;
    const itemsPerPage = 18;

    async function fetchSoapOperas(page = 1, size = 18, query = "") {
        try {
            let url = `/api/soapoperas/recent?page=${page}&size=${size}`;
            if (query && query.trim() !== '') {
                url = `/api/soapoperas/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`;
            }

            const response = await fetch(url);
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return await response.json(); // { content: [...], totalPages: X }
        } catch (error) {
            console.error('Soap Operas Section - Hata:', error);
            return { content: [], totalPages: 0 };
        }
    }

    function createSoapOperaItemHTML(item, index) {
        return `
      <a href="${item.videoUrl}" class="card new-soap-operas-item" style="animation-delay: ${index * 0.02}s">
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

    async function loadPage(page) {
        currentPage = page;
        newSoapOperasAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">Yükleniyor...</div>';

        const data = await fetchSoapOperas(currentPage, itemsPerPage, isSearchActive ? currentSearchTerm : "");
        const soapOperas = data.content || [];
        const totalPages = data.totalPages || 1;

        newSoapOperasAllGrid.innerHTML = '';
        if (soapOperas.length === 0) {
            newSoapOperasAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">İçerik bulunamadı.</div>';
        } else {
            const fragment = document.createDocumentFragment();
            soapOperas.forEach((soapOpera, i) => {
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = createSoapOperaItemHTML(soapOpera, i);
                const card = tempDiv.firstElementChild;
                handleImageSkeleton(card.querySelector('img'));
                fragment.appendChild(card);
            });
            newSoapOperasAllGrid.appendChild(fragment);
        }

        setupPagination(totalPages);

        if (newSoapOperasAllSection.classList.contains('hidden') === false) {
            const headerOffset = 100;
            const elementPosition = newSoapOperasAllSection.getBoundingClientRect().top;
            const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
            window.scrollTo({
                top: offsetPosition,
                behavior: "smooth"
            });
        }
    }

    function setupPagination(totalPages) {
        const existingPag = document.querySelector('.new-soap-operas-pagination');
        if (existingPag) existingPag.remove();

        if (totalPages <= 1) return;

        const paginationContainer = document.createElement('div');
        paginationContainer.className = 'new-soap-operas-pagination pagination-container';

        const createButton = (page, isActive = false) => {
            const btn = document.createElement('button');
            btn.className = `pagination-btn ${isActive ? 'active' : ''}`;
            btn.innerText = page;
            btn.addEventListener('click', () => {
                if (page !== currentPage) loadPage(page);
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

        newSoapOperasAllSection.appendChild(paginationContainer);
    }

    const searchInput = document.getElementById('search-soap-operas-all');
    let searchTimeout = null;

    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const term = e.target.value.trim();
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                if (term === '') {
                    isSearchActive = false;
                    currentSearchTerm = "";
                } else {
                    isSearchActive = true;
                    currentSearchTerm = term;
                }
                loadPage(1);
            }, 400);
        });
    }

    function populateCarousel(soapOperas) {
        newSoapOperasCarousel.innerHTML = '';
        soapOperas.forEach(soapOpera => {
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = `
                <a href="${soapOpera.videoUrl}" class="card">
                    <div class="card-image-container img-skeleton">
                        <img src="${soapOpera.thumbnailUrl}" alt="${soapOpera.title}">
                        <div class="play-icon"><i class="fas fa-play"></i></div>
                    </div>
                    <div class="card-content">
                        <p class="card-title">${soapOpera.title}</p>
                        <p class="card-info">${soapOpera.info}</p>
                    </div>
                </a>
            `;
            const card = tempDiv.firstElementChild;
            handleImageSkeleton(card.querySelector('img'));
            newSoapOperasCarousel.appendChild(card);
        });
    }

    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(async entry => {
            if (entry.isIntersecting) {
                obs.disconnect();
                const initialData = await fetchSoapOperas(1, 14, "");
                if (initialData && initialData.content) {
                    populateCarousel(initialData.content);
                }
            }
        });
    }, { rootMargin: "200px" });

    if (newSoapOperasCarousel) {
        observer.observe(newSoapOperasCarousel);
    }

    viewAllBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        newSoapOperasAllSection.classList.remove('hidden');
        newSoapOperasAllSection.scrollIntoView({ behavior: 'smooth' });
        loadPage(1);
    });

    closeAllBtn.addEventListener('click', () => {
        newSoapOperasAllSection.classList.add('hidden');
        const existingPag = document.querySelector('.new-soap-operas-pagination');
        if (existingPag) existingPag.remove();
        if (searchInput) {
            searchInput.value = '';
            isSearchActive = false;
            currentSearchTerm = '';
        }
    });
}
