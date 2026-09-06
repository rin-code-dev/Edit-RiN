const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const html = readFileSync(`${__dirname}/../www/p5_runner.html`, 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)]
  .map(match => match[1]).filter(Boolean);

function runner(code = 'function setup() {}') {
  const events = {}, hooks = {}, frames = new Map(), statuses = [], errors = [];
  let frameId = 0, resizes = 0, painted = 0, removed = false, stoppedTracks = 0;
  const drawing = { save() {}, restore() {}, setTransform() {}, drawImage() { painted++; } };
  const canvas = {
    width: 320, height: 180, style: {},
    toDataURL() { return 'data:image/png;base64,AA=='; },
    captureStream() { return { getTracks: () => [{ stop() { stoppedTracks++; } }] }; }
  };
  const context = {
    console, Blob,
    width: 320, height: 180, drawingContext: drawing,
    Android: {
      getSketchCode: () => code,
      onStatusChanged: status => statuses.push(status),
      onError: error => errors.push(error),
      onRuntimeError: error => errors.push(error),
      onRecordingStatusChanged: status => statuses.push(status),
      onCaptureError: error => errors.push(error)
    },
    requestAnimationFrame(callback) { frames.set(++frameId, callback); return frameId; },
    cancelAnimationFrame(id) { frames.delete(id); },
    setTimeout: () => 1, clearTimeout() {},
    addEventListener(name, callback) { events[name] = callback; },
    isLooping: () => true,
    resizeCanvas(w, h) { resizes++; context.width = w; context.height = h; canvas.width = w; canvas.height = h; },
    p5: function P5() {},
    document: {
      documentElement: { clientWidth: 320, clientHeight: 180 },
      getElementById: () => ({ remove() { removed = true; } }),
      querySelector: () => canvas,
      createElement: () => ({ getContext: () => drawing }),
      head: { appendChild(script) {
        try { vm.runInContext(script.textContent, context); }
        catch (error) { context.onerror(error.message, 'sketch.js', 1); }
      } }
    }
  };
  context.p5.prototype.registerMethod = (name, callback) => { hooks[name] = callback; };
  context.window = context;
  vm.createContext(context);
  scripts.forEach(source => vm.runInContext(source, context));
  return {
    context, events, canvas, statuses, errors,
    setup() { hooks.afterSetup.call({ _isGlobal: true }); },
    flush() { for (const [id, callback] of [...frames]) { frames.delete(id); callback(); } },
    stats: () => ({ resizes, painted, removed, stoppedTracks })
  };
}

test('slow preload is not reported as running before setup', () => {
  const r = runner(); r.events.load();
  assert.deepEqual(r.statuses, []);
  r.setup(); r.flush();
  assert.deepEqual(r.statuses, ['実行中']);
  assert.equal(r.stats().resizes, 0);
});

test('syntax errors remain errors without a false running state', () => {
  const r = runner('function setup( {'); r.events.load();
  assert.equal(r.errors.length, 1);
  assert.deepEqual(r.statuses, []);
});

test('a resize burst changes presentation only and preserves paused pixels', () => {
  const r = runner(); r.setup(); r.flush();
  r.context.isLooping = () => false;
  r.context.document.documentElement.clientHeight = 240;
  for (let i = 0; i < 20; i++) r.events.resize();
  r.flush();
  assert.equal(r.stats().resizes, 0);
  assert.equal(r.stats().painted, 0);
  assert.equal(r.canvas.style.width, '320px');
  assert.equal(r.canvas.style.height, '180px');
  assert.equal(r.canvas.style.top, '30px');
  r.events.resize(); r.flush();
  assert.equal(r.stats().resizes, 0);
});

test('800 square retains logical size and high-DPI buffer across repeated pane changes', () => {
  const r = runner();
  r.context.width = r.context.height = 800;
  r.canvas.width = r.canvas.height = 1600;
  r.setup(); r.flush();
  for (const [w, h] of [[400, 400], [200, 300], [900, 500], [400, 400]]) {
    r.context.document.documentElement.clientWidth = w;
    r.context.document.documentElement.clientHeight = h;
    r.events.resize(); r.flush();
    const side = Math.min(w, h);
    assert.equal(r.canvas.style.width, `${side}px`);
    assert.equal(r.canvas.style.height, `${side}px`);
    assert.equal(r.canvas.style.left, `${(w - side) / 2}px`);
    assert.equal(r.canvas.style.top, `${(h - side) / 2}px`);
    assert.equal(r.context.width, 800);
    assert.equal(r.context.height, 800);
    assert.equal(r.canvas.width, 1600);
    assert.equal(r.canvas.height, 1600);
  }
  assert.equal(r.stats().resizes, 0);
});

