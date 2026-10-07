export function isUserLoggedIn() {
    const authToken = localStorage.getItem('wdiUserToken');
    const userNickname = localStorage.getItem('wdiUserNickname');
    return !!(authToken && userNickname);
}

export function handleImageSkeleton(img) {
    if (!img) return;
    const parent = img.parentElement;

    // Cache-buster: force bypass of any stale browser disk-cache
    const src = img.getAttribute('src');
    if (src && src.startsWith('/media/image/') && !src.includes('v=2')) {
        const sep = src.includes('?') ? '&' : '?';
        img.src = src + sep + 'v=2';
    }

    const markError = () => {
        if (parent) {
            parent.classList.add('loaded');
            parent.classList.add('image-error');
        }
        img.style.display = 'none';
    };

    const markLoaded = () => {
        if (parent) {
            parent.classList.add('loaded');
            if (img.naturalWidth === 0) {
                markError();
                return;
            }
            parent.classList.remove('image-error');
        }
        img.style.display = '';
    };

    if (parent && (parent.classList.contains('img-skeleton') || parent.classList.contains('card-image-container') || parent.classList.contains('item-thumb') || parent.classList.contains('rec-image'))) {
        if (img.complete) {
            if (img.naturalWidth === 0) {
                markError();
            } else {
                markLoaded();
            }
        } else {
            img.addEventListener('load', markLoaded);
            img.addEventListener('error', markError);
        }
    }
}
