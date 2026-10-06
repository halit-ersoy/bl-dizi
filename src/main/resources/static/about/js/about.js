; (function () {
    'use strict';

    // Sekmeler ve animasyon için kullanılacak selector'lar
    const SELECTORS = {
        sections: '.about-section',
        animatedItems: [
            '.about-section',
            '.about-text p',
            '.team-member',
            '.feature-card',
            '.contact-item',
            '.about-content'
        ].join(','),
        heroItems: '.about-hero-content h1, .about-hero-content p',
        sectionTitles: '.about-section .section-title'
    };

    document.addEventListener('DOMContentLoaded', init);

    /**
     * Sayfa yüklenince çağrılan ana fonksiyon.
     * IntersectionObserver destekliyorsa animasyonları kurar,
     * desteklemiyorsa tüm öğelere in-view sınıfını ekler.
     */
    function init() {
        if ('IntersectionObserver' in window) {
            setSectionDelays();
            bindRippleEffect();
            setupScrollAnimations();
            animateHero();
        } else {
            // Eski tarayıcılarda tüm öğeleri görünür yap
            document.querySelectorAll(SELECTORS.animatedItems)
                .forEach(el => el.classList.add('in-view'));
        }
    }

    /**
     * Her .about-section öğesine index bazlı --item-index CSS değişkeni ekler.
     */
    function setSectionDelays() {
        document.querySelectorAll(SELECTORS.sections)
            .forEach((section, idx) => {
                section.style.setProperty('--item-index', idx);
            });
    }

    /**
     * .section-title öğelerine tıklanınca ripple efekti ekleyecek event binding.
     */
    function bindRippleEffect() {
        document.querySelectorAll(SELECTORS.sectionTitles)
            .forEach(title => {
                title.addEventListener('click', createRipple);
            });
    }

    /**
     * Click event objesine göre ripple span'ı oluşturur, konumlandırır ve siler.
     */
    function createRipple(event) {
        const title = event.currentTarget;
        const rect = title.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        const ripple = document.createElement('span');

        ripple.className = 'ripple-effect';
        ripple.style.width = ripple.style.height = `${size}px`;
        ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
        ripple.style.top = `${event.clientY - rect.top - size / 2}px`;

        title.appendChild(ripple);
        setTimeout(() => ripple.remove(), 600);
    }

    /**
     * Sayfadaki öğeleri gözlemleyen IntersectionObserver'ı başlatır.
     * Görününce .in-view sınıfı eklenir.
     */
    function setupScrollAnimations() {
        const options = { threshold: 0.1 };
        const observer = new IntersectionObserver(onIntersect, options);

        // Bölümler + diğer animasyonlu öğeler
        document.querySelectorAll(SELECTORS.animatedItems)
            .forEach(el => observer.observe(el));
    }

    /**
     * IntersectionObserver callback: öğe viewport'a girdiyse in-view ekle.
     */
    function onIntersect(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('in-view');
            }
        });
    }

    /**
     * Hero bölümündeki başlık ve paragraf öğelerine animasyon gecikmesi ve class.
     */
    function animateHero() {
        document.querySelectorAll(SELECTORS.heroItems)
            .forEach((el, idx) => {
                el.style.animationDelay = `${idx * 0.2}s`;
                el.classList.add('animate-in');
            });
    }

})();
