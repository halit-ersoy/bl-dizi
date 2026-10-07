document.addEventListener('DOMContentLoaded', function () {
    const updateBtn = document.getElementById('update-notes-btn');
    const updateModal = document.getElementById('update-notes-modal');
    const closeUpdateModal = document.getElementById('close-update-modal');
    const updateNotesList = document.getElementById('update-notes-list');
    const miniPopup = document.getElementById('mini-update-popup');
    const closeMiniPopup = document.getElementById('close-mini-popup');
    const updateBadge = document.getElementById('update-badge');

    let allNotes = [];

    // Fetch active updates
    async function fetchUpdates() {
        try {
            // Add timestamp to bypass browser cache
            const response = await fetch(`/api/updates?t=${new Date().getTime()}`);
            const notes = await response.json();

            allNotes = notes;

            // Always render (shows empty state if no notes)
            renderNotes(notes);

            if (notes.length > 0) {
                checkNewUpdate(notes[0]);
            }
        } catch (error) {
            console.error('Error fetching updates:', error);
            renderNotes([]); // Show empty state on error too
        }
    }

    function renderNotes(notes) {
        if (!notes || notes.length === 0) {
            updateNotesList.innerHTML = `
                <div class="update-notes-empty">
                    <div class="empty-icon"><i class="fas fa-rocket"></i></div>
                    <h3>Yeni güncellemeler yolda...</h3>
                    <p>Siteyi geliştirmeye devam ediyoruz. Yakında haberleri buradan paylaşacağız!</p>
                </div>
            `;
            return;
        }

        updateNotesList.innerHTML = notes.map(note => `
            <div class="update-note-item">
                <div class="update-note-date">${new Date(note.createdAt).toLocaleDateString('tr-TR')}</div>
                <h3 class="update-note-title">${note.title}</h3>
                <p class="update-note-message">${note.message.replace(/\n/g, '<br>')}</p>
            </div>
        `).join('');
    }

    function checkNewUpdate(latestNote) {
        const lastSeenId = localStorage.getItem('lastSeenUpdateId');

        if (lastSeenId !== latestNote.id) {
            // Show badge
            updateBadge.style.display = 'flex';

            // Show mini popup
            document.getElementById('mini-popup-title').innerText = latestNote.title;
            // Truncate message for popup
            const shortMsg = latestNote.message.length > 60 ? latestNote.message.substring(0, 60) + '...' : latestNote.message;
            document.getElementById('mini-popup-message').innerText = shortMsg;

            setTimeout(() => {
                miniPopup.style.display = 'flex';
                miniPopup.classList.add('show');
            }, 2000);
        }
    }

    function openModal() {
        updateModal.style.display = 'flex';
        document.body.style.overflow = 'hidden';

        // Mark as seen
        if (allNotes.length > 0) {
            localStorage.setItem('lastSeenUpdateId', allNotes[0].id);
            updateBadge.style.display = 'none';
        }

        // Hide mini popup if visible
        miniPopup.classList.remove('show');
        setTimeout(() => miniPopup.style.display = 'none', 300);
    }

    function closeModal() {
        updateModal.style.display = 'none';
        document.body.style.overflow = '';
    }

    if (updateBtn) updateBtn.addEventListener('click', openModal);
    if (closeUpdateModal) closeUpdateModal.addEventListener('click', closeModal);

    if (miniPopup) {
        miniPopup.addEventListener('click', (e) => {
            if (e.target.id === 'close-mini-popup') {
                miniPopup.classList.remove('show');
                setTimeout(() => miniPopup.style.display = 'none', 300);
                // Also mark as seen if user closes it? 
                // Let's not, they might want to read later.
                e.stopPropagation();
            } else {
                openModal();
            }
        });
    }

    if (updateModal) {
        updateModal.addEventListener('click', (e) => {
            if (e.target === updateModal) closeModal();
        });
    }

    fetchUpdates();
});
