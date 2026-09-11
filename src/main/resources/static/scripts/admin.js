/*
 * 관리자 화면 로직.
 *
 * 카테고리, 게시글, 사이트 설정, 계정을 한 페이지 안에서 섹션으로 전환하며 다룹니다.
 *
 * 이 파일의 규칙
 *   - 모든 API 호출은 apiFetch() 를 거칩니다. 인증 만료(401) 처리와
 *     서버 오류 메시지 전달을 한 곳에서 하기 위해서입니다.
 *   - 저장하지 않은 변경이 있는 상태에서 화면을 벗어나려 하면 확인을 받습니다.
 *   - 서버가 내려준 오류 메시지를 그대로 보여줍니다. "저장 실패" 같은
 *     막연한 문구 대신 원인을 알 수 있게 하기 위해서입니다.
 */
// API Base URL (상대 경로 사용 - 로컬/배포 환경 모두 호환)
const API_BASE_URL = '/api';
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

// 전역 상태
const adminState = {
    categories: [],
    currentCategory: '',
    searchQuery: '',
    editingPost: null,
    quillEditor: null,
    hasUnsavedChanges: false,      // 게시글/카테고리 모달
    settingsDirty: false,          // 사이트 설정 화면
    settingsLoaded: false,
    posts: []
};

// 자주 쓰는 Google Fonts 프리셋
const FONT_PRESETS = [
    { label: '기본 (시스템 폰트)', url: '', family: '' },
    { label: 'Noto Sans KR (고딕)', url: 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@300;400;500;700&display=swap', family: "'Noto Sans KR', sans-serif" },
    { label: 'Noto Serif KR (명조)', url: 'https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@300;400;600&display=swap', family: "'Noto Serif KR', serif" },
    { label: 'Nanum Myeongjo (나눔명조)', url: 'https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@400;700&display=swap', family: "'Nanum Myeongjo', serif" },
    { label: 'Nanum Gothic (나눔고딕)', url: 'https://fonts.googleapis.com/css2?family=Nanum+Gothic:wght@400;700&display=swap', family: "'Nanum Gothic', sans-serif" },
    { label: 'Gowun Batang (고운바탕)', url: 'https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@400;700&display=swap', family: "'Gowun Batang', serif" },
    { label: 'Playfair Display (세리프)', url: 'https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;500;600&display=swap', family: "'Playfair Display', serif" },
    { label: 'Cormorant Garamond (세리프)', url: 'https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@400;500;600&display=swap', family: "'Cormorant Garamond', serif" },
    { label: 'Inter (산세리프)', url: 'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap', family: "'Inter', sans-serif" },
    { label: 'Montserrat (산세리프)', url: 'https://fonts.googleapis.com/css2?family=Montserrat:wght@300;400;500;600&display=swap', family: "'Montserrat', sans-serif" },
    { label: 'Raleway (산세리프)', url: 'https://fonts.googleapis.com/css2?family=Raleway:wght@300;400;500;600&display=swap', family: "'Raleway', sans-serif" },
    { label: '직접 입력…', url: null, family: null }
];

// ============================
// API 호출 공통 처리
//  - X-Requested-With 헤더로 브라우저 기본 로그인 팝업 대신 401 JSON을 받음
//  - 401이면 로그인 페이지로 이동
//  - 실패 시 서버가 보낸 메시지를 그대로 Error로 던짐 (화면에 그대로 표시)
// ============================
/** 서버가 내려준 CSRF 토큰을 쿠키에서 읽습니다. */
function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
}

async function apiFetch(path, options = {}) {
    const headers = Object.assign({ 'X-Requested-With': 'XMLHttpRequest' }, options.headers || {});

    // 조회가 아닌 요청에는 CSRF 토큰을 실어 보냅니다.
    const method = (options.method || 'GET').toUpperCase();
    if (method !== 'GET' && method !== 'HEAD') {
        const token = csrfToken();
        if (token) headers['X-XSRF-TOKEN'] = token;
    }
    if (options.json !== undefined) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.json);
        delete options.json;
    }

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, Object.assign({ credentials: 'same-origin' }, options, { headers }));
    } catch (e) {
        throw new Error('서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.');
    }

    if (response.status === 401) {
        showToast('로그인이 만료되었습니다. 다시 로그인해주세요.', 'error', 4000);
        setTimeout(() => { location.href = '/login.html?expired'; }, 800);
        throw new Error('로그인이 필요합니다');
    }
    if (response.status === 403) {
        // 권한 부족이거나, 보안 토큰이 만료된 경우입니다.
        throw new Error('요청이 거부되었습니다. 페이지를 새로고침한 뒤 다시 시도해주세요.');
    }

    if (!response.ok) {
        let message = `요청 실패 (HTTP ${response.status})`;
        try {
            const data = await response.json();
            if (data && data.message) message = data.message;
        } catch (e) { /* JSON이 아니면 기본 메시지 */ }
        throw new Error(message);
    }

    if (response.status === 204) return null;
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

// ============================
// Toast 알림 시스템
// ============================
function showToast(message, type = 'info', duration = 3000) {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    toast.addEventListener('click', () => removeToast(toast));
    container.appendChild(toast);

    setTimeout(() => removeToast(toast), type === 'error' ? Math.max(duration, 5000) : duration);
}

function removeToast(toast) {
    if (!toast.parentElement) return;
    toast.classList.add('toast-out');
    toast.addEventListener('animationend', () => toast.remove());
}

// ============================
// 커스텀 확인 모달
// ============================
function showConfirm(message, okLabel = '확인', danger = true) {
    return new Promise((resolve) => {
        const overlay = document.getElementById('confirm-modal');
        const msgEl = document.getElementById('confirm-modal-message');
        const okBtn = document.getElementById('confirm-ok');
        const cancelBtn = document.getElementById('confirm-cancel');

        msgEl.textContent = message;
        okBtn.textContent = okLabel;
        okBtn.className = danger ? 'btn btn-danger' : 'btn btn-primary';
        overlay.classList.add('show');
        okBtn.focus();

        function cleanup() {
            overlay.classList.remove('show');
            okBtn.removeEventListener('click', onOk);
            cancelBtn.removeEventListener('click', onCancel);
            overlay.removeEventListener('click', onOverlay);
            document.removeEventListener('keydown', onKey);
        }

        function onOk() { cleanup(); resolve(true); }
        function onCancel() { cleanup(); resolve(false); }
        function onOverlay(e) {
            if (e.target === overlay) { cleanup(); resolve(false); }
        }
        function onKey(e) {
            if (e.key === 'Escape') { e.stopPropagation(); cleanup(); resolve(false); }
        }

        okBtn.addEventListener('click', onOk);
        cancelBtn.addEventListener('click', onCancel);
        overlay.addEventListener('click', onOverlay);
        document.addEventListener('keydown', onKey);
    });
}

// ============================
// 미저장 변경사항 추적
// ============================
function markUnsaved() {
    adminState.hasUnsavedChanges = true;
}

function clearUnsaved() {
    adminState.hasUnsavedChanges = false;
}

function markSettingsDirty() {
    if (!adminState.settingsLoaded) return;
    adminState.settingsDirty = true;
    document.getElementById('settings-save-bar').classList.add('dirty');
}

function clearSettingsDirty() {
    adminState.settingsDirty = false;
    document.getElementById('settings-save-bar').classList.remove('dirty');
}

window.addEventListener('beforeunload', (e) => {
    if (adminState.hasUnsavedChanges || adminState.settingsDirty) {
        e.preventDefault();
        e.returnValue = '';
    }
});

// ============================
// 초기화
// ============================
document.addEventListener('DOMContentLoaded', async () => {
    setupNavigation();
    setupThumbnailUpload();
    setupCvPdfUpload();
    setupBulkImageUpload();
    initializeQuillEditor();
    setupFormChangeTracking();
    initializeSettingsUI();
    setupFilters();
    setupModalKeyboard();
    await loadCategories();
    loadAccount(false);

    // 주소 해시로 섹션 복원 (#posts 등)
    const section = (location.hash || '').replace('#', '');
    if (['categories', 'posts', 'settings', 'account'].includes(section)) {
        switchSection(section);
    }
});

function setupFormChangeTracking() {
    const postForm = document.getElementById('post-form');
    postForm.addEventListener('input', markUnsaved);
    postForm.addEventListener('change', markUnsaved);

    const categoryForm = document.getElementById('category-form');
    categoryForm.addEventListener('input', markUnsaved);
    categoryForm.addEventListener('change', markUnsaved);

    const settingsForm = document.getElementById('settings-form');
    settingsForm.addEventListener('input', markSettingsDirty);
    settingsForm.addEventListener('change', markSettingsDirty);
}

function setupFilters() {
    document.getElementById('category-filter').addEventListener('change', (e) => {
        adminState.currentCategory = e.target.value;
        renderPostsGrid();
    });
    document.getElementById('post-search').addEventListener('input', (e) => {
        adminState.searchQuery = e.target.value.trim().toLowerCase();
        renderPostsGrid(true);
    });
}

