// forgot-pass.js

export function initForgotPass() {
    // DOM Element Referansları
    const forgotPasswordLink = document.querySelector('.forgot-password a');
    const forgotPasswordModal = document.getElementById('forgot-password-modal');
    const closeForgotPasswordModal = document.getElementById('close-forgot-modal');
    const forgotPasswordSubmit = document.getElementById('forgot-password-submit');
    const forgotPasswordForm = document.querySelector('#forgot-password-modal .login-form');
    const loginModal = document.getElementById('login-modal');

    // Create sections for verification and password reset
    const verificationSection = document.createElement('div');
    const passwordResetSection = document.createElement('div');

    let currentEmail = '';
    let currentCode = '';

    // Check if elements exist
    if (!forgotPasswordLink || !forgotPasswordModal || !closeForgotPasswordModal || !forgotPasswordSubmit || !forgotPasswordForm) {
        console.error('Forgot Password: Gerekli elementler bulunamadı.');
        return;
    }

    // Complete form reset function
    function resetForgotPasswordForm() {
        // Reset input fields
        const forgotEmailInput = document.getElementById('forgot-email');
        if (forgotEmailInput) {
            forgotEmailInput.value = '';
            forgotEmailInput.readOnly = false;
        }
        const verificationInput = document.getElementById('verification-code');
        if (verificationInput) verificationInput.value = '';
        const newPasswordInput = document.getElementById('new-password');
        if (newPasswordInput) newPasswordInput.value = '';
        const confirmPasswordInput = document.getElementById('confirm-password');
        if (confirmPasswordInput) confirmPasswordInput.value = '';

        // Reset form state and buttons
        forgotPasswordSubmit.style.display = 'inline-block';
        forgotPasswordSubmit.innerHTML = 'Şifremi Sıfırla';
        forgotPasswordSubmit.style.backgroundColor = '';
        forgotPasswordSubmit.classList.remove('loading');

        // Hide additional sections
        verificationSection.style.display = 'none';
        passwordResetSection.style.display = 'none';

        // Remove any notification elements
        const notifications = forgotPasswordForm.querySelectorAll('.email-notification, .password-success-message');
        notifications.forEach(element => element.remove());

        // Reset state variables
        currentEmail = '';
        currentCode = '';

        // Reset form field highlighting
        const inputs = forgotPasswordModal.querySelectorAll('.form-control');
        inputs.forEach(input => {
            input.parentElement.classList.remove('active');
        });

        // Ensure the login link is always at the bottom of the form
        const loginLink = forgotPasswordForm.querySelector('.register-link.login-link');
        if (loginLink) {
            forgotPasswordForm.appendChild(loginLink);
        }
    }

    // Remove the login link from its current position (we'll add it back at the end)
    const existingLoginLink = document.querySelector('#forgot-password-modal .register-link.login-link');
    if (existingLoginLink) {
        existingLoginLink.remove();
    }

    // Modal açma işlemleri
    forgotPasswordLink.addEventListener('click', (e) => {
        e.preventDefault();
        resetForgotPasswordForm(); // Her açılışta form sıfırlansın
        if (loginModal) loginModal.classList.remove('active');
        forgotPasswordModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        setTimeout(() => {
            const forgotEmailInput = document.getElementById('forgot-email');
            if (forgotEmailInput) forgotEmailInput.focus();
        }, 400);
    });

    // Modal kapatma işlemleri
    closeForgotPasswordModal.addEventListener('click', () => {
        forgotPasswordModal.classList.remove('active');
        document.body.style.overflow = '';
        resetForgotPasswordForm();
    });
    forgotPasswordModal.addEventListener('click', (e) => {
        if (e.target === forgotPasswordModal) {
            forgotPasswordModal.classList.remove('active');
            document.body.style.overflow = '';
            resetForgotPasswordForm();
        }
    });

    // Dinamik bölümlerin oluşturulması

    // Verification Section
    verificationSection.className = 'verification-section';
    verificationSection.style.display = 'none';
    verificationSection.innerHTML = `
        <div class="form-group">
            <label for="verification-code">Doğrulama Kodu</label>
            <input type="text" class="form-control" id="verification-code" placeholder="6 haneli kodu girin">
            <span class="highlight"></span>
        </div>
        <button class="login-btn-page" id="verify-code-submit">Kodu Doğrula</button>
    `;
    forgotPasswordForm.appendChild(verificationSection);

    // Password Reset Section
    passwordResetSection.className = 'password-reset-section';
    passwordResetSection.style.display = 'none';
    passwordResetSection.innerHTML = `
        <div class="form-group">
            <label for="new-password">Yeni Şifre</label>
            <input type="password" class="form-control" id="new-password" placeholder="Yeni şifrenizi giriniz">
        </div>
        <div class="form-group">
            <label for="confirm-password">Şifre Tekrar</label>
            <input type="password" class="form-control" id="confirm-password" placeholder="Şifrenizi tekrar giriniz">
        </div>
        <button class="login-btn-page" id="reset-password-submit">Şifremi Güncelle</button>
    `;
    forgotPasswordForm.appendChild(passwordResetSection);

    // Add back the login link at the end of the form
    const newLoginLinkContainer = document.createElement('div');
    newLoginLinkContainer.className = 'register-link login-link';
    newLoginLinkContainer.innerHTML = 'Şifrenizi hatırladınız mı? <a href="#" id="back-to-login">Giriş Yap</a>';
    forgotPasswordForm.appendChild(newLoginLinkContainer);

    // Login link tıklama işlemi
    document.body.addEventListener('click', function (e) {
        if (e.target && e.target.id === 'back-to-login') {
            e.preventDefault();
            forgotPasswordModal.classList.remove('active');
            if (loginModal) loginModal.classList.add('active');
        }
    });

    // Şifremi Sıfırla (E-posta gönderme) butonu
    forgotPasswordSubmit.addEventListener('click', async function () {
        const userInput = document.getElementById('forgot-email').value.trim();
        if (!userInput) {
            this.innerHTML = '<i class="fas fa-times"></i> E-posta gerekli';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Şifremi Sıfırla';
                this.style.backgroundColor = '';
            }, 3000);
            return;
        }
        this.classList.add('loading');
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        try {
            const response = await fetch('/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ usernameOrEmail: userInput })
            });
            const data = await response.json();
            if (data.success) {
                this.classList.remove('loading');
                this.innerHTML = '<i class="fas fa-check"></i> E-posta Gönderildi';
                this.style.backgroundColor = 'var(--primary-color)';
                currentEmail = userInput;
                document.getElementById('forgot-email').readOnly = true;
                verificationSection.style.display = 'block';
                forgotPasswordSubmit.style.display = 'none';

                // E-posta gönderildi bildirimi
                const emailNotification = document.createElement('div');
                emailNotification.className = 'email-notification';
                emailNotification.innerHTML =
                    `<div class="email-icon"><i class="fas fa-envelope"></i></div>
                     <div class="email-text">Doğrulama kodu e-posta adresinize gönderildi. Lütfen gelen kutunuzu kontrol edin.</div>`;
                emailNotification.style.padding = '15px';
                emailNotification.style.margin = '15px 0';
                emailNotification.style.backgroundColor = 'rgba(var(--primary-rgb), 0.1)';
                emailNotification.style.borderRadius = '8px';
                emailNotification.style.display = 'flex';
                emailNotification.style.alignItems = 'center';
                const emailIcon = emailNotification.querySelector('.email-icon');
                emailIcon.style.fontSize = '24px';
                emailIcon.style.color = 'var(--primary-color)';
                emailIcon.style.marginRight = '15px';
                forgotPasswordForm.appendChild(emailNotification);
            } else {
                this.classList.remove('loading');
                this.innerHTML = '<i class="fas fa-times"></i> ' + (data.message || 'E-posta gönderme başarısız oldu');
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Şifremi Sıfırla';
                    this.style.backgroundColor = '';
                }, 3000);
            }
        } catch (error) {
            this.classList.remove('loading');
            this.innerHTML = '<i class="fas fa-times"></i> Bir hata oluştu';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Şifremi Sıfırla';
                this.style.backgroundColor = '';
            }, 3000);
        }
    });

    // Reset Password (şifre güncelleme) butonu event listener'ı
    const resetPasswordSubmitBtn = document.getElementById('reset-password-submit');
    if (resetPasswordSubmitBtn) {
        resetPasswordSubmitBtn.addEventListener('click', async function () {
            const newPassword = document.getElementById('new-password').value.trim();
            const confirmPassword = document.getElementById('confirm-password').value.trim();

            if (!newPassword || !confirmPassword) {
                this.innerHTML = '<i class="fas fa-times"></i> Tüm alanlar gerekli';
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Şifremi Güncelle';
                    this.style.backgroundColor = '';
                }, 3000);
                return;
            }
            if (newPassword !== confirmPassword) {
                this.innerHTML = '<i class="fas fa-times"></i> Şifreler eşleşmiyor';
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Şifremi Güncelle';
                    this.style.backgroundColor = '';
                }, 3000);
                return;
            }

            if (newPassword.length < 6) {
                this.innerHTML = '<i class="fas fa-times"></i> Şifre çok kısa! (En az 6)';
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Şifremi Güncelle';
                    this.style.backgroundColor = '';
                }, 3000);
                return;
            }

            this.classList.add('loading');
            this.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

            try {
                const response = await fetch('/reset-password', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        usernameOrEmail: currentEmail,
                        code: currentCode,
                        newPassword: newPassword
                    })
                });
                const data = await response.json();

                if (data.success) {
                    this.innerHTML = '<i class="fas fa-check"></i> Şifre güncellendi';
                    this.style.backgroundColor = 'var(--primary-color)';

                    // Başarılı mesaj overlay'ı
                    const successMessage = document.createElement('div');
                    successMessage.className = 'password-success-message';
                    successMessage.innerHTML = `
                        <div class="success-icon">
                            <i class="fas fa-check-circle"></i>
                        </div>
                        <div class="success-text">Şifreniz başarıyla güncellendi!</div>
                    `;
                    successMessage.style.position = 'absolute';
                    successMessage.style.top = '0';
                    successMessage.style.left = '0';
                    successMessage.style.width = '100%';
                    successMessage.style.height = '100%';
                    successMessage.style.display = 'flex';
                    successMessage.style.flexDirection = 'column';
                    successMessage.style.alignItems = 'center';
                    successMessage.style.justifyContent = 'center';
                    successMessage.style.background = 'rgba(0,0,0,0.9)';
                    successMessage.style.zIndex = '100';
                    successMessage.style.borderRadius = '16px';
                    successMessage.style.animation = 'fadeIn 0.5s ease-in-out';
                    const successIcon = successMessage.querySelector('.success-icon');
                    successIcon.style.fontSize = '60px';
                    successIcon.style.color = 'var(--primary-color)';
                    successIcon.style.marginBottom = '20px';
                    successIcon.style.animation = 'popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards';
                    const successText = successMessage.querySelector('.success-text');
                    successText.style.color = '#ffffff';
                    successText.style.fontSize = '18px';
                    successText.style.fontWeight = 'bold';

                    const loginModalContent = document.querySelector('#forgot-password-modal .login-modal');
                    if (loginModalContent) {
                        loginModalContent.appendChild(successMessage);
                    }
                    const style = document.createElement('style');
                    style.textContent = `
                        @keyframes fadeIn {
                            from { opacity: 0; }
                            to { opacity: 1; }
                        }
                        @keyframes popIn {
                            0% { transform: scale(0); }
                            70% { transform: scale(1.2); }
                            100% { transform: scale(1); }
                        }
                    `;
                    document.head.appendChild(style);

                    // Complete reset and close the modal after showing success message
                    setTimeout(() => {
                        forgotPasswordModal.classList.remove('active');
                        document.body.style.overflow = '';
                        window.location.reload(); // Reload the page instead of just resetting the form
                    }, 2000);
                } else {
                    this.classList.remove('loading');
                    this.innerHTML = '<i class="fas fa-times"></i> ' + data.message;
                    this.style.backgroundColor = '#e74c3c';
                    setTimeout(() => {
                        this.innerHTML = 'Şifremi Güncelle';
                        this.style.backgroundColor = '';
                    }, 3000);
                }
            } catch (error) {
                this.classList.remove('loading');
                this.innerHTML = '<i class="fas fa-times"></i> Bir hata oluştu';
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Şifremi Güncelle';
                    this.style.backgroundColor = '';
                }, 3000);
            }
        });
    }

    // Verification code submit event listener (document.body üzerinden)
    document.body.addEventListener('click', function (e) {
        if (e.target && e.target.id === 'verify-code-submit') {
            const verifyButton = e.target;
            verifyButton.classList.add('loading');
            verifyButton.innerHTML = '';

            const code = document.getElementById('verification-code').value.trim();
            if (!code) {
                verifyButton.classList.remove('loading');
                verifyButton.innerHTML = '<i class="fas fa-times"></i> Kod boş olamaz';
                verifyButton.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    verifyButton.innerHTML = 'Kodu Doğrula';
                    verifyButton.style.backgroundColor = '';
                }, 3000);
                return;
            }

            const forgotEmailInput = document.getElementById('forgot-email');
            const userInput = forgotEmailInput ? forgotEmailInput.value.trim() : '';

            fetch('/verify-code', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    usernameOrEmail: userInput,
                    code: code
                })
            })
                .then(response => response.json())
                .then(data => {
                    verifyButton.classList.remove('loading');

                    if (data.success) {
                        currentCode = code;
                        verifyButton.innerHTML = '<i class="fas fa-check"></i> Doğrulandı';
                        verifyButton.style.backgroundColor = 'var(--primary-color)';

                        verificationSection.style.display = 'none';
                        if (passwordResetSection) {
                            passwordResetSection.style.display = 'block';
                        }
                    } else {
                        verifyButton.innerHTML = '<i class="fas fa-times"></i> ' + (data.message || 'Geçersiz kod');
                        verifyButton.style.backgroundColor = '#e74c3c';
                        setTimeout(() => {
                            verifyButton.innerHTML = 'Kodu Doğrula';
                            verifyButton.style.backgroundColor = '';
                        }, 3000);
                    }
                })
                .catch(error => {
                    console.error('Verification error:', error);
                    verifyButton.classList.remove('loading');
                    verifyButton.innerHTML = '<i class="fas fa-times"></i> Bir hata oluştu';
                    verifyButton.style.backgroundColor = '#e74c3c';
                    setTimeout(() => {
                        verifyButton.innerHTML = 'Kodu Doğrula';
                        verifyButton.style.backgroundColor = '';
                    }, 3000);
                });
        }
    });
}
