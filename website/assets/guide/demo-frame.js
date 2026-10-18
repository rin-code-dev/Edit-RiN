/* Runs only the manual's predefined examples in an opaque-origin sandbox. */
(async () => {
  'use strict';
  const config = JSON.parse(document.getElementById('config').textContent);
  const send = (type, detail = {}) => parent.postMessage({guideDemo: config.token, type, ...detail}, '*');
  let instance;
  let paused = false;
  let failed = false;
  const fail = error => { failed = true; send('error', {message: String(error?.message || error).slice(0, 500)}); };
  window.addEventListener('error', event => fail(event.error || event.message));
  window.addEventListener('unhandledrejection', event => fail(event.reason));
  function load(url) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = url;
      script.onload = resolve;
      script.onerror = () => reject(new Error('Library could not be loaded. / ライブラリを読み込めません。'));
      document.head.append(script);
    });
  }
  try {
    await load(config.p5);
    if (config.matter) await load(config.matter);
    const defaults = {size: 100, count: 3, ink: '#B85338', moving: false, shape: 'circle'};
    window.rinParams = {...defaults, ...config.values};
    const script = document.createElement('script');
    let source = config.code;
    // Only this fixed watch fixture is instrumented. This is not a general-purpose watch parser.
    if (config.kind === 'watch') source = source.replace('  circle(x, 140, 60);', '  window.__guideWatchValue = x;\n  circle(x, 140, 60);');
    script.textContent = source;
    document.head.append(script);
    const adapter = document.createElement('script');
    adapter.textContent = config.kind === 'inline'
      ? 'window.__guideApply = v => { size = v.size; count = v.count; ink = v.ink; moving = v.moving; shape = v.shape; };'
      : 'window.__guideApply = v => { Object.assign(window.rinParams, v); };';
    document.head.append(adapter);
    window.__guideApply({...defaults, ...config.values});
    const authoredSetup = window.setup;
    window.setup = function () {
      pixelDensity(1);
      return authoredSetup();
    };
    const authoredDraw = window.draw;
    let lastSent = 0;
    window.draw = function () {
      authoredDraw();
      if (performance.now() - lastSent < 160) return;
      lastSent = performance.now();
      try {
        // Read the canvas, not an assumed animation state, so tests can verify actual drawing.
        const points = [[10, 10], [200, 140], [100, 140], [300, 140], [200, 250]];
        const pixels = points.map(([x, y]) => Array.from(instance.get(x, y)));
        send('frame', {frames: instance.frameCount, pixels, watch: window.__guideWatchValue ?? null,
          physicsY: config.kind === 'physics' ? window.__guidePhysicsY() : null});
      } catch (error) { fail(error); }
    };
    if (config.kind === 'physics') {
      const probe = document.createElement('script');
      probe.textContent = 'window.__guidePhysicsY = () => ball.position.y;';
      document.head.append(probe);
    }
    // Loading p5 first, then installing source, avoids relying on its window-load bootstrap.
    instance = new p5(undefined, document.querySelector('main'));
    const ready = setInterval(() => {
      if (!instance.canvas || failed) return;
      clearInterval(ready);
      send('ready');
      if (config.reducedMotion) { instance.noLoop(); instance.redraw(); paused = true; send('paused'); }
    }, 20);
    setTimeout(() => {
      clearInterval(ready);
      if (!instance.canvas && !failed) fail(new Error('Canvas initialization timed out. / 描画の開始がタイムアウトしました。'));
    }, 12000);
    window.addEventListener('message', event => {
      if (event.source !== parent || event.data?.guideDemo !== config.token) return;
      const message = event.data;
      if (message.action === 'values') {
        const v = message.values;
        if (!v || !Number.isFinite(v.size) || v.size < 20 || v.size > 200 || !Number.isInteger(v.count) || v.count < 1 || v.count > 6
          || !/^#[0-9a-f]{6}$/i.test(v.ink) || typeof v.moving !== 'boolean' || !['circle', 'square', 'triangle'].includes(v.shape)) return;
        window.__guideApply(v);
        if (paused) instance.redraw();
      }
      if (message.action === 'pause') { instance.noLoop(); paused = true; send('paused'); }
      if (message.action === 'resume') { instance.loop(); paused = false; send('ready'); }
    });
  } catch (error) { fail(error); }
})();