// ESC로 모달 닫기 (미저장 확인 포함), 바깥 클릭도 동일하게 처리
function setupModalKeyboard() {
    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        const open = document.querySelector('.modal.show');
        if (open) requestCloseModal(open.id);
    });

    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('mousedown', (e) => {
            if (e.target === modal) modal.dataset.outsideDown = '1';
        });
        modal.addEventListener('mouseup', (e) => {
            const wasOutside = modal.dataset.outsideDown === '1';
            delete modal.dataset.outsideDown;
            // 모달 안에서 드래그하다 바깥에서 놓은 경우는 닫지 않음
            if (wasOutside && e.target === modal) requestCloseModal(modal.id);
        });
    });
}

// ============================
// 카테고리 로드
// ============================
async function loadCategories() {
    try {
        adminState.categories = await apiFetch('/categories');
        renderCategoriesTable();
        updateCategorySelects();
    } catch (error) {
        console.error('카테고리 로드 실패:', error);
        showToast(`카테고리를 불러오지 못했습니다: ${error.message}`, 'error');
    }
}

async function fetchPostCounts() {
    const counts = {};
    let all = [];
    try {
        all = await apiFetch('/posts');
    } catch (e) {
        return counts;
    }
    all.forEach(p => { counts[p.categoryId] = (counts[p.categoryId] || 0) + 1; });
    return counts;
}

// ============================
// 네비게이션 설정
// ============================
function setupNavigation() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => switchSection(btn.getAttribute('data-section')));
    });
}

async function switchSection(sectionName) {
    const current = document.querySelector('.admin-section.active');
    if (current && current.id === 'settings-section' && sectionName !== 'settings' && adminState.settingsDirty) {
        const ok = await showConfirm('사이트 설정에 저장하지 않은 변경사항이 있습니다. 저장하지 않고 이동할까요?', '이동', true);
        if (!ok) return;
        clearSettingsDirty();
    }

    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.toggle('active', btn.getAttribute('data-section') === sectionName);
    });
    document.querySelectorAll('.admin-section').forEach(section => section.classList.remove('active'));
    document.getElementById(`${sectionName}-section`).classList.add('active');
    history.replaceState(null, '', `#${sectionName}`);

    if (sectionName === 'categories') {
        renderCategoriesTable();
    } else if (sectionName === 'posts') {
        renderPostsGrid();
    } else if (sectionName === 'settings') {
        loadSettings();
    } else if (sectionName === 'account') {
        loadAccount(true);
    }
}

// ============================
// 카테고리 테이블 렌더링 (순서 변경 + 게시글 수)
// ============================
async function renderCategoriesTable() {
    const tbody = document.getElementById('categories-tbody');
    const postCounts = await fetchPostCounts();
    tbody.innerHTML = '';

    // 메인 페이지는 카테고리가 아니라 '사이트 설정'에서 관리하므로 목록에서 제외
    const visible = adminState.categories.filter(c => c.id !== 'main');

    if (visible.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty-cell">카테고리가 없습니다. "+ 새 카테고리"로 추가하세요.</td></tr>';
        return;
    }

    visible.forEach((category, index) => {
        const tr = document.createElement('tr');
        const badge = typeBadge(category.type);
        const count = postCounts[category.id] || 0;
        const isCv = category.id === 'cv';

        tr.innerHTML = `
            <td class="col-order">
                <div class="order-btns">
                    <button type="button" class="btn btn-secondary btn-sm btn-reorder" title="위로" aria-label="위로" ${index === 0 ? 'disabled' : ''}>&#9650;</button>
                    <button type="button" class="btn btn-secondary btn-sm btn-reorder" title="아래로" aria-label="아래로" ${index === visible.length - 1 ? 'disabled' : ''}>&#9660;</button>
                </div>
            </td>
            <td><code>${escapeHtml(category.id)}</code></td>
            <td>
                ${escapeHtml(category.name)}
                <span class="post-count-badge">${count}개 게시글</span>
                ${isCv ? '<span class="post-count-badge">PDF 전용 페이지</span>' : ''}
            </td>
            <td>${badge}</td>
            <td>
                <div class="table-actions">
                    <button type="button" class="btn btn-secondary btn-sm" data-action="edit">이름 수정</button>
                    ${category.isDeletable
                        ? '<button type="button" class="btn btn-danger btn-sm" data-action="delete">삭제</button>'
                        : '<span class="muted" title="사이트 구조에 필요한 기본 페이지입니다">삭제 불가</span>'
                    }
                </div>
            </td>
        `;
        const [upBtn, downBtn] = tr.querySelectorAll('.btn-reorder');
        upBtn.addEventListener('click', () => moveCategory(category.id, -1));
        downBtn.addEventListener('click', () => moveCategory(category.id, 1));
        tr.querySelector('[data-action="edit"]').addEventListener('click', () => editCategory(category.id));
        const delBtn = tr.querySelector('[data-action="delete"]');
        if (delBtn) delBtn.addEventListener('click', () => deleteCategory(category.id, count));
        tbody.appendChild(tr);
    });
}

function typeBadge(type) {
    const map = { PHOTO: 'badge-photo', ARTICLE: 'badge-article', HTML: 'badge-html' };
    if (!map[type]) return '<span class="badge badge-photo">PHOTO</span>';
    return `<span class="badge ${map[type]}">${escapeHtml(type)}</span>`;
}

async function moveCategory(categoryId, direction) {
    const visible = adminState.categories.filter(c => c.id !== 'main');
    const index = visible.findIndex(c => c.id === categoryId);
    const target = index + direction;
    if (index === -1 || target < 0 || target >= visible.length) return;

    [visible[index], visible[target]] = [visible[target], visible[index]];
    const ids = ['main', ...visible.map(c => c.id)].filter(id => adminState.categories.some(c => c.id === id));

    try {
        await apiFetch('/categories/reorder', { method: 'PUT', json: { ids } });
        await loadCategories();
    } catch (error) {
        showToast(`순서 변경 실패: ${error.message}`, 'error');
    }
}

// HTML 이스케이프
function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    const div = document.createElement('div');
    div.textContent = String(text);
    return div.innerHTML;
}

// ============================
// 카테고리 선택 박스 업데이트
// ============================
function updateCategorySelects() {
    const categoryFilter = document.getElementById('category-filter');
    const postCategory = document.getElementById('post-category');
    const prevFilter = categoryFilter.value;

    categoryFilter.innerHTML = '<option value="">모든 카테고리</option>';
    postCategory.innerHTML = '<option value="">선택하세요</option>';

    adminState.categories.forEach(cat => {
        if (cat.id === 'main') return;
        const a = document.createElement('option');
        a.value = cat.id;
        a.textContent = cat.name;
        categoryFilter.appendChild(a);

        const b = document.createElement('option');
        b.value = cat.id;
        b.textContent = cat.name;
        postCategory.appendChild(b);
    });

    if ([...categoryFilter.options].some(o => o.value === prevFilter)) {
        categoryFilter.value = prevFilter;
    } else {
        adminState.currentCategory = '';
    }
}

// ============================
// 게시글 그리드 렌더링
// ============================
async function renderPostsGrid(useCache = false) {
    const grid = document.getElementById('posts-grid');
    const categoryId = adminState.currentCategory;

    try {
        if (!useCache) {
            adminState.posts = await apiFetch(categoryId
                ? `/posts/category/${encodeURIComponent(categoryId)}`
                : '/posts');
        }

        const query = adminState.searchQuery;
        const posts = query
            ? adminState.posts.filter(p => (p.title || '').toLowerCase().includes(query))
            : adminState.posts;

        if (posts.length === 0) {
            grid.innerHTML = `<div class="empty-state"><p>${query ? '검색 결과가 없습니다' : '등록된 게시글이 없습니다'}</p></div>`;
            return;
        }

        const categoryNames = {};
        adminState.categories.forEach(c => { categoryNames[c.id] = c.name; });
        const canReorder = !!categoryId && !query;

        grid.innerHTML = '';
        posts.forEach((post, index) => {
            const card = document.createElement('div');
            card.className = 'post-card';
            const isPdf = post.thumbnail && post.thumbnail.toLowerCase().endsWith('.pdf');
            card.innerHTML = `
                <div class="post-card-image">
                    ${post.thumbnail && !isPdf
                        ? `<img src="${API_BASE_URL}/files/${encodeURIComponent(post.thumbnail)}" alt="" loading="lazy">`
                        : `<span>${isPdf ? 'PDF' : '이미지 없음'}</span>`
                    }
                    ${canReorder ? `
                    <div class="post-card-order">
                        <button type="button" class="btn-reorder-card" title="앞으로" aria-label="앞으로" data-dir="-1" ${index === 0 ? 'disabled' : ''}>&#9650;</button>
                        <span>${index + 1}</span>
                        <button type="button" class="btn-reorder-card" title="뒤로" aria-label="뒤로" data-dir="1" ${index === posts.length - 1 ? 'disabled' : ''}>&#9660;</button>
                    </div>` : ''}
                </div>
                <div class="post-card-content">
                    <div class="post-card-badges">
                        ${!categoryId ? `<span class="badge badge-category">${escapeHtml(categoryNames[post.categoryId] || post.categoryId)}</span>` : ''}
                        ${typeBadge(post.contentType)}
                    </div>
                    <h3 class="post-card-title">${escapeHtml(post.title)}</h3>
                    <p class="post-card-details">${metaLine(post) || '&nbsp;'}</p>
                    <div class="post-card-actions">
                        <a class="btn btn-secondary btn-sm" href="/#/${encodeURIComponent(post.categoryId)}/${post.id}" target="_blank" rel="noopener" title="사이트에서 보기">보기</a>
                        <button type="button" class="btn btn-secondary btn-sm" data-action="edit">수정</button>
                        <button type="button" class="btn btn-danger btn-sm" data-action="delete">삭제</button>
                    </div>
                </div>
            `;
            card.querySelector('[data-action="edit"]').addEventListener('click', () => editPost(post.id));
            card.querySelector('[data-action="delete"]').addEventListener('click', () => deletePost(post.id, post.title));
            card.querySelectorAll('.btn-reorder-card').forEach(btn => {
                btn.addEventListener('click', () => movePost(post.id, parseInt(btn.dataset.dir, 10)));
            });
            grid.appendChild(card);
        });
    } catch (error) {
        console.error('게시글 로드 실패:', error);
        grid.innerHTML = `<div class="empty-state"><p>게시글을 불러오지 못했습니다: ${escapeHtml(error.message)}</p></div>`;
    }
}

