/*** history.js - User Watch History Management for BL Dizi ***/
(() => {
    'use strict';

    // DOM Elements
    const container = document.querySelector('.history-container');
    const clearHistoryBtn = document.getElementById('clearHistoryBtn');
    const modal = document.getElementById('confirmModal');
    const closeModalBtn = modal ? modal.querySelector('.close-modal') : null;
    const cancelModalBtn = modal ? modal.querySelector('.btn-cancel') : null;
    const confirmDeleteBtn = modal ? modal.querySelector('.btn-confirm-delete') : null;

    let historyItems = [];

    document.addEventListener('DOMContentLoaded', () => {
        initHistory();
        setupEventListeners();
    });

    async function initHistory() {
        const token = localStorage.getItem('bldiziUserToken') || localStorage.getItem('wdiUserToken') || document.cookie.includes('wdiAuth');
        if (!token) {
            renderNotLoggedInState();
            return;
        }

        try {
            const res = await fetch('/api/history', { credentials: 'include' });
            if (res.status === 401) {
                renderNotLoggedInState();
                return;
            }
            if (!res.ok) {
                throw new Error('İzleme geçmişi alınırken bir hata oluştu.');
            }

            const data = await res.json();
            historyItems = Array.isArray(data) ? data : [];

            if (historyItems.length === 0) {
                renderEmptyState();
                if (clearHistoryBtn) clearHistoryBtn.style.display = 'none';
            } else {
                renderHistoryGrid(historyItems);
                if (clearHistoryBtn) clearHistoryBtn.style.display = 'inline-flex';
            }
        } catch (err) {
            console.error('History fetch error:', err);
            renderErrorState(err.message || 'Geçmiş yüklenemedi.');
            if (clearHistoryBtn) clearHistoryBtn.style.display = 'none';
        }
    }

    function setupEventListeners() {
        if (container) {
            container.addEventListener('click', handleContainerClick);
        }

        if (clearHistoryBtn) {
            clearHistoryBtn.addEventListener('click', () => {
                openConfirmModal();
            });
        }

        if (closeModalBtn) {
            closeModalBtn.addEventListener('click', closeConfirmModal);
        }

        if (cancelModalBtn) {
            cancelModalBtn.addEventListener('click', closeConfirmModal);
        }

        if (modal) {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    closeConfirmModal();
                }
            });
        }

        if (confirmDeleteBtn) {
            confirmDeleteBtn.addEventListener('click', handleClearAll);
        }
    }

    function handleContainerClick(e) {
        // Individual item remove button
        const removeBtn = e.target.closest('.remove-btn');
        if (removeBtn) {
            e.stopPropagation();
            const card = removeBtn.closest('.content-item');
            if (card && card.dataset.historyId) {
                deleteSingleItem(card.dataset.historyId, card);
            }
            return;
        }

        // Card navigation
        const card = e.target.closest('.content-item');
        if (card) {
            const slug = card.dataset.slug;
            if (slug) {
                window.location.href = `/${slug}`;
            }
        }
    }

    async function deleteSingleItem(historyId, cardElement) {
        cardElement.style.pointerEvents = 'none';
        cardElement.style.opacity = '0.5';

        try {
            const res = await fetch(`/api/history/${historyId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (!res.ok) throw new Error('Silinemedi.');

            // Animate card removal
            cardElement.style.transition = 'all 0.35s ease';
            cardElement.style.transform = 'scale(0.8)';
            cardElement.style.opacity = '0';

            setTimeout(() => {
                cardElement.remove();
                historyItems = historyItems.filter(item => String(item.historyId) !== String(historyId));

                const remainingCards = container.querySelectorAll('.content-item');
                if (remainingCards.length === 0) {
                    renderEmptyState();
                    if (clearHistoryBtn) clearHistoryBtn.style.display = 'none';
                }
                showToast('İçerik izleme geçmişinizden kaldırıldı.');
            }, 350);

        } catch (err) {
            console.error('Delete error:', err);
            cardElement.style.pointerEvents = '';
            cardElement.style.opacity = '1';
            showToast('Öğe silinirken hata oluştu.');
        }
    }

    async function handleClearAll() {
        if (!confirmDeleteBtn) return;
        const origText = confirmDeleteBtn.innerHTML;
        confirmDeleteBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Temizleniyor...';
        confirmDeleteBtn.disabled = true;

        try {
            const res = await fetch('/api/history/clear', {
                method: 'DELETE',
                credentials: 'include'
            });

            if (!res.ok) throw new Error('Geçmiş temizlenemedi.');

            closeConfirmModal();
            historyItems = [];
            renderEmptyState();
            if (clearHistoryBtn) clearHistoryBtn.style.display = 'none';
            showToast('Tüm izleme geçmişiniz temizlendi.');
        } catch (err) {
            console.error('Clear all error:', err);
            showToast('Geçmiş temizlenirken bir hata oluştu.');
        } finally {
            confirmDeleteBtn.innerHTML = origText;
            confirmDeleteBtn.disabled = false;
        }
    }

    function openConfirmModal() {
        if (!modal) return;
        modal.style.display = 'flex';
        modal.offsetHeight; // force reflow
        modal.classList.add('show');
    }

    function closeConfirmModal() {
        if (!modal) return;
        modal.classList.remove('show');
        setTimeout(() => {
            modal.style.display = 'none';
        }, 300);
    }

    function renderHistoryGrid(items) {
        const grid = document.createElement('div');
        grid.className = 'history-grid';

        items.forEach(item => {
            const card = createHistoryCard(item);
            grid.appendChild(card);
        });

        container.innerHTML = '';
        container.appendChild(grid);
    }

    function createHistoryCard(item) {
        const card = document.createElement('div');
        card.className = 'content-item';
        card.dataset.historyId = item.historyId || '';
        card.dataset.slug = item.targetSlug || item.contentId || '';

        const type = (item.contentType || 'movie').toLowerCase();
        let typeLabel = 'Film';
        if (type === 'episode') typeLabel = 'Dizi';
        else if (type === 'series') typeLabel = 'Dizi';

        const isEpisode = type === 'episode';
        const titleText = isEpisode ? (item.seriesName || item.displayName || 'Dizi') : (item.movieName || item.displayName || 'Film');
        const subtitleText = isEpisode 
            ? (item.episodeName && item.episodeName.trim() ? `S${item.seasonNumber} B${item.episodeNumber} • ${item.episodeName}` : `${item.seasonNumber}. Sezon ${item.episodeNumber}. Bölüm`)
            : (item.movieReleaseYear ? `${item.movieReleaseYear}` : '');

        const timeAgoText = formatRelativeTime(item.watchedAt);
        const imageId = item.imageId || item.contentId;
        const posterUrl = imageId ? `/media/image/${imageId}?v=2` : '';

        card.innerHTML = `
            <div class="no-image-placeholder">
                <i class="fas fa-film"></i>
            </div>
            ${posterUrl ? `<img src="${posterUrl}" alt="${escapeHtml(titleText)}" loading="lazy" onerror="this.remove();">` : ''}
            
            <div class="card-top-badges">
                <span class="type-badge">${typeLabel}</span>
                <span class="date-badge"><i class="fas fa-clock"></i> ${timeAgoText}</span>
            </div>

            <div class="play-overlay">
                <i class="fas fa-play"></i>
            </div>

            <button class="remove-btn" title="Geçmişten Kaldır" aria-label="Kaldır">
                <i class="fas fa-times"></i>
            </button>

            <div class="content-overlay">
                <h4 class="content-title" title="${escapeHtml(titleText)}">${escapeHtml(titleText)}</h4>
                ${subtitleText ? `<div class="content-subtitle" title="${escapeHtml(subtitleText)}">${escapeHtml(subtitleText)}</div>` : ''}
            </div>
        `;

        return card;
    }

    function renderEmptyState() {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-clock-rotate-left main-icon"></i>
                <h3>İzleme Geçmişiniz Boş</h3>
                <p>Henüz herhangi bir dizi veya film izlemediniz. İzlediğiniz tüm içerikler burada otomatik olarak saklanacaktır.</p>
                <a href="/" class="btn-explore">
                    <i class="fas fa-compass"></i> İçerikleri Keşfet
                </a>
            </div>
        `;
    }

    function renderNotLoggedInState() {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-user-lock main-icon"></i>
                <h3>Giriş Yapmalısınız</h3>
                <p>İzleme geçmişinizi görüntülemek ve kaydetmek için lütfen hesabınıza giriş yapın.</p>
                <a href="/" class="btn-explore">
                    <i class="fas fa-sign-in-alt"></i> Giriş Yap / Anasayfa
                </a>
            </div>
        `;
    }

    function renderErrorState(message) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-triangle-exclamation main-icon" style="color: #ff5252;"></i>
                <h3>Bir Hata Oluştu</h3>
                <p>${escapeHtml(message)}</p>
                <button class="btn-explore" onclick="location.reload()" style="cursor: pointer; border: none;">
                    <i class="fas fa-rotate-right"></i> Yeniden Dene
                </button>
            </div>
        `;
    }

    function formatRelativeTime(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        if (isNaN(date.getTime())) return '';

        const now = new Date();
        const diffSeconds = Math.floor((now - date) / 1000);

        if (diffSeconds < 60) {
            return 'Az önce';
        }

        const diffMinutes = Math.floor(diffSeconds / 60);
        if (diffMinutes < 60) {
            return `${diffMinutes} dk önce`;
        }

        const diffHours = Math.floor(diffMinutes / 60);
        const isSameDay = now.toDateString() === date.toDateString();
        const hoursStr = String(date.getHours()).padStart(2, '0');
        const minsStr = String(date.getMinutes()).padStart(2, '0');

        if (isSameDay) {
            return `Bugün ${hoursStr}:${minsStr}`;
        }

        const yesterday = new Date(now);
        yesterday.setDate(now.getDate() - 1);
        if (yesterday.toDateString() === date.toDateString()) {
            return `Dün ${hoursStr}:${minsStr}`;
        }

        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 7) {
            return `${diffDays} gün önce`;
        }

        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const year = date.getFullYear();
        return `${day}.${month}.${year}`;
    }

    function showToast(msg) {
        const existing = document.querySelector('.toast-msg');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = 'toast-msg';
        toast.innerHTML = `<i class="fas fa-circle-check" style="color: var(--primary-color);"></i> <span>${escapeHtml(msg)}</span>`;
        document.body.appendChild(toast);

        setTimeout(() => {
            if (toast && toast.parentNode) toast.remove();
        }, 3000);
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

})();
