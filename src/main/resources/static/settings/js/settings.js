import { initHeaderScroll } from '/homepage/js/headerScroll.js';

// Main initialization on DOMContentLoaded
document.addEventListener('DOMContentLoaded', () => {
    const loadingScreen = document.getElementById('loading-screen');
    if (loadingScreen) {
        loadingScreen.style.display = 'none';
    }

    initHeaderScroll();

    setupProfileSection()
        .then((userData) => {
            initSettingsNavigation();
            initPasswordForm();
            initNotificationToggles();
            initMessageToggles(userData.allowMessages);
            setupProfilePhotoUpload(userData);
            initThemeSelector();
        })
        .catch(error => {
            console.error('Initialization error:', error);
        });
});

/**
 * Fetches user profile data and populates settings UI.
 * Returns a Promise that resolves when data is loaded or rejects on error.
 */
async function setupProfileSection() {
    const response = await fetch('/api/user/profile');
    if (!response.ok) {
        if (response.status === 401) {
            window.location.href = '/';
            return;
        }
        throw new Error('Failed to fetch profile data');
    }
    const data = await response.json();

    // Header/Sidebar info
    const avatarEl = document.getElementById('user-avatar');
    // Try to load the user's profile photo
    const userId = data.id || data.ID || data.Id;
    const profileImgUrl = `/media/profile/${userId}?t=${new Date().getTime()}`;

    // Set fallback immediately: an initial letter
    const initialLetter = (data.nickname || '').charAt(0).toUpperCase();
    avatarEl.innerText = initialLetter;

    // Build the image tag to overlay on top, hiding the initial if it loads successfully
    avatarEl.style.backgroundImage = `url('${profileImgUrl}')`;
    avatarEl.style.color = "transparent"; // Hide letter initially
    avatarEl.style.backgroundColor = "transparent"; // Hide background color

    const removePhotoBtn = document.getElementById('remove-photo-btn');

    // Check if image actually loads. If not, fallback to letter color
    const imgTest = new Image();
    imgTest.onload = () => {
        avatarEl.style.backgroundImage = `url('${profileImgUrl}')`;
        avatarEl.style.color = "transparent";
        avatarEl.style.backgroundColor = "transparent";
        if (removePhotoBtn) removePhotoBtn.style.display = 'inline-flex';
    };
    imgTest.onerror = () => {
        avatarEl.style.backgroundImage = 'none';
        avatarEl.style.color = "#000"; // Show letter
        avatarEl.style.backgroundColor = ""; // Default CSS color
        if (removePhotoBtn) removePhotoBtn.style.display = 'none';
    };
    imgTest.src = profileImgUrl;

    document.getElementById('user-fullname').innerText = `${data.name} ${data.surname}`;
    const verifiedBadge = data.isVerified ? ' <i class="fas fa-check-circle" style="color: #1DA1F2; font-size: 0.85rem; margin-left: 4px;" title="Doğrulanmış Hesap"></i>' : '';
    document.getElementById('user-nickname').innerHTML = `@${data.nickname}${verifiedBadge}`;

    document.getElementById('input-name').value = data.name || '';
    document.getElementById('input-surname').value = data.surname || '';
    document.getElementById('input-nickname').value = data.nickname || '';
    document.getElementById('input-email').value = data.email || '';

    // Verification UI
    const verificationContainer = document.getElementById('verification-status-container');
    if (verificationContainer) {
        if (data.isVerified) {
            verificationContainer.innerHTML = '<div class="verification-badge"><i class="fas fa-check-circle"></i> Doğrulanmış Hesap</div>';
        } else {
            verificationContainer.innerHTML = '<button type="button" id="request-verification-btn" class="settings-btn" style="padding: 10px 20px; font-size: 0.8rem; margin: 0; width: auto; border-radius: 12px;">Hesabımı Doğrula</button>';
        }
    }

    initProfileForm();
    initVerification();
    return data;
}

function initProfileForm() {
    const profileForm = document.getElementById('profile-form');
    if (profileForm) {
        profileForm.addEventListener('submit', handleProfileUpdate);
    }
}