function metaLine(post) {
    return [post.year, post.medium, post.size]
        .map(v => (v || '').trim())
        .filter(v => v && v !== '-')
        .map(escapeHtml)
        .join(', ');
}

async function movePost(postId, direction) {
    const posts = [...adminState.posts];
    const index = posts.findIndex(p => p.id === postId);
    const target = index + direction;
    if (index === -1 || target < 0 || target >= posts.length) return;

    [posts[index], posts[target]] = [posts[target], posts[index]];
    try {
        await apiFetch('/posts/reorder', { method: 'PUT', json: { ids: posts.map(p => String(p.id)) } });
        await renderPostsGrid();
    } catch (error) {
        showToast(`순서 변경 실패: ${error.message}`, 'error');
    }
}

// ============================
// 모달
// ============================
function showModal(modalId) {
    const modal = document.getElementById(modalId);
    modal.classList.add('show');
    const first = modal.querySelector('input:not([type=hidden]):not([readonly]), select, textarea');
    if (first) setTimeout(() => first.focus(), 50);
}

// 닫기 요청: 저장하지 않은 변경사항이 있으면 먼저 확인
async function requestCloseModal(modalId) {
    if (adminState.hasUnsavedChanges) {
        const ok = await showConfirm('저장하지 않은 변경사항이 있습니다. 닫으면 입력한 내용이 사라집니다. 닫을까요?', '닫기', true);
        if (!ok) return;
    }
    closeModal(modalId);
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('show');

    if (modalId === 'category-modal') {
        document.getElementById('category-form').reset();
        document.getElementById('category-edit-id').value = '';
        document.getElementById('category-id').disabled = false;
    } else if (modalId === 'post-modal') {
        resetPostForm();
    }
    clearUnsaved();
}

function resetPostForm() {
    document.getElementById('post-form').reset();
    document.getElementById('post-id').value = '';
    document.getElementById('additional-images').innerHTML = '';
    document.getElementById('cv-pdf-filename').value = '';
    document.getElementById('cv-pdf-group').style.display = 'none';
    document.getElementById('normal-post-fields').style.display = '';
    const cvDropZone = document.getElementById('cv-pdf-drop-zone');
    if (cvDropZone) cvDropZone.innerHTML = '<div class="drop-icon">📄</div><p>PDF 파일을 드래그하거나 클릭하여 선택</p>';
    clearThumbnailPreview();
    if (adminState.quillEditor) adminState.quillEditor.setText('');
    adminState.editingPost = null;
    handleContentTypeChange();
}

// ============================
// 카테고리 CRUD
// ============================
function showAddCategoryModal() {
    document.getElementById('category-modal-title').textContent = '새 카테고리 추가';
    document.getElementById('category-form').reset();
    document.getElementById('category-edit-id').value = '';
    document.getElementById('category-id').disabled = false;
    clearUnsaved();
    showModal('category-modal');
}

function editCategory(categoryId) {
    const category = adminState.categories.find(c => c.id === categoryId);
    if (!category) return;

    document.getElementById('category-modal-title').textContent = '카테고리 수정';
    document.getElementById('category-edit-id').value = category.id;
    document.getElementById('category-id').value = category.id;
    document.getElementById('category-id').disabled = true;
    document.getElementById('category-name').value = category.name;
    const typeSelect = document.getElementById('category-type');
    typeSelect.value = ['PHOTO', 'ARTICLE', 'HTML'].includes(category.type) ? category.type : 'PHOTO';

    clearUnsaved();
    showModal('category-modal');
}

async function handleSaveCategory(event) {
    event.preventDefault();

    const editId = document.getElementById('category-edit-id').value;
    const isEdit = !!editId;

    const id = document.getElementById('category-id').value.toLowerCase().trim();
    const name = document.getElementById('category-name').value.trim();
    const type = document.getElementById('category-type').value;

    if (!isEdit && !/^[a-z0-9-]+$/.test(id)) {
        showToast('카테고리 ID는 영문 소문자, 숫자, 하이픈(-)만 사용할 수 있습니다.', 'warning');
        return;
    }
    if (!name) {
        showToast('카테고리 이름을 입력해주세요.', 'warning');
        return;
    }
    if (!isEdit && (id === 'main' || adminState.categories.some(c => c.id === id))) {
        showToast(`이미 사용 중인 ID입니다: ${id}`, 'warning');
        return;
    }

    try {
        await apiFetch(isEdit ? `/categories/${encodeURIComponent(editId)}` : '/categories', {
            method: isEdit ? 'PUT' : 'POST',
            json: { id: isEdit ? editId : id, name, type, isDeletable: true }
        });
        clearUnsaved();
        closeModal('category-modal');
        await loadCategories();
        showToast(isEdit ? '카테고리가 수정되었습니다.' : '카테고리가 추가되었습니다.', 'success');
    } catch (error) {
        console.error('카테고리 저장 실패:', error);
        showToast(`카테고리 저장 실패: ${error.message}`, 'error');
    }
}

async function deleteCategory(categoryId, knownCount) {
    let count = knownCount;
    if (count === undefined) {
        try {
            const result = await apiFetch(`/categories/${encodeURIComponent(categoryId)}/post-count`);
            count = result.count;
        } catch (e) {
            count = null;
        }
    }
    const category = adminState.categories.find(c => c.id === categoryId);
    const name = category ? category.name : categoryId;
    const message = count
        ? `"${name}" 카테고리를 삭제하면 안에 있는 게시글 ${count}개와 업로드한 파일이 모두 함께 삭제됩니다. 되돌릴 수 없습니다. 삭제할까요?`
        : `"${name}" 카테고리를 삭제할까요? (게시글 없음)`;

    const confirmed = await showConfirm(message, '삭제');
    if (!confirmed) return;

    try {
        await apiFetch(`/categories/${encodeURIComponent(categoryId)}`, { method: 'DELETE' });
        await loadCategories();
        showToast('카테고리가 삭제되었습니다.', 'success');
    } catch (error) {
        console.error('카테고리 삭제 실패:', error);
        showToast(`카테고리 삭제 실패: ${error.message}`, 'error');
    }
}

// ============================
// Quill 에디터
// ============================
function initializeQuillEditor() {
    if (typeof Quill === 'undefined') {
        // CDN 장애 등으로 에디터를 못 불러와도 나머지 관리 기능은 계속 쓸 수 있게 함
        console.warn('Quill 에디터를 불러오지 못했습니다.');
        document.getElementById('quill-editor').innerHTML =
            '<p class="muted" style="padding:1rem">리치 에디터를 불러오지 못했습니다. 페이지를 새로고침해주세요.</p>';
        return;
    }
    adminState.quillEditor = new Quill('#quill-editor', {
        theme: 'snow',
        modules: {
            toolbar: [
                [{ 'header': [1, 2, 3, false] }],
                ['bold', 'italic', 'underline'],
                [{ 'list': 'ordered' }, { 'list': 'bullet' }],
                ['link', 'image']
            ]
        }
    });

    adminState.quillEditor.on('text-change', (delta, oldDelta, source) => {
        if (source === 'user') markUnsaved();
    });
}

