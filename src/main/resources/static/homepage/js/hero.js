export function initHero() {
    const heroSection = document.querySelector('.hero');
    const heroContainer = document.querySelector('.hero-videos');
    const indicatorsContainer = document.querySelector('.hero-indicators');
    const prevBtn = document.querySelector('.prev-hero');
    const nextBtn = document.querySelector('.next-hero');

    if (!heroContainer) return;

    let currentIndex = 0;
    let heroList = [];
    let autoPlayTimer = null;

    fetch('/api/hero/videos')
        .then(res => res.json())
        .then(data => {
            heroList = data;
            if (!heroList || heroList.length === 0) return;
            renderHero();
            startAutoPlay();
        })
        .catch(err => console.error('Hero fetch error:', err));

    function renderHero() {
        heroContainer.innerHTML = '';
        if (indicatorsContainer) indicatorsContainer.innerHTML = '';

        heroList.forEach((item, idx) => {
            // Slide
            const slide = document.createElement('div');
            slide.className = `hero-video-container ${idx === 0 ? 'active' : ''}`;
            slide.dataset.index = idx;

            const categoryText = item.category || 'BL Dizi';
            const countryText = item.country ? item.country.toUpperCase() : 'TH';
            const yearText = item.releaseYear ? `<span><i class="far fa-calendar-alt"></i> ${item.releaseYear}</span>` : '';

            slide.innerHTML = `
                <img class="hero-bg" src="${item.thumbnailUrl}" alt="${item.title}" onerror="this.style.display='none'">
                <div class="hero-overlay"></div>
                <div class="hero-content">
                    <div class="hero-badge"><i class="fas fa-heart"></i> ÖNE ÇIKAN BL</div>
                    <h1 class="hero-title">${item.title}</h1>
                    <div class="hero-meta">
                        <span class="hero-category">${categoryText}</span>
                        <span><i class="fas fa-globe-asia"></i> ${countryText}</span>
                        ${yearText}
                    </div>
                    <p class="hero-summary">${item.summary || 'En sevilen BL dizisi bldizi.com farkıyla sizlerle.'}</p>
                    <div class="hero-actions">
                        <a href="${item.videoUrl}" class="hero-play-btn">
                            <i class="fas fa-play"></i> HEMEN İZLE
                        </a>
                    </div>
                </div>
            `;
            heroContainer.appendChild(slide);

            // Indicator
            if (indicatorsContainer) {
                const ind = document.createElement('span');
                ind.className = `hero-indicator ${idx === 0 ? 'active' : ''}`;
                ind.dataset.index = idx;
                ind.addEventListener('click', () => goToSlide(idx));
                indicatorsContainer.appendChild(ind);
            }
        });
    }

    function goToSlide(index) {
        if (index < 0) index = heroList.length - 1;
        if (index >= heroList.length) index = 0;
        currentIndex = index;

        const slides = heroContainer.querySelectorAll('.hero-video-container');
        slides.forEach(s => s.classList.remove('active'));
        if (slides[currentIndex]) slides[currentIndex].classList.add('active');

        if (indicatorsContainer) {
            const inds = indicatorsContainer.querySelectorAll('.hero-indicator');
            inds.forEach(i => i.classList.remove('active'));
            if (inds[currentIndex]) inds[currentIndex].classList.add('active');
        }

        resetAutoPlay();
    }

    function nextSlide() {
        goToSlide(currentIndex + 1);
    }

    function prevSlide() {
        goToSlide(currentIndex - 1);
    }

    if (nextBtn) nextBtn.addEventListener('click', nextSlide);
    if (prevBtn) prevBtn.addEventListener('click', prevSlide);

    function startAutoPlay() {
        autoPlayTimer = setInterval(nextSlide, 7000);
    }

    function resetAutoPlay() {
        if (autoPlayTimer) clearInterval(autoPlayTimer);
        startAutoPlay();
    }
}