async function handleProfileUpdate(e) {
    e.preventDefault();
    const updateBtn = document.getElementById('update-profile-btn');
    const messageDiv = document.getElementById('profile-message');

    const name = document.getElementById('input-name').value.trim();
    const surname = document.getElementById('input-surname').value.trim();
    const nickname = document.getElementById('input-nickname').value.trim();
    const email = document.getElementById('input-email').value.trim();

    if (!name || !surname || !nickname || !email) {
        let missingField = "";
        if (!name) missingField = "Ad";
        else if (!surname) missingField = "Soyad";
        else if (!nickname) missingField = "Kullanıcı Adı";
        else if (!email) missingField = "E-posta";

        showButtonError(updateBtn, `${missingField} eksik!`);
        return;
    }

    const nicknameRegex = /^[a-zA-Z0-9_.]+$/;
    if (!nicknameRegex.test(nickname)) {
        showButtonError(updateBtn, 'Geçersiz Karakter!', 'Profil Bilgilerini Güncelle');
        return;
    }

    const payload = { name, surname, nickname, email };

    try {
        updateBtn.disabled = true;
        updateBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Güncelleniyor...';
        const response = await fetch('/api/user/update-profile', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await response.json();

        if (response.ok && (data.Result || data.success)) {
            updateBtn.innerHTML = '<i class="fas fa-check"></i> Başarılı';
            // Update sidebar info
            document.getElementById('user-fullname').innerText = `${payload.name} ${payload.surname}`;
            // Keep the tick if it was there. We don't have isVerified in payload, 
            // but we can check if it exists in the current innerHTML or re-fetch.
            // Simplified: just check if the icon was there.
            const wasVerified = document.getElementById('user-nickname').querySelector('.fa-check-circle') !== null;
            const verifiedBadge = wasVerified ? ' <i class="fas fa-check-circle" style="color: #1DA1F2; font-size: 0.85rem; margin-left: 4px;" title="Doğrulanmış Hesap"></i>' : '';
            document.getElementById('user-nickname').innerHTML = `@${payload.nickname}${verifiedBadge}`;

            setTimeout(() => {
                updateBtn.disabled = false;
                updateBtn.innerHTML = 'Profil Bilgilerini Güncelle';
                updateBtn.style.backgroundColor = '';
            }, 3000);
        } else {
            showButtonError(updateBtn, data.message || 'Başarısız', 'Profil Bilgilerini Güncelle');
        }
    } catch (error) {
        console.error('Error updating profile:', error);
        showButtonError(updateBtn, 'Hata', 'Profil Bilgilerini Güncelle');
    }
}

/**
 * Initializes the email verification form and modal logic.
 */
function initVerification() {
    const requestBtn = document.getElementById('request-verification-btn');
    const modal = document.getElementById('verification-modal');
    const closeBtn = document.getElementById('close-verification-modal');
    const submitBtn = document.getElementById('submit-verification-btn');
    const input = document.getElementById('verification-code-input');
    const message = document.getElementById('verification-message');

    if (requestBtn) {
        requestBtn.addEventListener('click', async () => {
            const originalText = requestBtn.innerHTML;
            requestBtn.disabled = true;
            requestBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Gönderiliyor...';

            try {
                const response = await fetch('/api/user/request-verification', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });
                const data = await response.json();

                if (response.ok && data.success) {
                    requestBtn.innerHTML = '<i class="fas fa-check"></i> Gönderildi';
                    setTimeout(() => {
                        modal.style.display = 'flex';
                        requestBtn.innerHTML = originalText;
                        requestBtn.disabled = false;
                    }, 1000);
                } else {
                    showButtonError(requestBtn, data.message || 'Başarısız', originalText);
                }
            } catch (error) {
                console.error('Error requesting verification:', error);
                showButtonError(requestBtn, 'Sunucu Hatası', originalText);
            }
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
            if (message) message.style.display = 'none';
            if (input) input.value = '';
        });
    }

    // Close modal on outside click
    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
            if (message) message.style.display = 'none';
            if (input) input.value = '';
        }
    });

    if (submitBtn) {
        submitBtn.addEventListener('click', async () => {
            const code = input.value.trim();
            if (!code || code.length !== 6) {
                showButtonError(submitBtn, '6 haneli kodu girin', 'Doğrula');
                return;
            }

            const originalText = submitBtn.innerHTML;
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Doğrulanıyor...';
            if (message) message.style.display = 'none';

            try {
                const response = await fetch('/api/user/verify-email', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code })
                });
                const data = await response.json();

                if (response.ok && data.success) {
                    submitBtn.innerHTML = '<i class="fas fa-check"></i> Doğrulandı';
                    submitBtn.style.backgroundColor = 'var(--primary-color)';

                    setTimeout(() => {
                        modal.style.display = 'none';
                        // Refresh the UI by fetching profile again, or just reload the page for simplicity
                        window.location.reload();
                    }, 1500);
                } else {
                    showButtonError(submitBtn, data.message || 'Geçersiz kod', originalText);
                }
            } catch (error) {
                console.error('Error verifying code:', error);
                showButtonError(submitBtn, 'Sunucu Hatası', originalText);
            }
        });
    }
}

