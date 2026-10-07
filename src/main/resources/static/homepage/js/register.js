// register.js

export function initRegister() {
    // DOM Element References
    const registerLink = document.querySelector('.register-link a');
    const loginLink = document.getElementById('login-link');
    const registerModal = document.getElementById('register-modal');
    const loginModal = document.getElementById('login-modal');
    const closeRegisterModal = document.getElementById('close-register-modal');
    const registerSubmit = document.getElementById('register-submit');
    const formInputs = document.querySelectorAll('.form-control');

    // Register link (open register modal)
    registerLink.addEventListener('click', (e) => {
        e.preventDefault();
        loginModal.classList.remove('active');
        registerModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        setTimeout(() => {
            const firstNameInput = document.getElementById('first-name');
            if (firstNameInput) firstNameInput.focus();
        }, 400);
    });

    // Login link (from register modal)
    loginLink.addEventListener('click', (e) => {
        e.preventDefault();
        registerModal.classList.remove('active');
        loginModal.classList.add('active');
    });

    // Close register modal
    closeRegisterModal.addEventListener('click', () => {
        registerModal.classList.remove('active');
        document.body.style.overflow = '';
        clearErrors();

        // Reset all form fields
        document.getElementById('first-name').value = '';
        document.getElementById('last-name').value = '';
        document.getElementById('nickname').value = '';
        document.getElementById('register-email').value = '';
        document.getElementById('register-password').value = '';

        // Reset any error styling
        registerSubmit.innerHTML = 'Kayıt Ol';
        registerSubmit.style.backgroundColor = '';
        registerSubmit.classList.remove('loading');

        // Reset form field highlighting
        document.querySelectorAll('#register-modal .form-control').forEach(input => {
            input.value = '';
            input.parentElement.classList.remove('active');
        });
    });

    // Form input focus/blur handling
    formInputs.forEach(input => {
        input.addEventListener('focus', function () {
            this.parentElement.classList.add('active');
        });
        input.addEventListener('blur', function () {
            if (!this.value) {
                this.parentElement.classList.remove('active');
            }
        });
        input.addEventListener('input', function () {
            this.parentElement.classList.remove('has-error');
        });
    });

    function setError(inputElementId) {
        const element = document.getElementById(inputElementId);
        if (element && element.parentElement) {
            element.parentElement.classList.add('has-error');
        }
    }

    function clearErrors() {
        document.querySelectorAll('.form-group.has-error').forEach(group => {
            group.classList.remove('has-error');
        });
    }

    // Registration submission
    registerSubmit.addEventListener('click', async function () {
        clearErrors();
        const firstName = document.getElementById('first-name').value.trim();
        const lastName = document.getElementById('last-name').value.trim();
        const nickname = document.getElementById('nickname').value.trim();
        const email = document.getElementById('register-email').value.trim();
        const password = document.getElementById('register-password').value.trim();

        if (!firstName || !lastName || !nickname || !email || !password) {
            let missingField = "";
            let missingId = "";
            if (!firstName) { missingField = "Ad"; missingId = "first-name"; }
            else if (!lastName) { missingField = "Soyad"; missingId = "last-name"; }
            else if (!nickname) { missingField = "Kullanıcı Adı"; missingId = "nickname"; }
            else if (!email) { missingField = "E-posta"; missingId = "register-email"; }
            else if (!password) { missingField = "Şifre"; missingId = "register-password"; }

            setError(missingId);
            this.innerHTML = `<i class="fas fa-times"></i> ${missingField} eksik!`;
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Kayıt Ol';
                this.style.backgroundColor = '';
            }, 3000);
            return;
        }

        if (password.length < 6) {
            setError('register-password');
            this.innerHTML = '<i class="fas fa-times"></i> Şifre çok kısa! (En az 6)';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Kayıt Ol';
                this.style.backgroundColor = '';
            }, 3000);
            return;
        }

        const nicknameRegex = /^[a-zA-Z0-9_.]+$/;
        if (!nicknameRegex.test(nickname)) {
            setError('nickname');
            this.innerHTML = '<i class="fas fa-times"></i> Sadece harf, rakam, _ ve .';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Kayıt Ol';
                this.style.backgroundColor = '';
            }, 3000);
            return;
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            setError('register-email');
            this.innerHTML = '<i class="fas fa-times"></i> Geçersiz E-posta!';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Kayıt Ol';
                this.style.backgroundColor = '';
            }, 3000);
            return;
        }

        this.classList.add('loading');
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        const registerData = {
            name: firstName,
            surname: lastName,
            nickname,
            email,
            password
        };

        try {
            const response = await fetch('/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(registerData)
            });
            const data = await response.json();

            if (response.ok) {
                this.classList.remove('loading');
                this.innerHTML = '<i class="fas fa-check"></i> Başarılı';
                this.style.backgroundColor = 'var(--primary-color)';

                if (data.cookie) {
                    localStorage.setItem('wdiUserToken', data.cookie);
                    localStorage.setItem('wdiUserNickname', nickname);
                    const expiryDate = new Date();
                    expiryDate.setDate(expiryDate.getDate() + 30);
                    document.cookie = `wdiAuth=${data.cookie}; expires=${expiryDate.toUTCString()}; path=/`;
                }

                setTimeout(() => {
                    registerModal.classList.remove('active');
                    document.body.style.overflow = '';
                    window.location.reload();
                }, 1500);
            } else {
                this.classList.remove('loading');
                this.innerHTML = '<i class="fas fa-times"></i> ' + (data.message || data.error || 'Kayıt başarısız');
                this.style.backgroundColor = '#e74c3c';
                setTimeout(() => {
                    this.innerHTML = 'Kayıt Ol';
                    this.style.backgroundColor = '';
                }, 3000);
            }
        } catch (error) {
            this.classList.remove('loading');
            this.innerHTML = '<i class="fas fa-times"></i> Hata';
            this.style.backgroundColor = '#e74c3c';
            setTimeout(() => {
                this.innerHTML = 'Kayıt Ol';
                this.style.backgroundColor = '';
            }, 3000);
        }
    });
}