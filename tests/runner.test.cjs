const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const html = readFileSync(`${__dirname}/../www/p5_runner.html`, 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)]
  .map(match => match[1]).filter(Boolean);

test('recording streams bounded chunks in order and finishes only after final data', async () => {
  const r = runner();
  const writes = [], finished = [];
  let recorder;
  r.context.FileReader = class {
    readAsDataURL(blob) {
      blob.arrayBuffer().then(bytes => {
        this.result = 'data:video/mp4;base64,' + Buffer.from(bytes).toString('base64');
        this.onload();
      });
    }
  };
  r.context.Android.beginRecordingTransfer = () => true;
  r.context.Android.appendRecordingChunk = (id, data) => { writes.push(Buffer.from(data, 'base64')); return true; };
  r.context.Android.finishRecordingTransfer = (id, mime) => finished.push(mime);
  r.context.MediaRecorder = class {
    static isTypeSupported() { return true; }
    constructor() { recorder = this; this.mimeType = 'video/mp4'; this.state = 'inactive'; }
    start() { this.state = 'recording'; }
    stop() {
      this.state = 'inactive';
      this.ondataavailable({data:new Blob(['final'])});
      this.onstop();
    }
  };
  r.context.__editKiroStartRecording('mp4');
  const first = Buffer.alloc(500_000, 97);
  recorder.ondataavailable({data:new Blob([first])});
  r.context.__editKiroStopRecording();
  for (let i = 0; i < 50 && !finished.length; i++) await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(finished, ['video/mp4']);
  assert.ok(writes.every(chunk => chunk.length <= 192 * 1024));
  assert.deepEqual(Buffer.concat(writes), Buffer.concat([first, Buffer.from('final')]));
});

test('failed recording chunk aborts transfer without publishing a partial file', async () => {
  const r = runner();
  let recorder, aborted = 0, finished = 0;
  r.context.FileReader = class {
    readAsDataURL() { this.result = 'data:video/mp4;base64,YQ=='; queueMicrotask(() => this.onload()); }
  };
  r.context.Android.beginRecordingTransfer = () => true;
  r.context.Android.appendRecordingChunk = () => false;
  r.context.Android.abortRecordingTransfer = () => aborted++;
  r.context.Android.finishRecordingTransfer = () => finished++;
  r.context.MediaRecorder = class {
    static isTypeSupported() { return true; }
    constructor() { recorder = this; this.mimeType = 'video/mp4'; this.state = 'inactive'; }
    start() { this.state = 'recording'; }
    stop() { this.state = 'inactive'; }
  };
  r.context.__editKiroStartRecording('mp4');
  recorder.ondataavailable({data:new Blob(['a'])});
  await new Promise(resolve => setImmediate(resolve));
  assert.ok(aborted > 0);
  assert.equal(finished, 0);
  assert.equal(r.statuses.at(-1), false);
  assert.ok(r.errors.length);
});