test('non-square WebGL artwork fits without crop, redraw or camera resize', () => {
  const r = runner();
  r.context.drawingContext = {}; // WebGL context has no 2D drawImage.
  r.context.width = 800; r.context.height = 400;
  r.context.document.documentElement.clientWidth = 300;
  r.context.document.documentElement.clientHeight = 300;
  r.setup(); r.flush();
  assert.equal(r.canvas.style.width, '300px');
  assert.equal(r.canvas.style.height, '150px');
  assert.equal(r.canvas.style.top, '75px');
  assert.equal(r.stats().resizes, 0);
  assert.deepEqual(r.errors, []);
});

test('recording initialization failure releases the capture track', () => {
  const r = runner();
  r.context.MediaRecorder = class {
    static isTypeSupported() { return true; }
    constructor() { throw new Error('unsupported encoder'); }
  };
  r.context.__editKiroStartRecording();
  assert.equal(r.stats().stoppedTracks, 1);
  assert.deepEqual(r.statuses, [false]);
  assert.equal(r.errors[0], 'unsupported encoder');
});

test('screenshot failure does not report recording stopped', () => {
  const r = runner();
  r.canvas.toDataURL = () => { throw new Error('tainted canvas'); };
  r.context.__editKiroCaptureScreenshot();
  assert.deepEqual(r.statuses, []);
  assert.equal(r.errors[0], 'tainted canvas');
});

test('explicit orientation swap exchanges dimensions and restores them', () => {
  const r = runner('function setup() {} function draw() {}');
  r.events.load(); r.setup(); r.flush();
  r.context.width = 800; r.context.height = 450;
  assert.equal(r.context.__editKiroSetCanvasSwapped(true), true);
  r.flush();
  assert.equal(r.context.width, 450);
  assert.equal(r.context.height, 800);
  r.events.resize(); r.flush();
  assert.equal(r.stats().resizes, 1);
  assert.equal(r.context.__editKiroSetCanvasSwapped(false), true);
  r.flush();
  assert.equal(r.context.width, 800);
  assert.equal(r.context.height, 450);
  assert.equal(r.stats().resizes, 2);
});

test('square orientation is a no-op and an unready sketch cannot be swapped', () => {
  const r = runner();
  assert.equal(r.context.__editKiroSetCanvasSwapped(true), false);
  r.context.width = r.context.height = 800;
  r.setup(); r.flush();
  assert.equal(r.context.__editKiroSetCanvasSwapped(true), true);
  r.flush();
  assert.equal(r.stats().resizes, 0);
});

test('orientation repaints paused drawings and preserves setup-only 2D content', () => {
  const r = runner('function setup() {} function draw() {}');
  r.events.load(); r.setup(); r.flush();
  let redraws = 0;
  r.context.isLooping = () => false;
  r.context.redraw = () => redraws++;
  r.context.__editKiroSetCanvasSwapped(true); r.flush();
  assert.equal(redraws, 1);
  const staticWork = runner(); staticWork.setup(); staticWork.flush();
  staticWork.context.__editKiroSetCanvasSwapped(true); staticWork.flush();
  assert.equal(staticWork.stats().painted, 2);
});

test('bundled DVD logo moves, bounces, changes color and stays inside resized canvases', () => {
  const source = readFileSync(`${__dirname}/../www/samples/dvd.js`, 'utf8');
  let size = 12, lastColor;
  const c = { BOLD: 1, LEFT: 2, TOP: 3, deltaTime: 16, windowWidth: 800, windowHeight: 600, pixelDensity() {},
    min: Math.min, max: Math.max, constrain: (x, a, b) => Math.max(a, Math.min(b, x)),
    createCanvas(w, h) { c.width = w; c.height = h; },
    textSize(v) { if (v !== undefined) size = v; return size; },
    textWidth: s => s.length * size * .6, textAscent: () => size * .8, textDescent: () => size * .2,
    textFont() {}, textStyle() {}, textAlign() {}, noStroke() {}, background() {},
    fill(v) { lastColor = v; }, text() {}
  };
  vm.createContext(c); vm.runInContext(source + '\nsetup(); updateLogo();', c);
  const first = vm.runInContext('x', c), color = vm.runInContext('colorIndex', c);
  vm.runInContext('updateLogo()', c); assert.ok(vm.runInContext('x', c) > first);
  vm.runInContext('x = width - logoWidth; updateLogo();', c);
  assert.equal(vm.runInContext('directionX', c), -1);
  assert.notEqual(vm.runInContext('colorIndex', c), color);
  for (const [w, h] of [[540, 960], [100, 60], [1, 1]]) {
    c.width = w; c.height = h;
    vm.runInContext('measureLogo(); for (let i = 0; i < 1000; i++) updateLogo();', c);
    assert.equal(vm.runInContext('x >= 0 && y >= 0 && x + logoWidth <= width && y + logoHeight <= height', c), true);
  }
});
