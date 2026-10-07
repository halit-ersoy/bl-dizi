// feedback.js

export function initFeedback() {
    const feedbackModal = document.getElementById('feedback-modal');
    if (!feedbackModal) return;

    const closeBtn = document.getElementById('close-feedback-modal');
    const form = document.getElementById('feedback-form');
    const subjectInput = document.getElementById('feedback-subject');
    const messageInput = document.getElementById('feedback-message');
    const submitBtn = document.getElementById('feedback-submit');
    const charCounter = document.getElementById('feedback-char-counter');

    // Message input character counter
    if (messageInput && charCounter) {
        messageInput.addEventListener('input', () => {
            const currentLength = messageInput.value.length;
            charCounter.textContent = `${currentLength} / 400`;
            if (currentLength >= 400) {
                charCounter.style.color = '#e74c3c';
            } else {
                charCounter.style.color = 'rgba(255,255,255,0.4)';
            }
        });
    }

    // Feedback butonuna tıklama...
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('#feedback-open-btn');
        if (btn) {
            e.preventDefault();
            openFeedbackModal();
        }
    });

    // Kapatma butonu
    if (closeBtn) {
        closeBtn.addEventListener('click', closeFeedbackModal);
    }

    // Overlay'e tıklayarak kapatma
    feedbackModal.addEventListener('click', (e) => {
        if (e.target === feedbackModal) {
            closeFeedbackModal();
        }
    });

    // Form gönderme
    if (submitBtn) {
        submitBtn.addEventListener('click', handleSubmit);
    }

    // Enter tuşu ile textarea dışında submit
    if (subjectInput) {
        subjectInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (messageInput) messageInput.focus();
            }
        });
    }

    function openFeedbackModal() {
        feedbackModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        resetForm();
        if (subjectInput) subjectInput.focus();
    }

    function closeFeedbackModal() {
        feedbackModal.classList.remove('active');
        document.body.style.overflow = '';
        resetForm();
    }

    function resetForm() {
        if (subjectInput) subjectInput.value = '';
        if (messageInput) messageInput.value = '';
        if (charCounter) {
            charCounter.textContent = '0 / 400';
            charCounter.style.color = 'rgba(255,255,255,0.4)';
        }
        if (submitBtn) {
            submitBtn.innerHTML = '<i class="fas fa-paper-plane"></i> Gönder';
            submitBtn.disabled = false;
            submitBtn.style.backgroundColor = '';
            submitBtn.classList.remove('loading');
        }
    }

    async function handleSubmit() {
        const subject = subjectInput ? subjectInput.value.trim() : '';
        const message = messageInput ? messageInput.value.trim() : '';

        if (!subject || !message) {
            submitBtn.innerHTML = '<i class="fas fa-times"></i> Tüm alanları doldurun';
            submitBtn.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                submitBtn.innerHTML = '<i class="fas fa-paper-plane"></i> Gönder';
                submitBtn.style.backgroundColor = '';
            }, 3000);
            return;
        }

        submitBtn.disabled = true;
        submitBtn.classList.add('loading');
        submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        try {
            const response = await fetch('/api/feedback', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ subject, message })
            });

            const data = await response.json();

            if (response.ok && data.success) {
                submitBtn.classList.remove('loading');
                submitBtn.innerHTML = '<i class="fas fa-check"></i> Gönderildi';
                submitBtn.style.backgroundColor = 'var(--primary-color)';

                setTimeout(() => {
                    closeFeedbackModal();
                }, 1500);
            } else {
                submitBtn.classList.remove('loading');
                submitBtn.innerHTML = `<i class="fas fa-times"></i> ${data.message || 'Hata'}`;
                submitBtn.style.backgroundColor = '#e74c3c';

                setTimeout(() => {
                    submitBtn.innerHTML = '<i class="fas fa-paper-plane"></i> Gönder';
                    submitBtn.style.backgroundColor = '';
                    submitBtn.disabled = false;
                }, 3000);
            }
        } catch (err) {
            submitBtn.classList.remove('loading');
            submitBtn.innerHTML = '<i class="fas fa-times"></i> Hata';
            submitBtn.style.backgroundColor = '#e74c3c';

            setTimeout(() => {
                submitBtn.innerHTML = '<i class="fas fa-paper-plane"></i> Gönder';
                submitBtn.style.backgroundColor = '';
                submitBtn.disabled = false;
            }, 3000);
        }
    }
}