// ============================
// 카테고리 변경 처리 (CV 선택 시 PDF 전용 모드)
// ============================
function handleCategoryChange() {
    const categoryId = document.getElementById('post-category').value;
    const isCv = (categoryId === 'cv');

    document.getElementById('cv-pdf-group').style.display = isCv ? 'block' : 'none';
    document.getElementById('normal-post-fields').style.display = isCv ? 'none' : '';

    if (!isCv) {
        // 새 게시글이면 카테고리 기본 타입을 미리 선택
        const typeSelect = document.getElementById('post-content-type');
        if (!adminState.editingPost && !typeSelect.value) {
            const category = adminState.categories.find(c => c.id === categoryId);
            if (category && ['PHOTO', 'ARTICLE', 'HTML'].includes(category.type)) {
                typeSelect.value = category.type;
            }
        }
        handleContentTypeChange();
    }
}

// ============================
// 콘텐츠 타입 변경 처리
// ============================
function handleContentTypeChange() {
    const contentType = document.getElementById('post-content-type').value;

    const show = (id, visible) => { document.getElementById(id).style.display = visible ? 'block' : 'none'; };
    show('thumbnail-group', contentType === 'ARTICLE');
    show('photo-description-group', contentType === 'PHOTO');
    show('article-editor-group', contentType === 'ARTICLE');
    show('html-editor-group', contentType === 'HTML');
    show('additional-images-group', contentType === 'PHOTO' || contentType === 'ARTICLE');
    show('meta-row', contentType !== 'HTML');

    const hint = document.getElementById('media-hint');
    if (hint) {
        hint.textContent = contentType === 'PHOTO'
            ? '맨 위 이미지가 목록의 대표 이미지(썸네일)로 사용됩니다. ▲▼로 순서를 바꿀 수 있습니다.'
            : '본문과 함께 표시되는 이미지·영상입니다. 대표 이미지는 위에서 따로 지정합니다.';
    }
}

// ============================
// 게시글 추가/수정 모달
// ============================
function showAddPostModal() {
    document.getElementById('post-modal-title').textContent = '새 게시글 추가';
    resetPostForm();

    // 현재 필터 중인 카테고리를 기본으로 선택
    if (adminState.currentCategory) {
        document.getElementById('post-category').value = adminState.currentCategory;
    }
    handleCategoryChange();
    clearUnsaved();
    showModal('post-modal');
}

async function editPost(postId) {
    try {
        const post = await apiFetch(`/posts/${postId}`);

        resetPostForm();
        adminState.editingPost = postId;
        document.getElementById('post-modal-title').textContent = '게시글 수정';
        document.getElementById('post-id').value = postId;
        document.getElementById('post-category').value = post.categoryId;

        handleCategoryChange();

        if (post.categoryId === 'cv') {
            document.getElementById('cv-pdf-filename').value = post.thumbnail || '';
            if (post.thumbnail) {
                const cvDropZone = document.getElementById('cv-pdf-drop-zone');
                if (cvDropZone) cvDropZone.innerHTML = `<div class="drop-icon">✅</div><p>PDF 업로드됨 · <a href="${API_BASE_URL}/files/${encodeURIComponent(post.thumbnail)}" target="_blank" rel="noopener">열어보기</a><br><small>다른 파일을 드래그하거나 클릭하면 교체됩니다</small></p>`;
            }
            clearUnsaved();
            showModal('post-modal');
            return;
        }

        document.getElementById('post-content-type').value = post.contentType;
        document.getElementById('post-title').value = post.title || '';
        document.getElementById('post-year').value = post.year === '-' ? '' : (post.year || '');
        document.getElementById('post-medium').value = post.medium === '-' ? '' : (post.medium || '');
        document.getElementById('post-size').value = post.size === '-' ? '' : (post.size || '');
        document.getElementById('post-thumbnail').value = post.thumbnail || '';

        handleContentTypeChange();

        if (post.contentType === 'ARTICLE' && post.thumbnail) {
            showThumbnailPreview(`${API_BASE_URL}/files/${encodeURIComponent(post.thumbnail)}`);
        }

        if (post.contentType === 'PHOTO') {
            document.getElementById('post-description-text').value = post.description || '';
        } else if (post.contentType === 'ARTICLE') {
            if (post.description && adminState.quillEditor) {
                try {
                    adminState.quillEditor.setContents(JSON.parse(post.description), 'api');
                } catch (e) {
                    adminState.quillEditor.setText(post.description || '', 'api');
                }
            }
        } else if (post.contentType === 'HTML') {
            document.getElementById('post-html-content').value = post.htmlContent || '';
        }

        // 미디어 목록
        const additionalImages = document.getElementById('additional-images');
        additionalImages.innerHTML = '';

        const images = (post.images || []).slice().sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));
        images.forEach(img => {
            addMediaInput({
                imageUrl: img.imageUrl || '',
                imageDescription: img.imageDescription || '',
                mediaType: img.mediaType || 'IMAGE',
                showDescription: img.showDescription !== false,
                showImageDescription: img.showImageDescription || false,
                descriptionPosition: img.descriptionPosition || 'right'
            });
        });

        // 하위호환: 예전 데이터(썸네일만 있고 미디어 목록이 비어 있음)
        if (post.contentType === 'PHOTO' && post.thumbnail && !images.some(i => i.imageUrl === post.thumbnail)) {
            addMediaInput({ imageUrl: post.thumbnail, mediaType: 'IMAGE' }, true);
        }
        if (post.videoUrl && !images.some(img => img.mediaType === 'VIDEO')) {
            addMediaInput({ imageUrl: post.videoUrl, mediaType: 'VIDEO', showDescription: false, descriptionPosition: 'right' });
        }

        clearUnsaved();
        showModal('post-modal');
    } catch (error) {
        console.error('게시글 로드 실패:', error);
        showToast(`게시글을 불러오지 못했습니다: ${error.message}`, 'error');
    }
}

// ============================
// 파일 업로드
// ============================
function validateFile(file, { pdf = false, imageOnly = false } = {}) {
    if (file.size > MAX_UPLOAD_BYTES) {
        const mb = (file.size / (1024 * 1024)).toFixed(1);
        throw new Error(`파일이 너무 큽니다 (${mb}MB). 10MB 이하로 줄여서 올려주세요.`);
    }
    const isImage = file.type.startsWith('image/');
    const isPdf = file.type === 'application/pdf';
    if (pdf && !isPdf) throw new Error('PDF 파일만 업로드할 수 있습니다.');
    if (imageOnly && !isImage) throw new Error('이미지 파일(jpg, png, gif, webp, svg)만 업로드할 수 있습니다.');
    if (!pdf && !imageOnly && !isImage && !isPdf) throw new Error('이미지 또는 PDF 파일만 업로드할 수 있습니다.');
}

async function uploadImage(file, opts) {
    validateFile(file, opts);
    const formData = new FormData();
    formData.append('file', file);
    const result = await apiFetch('/files/upload', { method: 'POST', body: formData });
    return result.fileName;
}

// 썸네일 프리뷰 (ARTICLE 대표 이미지)
function showThumbnailPreview(src) {
    const group = document.getElementById('thumbnail-group');
    clearThumbnailPreview();

    const previewDiv = document.createElement('div');
    previewDiv.className = 'upload-preview';
    previewDiv.id = 'thumbnail-preview';

    const img = document.createElement('img');
    img.src = src;
    img.alt = '썸네일 미리보기';

    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'preview-remove';
    removeBtn.textContent = '×';
    removeBtn.title = '대표 이미지 제거';
    removeBtn.onclick = () => {
        document.getElementById('post-thumbnail').value = '';
        clearThumbnailPreview();
        markUnsaved();
    };

    previewDiv.appendChild(img);
    previewDiv.appendChild(removeBtn);
    group.appendChild(previewDiv);
}

function clearThumbnailPreview() {
    const preview = document.getElementById('thumbnail-preview');
    if (preview) preview.remove();
}

function createDropZone({ id, icon, text, accept, multiple = false, onFiles, validate }) {
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = accept;
    fileInput.multiple = multiple;
    fileInput.style.display = 'none';

    const dropZone = document.createElement('div');
    dropZone.className = 'drop-zone';
    dropZone.id = id;
    dropZone.setAttribute('role', 'button');
    dropZone.tabIndex = 0;
    dropZone.innerHTML = `<div class="drop-icon">${icon}</div><p>${text}</p>`;
    dropZone.onclick = () => fileInput.click();
    dropZone.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); } };

    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
    dropZone.addEventListener('drop', async (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
        const files = Array.from(e.dataTransfer.files);
        if (files.length) await onFiles(multiple ? files : [files[0]], dropZone);
    });
    fileInput.onchange = async (e) => {
        const files = Array.from(e.target.files);
        if (files.length) await onFiles(files, dropZone);
        fileInput.value = '';
    };

    return { fileInput, dropZone };
}

// ARTICLE 대표 이미지 업로드
function setupThumbnailUpload() {
    const group = document.getElementById('thumbnail-group');
    const { fileInput, dropZone } = createDropZone({
        id: 'thumbnail-drop-zone',
        icon: '🖼️',
        text: '대표 이미지를 드래그하거나 클릭하여 선택',
        accept: 'image/*',
        onFiles: ([file], zone) => handleThumbnailFile(file, zone)
    });
    group.appendChild(fileInput);
    group.appendChild(dropZone);
}

