(function () {
    'use strict';

    // Anti-Inspect Header
    console.log("%cDUR! %cBu alan geliştiriciler için özeldir.", "color: red; font-size: 50px; font-weight: bold; -webkit-text-stroke: 1px black;", "color: yellow; font-size: 20px;");

    const protection = {
        overlay: null,
        detected: false,

        init() {
            this.createOverlay();
            this.disableShortcuts();
            this.disableContextMenu();

            // Periyodik kontrol ve 'debugger' zamanlayıcısı Smart TV ve Mobil
            // cihazlarda takılmalar nedeniyle asılsız uyarılar (false positive) ürettiği için kaldırıldı.
            // Artık sadece tuş kombinasyonları (F12 vs.) dinleniyor.
        },

        createOverlay() {
            this.overlay = document.createElement('div');
            this.overlay.id = 'security-warning-overlay';
            this.overlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: #000;
                color: white;
                display: none;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                z-index: 2147483647;
                font-family: 'Poppins', 'Montserrat', sans-serif;
                text-align: center;
            `;

            this.overlay.innerHTML = `
                <div style="padding: 60px 40px; border: 2px solid #ff4d1c; border-radius: 40px; background: #0c0c0c; box-shadow: 0 0 50px rgba(255, 77, 28, 0.2); width: 90%; max-width: 550px; display: flex; flex-direction: column; align-items: center;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 100px; color: #ff4d1c; margin-bottom: 40px;"></i>
                    <h2 style="font-size: 28px; margin-bottom: 15px; color: #ff4d1c; font-weight: 800; letter-spacing: 1.5px; text-transform: uppercase;">NE YAPMAYA ÇALIŞIYORSUN?</h2>
                    <h3 style="font-size: 22px; margin-bottom: 25px; color: #fff; font-weight: 700;">Sistem güvenliği ihlal edildi!</h3>
                    <p style="font-size: 16px; line-height: 1.8; color: #bbb; max-width: 450px; margin-bottom: 0;">
                        İnceleme (DevTools) kullanımı bu sitede yasaktır.
                    </p>
                    <p style="font-size: 16px; line-height: 1.8; color: #bbb; max-width: 450px; margin-bottom: 0;">
                        Tüm aktiviteleriniz kayıt altına alınmaktadır.
                    </p>
                    <p style="font-size: 16px; line-height: 1.8; color: #bbb; max-width: 450px; margin-bottom: 40px;">
                        Lütfen derhal geliştirici araçlarını kapatın ve sayfayı yenileyin.
                    </p>
                    <button onclick="window.location.reload()" style="background: #ff4d1c; color: white; border: none; border-radius: 12px; padding: 18px 50px; cursor: pointer; font-weight: 700; font-size: 16px; letter-spacing: 1px; box-shadow: 0 10px 20px rgba(255, 77, 28, 0.3); transition: transform 0.2s;">SAYFAYI YENİLE</button>
                </div>
            `;

            document.body.appendChild(this.overlay);
        },

        showWarning() {
            if (this.detected) return;
            this.detected = true;
            this.overlay.style.display = 'flex';
            document.body.style.overflow = 'hidden';

            // Report violation to backend
            fetch('/api/security/report', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    pageUrl: window.location.href
                })
            }).catch(err => console.error("Violation report failed:", err));

            // Log attack attempt (optional)
            console.warn("DevTools attempt detected!");
        },

        disableShortcuts() {
            window.addEventListener('keydown', (e) => {
                // F12
                if (e.key === 'F12' || e.keyCode === 123) {
                    e.preventDefault();
                    this.showWarning();
                    return false;
                }

                const isCmdOrCtrl = e.ctrlKey || e.metaKey;

                // Windows/Linux (Ctrl+Shift+I/J/C) veya Mac (Cmd+Option+I/J/C) 
                if ((isCmdOrCtrl && e.shiftKey && (e.keyCode === 73 || e.keyCode === 74 || e.keyCode === 67)) ||
                    (e.metaKey && e.altKey && (e.keyCode === 73 || e.keyCode === 74 || e.keyCode === 67))) {
                    e.preventDefault();
                    this.showWarning();
                    return false;
                }
                // Ctrl+U veya Mac (Cmd+Option+U) (Kaynağı Görüntüle)
                if ((isCmdOrCtrl && e.keyCode === 85) || (e.metaKey && e.altKey && e.keyCode === 85)) {
                    e.preventDefault();
                    this.showWarning();
                    return false;
                }
            });
        },

        disableContextMenu() {
            window.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                return false;
            });
        },

    };

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => protection.init());
    } else {
        protection.init();
    }

})();
