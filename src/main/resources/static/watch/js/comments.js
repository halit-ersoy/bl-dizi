// comments.js
import { isUserLoggedIn } from '../../elements/userLogged.js';
import { showReportModal } from '../../elements/premium-modals.js';
import { escapeHtml } from '../../elements/sanitize.js';

export function initCommentsSection(videoId) {
    const commentForm = document.getElementById('commentForm');
    const commentsList = document.getElementById('comments-list');
    const commentText = document.getElementById('commentText');

    if (!commentsList) return;

    checkAuthentication();
    setupCharCounter();
    commentForm?.addEventListener('submit', (e) => onSubmitComment(e));
    loadComments();

    function checkAuthentication() {
        if (!isUserLoggedIn()) {
            const formContainer = document.querySelector('.comment-form');
            if (formContainer) {
                formContainer.classList.add('hidden');

                const warning = document.createElement('div');
                warning.className = 'login-warning';
                warning.innerHTML = `
                    <i class="fas fa-lock"></i>
                    <p>Yorum yapabilmek için giriş yapmanız gerekmektedir.</p>
                `;

                formContainer.parentNode.insertBefore(warning, formContainer);
            }
        }
    }


    function setupCharCounter() {
        if (!commentText) return;
        const counter = document.createElement('div');
        counter.className = 'char-counter';
        commentText.parentNode.appendChild(counter);

        commentText.addEventListener('input', () => {
            const len = commentText.value.length;
            counter.textContent = `${len}/500`;
            counter.style.color = len > 500 ? '#ff3860' : '#888';
        });

        commentText.addEventListener('focus', () => {
            if (!localStorage.getItem('bldiziUserToken') && !localStorage.getItem('wdiUserToken') && !document.cookie.includes('wdiAuth')) {
                commentText.blur();
                commentText.style.borderColor = '#ff3860';
                commentText.placeholder = 'Oturum açmalısınız!';
                setTimeout(() => commentText.placeholder = 'Düşüncelerinizi paylaşın...', 2000);
            }
        });

        commentText.addEventListener('blur', () => {
            commentText.placeholder = 'Bu dizi hakkında ne düşünüyorsunuz?';
        });
    }

    async function onSubmitComment(e, parentId = null) {
        if (e) e.preventDefault();

        const input = parentId
            ? document.getElementById(`reply-input-${parentId}`)
            : commentText;

        const text = input?.value.trim() || '';

        if (!text || text.length > 500) {
            if (input) {
                input.style.borderColor = '#ff3860';
                setTimeout(() => input.style.borderColor = '', 1000);
            }
            return;
        }

        try {
            const spoilerOn = parentId
                ? document.getElementById(`reply-spoiler-${parentId}`)?.checked
                : document.getElementById('spoilerCheck')?.checked;

            let url = `/api/video/comment?id=${videoId}&spoiler=${!!spoilerOn}`;
            if (parentId) url += `&parentId=${parentId}`;

            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                credentials: 'include',
                body: text
            });
            if (!res.ok) throw new Error();

            // Reload comments to show new one in correct place (simple approach)
            loadComments();

            if (input) input.value = '';

            // Reset spoiler check
            if (parentId) {
                const spCheck = document.getElementById(`reply-spoiler-${parentId}`);
                if (spCheck) spCheck.checked = false;
            } else {
                const spCheck = document.getElementById('spoilerCheck');
                if (spCheck) spCheck.checked = false;
            }

            incrementCommentCount();
        } catch {
            alert('Yorum gönderilemedi.');
        }
    }

    async function loadComments() {
        try {
            const res = await fetch(`/api/video/comments?id=${videoId}`);
            if (!res.ok) throw new Error();
            const data = await res.json();

            // Calculate total comments (including replies)
            let totalCount = 0;
            const countRecursive = (list) => {
                totalCount += list.length;
                list.forEach(c => {
                    if (c.replies) countRecursive(c.replies);
                });
            };
            countRecursive(data);
            setCommentCount(totalCount);

            commentsList.innerHTML = '';

            if (data.length === 0) {
                commentsList.innerHTML = '<div class="no-comments">Henüz yorum yapılmamış. İlk yorumu siz yapın!</div>';
                return;
            }

            data.forEach(c => {
                renderCommentRecursive(c, commentsList);
            });

        } catch (e) {
            commentsList.innerHTML = '<div class="error-message">Yorumlar yüklenirken hata oluştu.</div>';
            console.error(e);
        }
    }

    function renderCommentRecursive(comment, container) {
        const el = createCommentElement(comment);
        container.appendChild(el);

        if (comment.replies && comment.replies.length > 0) {
            let repliesContainer = el.querySelector('.replies-container');
            if (!repliesContainer) {
                repliesContainer = document.createElement('div');
                repliesContainer.className = 'replies-container';
                el.appendChild(repliesContainer);
            }
            comment.replies.forEach(reply => {
                renderCommentRecursive(reply, repliesContainer);
            });
        }
    }

    function createCommentElement(c) {
        const card = document.createElement('div');
        card.className = 'comment-card';
        card.id = `comment-${c.id}` ;

        const dateStr = new Date(c.date).toLocaleDateString('tr-TR', {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });

        const safeNickname = escapeHtml(c.nickname || 'Kullanıcı');
        const verifiedBadge = c.verified ? '<i class="fas fa-check-circle verified-badge" style="color: #1DA1F2; margin-left: 4px;" title="Doğrulanmış Hesap"></i>' : '';
        const safeComment = escapeHtml(c.comment);

        const initialLetter = safeNickname.charAt(0).toUpperCase();
        let profileImg = `<div class="user-avatar">${initialLetter}</div>`;

        if (c.authorId) {
            const profileImgUrl = `/media/profile/${c.authorId}?t=${new Date().getTime()}`;
            profileImg = `
                <div class="user-avatar">
                    ${initialLetter}
                    <img src="${profileImgUrl}" onerror="this.style.display='none'" style="position: absolute; top:0; left:0; width:100%; height:100%; object-fit: cover; z-index: 1;">
                </div>
            `;
        }

        let contentHtml = '';
        if (c.spoiler) {
            contentHtml = `
                <div class="spoiler-overlay" onclick="this.parentElement.querySelector('.comment-content').classList.remove('blurred'); this.remove();">
                    <div class="spoiler-info">
                        <i class="fas fa-eye-slash"></i> Spoiler İçeriyor
                    </div>
                    <button class="btn-reveal">Detayı Gör</button>
                </div>
                <div class="comment-content blurred">${safeComment}</div>
            `;
        } else {
            contentHtml = `<div class="comment-content">${safeComment}</div>`;
        }

        const isLoggedIn = isUserLoggedIn();

        let badgeHtml = '';
        if (c.role === 'ADMIN') {
            badgeHtml = '<span class="admin-badge"><i class="fas fa-shield-alt"></i> ADMİN</span>';
        } else if (c.role === 'SUPER_ADMIN') {
            badgeHtml = '<span class="super-admin-badge"><i class="fas fa-crown"></i> SUPER ADMİN</span>';
        } else if (c.role === 'KURUCU') {
            badgeHtml = '<span class="founder-badge"><i class="fas fa-crown"></i> KURUCU</span>';
        } else if (c.role === 'GELISTIRICI') {
            badgeHtml = '<span class="developer-badge"><i class="fas fa-code"></i> GELİŞTİRİCİ</span>';
        } else if (c.role === 'CEVIRMEN') {
            badgeHtml = '<span class="translator-badge"><i class="fas fa-language"></i> ÇEVİRMEN</span>';
        }

        card.innerHTML = `
          <div class="comment-header">
            ${profileImg}
            <div class="comment-user"><span class="user-nickname">${safeNickname}${verifiedBadge}</span> ${badgeHtml}</div>
            <div class="comment-date">${dateStr}</div>
          </div>
          ${contentHtml}
          <div class="comment-actions">
            <button class="action-button like-btn ${c.likedByCurrentUser ? 'liked' : ''}" data-id="${c.id}" ${!isLoggedIn ? 'title="Giriş yapmalısınız"' : ''}>
                <i class="${c.likedByCurrentUser ? 'fas' : 'far'} fa-heart"></i> 
                <span>${c.likeCount}</span>
            </button>
            ${isLoggedIn ? `<button class="action-button reply-btn" data-id="${c.id}"><i class="far fa-comment"></i> Yanıtla</button>` : ''}
            ${c.author ? `<button class="action-button delete-btn" data-id="${c.id}"><i class="far fa-trash-alt"></i> Sil</button>` : ''}
            ${isLoggedIn && !c.author ? `<button class="action-button report-btn" data-id="${c.id}" data-nickname="${c.nickname}"><i class="far fa-flag"></i> Bildir</button>` : ''}
          </div>
          <div class="reply-form-container" id="reply-form-${c.id}" style="display:none; margin-top:10px;">
                <textarea id="reply-input-${c.id}" class="comment-input" placeholder="Yanıtınızı yazın..." rows="2"></textarea>
                <div class="comment-button-container" style="justify-content: space-between; align-items: center;">
                    <label class="spoiler-toggle" style="margin-top: 5px;">
                        <input type="checkbox" id="reply-spoiler-${c.id}">
                        <span class="toggle-text"><i class="fas fa-mask"></i> Spoiler</span>
                    </label>
                    <button class="submit-comment-btn submit-reply-btn" data-id="${c.id}" style="margin-top: 5px;">Gönder</button>
                </div>
          </div>
        `;

        // Event Listeners
        const likeBtn = card.querySelector('.like-btn');
        likeBtn.onclick = () => {
            if (!isLoggedIn) {
                const loginBtn = document.getElementById('comment-login-btn');
                loginBtn?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                loginBtn?.classList.add('error-shake');
                setTimeout(() => loginBtn?.classList.remove('error-shake'), 500);
                return;
            }
            toggleLike(c.id, likeBtn);
        };

        const replyBtn = card.querySelector('.reply-btn');
        if (replyBtn) {
            replyBtn.onclick = () => {
                const form = card.querySelector(`#reply-form-${c.id}`);
                form.style.display = form.style.display === 'none' ? 'block' : 'none';
            };
        }

        const deleteBtn = card.querySelector('.delete-btn');
        if (deleteBtn) {
            deleteBtn.onclick = () => handleDeleteComment(c.id);
        }

        const submitReplyBtn = card.querySelector('.submit-reply-btn');
        if (submitReplyBtn) {
            submitReplyBtn.onclick = () => onSubmitComment(null, c.id);
        }

        const reportBtn = card.querySelector('.report-btn');
        if (reportBtn) {
            reportBtn.onclick = () => handleReportComment(c.nickname, c.comment);
        }


        return card;
    }

    async function toggleLike(commentId, btn) {
        try {
            const res = await fetch(`/api/video/comment/like?commentId=${commentId}`, { method: 'POST' });
            if (res.ok) {
                const icon = btn.querySelector('i');
                const cnt = btn.querySelector('span');
                const liked = icon.classList.contains('far'); // currently empty means not liked

                icon.className = liked ? 'fas fa-heart' : 'far fa-heart';
                btn.classList.toggle('liked', liked);
                cnt.textContent = +cnt.textContent + (liked ? 1 : -1);

                icon.classList.add('like-animation');
                setTimeout(() => icon.classList.remove('like-animation'), 500);
            }
        } catch (e) {
            console.error('Like failed', e);
        }
    }

    async function handleDeleteComment(commentId) {
        if (!confirm('Bu yorumu silmek istediğinize emin misiniz?')) return;

        try {
            const res = await fetch(`/api/video/comment?commentId=${commentId}`, {
                method: 'DELETE',
                credentials: 'include'
            });
            if (res.ok) {
                const el = document.getElementById(`comment-${commentId}`);
                if (el) {
                    el.style.opacity = '0';
                    el.style.transform = 'translateX(20px)';
                    setTimeout(() => {
                        el.remove();
                        // Potentially reload or just decrement count
                        loadComments();
                    }, 300);
                }
            } else {
                alert('Yorum silinemedi. Yetkiniz olmayabilir.');
            }
        } catch (e) {
            console.error('Delete failed', e);
        }
    }

    function setCommentCount(count) {
        let span = document.querySelector('.comment-count');
        if (span) {
            span.textContent = count;
        } else {
            const header = document.querySelector('.comments-section h3');
            if (header) header.insertAdjacentHTML('beforeend', ` <span class="comment-count">${count}</span>`);
        }
    }

    function incrementCommentCount() {
        const span = document.querySelector('.comment-count');
        if (span) span.textContent = +span.textContent + 1;
    }

    async function handleReportComment(nickname, commentText) {
        showReportModal(nickname, 'Yorum', (reason) => {
            return fetch('/api/user/report', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    nickname: nickname,
                    reason: reason,
                    context: `Yorum: "${commentText.substring(0, 100)}${commentText.length > 100 ? '...' : ''}"`
                })
            }).then(res => res.ok);
        });
    }
}
