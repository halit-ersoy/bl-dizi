// sanitize.js
/**
 * Utility for sanitizing HTML to prevent XSS.
 * Uses a simple DOM-based approach to escape HTML entities if DOMPurify is not available,
 * but ideally, you should include DOMPurify in your project.
 */

export function escapeHtml(unsafe) {
    if (!unsafe) return '';
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

export function sanitizeHtml(html) {
    // If you add DOMPurify to your project (highly recommended):
    // if (window.DOMPurify) return DOMPurify.sanitize(html);

    // For now, since we only need to escape text content in most places:
    return escapeHtml(html);
}