async function handleThumbnailFile(file, dropZone) {
    const originalText = dropZone.innerHTML;
    dropZone.innerHTML = '<p>업로드 중...</p>';

    let localUrl = null;
    try {
        validateFile(file, { imageOnly: true });
        localUrl = URL.createObjectURL(file);
        showThumbnailPreview(localUrl);
        const fileName = await uploadImage(file, { imageOnly: true });
        document.getElementById('post-thumbnail').value = fileName;
        showThumbnailPreview(`${API_BASE_URL}/files/${encodeURIComponent(fileName)}`);
        showToast('대표 이미지가 업로드되었습니다.', 'success');
        markUnsaved();
    } catch (error) {
        clearThumbnailPreview();
        showToast(error.message, 'error');
    } finally {
        dropZone.innerHTML = originalText;
        if (localUrl) URL.revokeObjectURL(localUrl);
    }
}

// CV PDF 업로드
function setupCvPdfUpload() {
    const cvGroup = document.getElementById('cv-pdf-group');
    const { fileInput, dropZone } = createDropZone({
        id: 'cv-pdf-drop-zone',
        icon: '📄',
        text: 'PDF 파일을 드래그하거나 클릭하여 선택',
        accept: '.pdf,application/pdf',
        onFiles: ([file], zone) => handleCvPdfFile(file, zone)
    });
    cvGroup.appendChild(fileInput);
    cvGroup.appendChild(dropZone);
}

async function handleCvPdfFile(file, dropZone) {
    const originalText = dropZone.innerHTML;
    dropZone.innerHTML = '<p>업로드 중...</p>';

    try {
        const fileName = await uploadImage(file, { pdf: true });
        document.getElementById('cv-pdf-filename').value = fileName;
        dropZone.innerHTML = `<div class="drop-icon">✅</div><p>${escapeHtml(file.name)} 업로드 완료<br><small>저장 버튼을 눌러야 반영됩니다</small></p>`;
        showToast('PDF가 업로드되었습니다. 저장을 눌러 반영하세요.', 'success');
        markUnsaved();
    } catch (error) {
        dropZone.innerHTML = originalText;
        showToast(error.message, 'error');
    }
}

// 여러 이미지 한 번에 추가
function setupBulkImageUpload() {
    const input = document.getElementById('bulk-image-input');
    input.onchange = async (e) => {
        const files = Array.from(e.target.files);
        input.value = '';
        if (!files.length) return;
        showToast(`${files.length}개 이미지를 업로드합니다...`, 'info');
        let ok = 0;
        for (const file of files) {
            try {
                const fileName = await uploadImage(file, { imageOnly: true });
                addMediaInput({ imageUrl: fileName, mediaType: 'IMAGE' });
                ok++;
            } catch (error) {
                showToast(`${file.name}: ${error.message}`, 'error');
            }
        }
        if (ok) {
            showToast(`${ok}개 이미지가 추가되었습니다.`, 'success');
            markUnsaved();
        }
    };
}

// ============================
// 통합 미디어 입력 (이미지/영상, 순서변경, 설명옵션)
// ============================
function addMediaInput(options = {}, prepend = false) {
    const {
        imageUrl = '',
        imageDescription = '',
        mediaType = 'IMAGE',
        showDescription = true,
        descriptionPosition = 'right'
    } = options;

    const container = document.getElementById('additional-images');
    const wrapper = document.createElement('div');
    wrapper.className = 'image-entry';
    wrapper.dataset.mediaType = mediaType;

    const topRow = document.createElement('div');
    topRow.className = 'image-input-group media-top-row';

    const upBtn = document.createElement('button');
    upBtn.type = 'button';
    upBtn.className = 'btn btn-secondary btn-sm btn-reorder';
    upBtn.textContent = '▲';
    upBtn.title = '위로';
    upBtn.setAttribute('aria-label', '위로');
    upBtn.onclick = () => moveMediaEntry(wrapper, -1);

    const downBtn = document.createElement('button');
    downBtn.type = 'button';
    downBtn.className = 'btn btn-secondary btn-sm btn-reorder';
    downBtn.textContent = '▼';
    downBtn.title = '아래로';
    downBtn.setAttribute('aria-label', '아래로');
    downBtn.onclick = () => moveMediaEntry(wrapper, 1);

    const typeBadgeEl = document.createElement('span');
    typeBadgeEl.className = `badge ${mediaType === 'VIDEO' ? 'badge-html' : 'badge-photo'}`;
    typeBadgeEl.textContent = mediaType === 'VIDEO' ? 'VIDEO' : 'IMAGE';

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'additional-image-url';
    input.value = imageUrl;
    input.style.flex = '1';

    if (mediaType === 'VIDEO') {
        input.placeholder = 'YouTube URL (예: https://www.youtube.com/watch?v=...)';
        input.addEventListener('input', () => {
            markUnsaved();
            const videoId = extractYouTubeId(input.value);
            if (videoId) {
                showInlinePreview(wrapper, `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`);
                input.classList.remove('invalid');
            } else {
                removeInlinePreview(wrapper);
                input.classList.toggle('invalid', input.value.trim() !== '');
            }
        });
    } else {
        input.placeholder = '이미지를 선택하세요';
        input.readOnly = true;
    }

    let fileInput, selectBtn;
    if (mediaType === 'IMAGE') {
        fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.accept = 'image/*';
        fileInput.style.display = 'none';

        selectBtn = document.createElement('button');
        selectBtn.type = 'button';
        selectBtn.className = 'btn btn-secondary btn-sm';
        selectBtn.textContent = imageUrl ? '교체' : '파일 선택';
        selectBtn.onclick = () => fileInput.click();

        fileInput.onchange = async (e) => {
            const file = e.target.files[0];
            fileInput.value = '';
            if (!file) return;
            let localUrl = null;
            try {
                validateFile(file, { imageOnly: true });
                localUrl = URL.createObjectURL(file);
                showInlinePreview(wrapper, localUrl);
                selectBtn.textContent = '업로드 중...';
                selectBtn.disabled = true;
                const fileName = await uploadImage(file, { imageOnly: true });
                input.value = fileName;
                showInlinePreview(wrapper, `${API_BASE_URL}/files/${encodeURIComponent(fileName)}`);
                showToast('이미지가 업로드되었습니다.', 'success');
                markUnsaved();
            } catch (error) {
                if (!input.value) removeInlinePreview(wrapper);
                showToast(error.message, 'error');
            } finally {
                selectBtn.textContent = input.value ? '교체' : '파일 선택';
                selectBtn.disabled = false;
                if (localUrl) URL.revokeObjectURL(localUrl);
            }
        };
    }

    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn-remove-image';
    removeBtn.textContent = '삭제';
    removeBtn.onclick = () => { wrapper.remove(); markUnsaved(); };

    topRow.appendChild(upBtn);
    topRow.appendChild(downBtn);
    topRow.appendChild(typeBadgeEl);
    topRow.appendChild(input);
    if (mediaType === 'IMAGE') {
        topRow.appendChild(fileInput);
        topRow.appendChild(selectBtn);
    }
    topRow.appendChild(removeBtn);

    // 옵션 행
    const optionsRow = document.createElement('div');
    optionsRow.className = 'media-options';

    const showDescLabel = document.createElement('label');
    showDescLabel.className = 'inline-check';
    const showDescCheck = document.createElement('input');
    showDescCheck.type = 'checkbox';
    showDescCheck.className = 'show-description-check';
    showDescCheck.checked = showDescription;
    showDescLabel.appendChild(showDescCheck);
    showDescLabel.appendChild(document.createTextNode('제목·설명 패널 표시'));

    const showImgDescLabel = document.createElement('label');
    showImgDescLabel.className = 'inline-check';
    const showImgDescCheck = document.createElement('input');
    showImgDescCheck.type = 'checkbox';
    showImgDescCheck.className = 'show-image-description-check';
    showImgDescCheck.checked = options.showImageDescription || false;
    showImgDescLabel.appendChild(showImgDescCheck);
    showImgDescLabel.appendChild(document.createTextNode('이 항목 설명 표시'));

    const posSelect = document.createElement('select');
    posSelect.className = 'description-position-select';
    posSelect.innerHTML = `
        <option value="right" ${descriptionPosition === 'right' ? 'selected' : ''}>설명 위치: 우측</option>
        <option value="bottom" ${descriptionPosition === 'bottom' ? 'selected' : ''}>설명 위치: 하단</option>
    `;

    const descInput = document.createElement('textarea');
    descInput.className = 'additional-image-desc';
    descInput.value = imageDescription;
    descInput.placeholder = '이 항목의 설명 (선택사항)';
    descInput.rows = 2;

    function updatePosSelectState() {
        const imgDescChecked = showImgDescCheck.checked;
        const mainDescChecked = showDescCheck.checked;
        posSelect.disabled = !imgDescChecked;
        descInput.style.display = imgDescChecked ? '' : 'none';
        const rightOption = posSelect.querySelector('option[value="right"]');
        if (mainDescChecked) {
            rightOption.disabled = true;
            if (posSelect.value === 'right') posSelect.value = 'bottom';
        } else {
            rightOption.disabled = false;
        }
    }
    updatePosSelectState();

    showDescCheck.onchange = () => { updatePosSelectState(); markUnsaved(); };
    showImgDescCheck.onchange = () => { updatePosSelectState(); markUnsaved(); };
    posSelect.onchange = () => markUnsaved();

    optionsRow.appendChild(showDescLabel);
    optionsRow.appendChild(showImgDescLabel);
    optionsRow.appendChild(posSelect);

    wrapper.appendChild(topRow);
    wrapper.appendChild(optionsRow);
    wrapper.appendChild(descInput);

    if (mediaType === 'IMAGE' && imageUrl) {
        showInlinePreview(wrapper, `${API_BASE_URL}/files/${encodeURIComponent(imageUrl)}`);
    } else if (mediaType === 'VIDEO' && imageUrl) {
        const videoId = extractYouTubeId(imageUrl);
        if (videoId) showInlinePreview(wrapper, `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`);
    }

    if (prepend && container.firstChild) {
        container.insertBefore(wrapper, container.firstChild);
    } else {
        container.appendChild(wrapper);
    }

    // 새로 추가한 IMAGE 항목은 바로 파일 선택창을 띄움
    if (mediaType === 'IMAGE' && !imageUrl && fileInput && !prepend && Object.keys(options).length === 0) {
        fileInput.click();
    }
}

