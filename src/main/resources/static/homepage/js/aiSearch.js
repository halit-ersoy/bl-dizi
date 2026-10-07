// AI Mood & Vibe Search Module with Beta Disclaimer & 1-Minute Cooldown

export function initAiSearch() {
    const aiBtn = document.getElementById('ai-vibe-btn');
    const modalBackdrop = document.getElementById('ai-modal-backdrop');
    const modalClose = document.getElementById('ai-modal-close');
    const submitBtn = document.getElementById('ai-vibe-submit');
    const inputArea = document.getElementById('ai-vibe-input');
    const loadingContainer = document.getElementById('ai-loading');
    const resultsContainer = document.getElementById('ai-results');
    const vibeChips = document.querySelectorAll('.ai-chip');

    // Disclaimer (Deneme Aşaması) Elements
    const disclaimerBackdrop = document.getElementById('ai-disclaimer-backdrop');
    const disclaimerClose = document.getElementById('ai-disclaimer-close');
    const disclaimerCancel = document.getElementById('ai-disclaimer-cancel');
    const disclaimerConfirm = document.getElementById('ai-disclaimer-confirm');

    // 1-minute (60 seconds) Cooldown Between Prompts
    const COOLDOWN_SECONDS = 60;
    let cooldownTimer = null;

    if (!aiBtn || !modalBackdrop) {
        return;
    }

    // 1. Button Click -> Show Disclaimer First
    aiBtn.addEventListener('click', (e) => {
        e.preventDefault();
        if (disclaimerBackdrop) {
            openDisclaimer();
        } else {
            openModal();
        }
    });

    // 2. Disclaimer "Tamam" Click -> Open AI Vibe Search Screen
    if (disclaimerConfirm) {
        disclaimerConfirm.addEventListener('click', () => {
            closeDisclaimer();
            openModal();
        });
    }

    // 3. Disclaimer Close / Cancel Click
    if (disclaimerClose) {
        disclaimerClose.addEventListener('click', closeDisclaimer);
    }
    if (disclaimerCancel) {
        disclaimerCancel.addEventListener('click', closeDisclaimer);
    }

    // 4. Close on click outside disclaimer dialog
    if (disclaimerBackdrop) {
        disclaimerBackdrop.addEventListener('click', (e) => {
            if (e.target === disclaimerBackdrop) {
                closeDisclaimer();
            }
        });
    }

    // 5. AI Search Modal Close Button
    if (modalClose) {
        modalClose.addEventListener('click', closeModal);
    }

    // 6. Close on click outside AI search dialog
    modalBackdrop.addEventListener('click', (e) => {
        if (e.target === modalBackdrop) {
            closeModal();
        }
    });

    // 7. Close on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (disclaimerBackdrop && disclaimerBackdrop.classList.contains('active')) {
                closeDisclaimer();
            } else if (modalBackdrop.classList.contains('active')) {
                closeModal();
            }
        }
    });

    // 8. Vibe Chip Clicks
    vibeChips.forEach(chip => {
        chip.addEventListener('click', () => {
            const vibeText = chip.getAttribute('data-vibe');
            if (vibeText && inputArea) {
                inputArea.value = vibeText;
                const rem = getRemainingCooldown();
                if (rem > 0) {
                    renderCooldownWarning(rem);
                    return;
                }
                triggerSearch(vibeText);
            }
        });
    });

    // 9. Submit Click
    if (submitBtn) {
        submitBtn.addEventListener('click', () => {
            if (inputArea) {
                const rem = getRemainingCooldown();
                if (rem > 0) {
                    renderCooldownWarning(rem);
                    return;
                }
                const query = inputArea.value.trim();
                triggerSearch(query);
            }
        });
    }

    // 10. Submit on Ctrl+Enter or Cmd+Enter
    if (inputArea) {
        inputArea.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                const rem = getRemainingCooldown();
                if (rem > 0) {
                    renderCooldownWarning(rem);
                    return;
                }
                triggerSearch(inputArea.value.trim());
            }
        });
    }

    function getRemainingCooldown() {
        try {
            const lastTimeStr = localStorage.getItem('ai_search_last_prompt_time');
            if (!lastTimeStr) return 0;
            const lastTime = parseInt(lastTimeStr, 10);
            if (isNaN(lastTime)) return 0;
            const elapsed = Date.now() - lastTime;
            if (elapsed < COOLDOWN_SECONDS * 1000) {
                return Math.ceil((COOLDOWN_SECONDS * 1000 - elapsed) / 1000);
            }
        } catch (e) {
            // LocalStorage inaccessible
        }
        return 0;
    }

    function recordPromptTimestamp() {
        try {
            localStorage.setItem('ai_search_last_prompt_time', Date.now().toString());
        } catch (e) {}
    }

    function setPromptTimestampWithRemaining(remSeconds) {
        try {
            const simulatedLastTime = Date.now() - ((COOLDOWN_SECONDS - remSeconds) * 1000);
            localStorage.setItem('ai_search_last_prompt_time', simulatedLastTime.toString());
        } catch (e) {}
    }

    function startCooldownTimer(seconds) {
        if (!submitBtn) return;
        if (cooldownTimer) {
            clearInterval(cooldownTimer);
            cooldownTimer = null;
        }

        let remaining = seconds;
        submitBtn.disabled = true;

        const updateBtnText = (sec) => {
            submitBtn.innerHTML = `
                <i class="fas fa-hourglass-half"></i>
                <span>Bekleyin (${sec}s)</span>
            `;
        };

        updateBtnText(remaining);

        cooldownTimer = setInterval(() => {
            remaining--;
            if (remaining <= 0) {
                clearInterval(cooldownTimer);
                cooldownTimer = null;
                submitBtn.disabled = false;
                submitBtn.innerHTML = `
                    <i class="fas fa-wand-magic-sparkles"></i>
                    <span>Önerileri Bul</span>
                `;
            } else {
                updateBtnText(remaining);
            }
        }, 1000);
    }

    function openDisclaimer() {
        if (!disclaimerBackdrop) return;
        disclaimerBackdrop.style.display = 'flex';
        void disclaimerBackdrop.offsetWidth;
        disclaimerBackdrop.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeDisclaimer() {
        if (!disclaimerBackdrop) return;
        disclaimerBackdrop.classList.remove('active');
        if (!modalBackdrop.classList.contains('active')) {
            document.body.style.overflow = '';
        }
        setTimeout(() => {
            disclaimerBackdrop.style.display = 'none';
        }, 300);
    }

    function openModal() {
        modalBackdrop.style.display = 'flex';
        void modalBackdrop.offsetWidth;
        modalBackdrop.classList.add('active');
        document.body.style.overflow = 'hidden';

        // Check if there is an active cooldown
        const rem = getRemainingCooldown();
        if (rem > 0) {
            startCooldownTimer(rem);
        }

        setTimeout(() => {
            if (inputArea) inputArea.focus();
        }, 150);
    }

    function closeModal() {
        modalBackdrop.classList.remove('active');
        if (!disclaimerBackdrop || !disclaimerBackdrop.classList.contains('active')) {
            document.body.style.overflow = '';
        }
        setTimeout(() => {
            modalBackdrop.style.display = 'none';
        }, 300);
    }

    async function triggerSearch(promptText) {
        if (!promptText || promptText.length < 3) {
            alert('Lütfen aramak istediğiniz ruh halini veya hissiyatı biraz daha detaylı yazın.');
            return;
        }

        const rem = getRemainingCooldown();
        if (rem > 0) {
            renderCooldownWarning(rem);
            startCooldownTimer(rem);
            return;
        }

        if (loadingContainer) loadingContainer.style.display = 'flex';
        if (resultsContainer) resultsContainer.innerHTML = '';
        if (submitBtn) submitBtn.disabled = true;

        try {
            const response = await fetch('/api/search/ai', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ prompt: promptText })
            });

            const data = await response.json();

            if (loadingContainer) loadingContainer.style.display = 'none';

            // Check if backend returned 429 rate limit
            if (response.status === 429 || data.rateLimited) {
                const remaining = data.remainingSeconds || 60;
                setPromptTimestampWithRemaining(remaining);
                startCooldownTimer(remaining);
                renderCooldownWarning(remaining);
                return;
            }

            if (!data.success) {
                if (submitBtn) submitBtn.disabled = false;
                renderError(data.message || 'Bir hata oluştu.');
                return;
            }

            // Successful search: activate 60s cooldown
            recordPromptTimestamp();
            startCooldownTimer(COOLDOWN_SECONDS);

            renderResults(data.results);
        } catch (err) {
            console.error('AI Vibe Search failed:', err);
            if (loadingContainer) loadingContainer.style.display = 'none';
            if (submitBtn) submitBtn.disabled = false;
            renderError('Sunucuyla iletişim kurulurken bir sorun oluştu. Sistem deneme aşamasında olduğu için lütfen biraz sonra tekrar deneyin.');
        }
    }

    function renderCooldownWarning(seconds) {
        if (!resultsContainer) return;
        resultsContainer.innerHTML = `
            <div class="ai-cooldown-box">
                <i class="fas fa-hourglass-half ai-cooldown-icon"></i>
                <div class="ai-cooldown-info">
                    <h4>İstek Limiti (1 Dakika Bekleme Süresi)</h4>
                    <p>Sistem yoğunluğunu önlemek ve stabil çalışabilmesi için her bir öneri araması arasında <strong>1 dakika</strong> bekleme süresi bulunmaktadır.</p>
                    <p class="ai-cooldown-timer-text">Yeni bir öneri istemek için lütfen <strong><span id="ai-cooldown-sec">${seconds}</span> saniye</strong> bekleyin.</p>
                </div>
            </div>
        `;

        const secSpan = document.getElementById('ai-cooldown-sec');
        if (secSpan) {
            const interval = setInterval(() => {
                const cur = getRemainingCooldown();
                if (cur <= 0 || !document.getElementById('ai-cooldown-sec')) {
                    clearInterval(interval);
                    if (resultsContainer.querySelector('.ai-cooldown-box')) {
                        resultsContainer.innerHTML = '';
                    }
                } else {
                    secSpan.textContent = cur;
                }
            }, 1000);
        }
    }

    function renderResults(items) {
        if (!resultsContainer) return;
        resultsContainer.innerHTML = '';

        if (!items || items.length === 0) {
            resultsContainer.innerHTML = `
                <div class="ai-error-box" style="text-align: center; color: #cbd5e1;">
                    <i class="fas fa-search" style="font-size: 1.5rem; margin-bottom: 0.5rem; display: block; color: #a855f7;"></i>
                    Bu hisse tam uyan bir yapım bulunamadı. Lütfen aramanızı biraz farklı kelimelerle deneyin.
                </div>
            `;
            return;
        }

        items.forEach(item => {
            const card = document.createElement('div');
            card.className = 'ai-result-card';

            const typeClass = (item.type && item.type.toLowerCase().includes('dizi')) ? 'ai-badge-dizi' : 'ai-badge-film';
            const yearText = item.year ? `(${item.year})` : '';
            const targetUrl = `/${item.slug || item.id}`;

            card.innerHTML = `
                <div class="ai-card-top">
                    <h3 class="ai-card-title">${item.name} <span style="font-size: 0.85rem; font-weight: 400; color: #94a3b8;">${yearText}</span></h3>
                    <div class="ai-card-badges">
                        <span class="ai-badge ${typeClass}">${item.type || 'İçerik'}</span>
                    </div>
                </div>
                ${item.category ? `<div class="ai-card-category"><i class="fas fa-tags" style="font-size: 0.75rem; margin-right: 4px;"></i>${item.category}</div>` : ''}
                <div class="ai-card-reason">
                    <strong>💡 Neden Bu Yapım:</strong> ${item.matchReason || 'Bu yapım aradığınız atmosfere tam uyuyor.'}
                </div>
                <a href="${targetUrl}" class="ai-card-action">
                    <span>Hemen İzle</span>
                    <i class="fas fa-arrow-right"></i>
                </a>
            `;

            resultsContainer.appendChild(card);
        });
    }

    function renderError(message) {
        if (!resultsContainer) return;
        resultsContainer.innerHTML = `
            <div class="ai-error-box">
                <i class="fas fa-exclamation-triangle" style="margin-right: 0.4rem;"></i>
                ${message}
            </div>
        `;
    }
}
