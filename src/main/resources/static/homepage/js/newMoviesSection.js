import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initNewMoviesSection() {
    const viewAllBtn = document.querySelector('.new-movies .view-all');
    const newMoviesAllSection = document.getElementById('new-movies-all');
    const newMoviesAllGrid = document.querySelector('.new-movies-all-grid');
    const closeAllBtn = document.querySelector('.new-movies-all-section .close-all-btn');
    const newMoviesCarousel = document.getElementById('new-movies-carousel');

    if (!viewAllBtn || !newMoviesAllSection || !newMoviesAllGrid || !closeAllBtn || !newMoviesCarousel) {
        console.error('New Movies Section: Gerekli elementler bulunamadı.');
        return;
    }

    let isSearchActive = false;
    let currentSearchTerm = "";
    let currentPage = 1;
    const itemsPerPage = 18;

    async function fetchMovies(page = 1, size = 18, query = "") {
        try {
            let url = `/api/movies/recent?page=${page}&size=${size}`;
            if (query && query.trim() !== '') {
                url = `/api/movies/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`;
            }

            const response = await fetch(url);
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return await response.json(); // Beklenen format: { content: [...], totalPages: X }
        } catch (error) {
            console.error('Movies Section - Hata:', error);
            return { content: [], totalPages: 0 };
        }
    }

    function createNewMoviesItemHTML(item, index) {
        return `
      <a href="${item.videoUrl}" class="card new-movies-item" style="animation-delay: ${index * 0.02}s">
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
        newMoviesAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">Yükleniyor...</div>';

        const data = await fetchMovies(currentPage, itemsPerPage, isSearchActive ? currentSearchTerm : "");
        const movies = data.content || [];
        const totalPages = data.totalPages || 1;

        newMoviesAllGrid.innerHTML = '';
        if (movies.length === 0) {
            newMoviesAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">İçerik bulunamadı.</div>';
        } else {
            const fragment = document.createDocumentFragment();
            movies.forEach((movie, i) => {
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = createNewMoviesItemHTML(movie, i);
                const card = tempDiv.firstElementChild;
                handleImageSkeleton(card.querySelector('img'));
                fragment.appendChild(card);
            });
            newMoviesAllGrid.appendChild(fragment);
        }

        setupPagination(totalPages);

        // Scroll to top of section when changing pages
        if (newMoviesAllSection.classList.contains('hidden') === false) {
            const headerOffset = 100;
            const elementPosition = newMoviesAllSection.getBoundingClientRect().top;
            const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
            window.scrollTo({
                top: offsetPosition,
                behavior: "smooth"
            });
        }
    }

    function setupPagination(totalPages) {
        const existingPag = document.querySelector('.new-movies-pagination');
        if (existingPag) existingPag.remove();

        if (totalPages <= 1) return;

        const paginationContainer = document.createElement('div');
        paginationContainer.className = 'new-movies-pagination pagination-container';

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

        newMoviesAllSection.appendChild(paginationContainer);
    }

    const searchInput = document.getElementById('search-movies-all');
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
            }, 400); // 400ms debounce
        });
    }

    function populateCarousel(movies) {
        newMoviesCarousel.innerHTML = '';
        movies.forEach(movie => {
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = `
                <a href="${movie.videoUrl}" class="card">
                    <div class="card-image-container img-skeleton">
                        <img src="${movie.thumbnailUrl}" alt="${movie.title}">
                        <div class="play-icon"><i class="fas fa-play"></i></div>
                    </div>
                    <div class="card-content">
                        <p class="card-title">${movie.title}</p>
                        <p class="card-info">${movie.info}</p>
                    </div>
                </a>
            `;
            const card = tempDiv.firstElementChild;
            handleImageSkeleton(card.querySelector('img'));
            newMoviesCarousel.appendChild(card);
        });
    }

    // Başlangıçta ana sayfadaki carousel için ilk sayfayı (14 item) çek
    // Sadece görünür olduğunda yükle (Lazy Load)
    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(async entry => {
            if (entry.isIntersecting) {
                obs.disconnect(); // Sadece bir kere yükle
                const initialData = await fetchMovies(1, 14, "");
                if (initialData && initialData.content) {
                    populateCarousel(initialData.content);
                }
            }
        });
    }, { rootMargin: "200px" });

    if (newMoviesCarousel) {
        observer.observe(newMoviesCarousel);
    }

    viewAllBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        newMoviesAllSection.classList.remove('hidden');
        newMoviesAllSection.scrollIntoView({ behavior: 'smooth' });
        // Tıklandığında direkt ilk sayfayı yükle
        loadPage(1);
    });

    closeAllBtn.addEventListener('click', () => {
        newMoviesAllSection.classList.add('hidden');
        const existingPag = document.querySelector('.new-movies-pagination');
        if (existingPag) existingPag.remove();
        if (searchInput) {
            searchInput.value = '';
            isSearchActive = false;
            currentSearchTerm = '';
        }
    });
}
