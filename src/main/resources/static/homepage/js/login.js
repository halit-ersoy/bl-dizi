// login.js

export function initLogin() {
    // DOM Element Referansları
    const loginBtn = document.querySelector('.login-btn');
    const loginLink = document.getElementById('login-link');
    const loginModal = document.getElementById('login-modal');
    const closeLoginModal = document.getElementById('close-modal');
    const loginSubmit = document.getElementById('login-submit');
    const formInputs = document.querySelectorAll('.form-control');

    // Kullanıcı giriş durumunu kontrol edip profil oluşturma
    // Kullanıcı giriş durumunu kontrol edip profil oluşturma
    async function checkAuthStatus() {
        const authCookie = localStorage.getItem('wdiUserToken');
        const userNickname = localStorage.getItem('wdiUserNickname');

        if (authCookie && userNickname) {
            try {
                const response = await fetch('/api/user/profile');
                if (response.ok) {
                    const data = await response.json();

                    // IF user is found but banned, clear everything and logout
                    if (data.isBanned) {
                        handleLogout();
                        return;
                    }

                    const userId = data.id || data.ID || data.Id;
                    const profileImgUrl = `/media/profile/${userId}?t=${new Date().getTime()}`;
                    const initialLetter = userNickname.charAt(0).toUpperCase();

                    const profileSection = document.createElement('div');
                    profileSection.className = 'profile-section';
                    profileSection.innerHTML = `
                        <button class="profile-btn" aria-label="Profil">
                            <span class="profile-avatar" id="header-profile-avatar">${initialLetter}</span>
                            <span class="profile-name">${userNickname}</span>
                            <i class="fas fa-chevron-down"></i>
                        </button>
                        <div class="profile-dropdown">
                            <a href="/profile"><i class="fas fa-user"></i> Profilim</a>
                            <a href="/favorites"><i class="fas fa-heart"></i> Favorilerim</a>
                            <a href="/settings"><i class="fas fa-cog"></i> Ayarlar</a>
                            <a href="/requests"><i class="fas fa-film"></i> İçerik İstekleri</a>
                            <a href="/history"><i class="fas fa-clock-rotate-left"></i> İzleme Geçmişi</a>
                            <a href="#" id="feedback-open-btn"><i class="fas fa-comment-dots"></i> Geri Bildirim</a>
                            <a href="#" id="logout-btn"><i class="fas fa-sign-out-alt"></i> Çıkış Yap</a>
                        </div>
                    `;
                    loginBtn.parentNode.replaceChild(profileSection, loginBtn);

                    const avatarEl = document.getElementById('header-profile-avatar');
                    avatarEl.style.backgroundImage = `url('${profileImgUrl}')`;
                    avatarEl.style.backgroundSize = 'cover';
                    avatarEl.style.backgroundPosition = 'center';
                    avatarEl.style.color = "transparent";
                    avatarEl.style.backgroundColor = "transparent";

                    const imgTest = new Image();
                    imgTest.onload = () => {
                        avatarEl.style.backgroundImage = `url('${profileImgUrl}')`;
                        avatarEl.style.color = "transparent";
                        avatarEl.style.backgroundColor = "transparent";
                    };
                    imgTest.onerror = () => {
                        avatarEl.style.backgroundImage = 'none';
                        avatarEl.style.color = "white";
                        avatarEl.style.backgroundColor = ""; // Reset to CSS default 
                    };
                    imgTest.src = profileImgUrl;

                    // Messaging icon visibility
                    const messagesWrapper = document.getElementById('messages-wrapper');
                    if (messagesWrapper) {
                        const isMessagingEnabled = data.allowMessages === true || data.allowMessages === 1 || data.allowMessages === "true";
                        messagesWrapper.style.display = isMessagingEnabled ? 'block' : 'none';
                    }

                    const profileBtn = profileSection.querySelector('.profile-btn');
                    const dropdown = profileSection.querySelector('.profile-dropdown');
                    if (profileBtn && dropdown) {
                        profileBtn.addEventListener('click', () => {
                            dropdown.classList.toggle('active');
                        });
                        document.addEventListener('click', (e) => {
                            if (!profileBtn.contains(e.target) && !dropdown.contains(e.target)) {
                                dropdown.classList.remove('active');
                            }
                        });
                    }

                    const logoutBtn = document.getElementById('logout-btn');
                    if (logoutBtn) {
                        logoutBtn.addEventListener('click', (e) => {
                            e.preventDefault();
                            handleLogout();
                        });
                    }
                } else if (response.status === 401 || response.status === 404) {
                    // Cookie invalid or not found in DB
                    handleLogout();
                }
            } catch (error) {
                console.error('Auth verification failed:', error);
            }
        }
    }

    function handleLogout() {
        localStorage.removeItem('wdiUserToken');
        localStorage.removeItem('wdiUserNickname');
        document.cookie = 'wdiAuth=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';

        const messagesWrapper = document.getElementById('messages-wrapper');
        if (messagesWrapper) {
            messagesWrapper.style.display = 'none';
        }

        window.location.reload();
    }

    checkAuthStatus();

    // Login modal açma
    loginBtn.addEventListener('click', (e) => {
        e.preventDefault();
        loginModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        setTimeout(() => {
            const emailInput = document.getElementById('email');
            if (emailInput) emailInput.focus();
        }, 400);
    });

    // Login modal kapatma
    closeLoginModal.addEventListener('click', () => {
        loginModal.classList.remove('active');
        document.body.style.overflow = '';

        // Reset form fields
        document.getElementById('email').value = '';
        document.getElementById('password').value = '';

        // Reset any error styling
        loginSubmit.innerHTML = 'Giriş Yap';
        loginSubmit.style.backgroundColor = '';
        loginSubmit.classList.remove('loading');

        // Reset form field highlighting
        formInputs.forEach(input => {
            input.value = '';
            input.parentElement.classList.remove('active');
        });
    });

    // Form input odaklanma/çıkma işlemleri
    formInputs.forEach(input => {
        input.addEventListener('focus', function () {
            this.parentElement.classList.add('active');
        });
        input.addEventListener('blur', function () {
            if (!this.value) {
                this.parentElement.classList.remove('active');
            }
        });
    });

    // Giriş Yapma İşlemleri (login submit)
    const loginForm = document.getElementById('login-form-element');
    if (loginForm) {
        loginForm.addEventListener('submit', async function (e) {
            e.preventDefault();

            const usernameOrEmailValue = document.getElementById('email').value;
            const passwordValue = document.getElementById('password').value;

            if (!usernameOrEmailValue || !passwordValue) {
                loginSubmit.innerHTML = '<i class="fas fa-times"></i> Tüm alanları doldurun';
                loginSubmit.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    loginSubmit.innerHTML = 'Giriş Yap';
                    loginSubmit.style.backgroundColor = '';
                }, 3000);
                return;
            }

            loginSubmit.classList.add('loading');
            loginSubmit.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
            const loginData = { usernameOrEmail: usernameOrEmailValue, password: passwordValue };

            try {
                const response = await fetch('/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(loginData)
                });

                if (response.status === 429) {
                    window.location.reload();
                    return;
                }

                const data = await response.json();

                // Backend'den gelen 'success' değeri boolean veya 1/0 olabilir
                const isSuccess = data.success === true || data.success === 1 || data.success === "true";

                if (isSuccess) {
                    loginSubmit.classList.remove('loading');
                    loginSubmit.innerHTML = '<i class="fas fa-check"></i> Başarılı';
                    loginSubmit.style.backgroundColor = 'var(--primary-color)';
                    localStorage.setItem('wdiUserToken', data.cookie);
                    localStorage.setItem('wdiUserNickname', data.nickname);

                    setTimeout(() => {
                        loginModal.classList.remove('active');
                        document.body.style.overflow = '';
                        document.getElementById('email').value = '';
                        document.getElementById('password').value = '';
                        window.location.reload();
                    }, 1500);
                } else {
                    loginSubmit.classList.remove('loading');

                    // Clear existing ban reason if any
                    const oldReason = document.getElementById('ban-reason-msg');
                    if (oldReason) oldReason.remove();

                    const isBannedFlag = data.isBanned || String(data.isBanned) === "1" || String(data.isBanned) === "true";
                    const isBanMessage = data.message && (data.message.includes('askıya') || data.message.includes('yasak') || data.message.includes('istesi'));
                    const notWrongCredentials = data.message && !data.message.includes('Hatali') && !data.message.includes('doldurun') && data.message !== 'Hata';

                    if (isBannedFlag || isBanMessage || notWrongCredentials) {
                        loginSubmit.innerHTML = '<i class="fas fa-ban"></i> Yasaklı Kullanıcı';
                        loginSubmit.style.backgroundColor = '#000000';
                        loginSubmit.style.color = '#ffffff';

                        // Provide reason from banReason, or fallback to message
                        const reason = data.banReason || data.message || 'Belirtilmedi';

                        const reasonEl = document.createElement('div');
                        reasonEl.id = 'ban-reason-msg';
                        reasonEl.style.color = '#ff4b4b';
                        reasonEl.style.fontSize = '14px';
                        reasonEl.style.marginTop = '10px';
                        reasonEl.style.fontWeight = '600';
                        reasonEl.style.textAlign = 'center';
                        reasonEl.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Sebep: ${reason}`;
                        loginSubmit.parentNode.insertBefore(reasonEl, loginSubmit.nextSibling);

                        setTimeout(() => {
                            loginSubmit.innerHTML = 'Giriş Yap';
                            loginSubmit.style.backgroundColor = '';
                            loginSubmit.style.color = '';
                            setTimeout(() => {
                                const currentReason = document.getElementById('ban-reason-msg');
                                if (currentReason) currentReason.remove();
                            }, 8000);
                        }, 4000);
                    } else {
                        loginSubmit.innerHTML = '<i class="fas fa-times"></i> Başarısız';
                        loginSubmit.style.backgroundColor = '#e74c3c';
                        setTimeout(() => {
                            loginSubmit.innerHTML = 'Giriş Yap';
                            loginSubmit.style.backgroundColor = '';
                        }, 2000);
                    }
                }
            } catch (error) {
                loginSubmit.classList.remove('loading');
                loginSubmit.innerHTML = '<i class="fas fa-times"></i> Hata';
                loginSubmit.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    loginSubmit.innerHTML = 'Giriş Yap';
                    loginSubmit.style.backgroundColor = '';
                }, 2000);
            }
        });
    }
}