function moveMediaEntry(wrapper, direction) {
    const container = document.getElementById('additional-images');
    const entries = Array.from(container.children);
    const index = entries.indexOf(wrapper);
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= entries.length) return;

    if (direction === -1) {
        container.insertBefore(wrapper, entries[targetIndex]);
    } else {
        container.insertBefore(wrapper, entries[targetIndex].nextSibling);
    }
    wrapper.classList.add('just-moved');
    setTimeout(() => wrapper.classList.remove('just-moved'), 400);
    markUnsaved();
}

function extractYouTubeId(url) {
    if (!url) return null;
    const match = url.match(
        /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/
    );
    return match ? match[1] : null;
}

function showInlinePreview(container, src) {
    removeInlinePreview(container);
    const preview = document.createElement('div');
    preview.className = 'upload-preview inline';
    const img = document.createElement('img');
    img.src = src;
    img.alt = '미리보기';
    preview.appendChild(img);
    container.appendChild(preview);
}

function removeInlinePreview(container) {
    const existing = container.querySelector('.upload-preview');
    if (existing) existing.remove();
}

// ============================
// 게시글 저장
// ============================
function collectMediaEntries() {
    return Array.from(document.querySelectorAll('#additional-images .image-entry'))
        .map((entry, index) => ({
            imageUrl: entry.querySelector('.additional-image-url').value.trim(),
            imageDescription: entry.querySelector('.additional-image-desc')?.value || '',
            displayOrder: index,
            mediaType: entry.dataset.mediaType || 'IMAGE',
            showDescription: entry.querySelector('.show-description-check')?.checked ?? true,
            showImageDescription: entry.querySelector('.show-image-description-check')?.checked ?? false,
            descriptionPosition: entry.querySelector('.description-position-select')?.value || 'right'
        }))
        .filter(img => img.imageUrl !== '');
}

async function handleSavePost(event) {
    event.preventDefault();

    const postId = document.getElementById('post-id').value;
    const contentType = document.getElementById('post-content-type').value;
    const categoryId = document.getElementById('post-category').value;
    const saveBtn = document.getElementById('post-save-btn');

    if (!categoryId) {
        showToast('카테고리를 선택해주세요.', 'warning');
        document.getElementById('post-category').focus();
        return;
    }

    let postData;

    if (categoryId === 'cv') {
        const pdfFileName = document.getElementById('cv-pdf-filename').value;
        if (!pdfFileName) {
            showToast('CV로 사용할 PDF 파일을 업로드해주세요.', 'warning');
            return;
        }
        postData = { categoryId: 'cv', contentType: 'PHOTO', title: 'CV', year: '', medium: '', size: '', thumbnail: pdfFileName, images: [] };
    } else {
        if (!contentType) {
            showToast('콘텐츠 타입을 선택해주세요.', 'warning');
            document.getElementById('post-content-type').focus();
            return;
        }
        const title = document.getElementById('post-title').value.trim();
        if (!title) {
            showToast('제목을 입력해주세요.', 'warning');
            document.getElementById('post-title').focus();
            return;
        }

        postData = {
            categoryId,
            contentType,
            title,
            year: document.getElementById('post-year').value.trim(),
            medium: document.getElementById('post-medium').value.trim(),
            size: document.getElementById('post-size').value.trim()
        };

        const mediaEntries = collectMediaEntries();
        const badVideo = mediaEntries.find(e => e.mediaType === 'VIDEO' && !extractYouTubeId(e.imageUrl));
        if (badVideo) {
            showToast('YouTube 주소 형식이 올바르지 않은 영상 항목이 있습니다.', 'warning');
            return;
        }

        if (contentType === 'PHOTO') {
            postData.description = document.getElementById('post-description-text').value;
            postData.images = mediaEntries;
            // 대표 이미지 = 미디어 목록의 첫 번째 이미지
            const firstImage = mediaEntries.find(e => e.mediaType === 'IMAGE');
            postData.thumbnail = firstImage ? firstImage.imageUrl : null;
            if (!firstImage) {
                const ok = await showConfirm('이미지가 하나도 없습니다. 목록에는 빈 칸으로 표시됩니다. 그래도 저장할까요?', '저장', false);
                if (!ok) return;
            }
        } else if (contentType === 'ARTICLE') {
            if (!adminState.quillEditor) {
                showToast('리치 에디터를 불러오지 못해 ARTICLE 본문을 저장할 수 없습니다. 페이지를 새로고침해주세요.', 'error');
                return;
            }
            postData.thumbnail = document.getElementById('post-thumbnail').value || null;
            postData.description = JSON.stringify(adminState.quillEditor.getContents());
            postData.images = mediaEntries;
        } else if (contentType === 'HTML') {
            postData.htmlContent = document.getElementById('post-html-content').value;
            postData.images = [];
        }
    }

    saveBtn.disabled = true;
    saveBtn.textContent = '저장 중...';
    try {
        await apiFetch(postId ? `/posts/${postId}` : '/posts', {
            method: postId ? 'PUT' : 'POST',
            json: postData
        });
        clearUnsaved();
        closeModal('post-modal');
        await renderPostsGrid();
        showToast(categoryId === 'cv' ? 'CV가 저장되었습니다.' : '게시글이 저장되었습니다.', 'success');
    } catch (error) {
        console.error('게시글 저장 실패:', error);
        showToast(`저장 실패: ${error.message}`, 'error');
    } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = '저장';
    }
}

// ============================
// 게시글 삭제
// ============================
async function deletePost(postId, title) {
    const confirmed = await showConfirm(`"${title || '제목 없음'}" 게시글과 업로드한 이미지를 삭제할까요? 되돌릴 수 없습니다.`, '삭제');
    if (!confirmed) return;

    try {
        await apiFetch(`/posts/${postId}`, { method: 'DELETE' });
        await renderPostsGrid();
        showToast('게시글이 삭제되었습니다.', 'success');
    } catch (error) {
        console.error('게시글 삭제 실패:', error);
        showToast(`게시글 삭제 실패: ${error.message}`, 'error');
    }
}

// ============================
// 사이트 설정 관리
// ============================
function initializeSettingsUI() {
    setupSettingsImageUpload('bg-image-drop-zone', 'setting-bg-image', 'bg-image-preview');
    setupSettingsImageUpload('logo-drop-zone', 'setting-logo-image', 'logo-preview');
    setupSettingsImageUpload('favicon-drop-zone', 'setting-favicon', 'favicon-preview');

    ['setting-bg-color', 'setting-text-color', 'setting-sidebar-bg',
     'setting-sidebar-text', 'setting-sidebar-active'].forEach(setupColorSync);

    setupFontPresets();

    // 미리보기 갱신
    document.getElementById('settings-form').addEventListener('input', updateSettingsPreview);
    document.getElementById('settings-form').addEventListener('change', updateSettingsPreview);
}

function setupColorSync(id) {
    const colorInput = document.getElementById(id);
    const textInput = document.getElementById(id + '-text');
    if (!colorInput || !textInput) return;

    colorInput.addEventListener('input', () => { textInput.value = colorInput.value; });
    textInput.addEventListener('input', () => {
        const val = textInput.value.trim();
        if (/^#[0-9a-fA-F]{6}$/.test(val)) {
            colorInput.value = val;
            textInput.classList.remove('invalid');
        } else {
            textInput.classList.add('invalid');
        }
    });
    textInput.addEventListener('blur', () => {
        // 잘못된 값이면 컬러피커 값으로 되돌림
        if (!/^#[0-9a-fA-F]{6}$/.test(textInput.value.trim())) {
            textInput.value = colorInput.value;
            textInput.classList.remove('invalid');
        }
    });
}