/**
 * Initializes the settings navigation tabs behavior.
 */
function initSettingsNavigation() {
    const navItems = document.querySelectorAll('.settings-nav li');
    navItems.forEach(item => {
        item.addEventListener('click', () => {
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');

            document.querySelectorAll('.settings-section').forEach(section => section.classList.remove('active'));
            const sectionId = `${item.dataset.section}-section`;
            document.getElementById(sectionId).classList.add('active');
        });
    });
}

/**
 * Sets up password update form handling.
 */
function initPasswordForm() {
    const passwordForm = document.getElementById('password-form');
    if (!passwordForm) {
        console.error('Password form not found');
        return;
    }
    passwordForm.addEventListener('submit', handlePasswordUpdate);
}

/**
 * Handles password update logic with validation and feedback.
 */
async function handlePasswordUpdate(e) {
    e.preventDefault();
    const newPasswordInput = document.getElementById('new-password');
    const confirmPasswordInput = document.getElementById('confirm-password');
    const updateBtn = document.getElementById('update-password-btn');

    const newPassword = newPasswordInput.value.trim();
    const confirmPassword = confirmPasswordInput.value.trim();

    if (!newPassword || !confirmPassword) {
        showButtonError(updateBtn, 'Tüm alanları doldurun');
        return;
    }
    if (newPassword !== confirmPassword) {
        showButtonError(updateBtn, 'Şifreler eşleşmiyor', 'Şifreyi Güncelle');
        return;
    }
    if (newPassword.length < 6) {
        showButtonError(updateBtn, 'Şifre çok kısa', 'Şifreyi Güncelle');
        return;
    }

    try {
        updateBtn.disabled = true;
        updateBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Güncelleniyor...';

        const response = await fetch('/api/user/update-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newPassword })
        });
        const data = await response.json();

        if (response.ok && (data.Result === true || data.Result === 1 || data.success === true)) {
            updateBtn.innerHTML = '<i class="fas fa-check"></i> Başarılı';
            resetPasswordInputs(newPasswordInput, confirmPasswordInput);
        } else {
            showButtonError(updateBtn, data.Message || data.message || 'Başarısız', 'Şifreyi Güncelle');
        }
    } catch (error) {
        console.error('Error updating password:', error);
        showButtonError(updateBtn, 'Hata', 'Şifreyi Güncelle');
    }
}

