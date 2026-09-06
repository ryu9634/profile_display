// API Base URL (상대 경로 사용 - 로컬/배포 환경 모두 호환)
const API_BASE_URL = '/api';

// 외부 라이브러리는 필요한 페이지에서만 지연 로드합니다 (첫 화면 속도)
const LIB_URLS = {
    pdfjs: 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js',
    pdfjsWorker: 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js',
    quillJs: 'https://cdn.quilljs.com/1.3.6/quill.js',
    quillCss: 'https://cdn.quilljs.com/1.3.6/quill.snow.css'
};

// 전역 상태 관리
const appState = {
    currentPage: null,
    categories: [],
    posts: {},
    settings: {},
    siteTitle: null,
    logoImage: null,
    contactInfo: null,
    // 상세 팝업 상태
    modal: null,
    modalPosts: [],
    modalIndex: -1,
    modalItems: [],
    modalSelectedItem: 0,
    modalPushedHistory: false,
    lightbox: null,
    lastFocused: null
};

// ============================
// 초기화
// ============================
document.addEventListener('DOMContentLoaded', async () => {
    // 설정이 늦게 와도 화면이 영영 숨겨지지 않도록 안전장치
    const readyTimer = setTimeout(markReady, 2000);

    // 설정과 카테고리는 서로 독립적이므로 동시에 요청
    await Promise.all([loadSiteSettings(), loadCategories()]);
    // 사이드바는 설정(제목·로고·연락처)과 카테고리가 모두 준비된 뒤 한 번만 그립니다
    renderSidebar();
    clearTimeout(readyTimer);
    markReady();

    setupNavigation();
    setupBackToTop();
    setupGlobalKeyboard();
    window.addEventListener('hashchange', handleRoute);
    handleRoute();
});

function markReady() {
    const app = document.querySelector('.app');
    if (app) app.classList.add('ready');
}

