export function initContentCarousels() {
    const navButtons = document.querySelectorAll('.carousel-nav');
    navButtons.forEach(button => {
        button.addEventListener('click', () => {
            const carouselId = button.getAttribute('data-carousel');
            const carousel = document.getElementById(carouselId);
            if (!carousel) return;
            const direction = button.classList.contains('prev-btn') ? -1 : 1;
            const scrollAmount = carousel.clientWidth * 0.8 * direction;
            carousel.scrollBy({ left: scrollAmount, behavior: 'smooth' });
            setTimeout(() => updateCarouselNavButtons(carousel), 500);
        });
    });

    const updateCarouselNavButtons = (carousel) => {
        const carouselId = carousel.id;
        const prevBtn = document.querySelector(`.prev-btn[data-carousel="${carouselId}"]`);
        const nextBtn = document.querySelector(`.next-btn[data-carousel="${carouselId}"]`);

        if (!carouselId || (!prevBtn && !nextBtn)) return;

        if (prevBtn) {
            if (carousel.scrollLeft <= 5) { // Small threshold
                prevBtn.classList.add('disabled');
            } else {
                prevBtn.classList.remove('disabled');
            }
        }
        if (nextBtn) {
            // Check if there's actual overflow to scroll
            const hasOverflow = carousel.scrollWidth > carousel.clientWidth + 5;
            const isAtEnd = carousel.scrollLeft + carousel.clientWidth >= carousel.scrollWidth - 10;

            if (!hasOverflow || isAtEnd) {
                nextBtn.classList.add('disabled');
            } else {
                nextBtn.classList.remove('disabled');
            }
        }
    };

    const carousels = document.querySelectorAll('.carousel, .upcoming-carousel');
    carousels.forEach(carousel => {
        // Initial check
        updateCarouselNavButtons(carousel);

        // Update on scroll
        carousel.addEventListener('scroll', () => updateCarouselNavButtons(carousel));

        // Update on content changes (fetches)
        const observer = new MutationObserver(() => {
            updateCarouselNavButtons(carousel);
        });
        observer.observe(carousel, { childList: true, subtree: true });

        // Update on window resize
        window.addEventListener('resize', () => updateCarouselNavButtons(carousel));
    });
}
