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

    fetch('/api/hero/videos?platform=bldizi')
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

            const heroId = item.ID || item.id;
            const refId = item.ReferenceId || item.referenceId || heroId;
            const heroName = item.name || item.title || 'İsimsiz İçerik';
            const heroSlug = item.slug || item.Slug || '';
            const heroSummary = item._content || item.summary || 'En sevilen BL dizisi bldizi.com farkıyla sizlerle.';
            const targetUrl = heroSlug ? (heroSlug.startsWith('/') ? heroSlug : '/' + heroSlug) : (refId ? '/' + refId : '#');

            const isImageValue = item.isImage !== undefined ? item.isImage :
                                 (item.IsImage !== undefined ? item.IsImage : true);
            const isImage = isImageValue === true || isImageValue === 1 || isImageValue === "1" || isImageValue === "true";

            const mediaHtml = isImage ? `
                <img class="hero-bg" src="/media/image/${heroId}?v=2" alt="${heroName}" onerror="if (this.src.indexOf('${refId}') === -1) this.src='/media/image/${refId}?v=2';">
            ` : `
                <video class="hero-bg-video" data-hero-id="${heroId}" data-index="${idx}" muted playsinline loop>
                    <img class="hero-bg" src="/media/image/${heroId}?v=2" alt="${heroName}" onerror="if (this.src.indexOf('${refId}') === -1) this.src='/media/image/${refId}?v=2';">
                </video>
            `;

            slide.innerHTML = `
                ${mediaHtml}
                <div class="hero-overlay"></div>
                <div class="hero-content">
                    <div class="hero-badge"><i class="fas fa-heart"></i> ÖNE ÇIKAN BL</div>
                    <h1 class="hero-title" style="cursor: pointer;" onclick="window.location.href='${targetUrl}'">${heroName}</h1>
                    <div class="hero-meta">
                        <span class="hero-category">${categoryText}</span>
                        <span><i class="fas fa-globe-asia"></i> ${countryText}</span>
                        ${yearText}
                    </div>
                    <p class="hero-summary">${heroSummary}</p>
                    <div class="hero-actions">
                        <a href="${targetUrl}" class="hero-play-btn">
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
        const currentHero = heroList[currentIndex];
        const isImage = !currentHero || currentHero.isImage === true || currentHero.isImage === 1;
        const duration = isImage ? 8000 : 30000;
        if (autoPlayTimer) clearTimeout(autoPlayTimer);
        autoPlayTimer = setTimeout(() => {
            nextSlide();
        }, duration);
    }

    function resetAutoPlay() {
        if (autoPlayTimer) clearTimeout(autoPlayTimer);
        startAutoPlay();
    }
}