function showButtonError(button, message, revertText) {
    const original = revertText || button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<i class="fas fa-times"></i> ${message}`;
    button.style.backgroundColor = '#e74c3c';
    setTimeout(() => {
        button.innerHTML = original;
        button.style.backgroundColor = '';
        button.disabled = false;
    }, 3000);
}

function resetPasswordInputs(...inputs) {
    inputs.forEach(i => i.value = '');
    setTimeout(() => {
        const btn = document.getElementById('update-password-btn');
        btn.innerHTML = 'Şifreyi Güncelle';
        btn.style.backgroundColor = 'var(--primary-color)';
        btn.disabled = false;
    }, 2000);
}

/**
 * Initializes notification toggle buttons with permission handling.
 */
function initNotificationToggles() {
    const onBtn = document.getElementById('notifications-on');
    const offBtn = document.getElementById('notifications-off');
    if (!onBtn || !offBtn) {
        console.error('Notification toggle buttons not found');
        return;
    }

    checkNotificationStatus();

    onBtn.addEventListener('click', async () => {
        if (Notification.permission !== 'granted') {
            const perm = await Notification.requestPermission();
            if (perm === 'granted') setNotificationPreference(true);
            else {
                setNotificationPreference(false);
                alert('Bildirim izni reddedildi. Tarayıcı izinlerinden değiştirin.');
            }
        } else {
            setNotificationPreference(true);
        }
    });
    offBtn.addEventListener('click', () => {
        setNotificationPreference(false);
        if (Notification.permission === 'granted') {
            alert('Bildirimler devre dışı; tarayıcı izinleri açık.');
        }
    });
}

function checkNotificationStatus() {
    const pref = (localStorage.getItem('bldiziNotificationsEnabled') || localStorage.getItem('wdiNotificationsEnabled')) === 'true';
    if (!('Notification' in window)) {
        setNotificationUIState(false);
        document.getElementById('notifications-on').disabled = true;
        return;
    }
    setNotificationUIState(Notification.permission === 'granted' && pref);
}

function setNotificationPreference(enabled) {
    localStorage.setItem('bldiziNotificationsEnabled', enabled);
        localStorage.setItem('wdiNotificationsEnabled', enabled);
    setNotificationUIState(enabled);
    // Notification preference saved to local store
}

function setNotificationUIState(enabled) {
    document.getElementById('notifications-on').classList.toggle('active', enabled);
    document.getElementById('notifications-off').classList.toggle('active', !enabled);
}

/**
 * Initializes direct message toggle buttons.
 */
function initMessageToggles(initialState) {
    const onBtn = document.getElementById('messages-on');
    const offBtn = document.getElementById('messages-off');

    if (!onBtn || !offBtn) return;

    setMessageUIState(initialState);

    onBtn.addEventListener('click', () => setMessagePreference(true));
    offBtn.addEventListener('click', () => setMessagePreference(false));
}

async function setMessagePreference(enabled) {
    try {
        const response = await fetch('/api/user/settings/toggle-messages', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ allow: enabled })
        });

        if (response.ok) {
            setMessageUIState(enabled);
        } else {
            alert('Ayarlar güncellenirken bir hata oluştu.');
        }
    } catch (error) {
        console.error('Error toggling messages:', error);
    }
}

function setMessageUIState(enabled) {
    // Backend returns bit/boolean, sometimes 1/0
    const isEnabled = enabled === true || enabled === 1 || enabled === "true";

    const onBtn = document.getElementById('messages-on');
    const offBtn = document.getElementById('messages-off');

    if (onBtn) onBtn.classList.toggle('active', isEnabled);
    if (offBtn) offBtn.classList.toggle('active', !isEnabled);

    // Sync header icon visibility
    const messagesWrapper = document.getElementById('messages-wrapper');
    if (messagesWrapper) {
        messagesWrapper.style.display = isEnabled ? 'block' : 'none';
    }
}

/**
 * Handles profile photo upload and Cropper.js logic.
 */
function setupProfilePhotoUpload(userData) {
    const avatarWrapper = document.querySelector('.settings-avatar-wrapper');
    const fileInput = document.getElementById('profile-photo-input');
    const cropModal = document.getElementById('crop-modal');
    const closeCropModal = document.getElementById('close-crop-modal');
    const cropImage = document.getElementById('crop-image');
    const saveCropBtn = document.getElementById('save-crop-btn');
    const cropMessage = document.getElementById('crop-message');
    const userAvatar = document.getElementById('user-avatar');

    let cropper = null;

    if (!avatarWrapper || !fileInput) return;

    // Trigger file selection
    avatarWrapper.addEventListener('click', () => {
        fileInput.click();
    });

    // Handle file selection
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;

        // Reset Cropper if exists
        if (cropper) {
            cropper.destroy();
            cropper = null;
        }

        // Validate type
        if (!file.type.startsWith('image/')) {
            alert('Lütfen geçerli bir resim dosyası seçin.');
            return;
        }

        // Show modal and initialize Cropper
        const reader = new FileReader();
        reader.onload = (event) => {
            cropImage.src = event.target.result;
            cropModal.style.display = 'flex';
            cropMessage.style.display = 'none';

            // Need to wait for image to load before cropping
            cropImage.onload = () => {
                cropper = new Cropper(cropImage, {
                    aspectRatio: 1, // Square
                    viewMode: 1, // Restrict crop box to not exceed canvas
                    dragMode: 'move',
                    autoCropArea: 1,
                    restore: false,
                    guides: false,
                    center: false,
                    highlight: false,
                    cropBoxMovable: false,
                    cropBoxResizable: false,
                    toggleDragModeOnDblclick: false,
                });
            };
        };
        reader.readAsDataURL(file);

        // Reset input so the same file can be selected again if needed
        fileInput.value = '';
    });

    // Close Modal
    const closeModalFunc = () => {
        cropModal.style.display = 'none';
        if (cropper) {
            cropper.destroy();
            cropper = null;
        }
    };

    if (closeCropModal) {
        closeCropModal.addEventListener('click', closeModalFunc);
    }

    window.addEventListener('click', (e) => {
        if (e.target === cropModal) {
            closeModalFunc();
        }
    });

    // Save and Upload Cropped Image
    if (saveCropBtn) {
        saveCropBtn.addEventListener('click', async () => {
            if (!cropper) return;

            const originalText = saveCropBtn.innerHTML;
            saveCropBtn.disabled = true;
            saveCropBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Yükleniyor...';
            cropMessage.style.display = 'none';

            try {
                // Get cropped canvas
                const canvas = cropper.getCroppedCanvas({
                    imageSmoothingEnabled: true,
                    imageSmoothingQuality: 'high',
                });

                if (!canvas) {
                    throw new Error('Canvas oluşturulamadı.');
                }

                // Convert canvas to Blob
                canvas.toBlob(async (blob) => {
                    if (!blob) {
                        showButtonError(saveCropBtn, 'Hata', originalText);
                        return;
                    }

                    const formData = new FormData();
                    formData.append('file', blob, 'profile.jpg');

                    try {
                        const response = await fetch('/api/user/upload-profile-photo', {
                            method: 'POST',
                            body: formData
                        });

                        const data = await response.json();

                        if (response.ok && data.success) {
                            saveCropBtn.innerHTML = '<i class="fas fa-check"></i> Başarılı';

                            // Load new avatar immediately
                            userAvatar.style.backgroundImage = `url('${data.imageUrl}')`;
                            userAvatar.style.color = "transparent";
                            const removeBtn = document.getElementById('remove-photo-btn');
                            if (removeBtn) removeBtn.style.display = 'inline-flex';

                            setTimeout(() => {
                                closeModalFunc();
                                saveCropBtn.disabled = false;
                                saveCropBtn.innerHTML = originalText;
                            }, 1000);
                        } else {
                            cropMessage.innerText = data.message || 'Yükleme başarısız.';
                            cropMessage.style.display = 'block';
                            cropMessage.style.color = '#ff4d4d'; // error red
                            saveCropBtn.disabled = false;
                            saveCropBtn.innerHTML = originalText;
                        }
                    } catch (uploadError) {
                        console.error('Upload Error:', uploadError);
                        cropMessage.innerText = 'Sunucuyla iletişim kurulamadı.';
                        cropMessage.style.display = 'block';
                        cropMessage.style.color = '#ff4d4d';
                        saveCropBtn.disabled = false;
                        saveCropBtn.innerHTML = originalText;
                    }
                }, 'image/jpeg', 0.9); // 90% quality JPEG
            } catch (error) {
                console.error('Crop Error:', error);
                showButtonError(saveCropBtn, 'Hata', originalText);
            }
        });
    }

    const removePhotoBtn = document.getElementById('remove-photo-btn');
    if (removePhotoBtn) {
        removePhotoBtn.addEventListener('click', async () => {
            if (!confirm('Profil fotoğrafınızı kaldırmak istediğinize emin misiniz?')) return;

            const originalText = removePhotoBtn.innerHTML;
            removePhotoBtn.disabled = true;
            removePhotoBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Kaldırılıyor...';

            try {
                const response = await fetch('/api/user/remove-profile-photo', {
                    method: 'DELETE'
                });
                const responseData = await response.json();

                if (response.ok && responseData.success) {
                    removePhotoBtn.style.display = 'none';
                    // Re-render avatar fallback
                    userAvatar.style.backgroundImage = 'none';
                    userAvatar.style.color = '#000';
                    userAvatar.style.backgroundColor = '';

                    // Trigger a custom event or let the user refresh to see changes globally
                    // A simple window reload does the trick to refresh header too
                    setTimeout(() => window.location.reload(), 500);
                } else {
                    showButtonError(removePhotoBtn, 'Hata', originalText);
                }
            } catch (error) {
                console.error('Remove Photo Error:', error);
                showButtonError(removePhotoBtn, 'Hata', originalText);
            }
        });
    }
}

/**
 * Initializes the theme selector buttons.
 */
function initThemeSelector() {
    const themeBtns = document.querySelectorAll('.theme-btn');
    if (themeBtns.length === 0) return;

    // Get current theme from localStorage or default to green
    const currentTheme = localStorage.getItem('bldizi-theme') || localStorage.getItem('wdi-theme') || 'pink';

    // Set initial active state
    themeBtns.forEach(btn => {
        if (btn.dataset.themeValue === currentTheme) {
            btn.classList.add('active');
        }

        btn.addEventListener('click', () => {
            const selectedTheme = btn.dataset.themeValue;

            // Remove active class from all buttons
            themeBtns.forEach(b => b.classList.remove('active'));
            // Add active class to clicked button
            btn.classList.add('active');

            // Save to localStorage
            localStorage.setItem('bldizi-theme', selectedTheme);
            localStorage.setItem('wdi-theme', selectedTheme);

            // Apply immediately to current document
            if (selectedTheme === 'green') {
                document.documentElement.removeAttribute('data-theme');
            } else {
                document.documentElement.setAttribute('data-theme', selectedTheme);
            }
        });
    });
}
