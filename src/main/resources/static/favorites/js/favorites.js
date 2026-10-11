/*** favorites.js - BL Dizi Favorilerim Modülü ***/
(() => {
    'use strict';

    // --- Cache'lenmiş DOM Elemanları ---
    const container = document.querySelector('.lists-container');
    const createListBtn = document.getElementById('createListBtn');
    const modal = document.getElementById('listManageModal');
    const modalTitle = modal.querySelector('.modal-header h3');
    const nameInput = document.getElementById('editListName');
    const saveBtn = modal.querySelector('.rename-list');
    const deleteBtn = modal.querySelector('.btn-delete');
    const cancelBtn = modal.querySelector('.btn-cancel');
    const closeBtn = modal.querySelector('.close-modal');

    let currentMode = null;   // 'create' | 'edit'
    let currentListName = null;
    let currentListElem = null;

    // --- Başlangıç ---
    document.addEventListener('DOMContentLoaded', () => {
        initFavorites();
        if (createListBtn) {
            createListBtn.addEventListener('click', () => openModal('create'));
        }
        // Liste içi tüm etkileşimler için event delegation
        container.addEventListener('click', handleContainerClick);
        // Modal üstündeki düğmeler
        saveBtn.addEventListener('click', handleSave);
        deleteBtn.addEventListener('click', handleDeleteConfirm);
        cancelBtn.addEventListener('click', closeModal);
        closeBtn.addEventListener('click', closeModal);
    });

    // --- Favori Listeleri Başlat ---
    async function initFavorites() {
        container.innerHTML = `<div class="loading-spinner"><div class="spinner"></div><p>Listeleriniz yükleniyor...</p></div>`;
        try {
            const lists = await fetchUserLists();
            if (!lists.length) {
                showNoListsMessage();
            } else {
                renderLists(lists);
                setupCollapsibleLists();
            }
        } catch (err) {
            console.error(err);
            if (err.message === 'AUTH_REQUIRED') {
                renderNotLoggedInState();
            } else {
                showError(err.message);
            }
        }
    }

    // --- Event Delegation Handler ---
    function handleContainerClick(e) {
        if (e.target.closest('.watch-btn') || e.target.closest('.play-overlay')) {
            const item = e.target.closest('.content-item');
            if (item) {
                const path = `/${item.dataset.slug || item.dataset.id}`;
                window.location.href = path;
            }
            return;
        }
        // Kaldır butonu
        if (e.target.closest('.remove-btn')) {
            e.stopPropagation();
            const item = e.target.closest('.content-item');
            removeItemFromList(item);
            return;
        }
        // Liste düzenle (kalem ikonu)
        if (e.target.closest('.list-action-btn')) {
            e.stopPropagation(); // Header click tetiklenmesin
            const wrapper = e.target.closest('.list-wrapper');
            openModal('edit', wrapper.dataset.listName, wrapper);
            return;
        }
        // Video ögesinin kendisine tıklandığında (kart tıklaması)
        if (e.target.closest('.content-item') && !e.target.closest('.remove-btn')) {
            const item = e.target.closest('.content-item');
            const path = `/${item.dataset.slug || item.dataset.id}`;
            window.location.href = path;
            return;
        }
        // Liste başlığına tıklayıp aç/kapa
        if (e.target.closest('.list-header')) {
            const wrapper = e.target.closest('.list-wrapper');
            toggleCollapse(wrapper);
        }
    }

    // --- Modal Açma/Kapama ---
    function openModal(mode, listName = '', listElem = null) {
        currentMode = mode;
        currentListName = listName;
        currentListElem = listElem;

        modal.style.display = 'flex';
        modal.offsetHeight; // Force reflow
        modal.classList.add('show');

        if (mode === 'create') {
            modalTitle.textContent = 'Yeni Liste Oluştur';
            nameInput.value = '';
            nameInput.placeholder = 'Liste adını giriniz';
            saveBtn.textContent = 'Oluştur';
            deleteBtn.style.display = 'none';
        } else {
            modalTitle.textContent = 'Listeyi Düzenle';
            nameInput.value = listName;
            saveBtn.textContent = 'Kaydet';
            deleteBtn.style.display = 'block';
        }

        nameInput.focus();
    }

    function closeModal() {
        modal.classList.remove('show');
        setTimeout(() => modal.style.display = 'none', 300);
    }

    // --- Kaydet Butonu İşlemi ---
    async function handleSave() {
        const title = nameInput.value.trim();
        if (!title) {
            const originalPlaceholder = nameInput.placeholder;
            nameInput.style.borderColor = '#ff4646';
            nameInput.placeholder = 'Liste adı boş olamaz';
            nameInput.focus();
            setTimeout(() => {
                nameInput.placeholder = originalPlaceholder;
                nameInput.style.borderColor = '';
            }, 2400);
            return;
        }

        saveBtn.disabled = true;
        try {
            if (currentMode === 'create') {
                const res = await createNewList(title);
                if (res.Result === 1) {
                    await initFavorites();
                    flashButton(createListBtn, '<i class="fas fa-check"></i> Oluşturuldu');
                    closeModal();
                } else {
                    nameInput.style.borderColor = '#ff4646';
                    nameInput.value = '';
                    nameInput.placeholder = res.Message || 'Bu isimde liste zaten var';
                    setTimeout(() => {
                        nameInput.placeholder = 'Liste adını giriniz';
                        nameInput.style.borderColor = '';
                    }, 2500);
                }
            } else {
                if (title === currentListName) {
                    closeModal();
                    return;
                }
                const res = await renameList(currentListName, title);
                if (res.Result === 1) {
                    flashButton(createListBtn, '<i class="fas fa-check"></i> Güncellendi');
                    closeModal();
                    setTimeout(() => location.reload(), 500);
                } else {
                    nameInput.style.borderColor = '#ff4646';
                    nameInput.value = '';
                    nameInput.placeholder = res.Message || 'Hata';
                    setTimeout(() => {
                        nameInput.placeholder = 'Liste adını giriniz';
                        nameInput.style.borderColor = '';
                    }, 2500);
                }
            }
        } catch (err) {
            console.error(err);
            nameInput.style.borderColor = '#ff4646';
            nameInput.value = '';
            nameInput.placeholder = err.message || 'Hata';
            setTimeout(() => {
                nameInput.placeholder = 'Liste adını giriniz';
                nameInput.style.borderColor = '';
            }, 2500);
        } finally {
            saveBtn.disabled = false;
        }
    }

    // --- Silme İşlemi ---
    async function handleDeleteConfirm() {
        if (currentMode !== 'edit') return;

        const originalText = deleteBtn.innerHTML;
        deleteBtn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Siliniyor...';
        deleteBtn.disabled = true;

        try {
            await deleteList(currentListName);

            deleteBtn.innerHTML = '<i class="fas fa-check"></i> Silindi';
            await new Promise(resolve => setTimeout(resolve, 800));

            deleteBtn.innerHTML = originalText;
            deleteBtn.disabled = false;

            if (currentListElem) {
                currentListElem.style.transition = 'all 0.4s ease';
                currentListElem.style.opacity = '0';
                currentListElem.style.transform = 'translateY(-20px)';
                setTimeout(() => {
                    currentListElem.remove();
                    if (!container.querySelector('.list-wrapper')) {
                        showNoListsMessage();
                    }
                }, 450);
            }
            closeModal();

        } catch (err) {
            console.error(err);
            deleteBtn.innerHTML = '<i class="fas fa-times"></i> Hata';
            setTimeout(() => {
                deleteBtn.innerHTML = originalText;
                deleteBtn.disabled = false;
            }, 3000);
        }
    }

    // --- Listeden Öğe Kaldır ---
    async function removeItemFromList(item) {
        try {
            await removeFromList(item.dataset.id);
            item.style.transition = 'all 0.3s';
            item.style.opacity = '0';
            item.style.transform = 'scale(0.8)';
            setTimeout(() => {
                const wrapper = item.closest('.list-wrapper');
                const grid = item.closest('.item-grid');
                const content = item.closest('.list-content');
                item.remove();

                // Video sayısını güncelle
                if (wrapper) {
                    const countSpan = wrapper.querySelector('.video-count');
                    const newCount = grid.querySelectorAll('.content-item').length;
                    if (countSpan) countSpan.textContent = `(${newCount} video)`;
                }

                if (!grid.children.length) {
                    grid.innerHTML = `<div class="empty-list" style="padding: 30px;"><p style="color:var(--text-muted)">Bu liste boş.</p></div>`;
                }

                // Yüksekliği yeniden hesapla
                if (wrapper && wrapper.classList.contains('expanded')) {
                    if (content.style.height !== 'auto') {
                        content.style.height = content.scrollHeight + 'px';
                    }
                }
            }, 300);
        } catch (err) {
            console.error(err);
            flashButton(createListBtn, '<i class="fas fa-times"></i> Hata');
        }
    }

    // --- Collapsible Logic ---
    function setupCollapsibleLists() {
        document.querySelectorAll('.list-wrapper').forEach(wrapper => {
            const content = wrapper.querySelector('.list-content');
            if (!wrapper.classList.contains('expanded')) {
                content.style.height = '0';
            } else {
                content.style.height = 'auto';
            }
        });
    }

    function toggleCollapse(wrapper) {
        const content = wrapper.querySelector('.list-content');
        const isExpanding = !wrapper.classList.contains('expanded');

        // Akordeon etkisi için diğerlerini kapat
        document.querySelectorAll('.list-wrapper.expanded').forEach(w => {
            if (w !== wrapper) {
                w.classList.remove('expanded');
                const otherContent = w.querySelector('.list-content');
                otherContent.style.height = otherContent.scrollHeight + 'px';
                otherContent.offsetHeight;
                otherContent.style.height = '0';
            }
        });

        if (isExpanding) {
            wrapper.classList.add('expanded');
            content.style.height = content.scrollHeight + 'px';

            const onTransitionEnd = (e) => {
                if (e.propertyName === 'height') {
                    content.style.height = 'auto';
                    content.removeEventListener('transitionend', onTransitionEnd);
                }
            };
            content.addEventListener('transitionend', onTransitionEnd);
        } else {
            wrapper.classList.remove('expanded');
            if (content.style.height === 'auto') {
                content.style.height = content.scrollHeight + 'px';
                content.offsetHeight;
            }
            content.style.height = '0';
        }
    }

    // --- API Çağrıları ---
    async function fetchUserLists() {
        const token = localStorage.getItem('bldiziUserToken') || localStorage.getItem('wdiUserToken') || document.cookie.includes('wdiAuth');
        if (!token) {
            throw new Error('AUTH_REQUIRED');
        }
        const res = await fetch('/api/saved/lists', { credentials: 'include' });
        if (res.status === 401) {
            throw new Error('AUTH_REQUIRED');
        }
        if (!res.ok) throw new Error('Listeler alınamadı');
        return processListData(await res.json());
    }

    function processListData(data) {
        if (!Array.isArray(data) || !data.length) return [];
        const map = {};
        data.forEach(item => {
            if (!map[item.ListName]) map[item.ListName] = { name: item.ListName, videos: [] };
            if (item.VideoID) {
                map[item.ListName].videos.push({
                    id: item.VideoID,
                    title: item.VideoName || 'İçerik',
                    image: '/media/image/' + item.VideoID,
                    year: item.Year || '',
                    type: (item.Category || '').split(',')[0] || '',
                    slug: item.slug || item.VideoID
                });
            }
        });
        return Object.values(map);
    }

    async function createNewList(title) {
        const res = await fetch(`/api/saved/create?title=${encodeURIComponent(title)}`, {
            method: 'POST', credentials: 'include'
        });
        const data = await res.json();
        return data;
    }

    async function renameList(oldName, newName) {
        const res = await fetch(
            `/api/saved/rename-list?title=${encodeURIComponent(oldName)}&newTitle=${encodeURIComponent(newName)}`, {
            method: 'POST', credentials: 'include'
        });
        const data = await res.json();
        return data;
    }

    async function deleteList(title) {
        const form = new FormData();
        form.append('title', title);
        const res = await fetch('/api/saved/delete-list', {
            method: 'POST', body: form, credentials: 'include'
        });
        if (!res.ok) throw new Error('Sunucu hatası');
        return res.json();
    }

    async function removeFromList(id) {
        const res = await fetch(`/api/saved/remove?videoId=${id}`, {
            method: 'POST', credentials: 'include'
        });
        const data = await res.json();
        if (data.Result !== 1) throw new Error('Hata');
        return data;
    }

    // --- Render ---
    function renderLists(lists) {
        container.innerHTML = '';
        lists.forEach((list, index) => {
            const wrap = document.createElement('div');
            wrap.className = 'list-wrapper';
            wrap.dataset.listName = list.name;
            wrap.style.animationDelay = `${index * 0.1}s`;

            const count = list.videos.length;
            wrap.innerHTML = `
        <div class="list-header">
          <h3 class="list-title">
            <i class="fas fa-bookmark"></i> 
            ${escapeHtml(list.name)} 
            <span class="video-count">(${count} video)</span>
          </h3>
          <div class="list-actions">
            <button class="list-action-btn edit-list-btn" title="Listeyi Düzenle"><i class="fas fa-pen"></i></button>
            <i class="fas fa-chevron-down toggle-indicator"></i>
          </div>
        </div>
        <div class="list-content">
          <div class="list-content-inner">
            <div class="item-grid">${renderItems(list.videos)}</div>
          </div>
        </div>
      `;
            container.appendChild(wrap);
        });
    }

    function renderItems(videos) {
        if (!videos.length) {
            return `<div class="empty-list" style="padding: 20px;"><p style="color:var(--text-muted)">Henüz video eklenmemiş.</p></div>`;
        }
        return videos.map(v => `
      <div class="content-item" data-id="${v.id}" data-slug="${v.slug}">
        <div class="no-image-placeholder">
          <i class="fas fa-film"></i>
        </div>
        ${v.image ? `<img src="${v.image}" loading="lazy" alt="${escapeHtml(v.title)}" onerror="this.remove();">` : ''}\n        
        <div class="play-overlay"><i class="fas fa-play"></i></div>
        <button class="remove-btn" title="Listeden Kaldır"><i class="fas fa-times"></i></button>
        
        <div class="content-overlay">
          <h4 class="content-title">${escapeHtml(v.title)}</h4>
          <div class="content-meta">
            ${v.year ? `<span>${v.year}</span>` : ''}
            ${v.type ? `<span style="margin-left:8px; opacity:0.7"> ${v.type}</span>` : ''}
          </div>
        </div>
      </div>
    `).join('');
    }

    // --- UI Helpers ---
    function showNoListsMessage() {
        container.innerHTML = `
      <div class="empty-list">
        <i class="fas fa-layer-group" style="opacity:0.3"></i>
        <h3>Henüz listeniz yok</h3>
        <p>İçerikleri gruplamak için yeni bir liste oluşturun.</p>
      </div>
    `;
    }

    function renderNotLoggedInState() {
        container.innerHTML = `
      <div class="empty-list">
        <i class="fas fa-user-lock" style="font-size:3.5rem;margin-bottom:16px;color:rgba(255,255,255,0.2);display:block;"></i>
        <h3>Giriş Yapmalısınız</h3>
        <p>Favori listelerinizi görüntülemek ve yönetmek için lütfen hesabınıza giriş yapın.</p>
        <a href="/" class="btn-primary retry-btn" style="margin-top:15px;text-decoration:none;">
          <i class="fas fa-sign-in-alt"></i> Giriş Yap / Anasayfa
        </a>
      </div>
    `;
    }

    function showError(msg) {
        container.innerHTML = `
      <div class="empty-list">
        <i class="fas fa-exclamation-triangle" style="color:#ff4646"></i>
        <h3>Hata</h3>
        <p>${escapeHtml(msg)}</p>
        <button class="btn-primary retry-btn" style="margin-top:15px"><i class="fas fa-redo"></i> Tekrar Dene</button>
      </div>
    `;
        container.querySelector('.retry-btn')
            ?.addEventListener('click', initFavorites);
    }

    function flashButton(btn, html) {
        const orig = btn.innerHTML;
        btn.innerHTML = html;
        setTimeout(() => btn.innerHTML = orig, 2000);
    }

    function escapeHtml(str) {
        if (!str) return '';
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

})();