function setupFontPresets() {
    ['title', 'body'].forEach(target => {
        const select = document.getElementById(`setting-${target}-font-preset`);
        select.innerHTML = FONT_PRESETS.map((p, i) => `<option value="${i}">${escapeHtml(p.label)}</option>`).join('');
        select.addEventListener('change', () => {
            const preset = FONT_PRESETS[parseInt(select.value, 10)];
            if (preset.url === null) {
                // 직접 입력: 고급 영역 열기
                document.querySelector('.font-advanced').open = true;
                document.getElementById(`setting-${target}-font-url`).focus();
                return;
            }
            document.getElementById(`setting-${target}-font-url`).value = preset.url;
            document.getElementById(`setting-${target}-font-family`).value = preset.family;
            updateSettingsPreview();
        });

        // 직접 입력 시 프리셋 선택을 동기화
        ['url', 'family'].forEach(kind => {
            document.getElementById(`setting-${target}-font-${kind}`).addEventListener('input', () => syncFontPreset(target));
        });
    });
}

function syncFontPreset(target) {
    const url = document.getElementById(`setting-${target}-font-url`).value.trim();
    const family = document.getElementById(`setting-${target}-font-family`).value.trim();
    const select = document.getElementById(`setting-${target}-font-preset`);
    const idx = FONT_PRESETS.findIndex(p => p.url !== null && p.url === url && p.family === family);
    select.value = String(idx !== -1 ? idx : FONT_PRESETS.length - 1);
    if (url && !url.startsWith('https://fonts.googleapis.com')) {
        document.getElementById(`setting-${target}-font-url`).classList.add('invalid');
    } else {
        document.getElementById(`setting-${target}-font-url`).classList.remove('invalid');
    }
}

const previewFontLinks = {};
function ensurePreviewFont(url) {
    if (!url || !url.startsWith('https://fonts.googleapis.com') || previewFontLinks[url]) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = url;
    document.head.appendChild(link);
    previewFontLinks[url] = true;
}

function updateSettingsPreview() {
    const v = id => document.getElementById(id).value;
    const preview = document.getElementById('site-preview');
    const sidebar = document.getElementById('preview-sidebar');
    const main = document.getElementById('preview-main');

    preview.style.backgroundColor = v('setting-bg-color');
    main.style.color = v('setting-text-color');
    sidebar.style.backgroundColor = v('setting-sidebar-bg');
    sidebar.style.color = v('setting-sidebar-text');
    document.getElementById('preview-nav-active').style.color = v('setting-sidebar-active');
    document.getElementById('preview-site-title').style.color = v('setting-sidebar-active');
    document.getElementById('preview-site-title').textContent = v('setting-site-title') || 'Site title';

    const bg = v('setting-bg-image');
    if (bg) {
        preview.style.backgroundImage = `url(${API_BASE_URL}/files/${encodeURIComponent(bg)})`;
        preview.style.backgroundSize = v('setting-bg-size');
        preview.style.backgroundRepeat = v('setting-bg-repeat');
        preview.style.backgroundPosition = 'center center';
    } else {
        preview.style.backgroundImage = 'none';
    }

    ensurePreviewFont(v('setting-title-font-url'));
    ensurePreviewFont(v('setting-body-font-url'));
    const titleFont = v('setting-title-font-family') || 'inherit';
    const bodyFont = v('setting-body-font-family') || '';
    preview.style.fontFamily = bodyFont;
    document.getElementById('preview-site-title').style.fontFamily = titleFont;
    document.getElementById('preview-work-title').style.fontFamily = titleFont;
}

function setupSettingsImageUpload(dropZoneId, hiddenInputId, previewId) {
    const dropZone = document.getElementById(dropZoneId);
    if (!dropZone) return;

    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = 'image/*';
    fileInput.style.display = 'none';
    dropZone.parentElement.appendChild(fileInput);

    dropZone.setAttribute('role', 'button');
    dropZone.tabIndex = 0;
    dropZone.onclick = () => fileInput.click();
    dropZone.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); } };

    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
    dropZone.addEventListener('drop', async (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
        const file = e.dataTransfer.files[0];
        if (file) await handleSettingsImageUpload(file, dropZoneId, hiddenInputId, previewId);
    });

    fileInput.onchange = async (e) => {
        const file = e.target.files[0];
        fileInput.value = '';
        if (file) await handleSettingsImageUpload(file, dropZoneId, hiddenInputId, previewId);
    };
}

async function handleSettingsImageUpload(file, dropZoneId, hiddenInputId, previewId) {
    const dropZone = document.getElementById(dropZoneId);
    const originalHtml = dropZone.innerHTML;
    dropZone.innerHTML = '<p>업로드 중...</p>';

    try {
        const fileName = await uploadImage(file, { imageOnly: true });
        document.getElementById(hiddenInputId).value = fileName;
        showSettingsImagePreview(previewId, dropZoneId, `${API_BASE_URL}/files/${encodeURIComponent(fileName)}`, hiddenInputId);
        showToast('이미지가 업로드되었습니다. 설정 저장을 눌러 반영하세요.', 'success');
        markSettingsDirty();
        updateSettingsPreview();
    } catch (error) {
        dropZone.innerHTML = originalHtml;
        showToast(error.message, 'error');
    }
}

function showSettingsImagePreview(previewId, dropZoneId, imageUrl, hiddenInputId) {
    const preview = document.getElementById(previewId);
    const dropZone = document.getElementById(dropZoneId);

    dropZone.style.display = 'none';
    preview.innerHTML = '';
    const img = document.createElement('img');
    img.src = imageUrl;
    img.alt = '미리보기';
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'remove-btn';
    remove.textContent = '삭제';
    remove.onclick = () => removeSettingsImage(previewId, dropZoneId, hiddenInputId);
    preview.appendChild(img);
    preview.appendChild(remove);
}

function removeSettingsImage(previewId, dropZoneId, hiddenInputId) {
    const preview = document.getElementById(previewId);
    const dropZone = document.getElementById(dropZoneId);

    preview.innerHTML = '';
    dropZone.style.display = '';
    dropZone.innerHTML = '<p>이미지를 드래그하거나 클릭하여 선택</p>';
    document.getElementById(hiddenInputId).value = '';
    markSettingsDirty();
    updateSettingsPreview();
}

function reloadSettings() {
    clearSettingsDirty();
    loadSettings();
    showToast('저장된 설정으로 되돌렸습니다.', 'info');
}

async function loadSettings() {
    adminState.settingsLoaded = false;
    try {
        const s = await apiFetch('/settings');

        const set = (id, value) => { document.getElementById(id).value = value || ''; };

        set('setting-site-title', s.siteTitle);
        set('setting-site-subtitle', s.siteSubtitle);
        set('setting-main-intro', s.mainIntroText);
        document.getElementById('setting-show-recent').checked = s.showRecentOnMain !== false;

        ['logo', 'bg-image', 'favicon'].forEach(kind => {
            const map = { logo: ['logoImage', 'setting-logo-image'], 'bg-image': ['backgroundImage', 'setting-bg-image'], favicon: ['favicon', 'setting-favicon'] };
            const [field, hidden] = map[kind];
            if (s[field]) {
                document.getElementById(hidden).value = s[field];
                showSettingsImagePreview(`${kind}-preview`, `${kind}-drop-zone`, `${API_BASE_URL}/files/${encodeURIComponent(s[field])}`, hidden);
            } else {
                removeSettingsImagePreviewSilently(`${kind}-preview`, `${kind}-drop-zone`, hidden);
            }
        });

        setColorValue('setting-bg-color', s.backgroundColor || '#0a0a0a');
        setColorValue('setting-text-color', s.textColor || '#ffffff');
        set('setting-bg-size', s.backgroundSize || 'cover');
        set('setting-bg-repeat', s.backgroundRepeat || 'no-repeat');
        set('setting-bg-attachment', s.backgroundAttachment || 'fixed');

        setColorValue('setting-sidebar-bg', s.sidebarBgColor || '#0a0a0a');
        setColorValue('setting-sidebar-text', s.sidebarTextColor || '#535353');
        setColorValue('setting-sidebar-active', s.sidebarActiveColor || '#ffffff');

        set('setting-title-font-url', s.titleFontUrl);
        set('setting-title-font-family', s.titleFontFamily);
        set('setting-body-font-url', s.bodyFontUrl);
        set('setting-body-font-family', s.bodyFontFamily);
        syncFontPreset('title');
        syncFontPreset('body');

        set('setting-contact-name', s.contactName);
        set('setting-contact-phone', s.contactPhone);
        set('setting-social-email', s.socialEmail);
        set('setting-social-instagram', s.socialInstagram);
        set('setting-social-behance', s.socialBehance);
        set('setting-social-linkedin', s.socialLinkedin);
        set('setting-social-website', s.socialWebsite);
        set('setting-footer-text', s.footerText);

        updateSettingsPreview();
        clearSettingsDirty();
    } catch (error) {
        console.error('설정 로드 실패:', error);
        showToast(`설정을 불러오지 못했습니다: ${error.message}`, 'error');
    } finally {
        adminState.settingsLoaded = true;
    }
}

