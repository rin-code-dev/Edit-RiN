const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const runnerHtml = readFileSync(`${__dirname}/../www/p5_runner.html`, 'utf8');
const hostSource = readFileSync(`${__dirname}/../www/p5_host.js`, 'utf8');
const bootstrapSource = readFileSync(`${__dirname}/../www/p5_bootstrap.js`, 'utf8');
const sketchSource = readFileSync(`${__dirname}/../www/p5_sketch.js`, 'utf8');
const html = runnerHtml + hostSource + bootstrapSource;
const scripts = [hostSource, bootstrapSource, sketchSource];

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
  r.context.Android.beginRecordingTransfer = owner => { assert.equal(owner, 'run-one'); return true; };
  r.context.Android.appendRecordingChunk = (owner, id, data) => { assert.equal(owner, 'run-one'); writes.push(Buffer.from(data, 'base64')); return true; };
  r.context.Android.finishRecordingTransfer = (owner, id, mime) => { assert.equal(owner, 'run-one'); finished.push(mime); };
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

function runner(code = 'function setup() {}', config = {}, extras = {}) {
  const events = {}, hooks = {}, frames = new Map(), statuses = [], errors = [];
  let frameId = 0, resizes = 0, painted = 0, removed = false, stoppedTracks = 0;
  const drawing = { save() {}, restore() {}, setTransform() {}, drawImage() { painted++; } };
  const canvas = {
    width: 320, height: 180, style: {},
    toDataURL() { return 'data:image/png;base64,AA=='; },
    captureStream() { return { getTracks: () => [{ stop() { stoppedTracks++; } }] }; }
  };
  const context = {
    console, Blob, location: { pathname: '/project/run-one/p5_runner.html' },
    width: 320, height: 180, drawingContext: drawing,
    Android: {
      getSketchCode: () => code,
      getProjectConfig: () => JSON.stringify(config),
      getP5Version: () => '1.11.5',
      isP5SoundEnabled: () => false,
      onStatusChanged: (owner, status) => statuses.push(status),
      onError: (owner, error) => errors.push(error),
      onRuntimeError: (owner, error) => errors.push(error),
      onRecordingStatusChanged: (owner, status) => statuses.push(status),
      onCaptureError: (owner, error) => errors.push(error)
    },
    requestAnimationFrame(callback) { frames.set(++frameId, callback); return frameId; },
    cancelAnimationFrame(id) { frames.delete(id); },
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 2, clearInterval() {},
    addEventListener(name, callback) {
      const previous = events[name];
      events[name] = previous ? (...args) => { previous(...args); callback(...args); } : callback;
    },
    dispatchEvent(event) {
      events[event.type]?.(event);
      return true;
    },
    isLooping: () => true,
    resizeCanvas(w, h) { resizes++; context.width = w; context.height = h; canvas.width = w; canvas.height = h; },
    p5: function P5() {},
    document: {
      documentElement: { clientWidth: 320, clientHeight: 180 },
      write() {},
      getElementById: () => ({ remove() { removed = true; } }),
      querySelector: () => canvas,
      createElement: tag => {
        const attrs = {};
        return {
          tagName: String(tag || '').toUpperCase(),
          setAttribute(k, v) { attrs[k] = v; },
          getAttribute(k) { return attrs[k]; },
          getContext: () => drawing
        };
      },
      head: { appendChild(script) {
        try { vm.runInContext(script.textContent, context); }
        catch (error) { context.onerror(error.message, 'sketch.js', 1); }
      } }
    }
  };
  Object.assign(context, extras);
  context.p5.prototype.registerMethod = (name, callback) => { hooks[name] = callback; };
  context.window = context;
  vm.createContext(context);
  scripts.forEach(source => vm.runInContext(source, context));
  return {
    context, events, canvas, statuses, errors,
    hooks,
    setup(instance = { _isGlobal: true }) { hooks.afterSetup.call(instance); },
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

const noop = () => {};
function sampleContext(extra = {}) {
  const context = {
    TWO_PI: Math.PI * 2, POINTS: 0, CENTER: 3, LEFT: 0, RIGHT: 2, VIDEO: 'video', WEBGL: 'webgl', WEBGPU: 'webgpu', width: 600, height: 600, frameCount: 1,
    mouseIsPressed: false, mouseX: 300, mouseY: 300, deltaTime: 16.67,
    millis: () => 1000, addEventListener: noop, document: { addEventListener: noop },
    redraw: noop,
    min: Math.min, lerp: (a, b, t) => a + (b - a) * t,
    sin: Math.sin, cos: Math.cos, sqrt: Math.sqrt, exp: Math.exp, atan2: Math.atan2, pow: Math.pow,
    round: Math.round, floor: Math.floor, sq: value => value * value,
    HSB: 'hsb', ROUND: 'round', noise: () => 0.5,
    BOLD: 'bold', NORMAL: 'normal',
    constrain: (v, low, high) => Math.min(high, Math.max(low, v)),
    random: (a, b) => (b === undefined ? Math.random() * (a || 1) : a + Math.random() * (b - a)),
    hex: (val, digits = 2) => Math.floor(val).toString(16).padStart(digits, '0'),
    nf: (num, left, right) => Number(num).toFixed(right),
    map: (v, a, b, c, d) => c + ((v - a) / (b - a)) * (d - c),
    createVector: (x = 0, y = 0, z = 0) => ({
      x, y, z,
      set(nx, ny) { this.x = nx; this.y = ny; },
      add(v) { this.x += v.x; this.y += v.y; },
      mult(n) { this.x *= n; this.y *= n; }
    }),
    p5: {
      Vector: {
        sub: (v1, v2) => {
          let x = v1.x - v2.x, y = v1.y - v2.y;
          return {
            x, y,
            normalize() { const m = Math.hypot(x, y) || 1; x /= m; y /= m; return this; },
            mult(n) { x *= n; y *= n; return this; }
          };
        }
      }
    },
    createCapture: (type, cb) => {
      if (cb) cb();
      return { size: noop, hide: noop, loadPixels: noop, width: 50, height: 50, pixels: new Uint8Array(50 * 50 * 4), loadedmetadata: true };
    }
  };
  for (const name of ['createCanvas', 'background', 'translate', 'rotate', 'rotateX', 'rotateY', 'rotateZ', 'push', 'pop',
    'fill', 'noFill', 'stroke', 'noStroke', 'strokeWeight', 'ellipse', 'circle', 'rect', 'line', 'beginShape', 'endShape', 'vertex',
    'textAlign', 'text', 'textSize', 'textStyle', 'setShakeThreshold', 'ambientLight', 'directionalLight', 'torus', 'box', 'userStartAudio',
    'pixelDensity', 'colorMode', 'angleMode', 'strokeCap', 'noLoop']) context[name] = noop;
  context.PI = Math.PI;
  context.DEGREES = 'degrees';
  context.RADIANS = 'radians';
  return Object.assign(context, extra);
}
async function runSample(name, extra = {}) {
  const source = readFileSync(`${__dirname}/../www/samples/${name}.js`, 'utf8');
  const context = vm.createContext(sampleContext(extra));
  context.window = context;
  vm.runInContext(source, context);
  await vm.runInContext('(async () => { await setup(); draw(); draw(); })()', context);
  return { context, source };
}

test('palette changes once and ripple collections remain bounded and expire', async () => {
  const shapes = await runSample('Shapes');
  vm.runInContext('mousePressed()', shapes.context);
  assert.equal(vm.runInContext('paletteIndex', shapes.context), 1);
  let time = 1000;
  const { context } = await runSample('Touch', { millis: () => time });
  vm.runInContext('for(let i=0;i<100;i++) addRipple(-100,700)', context);
  assert.equal(vm.runInContext('ripples.length', context), 40);
  assert.equal(vm.runInContext('ripples[0].x', context), 0);
  assert.equal(vm.runInContext('ripples[0].y', context), 600);
  context.deltaTime = 50;
  vm.runInContext('for(let i=0;i<45;i++) draw()', context);
  assert.equal(vm.runInContext('ripples.length', context), 0);
});

test('Halo keeps its orbital identity and supports pausing its time parameter', async () => {
  const { context } = await runSample('Halo', { rinParams: { speed: 0 } });
  assert.equal(vm.runInContext('time', context), 0);
  context.rinParams.speed = 0.6;
  vm.runInContext('draw()', context);
  assert.ok(vm.runInContext('time', context) > 0);
  assert.equal(vm.runInContext('RING_COUNT', context), 36);
});

test('bundled Halo.js and Gravity.js draw frames', async () => {
  for (const name of ['Halo', 'Gravity']) {
    const { context } = await runSample(name);
    assert.ok(vm.runInContext(name === 'Halo' ? 'time' : 't', context) > 0, name);
  }
});

test('bundled wave Parameter.js draws with rinParams', async () => {
  const { context, source } = await runSample('wave Parameter', { rinParams: { speed: 1, lineWidth: 4, ink: '#BA90E2' } });
  for (const type of ['number speed', 'number lineWidth', 'color ink']) assert.ok(source.includes('// @rin ' + type), type);
  assert.ok(vm.runInContext('phase', context) > 0);
});

test('bundled WebGPU.js uses WEBGPU when available and WEBGL otherwise', async () => {
  for (const [gpu, expected] of [[{requestAdapter: async () => ({})}, 'webgpu'], [{requestAdapter: async () => null}, 'webgl'], [undefined, 'webgl']]) {
    let renderer = null;
    const { context, source } = await runSample('WebGPU', {
      navigator: { gpu }, createCanvas(w, h, kind) { renderer = kind; },
      rinParams: { speed: 1.5, size: 1.2, accent: '#A8C7FA' }
    });
    for (const type of ['number speed', 'number size', 'color accent']) assert.ok(source.includes('// @rin ' + type), type);
    assert.equal(renderer, expected);
    assert.ok(vm.runInContext('t', context) > 0);
  }
});

function audioFixture() {
  const voices = [], tracks = [];
  class AudioContext {
    constructor() { this.state = 'suspended'; this.currentTime = 0; this.destination = {}; }
    async resume() { this.state = 'running'; }
    async suspend() { this.state = 'suspended'; }
    async close() { this.state = 'closed'; }
    createOscillator() {
      const voice = { frequency: {}, connect: noop, disconnect: noop, start: noop, stops: [],
        stop(time) { this.stops.push(time); } }; voices.push(voice); return voice;
    }
    createGain() { return { gain: { setValueAtTime: noop, linearRampToValueAtTime: noop, exponentialRampToValueAtTime: noop }, connect: noop, disconnect: noop }; }
    createAnalyser() { return { fftSize: 256, disconnect: noop, getByteTimeDomainData(data) { data.fill(152); } }; }
    createMediaStreamSource() { return { connect: noop, disconnect: noop }; }
  }
  const stream = { getTracks: () => tracks };
  tracks.push({ stops: 0, stop() { this.stops++; } });
  return { AudioContext, stream, voices, tracks };
}

test('sound starts only after input and short notes stop after release', async () => {
  const f = audioFixture();
  const { context } = await runSample('Sound', { AudioContext: f.AudioContext });
  assert.equal(f.voices.length, 0);
  await vm.runInContext('soundOn()', context);
  assert.equal(f.voices.length, 1);
  assert.ok(f.voices[0].stops[0] <= 0.25);
  assert.equal(vm.runInContext('active', context), true);
  vm.runInContext('soundOff()', context);
  assert.equal(vm.runInContext('active', context), false);
  assert.equal(f.voices[0].stops.length, 2);
});

test('microphone reads volume without speaker output and releases input', async () => {
  const f = audioFixture();
  const { context } = await runSample('Microphone', { AudioContext: f.AudioContext,
    navigator: { mediaDevices: { getUserMedia: async () => f.stream } } });
  await vm.runInContext('startMicrophone()', context);
  vm.runInContext('draw()', context);
  assert.ok(vm.runInContext('level', context) > 0);
  assert.equal(vm.runInContext('started', context), true);
  vm.runInContext('stopMicrophone()', context);
  assert.equal(f.tracks[0].stops, 1);
  assert.equal(vm.runInContext('started', context), false);
});

test('microphone rejects late permission results and permits retry after denial', async () => {
  const f = audioFixture(); let resolve;
  const { context } = await runSample('Microphone', { AudioContext: f.AudioContext,
    navigator: { mediaDevices: { getUserMedia: () => new Promise(r => { resolve = r; }) } } });
  const pending = vm.runInContext('startMicrophone()', context);
  for (let i = 0; i < 10 && !resolve; i++) await new Promise(r => setImmediate(r));
  assert.equal(typeof resolve, 'function');
  vm.runInContext('stopMicrophone()', context); resolve(f.stream); await pending;
  assert.equal(f.tracks[0].stops, 1);
  assert.equal(vm.runInContext('started', context), false);
  context.navigator.mediaDevices.getUserMedia = async () => { throw new Error('denied'); };
  await vm.runInContext('startMicrophone()', context);
  assert.equal(vm.runInContext('pending', context), false);
  assert.match(vm.runInContext('status', context), /RETRY/);
});

test('camera preserves crop aspect ratio and stops its tracks', async () => {
  const f = audioFixture(); let crop;
  const video = { play: async () => {}, pause: noop, readyState: 4, videoWidth: 720, videoHeight: 1280 };
  const buffer = { getContext: () => ({ drawImage(...args) { crop = args; }, getImageData: () => ({ data: new Uint8Array(32 * 24 * 4) }) }) };
  const { context } = await runSample('Camera', { navigator: { mediaDevices: { getUserMedia: async () => f.stream } },
    document: { addEventListener: noop, createElement: tag => tag === 'video' ? video : buffer } });
  await vm.runInContext('startCamera()', context); vm.runInContext('draw()', context);
  assert.equal(crop[3] / crop[4], 32 / 24);
  assert.equal(crop[2], 370);
  vm.runInContext('stopCamera()', context);
  assert.equal(f.tracks[0].stops, 1); assert.equal(video.srcObject, null);
});

test('sensor calibrates initial tilt, reacts to changes and resets after shaking', async () => {
  const { context, source } = await runSample('Sensor', { rotationX: 35, rotationY: 0,
    rinParams: { sensitivity: 1, bounce: 0.8, ballColor: '#64B5F6' } });
  for (const type of ['number sensitivity', 'number bounce', 'color ballColor']) assert.ok(source.includes('// @rin ' + type));
  assert.equal(vm.runInContext('vel.y', context), 0);
  context.rotationX = 55; context.rotationY = 15;
  vm.runInContext('draw()', context);
  assert.ok(vm.runInContext('vel.x', context) > 0);
  assert.ok(vm.runInContext('vel.y', context) > 0);
  vm.runInContext('deviceShaken()', context);
  assert.equal(vm.runInContext('pos.x', context), 300);
  assert.equal(vm.runInContext('pos.y', context), 300);
  context.rotationX = NaN; context.rotationY = Infinity;
  vm.runInContext('calibrate(); draw()', context);
  assert.ok(vm.runInContext('Number.isFinite(pos.x) && Number.isFinite(pos.y)', context));
});

test('sensor update bridge dispatches orientation and motion events', () => {
  const r = runner();
  let receivedOrientation = null;
  let receivedMotion = null;
  r.context.addEventListener('deviceorientation', e => { receivedOrientation = e; });
  r.context.addEventListener('devicemotion', e => { receivedMotion = e; });
  r.context.__editRinUpdateSensors(120.5, 25.3, -15.2, 0.5, -0.2, 9.8, 0.4, -0.1, 9.9);
  assert.ok(receivedOrientation != null, 'deviceorientation event should be dispatched');
  assert.equal(receivedOrientation.alpha, 120.5);
  assert.equal(receivedOrientation.beta, 25.3);
  assert.equal(receivedOrientation.gamma, -15.2);
  assert.ok(receivedMotion != null, 'devicemotion event should be dispatched');
  assert.equal(receivedMotion.acceleration.x, 0.5);
  assert.equal(receivedMotion.acceleration.y, -0.2);
  assert.equal(receivedMotion.acceleration.z, 9.8);
  assert.equal(receivedMotion.accelerationIncludingGravity.x, 0.4);
});

test('p5 sensor listener registration does not enable the native bridge', () => {
  const r = runner();
  const calls = [];
  r.context.Android.setSensorsEnabled = (...args) => calls.push(args);
  r.context.addEventListener('deviceorientation', () => {});
  r.context.addEventListener('devicemotion', () => {});
  assert.deepEqual(calls, []);
});

test('empty and synthetic browser events do not suppress native sensor fallback', () => {
  const r = runner();
  let received;
  r.context.addEventListener('deviceorientation', event => { received = event; });
  for (const event of [
    { type: 'deviceorientation', isTrusted: true, alpha: null, beta: null, gamma: null },
    { type: 'deviceorientation', alpha: 1, beta: 2, gamma: 3 }
  ]) {
    r.context.dispatchEvent(event);
    r.context.__editRinUpdateSensors(120, 25, -15, 0, 0, 0, 0, 0, 0);
    assert.equal(received.alpha, 120);
  }
});

test('fresh valid browser sensor streams suppress duplicates and expire independently', () => {
  let time = 1000;
  const r = runner('', {}, { performance: { now: () => time } });
  const orientations = [], motions = [];
  r.context.addEventListener('deviceorientation', e => orientations.push(e));
  r.context.addEventListener('devicemotion', e => motions.push(e));
  r.context.dispatchEvent({ type: 'deviceorientation', isTrusted: true, alpha: 1, beta: 2, gamma: 3 });
  r.context.dispatchEvent({ type: 'devicemotion', isTrusted: true, acceleration: { x: null, y: null, z: null } });
  orientations.length = motions.length = 0;
  r.context.__editRinUpdateSensors(120, 25, -15, 0.5, -0.2, 9.8, 0, 0, 9.8);
  assert.equal(orientations.length, 0);
  assert.equal(motions.length, 1);
  time += 501;
  r.context.__editRinUpdateSensors(120, 25, -15, 0.5, -0.2, 9.8, 0, 0, 9.8);
  assert.equal(orientations.length, 1);
  assert.equal(motions.length, 2);
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
  r.context.Android.onRuntimeError = (owner, message, line) => locations.push([message, line]);
  r.context.onerror('helper failed', 'sketch.js', 3);
  r.events.unhandledrejection({ reason: { message: 'async failed', stack: 'Error: async failed\n    at task (sketch.js:12:7)' } });
  r.context.onerror('library failed', 'p5-v2.min.js', 200, 1,
    { stack: 'Error\n    at library (p5-v2.min.js:200:1)\n    at draw (sketch.js:17:2)' });
  r.context.onerror('external', 'https://example.com/sketch.js', 99);
  assert.deepEqual(locations, [['helper failed', 3], ['async failed', 12], ['library failed', 17]]);
  assert.equal(r.errors.at(-1), 'external');
});

test('bundled matter.js and rinShaders are available in runner environment', () => {
  const fs = require('fs');
  assert.ok(fs.existsSync('www/matter-0.20.0.min.js'), 'matter-0.20.0.min.js should exist in www/');
  const matterContent = fs.readFileSync('www/matter-0.20.0.min.js', 'utf8');
  assert.ok(matterContent.includes('matter-js'), 'matter-js header should be present');

  assert.ok(bootstrapSource.includes("libraries['matter-js'] === '0.20.0'"), 'runner should support matter-js library loading');
  assert.ok(hostSource.includes('window.rinShaders ='), 'runner should expose window.rinShaders');
});


function captureRunner({ looping = true, density = 2, asyncDraw = false } = {}) {
  const r = runner('function setup() {} function draw() {}');
  r.setup();
  let currentDensity = density, running = looping;
  const densities = [], exported = [];
  r.canvas.width = r.context.width * density;
  r.canvas.height = r.context.height * density;
  r.context.pixelDensity = value => {
    if (value === undefined) return currentDensity;
    currentDensity = value;
    densities.push(value);
    r.canvas.width = r.context.width * value;
    r.canvas.height = r.context.height * value;
  };
  r.context.isLooping = () => running;
  r.context.noLoop = () => { running = false; };
  r.context.loop = () => { running = true; };
  const draws = [];
  r.context.redraw = async () => {
    if (asyncDraw) await new Promise(resolve => setImmediate(resolve));
    draws.push(currentDensity);
  };
  r.canvas.toDataURL = () => `data:image/png;${r.canvas.width}x${r.canvas.height};base64,AA==`;
  r.context.Android.onScreenshotExportReady = (owner, data, width, height) => exported.push({ data, width, height });
  return Object.assign(r, { densities, draws, exported, density: () => currentDensity, looping: () => running });
}

test('2x PNG really redraws at twice the original buffer size and restores a running sketch', async () => {
  const r = captureRunner();
  await r.context.__editKiroCaptureScreenshot(2);
  assert.deepEqual(r.densities, [4, 2]);
  assert.deepEqual(r.draws, [4, 2]);
  assert.deepEqual(r.exported, [{ data: 'data:image/png;1280x720;base64,AA==', width: 1280, height: 720 }]);
  assert.equal(r.canvas.width, 640);
  assert.equal(r.canvas.height, 360);
  assert.equal(r.looping(), true);
  assert.equal(r.errors.length, 0);
});

test('4x PNG waits for async redraw and leaves a paused sketch paused', async () => {
  const r = captureRunner({ looping: false, asyncDraw: true });
  await r.context.__editKiroCaptureScreenshot(4);
  assert.deepEqual(r.draws, [8, 2]);
  assert.equal(r.exported[0].width, 2560);
  assert.equal(r.exported[0].height, 1440);
  assert.equal(r.looping(), false);
  assert.equal(r.density(), 2);
});

test('normal PNG capture keeps pixels without redraw or density changes', async () => {
  const r = captureRunner({ looping: false });
  await r.context.__editKiroCaptureScreenshot();
  assert.deepEqual(r.draws, []);
  assert.deepEqual(r.densities, []);
  assert.equal(r.exported[0].width, 640);
  assert.equal(r.looping(), false);
});

test('PNG encoding and async draw failures restore density and playback without publishing', async () => {
  for (const failure of ['encoding', 'draw']) {
    const r = captureRunner();
    if (failure === 'encoding') r.canvas.toDataURL = () => { throw new Error('PNG failed'); };
    else r.context.redraw = async () => { if (r.density() > 2) throw new Error('draw failed'); };
    await r.context.__editKiroCaptureScreenshot(2);
    assert.equal(r.density(), 2);
    assert.equal(r.looping(), true);
    assert.equal(r.exported.length, 0);
    assert.equal(r.errors.length, 1);
  }
});

test('invalid scale and oversized PNGs fail before allocating or changing playback', async () => {
  const r = captureRunner();
  await r.context.__editKiroCaptureScreenshot(3);
  r.canvas.width = 5000; r.canvas.height = 5000;
  await r.context.__editKiroCaptureScreenshot(2);
  assert.equal(r.errors.length, 2);
  assert.deepEqual(r.densities, []);
  assert.deepEqual(r.draws, []);
  assert.equal(r.looping(), true);
  assert.equal(r.exported.length, 0);
});

test('high-res capture rejects sketches that change canvas dimensions during redraw', async () => {
  const r = captureRunner();
  r.context.redraw = async () => { if (r.density() > 2) r.canvas.width = 1; };
  await r.context.__editKiroCaptureScreenshot(2);
  assert.equal(r.exported.length, 0);
  assert.equal(r.errors.length, 1);
  assert.equal(r.canvas.width, 640);
  assert.equal(r.density(), 2);
});

test('concurrent PNG requests do not resize or publish twice', async () => {
  const r = captureRunner({ asyncDraw: true });
  await Promise.all([r.context.__editKiroCaptureScreenshot(2), r.context.__editKiroCaptureScreenshot(4)]);
  assert.equal(r.exported.length, 1);
  assert.equal(r.exported[0].width, 1280);
  assert.deepEqual(r.densities, [4, 2]);
});

test('high-res PNG does not disturb an active recording session', async () => {
  const r = captureRunner();
  r.context.MediaRecorder = class {
    static isTypeSupported() { return true; }
    constructor() { this.mimeType = 'video/mp4'; this.state = 'inactive'; }
    start() { this.state = 'recording'; }
    stop() { this.state = 'inactive'; }
  };
  r.context.__editKiroStartRecording('mp4');
  await r.context.__editKiroCaptureScreenshot(2);
  assert.equal(r.exported.length, 0);
  assert.deepEqual(r.densities, []);
  assert.equal(r.stats().stoppedTracks, 0);
  assert.equal(r.errors.length, 1);
});

function chunkedScreenshotRunner() {
  const r = runner();
  r.writes = []; r.finished = []; r.aborts = [];
  r.context.FileReader = class {
    readAsDataURL(blob) {
      blob.arrayBuffer().then(bytes => {
        this.result = 'data:image/png;base64,' + Buffer.from(bytes).toString('base64');
        this.onload();
      });
    }
  };
  r.context.Android.beginScreenshotTransfer = () => true;
  r.context.Android.appendScreenshotChunk = (owner, token, chunk) => { r.writes.push(Buffer.from(chunk, 'base64')); return true; };
  r.context.Android.finishScreenshotTransfer = (owner, token, width, height) => r.finished.push({width, height});
  r.context.Android.abortScreenshotTransfer = (owner, token) => r.aborts.push(token);
  r.canvas.toDataURL = () => { throw new Error('whole-image Base64 must not be used'); };
  return r;
}

test('PNG transfers bounded chunks without whole-image Base64 and completes after the last chunk', async () => {
  const r = chunkedScreenshotRunner();
  const png = Buffer.alloc(600_000, 37);
  r.canvas.toBlob = done => queueMicrotask(() => done(new Blob([png], {type:'image/png'})));
  await r.context.__editKiroCaptureScreenshot();
  assert.deepEqual(Buffer.concat(r.writes), png);
  assert.ok(r.writes.every(chunk => chunk.length <= 192 * 1024));
  assert.deepEqual(r.finished, [{width:320, height:180}]);
  assert.equal(r.aborts.length, 0);
});

test('failed PNG transfer aborts the partial file and permits a new capture', async () => {
  const r = chunkedScreenshotRunner();
  r.canvas.toBlob = done => done(new Blob(['png']));
  r.context.Android.appendScreenshotChunk = () => false;
  await r.context.__editKiroCaptureScreenshot();
  assert.equal(r.aborts.length, 1);
  assert.equal(r.finished.length, 0);
  assert.ok(r.errors.length);
  r.context.Android.appendScreenshotChunk = () => true;
  await r.context.__editKiroCaptureScreenshot();
  assert.equal(r.finished.length, 1);
});

test('null PNG encoding does not begin a native transfer', async () => {
  const r = chunkedScreenshotRunner();
  let begins = 0;
  r.context.Android.beginScreenshotTransfer = () => { begins++; return true; };
  r.canvas.toBlob = done => done(null);
  await r.context.__editKiroCaptureScreenshot();
  assert.equal(begins, 0);
  assert.equal(r.finished.length, 0);
  assert.ok(r.errors.length);
});

test('preview readiness is reported after setup and a presentation frame', () => {
  const r = runner();
  let ready = 0;
  r.context.Android.onPreviewReady = () => ready++;
  r.events.load();
  assert.equal(ready, 0);
  r.setup();
  assert.equal(ready, 0);
  r.flush();
  assert.equal(ready, 1);
});

test('PNG completion and errors carry the originating run identity', async () => {
  const r = chunkedScreenshotRunner();
  let token;
  const failures = [];
  r.canvas.toBlob = done => done(new Blob(['png']));
  r.context.location.pathname = '/project/newer-run/p5_runner.html';
  r.context.Android.beginScreenshotTransfer = (owner, value) => { token = value; return true; };
  r.context.Android.appendScreenshotChunk = () => false;
  r.context.Android.onScreenshotError = (owner, message) => failures.push({owner, message});
  await r.context.__editKiroCaptureScreenshot();
  // A later URL change cannot reassign this page's in-flight export.
  assert.ok(token.startsWith('run-one:png-'));
  assert.equal(failures[0].owner, 'run-one');
  assert.equal(r.finished.length, 0);
});

test('runtime errors, pause status and ready callbacks retain the page run identity', () => {
  const r = runner();
  const calls = [];
  r.context.Android.onStatusChanged = (owner, status) => calls.push([owner, status]);
  r.context.Android.onPreviewReady = owner => calls.push([owner, 'ready']);
  r.context.Android.onRuntimeError = (owner, message, line) => calls.push([owner, message, line]);
  r.context.location.pathname = '/project/newer-run/p5_runner.html';
  r.context.__editRinRunToken = 'newer-run';
  assert.equal(r.context.__editRinRunToken, 'run-one');
  r.setup();
  r.flush();
  r.context.pauseSketch();
  r.events.unhandledrejection({ reason: { message: 'old error', stack: 'Error\n at draw (sketch.js?run=run-one:8:2)' } });
  r.events.unhandledrejection({ reason: { message: 'absolute source', stack: 'Error\n at draw (https://appassets.androidplatform.net/project/run-one/sketch.js?run=run-one:9:2)' } });
  assert.deepEqual(calls, [['run-one', '実行中'], ['run-one', 'ready'], ['run-one', '一時停止中'], ['run-one', 'old error', 8], ['run-one', 'absolute source', 9]]);
});

test('separate JS files remain valid at ASI-sensitive IIFE and array boundaries', () => {
  const r = runner('const n = 1\n;\n(function(){ window.first = n; })() // trailing comment\n;\n[2].forEach(value => { window.second = value; })\n;\nfunction setup() {}');
  assert.deepEqual(r.errors, []);
  assert.equal(r.context.first, 1);
  assert.equal(r.context.second, 2);
});

function instanceRunner(config = {}) {
  const r = runner('', config);
  const drawn = [];
  const instances = [2, 4].map((density, index) => {
    let running = index === 0, currentDensity = density;
    const densities = [], calls = { loop: 0, noLoop: 0 };
    const canvas = { width: 100 * density, height: 50 * density, style: {}, isConnected: true,
      toDataURL: () => 'data:image/png;base64,AA==' };
    const instance = { width: 100, height: 50, canvas, densities, calls,
      isLooping: () => running,
      noLoop() { running = false; calls.noLoop++; }, loop() { running = true; calls.loop++; },
      draw() {}, redraw() {},
      pixelDensity(value) {
        if (value === undefined) return currentDensity;
        currentDensity = value; densities.push(value); canvas.width = 100 * value; canvas.height = 50 * value;
      } };
    r.hooks.init.call(instance);
    return instance;
  });
  r.context.document.querySelectorAll = () => instances.map(instance => instance.canvas);
  r.context.document.createElement = () => ({ width: 0, height: 0,
    getContext: () => ({ clearRect() { drawn.length = 0; }, drawImage(canvas) { drawn.push(canvas); } }),
    toDataURL() { return `data:image/png;${this.width}x${this.height};base64,AA==`; } });
  return Object.assign(r, { instances, drawn });
}

test('instance readiness waits for every pending setup and fits all canvases without resizing buffers', () => {
  const r = instanceRunner();
  let ready = 0; r.context.Android.onPreviewReady = () => ready++;
  r.setup(r.instances[0]); r.flush();
  assert.deepEqual(r.statuses, []);
  r.setup(r.instances[1]); r.flush();
  assert.equal(ready, 1);
  assert.deepEqual(r.statuses, ['実行中']);
  assert.equal(r.instances[0].canvas.style.width, '160px');
  assert.equal(r.instances[1].canvas.style.left, '160px');
  assert.equal(r.instances[0].canvas.width, 200);
  assert.equal(r.instances[1].canvas.width, 400);
});

test('p5 1 global hooks resolve window to the instance previously tracked by init', () => {
  const r = runner();
  const instance = { _isGlobal: true, canvas: r.canvas };
  r.context.p5.instance = instance;
  r.hooks.init.call(instance);
  r.context.__testAfterSetup = r.hooks.afterSetup;
  vm.runInContext('__testAfterSetup.call(window)', r.context);
  r.flush();
  assert.deepEqual(r.statuses, ['実行中']);
});

test('pause and resume preserve each instance noLoop state and gate artwork RAFs', () => {
  const r = instanceRunner();
  r.instances.forEach(instance => r.setup(instance)); r.flush();
  let frames = 0;
  r.context.requestAnimationFrame(() => frames++);
  r.context.pauseSketch(); r.flush();
  assert.equal(frames, 0);
  assert.equal(r.instances.every(instance => !instance.isLooping()), true);
  r.context.resumeSketch(); r.flush();
  assert.equal(frames, 1);
  assert.equal(r.instances[0].isLooping(), true);
  assert.equal(r.instances[1].isLooping(), false);
});

test('multi-instance 2x PNG redraws every density and restores each playback state', async () => {
  const r = instanceRunner();
  r.instances.forEach(instance => r.setup(instance)); r.flush();
  const exports = [];
  r.context.Android.onScreenshotExportReady = (owner, data, width, height) => exports.push({ owner, width, height });
  await r.context.__editKiroCaptureScreenshot(2);
  assert.deepEqual(r.errors, []);
  assert.deepEqual(exports, [{ owner: 'run-one', width: 1600, height: 400 }]);
  assert.deepEqual(r.instances.map(instance => instance.densities), [[4, 2], [8, 4]]);
  assert.deepEqual(r.instances.map(instance => instance.isLooping()), [true, false]);
  assert.equal(new Set(r.drawn).size, 2);
});

test('global callbacks use window.draw while redraw and density use the concrete p5 instance', async () => {
  const r = instanceRunner();
  r.instances[0]._isGlobal = true;
  delete r.instances[0].draw;
  r.context.draw = () => {};
  r.instances.forEach(instance => r.setup(instance)); r.flush();
  let completed = 0;
  r.context.Android.onScreenshotExportReady = () => completed++;
  await r.context.__editKiroCaptureScreenshot(4);
  assert.equal(completed, 1);
  assert.deepEqual(r.errors, []);
  assert.deepEqual(r.instances.map(instance => instance.densities), [[8, 2], [16, 4]]);
});

test('HTML readiness preserves authored canvas CSS and does not require global setup', () => {
  const r = runner('', { documentMode: true });
  r.canvas.style = { position: 'relative', width: '77px', left: '9px' };
  r.events.load(); r.flush(); r.flush();
  assert.deepEqual(r.errors, []);
  assert.deepEqual(r.statuses, ['実行中']);
  assert.deepEqual(r.canvas.style, { position: 'relative', width: '77px', left: '9px' });
});

test('module readiness is held until the entry signals completion', () => {
  const r = runner('', { moduleMode: true });
  r.events.load(); r.flush();
  assert.deepEqual(r.statuses, []);
  r.context.__editRinModuleReady(); r.flush();
  assert.deepEqual(r.errors, []);
  assert.deepEqual(r.statuses, ['実行中']);
});

function downloadsRunner(extras = {}) {
  const r = runner('', {}, extras), writes = [], finished = [], begins = [], aborts = [];
  r.context.FileReader = class {
    readAsDataURL(blob) {
      blob.arrayBuffer().then(bytes => { this.result = 'data:;base64,' + Buffer.from(bytes).toString('base64'); this.onload(); });
    }
  };
  r.context.Android.beginFileDownload = (owner, id, name, mime) => { begins.push({owner,id,name,mime}); return true; };
  r.context.Android.appendFileDownloadChunk = (owner, id, data) => { writes.push({owner,id,bytes:Buffer.from(data,'base64')}); return true; };
  r.context.Android.finishFileDownload = (owner,id) => finished.push({owner,id});
  r.context.Android.abortFileDownload = (owner,id) => aborts.push({owner,id});
  return Object.assign(r, { writes, finished, begins, aborts });
}

test('generic downloads serialize bounded chunks, preserve filenames/MIME and permit empty files', async () => {
  const r = downloadsRunner();
  const bytes = Buffer.alloc(600000, 51);
  await Promise.all([r.context.__editRinDownload(new Blob([bytes]), 'data', 'csv'),
    r.context.__editRinDownload(new Blob([], {type:'application/json'}), 'empty.json')]);
  assert.equal(r.finished.length, 2);
  assert.deepEqual(r.begins.map(item => [item.name,item.mime]), [['data.csv','text/csv'],['empty.json','application/json']]);
  assert.deepEqual(Buffer.concat(r.writes.map(item => item.bytes)), bytes);
  assert.equal(r.writes.every(item => item.bytes.length <= 192*1024 && item.owner === 'run-one' && item.id === r.begins[0].id), true);
});

test('revoking a Blob URL cannot invalidate a queued anchor download', async () => {
  let clicked = 0, next = 0;
  class Anchor {
    hasAttribute(name) { return name === 'download'; }
    click() { clicked++; }
  }
  const r = downloadsRunner({ URL: { createObjectURL: () => `blob:${++next}`, revokeObjectURL() {} }, HTMLAnchorElement: Anchor });
  const url = r.context.URL.createObjectURL(new Blob(['retained']));
  const anchor = new Anchor(); anchor.href = url; anchor.download = 'retained.txt'; anchor.click();
  r.context.URL.revokeObjectURL(url);
  await r.context.__editRinDownload(new Blob(['second']), 'second.txt');
  assert.equal(clicked, 0);
  assert.deepEqual(r.begins.map(item => item.name), ['retained.txt','second.txt']);
  assert.equal(Buffer.concat(r.writes.filter(item => item.id === r.begins[0].id).map(item => item.bytes)).toString(), 'retained');
});

test('a rejected generic chunk aborts its transfer and allows the next queued file', async () => {
  const r = downloadsRunner();
  r.context.Android.appendFileDownloadChunk = () => false;
  await r.context.__editRinDownload(new Blob(['first']), 'first.txt');
  assert.equal(r.aborts.length, 1); assert.equal(r.finished.length, 0); assert.equal(r.errors.length, 1);
  r.context.Android.appendFileDownloadChunk = () => true;
  await r.context.__editRinDownload(new Blob(['second']), 'second.txt');
  assert.equal(r.finished.length, 1);
});

test('generic URL downloads report CORS errors without publishing partial native files', async () => {
  const r = downloadsRunner({ fetch: async () => { throw new Error('Failed to fetch'); } });
  await r.context.__editRinDownload('https://example.com/private.csv', 'private.csv');
  assert.equal(r.begins.length, 0); assert.match(r.errors[0], /CORS/);
});

test('file downloads wait for native save completion, reject stale ACKs and recover after cancellation', async () => {
  const r = downloadsRunner();
  r.context.Android.supportsFileDownloadResult = () => true;
  const first = r.context.__editRinDownload(new Blob(['a']), 'first.txt');
  const second = r.context.__editRinDownload(new Blob(['b']), 'second.txt');
  for (let i = 0; i < 10 && !r.finished.length; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(r.begins.length, 1); assert.equal(r.finished.length, 1);
  r.context.__editRinFileDownloadResult('newer-run:download-other', true, '');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(r.begins.length, 1);
  r.context.__editRinFileDownloadResult(r.begins[0].id, false, 'save cancelled');
  await first;
  for (let i = 0; i < 10 && r.finished.length < 2; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(r.begins.length, 2);
  r.context.__editRinFileDownloadResult(r.begins[1].id, true, ''); await second;
  assert.equal(r.aborts.length, 1); assert.deepEqual(r.errors, ['save cancelled']);
});

test('a 90-frame batch is queued without the former 32-file drop', async () => {
  const r = downloadsRunner();
  await Promise.all(Array.from({length:90}, (_, i) => r.context.__editRinDownload(new Blob([String(i)]), `frame-${i}.png`)));
  assert.deepEqual(r.errors, []); assert.equal(r.finished.length, 90);
  assert.deepEqual(r.begins.map(item => item.name), Array.from({length:90}, (_, i) => `frame-${i}.png`));
});

test('queued payload memory has a 256MiB budget and reports overload before beginning a transfer', async () => {
  const r = downloadsRunner();
  class OversizedBlob extends Blob { get size() { return 256*1024*1024+1; } }
  await r.context.__editRinDownload(new OversizedBlob([]), 'large.bin');
  assert.equal(r.begins.length, 0); assert.match(r.errors[0], /256MiB/);
  await r.context.__editRinDownload(new Blob(['fine']), 'small.txt');
  assert.equal(r.finished.length, 1);
});

test('unloading aborts an active native save ACK and releases queued downloads', async () => {
  const r = downloadsRunner();
  r.context.Android.supportsFileDownloadResult = () => true;
  const first = r.context.__editRinDownload(new Blob(['a']), 'first.txt');
  const second = r.context.__editRinDownload(new Blob(['b']), 'second.txt');
  for (let i = 0; i < 10 && !r.finished.length; i++) await new Promise(resolve => setImmediate(resolve));
  r.events.beforeunload();
  await Promise.all([first, second]);
  assert.equal(r.begins.length, 1); assert.ok(r.aborts.length >= 1);
  assert.deepEqual(r.errors, []);
});

test('individual module runtime errors retain their actual project file and line', () => {
  const r = runner('', {moduleMode:true}), errors = [];
  r.context.Android.onRuntimeErrorFile = (owner,message,file,line) => errors.push([owner,message,file,line]);
  r.context.onerror('helper failed','https://appassets.androidplatform.net/project/run-one/lib/helper.mjs',4);
  r.events.unhandledrejection({reason:{message:'async failed',stack:'Error\n at task (https://appassets.androidplatform.net/project/run-one/lib/helper.mjs:12:3)'}});
  assert.deepEqual(errors,[['run-one','helper failed','lib/helper.mjs',4],['run-one','async failed','lib/helper.mjs',12]]);
});

test('WEBGPU fails clearly before invoking the renderer when no GPU API exists', () => {
  const r = runner(); let invoked = false;
  function LaterP5() {}
  LaterP5.prototype.registerMethod = () => {};
  LaterP5.prototype.createCanvas = () => { invoked = true; };
  r.context.p5 = LaterP5;
  assert.throws(() => LaterP5.prototype.createCanvas(10,10,'webgpu'), /WEBGPU/);
  assert.equal(invoked,false);
});

test('addon selection follows the actual custom p5 version rather than the work version', () => {
  const r = runner('', {p5Url:'https://example.com/custom-p5.js'}), writes = [];
  r.context.document.write = value => writes.push(value);
  r.context.__editKiroSoundEnabled = true;
  r.context.p5.VERSION = '1.11.5';r.context.__editRinLoadP5Addons();
  assert.equal(writes.some(value=>value.includes('p5.sound-v1.min.js')),true);
  assert.equal(writes.some(value=>value.includes('p5.webgpu.js')),false);
  writes.length=0;r.context.p5.VERSION = '2.3.4';r.context.__editRinLoadP5Addons();
  assert.equal(writes.some(value=>value.includes('p5.sound.min.js')),true);
  assert.equal(writes.some(value=>value.includes('p5.webgpu.js')),true);
  writes.length=0;r.context.p5.VERSION = '';r.context.__editRinLoadP5Addons();
  assert.equal(writes.length,0);assert.match(r.errors[0],/p5.VERSION/);
});

test('video elements receive transparent poster and offscreen canvas sets willReadFrequently', () => {
  const recorded = [];
  class FakeCanvas {
    constructor(id = '', isConnected = false) {
      this.id = id;
      this.isConnected = isConnected;
    }
    getContext(type, attrs) {
      recorded.push({ id: this.id, isConnected: this.isConnected, type, attrs });
      return {};
    }
  }
  const r = runner('', {}, {
    HTMLCanvasElement: FakeCanvas
  });
  // 1. Video element automatically receives dummy transparent poster
  const video = r.context.document.createElement('video');
  assert.match(video.getAttribute('poster') || '', /^data:image\/gif/);

  // 2. Offscreen canvas gets willReadFrequently: true
  const offscreen = new FakeCanvas('', false);
  offscreen.getContext('2d');
  assert.equal(recorded[0].attrs.willReadFrequently, true);

  // 3. Main presentation canvas does not get forced willReadFrequently
  const main = new FakeCanvas('defaultCanvas0', true);
  main.getContext('2d');
  assert.equal(recorded[1].attrs, undefined);
});


test('thumbnail renderer suppresses audio but leaves the interactive renderer unchanged', async () => {
  for (const thumbnailOnly of [true, false]) {
    let resumes = 0, plays = 0;
    class AudioContext { resume() { resumes++; return Promise.resolve(); } }
    class HTMLMediaElement { play() { plays++; return Promise.resolve(); } }
    runner('function setup() {}', { thumbnailOnly }, { AudioContext, HTMLMediaElement });
    await new AudioContext().resume();
    await new HTMLMediaElement().play();
    assert.equal(resumes, thumbnailOnly ? 0 : 1);
    assert.equal(plays, thumbnailOnly ? 0 : 1);
  }
});