test('sound starts from preview interaction without visible language text', () => {
  assert.equal(html.includes('タップして音声を開始'), false);
  assert.equal(html.includes('id="sound-start"'), false);
  assert.match(html, /addEventListener\('pointerdown', unlockAudio, true\)/);
});

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
      getP5Version: () => '1.11.5',
      isP5SoundEnabled: () => false,
      onStatusChanged: status => statuses.push(status),
      onError: error => errors.push(error),
      onRuntimeError: error => errors.push(error),
      onRecordingStatusChanged: status => statuses.push(status),
      onCaptureError: error => errors.push(error)
    },
    requestAnimationFrame(callback) { frames.set(++frameId, callback); return frameId; },
    cancelAnimationFrame(id) { frames.delete(id); },
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 2, clearInterval() {},
    addEventListener(name, callback) { events[name] = callback; },
    isLooping: () => true,
    resizeCanvas(w, h) { resizes++; context.width = w; context.height = h; canvas.width = w; canvas.height = h; },
    p5: function P5() {},
    document: {
      documentElement: { clientWidth: 320, clientHeight: 180 },
      write() {},
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

test('MP4 recording never falls back to a differently labelled WebM file', () => {
  const r = runner();
  r.context.MediaRecorder = class {
    static isTypeSupported(type) { return type.startsWith('video/webm'); }
  };
  r.context.__editKiroStartRecording('mp4');
  assert.deepEqual(r.statuses, [false]);
  assert.equal(r.errors[0], 'この端末はMP4録画に対応していません');
  assert.equal(r.stats().stoppedTracks, 0);
});

test('MP4 recording applies the selected bitrate', () => {
  const r = runner();
  let options;
  r.context.MediaRecorder = class {
    static isTypeSupported(type) { return type.startsWith('video/mp4'); }
    constructor(stream, selected) {
      options = selected;
      this.state = 'inactive';
      this.mimeType = selected.mimeType;
    }
    start() {}
  };
  r.context.__editKiroStartRecording('mp4', 10000000);
  assert.equal(options.videoBitsPerSecond, 10000000);
  assert.match(options.mimeType, /^video\/mp4/);
  assert.equal(r.statuses.at(-1), true);
});

test('GIF worker writes a looping 30 fps GIF with a valid frame envelope', async () => {
  const r = runner();
  const messages = [];
  const workerSelf = { postMessage: message => messages.push(message) };
  const workerContext = { self: workerSelf, Blob, Uint8Array, Uint8ClampedArray, Map, Math };
  vm.createContext(workerContext);
  const workerSource = r.context.__editKiroGifWorkerSource;
  vm.runInContext(workerSource, workerContext);
  workerSelf.onmessage({ data: { type: 'init', width: 32, height: 32 } });
  const pixels = new Uint8ClampedArray(32 * 32 * 4);
  for (let index = 0; index < 32 * 32; index++) {
    const value = index & 255;
    pixels[index * 4] = value;
    pixels[index * 4 + 1] = value;
    pixels[index * 4 + 2] = value;
    pixels[index * 4 + 3] = 255;
  }
  workerSelf.onmessage({ data: { type: 'frame', buffer: pixels.buffer, delayCs: 3 } });
  workerSelf.onmessage({ data: { type: 'extend', delayCs: 7 } });
  workerSelf.onmessage({ data: { type: 'finish' } });
  assert.deepEqual(messages.map(message => message.type), ['ready', 'done']);
  const encoded = new Uint8Array(await messages[1].blob.arrayBuffer());
  assert.equal(Buffer.from(encoded.subarray(0, 6)).toString('ascii'), 'GIF89a');
  assert.equal(encoded[encoded.length - 1], 0x3b);
  const imageDescriptor = encoded.indexOf(0x2c);
  assert.ok(imageDescriptor > 0);
  assert.equal(encoded[imageDescriptor + 9], 0x87);
  const localPalette = encoded.subarray(imageDescriptor + 10, imageDescriptor + 10 + 256 * 3);
  const paletteColors = new Set();
  for (let index = 0; index < 256; index++) {
    const red = localPalette[index * 3];
    const green = localPalette[index * 3 + 1];
    const blue = localPalette[index * 3 + 2];
    assert.equal(red, green);
    assert.equal(green, blue);
    paletteColors.add(`${red},${green},${blue}`);
  }
  // The 5-bit/channel histogram retains all 32 grayscale buckets instead of
  // forcing them through the old RGB332 palette's four blue levels.
  assert.ok(paletteColors.size >= 30);
  const graphicControl = encoded.findIndex((value, index) =>
    value === 0x21 && encoded[index + 1] === 0xf9 && encoded[index + 2] === 4
  );
  assert.equal(encoded[graphicControl + 4] | (encoded[graphicControl + 5] << 8), 10);
  assert.match(html, /gifTickIndex % 3 === 2 \? 4 : 3/);
  assert.match(html, /ditherAndIndex/);
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

test('bundled Parameters.js parses declarations and draws with rinParams', () => {
  const source = readFileSync(`${__dirname}/../www/samples/Parameters.js`, 'utf8');
  assert.ok(source.includes('// @rin number speed'));
  assert.ok(source.includes('// @rin boolean glow'));
  assert.ok(source.includes('// @rin color theme'));
  const c = {
    TWO_PI: Math.PI * 2, CLOSE: 1, width: 600, height: 600, frameCount: 1,
    createCanvas() {}, background() {}, translate() {}, push() {}, pop() {},
    rotate() {}, beginShape() {}, endShape() {}, vertex() {}, bezierVertex() {},
    circle() {}, stroke() {}, noStroke() {}, strokeWeight() {}, fill() {}, noFill() {},
    sin: Math.sin, cos: Math.cos, red: () => 0, green: () => 229, blue: () => 255,
    color: () => ({}),
    rinParams: { speed: 2, petals: 6, theme: '#00e5ff', bg: '#000000', glow: true, filled: true }
  };
  vm.createContext(c);
  vm.runInContext(source + '\nsetup(); draw();', c);
  assert.ok(vm.runInContext('angle', c) > 0);
});

test('bundled Sound.js parses and initializes with p5.sound APIs', () => {
  const source = readFileSync(`${__dirname}/../www/samples/Sound.js`, 'utf8');
  assert.ok(source.includes('p5.Oscillator'));
  assert.ok(source.includes('p5.FFT'));
  const c = {
    TWO_PI: Math.PI * 2, width: 600, height: 600, frameCount: 1,
    HSB: 1, CLOSE: 2, CENTER: 3, mouseIsPressed: false, mouseX: 0, mouseY: 0,
    createCanvas() {}, colorMode() {}, background() {}, translate() {}, push() {}, pop() {},
    line() {}, stroke() {}, noStroke() {}, strokeWeight() {}, fill() {}, noFill() {},
    beginShape() {}, endShape() {}, curveVertex() {}, textAlign() {}, textSize() {}, text() {},
    min: Math.min, map: (v, a, b, c, d) => c + ((v - a) / (b - a)) * (d - c),
    cos: Math.cos, sin: Math.sin, floor: Math.floor,
    midiToFreq: (m) => 440 * Math.pow(2, (m - 69) / 12),
    p5: {
      Oscillator: function() {
        return { start() {}, amp() {}, freq() {} };
      },
      FFT: function() {
        return {
          waveform: () => new Float32Array(128),
          analyze: () => new Uint8Array(64)
        };
      }
    }
  };
  vm.createContext(c);
  vm.runInContext(source + '\nsetup(); draw();', c);
  assert.ok(vm.runInContext('osc', c));
  assert.ok(vm.runInContext('fft', c));
});

test('hiding and restoring portrait preview preserves artwork and paused pixels', () => {
  const r = runner(); r.setup(); r.flush();
  r.context.isLooping = () => false;
  const original = { ...r.canvas.style };
  r.context.document.documentElement.clientHeight = 0;
  r.events.resize(); r.flush();
  assert.equal(r.context.width, 320);
  assert.equal(r.context.height, 180);
  assert.equal(r.canvas.width, 320);
  assert.equal(r.canvas.height, 180);
  r.context.document.documentElement.clientHeight = 180;
  r.events.resize(); r.flush();
  assert.deepEqual(r.canvas.style, original);
  assert.equal(r.stats().resizes, 0);
  assert.equal(r.stats().painted, 0);
  assert.deepEqual(r.errors, []);
});

test('runtime errors preserve combined source lines and find user frames in Promise stacks', () => {
  const r = runner();
  const locations = [];
  r.context.Android.onRuntimeError = (message, line) => locations.push([message, line]);
  r.context.onerror('helper failed', 'sketch.js', 3);
  r.events.unhandledrejection({ reason: { message: 'async failed', stack: 'Error: async failed\n    at task (sketch.js:12:7)' } });
  r.context.onerror('library failed', 'p5-v2.min.js', 200, 1,
    { stack: 'Error\n    at library (p5-v2.min.js:200:1)\n    at draw (sketch.js:17:2)' });
  r.context.onerror('external', 'https://example.com/sketch.js', 99);
  assert.deepEqual(locations, [['helper failed', 3], ['async failed', 12], ['library failed', 17]]);
  assert.equal(r.errors.at(-1), 'external');
});