// ============================
// 라우팅 (#/카테고리, #/카테고리/게시글ID)
// 주소가 바뀌므로 새로고침·뒤로가기·링크 공유가 가능합니다
// ============================
function parseHash() {
    const raw = (location.hash || '').replace(/^#\/?/, '');
    if (!raw) return { page: 'main', postId: null };
    const [page, postId] = raw.split('/');
    return {
        page: decodeURIComponent(page || 'main'),
        postId: postId ? parseInt(postId, 10) : null
    };
}

async function handleRoute() {
    const { page, postId } = parseHash();
    const known = page === 'main' || appState.categories.some(c => c.id === page);
    const targetPage = known ? page : 'main';

    // 이미 해당 카테고리 화면에 있었다면 이번 해시 변경은 사용자의 클릭(브라우저가 히스토리를 쌓음)
    const alreadyOnPage = targetPage === appState.currentPage;

    if (!alreadyOnPage) {
        await renderPage(targetPage);
    }

    if (postId && targetPage !== 'main') {
        const posts = appState.posts[targetPage] || [];
        const index = posts.findIndex(p => p.id === postId);
        if (index !== -1) {
            if (appState.modal && appState.modalPosts === posts) {
                if (appState.modalIndex !== index) showPostAt(index);
            } else {
                openPostDetail(posts, index, alreadyOnPage);
            }
            return;
        }
        // 존재하지 않는 게시글이면 주소만 정리
        history.replaceState(null, '', `#/${encodeURIComponent(targetPage)}`);
    }

    if (appState.modal) {
        closePostDetail(false);
    }
}

// ============================
// 사이트 설정 로드 및 적용
// ============================
async function loadSiteSettings() {
    try {
        const response = await fetch(`${API_BASE_URL}/settings`);
        if (!response.ok) return;
        const s = await response.json();
        appState.settings = s;
        applySiteSettings(s);
    } catch (error) {
        console.error('사이트 설정 로드 실패:', error);
    }
}

function applySiteSettings(s) {
    const root = document.documentElement;

    // 색상
    if (s.backgroundColor) root.style.setProperty('--bg-color', s.backgroundColor);
    if (s.textColor) root.style.setProperty('--text-color', s.textColor);
    if (s.sidebarBgColor) root.style.setProperty('--sidebar-bg', s.sidebarBgColor);
    if (s.sidebarTextColor) root.style.setProperty('--sidebar-text', s.sidebarTextColor);
    if (s.sidebarActiveColor) root.style.setProperty('--sidebar-active', s.sidebarActiveColor);

    // 배경 이미지
    if (s.backgroundImage) {
        const appEl = document.querySelector('.app');
        if (appEl) {
            appEl.style.backgroundImage = `url(${fileUrl(s.backgroundImage)})`;
            appEl.style.backgroundSize = s.backgroundSize || 'cover';
            appEl.style.backgroundRepeat = s.backgroundRepeat || 'no-repeat';
            appEl.style.backgroundAttachment = s.backgroundAttachment || 'fixed';
            appEl.style.backgroundPosition = s.backgroundPosition || 'center center';
        }
    }

    // 브라우저 탭 제목 및 공유용 메타
    const tabTitle = s.siteSubtitle || s.siteTitle;
    if (tabTitle) {
        document.title = tabTitle;
        const og = document.querySelector('meta[property="og:title"]');
        if (og) og.setAttribute('content', tabTitle);
    }

    // 파비콘
    if (s.favicon) {
        let link = document.querySelector("link[rel*='icon']") || document.createElement('link');
        link.rel = 'icon';
        link.href = fileUrl(s.favicon);
        document.head.appendChild(link);
    }

    // Google Fonts
    [s.titleFontUrl, s.bodyFontUrl].forEach(url => {
        if (url && url.startsWith('https://fonts.googleapis.com')) {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = url;
            document.head.appendChild(link);
        }
    });

    // 폰트
    if (s.titleFontFamily) root.style.setProperty('--title-font', s.titleFontFamily);
    if (s.bodyFontFamily) root.style.setProperty('--body-font', s.bodyFontFamily);

    // 사이트 정보 저장
    if (s.siteTitle) appState.siteTitle = s.siteTitle;
    if (s.logoImage) appState.logoImage = s.logoImage;

    // 푸터
    if (s.footerText) {
        const footer = document.getElementById('site-footer');
        if (footer) {
            footer.textContent = s.footerText;
            footer.style.display = 'block';
        }
    }

    appState.contactInfo = {
        name: s.contactName,
        phone: s.contactPhone,
        email: s.socialEmail,
        instagram: s.socialInstagram,
        behance: s.socialBehance,
        website: s.socialWebsite,
        linkedin: s.socialLinkedin
    };
}

// ============================
// 데이터 로드
// ============================
async function loadCategories() {
    try {
        const response = await fetch(`${API_BASE_URL}/categories`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        appState.categories = await response.json();
    } catch (error) {
        console.error('카테고리 로드 실패:', error);
        appState.categories = [
            { id: 'main', name: 'Main', type: 'PHOTO' },
            { id: 'artwork', name: 'Art work', type: 'PHOTO' },
            { id: 'cv', name: 'CV', type: 'PHOTO' }
        ];
    }
}

async function loadPostsByCategory(categoryId) {
    try {
        const response = await fetch(`${API_BASE_URL}/posts/category/${encodeURIComponent(categoryId)}`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const posts = await response.json();
        appState.posts[categoryId] = posts;
        return posts;
    } catch (error) {
        console.error(`게시글 로드 실패 (${categoryId}):`, error);
        return null;
    }
}

async function loadAllPosts() {
    try {
        const response = await fetch(`${API_BASE_URL}/posts`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    } catch (error) {
        console.error('전체 게시글 로드 실패:', error);
        return null;
    }
}

// ============================
// 사이드바 렌더링
// ============================
function renderSidebar() {
    const navMenu = document.querySelector('.nav-menu');
    navMenu.innerHTML = '';

    const oldContact = document.querySelector('.sidebar-contact');
    if (oldContact) oldContact.remove();

    const mainCategory = appState.categories.find(cat => cat.id === 'main');
    if (mainCategory) {
        const li = document.createElement('li');
        const btn = document.createElement('a');
        btn.className = 'nav-link nav-home';
        btn.href = '#/';
        btn.setAttribute('data-page', 'main');

        if (appState.logoImage) {
            const logo = document.createElement('img');
            logo.src = fileUrl(appState.logoImage);
            logo.alt = '';
            logo.className = 'nav-logo';
            btn.appendChild(logo);
        }

        const titleSpan = document.createElement('span');
        titleSpan.textContent = appState.siteTitle || 'Portfolio';
        btn.appendChild(titleSpan);

        li.appendChild(btn);
        navMenu.appendChild(li);
    }

    appState.categories.forEach(category => {
        if (category.id === 'main') return;
        const li = document.createElement('li');
        const btn = document.createElement('a');
        btn.className = 'nav-link';
        btn.href = `#/${encodeURIComponent(category.id)}`;
        btn.textContent = category.name;
        btn.setAttribute('data-page', category.id);
        li.appendChild(btn);
        navMenu.appendChild(li);
    });

    updateActiveNav();

    // 연락처 정보
    const info = appState.contactInfo;
    if (info && (info.name || info.phone || info.email || info.instagram || info.behance || info.website || info.linkedin)) {
        const contactDiv = document.createElement('div');
        contactDiv.className = 'sidebar-contact';

        if (info.name) {
            const nameEl = document.createElement('p');
            nameEl.className = 'contact-name';
            nameEl.textContent = info.name;
            contactDiv.appendChild(nameEl);
        }
        if (info.phone) {
            const phoneEl = document.createElement('a');
            phoneEl.className = 'contact-item';
            phoneEl.href = `tel:${info.phone.replace(/[^+\d]/g, '')}`;
            phoneEl.textContent = info.phone;
            contactDiv.appendChild(phoneEl);
        }
        if (info.email) {
            const emailEl = document.createElement('a');
            emailEl.className = 'contact-item';
            emailEl.href = `mailto:${info.email}`;
            emailEl.textContent = info.email;
            contactDiv.appendChild(emailEl);
        }

        const socials = [
            { url: info.instagram, label: 'Instagram', icon: instagramIcon() },
            { url: info.behance, label: 'Behance', icon: null },
            { url: info.linkedin, label: 'LinkedIn', icon: null },
            { url: info.website, label: 'Website', icon: null }
        ].filter(s => s.url);

        if (socials.length > 0) {
            const row = document.createElement('div');
            row.className = 'contact-socials';
            socials.forEach(s => {
                const a = document.createElement('a');
                a.href = s.url;
                a.target = '_blank';
                a.rel = 'noopener noreferrer';
                a.className = 'contact-social';
                a.setAttribute('aria-label', s.label);
                a.title = s.label;
                if (s.icon) {
                    a.innerHTML = s.icon;
                } else {
                    a.textContent = s.label;
                }
                row.appendChild(a);
            });
            contactDiv.appendChild(row);
        }

        navMenu.parentElement.appendChild(contactDiv);
    }
}

function instagramIcon() {
    return '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"/></svg>';
}

function updateActiveNav() {
    document.querySelectorAll('.nav-menu .nav-link').forEach(link => {
        const isActive = link.getAttribute('data-page') === appState.currentPage;
        link.classList.toggle('active', isActive);
        if (isActive) {
            link.setAttribute('aria-current', 'page');
        } else {
            link.removeAttribute('aria-current');
        }
    });
}

function setupNavigation() {
    // 링크(href="#/...")의 기본 동작이 곧 라우팅이므로 별도 처리가 필요 없습니다.
    // 이미 열려 있는 페이지를 다시 누르면 맨 위로 스크롤합니다.
    document.querySelector('.nav-menu').addEventListener('click', (e) => {
        const link = e.target.closest('.nav-link');
        if (!link) return;
        if (link.getAttribute('data-page') === appState.currentPage && !appState.modal) {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });
}

// ============================
// 페이지 렌더링
// ============================
async function renderPage(page) {
    appState.currentPage = page;
    updateActiveNav();
    window.scrollTo({ top: 0 });

    const contentDiv = document.getElementById('page-content');

    if (page === 'main') {
        contentDiv.style.opacity = '0';
        await renderMainPage(contentDiv);
        requestAnimationFrame(() => { contentDiv.style.opacity = '1'; });
        return;
    }

    contentDiv.innerHTML = '<div class="loading-spinner" role="status" aria-label="불러오는 중"><div class="spinner"></div></div>';

    const posts = await loadPostsByCategory(page);
    // 로딩 중에 다른 페이지로 이동했으면 결과를 버립니다
    if (appState.currentPage !== page) return;

    contentDiv.style.opacity = '0';
    if (posts === null) {
        contentDiv.innerHTML = `
            <div class="error-state">
                <p>Failed to load content.</p>
                <button class="retry-btn" type="button">Retry</button>
            </div>`;
        contentDiv.querySelector('.retry-btn').addEventListener('click', () => {
            appState.currentPage = null;
            renderPage(page);
        });
    } else if (page === 'cv') {
        renderCvPage(contentDiv, posts);
    } else {
        renderPostsPage(contentDiv, posts, page);
    }
    requestAnimationFrame(() => { contentDiv.style.opacity = '1'; });
}

// 메인 페이지: 소개 문구 + 최근 작품
async function renderMainPage(container) {
    const s = appState.settings || {};
    const showRecent = s.showRecentOnMain !== false;
    const title = appState.siteTitle || '';
    const intro = s.mainIntroText || '';

    let html = '<div class="main-page">';
    if (title || intro) {
        html += '<section class="main-hero">';
        if (title) html += `<h1 class="main-title">${escapeHtml(title)}</h1>`;
        if (intro) html += `<p class="main-intro">${escapeHtml(intro)}</p>`;
        html += '</section>';
    }
    html += '<section class="main-recent" id="main-recent"></section></div>';
    container.innerHTML = html;

    if (!showRecent) return;

    const recentEl = container.querySelector('#main-recent');
    recentEl.innerHTML = '<div class="loading-spinner small"><div class="spinner"></div></div>';

    const all = await loadAllPosts();
    if (appState.currentPage !== 'main') return;

    if (!all) {
        recentEl.innerHTML = '';
        return;
    }

    const categoryNames = {};
    appState.categories.forEach(c => { categoryNames[c.id] = c.name; });

    const recent = all
        .filter(p => p.categoryId !== 'cv' && p.categoryId !== 'main' && p.thumbnail && categoryNames[p.categoryId])
        .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        .slice(0, 8);

    if (recent.length === 0) {
        recentEl.innerHTML = '';
        // 소개 문구도 작품도 없으면 첫 카테고리로 안내
        if (!title && !intro) {
            const first = appState.categories.find(c => c.id !== 'main');
            if (first) {
                recentEl.innerHTML = `<div class="empty-state"><a class="retry-btn" href="#/${encodeURIComponent(first.id)}">${escapeHtml(first.name)} 보기</a></div>`;
            }
        }
        return;
    }

    recentEl.innerHTML = `
        <h2 class="main-recent-title">Recent works</h2>
        <div class="highlight-grid">
            ${recent.map(post => `
                <a class="highlight-item" href="#/${encodeURIComponent(post.categoryId)}/${post.id}">
                    <div class="highlight-image">${imageTag(post.thumbnail, post.title)}</div>
                    <div class="highlight-caption">
                        <span class="highlight-title">${escapeHtml(post.title)}</span>
                        <span class="highlight-category">${escapeHtml(categoryNames[post.categoryId])}</span>
                    </div>
                </a>
            `).join('')}
        </div>`;
}

// CV 페이지 렌더링 (PDF.js는 이 페이지에서만 로드)
function renderCvPage(container, posts) {
    const pdfPost = posts.find(p => p.thumbnail && p.thumbnail.toLowerCase().endsWith('.pdf'));

    if (!pdfPost) {
        if (posts.length > 0) {
            renderPostsPage(container, posts, 'cv');
        } else {
            container.innerHTML = '<div class="empty-state"><p>CV has not been uploaded yet.</p></div>';
        }
        return;
    }

    container.innerHTML = `
        <div class="cv-toolbar">
            <a class="cv-download" href="${fileUrl(pdfPost.thumbnail)}" target="_blank" rel="noopener">Open PDF</a>
        </div>
        <div class="cv-pages" id="cv-pages-container">
            <div class="loading-spinner"><div class="spinner"></div></div>
        </div>`;
    const pagesContainer = document.getElementById('cv-pages-container');
    const pdfUrl = fileUrl(pdfPost.thumbnail);

    loadScript(LIB_URLS.pdfjs).then(() => {
        pdfjsLib.GlobalWorkerOptions.workerSrc = LIB_URLS.pdfjsWorker;
        return pdfjsLib.getDocument(pdfUrl).promise;
    }).then(pdf => {
        pagesContainer.innerHTML = '';
        // 페이지 순서를 보장하기 위해 자리를 먼저 만들어 둡니다
        const slots = [];
        for (let i = 1; i <= pdf.numPages; i++) {
            const canvas = document.createElement('canvas');
            canvas.className = 'cv-page-canvas';
            canvas.setAttribute('aria-label', `CV page ${i}`);
            pagesContainer.appendChild(canvas);
            slots.push(canvas);
        }
        for (let i = 1; i <= pdf.numPages; i++) {
            pdf.getPage(i).then(page => {
                const viewport = page.getViewport({ scale: 2 });
                const canvas = slots[i - 1];
                canvas.width = viewport.width;
                canvas.height = viewport.height;
                page.render({ canvasContext: canvas.getContext('2d'), viewport });
            });
        }
    }).catch(err => {
        console.error('PDF 로드 실패:', err);
        pagesContainer.innerHTML = `<div class="empty-state"><p>Could not display the CV. <a href="${fileUrl(pdfPost.thumbnail)}" target="_blank" rel="noopener">Open the PDF directly</a>.</p></div>`;
    });
}

// 게시글 목록 페이지
function renderPostsPage(container, posts, categoryId) {
    const html = `
        <div class="artwork-container">
            <div class="artwork-grid">
                ${posts.length === 0
                    ? '<div class="empty-state"><p>No works have been added yet.</p></div>'
                    : posts.map((post, index) => `
                        <a class="artwork-item" href="#/${encodeURIComponent(categoryId)}/${post.id}" data-index="${index}">
                            <div class="artwork-image-placeholder ${post.thumbnail ? 'has-image' : ''}">
                                ${post.thumbnail && !post.thumbnail.toLowerCase().endsWith('.pdf')
                                    ? imageTag(post.thumbnail, post.title)
                                    : '<div class="placeholder-text">Image</div>'
                                }
                            </div>
                            <div class="artwork-info">
                                <h2 class="artwork-title">${escapeHtml(post.title)}</h2>
                                ${metaLine(post) ? `<p class="artwork-details">${metaLine(post)}</p>` : ''}
                            </div>
                        </a>
                    `).join('')
                }
            </div>
        </div>
    `;
    container.innerHTML = html;
}

// ============================
// 게시글 상세 팝업
// ============================
function openPostDetail(posts, index, pushHistory) {
    appState.lastFocused = document.activeElement;

    if (!appState.modal) {
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closePostDetail(true);
        });
        document.body.appendChild(modal);
        document.body.classList.add('modal-open');
        appState.modal = modal;
    }

    appState.modalPosts = posts;
    appState.modalPushedHistory = !!pushHistory;
    showPostAt(index);
}

function showPostAt(index) {
    const posts = appState.modalPosts;
    const post = posts[index];
    if (!post) return;
    appState.modalIndex = index;

    // 주소 동기화 (이전/다음 이동은 히스토리를 쌓지 않음)
    const wanted = `#/${encodeURIComponent(post.categoryId)}/${post.id}`;
    if (location.hash !== wanted) {
        history.replaceState(null, '', wanted);
    }

    const modal = appState.modal;
    modal.setAttribute('aria-label', post.title || 'Detail');

    if (post.contentType === 'HTML') {
        renderHtmlDetail(modal, post);
    } else {
        renderSplitDetail(modal, post);
    }

    renderPostNav(modal, posts, index);
    setupModalClose(modal);
    modal.scrollTop = 0;

    const closeBtn = modal.querySelector('.close-modal');
    if (closeBtn) closeBtn.focus({ preventScroll: true });
}

function renderPostNav(modal, posts, index) {
    if (posts.length < 2) return;
    const container = modal.querySelector('.detail-container');
    if (!container) return;

    const nav = document.createElement('div');
    nav.className = 'post-nav';
    nav.innerHTML = `
        <button type="button" class="post-nav-btn post-nav-prev" aria-label="Previous work" ${index === 0 ? 'disabled' : ''}>&#10094;</button>
        <span class="post-nav-counter">${index + 1} / ${posts.length}</span>
        <button type="button" class="post-nav-btn post-nav-next" aria-label="Next work" ${index === posts.length - 1 ? 'disabled' : ''}>&#10095;</button>
    `;
    nav.querySelector('.post-nav-prev').addEventListener('click', () => stepPost(-1));
    nav.querySelector('.post-nav-next').addEventListener('click', () => stepPost(1));
    container.appendChild(nav);
}

function stepPost(direction) {
    if (!appState.modal) return;
    const next = appState.modalIndex + direction;
    if (next < 0 || next >= appState.modalPosts.length) return;
    showPostAt(next);
}

function closePostDetail(updateHistory) {
    if (appState.lightbox) closeLightbox();
    const modal = appState.modal;
    if (!modal) return;
    modal.remove();
    appState.modal = null;
    appState.modalPosts = [];
    appState.modalIndex = -1;
    document.body.classList.remove('modal-open');

    if (updateHistory) {
        if (appState.modalPushedHistory && history.length > 1) {
            // 우리가 쌓은 히스토리면 뒤로가기로 정리 → hashchange가 처리
            history.back();
        } else {
            history.replaceState(null, '', `#/${encodeURIComponent(appState.currentPage || '')}`);
        }
    }
    appState.modalPushedHistory = false;

    if (appState.lastFocused && document.body.contains(appState.lastFocused)) {
        appState.lastFocused.focus({ preventScroll: true });
    }
}

// PHOTO/ARTICLE용 분할 레이아웃 렌더링
function renderSplitDetail(modal, post) {
    const items = collectMediaItems(post);
    appState.modalItems = items;
    appState.modalSelectedItem = 0;

    const thumbsHtml = items.map((item, i) => `
        <button type="button" class="thumb-item ${i === 0 ? 'active' : ''}" data-index="${i}" aria-label="${item.type === 'video' ? 'Video' : 'Image'} ${i + 1}">
            ${item.type === 'video'
                ? `<img src="${item.thumbnailUrl}" alt=""><div class="thumb-play-icon">&#9654;</div>`
                : imageTag(item.fileName, '')
            }
        </button>
    `).join('');

    modal.innerHTML = `
        <div class="detail-container">
            <button type="button" class="close-modal" aria-label="Close">&times;</button>
            ${items.length > 1 ? `
            <div class="detail-left">
                <div class="thumb-strip">${thumbsHtml}</div>
            </div>` : ''}
            <div class="detail-right">
                <div class="detail-main-view"></div>
                <div class="detail-info">
                    <h2 class="detail-title">${escapeHtml(post.title)}</h2>
                    ${metaLine(post) ? `<p class="detail-meta">${metaLine(post)}</p>` : ''}
                    <div class="detail-description"></div>
                    <div class="detail-image-desc"></div>
                </div>
            </div>
        </div>
    `;

    renderDescription(post, modal.querySelector('.detail-description'));

    if (items.length > 0) {
        renderMainView(modal, items, 0);
    } else {
        modal.querySelector('.detail-main-view').innerHTML = '';
    }

    modal.querySelectorAll('.thumb-item').forEach(thumb => {
        thumb.addEventListener('click', () => {
            const index = parseInt(thumb.dataset.index, 10);
            appState.modalSelectedItem = index;
            renderMainView(modal, items, index);
            modal.querySelectorAll('.thumb-item').forEach(t => t.classList.remove('active'));
            thumb.classList.add('active');
        });
    });

    modal.querySelector('.detail-main-view').addEventListener('click', (e) => {
        if (e.target.classList && e.target.classList.contains('main-view-image')) {
            openLightbox(items, appState.modalSelectedItem);
        }
    });
}

function collectMediaItems(post) {
    const items = [];

    if (post.images && post.images.length > 0) {
        const sorted = [...post.images].sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));

        sorted.forEach(img => {
            if (img.mediaType === 'VIDEO') {
                const videoId = extractYouTubeId(img.imageUrl);
                if (videoId) {
                    items.push({
                        type: 'video',
                        videoId,
                        thumbnailUrl: `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`,
                        showDescription: img.showDescription !== false,
                        showImageDescription: img.showImageDescription || false,
                        descriptionPosition: img.descriptionPosition || 'right'
                    });
                }
            } else if (img.imageUrl) {
                items.push({
                    type: 'image',
                    fileName: img.imageUrl,
                    url: fileUrl(img.imageUrl),
                    description: img.imageDescription,
                    showDescription: img.showDescription !== false,
                    showImageDescription: img.showImageDescription || false,
                    descriptionPosition: img.descriptionPosition || 'right'
                });
            }
        });
    } else {
        // 하위호환: images 비어있으면 기존 thumbnail + videoUrl 폴백
        if (post.thumbnail) {
            items.push({
                type: 'image',
                fileName: post.thumbnail,
                url: fileUrl(post.thumbnail),
                description: null,
                showDescription: true,
                descriptionPosition: 'right'
            });
        }
        if (post.videoUrl) {
            const videoId = extractYouTubeId(post.videoUrl);
            if (videoId) {
                items.push({
                    type: 'video',
                    videoId,
                    thumbnailUrl: `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`,
                    showDescription: false,
                    descriptionPosition: 'right'
                });
            }
        }
    }
    return items;
}

// 우측 메인 뷰 업데이트
function renderMainView(modal, items, index) {
    const mainView = modal.querySelector('.detail-main-view');
    const detailInfo = modal.querySelector('.detail-info');
    const detailRight = modal.querySelector('.detail-right');
    const imgDescArea = modal.querySelector('.detail-image-desc');
    const item = items[index];

    const hasImgDesc = item.showImageDescription && item.description;

    let contentHtml;
    if (item.type === 'video') {
        contentHtml = `
            <div class="main-video-wrapper">
                <iframe src="https://www.youtube.com/embed/${item.videoId}?rel=0"
                    title="Video" frameborder="0" allowfullscreen
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"></iframe>
            </div>`;
    } else {
        contentHtml = `<img src="${item.url}" alt="" class="main-view-image" title="Click to enlarge">`;
    }

    if (hasImgDesc && item.descriptionPosition === 'bottom') {
        mainView.innerHTML = `${contentHtml}<p class="main-view-desc">${escapeHtml(item.description)}</p>`;
    } else {
        mainView.innerHTML = contentHtml;
    }

    if (imgDescArea) {
        if (hasImgDesc && item.descriptionPosition === 'right') {
            imgDescArea.innerHTML = `<div class="image-desc-in-info">${escapeHtml(item.description)}</div>`;
            imgDescArea.style.display = '';
        } else {
            imgDescArea.innerHTML = '';
            imgDescArea.style.display = 'none';
        }
    }

    detailRight.classList.remove('layout-fullwidth', 'layout-right', 'layout-bottom');
    const showRightPanel = item.showDescription || (hasImgDesc && item.descriptionPosition === 'right');

    if (showRightPanel) {
        detailRight.classList.add('layout-right');
        if (detailInfo) detailInfo.style.display = '';
    } else {
        detailRight.classList.add('layout-fullwidth');
        if (detailInfo) detailInfo.style.display = 'none';
    }

    ['.detail-title', '.detail-meta', '.detail-description'].forEach(sel => {
        const el = modal.querySelector(sel);
        if (el) el.style.display = item.showDescription ? '' : 'none';
    });
}

// 설명 렌더링 (ARTICLE: Quill 델타 → HTML, PHOTO: 일반 텍스트)
function renderDescription(post, target) {
    if (!target) return;
    if (!post.description) {
        target.innerHTML = '';
        return;
    }

    if (post.contentType === 'ARTICLE') {
        let delta;
        try {
            delta = JSON.parse(post.description);
        } catch (e) {
            target.innerHTML = `<p style="white-space: pre-wrap;">${escapeHtml(post.description)}</p>`;
            return;
        }
        target.innerHTML = '<p class="detail-loading">Loading…</p>';
        Promise.all([loadScript(LIB_URLS.quillJs), loadStyle(LIB_URLS.quillCss)]).then(() => {
            const tempDiv = document.createElement('div');
            tempDiv.style.display = 'none';
            document.body.appendChild(tempDiv);
            try {
                const tempQuill = new Quill(tempDiv, { readOnly: true });
                tempQuill.setContents(delta);
                target.innerHTML = `<div class="ql-snow"><div class="ql-editor">${tempQuill.root.innerHTML}</div></div>`;
            } catch (e) {
                console.error('Quill 렌더링 실패:', e);
                target.innerHTML = `<p>${escapeHtml(post.description)}</p>`;
            } finally {
                document.body.removeChild(tempDiv);
            }
        }).catch(() => {
            target.innerHTML = `<p>${escapeHtml(post.description)}</p>`;
        });
        return;
    }

    target.innerHTML = `<p style="white-space: pre-wrap;">${escapeHtml(post.description)}</p>`;
}

// HTML 타입 전체 너비 렌더링 (스크립트 허용, 높이는 iframe 안에서 알려줌)
function renderHtmlDetail(modal, post) {
    modal.innerHTML = `
        <div class="detail-container detail-html-fullwidth">
            <button type="button" class="close-modal" aria-label="Close">&times;</button>
            <div class="detail-html-header">
                <h2 class="detail-title">${escapeHtml(post.title)}</h2>
            </div>
            <div class="detail-html-content">
                <iframe class="html-sandbox" title="${escapeHtml(post.title)}" sandbox="allow-scripts allow-popups"
                    style="width:100%; border:none; min-height:60vh;"></iframe>
            </div>
        </div>
    `;

    const iframe = modal.querySelector('.html-sandbox');
    if (!iframe || !post.htmlContent) return;

    const reporter = `<script>(function(){function r(){try{parent.postMessage({type:'portfolio-html-height',height:document.documentElement.scrollHeight},'*');}catch(e){}}
window.addEventListener('load',r);window.addEventListener('resize',r);setInterval(r,800);r();})();<\/script>`;
    iframe.srcdoc = `<base target="_blank">${post.htmlContent}${reporter}`;

    const onMessage = (e) => {
        if (e.source !== iframe.contentWindow) return;
        if (!e.data || e.data.type !== 'portfolio-html-height') return;
        const h = Math.max(200, Math.min(20000, Number(e.data.height) || 0));
        iframe.style.height = `${h + 20}px`;
    };
    window.addEventListener('message', onMessage);
    // 모달이 사라지면 리스너 정리
    const observer = new MutationObserver(() => {
        if (!document.body.contains(iframe)) {
            window.removeEventListener('message', onMessage);
            observer.disconnect();
        }
    });
    observer.observe(document.body, { childList: true, subtree: true });
}

// ============================
// 이미지 라이트박스 (키보드·스와이프 지원)
// ============================
function openLightbox(items, startIndex) {
    const imageItems = items.filter(item => item.type === 'image');
    if (imageItems.length === 0) return;

    const startItem = items[startIndex];
    let currentIndex = imageItems.indexOf(startItem);
    if (currentIndex === -1) currentIndex = 0;

    const lightbox = document.createElement('div');
    lightbox.className = 'lightbox-overlay';
    lightbox.setAttribute('role', 'dialog');
    lightbox.setAttribute('aria-label', 'Enlarged image');
    lightbox.innerHTML = `
        <button type="button" class="lightbox-close" aria-label="Close">&times;</button>
        ${imageItems.length > 1 ? '<button type="button" class="lightbox-prev" aria-label="Previous image">&#10094;</button>' : ''}
        <div class="lightbox-image-container">
            <img src="${imageItems[currentIndex].url}" alt="">
        </div>
        ${imageItems.length > 1 ? '<button type="button" class="lightbox-next" aria-label="Next image">&#10095;</button>' : ''}
        ${imageItems.length > 1 ? `<div class="lightbox-counter">${currentIndex + 1} / ${imageItems.length}</div>` : ''}
    `;
    document.body.appendChild(lightbox);
    appState.lightbox = lightbox;

    const imgEl = lightbox.querySelector('.lightbox-image-container img');

    function update() {
        imgEl.src = imageItems[currentIndex].url;
        const counter = lightbox.querySelector('.lightbox-counter');
        if (counter) counter.textContent = `${currentIndex + 1} / ${imageItems.length}`;
        // 다음 이미지 미리 받아두기
        const next = imageItems[(currentIndex + 1) % imageItems.length];
        if (next) { const pre = new Image(); pre.src = next.url; }
    }

    lightbox.step = (dir) => {
        if (imageItems.length < 2) return;
        currentIndex = (currentIndex + dir + imageItems.length) % imageItems.length;
        update();
    };

    const prevBtn = lightbox.querySelector('.lightbox-prev');
    const nextBtn = lightbox.querySelector('.lightbox-next');
    if (prevBtn) prevBtn.onclick = (e) => { e.stopPropagation(); lightbox.step(-1); };
    if (nextBtn) nextBtn.onclick = (e) => { e.stopPropagation(); lightbox.step(1); };

    lightbox.querySelector('.lightbox-close').onclick = closeLightbox;
    lightbox.onclick = (e) => { if (e.target === lightbox || e.target.classList.contains('lightbox-image-container')) closeLightbox(); };

    // 모바일 스와이프
    let touchStartX = null;
    let touchStartY = null;
    lightbox.addEventListener('touchstart', (e) => {
        if (e.touches.length !== 1) return;
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
    }, { passive: true });
    lightbox.addEventListener('touchend', (e) => {
        if (touchStartX === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX;
        const dy = e.changedTouches[0].clientY - touchStartY;
        touchStartX = null;
        touchStartY = null;
        if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) {
            lightbox.step(dx < 0 ? 1 : -1);
        }
    }, { passive: true });

    update();
    lightbox.querySelector('.lightbox-close').focus({ preventScroll: true });
}

function closeLightbox() {
    if (!appState.lightbox) return;
    appState.lightbox.remove();
    appState.lightbox = null;
    const closeBtn = appState.modal && appState.modal.querySelector('.close-modal');
    if (closeBtn) closeBtn.focus({ preventScroll: true });
}

// ============================
// 키보드: 한 곳에서만 처리해 라이트박스 Esc가 상세 팝업까지 닫는 문제를 방지
// ============================
function setupGlobalKeyboard() {
    document.addEventListener('keydown', (e) => {
        if (appState.lightbox) {
            if (e.key === 'Escape') { e.preventDefault(); closeLightbox(); }
            else if (e.key === 'ArrowLeft') { e.preventDefault(); appState.lightbox.step(-1); }
            else if (e.key === 'ArrowRight') { e.preventDefault(); appState.lightbox.step(1); }
            return;
        }
        if (appState.modal) {
            if (e.key === 'Escape') { e.preventDefault(); closePostDetail(true); }
            else if (e.key === 'ArrowLeft') { e.preventDefault(); stepPost(-1); }
            else if (e.key === 'ArrowRight') { e.preventDefault(); stepPost(1); }
        }
    });
}

// 모달 닫기 버튼
function setupModalClose(modal) {
    const closeBtn = modal.querySelector('.close-modal');
    if (closeBtn) closeBtn.onclick = () => closePostDetail(true);
}

// ============================
// 유틸리티
// ============================
function fileUrl(fileName) {
    return `${API_BASE_URL}/files/${encodeURIComponent(fileName)}`;
}

// 업로드 시 함께 생성되는 축소본 이름 (없으면 원본으로 폴백)
function thumbUrl(fileName) {
    const dot = fileName.lastIndexOf('.');
    const name = dot < 0 ? `${fileName}_thumb` : `${fileName.slice(0, dot)}_thumb${fileName.slice(dot)}`;
    return fileUrl(name);
}

function imageTag(fileName, alt) {
    const full = fileUrl(fileName);
    const lower = fileName.toLowerCase();
    const canThumb = /\.(jpe?g|png)$/.test(lower);
    const src = canThumb ? thumbUrl(fileName) : full;
    return `<img src="${src}" data-full="${full}" alt="${escapeHtml(alt)}" loading="lazy" decoding="async"
        onerror="if(this.dataset.full&&this.src!==this.dataset.full){this.src=this.dataset.full;}else{this.onerror=null;}">`;
}

function metaLine(post) {
    return [post.year, post.medium, post.size]
        .map(v => (v || '').trim())
        .filter(v => v && v !== '-')
        .map(escapeHtml)
        .join(', ');
}

function extractYouTubeId(url) {
    if (!url) return null;
    const patterns = [
        /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/,
        /^([a-zA-Z0-9_-]{11})$/
    ];
    for (const pattern of patterns) {
        const match = url.match(pattern);
        if (match) return match[1];
    }
    return null;
}

function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    const div = document.createElement('div');
    div.textContent = String(text);
    return div.innerHTML;
}

const loadedAssets = {};
function loadScript(url) {
    if (!loadedAssets[url]) {
        loadedAssets[url] = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = url;
            script.onload = resolve;
            script.onerror = () => { delete loadedAssets[url]; reject(new Error(`Failed to load ${url}`)); };
            document.head.appendChild(script);
        });
    }
    return loadedAssets[url];
}

function loadStyle(url) {
    if (!loadedAssets[url]) {
        loadedAssets[url] = new Promise((resolve) => {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = url;
            link.onload = resolve;
            link.onerror = resolve;
            document.head.appendChild(link);
        });
    }
    return loadedAssets[url];
}

// Back-to-top 버튼
function setupBackToTop() {
    const backToTop = document.getElementById('back-to-top');
    if (!backToTop) return;

    window.addEventListener('scroll', () => {
        backToTop.classList.toggle('visible', window.scrollY > 300);
    }, { passive: true });
    backToTop.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
}
