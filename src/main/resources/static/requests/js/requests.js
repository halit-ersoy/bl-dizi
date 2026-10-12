// requests.js - Who Dat Idols Content Requests Page Logic

document.addEventListener('DOMContentLoaded', () => {
    // State Variables
    let currentFilter = 'all';
    let currentSort = 'votes';
    let currentPage = 1;
    let totalPages = 1;
    let requestsData = [];
    let selectedExternalItem = null;
    let isManualMode = false;
    let searchDebounceTimeout = null;
    let manualCheckTimeout = null;
    let currentSearchAbortController = null;

    // DOM Elements
    const requestsList = document.getElementById('requestsList');
    const emptyState = document.getElementById('emptyRequestsState');
    const paginationWrapper = document.getElementById('requestsPagination');
    const prevPageBtn = document.getElementById('prevPageBtn');
    const nextPageBtn = document.getElementById('nextPageBtn');
    const pageInfo = document.getElementById('pageInfo');
    const localSearchInput = document.getElementById('requestsLocalSearch');

    // Filter & Sort Elements
    const filterTabs = document.querySelectorAll('.filter-tab');
    const sortPills = document.querySelectorAll('.sort-pill');

    // Modal Elements
    const openNewRequestBtn = document.getElementById('openNewRequestBtn');
    const emptyStateRequestBtn = document.getElementById('emptyStateRequestBtn');
    const newRequestModal = document.getElementById('newRequestModal');
    const closeRequestModal = document.getElementById('closeRequestModal');
    const cancelRequestBtn = document.getElementById('cancelRequestBtn');
    const newRequestForm = document.getElementById('newRequestForm');
    const modalAuthWarning = document.getElementById('modalAuthWarning');
    const modalOpenLoginBtn = document.getElementById('modalOpenLoginBtn');

    // Login Modal Elements
    const loginModal = document.getElementById('login-modal');
    const closeLoginModalBtn = document.getElementById('close-modal');
    const loginForm = document.getElementById('login-form-element');
    const loginSubmitBtn = document.getElementById('login-submit');

    // Already Exists Elements
    const alreadyExistsBanner = document.getElementById('alreadyExistsBanner');
    const alreadyExistsTitle = document.getElementById('alreadyExistsTitle');
    const alreadyExistsSubtitle = document.getElementById('alreadyExistsSubtitle');
    const alreadyExistsLink = document.getElementById('alreadyExistsLink');

    // Form Sub-elements
    const typeRadios = document.querySelectorAll('input[name="reqType"]');
    const externalSearchInput = document.getElementById('externalSearchInput');
    const externalSearchSpinner = document.getElementById('externalSearchSpinner');
    const triggerSearchBtn = document.getElementById('triggerSearchBtn');
    const externalSearchResults = document.getElementById('externalSearchResults');
    const externalSearchSection = document.getElementById('externalSearchSection');

    const selectedItemPreview = document.getElementById('selectedItemPreview');
    const previewPoster = document.getElementById('previewPoster');
    const previewTitle = document.getElementById('previewTitle');
    const previewTypeBadge = document.getElementById('previewTypeBadge');
    const previewYear = document.getElementById('previewYear');
    const previewSource = document.getElementById('previewSource');
    const previewOverview = document.getElementById('previewOverview');
    const clearSelectionBtn = document.getElementById('clearSelectionBtn');

    const toggleManualBtn = document.getElementById('toggleManualBtn');
    const manualToggleText = document.getElementById('manualToggleText');
    const manualFieldsGroup = document.getElementById('manualFieldsGroup');
    const manualTitleInput = document.getElementById('manualTitleInput');
    const manualYearInput = document.getElementById('manualYearInput');
    const manualPosterInput = document.getElementById('manualPosterInput');
    const requestNoteInput = document.getElementById('requestNoteInput');
    const formFeedbackMsg = document.getElementById('formFeedbackMsg');
    const submitRequestBtn = document.getElementById('submitRequestBtn');

    // Toast Element
    const toastNotification = document.getElementById('toastNotification');
    const toastMessage = document.getElementById('toastMessage');

    // Check if user is logged in
    function isUserLoggedIn() {
        return !!(localStorage.getItem('bldiziUserToken') || localStorage.getItem('wdiUserToken') || document.cookie.includes('wdiAuth'));
    }

    // Login Modal Controls
    function openLoginModal() {
        if (!loginModal) return;
        loginModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        const emailInput = document.getElementById('email');
        if (emailInput) setTimeout(() => emailInput.focus(), 300);
    }

    function closeLoginModal() {
        if (!loginModal) return;
        loginModal.classList.remove('active');
        document.body.style.overflow = '';
    }

    if (closeLoginModalBtn) {
        closeLoginModalBtn.addEventListener('click', closeLoginModal);
    }

    if (loginModal) {
        loginModal.addEventListener('click', (e) => {
            if (e.target === loginModal) closeLoginModal();
        });
    }

    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const usernameOrEmail = document.getElementById('email')?.value?.trim();
            const password = document.getElementById('password')?.value;
            if (!usernameOrEmail || !password) {
                if (loginSubmitBtn) {
                    loginSubmitBtn.innerHTML = '<i class="fas fa-times"></i> Bilgileri doldurun';
                    loginSubmitBtn.style.backgroundColor = '#e74c3c';
                    setTimeout(() => {
                        loginSubmitBtn.innerHTML = 'Giriş Yap';
                        loginSubmitBtn.style.backgroundColor = '';
                    }, 2000);
                }
                return;
            }

            if (loginSubmitBtn) {
                loginSubmitBtn.classList.add('loading');
                loginSubmitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
            }

            try {
                const response = await fetch('/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ usernameOrEmail, password })
                });

                const data = await response.json();
                const isSuccess = data.success === true || data.success === 1 || data.success === 'true';

                if (isSuccess) {
                    if (loginSubmitBtn) {
                        loginSubmitBtn.classList.remove('loading');
                        loginSubmitBtn.innerHTML = '<i class="fas fa-check"></i> Başarılı';
                        loginSubmitBtn.style.backgroundColor = 'var(--primary-color)';
                    }
                    localStorage.setItem('wdiUserToken', data.cookie);
                    localStorage.setItem('wdiUserNickname', data.nickname);

                    setTimeout(() => {
                        closeLoginModal();
                        window.location.reload();
                    }, 1000);
                } else {
                    if (loginSubmitBtn) {
                        loginSubmitBtn.classList.remove('loading');
                        loginSubmitBtn.innerHTML = '<i class="fas fa-times"></i> Hatalı Giriş';
                        loginSubmitBtn.style.backgroundColor = '#e74c3c';
                        setTimeout(() => {
                            loginSubmitBtn.innerHTML = 'Giriş Yap';
                            loginSubmitBtn.style.backgroundColor = '';
                        }, 2500);
                    }
                }
            } catch (err) {
                if (loginSubmitBtn) {
                    loginSubmitBtn.classList.remove('loading');
                    loginSubmitBtn.innerHTML = '<i class="fas fa-times"></i> Hata';
                    loginSubmitBtn.style.backgroundColor = '#e74c3c';
                    setTimeout(() => {
                        loginSubmitBtn.innerHTML = 'Giriş Yap';
                        loginSubmitBtn.style.backgroundColor = '';
                    }, 2000);
                }
            }
        });
    }

    // Show Toast
    function showToast(msg, duration = 3000) {
        if (!toastNotification || !toastMessage) return;
        toastMessage.textContent = msg;
        toastNotification.style.display = 'block';
        setTimeout(() => {
            toastNotification.style.display = 'none';
        }, duration);
    }

    // Relative Time Formatter
    function formatRelativeTime(dateString) {
        if (!dateString) return '';
        try {
            const date = new Date(dateString);
            const now = new Date();
            const diffInSeconds = Math.floor((now - date) / 1000);

            if (diffInSeconds < 60) return 'Az önce';
            const diffInMinutes = Math.floor(diffInSeconds / 60);
            if (diffInMinutes < 60) return `${diffInMinutes} dk önce`;
            const diffInHours = Math.floor(diffInSeconds / 60);
            if (diffInHours < 24) return `${diffInHours} saat önce`;
            const diffInDays = Math.floor(diffInHours / 24);
            if (diffInDays < 30) return `${diffInDays} gün önce`;

            return date.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: 'numeric' });
        } catch (e) {
            return '';
        }
    }

    // Already Exists Banner Controls
    function showAlreadyExistsBanner(title, slug) {
        if (!alreadyExistsBanner) return;
        if (alreadyExistsTitle) alreadyExistsTitle.textContent = `"${title}" zaten sitemizde mevcut!`;
        if (alreadyExistsSubtitle) alreadyExistsSubtitle.textContent = 'Bu yapım kütüphanemizde bulunmaktadır. Yeni bir istek oluşturmanıza gerek yoktur, hemen izleyebilirsiniz.';
        if (alreadyExistsLink) {
            alreadyExistsLink.href = `/${slug}`;
            alreadyExistsLink.style.display = 'inline-flex';
        }
        alreadyExistsBanner.style.display = 'flex';

        if (submitRequestBtn) {
            submitRequestBtn.disabled = true;
            submitRequestBtn.innerHTML = '<i class="fas fa-check-circle"></i> Sitemizde Zaten Mevcut';
            submitRequestBtn.title = 'Bu içerik zaten sitede ekli olduğu için istek gönderilemez.';
        }
    }

    function hideAlreadyExistsBanner() {
        if (alreadyExistsBanner) alreadyExistsBanner.style.display = 'none';
        if (submitRequestBtn) {
            const loggedIn = isUserLoggedIn();
            submitRequestBtn.disabled = !loggedIn;
            submitRequestBtn.innerHTML = '<i class="fas fa-check"></i> İsteği Gönder';
            submitRequestBtn.title = '';
        }
    }

    // Check if manually typed title already exists in system
    async function checkManualExisting(title, type) {
        if (!title || title.trim().length < 2) {
            hideAlreadyExistsBanner();
            return;
        }
        try {
            const res = await fetch(`/api/requests/check-existing?title=${encodeURIComponent(title.trim())}&type=${encodeURIComponent(type)}`);
            if (res.ok) {
                const data = await res.json();
                if (data.alreadyExists) {
                    showAlreadyExistsBanner(data.existingTitle || title, data.existingSlug);
                } else {
                    hideAlreadyExistsBanner();
                }
            }
        } catch (err) {
            console.error('Manual check existing error:', err);
        }
    }

    // Load Requests from Backend
    async function loadRequests() {
        if (!requestsList) return;
        requestsList.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: rgba(255,255,255,0.5);">
                <i class="fas fa-spinner fa-spin fa-2x" style="color: var(--primary-color);"></i>
                <p style="margin-top: 12px; font-size: 0.9rem;">İstekler yükleniyor...</p>
            </div>
        `;

        try {
            const url = `/api/requests?filter=${encodeURIComponent(currentFilter)}&sort=${encodeURIComponent(currentSort)}&page=${currentPage}&size=15`;
            const res = await fetch(url);
            if (!res.ok) throw new Error('İstekler alınamadı');

            const data = await res.json();
            requestsData = data.items || [];
            totalPages = data.totalPages || 1;
            currentPage = data.page || 1;

            renderRequests(requestsData);
            updatePaginationUI();
        } catch (err) {
            console.error('Fetch requests error:', err);
            requestsList.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #ff6b6b;">
                    <i class="fas fa-exclamation-triangle fa-2x"></i>
                    <p style="margin-top: 12px;">İçerik istekleri yüklenirken bir hata oluştu.</p>
                </div>
            `;
        }
    }

    // Render Request Cards
    function renderRequests(items) {
        if (!requestsList) return;

        if (!items || items.length === 0) {
            requestsList.innerHTML = '';
            if (emptyState) emptyState.style.display = 'block';
            if (paginationWrapper) paginationWrapper.style.display = 'none';
            return;
        }

        if (emptyState) emptyState.style.display = 'none';

        requestsList.innerHTML = items.map(req => {
            const isMovie = req.contentType === 'movie';
            const typeLabel = isMovie ? 'Film' : 'Dizi';
            const typeClass = isMovie ? 'movie' : 'series';

            const status = (req.status || 'PENDING').toLowerCase();
            let statusLabel = 'Beklemede';
            let statusClass = 'pending';
            if (status === 'approved' || status === 'eklendi') {
                statusLabel = 'Eklendi';
                statusClass = 'completed';
            } else if (status === 'in_review' || status === 'inceleniyor') {
                statusLabel = 'İnceleniyor';
                statusClass = 'approved';
            }

            const initialLetter = (req.userNickname || 'U').charAt(0).toUpperCase();
            const profileImgUrl = `/media/profile/${req.userId}`;

            const posterHtml = req.posterUrl ? `
                <img src="${req.posterUrl}" alt="${escapeHtml(req.title)}" loading="lazy"
                    onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'poster-fallback\\'><i class=\\'fas ${isMovie ? 'fa-film' : 'fa-tv'}\\'></i><span>${typeLabel}</span></div>';">
            ` : `
                <div class="poster-fallback">
                    <i class="fas ${isMovie ? 'fa-film' : 'fa-tv'}"></i>
                    <span>${typeLabel}</span>
                </div>
            `;

            const noteHtml = req.description ? `
                <div class="request-card-note" title="${escapeHtml(req.description)}">
                    "${escapeHtml(req.description)}"
                </div>
            ` : '';

            const yearHtml = req.releaseYear ? `<span class="badge-year">${req.releaseYear}</span>` : '';
            const relativeTime = formatRelativeTime(req.createdAt);

            return `
                <div class="request-card" data-id="${req.id}">
                    <div class="request-card-poster">
                        ${posterHtml}
                    </div>
                    <div class="request-card-content">
                        <div class="card-top-badges">
                            <span class="badge-type ${typeClass}">${typeLabel}</span>
                            ${yearHtml}
                            <span class="badge-status ${statusClass}">
                                <i class="fas fa-circle" style="font-size: 0.45rem;"></i> ${statusLabel}
                            </span>
                        </div>

                        <h3 class="request-card-title">${escapeHtml(req.title)}</h3>
                        ${noteHtml}

                        <div class="request-card-footer">
                            <div class="requester-info">
                                <div class="requester-avatar" id="avatar-${req.id}" style="background-image: url('${profileImgUrl}');">
                                    ${initialLetter}
                                </div>
                                <div class="requester-meta">
                                    <span class="requester-name">@${escapeHtml(req.userNickname)}</span>
                                    <span class="request-date">${relativeTime}</span>
                                </div>
                            </div>

                            <div class="vote-button-wrap">
                                <button class="btn-vote ${req.userVoted ? 'voted' : ''}" data-id="${req.id}"
                                    title="${req.userVoted ? 'Oyu kaldır (-)' : 'İsteği oyla (+)'}">
                                    <i class="fas fa-plus"></i>
                                    <span class="vote-count">${req.voteCount || 0}</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        // Attach Avatar error fallbacks
        items.forEach(req => {
            const avatarEl = document.getElementById(`avatar-${req.id}`);
            if (avatarEl) {
                const img = new Image();
                img.onload = () => {
                    avatarEl.style.backgroundImage = `url('/media/profile/${req.userId}')`;
                    avatarEl.style.color = 'transparent';
                };
                img.onerror = () => {
                    avatarEl.style.backgroundImage = 'none';
                    avatarEl.style.color = '#ffffff';
                };
                img.src = `/media/profile/${req.userId}`;
            }
        });

        // Attach Vote listeners
        document.querySelectorAll('.btn-vote').forEach(btn => {
            btn.addEventListener('click', handleVoteClick);
        });
    }

    // Handle Upvote / Downvote (Auto-deletes if vote count drops to 0)
    async function handleVoteClick(e) {
        e.preventDefault();
        const btn = e.currentTarget;
        const requestId = btn.getAttribute('data-id');
        if (!requestId) return;

        if (!isUserLoggedIn()) {
            showToast('Oy verebilmek için giriş yapmalısınız!');
            openLoginModal();
            return;
        }

        const countSpan = btn.querySelector('.vote-count');
        const wasVoted = btn.classList.contains('voted');
        let currentCount = parseInt(countSpan.textContent, 10) || 0;

        // Optimistic UI update
        if (wasVoted) {
            btn.classList.remove('voted');
            countSpan.textContent = Math.max(0, currentCount - 1);
        } else {
            btn.classList.add('voted');
            countSpan.textContent = currentCount + 1;
        }

        try {
            const res = await fetch(`/api/requests/${requestId}/vote`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });

            if (res.status === 401) {
                // Revert
                if (wasVoted) btn.classList.add('voted');
                else btn.classList.remove('voted');
                countSpan.textContent = currentCount;

                showToast('Oturum süresi dolmuş. Lütfen tekrar giriş yapın.');
                openLoginModal();
                return;
            }

            const data = await res.json();
            if (data.success) {
                // If auto-deleted because vote count dropped to 0:
                if (data.deleted) {
                    showToast('İsteyen kişi sayısı 0 olduğu için istek silindi.');
                    const card = document.querySelector(`.request-card[data-id="${requestId}"]`);
                    if (card) {
                        card.style.transition = 'all 0.35s ease';
                        card.style.transform = 'scale(0.85)';
                        card.style.opacity = '0';
                        setTimeout(() => {
                            card.remove();
                            const remaining = requestsList ? requestsList.querySelectorAll('.request-card') : [];
                            if (remaining.length === 0) {
                                loadRequests();
                            }
                        }, 350);
                    }
                    return;
                }

                if (data.voted) {
                    btn.classList.add('voted');
                } else {
                    btn.classList.remove('voted');
                }
                countSpan.textContent = data.voteCount;
            } else {
                // Revert
                if (wasVoted) btn.classList.add('voted');
                else btn.classList.remove('voted');
                countSpan.textContent = currentCount;
                showToast(data.message || 'Oy kaydedilemedi.');
            }
        } catch (err) {
            // Revert
            if (wasVoted) btn.classList.add('voted');
            else btn.classList.remove('voted');
            countSpan.textContent = currentCount;
            console.error('Vote error:', err);
            showToast('Bağlantı hatası.');
        }
    }

    // Pagination Updates
    function updatePaginationUI() {
        if (!paginationWrapper) return;
        if (totalPages <= 1) {
            paginationWrapper.style.display = 'none';
            return;
        }

        paginationWrapper.style.display = 'flex';
        pageInfo.textContent = `Sayfa ${currentPage} / ${totalPages}`;
        prevPageBtn.disabled = currentPage <= 1;
        nextPageBtn.disabled = currentPage >= totalPages;
    }

    if (prevPageBtn) {
        prevPageBtn.addEventListener('click', () => {
            if (currentPage > 1) {
                currentPage--;
                loadRequests();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        });
    }

    if (nextPageBtn) {
        nextPageBtn.addEventListener('click', () => {
            if (currentPage < totalPages) {
                currentPage++;
                loadRequests();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        });
    }

    // Filter Tabs
    filterTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            filterTabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            currentFilter = tab.getAttribute('data-filter') || 'all';
            currentPage = 1;
            loadRequests();
        });
    });

    // Sorting Pills
    sortPills.forEach(pill => {
        pill.addEventListener('click', () => {
            sortPills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            currentSort = pill.getAttribute('data-sort') || 'votes';
            currentPage = 1;
            loadRequests();
        });
    });

    // Local Search filtering inside current loaded items
    if (localSearchInput) {
        localSearchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            if (!query) {
                renderRequests(requestsData);
                return;
            }

            const filtered = requestsData.filter(item => {
                const titleMatch = (item.title || '').toLowerCase().includes(query);
                const userMatch = (item.userNickname || '').toLowerCase().includes(query);
                const descMatch = (item.description || '').toLowerCase().includes(query);
                return titleMatch || userMatch || descMatch;
            });

            renderRequests(filtered);
        });
    }

    // Modal Opening & Closing
    function openModal() {
        if (!newRequestModal) return;
        newRequestModal.style.display = 'flex';
        document.body.style.overflow = 'hidden';

        const loggedIn = isUserLoggedIn();
        if (modalAuthWarning) {
            modalAuthWarning.style.display = loggedIn ? 'none' : 'flex';
        }
        if (submitRequestBtn) {
            submitRequestBtn.disabled = !loggedIn;
        }

        resetModalForm();
    }

    function closeModal() {
        if (!newRequestModal) return;
        newRequestModal.style.display = 'none';
        document.body.style.overflow = '';
        resetModalForm();
    }

    if (openNewRequestBtn) openNewRequestBtn.addEventListener('click', openModal);
    if (emptyStateRequestBtn) emptyStateRequestBtn.addEventListener('click', openModal);
    if (closeRequestModal) closeRequestModal.addEventListener('click', closeModal);
    if (cancelRequestBtn) cancelRequestBtn.addEventListener('click', closeModal);

    if (newRequestModal) {
        newRequestModal.addEventListener('click', (e) => {
            if (e.target === newRequestModal) closeModal();
        });
    }

    if (modalOpenLoginBtn) {
        modalOpenLoginBtn.addEventListener('click', () => {
            closeModal();
            openLoginModal();
        });
    }

    // Reset Modal Form
    function resetModalForm() {
        selectedExternalItem = null;
        isManualMode = false;
        if (currentSearchAbortController) {
            currentSearchAbortController.abort();
            currentSearchAbortController = null;
        }
        if (searchDebounceTimeout) clearTimeout(searchDebounceTimeout);
        if (manualCheckTimeout) clearTimeout(manualCheckTimeout);
        if (newRequestForm) newRequestForm.reset();

        // Default type: series
        const seriesRadio = document.querySelector('input[name="reqType"][value="series"]');
        if (seriesRadio) seriesRadio.checked = true;
        document.querySelectorAll('.radio-pill').forEach(pill => {
            pill.classList.toggle('active', pill.getAttribute('data-type') === 'series');
        });

        if (externalSearchSection) externalSearchSection.style.display = 'block';
        if (externalSearchSpinner) externalSearchSpinner.style.display = 'none';
        if (externalSearchResults) {
            externalSearchResults.style.display = 'none';
            externalSearchResults.innerHTML = '';
        }
        if (selectedItemPreview) selectedItemPreview.style.display = 'none';
        if (manualFieldsGroup) manualFieldsGroup.style.display = 'none';
        if (manualToggleText) manualToggleText.textContent = 'Listede bulamadınız mı? Elle bilgi girin';
        hideAlreadyExistsBanner();

        if (formFeedbackMsg) {
            formFeedbackMsg.style.display = 'none';
            formFeedbackMsg.textContent = '';
            formFeedbackMsg.className = 'form-feedback';
        }
    }

    // Type Selector Radios
    typeRadios.forEach(radio => {
        radio.addEventListener('change', () => {
            document.querySelectorAll('.radio-pill').forEach(pill => {
                pill.classList.toggle('active', pill.getAttribute('data-type') === radio.value);
            });

            // If an external item of a different type was already selected,
            // deselect it and show external search with that title for the newly selected type
            if (selectedExternalItem && selectedExternalItem.type !== radio.value) {
                const prevQuery = selectedExternalItem.title;
                selectedExternalItem = null;
                selectedItemPreview.style.display = 'none';
                hideAlreadyExistsBanner();
                externalSearchSection.style.display = 'block';

                if (externalSearchInput) {
                    externalSearchInput.value = prevQuery;
                    triggerExternalSearch(prevQuery);
                }
            } else if (!isManualMode && externalSearchInput && externalSearchInput.value.trim().length >= 2) {
                triggerExternalSearch(externalSearchInput.value.trim());
            } else if (isManualMode && manualTitleInput && manualTitleInput.value.trim().length >= 2) {
                checkManualExisting(manualTitleInput.value.trim(), radio.value);
            }
        });
    });

    // Toggle Manual Mode
    if (toggleManualBtn) {
        toggleManualBtn.addEventListener('click', () => {
            isManualMode = !isManualMode;
            hideAlreadyExistsBanner();

            if (isManualMode) {
                manualFieldsGroup.style.display = 'block';
                externalSearchSection.style.display = 'none';
                selectedItemPreview.style.display = 'none';
                selectedExternalItem = null;
                manualToggleText.textContent = 'Arama moduna dön (TMDB / TVmaze)';
                if (manualTitleInput) {
                    manualTitleInput.focus();
                    if (manualTitleInput.value.trim().length >= 2) {
                        const selectedType = document.querySelector('input[name="reqType"]:checked')?.value || 'series';
                        checkManualExisting(manualTitleInput.value.trim(), selectedType);
                    }
                }
            } else {
                manualFieldsGroup.style.display = 'none';
                externalSearchSection.style.display = 'block';
                manualToggleText.textContent = 'Listede bulamadınız mı? Elle bilgi girin';
                if (externalSearchInput && externalSearchInput.value.trim().length >= 2) {
                    triggerExternalSearch(externalSearchInput.value.trim());
                }
            }
        });
    }

    // Clear Selection
    if (clearSelectionBtn) {
        clearSelectionBtn.addEventListener('click', () => {
            selectedExternalItem = null;
            selectedItemPreview.style.display = 'none';
            hideAlreadyExistsBanner();
            externalSearchSection.style.display = 'block';
            if (externalSearchInput) {
                externalSearchInput.value = '';
                externalSearchInput.focus();
            }
        });
    }

    // External Search Input Debounce & Enter handling
    if (externalSearchInput) {
        externalSearchInput.addEventListener('input', (e) => {
            const query = e.target.value.trim();
            clearTimeout(searchDebounceTimeout);

            if (query.length < 2) {
                if (externalSearchResults) externalSearchResults.style.display = 'none';
                if (externalSearchSpinner) externalSearchSpinner.style.display = 'none';
                return;
            }

            if (externalSearchSpinner) externalSearchSpinner.style.display = 'block';
            searchDebounceTimeout = setTimeout(() => {
                triggerExternalSearch(query);
            }, 300);
        });

        // Prevent enter from submitting the entire form
        externalSearchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                const query = externalSearchInput.value.trim();
                if (query.length >= 2) {
                    clearTimeout(searchDebounceTimeout);
                    triggerExternalSearch(query);
                }
            }
        });
    }

    // Trigger Search Button
    if (triggerSearchBtn) {
        triggerSearchBtn.addEventListener('click', (e) => {
            e.preventDefault();
            const query = externalSearchInput ? externalSearchInput.value.trim() : '';
            if (query.length >= 2) {
                clearTimeout(searchDebounceTimeout);
                triggerExternalSearch(query);
            } else if (externalSearchInput) {
                externalSearchInput.focus();
            }
        });
    }

    // Manual Title Input - check if exists in DB
    if (manualTitleInput) {
        manualTitleInput.addEventListener('input', (e) => {
            const query = e.target.value.trim();
            clearTimeout(manualCheckTimeout);
            if (query.length < 2) {
                hideAlreadyExistsBanner();
                return;
            }
            manualCheckTimeout = setTimeout(() => {
                const selectedType = document.querySelector('input[name="reqType"]:checked')?.value || 'series';
                checkManualExisting(query, selectedType);
            }, 400);
        });
    }

    // Trigger External Search with AbortController and instant feedback
    async function triggerExternalSearch(query) {
        if (currentSearchAbortController) {
            currentSearchAbortController.abort();
        }
        currentSearchAbortController = new AbortController();

        const selectedType = document.querySelector('input[name="reqType"]:checked')?.value || 'series';
        if (externalSearchSpinner) externalSearchSpinner.style.display = 'block';

        // Provide immediate visible feedback inside dropdown
        if (externalSearchResults) {
            externalSearchResults.innerHTML = `
                <div style="padding: 16px; text-align: center; color: rgba(255,255,255,0.7); font-size: 0.88rem;">
                    <i class="fas fa-spinner fa-spin" style="color: var(--primary-color); margin-right: 8px;"></i> TMDB ve TVmaze aranıyor...
                </div>
            `;
            externalSearchResults.style.display = 'block';
        }

        try {
            const res = await fetch(`/api/requests/search-external?q=${encodeURIComponent(query)}&type=${encodeURIComponent(selectedType)}`, {
                signal: currentSearchAbortController.signal
            });
            if (externalSearchSpinner) externalSearchSpinner.style.display = 'none';
            if (!res.ok) throw new Error('Arama başarısız');

            const results = await res.json();
            renderExternalSearchResults(results);
        } catch (err) {
            if (err.name === 'AbortError') return; // Request aborted due to new input
            console.error('External search error:', err);
            if (externalSearchSpinner) externalSearchSpinner.style.display = 'none';
            if (externalSearchResults) {
                externalSearchResults.innerHTML = `
                    <div style="padding: 14px; text-align: center; color: #ff6b6b; font-size: 0.85rem;">
                        Arama sırasında bir hata oluştu veya bağlantı zaman aşımına uğradı.
                    </div>
                `;
                externalSearchResults.style.display = 'block';
            }
        }
    }

    // Render External Search Autocomplete
    function renderExternalSearchResults(results) {
        if (!externalSearchResults) return;

        if (!results || results.length === 0) {
            externalSearchResults.innerHTML = `
                <div style="padding: 14px; text-align: center; color: rgba(255,255,255,0.6); font-size: 0.85rem;">
                    Sonuç bulunamadı. "Elle bilgi girin" seçeneğini kullanabilirsiniz.
                </div>
            `;
            externalSearchResults.style.display = 'block';
            return;
        }

        externalSearchResults.innerHTML = results.map((item, index) => {
            const yearStr = item.releaseYear ? `(${item.releaseYear})` : '';
            const posterImg = item.posterUrl
                ? `<img src="${item.posterUrl}" class="autocomplete-thumb" alt="Poster" onerror="this.style.display='none';">`
                : `<div class="autocomplete-thumb" style="display:flex;align-items:center;justify-content:center;color:#666;"><i class="fas fa-image"></i></div>`;

            const existingBadge = item.alreadyExists
                ? `<span class="source-badge existing" title="Bu yapım zaten sitede mevcuttur!"><i class="fas fa-check-circle"></i> Sitede Mevcut</span>`
                : '';

            return `
                <div class="autocomplete-item" data-index="${index}">
                    ${posterImg}
                    <div class="autocomplete-meta">
                        <div class="autocomplete-title">${escapeHtml(item.title)} ${yearStr}</div>
                        <div class="autocomplete-sub">
                            <span>${item.typeLabel || (item.type === 'movie' ? 'Film' : 'Dizi')}</span>
                            <span class="dot-separator">•</span>
                            <span class="source-badge">${item.source || 'TMDB'}</span>
                            ${existingBadge}
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        externalSearchResults.style.display = 'block';

        // Attach click listener to items
        externalSearchResults.querySelectorAll('.autocomplete-item').forEach(el => {
            el.addEventListener('click', () => {
                const idx = parseInt(el.getAttribute('data-index'), 10);
                selectExternalItem(results[idx]);
            });
        });
    }

    // Select an item from External Search
    function selectExternalItem(item) {
        selectedExternalItem = item;
        externalSearchResults.style.display = 'none';
        externalSearchSection.style.display = 'none';

        // Fill preview
        previewPoster.src = item.posterUrl || '';
        previewPoster.style.display = item.posterUrl ? 'block' : 'none';
        previewTitle.textContent = item.title;
        previewTypeBadge.textContent = item.typeLabel || (item.type === 'movie' ? 'Film' : 'Dizi');
        previewYear.textContent = item.releaseYear ? `${item.releaseYear}` : 'Tarih Belirtilmemiş';
        previewSource.textContent = item.source || 'TMDB';
        previewOverview.textContent = item.overview || 'Açıklama bulunmuyor.';

        selectedItemPreview.style.display = 'flex';

        // Inform user if item is already loaded in our system
        if (item.alreadyExists) {
            showAlreadyExistsBanner(item.existingTitle || item.title, item.existingSlug);
        } else {
            hideAlreadyExistsBanner();
        }
    }

    // Form Submission
    if (newRequestForm) {
        newRequestForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            if (!isUserLoggedIn()) {
                showToast('İçerik isteğinde bulunmak için giriş yapmalısınız.');
                openLoginModal();
                return;
            }

            // Prevent submitting if already exists
            if (submitRequestBtn && submitRequestBtn.disabled) {
                return;
            }

            const selectedType = document.querySelector('input[name="reqType"]:checked')?.value || 'series';
            let title = '';
            let releaseYear = null;
            let posterUrl = null;
            let tmdbId = null;
            let tvmazeId = null;
            const description = requestNoteInput ? requestNoteInput.value.trim() : null;

            if (isManualMode) {
                title = manualTitleInput ? manualTitleInput.value.trim() : '';
                if (!title) {
                    showFormFeedback('Lütfen içerik adını belirtin.', 'error');
                    return;
                }
                const yr = manualYearInput ? parseInt(manualYearInput.value.trim(), 10) : null;
                if (yr && !isNaN(yr)) releaseYear = yr;
                posterUrl = manualPosterInput ? manualPosterInput.value.trim() : null;
            } else {
                if (!selectedExternalItem) {
                    const typed = externalSearchInput ? externalSearchInput.value.trim() : '';
                    if (typed.length >= 2) {
                        title = typed;
                    } else {
                        showFormFeedback('Lütfen arama yapıp listeden bir içerik seçin veya elle ekleyin.', 'error');
                        return;
                    }
                } else {
                    if (selectedExternalItem.alreadyExists) {
                        showAlreadyExistsBanner(selectedExternalItem.existingTitle || selectedExternalItem.title, selectedExternalItem.existingSlug);
                        showFormFeedback('Bu yapım zaten sitemizde yayındadır. Yeni istek oluşturulamaz.', 'error');
                        return;
                    }
                    title = selectedExternalItem.title;
                    releaseYear = selectedExternalItem.releaseYear;
                    posterUrl = selectedExternalItem.posterUrl;
                    tmdbId = selectedExternalItem.tmdbId;
                    tvmazeId = selectedExternalItem.tvmazeId;
                }
            }

            submitRequestBtn.disabled = true;
            submitRequestBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Gönderiliyor...';

            try {
                const res = await fetch('/api/requests', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title,
                        contentType: selectedType,
                        releaseYear,
                        posterUrl,
                        tmdbId,
                        tvmazeId,
                        description
                    })
                });

                const data = await res.json();
                if (res.ok && data.success) {
                    showToast('İsteğiniz başarıyla paylaşıldı!');
                    closeModal();
                    currentPage = 1;
                    await loadRequests();
                } else {
                    if (data.alreadyExists) {
                        showAlreadyExistsBanner(title, data.existingSlug);
                    }
                    showFormFeedback(data.message || 'İstek gönderilemedi.', 'error');
                    if (!data.alreadyExists) {
                        submitRequestBtn.disabled = false;
                        submitRequestBtn.innerHTML = '<i class="fas fa-check"></i> İsteği Gönder';
                    }
                }
            } catch (err) {
                console.error('Submit request error:', err);
                showFormFeedback('Bağlantı hatası oluştu. Lütfen tekrar deneyin.', 'error');
                submitRequestBtn.disabled = false;
                submitRequestBtn.innerHTML = '<i class="fas fa-check"></i> İsteği Gönder';
            }
        });
    }

    function showFormFeedback(msg, type = 'error') {
        if (!formFeedbackMsg) return;
        formFeedbackMsg.textContent = msg;
        formFeedbackMsg.className = `form-feedback ${type}`;
        formFeedbackMsg.style.display = 'block';
    }

    // Helper: Escape HTML
    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // Initial Load
    loadRequests();
});

