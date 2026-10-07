export function isUserLoggedIn() {
    const authToken = localStorage.getItem('wdiUserToken');
    const userNickname = localStorage.getItem('wdiUserNickname');
    return !!(authToken && userNickname);
}

export function handleImageSkeleton(img) {
    if (!img) return;
    const parent = img.parentElement;
    if (parent && parent.classList.contains('img-skeleton')) {
        if (img.complete) {
            parent.classList.add('loaded');
        } else {
            img.addEventListener('load', () => {
                parent.classList.add('loaded');
            });
            img.addEventListener('error', () => {
                parent.classList.add('loaded'); // Hide skeleton on error too
                parent.classList.add('image-error'); // Add error class for styling
            });
        }
    }
}