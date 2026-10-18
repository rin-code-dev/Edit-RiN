/* Example-specific teaching controls. Android storage and permissions are never invoked. */
(() => {
  'use strict';
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const bi = (ja, en) => `<span lang="ja">${escape(ja)}</span><span lang="en">${escape(en)}</span>`;
  const escape = value => String(value).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const action = (id, ja, en, extra = '') => `<button type="button" data-action="${id}" ${extra}>${bi(ja, en)}</button>`;
  const slider = (id, ja, en, min, max, value, step = 1) => `<label class="control"><span>${bi(ja, en)}<output data-output="${id}">${value}</output></span><input type="range" data-control="${id}" min="${min}" max="${max}" step="${step}" value="${value}"></label>`;
  const checkbox = (id, ja, en, checked = false) => `<label class="control check"><input data-control="${id}" type="checkbox" ${checked ? 'checked' : ''}>${bi(ja, en)}</label>`;
  const select = (id, ja, en, options) => `<label class="control"><span>${bi(ja, en)}</span><select data-control="${id}">${options.map(([v, j, e]) => `<option value="${v}">${document.documentElement.lang === 'ja' ? j : e}</option>`).join('')}</select></label>`;
  const status = (el, ja, en) => { $('.demo-status', el).innerHTML = bi(ja, en); };
  const defaults = () => ({size: 100, count: 3, ink: '#B85338', moving: false, shape: 'circle'});
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const liveExamples = [];
  const vendor = new URL('assets/guide/vendor/', location.href);
  const frameScript = new URL('assets/guide/demo-frame.js', location.href).href;
  function setupDemoCollapse(host, onCollapse) {
    const heading = $('.demo-heading', host);
    if (!heading) return;
    let btn = $('.demo-collapse-toggle', heading);
    if (!btn) {
      btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'demo-collapse-toggle';
      btn.dataset.ui = 'toggle-demo';
      heading.append(btn);
    }
    const apply = collapsed => {
      host.classList.toggle('is-collapsed', collapsed);
      btn.setAttribute('aria-expanded', String(!collapsed));
      btn.innerHTML = collapsed ? bi('デモを開く', 'Open demo') : bi('閉じる', 'Collapse');
      if (collapsed && onCollapse) onCollapse();
    };
    const initial = host.dataset.collapsedInit ? host.classList.contains('is-collapsed') : window.innerWidth <= 760;
    host.dataset.collapsedInit = '1';
    apply(initial);
    btn.onclick = () => apply(!host.classList.contains('is-collapsed'));
  }

  $$('[data-live]').forEach(host => {
    const kind = host.dataset.live;
    const controls = $('.live-controls', host);
    const stage = $('.live-stage', host);
    const code = $('code', host.previousElementSibling);
    const authoredSource = code.textContent;
    const originalDefaults = defaults();
    let resetValues = defaults();
    let values = defaults();
    let frame = null;
    let token = '';
    let ready = false;
    let paused = false;
    let automaticPause = false;
    let comparing = false;
    let diameter = 100;
    let timeout;
    let autoRunTimer;
    let visible = true;
    const get = name => $(`[data-control="${name}"]`, controls);
    const post = (action, detail = {}) => frame?.contentWindow?.postMessage({guideDemo: token, action, ...detail}, '*');
    const isParameters = ['params', 'inline'].includes(kind);
    const tuning = {
      basic: {ja:'移動の幅', en:'Travel distance', min:20, max:150, value:100, step:10, from:'* 100;', to:v=>`* ${v};`},
      watch: {ja:'移動の幅', en:'Travel distance', min:20, max:150, value:100, step:10, from:'* 100;', to:v=>`* ${v};`},
      webgl: {ja:'立方体の大きさ', en:'Box size', min:50, max:160, value:110, step:10, from:'box(110)', to:v=>`box(${v})`},
      shader: {ja:'緑の成分', en:'Green channel', min:0, max:1, value:.3, step:.05, from:'uv.x, 0.3, uv.y', to:v=>`uv.x, ${Number(v).toFixed(2)}, uv.y`},
      physics: {ja:'反発係数', en:'Restitution', min:0, max:1, value:.75, step:.05, from:'restitution: 0.75', to:v=>`restitution: ${v}`}
    }[kind];
    let tunedValue = tuning?.value;
    let appliedTuning = tunedValue;
    if (kind === 'run') {
      controls.innerHTML = slider('diameter', 'コードの直径', 'Source diameter', 20, 200, 100, 10) + checkbox('auto', 'Auto-Run', 'Auto-Run');
    } else if (isParameters) {
      controls.innerHTML = slider('size', '大きさ', 'Size', 20, 200, 100, 10)
        + slider('count', '個数', 'Count', 1, 6, 3)
        + `<label class="control"><span>${bi('色', 'Color')}</span><input data-control="ink" type="color" value="#b85338"></label>`
        + select('shape', '図形', 'Shape', [['circle', '円', 'Circle'], ['square', '四角', 'Square'], ['triangle', '三角', 'Triangle']])
        + checkbox('moving', '回転する', 'Rotate');
      const tools = document.createElement('div');
      tools.className = 'parameter-tools';
      tools.innerHTML = `<div class="pin-controls">${['size', 'count', 'ink', 'shape', 'moving'].map((k, i) => checkbox(`pin-${k}`, ['大きさを固定', '個数を固定', '色を固定', '図形を固定', '回転を固定'][i], `Pin ${k}`)).join('')}</div><div class="demo-actions">`
        + action('random', 'ランダム', 'Randomize') + action('compare', '押して初期値と比較', 'Hold to compare')
        + action('defaults', '初期値に戻す', 'Restore default') + action('set-defaults', '現在値を初期値にする', 'Set as defaults') + '</div>';
      controls.after(tools);
    } else if (kind === 'watch') {
      controls.innerHTML = `<output class="watch-output" data-watch aria-live="off">x = —</output>`;
    }
    if (tuning) controls.insertAdjacentHTML('beforeend', slider('tuning', tuning.ja, tuning.en, tuning.min, tuning.max, tuning.value, tuning.step) + `<p class="tuning-help">${bi('値を変えたら「実行」で反映します。','Change the value, then press Run to apply.')}</p>`);
    // Keep the published code next to its runtime, with a stable preview and tool area.
    const exercises = {
      basic: ['実行して、円の動きを確認します。停止すると、その位置で止まります。', 'Run to see the circle move. Stop freezes it in place.'],
      run: ['直径を変えてから実行します。Auto-Runを有効にした場合と比べてください。', 'Change the diameter, then Run. Compare this with Auto-Run enabled.'],
      inline: ['実行後に値を動かして、上のコードと図形の変化を見比べます。', 'Run, then adjust values and compare the code above with the drawing.'],
      params: ['大きさや色を変えます。値を固定してからランダムも試せます。', 'Change size and color. Pin a value, then try Randomize.'],
      watch: ['円の位置と変数xの値が、一緒に変わる様子を確認します。', 'Watch the circle position and the value of x change together.'],
      webgl: ['回転する立体を確認し、停止して面の色を見比べます。', 'Run the rotating solid, then stop to compare its face colors.'],
      shader: ['コードのシェーダーが、座標に応じた色を描きます。', 'The shader in the code draws a color for each position.'],
      physics: ['落下と床での跳ね返りを確認します。初期化で最初から試せます。', 'Observe the fall and bounce. Reset to try it from the beginning.'],
      touch: ['実行後、キャンバス内を動かすと円が追従します。押すと色が変わります。', 'Run, then move inside the canvas to move the circle. Press to change its color.']
    };
    const exercise = document.createElement('p');
    exercise.className = 'demo-exercise';
    exercise.innerHTML = `<span class="exercise-number" aria-hidden="true">↳</span>${bi(...exercises[kind])}`;
    $('.demo-heading', host).after(exercise);
    const studio = document.createElement('div'); studio.className = 'demo-studio';
    const preview = document.createElement('div'); preview.className = 'demo-preview';
    const bar = document.createElement('div'); bar.className = 'preview-bar';
    bar.innerHTML = `<span>${bi('プレビュー', 'Preview')}</span><span class="runtime-state" role="status"></span><span class="canvas-size">400 × 280</span>`;
    controls.before(studio); studio.append(preview); preview.append(bar, stage);
    const actions = host.querySelector(':scope > .demo-actions');
    preview.append(actions);
    if (controls.childElementCount) {
      const tools = document.createElement('div'); tools.className = 'studio-tools';
      tools.innerHTML = `<h4>${bi(kind === 'watch' ? '変数の値' : '値を調整', kind === 'watch' ? 'Variable value' : 'Adjust values')}</h4>`;
      studio.append(tools); tools.append(controls);
      const parameterTools = $('.parameter-tools', host);
      if (parameterTools) {
        // Put each pin beside its corresponding value, so its target is unambiguous.
        ['size', 'count', 'ink', 'shape', 'moving'].forEach(k => {
          const row = document.createElement('div'); row.className = 'parameter-row';
          const control = get(k).closest('label'); control.before(row); row.append(control);
          const pin = $(`[data-control="pin-${k}"]`, host).closest('label');
          pin.classList.add('parameter-pin'); row.append(pin);
        });
        $('.pin-controls', parameterTools).remove();
        tools.append(parameterTools);
      }
    } else controls.remove();
    $('.demo-help', host).classList.add('demo-technical-note');
    host.append($('.demo-help', host));
    function sourceForValues(v) {
      if (tuning) return authoredSource.replace(tuning.from, tuning.to(tunedValue));
      if (kind === 'run') return authoredSource.replace('let diameter = 100;', `let diameter = ${diameter};`);
      if (kind === 'inline') {
        return authoredSource.replace('let size = 100;', `let size = ${v.size};`)
          .replace('let count = 3;', `let count = ${v.count};`)
          .replace("let ink = '#B85338';", `let ink = '${v.ink}';`)
          .replace('let moving = false;', `let moving = ${v.moving};`)
          .replace("let shape = 'circle';", `let shape = '${v.shape}';`);
      }
      if (kind === 'params') {
        return authoredSource.replace('20 200 100 10', `20 200 ${v.size} 10`)
          .replace('1 6 3 1', `1 6 ${v.count} 1`)
          .replace('"Ink" #B85338', `"Ink" ${v.ink}`)
          .replace('"Rotate" false', `"Rotate" ${v.moving}`)
          .replace('"Shape" circle circle square triangle', `"Shape" ${v.shape} circle square triangle`);
      }
      return authoredSource;
    }
    function refreshControls() {
      if (isParameters) {
        ['size', 'count', 'ink', 'shape'].forEach(k => { get(k).value = values[k]; });
        get('moving').checked = values.moving;
        ['size', 'count'].forEach(k => { $(`[data-output="${k}"]`, controls).textContent = values[k]; });
      }
      code.textContent = sourceForValues(values);
    }
    function destroy(message = false) {
      clearTimeout(timeout);
      clearTimeout(autoRunTimer);
      frame?.remove(); frame = null; token = ''; ready = false; paused = false; automaticPause = false;
      delete host.dataset.frames; delete host.dataset.pixels; delete host.dataset.physicsY;
      stage.innerHTML = `<p class="stage-placeholder">${bi('実行すると、ここに描画します。', 'Run the example to draw here.')}</p>`;
      if (message) status(host, '章を移動したためデモを終了しました。', 'The example ended when you changed chapters.');
      updateButtons();
    }
    function updateButtons() {
      host.dataset.runtime = !frame ? 'idle' : !ready ? 'loading' : paused ? 'paused' : 'running';
      $('.runtime-state', host).innerHTML = !frame ? bi('未実行', 'Ready') : !ready ? bi('準備中', 'Loading') : paused ? bi('停止中', 'Stopped') : bi('実行中', 'Running');
      $('[data-action=stop]', host).disabled = !frame || paused || !ready;
      $('[data-action=start]', host).disabled = !!frame && !ready;
    }
    function start(restart = false) {
      if (frame && ready && !restart) {
        post('resume'); paused = false; automaticPause = false; updateButtons(); return;
      }
      destroy();
      const rect = host.getBoundingClientRect();
      visible = rect.bottom > 0 && rect.top < window.innerHeight;
      appliedTuning = tunedValue;
      refreshControls();
      status(host, 'ライブラリを読み込んでいます…', 'Loading libraries…');
      frame = document.createElement('iframe');
      frame.setAttribute('sandbox', location.protocol === 'file:' ? 'allow-scripts allow-same-origin' : 'allow-scripts');
      frame.setAttribute('allow', "camera 'none'; microphone 'none'; geolocation 'none'; accelerometer 'none'; gyroscope 'none'");
      frame.title = document.documentElement.lang === 'ja' ? 'コード例の実行結果' : 'Code example output';
      token = crypto.randomUUID ? crypto.randomUUID() : Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('');
      const config = {
        token, kind, code: code.textContent, values,
        p5: new URL('p5-2.3.4.min.js', vendor).href,
        matter: kind === 'physics' ? new URL('matter-0.20.0.min.js', vendor).href : null,
        reducedMotion: reducedMotion.matches
      };
      const configJSON = JSON.stringify(config).replace(/</g, '\\u003c');
      // No same-origin privilege on HTTP(S), forms, popups, downloads, or browser permission access.
      const scriptOrigin = location.protocol === 'file:' ? 'file:' : location.origin;
      const csp = `default-src 'none'; script-src 'unsafe-inline' ${scriptOrigin}; style-src 'unsafe-inline'; img-src data: blob:; connect-src 'none'; media-src 'none'; worker-src 'none'; base-uri 'none'`;
      frame.srcdoc = `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${escape(csp)}"><style>html,body{margin:0;background:#f1f1ea;overflow:hidden}main{position:fixed;inset:0;display:flex;align-items:center;justify-content:center}canvas{max-width:100%!important;max-height:100%!important;width:100%!important;height:100%!important;object-fit:contain;display:block;touch-action:none}</style></head><body><main></main><script id="config" type="application/json">${configJSON}</script><script src="${escape(frameScript)}"></script></body></html>`;
      stage.replaceChildren(frame);
      updateButtons();
      timeout = setTimeout(() => {
        if (!ready) { destroy(); status(host, '描画を開始できませんでした。実行で再試行してください。', 'Drawing did not start. Press Run to retry.'); }
      }, 15000);
    }
    function sendValues(v = values) {
      post('values', {values: v});
    }
    function compare(active) {
      if (!isParameters || comparing === active) return;
      comparing = active;
      sendValues(active ? resetValues : values);
      $('[data-action=compare]', host).setAttribute('aria-pressed', String(active));
    }
    if (isParameters) {
      const compareButton = $('[data-action=compare]', host);
      compareButton.setAttribute('aria-pressed', 'false');
      compareButton.addEventListener('pointerdown', event => {
        if (event.button !== 0) return;
        compareButton.setPointerCapture(event.pointerId); compare(true);
      });
      ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(name => compareButton.addEventListener(name, () => compare(false)));
      compareButton.addEventListener('keydown', event => {
        if (event.key === ' ' || event.key === 'Enter') { event.preventDefault(); compare(true); }
      });
      compareButton.addEventListener('keyup', event => { if (event.key === ' ' || event.key === 'Enter') compare(false); });
      compareButton.addEventListener('blur', () => compare(false));
    }
    host.addEventListener('input', event => {
      const name = event.target.dataset.control;
      if (!name || name.startsWith('pin-')) return;
      if (name === 'tuning') {
        tunedValue = Number(get('tuning').value);
        $('[data-output=tuning]', controls).textContent = tunedValue;
        refreshControls(); status(host, 'コードを変更しました。実行で最初から描画します。', 'Source changed. Run to draw from the beginning.');
      } else if (kind === 'run') {
        clearTimeout(autoRunTimer);
        diameter = Number(get('diameter').value);
        $('[data-output=diameter]', controls).textContent = diameter;
        refreshControls();
        if (get('auto').checked) {
          autoRunTimer = setTimeout(() => start(true), 350);
          status(host, '入力が止まると再実行します。', 'Reruns when input pauses.');
        } else status(host, 'コードを変更しました。実行で描画を更新します。', 'Source changed. Press Run to apply it.');
      } else if (isParameters) {
        if (['size', 'count'].includes(name)) values[name] = Number(get(name).value);
        else if (name === 'moving') values.moving = get(name).checked;
        else values[name] = get(name).value;
        refreshControls(); sendValues();
      }
    });
    host.addEventListener('click', event => {
      const name = event.target.closest('[data-action]')?.dataset.action;
      if (!name) return;
      if (name === 'start') start(kind === 'run' || (!!tuning && tunedValue !== appliedTuning));
      if (name === 'stop') { clearTimeout(autoRunTimer); post('pause'); paused = true; automaticPause = false; updateButtons(); }
      if (name === 'reset') {
        destroy(); values = {...originalDefaults}; resetValues = {...originalDefaults}; diameter = 100; comparing = false;
        if (tuning) { tunedValue = tuning.value; get('tuning').value = tunedValue; $('[data-output=tuning]',controls).textContent = tunedValue; }
        if (kind === 'run') { get('diameter').value = 100; get('auto').checked = false; $('[data-output=diameter]', controls).textContent = '100'; }
        if (isParameters) $$('[data-control^="pin-"]', controls.parentElement).forEach(pin => { pin.checked = false; });
        if (kind === 'watch') $('[data-watch]', host).textContent = 'x = —';
        refreshControls(); status(host, '初期状態に戻しました。実行で開始できます。', 'Reset to the initial state. Press Run to start.');
      }
      if (name === 'random' && isParameters) {
        const random = {size: 20 + Math.floor(Math.random() * 19) * 10, count: 1 + Math.floor(Math.random() * 6), ink: '#' + Math.floor(Math.random() * 0xffffff).toString(16).padStart(6, '0'), shape: ['circle', 'square', 'triangle'][Math.floor(Math.random() * 3)], moving: Math.random() > .5};
        Object.keys(random).forEach(k => { if (!$(`[data-control="pin-${k}"]`, host).checked) values[k] = random[k]; });
        refreshControls(); sendValues(); status(host, '固定していない値を変更しました。', 'Changed unpinned values.');
      }
      if (name === 'defaults') { values = {...resetValues}; refreshControls(); sendValues(); status(host, '初期値に戻しました。', 'Default values restored.'); }
      if (name === 'set-defaults') { resetValues = {...values}; status(host, 'このデモの初期値を現在値にしました。', 'Current values are now the defaults for this demo.'); }
    });
    window.addEventListener('message', event => {
      if (!frame || event.source !== frame.contentWindow || event.data?.guideDemo !== token) return;
      const message = event.data;
      if (message.type === 'ready') {
        clearTimeout(timeout); ready = true; paused = false;
        if (isParameters) sendValues(comparing ? resetValues : values);
        status(host, '描画中。停止すると現在の状態で止まります。', 'Drawing. Stop freezes the current state.');
        if (!visible || document.hidden) { post('pause'); automaticPause = true; }
      }
      if (message.type === 'paused') { paused = true; status(host, automaticPause ? '画面外のため一時停止中。' : '停止中。実行で再開できます。', automaticPause ? 'Paused while offscreen.' : 'Stopped. Press Run to resume.'); }
      if (message.type === 'error') {
        const detail = typeof message.message === 'string' ? message.message.slice(0, 500) : '';
        destroy(); status(host, '実行できませんでした。ログ：' + detail, 'Could not run. Log: ' + detail);
      }
      if (message.type === 'frame') {
        host.dataset.frames = String(message.frames);
        host.dataset.pixels = JSON.stringify(message.pixels);
        if (message.physicsY !== null) host.dataset.physicsY = String(message.physicsY);
        if (kind === 'watch' && typeof message.watch === 'number') $('[data-watch]', host).textContent = `x = ${message.watch.toFixed(2)}`;
      }
      updateButtons();
    });
    function pauseForVisibility() {
      if (!frame || !ready) return;
      const canRun = visible && !document.hidden;
      if (!canRun && !paused) { automaticPause = true; post('pause'); }
      else if (canRun && automaticPause) {
        automaticPause = false;
        if (!reducedMotion.matches) post('resume');
      }
    }
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver(entries => { visible = entries[0].isIntersecting; pauseForVisibility(); });
      observer.observe(host);
    }
    document.addEventListener('visibilitychange', pauseForVisibility);
    reducedMotion.addEventListener('change', event => { if (event.matches && frame) post('pause'); });
    document.addEventListener('guide:language', () => {
      if (frame) frame.title = document.documentElement.lang === 'ja' ? 'コード例の実行結果' : 'Code example output';
      const shape = get('shape');
      if (shape) [...shape.options].forEach((option, index) => { option.textContent = document.documentElement.lang === 'ja' ? ['円', '四角', '三角'][index] : ['Circle', 'Square', 'Triangle'][index]; });
    });
    window.GuideAppUI.decorateLive(host, code);
    setupDemoCollapse(host, () => {
      if (frame && ready && !paused) {
        clearTimeout(autoRunTimer);
        post('pause');
        paused = true;
        automaticPause = false;
        updateButtons();
      }
    });
    liveExamples.push({host, destroy});
    updateButtons();
  });
  document.addEventListener('guide:chapter', () => liveExamples.forEach(({host, destroy}) => { if (host.closest('[data-chapter]').hidden) destroy(true); }));

  // Each simulation owns its state. All file/save/export outcomes below stay in memory.
  $$('[data-sim]').forEach(host => {
    const kind = host.dataset.sim;
    let state;
    let elapsedTimer;
    const get = name => $(`[data-control="${name}"]`, host);
    const body = () => $('.sim-body', host);
    const result = (ja, en) => { $('[data-result]', host).innerHTML = bi(ja, en); };
    const say = (ja, en) => status(host, ja, en);
    const names = {
      gallery: ['作品選択とコピー', 'Choose and copy a work'], editor: ['編集・検索・折りたたみ', 'Edit, search, and fold'],
      pip: ['PiPの配置', 'Arrange PiP'], files: ['ファイルと実行設定', 'Files and runtime'], camera: ['カメラ入力の操作図', 'Camera input diagram'],
      mic: ['音量の入力', 'Audio level input'], sensors: ['傾きと加速度', 'Tilt and acceleration'], history: ['保存と履歴', 'Save and history'],
      recovery: ['下書き復旧と再試行', 'Draft recovery and retry'], export: ['PNG・録画の出力', 'PNG and recording export'],
      organize: ['整理と一括操作', 'Organize and batch actions'], backup: ['全体バックアップ', 'Full backup'], share: ['共有カード', 'Share card']
    };
    const lessons = {
      gallery: [['作品を選ぶ', 'コピーする', 'コピーを確認'], ['Choose', 'Copy', 'Inspect copy'], ['palette|weave', 'copy', 'copy']],
      editor: [['数値を変える', '検索・置換', '元に戻す'], ['Adjust', 'Find / replace', 'Undo'], ['number', 'find|replace|fold', 'undo']],
      pip: [['小窓を移動', '表示を調整', '最小化する'], ['Move window', 'Adjust view', 'Minimize'], ['corner|drag', 'size|ghost', 'minimize']],
      files: [['ファイルを選ぶ', '実行方式を選ぶ', '開始ファイルを確認'], ['Choose file', 'Choose mode', 'Inspect entry'], ['file', 'mode', 'resolve']],
      camera: [['模擬映像を表示', '左右反転', '停止する'], ['Show diagram', 'Mirror', 'Stop'], ['camera', 'mirror', 'camera']],
      mic: [['入力値を変える', '音量を見る', '円の変化を見る'], ['Set input', 'Read level', 'Inspect circle'], ['level', 'level', 'level']],
      sensors: [['傾きを変える', '加速度を変える', 'シェイクする'], ['Tilt', 'Accelerate', 'Shake'], ['rx|ry', 'acc', 'shake']],
      history: [['値を変えて保存', '履歴を残す', '履歴から戻す'], ['Edit and save', 'Snapshot', 'Restore'], ['save', 'snapshot', 'restore-0|revert']],
      recovery: [['下書きを適用', '保存失敗を試す', '再試行する'], ['Apply draft', 'Fail save', 'Retry'], ['recover', 'fail', 'retry']],
      export: [['PNGを確認', '録画して停止', '保存失敗から再試行'], ['Inspect PNG', 'Record / stop', 'Fail and retry'], ['png', 'finish', 'retry|destination']],
      organize: [['作品を選ぶ', '移動・タグ付け', '分類を解除'], ['Select', 'Move / tag', 'Unfile'], ['a|b', 'move|tag', 'delete-folder']],
      backup: [['模擬ZIPを作る', '空の移行先を見る', '復元する'], ['Create backup', 'Empty target', 'Restore'], ['backup', 'clear', 'restore']],
      share: [['作者名を変える', '見た目を調整', '出力を確認'], ['Set author', 'Style card', 'Inspect export'], ['author', 'theme|show-code', 'share']]
    };
    let completed = new Set();
    const shell = (contents, actions) => {
      completed = new Set();
      const lesson = lessons[kind];
      host.innerHTML = `<div class="demo-heading"><span class="demo-tag">${bi('模擬操作', 'SIMULATION')}</span><span>${bi('実ファイルの保存・変更は行いません', 'No actual files are saved or changed')}</span></div><h4 class="simulation-title">${bi(...names[kind])}</h4><ol class="practice-steps">${lesson[0].map((j,i) => `<li data-step="${i}"><span class="step-marker">${i+1}</span>${bi(j,lesson[1][i])}</li>`).join('')}</ol><div class="sim-panel"><div class="sim-toolbar"><span class="sim-app-mark" aria-hidden="true">RiN</span><b>${bi(...names[kind])}</b><span class="mock-label">${bi('操作図', 'Mockup')}</span></div><div class="sim-body">${contents}</div><div class="sim-commandbar demo-actions">${actions}</div><div class="sim-readout" data-result role="status"></div></div><div class="simulation-footer">${action('reset', 'はじめから試す', 'Start over')}<p class="demo-status" role="status"></p></div>`;
    };
    function progress(name) {
      lessons[kind][2].forEach((actions,i) => {
        if (kind === 'camera' && name === 'camera') { if (i === (state.camera ? 2 : 0)) completed.add(i); }
        else if (actions.split('|').includes(name) || (kind === 'history' && i === 2 && name.startsWith('restore-'))) completed.add(i);
      });
      $$('[data-step]',host).forEach((el,i) => {
        el.classList.toggle('complete', completed.has(i));
        $('.step-marker',el).textContent = completed.has(i) ? '✓' : String(i+1);
      });
    }
    const circlePreview = (label, attr) => `<div class="value-preview"><span>${bi(...label)}</span><div class="value-canvas"><i ${attr} style="--diameter:100px"></i></div></div>`;
    function init() {
      clearInterval(elapsedTimer);
      state = {selected: 'Palette', copied: false, corner: 3, large: false, ghost: false, minimized: false, camera: false, mirror: false, value: 100, saved: 100, snapshots: [], folded: false, source: 'function draw() {\n  background(242);\n  circle(200, 140, 100);\n}', undo: [], recording: false, failed: false, backup: null, deleted: false};
      if (kind === 'gallery') {
        shell(`<div class="mock-section-label">${bi('サンプル / 読み取り専用', 'Samples / Read-only')}</div><div class="sim-grid"><button class="sim-card" data-action="palette" aria-pressed="true"><span class="mini-art"></span>Palette</button><button class="sim-card" data-action="weave" aria-pressed="false"><span class="mini-art weave"></span>Weave</button></div><div class="gallery-copy" hidden></div>`, action('copy', 'コピーして編集', 'Copy to edit'));
        result('サンプル → Palette（読み取り専用）', 'Samples → Palette (read-only)');
      } else if (kind === 'editor') {
        shell(`<div class="editor-document"><div class="document-tab">sketch.js <span>JavaScript</span></div><div class="sim-code" tabindex="0" data-source></div></div><div class="editor-accessory">${slider('number', '数値調整', 'Number adjustment', 20, 200, 100, 10)}<label class="control">${bi('検索文字列', 'Find text')}<input type="text" data-control="find" value="circle"></label></div>`, action('undo', 'Undo', 'Undo') + action('fold', '折りたたむ／展開', 'Fold / expand') + action('replace', 'circleをsquareに置換', 'Replace circle with square'));
        renderEditor();
      } else if (kind === 'pip') {
        shell(`<div class="pip-workspace"><div class="sim-code">function draw() {<br>  background(242);<br>  circle(200, 140, 100);<br>}</div><div class="pip-window" aria-label="${document.documentElement.lang === 'en' ? 'Simulated PiP' : '模擬PiP'}" data-aria-ja="模擬PiP" data-aria-en="Simulated PiP"></div></div>`, action('corner', '次の隅へ', 'Next corner') + action('size', 'サイズ', 'Size') + action('ghost', '半透明', 'Translucent') + action('minimize', '最小化', 'Minimize'));
        placePip();
        const win = $('.pip-window', host), workspace = $('.pip-workspace', host);
        let drag = null;
        win.addEventListener('pointerdown', event => { if (event.button !== 0 || event.target.closest('button')) return; drag = {x: event.clientX, y: event.clientY, left: win.offsetLeft, top: win.offsetTop}; win.setPointerCapture(event.pointerId); });
        win.addEventListener('pointermove', event => {
          if (!drag) return;
          win.style.right = 'auto'; win.style.bottom = 'auto';
          win.style.left = `${Math.max(0, Math.min(workspace.clientWidth - win.offsetWidth, drag.left + event.clientX - drag.x))}px`;
          win.style.top = `${Math.max(0, Math.min(workspace.clientHeight - win.offsetHeight, drag.top + event.clientY - drag.y))}px`;
        });
        const end = () => { if (!drag) return; state.corner = (win.offsetTop + win.offsetHeight / 2 > workspace.clientHeight / 2 ? 2 : 0) + (win.offsetLeft + win.offsetWidth / 2 > workspace.clientWidth / 2 ? 1 : 0); drag = null; placePip(); progress('drag'); };
        win.addEventListener('pointerup', end); win.addEventListener('pointercancel', end);
      } else if (kind === 'files') {
        shell(`<div class="mock-file-browser"><div class="file-tree">${['sketch.js','index.html','lib/math.mjs','assets/photo.png'].map((f,i)=>`<button type="button" data-file="${i}" aria-pressed="${i===0}"><span class="file-kind">${['JS','HTML','MJS','PNG'][i]}</span>${f}</button>`).join('')}</div><pre class="file-preview" data-file-preview>function setup() {
  createCanvas(400, 280);
}</pre></div>${select('mode', '実行方式', 'Execution mode', [['auto', '自動', 'Automatic'], ['classic', '通常JS', 'Classic JS'], ['module', 'モジュール', 'Module'], ['html', 'HTML', 'HTML']])}`, action('resolve', '開始ファイルを確認', 'Inspect entry file'));
        result('この構成の「自動」はindex.htmlを優先します。', 'With this structure, Automatic prefers index.html.');
      } else if (kind === 'camera') {
        shell(`<div class="camera-diagram off"><span>${bi('合成した説明図・カメラアクセスなし', 'Synthetic diagram · no camera access')}</span></div>`, action('camera', '模擬映像の開始／停止', 'Start / stop diagram') + action('mirror', '左右反転', 'Mirror'));
      } else if (kind === 'mic') {
        shell(`${slider('level', '入力レベル（模擬）', 'Simulated input level', 0, 1, .25, .01)}<div class="meter"><div class="meter-fill"></div></div><div class="camera-diagram"><span data-size></span></div>`, '');
        updateMic();
      } else if (kind === 'sensors') {
        shell(`<div class="sensor-board"><div class="sensor-phone"><div class="sensor-ball"></div></div></div><div class="sim-controls">${slider('rx', 'rotationX', 'rotationX', -45, 45, 0)}${slider('ry', 'rotationY', 'rotationY', -45, 45, 0)}${slider('acc', 'accelerationX', 'accelerationX', -10, 10, 0, .5)}</div>`, action('shake', 'シェイク入力', 'Simulate shake'));
        updateSensors();
      } else if (kind === 'history') {
        shell(`${slider('value', '編集中の直径', 'Edited diameter', 20, 200, 100, 10)}<div class="history-preview">${circlePreview(['編集中','Editing'],'data-edit-circle')}${circlePreview(['保存済み','Saved'],'data-save-circle')}</div><div class="history-values"><p>${bi('編集中', 'Editing')}<output data-edited>100</output></p><p>${bi('保存済み', 'Saved')}<output data-saved>100</output></p></div><div class="snapshot-list"></div>`, action('save', '模擬保存', 'Simulate save') + action('snapshot', 'スナップショット', 'Snapshot') + action('revert', '保存済み状態に戻す', 'Revert to saved'));
        updateHistory();
        result('保存・履歴はこのデモ内の数値だけを保持します。', 'Saves and snapshots retain only the number inside this demo.');
      } else if (kind === 'recovery') {
        shell(`<div class="recovery-comparison"><div><span>${bi('保存済み','Saved')}</span><code>diameter: 100</code></div><div><span>${bi('見つかった下書き','Recovered draft')}</span><code>diameter: 150</code></div></div><div class="recovery-editor"><div class="document-tab">${bi('編集中の内容','Editing')}<span data-save-state>${bi('保存済み','Saved')}</span></div><div class="sim-code" data-recovery>circle(200, 140, 100);</div></div>`, action('recover', '下書きを適用', 'Apply draft') + action('fail', '保存失敗を試す', 'Simulate save failure') + action('retry', '再試行', 'Retry'));
        result('対象：練習用作品。内容を確認して復旧します。', 'Target: practice work. Review its contents before recovery.');
      } else if (kind === 'export') {
        shell(`<div class="sim-controls">${select('scale', 'PNG倍率', 'PNG scale', [['1', '1倍', '1×'], ['2', '2倍', '2×'], ['4', '4倍', '4×']])}${select('format', '録画形式', 'Recording format', [['MP4', 'MP4', 'MP4'], ['GIF', 'GIF', 'GIF']])}</div><div class="export-canvas"><div class="mini-art"></div><span class="export-dimensions">400 × 280 px</span><span class="record-indicator" hidden>● REC <output data-time>00:00</output></span></div><div class="export-receipt" hidden><span class="receipt-type">PNG</span><div><strong data-export-name></strong><p data-export-state></p></div></div>`, action('png', 'PNGを模擬出力', 'Simulate PNG') + action('record', '録画開始', 'Start recording') + action('finish', '停止・模擬保存', 'Stop / simulate save') + action('fail', '保存失敗', 'Fail save') + action('retry', 'もう一度保存', 'Retry saving') + action('destination', '別の保存先を選ぶ', 'Choose another destination') + action('discard', '録画を破棄', 'Discard recording'));
        result('400 × 280pxのキャンバスを想定します。実際のファイルは作成しません。', 'Assumes a 400 × 280px canvas. No actual file is created.');
        updateRecording();
      } else if (kind === 'organize') {
        shell(`<div class="sim-controls">${checkbox('a', '作品Aを選択', 'Select work A')}${checkbox('b', '作品Bを選択', 'Select work B')}</div><div class="organization-list" data-location>A → 未分類 / Unfiled\nB → 未分類 / Unfiled</div>`, action('move', '選択作品を練習フォルダーへ', 'Move selected to Practice') + action('tag', '選択作品にタグ', 'Tag selected') + action('delete-folder', '練習フォルダーを削除', 'Delete Practice folder'));
        state.locations = ['unfiled', 'unfiled']; state.tags = ['', ''];
        renderOrganization();
        result('作品を選択してから、一括操作を試します。', 'Select works before trying batch actions.');
      } else if (kind === 'backup') {
        shell(`<div class="backup-map"><div class="backup-collection" data-collection><strong>${bi('作品集','Collection')}</strong><span>A · B</span><small>${bi('テンプレート・設定','Templates · settings')}</small></div><span class="backup-arrow" aria-hidden="true">⇄</span><div class="backup-archive" data-archive><strong>ZIP</strong><small>${bi('未作成','Not created')}</small></div></div>`, action('backup', '模擬ZIPを作る', 'Simulate backup') + action('clear', '移行先の空状態へ', 'Empty destination') + action('restore', '模擬ZIPから復元', 'Restore simulated backup'));
        result('練習用の作品集があります。バックアップを作ってから移行を試します。', 'A practice collection is present. Back it up before simulating migration.');
      } else if (kind === 'share') {
        shell(`<div class="sim-controls"><label class="control">${bi('作者名（模擬）', 'Author (simulation)')}<input type="text" data-control="author" maxlength="40" value="Creator"></label>${checkbox('show-code', 'コードを掲載', 'Include code', true)}${select('theme', 'テーマ', 'Theme', [['dark', 'ダーク', 'Dark'], ['light', 'ライト', 'Light'], ['sumi', '墨', 'Sumi']])}</div><div class="share-card-preview dark-card"><div class="mini-art"></div><strong data-author>Creator</strong><pre data-card-code>circle(200, 140, 100);</pre></div>`, action('share', '共有画像を模擬作成', 'Simulate card export'));
        result('QRは指定URLへのリンクです。ここでは画像やQRを生成しません。', 'A QR links to a specified URL. This demo does not generate an image or QR code.');
      }
      say('実機の配置を参考にした模擬画面です。', 'An interactive mockup based on the app layout.');
      window.GuideAppUI.decorateSimulation(host);
      setupDemoCollapse(host);
    }
    function renderEditor() {
      const query = get('find').value;
      const number = state.source.match(/, (\d+)\);/);
      if (number) {
        get('number').value = number[1];
        $('[data-output=number]', host).textContent = number[1];
      }
      let source = state.folded ? 'function draw() { … }' : state.source;
      const escaped = escape(source);
      $('[data-source]', host).innerHTML = query ? escaped.split(escape(query)).join(`<mark>${escape(query)}</mark>`) : escaped;
      result(state.folded ? 'ブロックを非表示にしています。元のコードは保持しています。' : '数値や検索語を変えて、コード表示を確認します。', state.folded ? 'The block is hidden; source is retained.' : 'Change the number or search term to inspect the source.');
    }
    function placePip() {
      const win = $('.pip-window', host);
      win.style.left = state.corner % 2 ? 'auto' : '8px'; win.style.right = state.corner % 2 ? '8px' : 'auto';
      win.style.top = state.corner < 2 ? '8px' : 'auto'; win.style.bottom = state.corner < 2 ? 'auto' : '8px';
      win.classList.toggle('large', state.large); win.classList.toggle('ghost', state.ghost); win.classList.toggle('minimized', state.minimized);
      ['size', 'ghost', 'minimize'].forEach(name => $(`[data-action=${name}]`, host).setAttribute('aria-pressed', String(state[{size: 'large', ghost: 'ghost', minimize: 'minimized'}[name]])));
      result('小窓は四隅へ移動します。アプリ外への表示はしません。', 'The window snaps to a corner. It does not overlay other apps.');
    }
    function updateMic() {
      const level = Number(get('level').value);
      $('[data-output=level]', host).textContent = level.toFixed(2);
      $('.meter-fill', host).style.width = `${level * 100}%`;
      $('[data-size]', host).innerHTML = bi(`直径 = ${(30 + level * 130).toFixed(1)}`, `Diameter = ${(30 + level * 130).toFixed(1)}`);
      $('.camera-diagram', host).style.setProperty('--mic-size', `${30 + level * 130}px`);
      result('マイク入力をスライダーの値で置き換えています。', 'A slider substitutes for microphone input.');
    }
    function updateSensors() {
      const rx = Number(get('rx').value), ry = Number(get('ry').value), acc = Number(get('acc').value);
      ['rx', 'ry', 'acc'].forEach(k => { $(`[data-output=${k}]`, host).textContent = get(k).value; });
      $('.sensor-phone', host).style.transform = `rotate(${ry * .5}deg)`;
      $('.sensor-ball', host).style.transform = `translate(${ry * .4}px,${rx * .7}px) scale(${1 + Math.abs(acc) * .05})`;
      result(`rotationX=${rx} / rotationY=${ry} / accelerationX=${acc}（模擬入力）`, `rotationX=${rx} / rotationY=${ry} / accelerationX=${acc} (simulated)`);
    }
    function updateHistory() {
      $('[data-edit-circle]',host).style.setProperty('--diameter', `${state.value*.6}px`);
      $('[data-save-circle]',host).style.setProperty('--diameter', `${state.saved*.6}px`);
      host.dataset.dirty = String(state.value !== state.saved);
      $('[data-edited]', host).textContent = state.value; $('[data-saved]', host).textContent = state.saved; get('value').value = state.value;
    }
    function updateRecording() {
      $('[data-action=png]', host).disabled = state.recording;
      $('[data-action=finish]', host).disabled = !state.recording;
      $('[data-action=record]', host).disabled = state.recording;
      $('[data-action=fail]', host).disabled = !state.recorded;
      ['retry', 'destination', 'discard'].forEach(k => { $(`[data-action=${k}]`, host).disabled = !state.failed; });
      get('format').disabled = state.recording;
      $('.record-indicator',host).hidden = !state.recording;
      if (state.recorded) {
        $('.export-receipt',host).hidden = false;
        $('.receipt-type',host).textContent = state.format;
        $('[data-export-name]',host).textContent = `practice.${state.format.toLowerCase()}`;
        $('[data-export-state]',host).innerHTML = state.failed ? bi('保存待ち・再試行できます','Pending · retry available') : bi('模擬保存完了','Simulated save complete');
        $('.export-receipt',host).classList.toggle('pending',state.failed);
      }
    }
    function renderOrganization() {
      $('[data-location]', host).innerHTML = ['A', 'B'].map((name, i) => {
        const location = state.locations[i] === 'practice' ? ['練習', 'Practice'] : ['未分類', 'Unfiled'];
        return `<div class="organization-work"><span class="work-initial">${name}</span><div>${bi(`${name} → ${location[0]} ${state.tags[i]}`, `${name} → ${location[1]} ${state.tags[i]}`)}</div></div>`;
      }).join('\n');
    }
    host.addEventListener('input', event => {
      const name = event.target.dataset.control;
      if (!name) return;
      progress(name);
      if (kind === 'editor') {
        if (name === 'number') { state.undo.push(state.source); state.source = state.source.replace(/, \d+\);/, `, ${get('number').value});`); $('[data-output=number]', host).textContent = get('number').value; }
        renderEditor();
      }
      if (kind === 'mic') updateMic();
      if (kind === 'sensors') updateSensors();
      if (kind === 'history') { state.value = Number(get('value').value); $('[data-output=value]', host).textContent = state.value; updateHistory(); result('編集中の値だけが変わりました。', 'Only the edited value changed.'); }
      if (kind === 'share') {
        $('[data-author]', host).textContent = get('author').value;
        $('[data-card-code]', host).hidden = !get('show-code').checked;
        const themeValue = get('theme').value;
        $('.share-card-preview', host).classList.toggle('dark-card', themeValue === 'dark');
        $('.share-card-preview', host).classList.toggle('sumi-card', themeValue === 'sumi');
      }
    });
    host.addEventListener('click', event => {
      const file = event.target.closest('[data-file]');
      if (file) {
        $$('[data-file]',host).forEach(b=>b.setAttribute('aria-pressed',String(b===file)));
        const snippets = ['function setup() {\n  createCanvas(400, 280);\n}', '<main></main>\n<script src="sketch.js"></script>', 'export const twice = x => x * 2;', 'photo.png  ·  400 × 280 px'];
        $('[data-file-preview]',host).textContent = snippets[Number(file.dataset.file)];
        progress('file');
      }
      const name = event.target.closest('[data-action]')?.dataset.action;
      if (!name) return;
      if (name === 'reset') { init(); $('[data-action=reset]', host).focus(); return; }
      progress(name);
      if (kind === 'gallery') {
        if (name === 'palette' || name === 'weave') {
          state.selected = name === 'palette' ? 'Palette' : 'Weave';
          $$('[data-action=palette],[data-action=weave]', host).forEach(b => b.setAttribute('aria-pressed', String(b.dataset.action === name)));
          result(`サンプル → ${state.selected}（読み取り専用）`, `Samples → ${state.selected} (read-only)`);
        }
        if (name === 'copy') {
          const copy = $('.gallery-copy',host); copy.hidden = false;
          copy.innerHTML = `<div class="mock-section-label">${bi('自分の作品 / 編集可能（模擬）','My works / Editable (simulated)')}</div><div class="copied-work"><span class="mini-art ${state.selected === 'Weave' ? 'weave' : ''}"></span><div><strong>${escape(state.selected)}</strong><p>${bi('コピーを作成しました。サンプルは元のままです。','A copy is ready. The sample is unchanged.')}</p><code>sketch.js</code></div></div>`;
          result(`自分の作品 → ${state.selected}のコピー（模擬作成）`, `My works → ${state.selected} copy (simulated)`);
        }
      }
      if (kind === 'editor') {
        if (name === 'fold') state.folded = !state.folded;
        if (name === 'undo' && state.undo.length) state.source = state.undo.pop();
        if (name === 'replace') { state.undo.push(state.source); state.source = state.source.replaceAll('circle', 'square'); }
        renderEditor();
      }
      if (kind === 'pip') {
        if (name === 'corner') state.corner = (state.corner + 1) % 4;
        if (name === 'size') state.large = !state.large;
        if (name === 'ghost') state.ghost = !state.ghost;
        if (name === 'minimize') state.minimized = !state.minimized;
        placePip();
      }
      if (kind === 'files' && name === 'resolve') {
        const mode = get('mode').value;
        result(mode === 'module' ? 'モジュール → lib/math.mjs（この例で選んだ開始ファイル）' : mode === 'classic' ? '通常JS → sketch.js' : 'HTML → index.html', mode === 'module' ? 'Module → lib/math.mjs (selected entry in this example)' : mode === 'classic' ? 'Classic JS → sketch.js' : 'HTML → index.html');
      }
      if (kind === 'camera') {
        if (name === 'camera') { state.camera = !state.camera; $('.camera-diagram', host).classList.toggle('off', !state.camera); }
        if (name === 'mirror') { state.mirror = !state.mirror; $('.camera-diagram', host).classList.toggle('mirror', state.mirror); }
        $('[data-action=camera]', host).setAttribute('aria-pressed', String(state.camera));
        $('[data-action=mirror]', host).setAttribute('aria-pressed', String(state.mirror));
        result(state.camera ? '説明図を表示中。実際のカメラ映像ではありません。' : '説明図を停止中。', state.camera ? 'Diagram active. This is not a real camera feed.' : 'Diagram stopped.');
      }
      if (kind === 'sensors' && name === 'shake') {
        state.shaken = !state.shaken; $('.sensor-ball', host).style.background = state.shaken ? '#426b5d' : '#b85338';
        result('deviceShaken()で色を変更する操作の例です。', 'An example reaction to deviceShaken() changes the color.');
      }
      if (kind === 'history') {
        if (name === 'save') state.saved = state.value;
        if (name === 'snapshot') {
          if (state.snapshots.length >= 8) {
            result('この練習ではスナップショットは8件までです。初期化で消去できます。', 'This practice keeps up to eight snapshots. Reset clears them.');
            say('この練習では8件までです。初期化で消去できます。', 'This practice keeps up to eight snapshots. Reset clears them.');
            return;
          }
          state.snapshots.push(state.value);
          $('.snapshot-list', host).insertAdjacentHTML('beforeend', action(`restore-${state.snapshots.length - 1}`, `版${state.snapshots.length}：${state.value}`, `Version ${state.snapshots.length}: ${state.value}`));
        }
        if (name === 'revert') state.value = state.saved;
        if (name.startsWith('restore-')) state.value = state.snapshots[Number(name.slice(8))];
        updateHistory();
        $('[data-output=value]', host).textContent = state.value;
        result('このデモ内の保存・履歴を更新しました。', 'Updated the in-memory save or snapshot state.');
      }
      if (kind === 'recovery') {
        if (name === 'recover') { state.value = 150; $('[data-recovery]', host).textContent = 'circle(200, 140, 150);'; result('下書きを編集中の内容へ適用しました。保存済みは100のままです。', 'Applied the draft to editing. The saved value remains 100.'); }
        if (name === 'fail') { state.failed = true; result(`模擬保存失敗。編集中の値${state.value}を保持しています。`, `Simulated failure. Edited value ${state.value} is retained.`); }
        if (name === 'retry') { state.saved = state.value; state.failed = false; result(`再試行で${state.saved}を模擬保存しました。`, `Retried and saved ${state.saved} in the simulation.`); }
      }
      if (kind === 'recovery') {
        const label = $('[data-save-state]',host);
        label.innerHTML = state.failed ? bi('保存失敗・内容は保持','Save failed · content retained') : state.saved !== state.value ? bi('未保存','Unsaved') : bi('保存済み（模擬）','Saved (simulated)');
        label.classList.toggle('save-failed',state.failed);
        $('.recovery-comparison code',host).textContent = `diameter: ${state.saved}`;
      }
      if (kind === 'export') {
        if (name === 'png') { state.recorded = false; state.failed = false; const scale = Number(get('scale').value);
          $('.export-receipt',host).hidden = false; $('.export-receipt',host).classList.remove('pending');
          $('.receipt-type',host).textContent = 'PNG'; $('[data-export-name]',host).textContent = 'practice.png';
          $('[data-export-state]',host).innerHTML = bi(`${400*scale} × ${280*scale} px・模擬出力`,`${400*scale} × ${280*scale} px · simulated`); result(`PNG ${400 * scale} × ${280 * scale}px（模擬出力）`, `PNG ${400 * scale} × ${280 * scale}px (simulated)`); }
        if (name === 'record') {
          state.recording = true; state.recorded = false; state.failed = false; state.format = get('format').value; state.seconds = 0;
          $('[data-time]',host).textContent = '00:00'; $('.export-receipt',host).hidden = true;
          clearInterval(elapsedTimer);
          elapsedTimer = setInterval(() => {
            if (host.closest('[data-chapter]').hidden || document.hidden) return;
            state.seconds++; $('[data-time]',host).textContent = `${String(Math.floor(state.seconds/60)).padStart(2,'0')}:${String(state.seconds%60).padStart(2,'0')}`; result(`${state.format} 録画操作の練習 ${state.seconds}秒`, `${state.format} recording practice ${state.seconds}s`);
          }, 1000);
          result('録画操作の練習中。停止ボタンで終了します。', 'Recording practice started. Stop to finish.');
        }
        if (name === 'finish') { clearInterval(elapsedTimer); state.recording = false; state.recorded = true; result(`${state.format} ${state.seconds}秒・模擬保存完了`, `${state.format} ${state.seconds}s · simulated save complete`); }
        if (name === 'fail') { state.failed = true; result('保存失敗の練習。録画は保留しています。', 'Save failure practice. Recording remains pending.'); }
        if (name === 'retry' || name === 'destination') { state.failed = false; result('保留していた録画を模擬保存しました。', 'Simulated saving of the pending recording.'); }
        if (name === 'discard') { $('.export-receipt',host).hidden = true; state.failed = false; state.recorded = false; result('保留していた模擬録画を破棄しました。', 'Discarded the simulated pending recording.'); }
        updateRecording();
      }
      if (kind === 'organize') {
        if (name === 'move') ['a', 'b'].forEach((k, i) => { if (get(k).checked) state.locations[i] = 'practice'; });
        if (name === 'tag') ['a', 'b'].forEach((k, i) => { if (get(k).checked) state.tags[i] = '#study'; });
        if (name === 'delete-folder') state.locations = state.locations.map(() => 'unfiled');
        renderOrganization(); result('練習用の分類を更新しました。作品は削除していません。', 'Updated practice organization. No works were deleted.');
      }
      if (kind === 'backup') {
        if (name === 'backup') { state.backup = !state.deleted; result(state.backup ? '練習用の作品集・テンプレート・設定をメモリー内に保持しました。' : '移行先は空です。初期化で練習をやり直せます。', state.backup ? 'Retained the practice collection, templates, and settings in memory.' : 'The destination is empty. Reset to try again.'); }
        if (name === 'clear') { state.deleted = true; result('移行先の空の作品集を模擬表示しています。', 'Simulating an empty collection on the destination.'); }
        if (name === 'restore') { if (state.backup) { state.deleted = false; result('作品A・作品B・テンプレート・設定を模擬復元しました。', 'Simulated restoration of works A and B, templates, and settings.'); } else result('先に模擬バックアップを作成してください。', 'Create a simulated backup first.'); }
      }
      if (kind === 'backup') {
        $('[data-collection]',host).classList.toggle('empty',state.deleted);
        $('[data-collection] > span',host).innerHTML = state.deleted ? bi('空の移行先','Empty destination') : 'A · B';
        $('[data-collection] small',host).hidden = state.deleted;
        $('[data-archive]',host).classList.toggle('ready',!!state.backup);
        $('[data-archive] small',host).innerHTML = state.backup ? bi('メモリー内に保持','Held in memory') : bi('未作成','Not created');
      }
      if (kind === 'share' && name === 'share') result('プレビューの条件で共有画像を作る操作です。実際の保存・共有は行いません。', 'This illustrates creating a card from the preview. No file is saved or shared.');
    });
    document.addEventListener('guide:chapter', () => {
      if (host.closest('[data-chapter]').hidden) {
        clearInterval(elapsedTimer);
        if (kind === 'export' && state.recording) { state.recording = false; result('章の移動により模擬録画を終了しました。', 'Simulated recording ended when leaving the chapter.'); updateRecording(); }
      }
    });
    document.addEventListener('guide:language', () => {
      const translations = {mode: [['自動', 'Automatic'], ['通常JS', 'Classic JS'], ['モジュール', 'Module'], ['HTML', 'HTML']], scale: [['1倍', '1×'], ['2倍', '2×'], ['4倍', '4×']], theme: [['ダーク', 'Dark'], ['ライト', 'Light'], ['墨', 'Sumi']]};
      Object.entries(translations).forEach(([name, labels]) => {
        const element = get(name);
        if (element) [...element.options].forEach((option, i) => { option.textContent = labels[i][document.documentElement.lang === 'ja' ? 0 : 1]; });
      });
    });
    init();
  });
})();
