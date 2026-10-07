import { handleImageSkeleton } from '../../elements/userLogged.js';

export function initNewProgramsSection() {
    const viewAllBtn = document.querySelector('.new-programs .view-all');
    const newProgramsAllSection = document.getElementById('new-programs-all');
    const newProgramsAllGrid = document.querySelector('.new-programs-all-grid');
    const closeAllBtn = document.querySelector('#new-programs-all .close-all-btn');
    const newProgramsCarousel = document.querySelector('.new-programs .carousel');

    if (!viewAllBtn || !newProgramsAllSection || !newProgramsAllGrid || !closeAllBtn || !newProgramsCarousel) {
        console.error('New Programs Section: Gerekli elementler bulunamadı.');
        return;
    }

    let isSearchActive = false;
    let currentSearchTerm = "";
    let currentPage = 1;
    const itemsPerPage = 18;

    async function fetchPrograms(page = 1, size = 18, query = "") {
        try {
            let url = `/api/soapoperas/recent?type=Program&page=${page}&size=${size}`;
            if (query && query.trim() !== '') {
                url = `/api/soapoperas/search?type=Program&query=${encodeURIComponent(query)}&page=${page}&size=${size}`;
            }

            const response = await fetch(url);
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return await response.json();
        } catch (error) {
            console.error('Programs Section - Hata:', error);
            return { content: [], totalPages: 0 };
        }
    }

    function createProgramItemHTML(item, index) {
        return `
      <a href="${item.videoUrl}" class="card new-programs-item" style="animation-delay: ${index * 0.02}s">
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
        newProgramsAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">Yükleniyor...</div>';

        const data = await fetchPrograms(currentPage, itemsPerPage, isSearchActive ? currentSearchTerm : "");
        const programs = data.content || [];
        const totalPages = data.totalPages || 1;

        newProgramsAllGrid.innerHTML = '';
        if (programs.length === 0) {
            newProgramsAllGrid.innerHTML = '<div style="text-align: center; color: white; width: 100%;">İçerik bulunamadı.</div>';
        } else {
            const fragment = document.createDocumentFragment();
            programs.forEach((program, i) => {
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = createProgramItemHTML(program, i);
                const card = tempDiv.firstElementChild;
                handleImageSkeleton(card.querySelector('img'));
                fragment.appendChild(card);
            });
            newProgramsAllGrid.appendChild(fragment);
        }

        setupPagination(totalPages);

        if (newProgramsAllSection.classList.contains('hidden') === false) {
            const headerOffset = 100;
            const elementPosition = newProgramsAllSection.getBoundingClientRect().top;
            const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
            window.scrollTo({
                top: offsetPosition,
                behavior: "smooth"
            });
        }
    }

    function setupPagination(totalPages) {
        const existingPag = document.querySelector('.new-programs-pagination');
        if (existingPag) existingPag.remove();

        if (totalPages <= 1) return;

        const paginationContainer = document.createElement('div');
        paginationContainer.className = 'new-programs-pagination pagination-container';

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

        newProgramsAllSection.appendChild(paginationContainer);
    }

    const searchInput = document.getElementById('search-programs-all');
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

    function populateCarousel(programs) {
        newProgramsCarousel.innerHTML = '';
        programs.forEach(program => {
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = `
                <a href="${program.videoUrl}" class="card">
                    <div class="card-image-container img-skeleton">
                        <img src="${program.thumbnailUrl}" alt="${program.title}">
                        <div class="play-icon"><i class="fas fa-play"></i></div>
                    </div>
                    <div class="card-content">
                        <p class="card-title">${program.title}</p>
                        <p class="card-info">${program.info}</p>
                    </div>
                </a>
            `;
            const card = tempDiv.firstElementChild;
            handleImageSkeleton(card.querySelector('img'));
            newProgramsCarousel.appendChild(card);
        });
    }

    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(async entry => {
            if (entry.isIntersecting) {
                obs.disconnect();
                const initialData = await fetchPrograms(1, 14, "");
                if (initialData && initialData.content) {
                    populateCarousel(initialData.content);
                }
            }
        });
    }, { rootMargin: "200px" });

    if (newProgramsCarousel) {
        observer.observe(newProgramsCarousel);
    }

    viewAllBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        newProgramsAllSection.classList.remove('hidden');
        newProgramsAllSection.scrollIntoView({ behavior: 'smooth' });
        loadPage(1);
    });

    closeAllBtn.addEventListener('click', () => {
        newProgramsAllSection.classList.add('hidden');
        const existingPag = document.querySelector('.new-programs-pagination');
        if (existingPag) existingPag.remove();
        if (searchInput) {
            searchInput.value = '';
            isSearchActive = false;
            currentSearchTerm = '';
        }
    });

    const scrollLeftBtn = document.querySelector('.new-programs .scroll-btn.left');
    const scrollRightBtn = document.querySelector('.new-programs .scroll-btn.right');
    const carouselWrapper = document.querySelector('.new-programs .carousel-wrapper');
    const carousel = document.querySelector('.new-programs .carousel');

    if (scrollLeftBtn && scrollRightBtn && carouselWrapper) {
        const itemWidth = 240; 
        const gap = parseInt(window.getComputedStyle(carousel).gap) || 20;
        const scrollAmount = itemWidth + gap;

        function updateScrollButtons() {
            if (carouselWrapper.scrollLeft <= 5) {
                scrollLeftBtn.classList.add('hidden');
            } else {
                scrollLeftBtn.classList.remove('hidden');
            }

            if (carouselWrapper.scrollLeft + carouselWrapper.clientWidth >= carouselWrapper.scrollWidth - 5) {
                scrollRightBtn.classList.add('hidden');
            } else {
                scrollRightBtn.classList.remove('hidden');
            }
        }

        carouselWrapper.addEventListener('scroll', updateScrollButtons);
        updateScrollButtons();

        scrollLeftBtn.addEventListener('click', () => {
            carouselWrapper.scrollBy({ left: -scrollAmount, behavior: 'smooth' });
        });

        scrollRightBtn.addEventListener('click', () => {
            carouselWrapper.scrollBy({ left: scrollAmount, behavior: 'smooth' });
        });
    }
}
