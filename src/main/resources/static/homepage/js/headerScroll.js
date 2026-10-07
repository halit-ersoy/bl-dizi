// Modify headerScroll.js
export function initHeaderScroll() {
    window.addEventListener('scroll', () => {
        const header = document.getElementById('header');
        if (!header) return;

        if (window.scrollY > 50) {
            header.classList.add('scrolled');
            header.classList.remove('at-top');
        } else {
            header.classList.remove('scrolled');
            header.classList.add('at-top');
        }
    });

    // Set initial state
    const header = document.getElementById('header');
    if (header) {
        if (window.scrollY > 50) {
            header.classList.add('scrolled');
        } else {
            header.classList.add('at-top');
        }
    }
}