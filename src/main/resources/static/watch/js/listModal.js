let modal, selectedName, currentVideoId;

export function initListModal(videoId) {
    if (!videoId) return;
    currentVideoId = videoId;

    const glowButton = document.querySelector('.glow-button');
    if (!glowButton) return;

    // Reset any existing event listeners
    const newButton = glowButton.cloneNode(true);
    glowButton.parentNode.replaceChild(newButton, glowButton);

    // Check if video is already in any list
    const checkAndInit = () => {
        checkVideoInLists().then(isInList => {
            updateButtonState(newButton, isInList);
            if (isInList) {
                newButton.removeEventListener('click', openModal);
                newButton.addEventListener('click', removeVideoDirectly);
            } else {
                newButton.removeEventListener('click', removeVideoDirectly);
                newButton.addEventListener('click', openModal);
            }
        }).catch(err => {
            console.error("Error checking lists:", err);
            newButton.addEventListener('click', openModal);
        });
    };

    checkAndInit();

    // Listen for Series ID broadcast from contentDetails
    document.addEventListener('contentDetailsLoaded', (e) => {
        if (e.detail && e.detail.seriesId) {
            // Re-check with series ID known
            checkAndInit();
        }
    });
}

// Improved checkVideoInLists function
async function checkVideoInLists() {
    const videoId = currentVideoId;
    if (!videoId) return false;

    // Get seriesId if available (from contentDetails)
    const seriesId = document.body.dataset.seriesId || null;

    try {
        const res = await fetch('/api/saved/lists', { credentials: 'include' });
        if (res.status === 401) return false;
        if (!res.ok) return false;

        const data = await res.json();

        // If the API returned an error object instead of an array
        if (!Array.isArray(data)) {
            console.error('List data is not an array:', data);
            return false;
        }

        // Filter out entries with no VideoID (empty lists)
        const videos = data.filter(item => item.VideoID);

        // Compare using normalized strings
        const normalizedVideoId = String(videoId).trim().toLowerCase();
        const normalizedSeriesId = seriesId ? String(seriesId).trim().toLowerCase() : null;

        const isInList = videos.some(item => {
            const itemVid = String(item.VideoID).trim().toLowerCase();
            return itemVid === normalizedVideoId || (normalizedSeriesId && itemVid === normalizedSeriesId);
        });

        return isInList;
    } catch (err) {
        console.error('Liste kontrolü yapılamadı:', err);
        return false;
    }
}

// Update button appearance based on state
function updateButtonState(button, isInList) {
    button.innerHTML = isInList
        ? '<i class="fas fa-times"></i> İstek Listesinden Çıkar'
        : '<i class="fas fa-plus"></i> İstek Listesine Ekle';

    if (isInList) {
        button.classList.add('in-list');
    } else {
        button.classList.remove('in-list');
    }
}

async function removeVideoDirectly() {
    const videoId = currentVideoId;
    if (!videoId) return;

    try {
        const res = await fetch(`/api/saved/remove?videoId=${videoId}`, {
            method: 'POST',
            credentials: 'include'
        });

        const data = await res.json();
        if (data.Result === 1) {
            // Update button state after successful removal
            const glowButton = document.querySelector('.glow-button');
            updateButtonState(glowButton, false);
            glowButton.removeEventListener('click', removeVideoDirectly);
            glowButton.addEventListener('click', openModal);

            // Show temporary success message
            const infoBar = document.querySelector('.info-bar');
            const msg = document.createElement('div');
            msg.className = 'temp-success-message';
            msg.innerHTML = '<i class="fas fa-check-circle"></i> Listeden çıkarıldı';
            infoBar.appendChild(msg);
            setTimeout(() => msg.remove(), 3000);
        }
    } catch (err) {
        console.error('Çıkarma işlemi başarısız:', err);
    }
}

function openModal() {
    modal = document.getElementById('addToListModal');
    if (!modal) return;
    modal.style.display = 'flex';
    setTimeout(() => modal.classList.add('show'), 10);
    setupModalClose();
    loadUserLists();
    setupCreateList();
}

function setupModalClose() {
    const closeBtn = modal.querySelector('.close-modal');
    closeBtn.onclick = closeModal;
    window.onclick = e => {
        if (e.target === modal) closeModal();
    };
}

function closeModal() {
    modal.classList.remove('show');
    modal.style.display = 'flex';
    modal.style.justifyContent = 'center';
    modal.style.alignItems = 'center';
    setTimeout(() => (modal.style.display = 'none'), 300);
}

async function loadUserLists() {
    const container = modal.querySelector('#userLists');
    const token = localStorage.getItem('wdiUserToken');
    if (!token) {
        container.innerHTML = `
      <div class="error-message">
        <i class="fas fa-exclamation-circle"></i>
        Oturum açmalısınız.
      </div>`;
        return;
    }

    container.innerHTML = `
    <div class="loading-spinner">
      <i class="fas fa-spinner fa-spin"></i> Yükleniyor...
    </div>`;

    try {
        const res = await fetch('/api/saved/lists', { credentials: 'include' });
        if (!res.ok) throw new Error('Liste alınamadı');
        const data = await res.json();
        renderUserLists(data, container);
    } catch (err) {
        container.innerHTML = `
      <div class="error-message">
        <i class="fas fa-exclamation-circle"></i>
        ${err.message}
      </div>`;
    }
}

