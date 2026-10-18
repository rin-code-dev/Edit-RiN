/* Navigation progressively enhances the static manual; it never executes sketch code. */
(() => {
  'use strict';
  const root = document.documentElement;
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const chapters = $$('[data-chapter]');
  const topics = $$('[data-topic]');
  const input = $('#guide-search');
  const results = $('#search-results');
  const list = $('#result-list');
  const toc = $('#page-toc');
  const sidebar = $('#sidebar');
  const menu = $('#menu-toggle');
  let current = chapters[0];
  let fromHistory = false;
  let language = 'ja';
  let pendingScroll;
  let observer;
  const text = pair => pair[language === 'ja' ? 0 : 1];
  const title = el => $('h2,h3', el).cloneNode(true);
  const label = el => $(`[lang=${language}]`, $('h2,h3', el)).textContent;
  const announce = pair => { $('#announcement').textContent = text(pair); };
  const searchIndex = topics.map(topic => ({ topic, content: topic.textContent.toLocaleLowerCase() }));
  try {
    const saved = localStorage.getItem('editrin_guide_lang');
    if (saved === 'ja' || saved === 'en') language = saved;
  } catch { /* The manual works when storage is blocked. */ }

  function remember() {
    if (fromHistory) return;
    history.replaceState({guide: true, query: input.value, scroll: window.scrollY}, '', location.href);
  }
  function closeMenu() {
    sidebar.classList.remove('open');
    root.classList.remove('menu-open');
    menu.setAttribute('aria-expanded', 'false');
  }
  let openDrawerChapterId = null;

  function renderDrawerSubnav(chapterId, a) {
    const chEl = document.getElementById(chapterId);
    if (!chEl || !a) return;
    const subnav = document.createElement('div');
    subnav.className = 'sidebar-subnav is-drawer-subnav';
    subnav.dataset.parentChapter = chapterId;

    const topA = document.createElement('a');
    topA.href = `#${chapterId}`;
    topA.className = 'drawer-top-link';
    topA.innerHTML = `<span>${text(['この章の先頭へ移動', 'Go to chapter top'])}</span> <span aria-hidden="true">→</span>`;
    subnav.append(topA);

    $$('[data-topic]', chEl).forEach(topic => {
      const topicA = document.createElement('a');
      topicA.href = `#${topic.id}`;
      topicA.textContent = label(topic);
      subnav.append(topicA);
    });

    a.after(subnav);
  }

  function toggleChapterSubnav(chapterId, a) {
    const alreadyOpen = openDrawerChapterId === chapterId;
    $$('.sidebar-subnav.is-drawer-subnav', sidebar).forEach(el => el.remove());
    $$('[data-chapter-link]', sidebar).forEach(link => link.setAttribute('aria-expanded', 'false'));

    if (alreadyOpen) {
      openDrawerChapterId = null;
    } else {
      openDrawerChapterId = chapterId;
      a.setAttribute('aria-expanded', 'true');
      renderDrawerSubnav(chapterId, a);
    }
  }

  function setToc() {
    toc.replaceChildren();
    if (window.innerWidth > 760) {
      $('.sidebar-subnav:not(.is-drawer-subnav)', sidebar)?.remove();
      const subnav = document.createElement('div');
      subnav.className = 'sidebar-subnav';
      $$('[data-topic]', current).forEach(topic => {
        const a = document.createElement('a');
        a.href = `#${topic.id}`;
        a.textContent = label(topic);
        toc.append(a);
        subnav.append(a.cloneNode(true));
      });
      $(`[data-chapter-link="${current.id}"]`, sidebar)?.after(subnav);
    }
    if (observer) observer.disconnect();
    if ('IntersectionObserver' in window) {
      observer = new IntersectionObserver(entries => {
        const visible = entries.filter(entry => entry.isIntersecting);
        if (!visible.length) return;
        const first = visible.sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0].target;
        $$('#page-toc a, .sidebar-subnav a').forEach(a => {
          if (a.hash === `#${first.id}`) a.setAttribute('aria-current', 'location');
          else a.removeAttribute('aria-current');
        });
      }, {rootMargin: '-90px 0px -55% 0px', threshold: 0});
      $$('[data-topic]', current).forEach(topic => observer.observe(topic));
    }
  }
  function search() {
    const words = input.value.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
    list.replaceChildren();
    results.hidden = !words.length;
    if (words.length) {
      const matches = searchIndex.filter(item => words.every(word => item.content.includes(word)));
      $('#search-status').textContent = matches.length
        ? text([`${matches.length}件の項目`, `${matches.length} topics found`])
        : text(['一致する項目がありません。別の機能名やAPI名で検索してください。', 'No matching topics. Try another feature name or API.']);
      matches.forEach(({topic}) => {
        const link = document.createElement('a');
        link.href = `#${topic.id}`;
        link.className = 'result-link';
        const category = document.createElement('small');
        category.textContent = label(topic.closest('[data-chapter]'));
        const heading = document.createElement('strong');
        heading.textContent = label(topic);
        const excerpt = document.createElement('p');
        const prose = $$(`.topic-lead [lang=${language}],.access-path p [lang=${language}],.steps [lang=${language}],.result [lang=${language}],.note p [lang=${language}],table td [lang=${language}]`, topic).map(p => p.textContent);
        const relevant = prose.find(p => words.some(word => p.toLocaleLowerCase().includes(word))) || prose[0];
        excerpt.textContent = relevant.length > 150 ? relevant.slice(0, 150) + '…' : relevant;
        link.append(category, heading, excerpt);
        list.append(link);
      });
    }
    if ($('#drawer-search-input')) {
      $('#drawer-search-input').value = input.value;
      const dResults = $('#drawer-search-results');
      if (dResults) {
        dResults.hidden = !words.length;
        $('#drawer-search-status').textContent = words.length ? $('#search-status').textContent : '';
        const dList = $('#drawer-result-list');
        dList.replaceChildren(...[...list.children].map(n => n.cloneNode(true)));
      }
    }
  }
  function updateMobileHeader() {
    const badge = $('#mobile-current-chapter');
    if (!badge || !current) return;
    const idx = String(chapters.indexOf(current) + 1).padStart(2, '0');
    badge.textContent = `${idx} · ${label(current)}`;
  }
  function updateMobileAccordions() {
    const mobile = window.innerWidth <= 760;
    $$('.chapter').forEach(ch => {
      const topicsCount = $$('[data-topic]', ch).length;
      const inlineToc = $('.inline-toc', ch);
      if (inlineToc) {
        let btn = $('.mobile-accordion-toggle[data-target="inline-toc"]', ch);
        if (!btn) {
          btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'mobile-accordion-toggle enhanced';
          btn.dataset.target = 'inline-toc';
          inlineToc.before(btn);
          btn.addEventListener('click', () => {
            const collapsed = inlineToc.classList.toggle('is-collapsed');
            btn.setAttribute('aria-expanded', String(!collapsed));
          });
          if (mobile) inlineToc.classList.add('is-collapsed');
        }
        const expanded = !inlineToc.classList.contains('is-collapsed');
        btn.setAttribute('aria-expanded', String(expanded));
        btn.innerHTML = `<span class="accordion-label">${text([`この章の項目（${topicsCount}件）`, `In this chapter (${topicsCount})`])}</span><span class="accordion-chevron" aria-hidden="true">▾</span>`;
      }
      const flowFig = $('.flow-figure, .screen-figure', ch);
      if (flowFig) {
        let btn = $('.mobile-accordion-toggle[data-target="figure"]', ch);
        if (!btn) {
          btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'mobile-accordion-toggle enhanced';
          btn.dataset.target = 'figure';
          flowFig.before(btn);
          btn.addEventListener('click', () => {
            const collapsed = flowFig.classList.toggle('is-collapsed');
            btn.setAttribute('aria-expanded', String(!collapsed));
          });
          if (mobile) flowFig.classList.add('is-collapsed');
        }
        const isScreen = flowFig.classList.contains('screen-figure');
        const expanded = !flowFig.classList.contains('is-collapsed');
        btn.setAttribute('aria-expanded', String(expanded));
        btn.innerHTML = `<span class="accordion-label">${text(isScreen ? ['画面の読み方（図解）', 'Screen map diagram'] : ['操作の流れ（説明図）', 'Workflow diagram'])}</span><span class="accordion-chevron" aria-hidden="true">▾</span>`;
      }
    });
  }
  function setupDrawerControls() {
    if ($('.drawer-controls', sidebar)) return;
    const head = document.createElement('div');
    head.className = 'drawer-controls enhanced';
    head.innerHTML = `
      <div class="drawer-top-row">
        <span class="drawer-title"><span lang="ja">メニュー・目次</span><span lang="en">MENU &amp; CONTENTS</span></span>
        <div class="drawer-language" role="group" data-aria-ja="言語選択" data-aria-en="Language" aria-label="言語選択">
          <button type="button" data-lang="ja" aria-pressed="true">日本語</button>
          <button type="button" data-lang="en" aria-pressed="false">EN</button>
        </div>
      </div>
      <div class="drawer-search-box">
        <span aria-hidden="true">⌕</span>
        <input id="drawer-search-input" type="search" autocomplete="off" placeholder="機能名・API・困っていること" aria-label="機能を調べる" data-aria-ja="機能を調べる" data-aria-en="Find a feature">
      </div>
      <div id="drawer-search-results" hidden>
        <p id="drawer-search-status"></p>
        <div id="drawer-result-list"></div>
      </div>
    `;
    sidebar.prepend(head);
    const dInput = $('#drawer-search-input', head);
    dInput.addEventListener('input', () => {
      input.value = dInput.value;
      search();
      remember();
    });
  }
  function showChapter(target) {
    const next = target?.closest('[data-chapter]') || (target?.matches('[data-chapter]') ? target : current);
    const changed = current !== next;
    current = next || chapters[0];
    chapters.forEach(chapter => { chapter.hidden = chapter !== current; });
    $$('[data-chapter-link]').forEach(a => {
      if (a.dataset.chapterLink === current.id) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    setToc();
    updateMobileHeader();
    if (changed) document.dispatchEvent(new CustomEvent('guide:chapter', {detail: {id: current.id}}));
  }
  function targetFromHash() {
    let id;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch { return chapters[0]; }
    if (!id) return chapters[0];
    const target = document.getElementById(id);
    return target?.matches('[data-chapter],[data-topic],#content') ? target : chapters[0];
  }
  function move(target, focus = true) {
    showChapter(target);
    const heading = target.matches('[data-topic]') ? $('h3', target)
      : target.matches('[data-chapter]') ? $('h2', target) : target;
    requestAnimationFrame(() => {
      target.scrollIntoView({block: 'start', behavior: 'instant'});
      if (focus) heading.focus({preventScroll: true});
      remember();
    });
  }
  function restore() {
    fromHistory = true;
    input.value = history.state?.query || '';
    search();
    showChapter(targetFromHash());
    clearTimeout(pendingScroll);
    const scroll = history.state?.scroll;
    requestAnimationFrame(() => {
      if (typeof scroll === 'number') window.scrollTo({top: scroll, behavior: 'instant'});
      else targetFromHash().scrollIntoView({block: 'start', behavior: 'instant'});
      fromHistory = false;
    });
  }
  function setLanguage(next, preservePosition = true) {
    // Pin the current reading location, rather than the absolute scroll offset of another language.
    const anchor = preservePosition ? $$('[data-topic]', current).find(t => t.getBoundingClientRect().bottom > 100) : null;
    const offset = anchor?.getBoundingClientRect().top;
    language = next;
    root.lang = next;
    document.title = text(['Edit:RiN 詳細マニュアル', 'Edit:RiN User manual']);
    input.placeholder = text(['機能名・API・困っていること', 'Feature, API, or problem']);
    if ($('#drawer-search-input')) $('#drawer-search-input').placeholder = input.placeholder;
    $$('[data-lang]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.lang === next)));
    $$('[data-aria-ja]').forEach(el => el.setAttribute('aria-label', next === 'en' ? el.dataset.ariaEn : el.dataset.ariaJa));
    setToc();
    updateMobileHeader();
    updateMobileAccordions();
    if (openDrawerChapterId) {
      const activeLink = $(`[data-chapter-link="${openDrawerChapterId}"]`, sidebar);
      if (activeLink) {
        $$('.sidebar-subnav.is-drawer-subnav', sidebar).forEach(el => el.remove());
        renderDrawerSubnav(openDrawerChapterId, activeLink);
      }
    }
    search();
    document.dispatchEvent(new CustomEvent('guide:language', {detail: {language}}));
    try { localStorage.setItem('editrin_guide_lang', next); } catch { /* Optional preference. */ }
    if (anchor) requestAnimationFrame(() => {
      window.scrollBy(0, anchor.getBoundingClientRect().top - offset);
      remember();
    });
  }
  setupDrawerControls();
  document.addEventListener('click', event => {
    if (sidebar.classList.contains('open') && !event.target.closest('#sidebar, #menu-toggle')) {
      closeMenu();
    }
    const a = event.target.closest('a[href^="#"]');
    if (!a || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;

    const isMobileDrawer = (sidebar.classList.contains('open') || window.innerWidth <= 760) && a.closest('#sidebar') && a.dataset.chapterLink;
    if (isMobileDrawer) {
      event.preventDefault();
      toggleChapterSubnav(a.dataset.chapterLink, a);
      return;
    }

    const id = a.hash.slice(1);
    const target = document.getElementById(id);
    if (!target) return;
    event.preventDefault();
    remember();
    history.pushState({guide: true, query: input.value, scroll: null}, '', a.hash);
    if (a.classList.contains('result-link') && window.innerWidth <= 900) results.hidden = true;
    closeMenu();
    move(target);
  });
  window.addEventListener('popstate', restore);
  // Handle anchors set by the address bar or other code as well as in-page links.
  window.addEventListener('hashchange', () => { if (!fromHistory) move(targetFromHash()); });
  window.addEventListener('scroll', () => {
    clearTimeout(pendingScroll);
    pendingScroll = setTimeout(remember, 120);
  }, {passive: true});
  input.addEventListener('input', () => { search(); remember(); });
  $('#clear-search').addEventListener('click', () => { input.value = ''; search(); remember(); input.focus(); });
  $$('[data-lang]').forEach(button => button.addEventListener('click', () => setLanguage(button.dataset.lang)));
  menu.addEventListener('click', () => {
    const opened = sidebar.classList.toggle('open');
    root.classList.toggle('menu-open', opened);
    menu.setAttribute('aria-expanded', String(opened));
    if (opened) {
      if (!openDrawerChapterId && current) {
        const activeLink = $(`[data-chapter-link="${current.id}"]`, sidebar);
        if (activeLink) toggleChapterSubnav(current.id, activeLink);
      }
      $('[aria-current=page]', sidebar)?.focus();
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && sidebar.classList.contains('open')) { closeMenu(); menu.focus(); }
  });
  $$('.copy').forEach(button => button.addEventListener('click', async () => {
    const code = $('code', button.closest('.code-example'));
    try {
      if (!navigator.clipboard?.writeText) throw new Error('No clipboard');
      await navigator.clipboard.writeText(code.textContent);
      announce(['コードをコピーしました。', 'Code copied.']);
    } catch {
      const range = document.createRange();
      range.selectNodeContents(code);
      const selection = window.getSelection();
      selection.removeAllRanges(); selection.addRange(range);
      announce(['コピーできませんでした。選択されたコードを手動でコピーしてください。', 'Copy failed. Copy the selected source manually.']);
    }
  }));
  // Apply enhancement only once navigation is ready, leaving readable HTML if initialization fails.
  root.classList.add('js');
  history.scrollRestoration = 'manual';
  showChapter(targetFromHash());
  setLanguage(language, false);
  if (history.state?.guide) restore();
  else {
    if (location.hash) move(targetFromHash(), false);
    else remember();
  }
})();