function removeSettingsImagePreviewSilently(previewId, dropZoneId, hiddenInputId) {
    document.getElementById(previewId).innerHTML = '';
    const dropZone = document.getElementById(dropZoneId);
    dropZone.style.display = '';
    document.getElementById(hiddenInputId).value = '';
}

function setColorValue(id, value) {
    const colorInput = document.getElementById(id);
    const textInput = document.getElementById(id + '-text');
    if (colorInput) colorInput.value = value;
    if (textInput) textInput.value = value;
}

async function handleSaveSettings() {
    const v = id => document.getElementById(id).value.trim();
    const orNull = s => (s ? s : null);

    for (const target of ['title', 'body']) {
        const url = v(`setting-${target}-font-url`);
        if (url && !url.startsWith('https://fonts.googleapis.com')) {
            showToast(`${target === 'title' ? '제목' : '본문'} 폰트 URL은 https://fonts.googleapis.com 으로 시작해야 합니다.`, 'warning');
            return;
        }
        if (url && !v(`setting-${target}-font-family`)) {
            showToast(`${target === 'title' ? '제목' : '본문'} 폰트의 font-family 이름을 입력해주세요.`, 'warning');
            return;
        }
    }
    for (const id of ['setting-social-instagram', 'setting-social-behance', 'setting-social-linkedin', 'setting-social-website']) {
        const url = v(id);
        if (url && !/^https?:\/\//i.test(url)) {
            showToast('소셜/웹사이트 주소는 http:// 또는 https:// 로 시작해야 합니다.', 'warning');
            document.getElementById(id).focus();
            return;
        }
    }

    const settingsData = {
        siteTitle: orNull(v('setting-site-title')),
        siteSubtitle: orNull(v('setting-site-subtitle')),
        mainIntroText: orNull(v('setting-main-intro')),
        showRecentOnMain: document.getElementById('setting-show-recent').checked,
        backgroundColor: v('setting-bg-color'),
        backgroundImage: orNull(v('setting-bg-image')),
        backgroundSize: v('setting-bg-size'),
        backgroundRepeat: v('setting-bg-repeat'),
        backgroundAttachment: v('setting-bg-attachment'),
        backgroundPosition: 'center center',
        textColor: v('setting-text-color'),
        sidebarBgColor: v('setting-sidebar-bg'),
        sidebarTextColor: v('setting-sidebar-text'),
        sidebarActiveColor: v('setting-sidebar-active'),
        titleFontUrl: orNull(v('setting-title-font-url')),
        titleFontFamily: orNull(v('setting-title-font-family')),
        bodyFontUrl: orNull(v('setting-body-font-url')),
        bodyFontFamily: orNull(v('setting-body-font-family')),
        favicon: orNull(v('setting-favicon')),
        logoImage: orNull(v('setting-logo-image')),
        contactName: orNull(v('setting-contact-name')),
        contactPhone: orNull(v('setting-contact-phone')),
        socialInstagram: orNull(v('setting-social-instagram')),
        socialBehance: orNull(v('setting-social-behance')),
        socialLinkedin: orNull(v('setting-social-linkedin')),
        socialWebsite: orNull(v('setting-social-website')),
        socialEmail: orNull(v('setting-social-email')),
        footerText: orNull(v('setting-footer-text'))
    };

    try {
        await apiFetch('/settings', { method: 'POST', json: settingsData });
        clearSettingsDirty();
        showToast('설정이 저장되었습니다. 사이트를 새로고침하면 반영됩니다.', 'success');
    } catch (error) {
        console.error('설정 저장 실패:', error);
        showToast(`설정 저장 실패: ${error.message}`, 'error');
    }
}

// ============================
// 관리자 계정 (아이디 / 비밀번호 변경)
// ============================
async function loadAccount(showErrors) {
    try {
        const info = await apiFetch('/account');
        document.getElementById('header-username').textContent = info.username || '';
        document.getElementById('account-username').textContent = info.username || '-';
        document.getElementById('account-changed-at').textContent = info.passwordChangedAt
            ? new Date(info.passwordChangedAt).toLocaleString('ko-KR')
            : '기록 없음';
        document.getElementById('initial-password-banner').hidden = !info.usingInitialPassword;
    } catch (error) {
        console.error('계정 정보 로드 실패:', error);
        if (showErrors) showToast(`계정 정보를 불러오지 못했습니다: ${error.message}`, 'error');
    }
}

function passwordStrength(pw) {
    if (!pw) return { text: '', level: '' };
    const hasLetter = /[A-Za-z]/.test(pw);
    const hasDigit = /\d/.test(pw);
    const hasSpecial = /[^A-Za-z0-9]/.test(pw);
    if (pw.length < 8 || !hasLetter || !hasDigit) return { text: '8자 이상, 영문+숫자 필요', level: 'weak' };
    if (pw.length >= 12 && hasSpecial) return { text: '강함', level: 'strong' };
    return { text: '보통 (12자 이상 + 특수문자면 더 안전)', level: 'ok' };
}

document.addEventListener('DOMContentLoaded', () => {
    const newPw = document.getElementById('account-new-password');
    const strengthEl = document.getElementById('password-strength');
    if (newPw && strengthEl) {
        newPw.addEventListener('input', () => {
            const st = passwordStrength(newPw.value);
            strengthEl.textContent = st.text;
            strengthEl.className = `password-strength ${st.level}`;
        });
    }
    const show = document.getElementById('account-show-password');
    if (show) {
        show.addEventListener('change', () => {
            ['account-current-password', 'account-new-password', 'account-new-password2'].forEach(id => {
                document.getElementById(id).type = show.checked ? 'text' : 'password';
            });
        });
    }
});

async function handleChangeAccount(event) {
    event.preventDefault();

    const currentPassword = document.getElementById('account-current-password').value;
    const newUsername = document.getElementById('account-new-username').value.trim();
    const newPassword = document.getElementById('account-new-password').value;
    const newPassword2 = document.getElementById('account-new-password2').value;
    const btn = document.getElementById('account-save-btn');

    if (!currentPassword) {
        showToast('현재 비밀번호를 입력해주세요.', 'warning');
        document.getElementById('account-current-password').focus();
        return;
    }
    if (!newUsername && !newPassword) {
        showToast('새 아이디 또는 새 비밀번호 중 하나는 입력해야 합니다.', 'warning');
        return;
    }
    if (newUsername && !/^[A-Za-z0-9._-]{3,64}$/.test(newUsername)) {
        showToast('아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용해 3~64자로 입력해주세요.', 'warning');
        document.getElementById('account-new-username').focus();
        return;
    }
    if (newPassword) {
        const st = passwordStrength(newPassword);
        if (st.level === 'weak') {
            showToast('새 비밀번호는 8자 이상이고 영문과 숫자를 모두 포함해야 합니다.', 'warning');
            document.getElementById('account-new-password').focus();
            return;
        }
        if (newPassword !== newPassword2) {
            showToast('새 비밀번호와 확인 값이 일치하지 않습니다.', 'warning');
            document.getElementById('account-new-password2').focus();
            return;
        }
        if (newPassword === currentPassword) {
            showToast('새 비밀번호가 현재 비밀번호와 같습니다.', 'warning');
            return;
        }
    }

    const payload = { currentPassword };
    if (newUsername) payload.newUsername = newUsername;
    if (newPassword) payload.newPassword = newPassword;

    btn.disabled = true;
    btn.textContent = '저장 중...';
    try {
        const info = await apiFetch('/account/change', { method: 'POST', json: payload });
        document.getElementById('account-form').reset();
        document.getElementById('password-strength').textContent = '';
        if (info.reloginRequired) {
            showToast('아이디가 변경되었습니다. 새 아이디로 다시 로그인해주세요.', 'success', 4000);
            setTimeout(() => { location.href = '/login.html?relogin'; }, 1200);
            return;
        }
        showToast(newPassword ? '비밀번호가 변경되었습니다.' : '계정 정보가 변경되었습니다.', 'success');
        loadAccount(true);
    } catch (error) {
        showToast(`변경 실패: ${error.message}`, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = '변경 저장';
    }
}

// ============================
// 로그아웃
//  CSRF 토큰이 필요하므로 단순 form submit 대신 fetch 로 보냅니다.
// ============================
async function handleLogout() {
    try {
        await fetch('/api/logout', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-XSRF-TOKEN': csrfToken() || '' }
        });
    } catch (e) {
        // 요청이 실패해도 로그인 화면으로 보냅니다.
    }
    location.href = '/login.html?logout';
}
