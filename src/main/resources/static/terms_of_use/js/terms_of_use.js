document.addEventListener('DOMContentLoaded', () => {

    const tocLinks = document.querySelectorAll('.toc-link');
    const cards = document.querySelectorAll('.policy-card');

    // 1. Smooth Scroll with fixed header offset on TOC link click
    tocLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const targetId = link.getAttribute('href')?.replace('#', '');
            if (!targetId) return;

            const targetElement = document.getElementById(targetId);
            if (targetElement) {
                const headerOffset = 95;
                const elementPosition = targetElement.getBoundingClientRect().top;
                const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

                window.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });

                // Update active state manually
                tocLinks.forEach(l => l.classList.remove('active'));
                link.classList.add('active');

                // Update hash in browser without jumping
                if (history.pushState) {
                    history.pushState(null, null, `#${targetId}`);
                }
            }
        });
    });

    // 2. Active Section Spy on Scroll using IntersectionObserver
    const observerOptions = {
        root: null,
        rootMargin: '-100px 0px -60% 0px',
        threshold: 0
    };

    const sectionObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const activeId = entry.target.id;
                tocLinks.forEach(link => {
                    const linkTarget = link.getAttribute('data-target') || link.getAttribute('href')?.replace('#', '');
                    if (linkTarget === activeId) {
                        link.classList.add('active');
                        // In mobile scrollable horizontal navbar, keep active link in view
                        if (window.innerWidth <= 992) {
                            link.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' });
                        }
                    } else {
                        link.classList.remove('active');
                    }
                });
            }
        });
    }, observerOptions);

    cards.forEach(card => sectionObserver.observe(card));

    // 3. Check for initial URL hash on load
    if (window.location.hash) {
        const hashId = window.location.hash.replace('#', '');
        const target = document.getElementById(hashId);
        if (target) {
            setTimeout(() => {
                const headerOffset = 95;
                const elementPosition = target.getBoundingClientRect().top;
                const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
                window.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });
            }, 100);
        }
    }
});
