/* premium-modals.js - Custom Reporting Logic */

export function showReportModal(nickname, context, callback) {
    // Create overlay
    const overlay = document.createElement('div');
    overlay.className = 'premium-modal-overlay';

    overlay.innerHTML = `
        <div class="premium-modal-container">
            <div class="premium-modal-header">
                <i class="fas fa-shield-alt"></i>
                <h2>Güvenlik Bildirimi</h2>
                <p><strong>${nickname}</strong> isimli kullanıcıyı bildiriyorsunuz.</p>
            </div>
            <div class="premium-modal-body">
                <div class="report-categories">
                    <div class="category-chip" data-reason="Hakaret / Küfür">Hakaret</div>
                    <div class="category-chip" data-reason="Spam / Reklam">Spam</div>
                    <div class="category-chip" data-reason="Rahatsız Edici Davranış">Rahatsız Edici</div>
                    <div class="category-chip" data-reason="Uygunsuz İçerik">Uygunsuz</div>
                    <div class="category-chip" data-reason="Diğer">Diğer</div>
                </div>
                <div class="premium-input-group">
                    <textarea class="premium-textarea" placeholder="Eklemek istediğiniz notlar (isteğe bağlı)..."></textarea>
                </div>
            </div>
            <div class="premium-modal-footer">
                <button class="premium-btn premium-btn-cancel">İPTAL</button>
                <button class="premium-btn premium-btn-submit" disabled>BİLDİRİ GÖNDER</button>
            </div>
        </div>
    `;

    document.body.appendChild(overlay);

    // Trigger animation
    setTimeout(() => overlay.classList.add('active'), 10);

    const chips = overlay.querySelectorAll('.category-chip');
    const submitBtn = overlay.querySelector('.premium-btn-submit');
    const cancelBtn = overlay.querySelector('.premium-btn-cancel');
    const textarea = overlay.querySelector('.premium-textarea');
    let selectedReason = null;

    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            chips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            selectedReason = chip.dataset.reason;
            submitBtn.disabled = false;
        });
    });

    submitBtn.addEventListener('click', () => {
        const extraNote = textarea.value.trim();
        const fullReason = extraNote ? `${selectedReason}: ${extraNote}` : selectedReason;

        submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> GÖNDERİLİYOR...';
        submitBtn.disabled = true;

        callback(fullReason).then(success => {
            if (success) {
                overlay.querySelector('.premium-modal-header i').style.color = 'var(--primary-color)';
                overlay.querySelector('.premium-modal-header i').className = 'fas fa-check-circle';
                overlay.querySelector('.premium-modal-header i').style.filter = 'drop-shadow(0 0 15px rgba(var(--primary-rgb), 0.4))';
                overlay.querySelector('.premium-modal-header h2').innerText = 'Bildirim Alındı';
                overlay.querySelector('.premium-modal-header p').innerHTML = 'Geri bildiriminiz için teşekkürler.<br>Topluluğumuzu birlikte koruyoruz.';
                overlay.querySelector('.premium-modal-body').style.display = 'none';
                overlay.querySelector('.premium-modal-footer').innerHTML = `
                    <button class="premium-btn premium-btn-submit" style="background: var(--primary-color); color: #000; box-shadow: 0 5px 15px rgba(var(--primary-rgb), 0.3);">KAPAT</button>
                `;
                overlay.querySelector('.premium-btn-submit').onclick = closeModal;
            } else {
                alert('Bir hata oluştu. Lütfen tekrar deneyin.');
                submitBtn.disabled = false;
                submitBtn.innerText = 'BİLDİRİ GÖNDER';
            }
        });
    });

    cancelBtn.addEventListener('click', closeModal);
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closeModal();
    });

    function closeModal() {
        overlay.classList.remove('active');
        setTimeout(() => overlay.remove(), 400);
    }
}
