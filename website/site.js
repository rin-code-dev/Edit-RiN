(() => {
  'use strict';
  const experience = document.querySelector('#experience');
  const glyphs = [...document.querySelectorAll('.glyph')];
  const chapters = [...document.querySelectorAll('.chapter')];
  const videos = [...document.querySelectorAll('video')];
  const slider = document.querySelector('#rhythm');
  const output = document.querySelector('#rhythm-value');
  const hint = document.querySelector('#interaction-hint');
  const back = document.querySelector('#back-button');
  const status = document.querySelector('#scene-status');
  const motion = document.querySelector('#motion-toggle');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let current = 'home';
  let paused = reduceMotion.matches;
  let rhythm = 0.5;
  let returnFocus = null;
  let drag = null;
  let suppressClick = false;
  let frame = 0;
  let last = 0;
  let phase = 0;
  const manualPause = new WeakSet();
  const videoDescriptions = {
    write: 'code and preview recording',
    tune: 'live parameter recording',
    keep: 'recording and export demo'
  };
  const demos = videos.map(video => ({
    video,
    scene: video.closest('.chapter').id.replace('chapter-', ''),
    button: video.parentElement.querySelector('.video-toggle'),
    request: 0,
    pending: false
  }));

  function setSaveLabel(text) {
    document.querySelector('#save-label').textContent = text;
  }

  function updateGeometry(time = phase) {
    const strength = (rhythm - 0.5) * 2;
    const wave = paused ? 0 : Math.sin(time) * rhythm;
    experience.style.setProperty('--r-shift', `${(strength * 9 + wave * 2).toFixed(2)}px`);
    experience.style.setProperty('--n-shift', `${(-strength * 8 - wave * 2).toFixed(2)}px`);
    experience.style.setProperty('--i-shift', `${(strength * -24).toFixed(2)}px`);
    experience.style.setProperty('--tilt', `${(strength * 5 + wave).toFixed(2)}deg`);
  }
  function setRhythm(value) {
    rhythm = Math.max(0, Math.min(1, Number(value)));
    slider.value = Math.round(rhythm * 100);
    output.value = rhythm.toFixed(2);
    updateGeometry();
  }
  function animate(timestamp) {
    frame = 0;
    if (paused || document.hidden) { last = 0; return; }
    if (last) phase += Math.min(timestamp - last, 50) * (0.0004 + rhythm * 0.0013);
    last = timestamp;
    updateGeometry();
    frame = requestAnimationFrame(animate);
  }
  function syncLoop() {
    if (frame) cancelAnimationFrame(frame);
    frame = 0; last = 0;
    updateGeometry();
    if (!paused && !document.hidden) frame = requestAnimationFrame(animate);
  }
  function renderVideoButton(demo) {
    const failed = Boolean(demo.video.error);
    demo.video.classList.toggle('media-error', failed);
    const action = failed ? 'Retry' : demo.video.paused ? 'Play' : 'Pause';
    demo.button.setAttribute('aria-label', `${action} ${videoDescriptions[demo.scene]}`);
    demo.button.querySelector('span').textContent = action === 'Pause' ? 'Ⅱ' : '▶';
  }
  function shouldPlay(demo) {
    return demo.scene === current && !paused && !document.hidden &&
      !document.querySelector('dialog[open]') && !manualPause.has(demo.video);
  }
  function stopVideo(demo, release = false) {
    // Invalidate an outstanding play promise before pausing or releasing its source.
    demo.request++;
    demo.pending = false;
    demo.video.pause();
    if (release && demo.video.hasAttribute('src')) {
      demo.video.classList.remove('media-ready');
      demo.video.removeAttribute('src');
      demo.video.load();
    }
    renderVideoButton(demo);
  }
  async function playVideo(demo) {
    if (!shouldPlay(demo) || demo.pending) return;
    const video = demo.video;
    if (!video.hasAttribute('src') || video.error) {
      video.classList.remove('media-error', 'media-ready');
      video.src = video.dataset.src;
      video.load();
    }
    const request = ++demo.request;
    demo.pending = true;
    try {
      await video.play();
    } catch {
      // Autoplay rejection leaves the poster and explicit play/retry control available.
    } finally {
      if (request === demo.request) {
        demo.pending = false;
        if (!shouldPlay(demo)) video.pause();
        renderVideoButton(demo);
      }
    }
  }
  function syncVideos() {
    // Release all inactive decoders before starting the selected chapter.
    for (const demo of demos) {
      if (!shouldPlay(demo)) stopVideo(demo, demo.scene !== current);
    }
    for (const demo of demos) {
      if (shouldPlay(demo)) playVideo(demo);
    }
  }
  function showScene(scene, focusHeading = true) {
    if (!['home','write','tune','keep'].includes(scene)) return;
    current = scene;
    experience.dataset.scene = scene;
    document.querySelector('#home-caption').setAttribute('aria-hidden', String(scene !== 'home'));
    for (const chapter of chapters) chapter.hidden = chapter.id !== `chapter-${scene}`;
    for (const glyph of glyphs) glyph.setAttribute('aria-expanded', String(glyph.dataset.scene === scene));
    hint.hidden = scene !== 'home';
    back.hidden = scene === 'home';
    status.textContent = scene === 'home' ? 'Interactive artwork. Choose Write, Tune, or Keep.' : `${scene[0].toUpperCase() + scene.slice(1)}. App information and recording are open.`;
    syncVideos();
    if (scene !== 'home' && focusHeading) document.querySelector(`#${scene}-title`).focus({preventScroll:true});
    if (scene === 'home' && focusHeading) (returnFocus || glyphs[0]).focus({preventScroll:true});
  }
  glyphs.forEach(glyph => glyph.addEventListener('click', event => {
    if (glyph.id === 'tune-glyph' && suppressClick && event.detail !== 0) { suppressClick = false; return; }
    returnFocus = glyph;
    showScene(glyph.dataset.scene);
  }));
  back.addEventListener('click', () => showScene('home'));
  document.querySelector('#home-button').addEventListener('click', () => { showScene('home', false); });
  slider.addEventListener('input', () => setRhythm(slider.value / 100));

  // Keep browser zoom, horizontal gestures, and native controls available.
  experience.addEventListener('wheel', event => {
    if (event.ctrlKey || event.metaKey || drag || document.querySelector('dialog[open]')) return;
    if (event.target.closest('input, textarea, select, video, .video-toggle')) return;
    if (!event.deltaY || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;
    const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? window.innerHeight : 1;
    const delta = Math.max(-120, Math.min(120, event.deltaY * unit));
    event.preventDefault();
    setRhythm(rhythm - delta / 600);
  }, {passive:false});

  let scrollTouch = null;
  experience.addEventListener('touchstart', event => {
    scrollTouch = null;
    if (event.touches.length !== 1 || document.querySelector('dialog[open]')) return;
    if (event.target.closest('button, a, input, textarea, select, video')) return;
    const touch = event.touches[0];
    scrollTouch = {id:touch.identifier, x:touch.clientX, y:touch.clientY, lastY:touch.clientY, vertical:false};
  }, {passive:true});
  experience.addEventListener('touchmove', event => {
    if (!scrollTouch) return;
    if (event.touches.length !== 1) { scrollTouch = null; return; }
    const touch = [...event.touches].find(touch => touch.identifier === scrollTouch.id);
    if (!touch) return;
    if (!scrollTouch.vertical) {
      const dx = touch.clientX - scrollTouch.x;
      const dy = touch.clientY - scrollTouch.y;
      if (Math.max(Math.abs(dx), Math.abs(dy)) < 6) return;
      if (Math.abs(dx) > Math.abs(dy)) { scrollTouch = null; return; }
      scrollTouch.vertical = true;
    }
    event.preventDefault();
    setRhythm(rhythm - (touch.clientY - scrollTouch.lastY) / 600);
    scrollTouch.lastY = touch.clientY;
  }, {passive:false});
  function endScrollTouch() {
    if (scrollTouch) status.textContent = `Artwork rhythm ${Math.round(rhythm * 100)} percent.`;
    scrollTouch = null;
  }
  experience.addEventListener('touchend', endScrollTouch);
  experience.addEventListener('touchcancel', endScrollTouch);

  const tuneGlyph = document.querySelector('#tune-glyph');
  tuneGlyph.addEventListener('pointerdown', event => {
    if (event.button !== 0 || !event.isPrimary) return;
    suppressClick = false;
    drag = {id:event.pointerId, y:event.clientY, initial:rhythm, moved:false};
    tuneGlyph.setPointerCapture(event.pointerId);
  });
  tuneGlyph.addEventListener('pointermove', event => {
    if (!drag || drag.id !== event.pointerId) return;
    const delta = drag.y - event.clientY;
    if (Math.abs(delta) > 6) drag.moved = true;
    if (drag.moved) { tuneGlyph.classList.add('is-dragging'); setRhythm(drag.initial + delta / 200); }
  });
  function endDrag(event) {
    if (!drag || drag.id !== event.pointerId) return;
    suppressClick = event.type === 'pointerup' && drag.moved;
    if (drag.moved) status.textContent = `Artwork rhythm ${Math.round(rhythm * 100)} percent.`;
    drag = null;
    tuneGlyph.classList.remove('is-dragging');
    if (tuneGlyph.hasPointerCapture(event.pointerId)) tuneGlyph.releasePointerCapture(event.pointerId);
  }
  tuneGlyph.addEventListener('pointerup', endDrag);
  tuneGlyph.addEventListener('pointercancel', endDrag);
  tuneGlyph.addEventListener('lostpointercapture', () => { drag = null; tuneGlyph.classList.remove('is-dragging'); });

  function renderMotionLabels() {
    motion.setAttribute('aria-pressed', String(paused));
    motion.setAttribute('aria-label', paused ? 'Resume the artwork and recordings' : 'Pause the artwork and recordings');
    document.querySelector('#motion-symbol').textContent = paused ? '▶' : 'Ⅱ';
    document.querySelector('#motion-label').textContent = paused ? 'Motion off' : 'Motion on';
  }
  function updatePause() {
    experience.classList.toggle('is-paused', paused);
    renderMotionLabels();
    syncLoop(); syncVideos();
  }
  motion.addEventListener('click', () => { paused = !paused; updatePause(); });
  reduceMotion.addEventListener('change', event => { paused = event.matches; updatePause(); });
  document.addEventListener('visibilitychange', () => { syncLoop(); syncVideos(); });
  demos.forEach(demo => {
    // Keep the first-frame poster visible until the new source has decoded a frame.
    demo.video.addEventListener('loadeddata', () => {
      if (demo.video.hasAttribute('src') && demo.scene === current) {
        demo.video.classList.add('media-ready');
      }
    });
    demo.video.addEventListener('emptied', () => demo.video.classList.remove('media-ready'));
    for (const event of ['play', 'pause', 'error', 'emptied']) {
      demo.video.addEventListener(event, () => renderVideoButton(demo));
    }
    demo.button.addEventListener('click', () => {
      if (demo.video.paused || demo.video.error) {
        manualPause.delete(demo.video);
        // An explicit play request also resumes Motion, so its label stays truthful.
        paused = false;
        updatePause();
      } else {
        manualPause.add(demo.video);
        stopVideo(demo);
      }
    });
  });

  document.querySelectorAll('[data-dialog]').forEach(button => button.addEventListener('click', () => {
    const dialog = document.getElementById(button.dataset.dialog);
    dialog.showModal(); syncVideos();
  }));
  document.querySelectorAll('dialog').forEach(dialog => {
    dialog.querySelector('.dialog-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', syncVideos);
    dialog.addEventListener('click', event => {
      if (event.target !== dialog) return;
      const rect = dialog.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    });
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !document.querySelector('dialog[open]') && current !== 'home') showScene('home');
  });

  document.querySelector('#save-artwork').addEventListener('click', async () => {
    const button = document.querySelector('#save-artwork');
    button.disabled = true;
    try {
      await document.fonts.ready;
      const canvas = document.createElement('canvas');
      canvas.width = 1600; canvas.height = 1000;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#F2EFE7'; ctx.fillRect(0,0,1600,1000);
      const offsets = [342,690,850];
      const widths = [260,72,260];
      const vars = getComputedStyle(experience);
      const geometry = [parseFloat(vars.getPropertyValue('--r-shift')), parseFloat(vars.getPropertyValue('--n-shift')), parseFloat(vars.getPropertyValue('--i-shift')), parseFloat(vars.getPropertyValue('--tilt'))];
      for (let i=0; i<glyphs.length; i++) {
        const svg = glyphs[i].querySelector('svg').cloneNode(true);
        svg.setAttribute('xmlns','http://www.w3.org/2000/svg');
        svg.setAttribute('width',widths[i]); svg.setAttribute('height','320');
        svg.setAttribute('fill',i===1?'#9F4029':'#2B2825');
        svg.querySelector('.r-bowl')?.setAttribute('transform',`translate(0 ${geometry[0]})`);
        svg.querySelector('.r-leg')?.setAttribute('transform',`rotate(${geometry[3]} 110 155)`);
        svg.querySelector('.n-diagonal')?.setAttribute('transform',`translate(0 ${geometry[1]})`);
        svg.querySelector('.i-stem')?.setAttribute('transform',`translate(0 ${geometry[2]})`);
        const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)],{type:'image/svg+xml'}));
        try {
          const img = new Image(); img.src = url; await img.decode();
          ctx.drawImage(img,offsets[i],326,widths[i],320);
        } finally { URL.revokeObjectURL(url); }
      }
      ctx.fillStyle='#2B2825';ctx.font='400 28px "Archivo Black", sans-serif';ctx.fillText('Edit:RiN',56,67);
      ctx.font='400 14px "IBM Plex Mono", monospace';ctx.fillText('WRITE. TUNE. KEEP.',56,950);
      const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));
      if (!blob) throw new Error('PNG creation failed');
      const url=URL.createObjectURL(blob);
      const link=document.createElement('a');link.href=url;link.download='EditRiN-letter-study.png';link.click();
      setTimeout(()=>URL.revokeObjectURL(url),30000);
      status.textContent = 'Artwork saved as a PNG.';
      setSaveLabel('Artwork saved');
      setTimeout(()=>setSaveLabel('Save this artwork'),2500);
    } catch {
      status.textContent = 'The artwork could not be saved. Please try again.';
    } finally { button.disabled=false; }
  });
  setRhythm(0.5);
  updatePause();
})();