function renderUserLists(listData, container) {
    container.innerHTML = '';
    if (!listData.length) {
        container.innerHTML = `
      <div class="empty-list-message">
        Henüz liste yok. Oluşturun.
      </div>`;
        return;
    }

    const groups = listData.reduce((acc, item) => {
        const name = item.ListName;
        if (!acc[name]) acc[name] = { name, videos: [], has: false };
        if (item.VideoID) {
            acc[name].videos.push(item);
            if (String(item.VideoID) === String(currentVideoId))
                acc[name].has = true;
        }
        return acc;
    }, {});

    Object.values(groups).forEach(g => {
        const el = document.createElement('div');
        el.className = `list-item${g.has ? ' has-video' : ''}`;
        el.innerHTML = `
      <h5>${g.name}</h5>
      <p>${g.videos.length} video</p>
      ${g.has ? '<span class="in-list-badge"><i class="fas fa-check"></i> Listede</span>' : ''}
    `;
        el.onclick = () => selectList(el, g.name, g.has);
        container.appendChild(el);
    });
}

function selectList(el, name, has) {
    modal.querySelectorAll('.list-item').forEach(x => x.classList.remove('selected'));
    el.classList.add('selected');
    selectedName = name;
    showListActions(has);
}

function showListActions(isInList) {
    let actions = modal.querySelector('.list-actions');
    if (!actions) {
        actions = document.createElement('div');
        actions.className = 'list-actions';
        actions.innerHTML = `
      <button class="btn-cancel">İptal</button>
      <button class="btn-save">
        <i class="fas fa-${isInList ? 'times' : 'plus'}"></i>
        ${isInList ? 'Çıkar' : 'Ekle'}
      </button>
    `;
        modal.querySelector('.modal-body').appendChild(actions);
        actions.querySelector('.btn-cancel').onclick = closeModal;
        actions.querySelector('.btn-save').onclick = isInList ? removeFromList : addToList;
    } else {
        const btn = actions.querySelector('.btn-save');
        btn.innerHTML = `<i class="fas fa-${isInList ? 'times' : 'plus'}"></i> ${isInList ? 'Çıkar' : 'Ekle'}`;
    }
}

async function addToList() {
    if (!selectedName) return showMessage('error', 'Liste seçilmedi.');
    const videoId = currentVideoId;
    try {
        const res = await fetch(
            `/api/saved/add?title=${encodeURIComponent(selectedName)}&videoId=${videoId}`,
            { method: 'POST', credentials: 'include' }
        );
        const d = await res.json();
        if (d.Result === 1) {
            // Update button state immediately
            const glowButton = document.querySelector('.glow-button');
            updateButtonState(glowButton, true);

            // Change the event listener
            glowButton.removeEventListener('click', openModal);
            glowButton.addEventListener('click', removeVideoDirectly);

            // Show temporary success message on the button
            const successIndicator = document.createElement('span');
            successIndicator.className = 'temp-success-message';
            document.querySelector('.info-bar').appendChild(successIndicator);
            setTimeout(() => successIndicator.remove(), 2000);
            closeModal();
        } else {
            showMessage('error', d.Message);
        }
    } catch {
        showMessage('error', 'Eklerken hata oluştu.');
    }
}

async function removeFromList() {
    const videoId = currentVideoId;
    try {
        const res = await fetch(`/api/saved/remove?videoId=${videoId}`, {
            method: 'POST', credentials: 'include'
        });
        const d = await res.json();
        if (d.Result === 1) {
            showMessage('success', 'Listeden çıkarıldı');
            setTimeout(closeModal, 1500);
        } else {
            showMessage('error', d.Message);
        }
    } catch {
        showMessage('error', 'Çıkarırken hata oluştu.');
    }
}

function showMessage(type, text) {
    modal.querySelectorAll('.success-message, .error-message').forEach(x => x.remove());
    const div = document.createElement('div');
    div.className = type === 'success' ? 'success-message' : 'error-message';
    div.innerHTML = `<i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i> ${text}`;
    modal.querySelector('.modal-body').appendChild(div);
    if (type === 'success') setTimeout(() => div.remove(), 3000);
}

function setupCreateList() {
    const btn = modal.querySelector('#createListBtn');
    const input = modal.querySelector('#newListName');
    btn?.addEventListener('click', async () => {
        const name = input.value.trim();
        if (!name) return showMessage('error', 'Ad boş olamaz.');
        try {
            const res = await fetch(`/api/saved/create?title=${encodeURIComponent(name)}`, {
                method: 'POST', credentials: 'include'
            });
            const d = await res.json();
            if (d.Result === 1) {
                input.value = '';
                loadUserLists();
                showMessage('success', 'Liste oluşturuldu. Şimdi ekleyebilirsiniz.');
            } else {
                showMessage('error', d.Message);
            }
        } catch {
            showMessage('error', 'Oluştururken hata oluştu.');
        }
    });
}
