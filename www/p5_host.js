
(() => {
  // Keep the URL identity even if a newer run replaces the native snapshot.
  const runToken = window.location?.pathname?.match(/^\/project\/([^/]+)\//)?.[1] || '';
  Object.defineProperty(window, '__editRinRunToken', { value: runToken });
  let projectConfig = {};
  try { projectConfig = JSON.parse(window.Android?.getProjectConfig?.(runToken) || '{}'); } catch (_) {}
  window.__editRinProjectConfig = projectConfig;
  if (projectConfig.thumbnailOnly) {
    // Thumbnail rendering must never start sound, including sketches that resume audio in setup.
    for (const AudioContextType of new Set([window.AudioContext, window.webkitAudioContext])) {
      if (AudioContextType?.prototype) AudioContextType.prototype.resume = function () { return Promise.resolve(); };
    }
    if (window.HTMLMediaElement?.prototype) {
      window.HTMLMediaElement.prototype.play = function () { return Promise.resolve(); };
    }
  }

  window.__editKiroRuntimeHasError = false;
  const reportError = (message, line = 0, file = '') => {
    window.__editKiroRuntimeHasError = true;
    document.getElementById('boot')?.remove();
    try {
      const text = String(message);
      const lineNumber = Number(line) || 0;

      if (lineNumber > 0 && file && window.Android?.onRuntimeErrorFile &&
          (projectConfig.documentMode || projectConfig.moduleMode || projectConfig.classicScripts?.length)) {
        window.Android.onRuntimeErrorFile(runToken, text, file, lineNumber);
      } else if (lineNumber > 0 && window.Android?.onRuntimeError) {
        window.Android.onRuntimeError(runToken, text, lineNumber);
      } else {
        window.Android?.onError(runToken, text);
      }
    } catch (_) {}
  };
  window.__editKiroReportError = reportError;

  const stackLine = error => {
    const match = String(error?.stack || '').match(/(?:^|[\s(])(?:https?:\/\/[^\s()]*\/)?sketch\.js(?:\?run=[^:\s)]+)?:(\d+):\d+/m);
    return match ? Number(match[1]) : 0;
  };
  const projectFile = source => {
    const prefix = `/project/${runToken}/`;
    const text = String(source || '');
    const path = text.startsWith(prefix) ? text : text.match(/^https?:\/\/[^/]+(\/project\/[^?#]+)/)?.[1];
    if (!path?.startsWith(prefix)) return '';
    const file = decodeURIComponent(path.slice(prefix.length).split('?')[0]);
    if (file.startsWith('__edit-rin__/')) return '';
    return /^(?:p5(?:-v[12])?(?:\.webgpu)?(?:\.min)?\.js|p5_host\.js|p5_bootstrap\.js|p5_sketch\.js)$/.test(file) ? '' : file;
  };
  const sourceLocation = error => {
    for (const frame of String(error?.stack || '').split('\n')) {
      const match = frame.match(/(https?:\/\/[^\s()]+|\/project\/[^\s()]+):(\d+):\d+/);
      const file = match && projectFile(match[1]);
      if (file) return { file, line: Number(match[2]) };
    }
    return { file: '', line: stackLine(error) };
  };
  const observedErrors = new WeakSet();
  const handleError = (message, source, line, column, error) => {
    if (String(message || '').includes('android-webview-video-poster')) return true;
    if (error && typeof error === 'object') {
      if (observedErrors.has(error)) return true;
      observedErrors.add(error);
      Promise.resolve().then(() => observedErrors.delete(error));
    }
    const userLine =
      (String(source || '') === 'sketch.js' || String(source || '').endsWith('sketch.js?run=' + runToken))
        ? line
        : stackLine(error);

    const location = sourceLocation(error);
    const file = projectFile(source);
    reportError(message, file ? line : userLine || location.line, file || location.file);
    return true;
  };
  window.onerror = handleError;

  window.addEventListener('unhandledrejection', event => {
    const location = sourceLocation(event.reason);
    reportError(event.reason?.message || event.reason || 'Promise error', location.line, location.file);
  });
  window.addEventListener('error', event => {
    if (event.target?.tagName === 'SCRIPT') reportError(`Script load failed: ${event.target.src}`);
    else if (event.message) handleError(event.message, event.filename, event.lineno, event.colno, event.error);
  }, true);
})();

(() => {
  // 1. Prevent Android WebView CORS errors on <video> elements by supplying a transparent poster.
  // WebView tries to load an internal "android-webview-video-poster:" URL if poster is missing.
  const DUMMY_VIDEO_POSTER = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
  if (typeof document !== 'undefined' && typeof document.createElement === 'function') {
    const origCreateElement = document.createElement.bind(document);
    document.createElement = function (tagName, options) {
      const el = origCreateElement(tagName, options);
      if (typeof tagName === 'string' && tagName.toLowerCase() === 'video' && typeof el?.setAttribute === 'function') {
        el.setAttribute('poster', DUMMY_VIDEO_POSTER);
      }
      return el;
    };
  }

  // 2. Optimize offscreen 2D canvas readbacks (e.g. p5.MediaElement.loadPixels, image readbacks)
  // by ensuring willReadFrequently is true for offscreen helper canvases, eliminating GPU stalls and Chromium warnings.
  if (typeof HTMLCanvasElement !== 'undefined' && HTMLCanvasElement.prototype) {
    const origGetContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, attributes) {
      if (type === '2d') {
        const isMainDisplay = Boolean(
          this.id === 'defaultCanvas0' ||
          this.classList?.contains('p5Canvas') ||
          this.parentNode ||
          this.isConnected
        );
        if (!isMainDisplay) {
          if (attributes && typeof attributes === 'object') {
            if (attributes.willReadFrequently === undefined) {
              attributes = Object.assign({ willReadFrequently: true }, attributes);
            }
          } else {
            attributes = { willReadFrequently: true };
          }
        }
      }
      return origGetContext.call(this, type, attributes);
    };
  }
})();
  


(() => {
  let initial = {};
  try { initial = JSON.parse(window.Android?.getWorkParameters?.(window.__editRinRunToken) || '{}'); } catch (_) {}
  window.rinParams = Object.assign(Object.create(null), initial);
  window.__editRinSetParameter = (name, value) => {
    if (Object.prototype.hasOwnProperty.call(window.rinParams, name) &&
        (typeof value === 'number' && Number.isFinite(value) ||
         typeof value === 'boolean' ||
         typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value))) {
      window.rinParams[name] = value;
    }
  };
})();
  


(() => {
  let shaders = {};
  try { shaders = JSON.parse(window.Android?.getWorkShaders?.(window.__editRinRunToken) || '{}'); } catch (_) {}
  window.rinShaders = Object.assign(Object.create(null), shaders);
})();

(() => {
  // p5 registers its own listeners for every sketch. Do not treat registration
  // as evidence that a sketch uses sensors; native admission uses the run source.
  let browserOrientationAt = -Infinity;
  let browserMotionAt = -Infinity;
  const freshnessMs = 500;
  const now = () => typeof performance !== 'undefined' ? performance.now() : Date.now();
  const finite = value => typeof value === 'number' && Number.isFinite(value);

  window.addEventListener('deviceorientation', e => {
    if (e?.isTrusted && finite(e.alpha) && finite(e.beta) && finite(e.gamma)) {
      browserOrientationAt = now();
    }
  }, { passive: true, capture: true });
  window.addEventListener('devicemotion', e => {
    const a = e?.acceleration;
    if (e?.isTrusted && a && finite(a.x) && finite(a.y) && finite(a.z)) {
      browserMotionAt = now();
    }
  }, { passive: true, capture: true });

  window.__editRinUpdateSensors = (alpha, beta, gamma, ax, ay, az, gx, gy, gz) => {
    const time = now();
    if (time - browserOrientationAt > freshnessMs) {
      let event;
      try {
        if (typeof DeviceOrientationEvent === 'function') {
          event = new DeviceOrientationEvent('deviceorientation', {
            alpha, beta, gamma, absolute: true, bubbles: false, cancelable: false
          });
        }
      } catch (_) {}
      if (!event) {
        event = typeof Event === 'function' ? new Event('deviceorientation') : { type: 'deviceorientation' };
        Object.assign(event, { alpha, beta, gamma, absolute: true });
      }
      try { window.dispatchEvent(event); } catch (_) {}
    }
    if (time - browserMotionAt > freshnessMs) {
      let event;
      const acceleration = { x: ax, y: ay, z: az };
      const accelerationIncludingGravity = { x: gx, y: gy, z: gz };
      try {
        if (typeof DeviceMotionEvent === 'function') {
          event = new DeviceMotionEvent('devicemotion', {
            acceleration, accelerationIncludingGravity, interval: 16.6,
            bubbles: false, cancelable: false
          });
        }
      } catch (_) {}
      if (!event) {
        event = typeof Event === 'function' ? new Event('devicemotion') : { type: 'devicemotion' };
        Object.assign(event, { acceleration, accelerationIncludingGravity, interval: 16.6 });
      }
      try { window.dispatchEvent(event); } catch (_) {}
    }
  };
})();

(() => {
  const owner = window.__editRinRunToken;
  const blobs = new Map();
  let pending = Promise.resolve(), count = 0, unloaded = false, activeId = null;
  let retainedBytes = 0;
  const acknowledgements = new Map();
  const available = () => window.Android?.beginFileDownload && window.Android?.appendFileDownloadChunk && window.Android?.finishFileDownload;
  const fail = error => {
    if (unloaded) return;
    try { window.Android?.onCaptureError?.(owner, error?.message || String(error)); } catch (_) {}
  };
  const create = window.URL?.createObjectURL?.bind(window.URL);
  const revoke = window.URL?.revokeObjectURL?.bind(window.URL);
  if (create) window.URL.createObjectURL = blob => {
    const url = create(blob); blobs.set(url, blob); return url;
  };
  if (revoke) window.URL.revokeObjectURL = url => { blobs.delete(String(url)); revoke(url); };
  const mimeFor = name => ({ png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp',
    gif: 'image/gif', svg: 'image/svg+xml', json: 'application/json', csv: 'text/csv', txt: 'text/plain',
    html: 'text/html', wav: 'audio/wav', mp4: 'video/mp4', webm: 'video/webm' }[name.split('.').pop().toLowerCase()] || 'application/octet-stream');
  function payloadFor(data) {
    // Take ownership of the Blob reference now: a saver may revoke its URL in
    // the same click callback, before an earlier queued transfer completes.
    if (data instanceof Blob) return Promise.resolve(data);
    if (blobs.has(String(data))) return Promise.resolve(blobs.get(String(data)));
    if (typeof fetch !== 'function') return Promise.reject(new Error('URLファイルを取得できません'));
    return fetch(String(data)).then(response => {
      if (!response.ok) throw new Error(`ファイルを取得できませんでした (HTTP ${response.status})`);
      return response.blob();
    }).catch(error => { throw new Error(`ファイルを取得できません。URLとCORS設定を確認してください: ${error.message}`); });
  }
  function encode(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1]);
      reader.onerror = reader.onabort = () => reject(new Error('ファイルデータを読み出せませんでした'));
      reader.readAsDataURL(blob);
    });
  }
  window.__editRinDownload = (data, filename = 'untitled', extension) => {
    if (!available()) return null;
    let name = String(filename || 'untitled');
    if (typeof extension === 'string' && extension !== 'true' && extension && !name.endsWith('.' + extension)) name += '.' + extension;
    if (count >= 256) { fail(new Error('保存要求が多すぎます。保存が完了するまでお待ちください')); return Promise.resolve(); }
    count++;
    let reserved = 0;
    const payload = payloadFor(data).then(blob => {
      if (blob.size > 256 * 1024 * 1024 || retainedBytes + blob.size > 256 * 1024 * 1024) {
        throw new Error('保存待ちデータが256MiBを超えています。保存が完了するまでお待ちください');
      }
      retainedBytes += blob.size; reserved = blob.size; return blob;
    });
    // Attach a rejection handler immediately while this payload waits its turn.
    payload.catch(() => {});
    const task = pending.then(async () => {
      const blob = await payload;
      if (unloaded) return;
      const id = owner + ':download-' + Date.now() + '-' + Math.random().toString(36).slice(2);
      const mime = blob.type && blob.type !== 'application/octet-stream' ? blob.type : mimeFor(name);
      if (!window.Android.beginFileDownload(owner, id, name, mime)) throw new Error('ファイルの保存を開始できませんでした');
      activeId = id;
      try {
        for (let offset = 0; offset < blob.size; offset += 192 * 1024) {
          const data = await encode(blob.slice(offset, offset + 192 * 1024));
          if (unloaded || !window.Android.appendFileDownloadChunk(owner, id, data)) throw new Error('ファイルを保存できませんでした');
        }
        let acknowledgement;
        if (window.Android.supportsFileDownloadResult?.() === true) {
          acknowledgement = new Promise((resolve, reject) => acknowledgements.set(id, {resolve,reject}));
        }
        window.Android.finishFileDownload(owner, id);
        if (acknowledgement) await acknowledgement;
      } catch (error) { window.Android?.abortFileDownload?.(owner, id); throw error; }
      finally { acknowledgements.delete(id); activeId = null; }
    });
    pending = task.catch(fail).finally(() => { count--; retainedBytes -= reserved; });
    return pending;
  };
  window.__editRinFileDownloadResult = (id, success, message) => {
    if (!String(id).startsWith(owner + ':')) return;
    const acknowledgement = acknowledgements.get(id);
    if (acknowledgement) {
      if (success) acknowledgement.resolve(); else acknowledgement.reject(new Error(message || 'ファイルを保存できませんでした'));
    } else if (!success) fail(message || 'ファイルを保存できませんでした');
  };
  const intercept = anchor => {
    if (!available() || !anchor?.hasAttribute?.('download')) return false;
    window.__editRinDownload(anchor.href, anchor.download || 'untitled'); return true;
  };
  const prototype = window.HTMLAnchorElement?.prototype;
  if (prototype?.click) {
    const click = prototype.click;
    prototype.click = function (...args) { if (!intercept(this)) return click.apply(this, args); };
  }
  document.addEventListener?.('click', event => {
    const anchor = event.target?.closest?.('a[download]');
    if (!event.defaultPrevented && intercept(anchor)) event.preventDefault();
  }, true);
  window.addEventListener('beforeunload', () => {
    unloaded = true; blobs.clear();
    if (activeId) window.Android?.abortFileDownload?.(owner, activeId);
    for (const acknowledgement of acknowledgements.values()) acknowledgement.reject(new Error('ページが終了しました'));
    acknowledgements.clear();
  }, { once: true });
})();
  


(() => {
let lastWidth = 0;
let lastHeight = 0;
let resizeFrame = 0;
let pendingForcedFit = false;
const runToken = window.__editRinRunToken;
const config = window.__editRinProjectConfig || {};
const hostFrame = window.requestAnimationFrame.bind(window);
const cancelHostFrame = window.cancelAnimationFrame.bind(window);
const instances = new Map();
let sketchReady = false, readyReported = false, hostPaused = false;
let visualReadyReported = false, visualReadyPending = false;
let recordingSession = null;
const canvasSizeObservers = new Map();
let orientationBase = null, compositeCanvas = null, compositeFrame = 0;
let pageLoaded = document.readyState === 'complete';

function notifyAndroid(method, ...args) {
  try { window.Android?.[method]?.(runToken, ...args); } catch (_) {}
}
function scope(instance) {
  return instance?._isGlobal && typeof instance.noLoop !== 'function' ? window : instance;
}
function drawCallback(instance, api = scope(instance) || window) {
  // Global-mode callbacks live on window even when p5 exposes its control
  // methods on the concrete instance. Instance-mode callbacks live on p.
  return instance?._isGlobal ? window.draw : api.draw;
}
function track(instance) {
  if (!instances.has(instance)) instances.set(instance, { ready: false, drawn: false, pausedLoop: null });
  return instances.get(instance);
}
function instanceCanvas(instance) {
  return instance?.canvas || instance?._renderer?.canvas || instance?._curElement?.elt;
}
function canvasEntries() {
  const result = [], seen = new Set();
  for (const [instance, state] of instances) {
    const canvas = instanceCanvas(instance);
    if (state.ready && canvas && canvas.isConnected !== false && !seen.has(canvas)) {
      seen.add(canvas); result.push({ instance, canvas });
    }
  }
  if (!result.length) {
    const canvases = document.querySelectorAll?.('canvas.p5Canvas, canvas') ||
      [document.querySelector('canvas.p5Canvas') || document.querySelector('canvas')];
    for (const canvas of canvases) if (canvas && canvas !== compositeCanvas && !seen.has(canvas)) {
      seen.add(canvas);
      const instance = [...instances.keys()].find(value => value._isGlobal);
      result.push({ instance, canvas });
    }
  }
  return result;
}
function viewportSize() {
  const root = document.documentElement;
  return { width: Math.max(1, Math.round(root.clientWidth || window.innerWidth || 1)),
    height: Math.max(1, Math.round(root.clientHeight || window.innerHeight || 1)) };
}
function canvasLayout() {
  return canvasEntries().map(({ instance, canvas }) => {
    const bounds = canvas.getBoundingClientRect?.();
    const api = scope(instance) || window;
    return { instance, canvas,
      left: bounds?.left ?? (parseFloat(canvas.style.left) || 0),
      top: bounds?.top ?? (parseFloat(canvas.style.top) || 0),
      width: bounds?.width || parseFloat(canvas.style.width) || Number(api.width) || canvas.width,
      height: bounds?.height || parseFloat(canvas.style.height) || Number(api.height) || canvas.height };
  }).filter(item => item.width > 0 && item.height > 0);
}
function sketchCanvas() {
  const layout = canvasLayout();
  if (layout.length < 2) return layout[0]?.canvas || null;
  const left = Math.min(...layout.map(item => item.left)), top = Math.min(...layout.map(item => item.top));
  const width = Math.max(...layout.map(item => item.left + item.width)) - left;
  const height = Math.max(...layout.map(item => item.top + item.height)) - top;
  const density = Math.max(...layout.map(item => Math.max(item.canvas.width / item.width, item.canvas.height / item.height)));
  const outputWidth = Math.max(1, Math.round(width * density)), outputHeight = Math.max(1, Math.round(height * density));
  if (outputWidth > 8192 || outputHeight > 8192 || outputWidth * outputHeight > 16000000) {
    throw new Error('画像が大きすぎます。倍率またはキャンバスサイズを下げてください');
  }
  compositeCanvas ||= document.createElement('canvas');
  if (compositeCanvas.width !== outputWidth || compositeCanvas.height !== outputHeight) {
    compositeCanvas.width = outputWidth; compositeCanvas.height = outputHeight;
  }
  const context = compositeCanvas.getContext('2d');
  context.clearRect(0, 0, outputWidth, outputHeight);
  for (const item of layout) context.drawImage(item.canvas,
    (item.left - left) * density, (item.top - top) * density, item.width * density, item.height * density);
  return compositeCanvas;
}
function updateRecordingComposite() {
  if (!recordingSession || !compositeCanvas) return;
  try { sketchCanvas(); } catch (error) { failRecording(recordingSession, error.message); return; }
  compositeFrame = hostFrame(updateRecordingComposite);
}
function resizeForOrientation(width, height) {
  const entries = canvasEntries();
  if (entries.length !== 1 || !entries[0].instance) throw new Error('キャンバスの縦横切替は単一のp5スケッチで使えます');
  const api = scope(entries[0].instance);
  if (api.width === width && api.height === height) return;
  if (typeof api.resizeCanvas !== 'function') throw new Error('Canvas resizing is unavailable');
  let snapshot = null;
  if (typeof drawCallback(entries[0].instance, api) !== 'function' && api.drawingContext?.drawImage) {
    const source = entries[0].canvas;
    snapshot = document.createElement('canvas'); snapshot.width = source.width; snapshot.height = source.height;
    snapshot.getContext('2d').drawImage(source, 0, 0);
  }
  api.resizeCanvas(width, height, true);
  if (snapshot) {
    const ctx = api.drawingContext, target = entries[0].canvas;
    const scale = Math.min(target.width / snapshot.width, target.height / snapshot.height);
    ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.drawImage(snapshot, (target.width - snapshot.width * scale) / 2,
      (target.height - snapshot.height * scale) / 2, snapshot.width * scale, snapshot.height * scale);
    ctx.restore();
  }
  if (typeof api.isLooping === 'function' && !api.isLooping() && typeof drawCallback(entries[0].instance, api) === 'function') api.redraw?.();
}
window.__editKiroSetCanvasSwapped = swapped => {
  if (!sketchReady || recordingSession || captureBusy || config.documentMode || canvasEntries().length !== 1) return false;
  try {
    const api = scope(canvasEntries()[0].instance) || window;
    if (swapped && !orientationBase) {
      const width = Number(api.width), height = Number(api.height);
      if (!(width > 0 && height > 0)) return false;
      resizeForOrientation(height, width); orientationBase = { width, height };
    } else if (!swapped && orientationBase) {
      resizeForOrientation(orientationBase.width, orientationBase.height); orientationBase = null;
    }
    scheduleFit(true); return true;
  } catch (error) { notifyAndroid('onCaptureError', error.message); return false; }
};
function fitSketch(force = false) {
  if (!sketchReady || captureBusy || config.documentMode) return;
  const { width, height } = viewportSize();
  if (!force && width === lastWidth && height === lastHeight) return;
  const entries = canvasEntries(), columns = Math.ceil(Math.sqrt(entries.length)), rows = Math.ceil(entries.length / columns);
  const cellWidth = width / columns, cellHeight = height / rows;
  try {
    const primary = scope(entries[0]?.instance) || window;
    if (orientationBase && orientationBase.width !== orientationBase.height && primary.width !== primary.height &&
        (primary.width > primary.height) === (orientationBase.width > orientationBase.height)) resizeForOrientation(primary.height, primary.width);
    entries.forEach(({ instance, canvas }, index) => {
      const api = scope(instance) || window;
      const logicalWidth = Number(api.width) || canvas.width, logicalHeight = Number(api.height) || canvas.height;
      if (!(logicalWidth > 0 && logicalHeight > 0)) return;
      const scale = Math.min(cellWidth / logicalWidth, cellHeight / logicalHeight);
      Object.assign(canvas.style, { position: 'absolute', display: 'block', margin: '0', right: 'auto', bottom: 'auto',
        maxWidth: 'none', maxHeight: 'none', width: `${logicalWidth * scale}px`, height: `${logicalHeight * scale}px`,
        left: `${(index % columns) * cellWidth + (cellWidth - logicalWidth * scale) / 2}px`,
        top: `${Math.floor(index / columns) * cellHeight + (cellHeight - logicalHeight * scale) / 2}px` });
    });
    lastWidth = width; lastHeight = height;
  } catch (error) { window.__editKiroReportError(error.message); }
}
function scheduleFit(force = false) {
  pendingForcedFit ||= force;
  if (resizeFrame) return;
  resizeFrame = hostFrame(() => {
    resizeFrame = 0; const mustFit = pendingForcedFit; pendingForcedFit = false; fitSketch(mustFit);
  });
}
const pausedAnimations = new Set();
window.pauseSketch = () => {
  if (hostPaused) return;
  hostPaused = true;
  for (const [instance, state] of instances) {
    const api = scope(instance);
    state.pausedLoop = typeof api.isLooping === 'function' ? api.isLooping() : true;
    api.noLoop?.();
  }
  for (const animation of document.getAnimations?.() || []) if (animation.playState === 'running') {
    pausedAnimations.add(animation); animation.pause();
  }
  try { window.getAudioContext?.().suspend(); } catch (_) {}
  notifyAndroid('onStatusChanged', '一時停止中');
};
window.resumeSketch = () => {
  if (hostPaused) {
    hostPaused = false;
    for (const [instance, state] of instances) {
      if (state.pausedLoop) scope(instance).loop?.();
      state.pausedLoop = null;
    }
    for (const animation of pausedAnimations) animation.play();
    pausedAnimations.clear();
    resumeAnimationFrames();
  }
  try { window.getAudioContext?.().resume().catch(() => {}); } catch (_) {}
  if (!window.__editKiroRuntimeHasError) notifyAndroid('onStatusChanged', '実行中');
};
// Host presentation/capture uses the original RAF. Artwork RAFs can be paused,
// including an HTML canvas which does not use p5; timers retain their normal semantics.
let artworkFrameId = 0;
const artworkFrames = new Map();
function dispatchArtworkFrame(id) {
  const entry = artworkFrames.get(id);
  if (!entry) return;
  entry.native = hostFrame(time => {
    if (!artworkFrames.has(id)) return;
    entry.native = 0;
    if (hostPaused) return;
    artworkFrames.delete(id); entry.callback(time);
  });
}
function resumeAnimationFrames() {
  for (const [id, entry] of artworkFrames) if (!entry.native) dispatchArtworkFrame(id);
}
window.requestAnimationFrame = callback => {
  const id = --artworkFrameId;
  artworkFrames.set(id, { callback, native: 0 });
  if (!hostPaused) dispatchArtworkFrame(id);
  return id;
};
window.cancelAnimationFrame = id => {
  const entry = artworkFrames.get(id);
  if (entry) { if (entry.native) cancelHostFrame(entry.native); artworkFrames.delete(id); }
  else cancelHostFrame(id);
};

const chunkedPng = () => window.Android?.beginScreenshotTransfer &&
  window.Android?.appendScreenshotChunk && window.Android?.finishScreenshotTransfer;

function encodePng(canvas) {
  if (!chunkedPng() || !canvas.toBlob) return canvas.toDataURL('image/png');
  return new Promise((resolve, reject) => canvas.toBlob(blob => {
    if (blob) resolve(blob); else reject(new Error('PNGの作成に失敗しました'));
  }, 'image/png'));
}

async function publishPng(payload, width, height) {
  if (typeof payload === 'string') {
    if (window.Android?.onScreenshotExportReady) notifyAndroid('onScreenshotExportReady', payload, width, height);
    else notifyAndroid('onScreenshotReady', payload);
    return;
  }
  const token = runToken + ':png-' + Date.now() + '-' + Math.random().toString(36).slice(2);
  if (!window.Android.beginScreenshotTransfer(runToken, token)) throw new Error('画像の保存を開始できませんでした');
  try {
    for (let offset = 0; offset < payload.size; offset += 192 * 1024) {
      const encoded = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(',')[1]);
        reader.onerror = reader.onabort = () => reject(new Error('画像データを読み出せませんでした'));
        reader.readAsDataURL(payload.slice(offset, offset + 192 * 1024));
      });
      if (!window.Android.appendScreenshotChunk(runToken, token, encoded)) throw new Error('画像を保存できませんでした');
    }
    window.Android.finishScreenshotTransfer(runToken, token, width, height);
  } catch (error) {
    window.Android?.abortScreenshotTransfer?.(runToken, token);
    throw error;
  }
}

let captureBusy = false;
// Only the disposable thumbnail page may stop/redraw a sketch. A setup-ready
// notification can arrive before p5's first draw (including async p5 2 draws).
window.__editRinCaptureThumbnail = async () => {
  if (!config.thumbnailOnly || captureBusy) return;
  captureBusy = true;
  let encoded = '';
  try {
    let entries;
    do {
      if (window.__editKiroRuntimeHasError) return;
      entries = canvasEntries();
      if (entries.length && entries.every(({ instance }) => {
        const api = scope(instance) || window;
        return !instance || (typeof drawCallback(instance, api) !== 'function' || track(instance).drawn) && !instance._inUserDraw;
      })) break;
      await new Promise(resolve => hostFrame(resolve));
    } while (true);
    for (const { instance } of entries) (scope(instance) || window).noLoop?.();
    // redraw resolves after p5 2 renderer submission and refreshes WebGL
    // canvases whose drawing buffer is discarded after presentation.
    for (let attempt = 0; attempt < 12; attempt++) {
      for (const { instance } of entries) {
        const api = scope(instance) || window;
        if (typeof drawCallback(instance, api) === 'function') await api.redraw?.();
      }
      if (window.__editKiroRuntimeHasError) return;
      const source = sketchCanvas();
      if (!source?.width || !source.height) return;
      const copy = document.createElement('canvas');
      const scale = Math.min(1, 480 / Math.max(source.width, source.height));
      copy.width = Math.max(1, Math.round(source.width * scale));
      copy.height = Math.max(1, Math.round(source.height * scale));
      const context = copy.getContext('2d', { willReadFrequently: true });
      context.drawImage(source, 0, 0, copy.width, copy.height);
      const pixels = context.getImageData(0, 0, copy.width, copy.height).data;
      if (pixels.some((value, index) => index % 4 === 3 && value > 0)) {
        encoded = copy.toDataURL('image/png').split(',')[1];
        break;
      }
      await new Promise(resolve => hostFrame(resolve));
    }
  } catch (_) {
    // Missing/tainted canvases are failed captures, never cached blank images.
  } finally {
    captureBusy = false;
    notifyAndroid('onThumbnailReady', encoded);
  }
};
window.__editKiroCaptureScreenshot = async (scale = 1) => {
  if (captureBusy) return;
  captureBusy = true;
  try {
    if (![1, 2, 4].includes(scale)) throw new Error('書き出し倍率が無効です');
    const canvas = sketchCanvas();
    if (!canvas) throw new Error('キャンバスが見つかりません');
    let dataUrl;
    const outputWidth = canvas.width * scale, outputHeight = canvas.height * scale;
    if (outputWidth > 8192 || outputHeight > 8192 || outputWidth * outputHeight > 16000000) {
      throw new Error('画像が大きすぎます。倍率またはキャンバスサイズを下げてください');
    }
    if (scale === 1) {
      dataUrl = await encodePng(canvas);
    } else {
      const entries = canvasEntries();
      const states = entries.map(({ instance, canvas }) => {
        const api = scope(instance) || window;
        return { instance, canvas, api, density: api.pixelDensity?.(), looping: api.isLooping?.() === true,
          oldStyle: canvas.style.cssText, width: canvas.width, height: canvas.height, changed: false };
      });
      if (!sketchReady || recordingSession || window.__editKiroRuntimeHasError ||
          !states.length || states.some(item => typeof item.api.pixelDensity !== 'function' ||
            typeof item.api.redraw !== 'function' || typeof drawCallback(item.instance, item.api) !== 'function')) {
        throw new Error('再描画できるp5作品で録画を停止してから書き出してください');
      }
      try {
        for (const item of states) if (item.looping) item.api.noLoop();
        const deadline = Date.now() + 2000;
        while (states.some(item => (item.instance || window.p5?.instance)?._inUserDraw)) {
          if (Date.now() > deadline) throw new Error('描画が完了してから再度お試しください');
          await new Promise(resolve => hostFrame(resolve));
        }
        for (const item of states) {
          item.changed = true;
          item.api.pixelDensity(item.density * scale);
          item.canvas.style.cssText = item.oldStyle;
          await item.api.redraw();
          if (item.canvas.width !== item.width * scale || item.canvas.height !== item.height * scale) {
            throw new Error('指定した解像度で再描画できませんでした');
          }
        }
        const rendered = sketchCanvas();
        if (window.__editKiroRuntimeHasError || rendered.width !== outputWidth || rendered.height !== outputHeight) {
          throw new Error('指定した解像度で再描画できませんでした');
        }
        dataUrl = await encodePng(rendered);
      } finally {
        let restoreError;
        for (const item of states) {
          try {
            if (item.changed) { item.api.pixelDensity(item.density); await item.api.redraw(); }
          } catch (error) { restoreError ||= error; }
          finally {
            item.canvas.style.cssText = item.oldStyle;
            if (item.looping) item.api.loop?.(); else item.api.noLoop?.();
          }
        }
        if (restoreError) throw restoreError;
      }
    }
    await publishPng(dataUrl, outputWidth, outputHeight);
  } catch (error) {
    if (window.Android?.onScreenshotError) notifyAndroid('onScreenshotError', error?.message || String(error));
    else notifyAndroid('onCaptureError', error?.message || String(error));
  } finally {
    captureBusy = false;
    scheduleFit();
  }
};

function releaseRecording(session) {
  clearTimeout(session.timer);
  clearInterval(session.captureTimer);
  session.stream?.getTracks().forEach(track => track.stop());
  session.gifWorker?.terminate();
  if (session.gifWorkerUrl) URL.revokeObjectURL(session.gifWorkerUrl);
  if (session.recorder) {
    session.recorder.ondataavailable = null;
    session.recorder.onerror = null;
    session.recorder.onstop = null;
  }
  if (recordingSession === session) { recordingSession = null; cancelHostFrame(compositeFrame); compositeFrame = 0; }
}

function failRecording(session, message) {
  if (recordingSession && recordingSession !== session) return;
  const recorder = session?.recorder;
  if (session?.transferId) notifyAndroid('abortRecordingTransfer', session.transferId);
  if (session) releaseRecording(session);
  try {
    if (recorder && recorder.state !== 'inactive') recorder.stop();
  } catch (_) {}
  notifyAndroid('onRecordingStatusChanged', false);
  notifyAndroid('onCaptureError', message);
}

function gifWorkerMain() {
  let width = 0;
  let height = 0;
  let parts = [];
  let lastDelayCs = 0;
  let lastDelayBytes = null;

  const bytes = (...values) => Uint8Array.from(values);
  const littleEndian = value => bytes(value & 255, (value >>> 8) & 255);
  const textBytes = value => Uint8Array.from([...value].map(character => character.charCodeAt(0)));

  function adaptivePalette(rgba) {
    // Build a compact 5-bit/channel histogram, then split it into weighted
    // median-cut boxes. Per-frame palettes retain gradients far better than
    // the previous fixed RGB332 table.
    const counts = new Uint32Array(32768);
    const redSums = new Uint32Array(32768);
    const greenSums = new Uint32Array(32768);
    const blueSums = new Uint32Array(32768);
    const colors = [];
    for (let offset = 0; offset < rgba.length; offset += 4) {
      const red = rgba[offset];
      const green = rgba[offset + 1];
      const blue = rgba[offset + 2];
      const bin = (red >>> 3) << 10 | (green >>> 3) << 5 | (blue >>> 3);
      if (counts[bin] === 0) colors.push(bin);
      counts[bin]++;
      redSums[bin] += red;
      greenSums[bin] += green;
      blueSums[bin] += blue;
    }

    const makeBox = entries => {
      let minRed = 31, minGreen = 31, minBlue = 31;
      let maxRed = 0, maxGreen = 0, maxBlue = 0, population = 0;
      for (const bin of entries) {
        const red = (bin >>> 10) & 31;
        const green = (bin >>> 5) & 31;
        const blue = bin & 31;
        minRed = Math.min(minRed, red); maxRed = Math.max(maxRed, red);
        minGreen = Math.min(minGreen, green); maxGreen = Math.max(maxGreen, green);
        minBlue = Math.min(minBlue, blue); maxBlue = Math.max(maxBlue, blue);
        population += counts[bin];
      }
      const ranges = [maxRed - minRed, maxGreen - minGreen, maxBlue - minBlue];
      return {
        entries, population, ranges,
        score: Math.max(...ranges) * Math.sqrt(population)
      };
    };

    const boxes = [makeBox(colors.length ? colors : [0])];
    while (boxes.length < 256) {
      let selectedIndex = -1;
      let selectedScore = -1;
      for (let index = 0; index < boxes.length; index++) {
        const box = boxes[index];
        if (box.entries.length > 1 && box.score > selectedScore) {
          selectedIndex = index;
          selectedScore = box.score;
        }
      }
      if (selectedIndex < 0) break;
      const selected = boxes.splice(selectedIndex, 1)[0];
      const channel = selected.ranges.indexOf(Math.max(...selected.ranges));
      const shift = channel === 0 ? 10 : channel === 1 ? 5 : 0;
      selected.entries.sort((left, right) =>
        ((left >>> shift) & 31) - ((right >>> shift) & 31)
      );
      const midpoint = selected.population / 2;
      let accumulated = 0;
      let split = 1;
      for (; split < selected.entries.length; split++) {
        accumulated += counts[selected.entries[split - 1]];
        if (accumulated >= midpoint) break;
      }
      split = Math.max(1, Math.min(selected.entries.length - 1, split));
      boxes.push(
        makeBox(selected.entries.slice(0, split)),
        makeBox(selected.entries.slice(split))
      );
    }

    const palette = new Uint8Array(256 * 3);
    boxes.forEach((box, paletteIndex) => {
      let population = 0, red = 0, green = 0, blue = 0;
      for (const bin of box.entries) {
        population += counts[bin];
        red += redSums[bin];
        green += greenSums[bin];
        blue += blueSums[bin];
      }
      palette[paletteIndex * 3] = Math.round(red / Math.max(1, population));
      palette[paletteIndex * 3 + 1] = Math.round(green / Math.max(1, population));
      palette[paletteIndex * 3 + 2] = Math.round(blue / Math.max(1, population));
    });
    const last = Math.max(0, boxes.length - 1);
    for (let index = boxes.length; index < 256; index++) {
      palette[index * 3] = palette[last * 3];
      palette[index * 3 + 1] = palette[last * 3 + 1];
      palette[index * 3 + 2] = palette[last * 3 + 2];
    }
    return { palette, colorCount: boxes.length };
  }

  function ditherAndIndex(rgba, palette, colorCount) {
    const indexed = new Uint8Array(width * height);
    const lookup = new Int16Array(32768);
    lookup.fill(-1);
    let currentRed = new Float32Array(width + 2);
    let currentGreen = new Float32Array(width + 2);
    let currentBlue = new Float32Array(width + 2);
    let nextRed = new Float32Array(width + 2);
    let nextGreen = new Float32Array(width + 2);
    let nextBlue = new Float32Array(width + 2);
    const clamp = value => Math.max(0, Math.min(255, value));

    const nearestColor = (red, green, blue) => {
      const bin = (red >>> 3) << 10 | (green >>> 3) << 5 | (blue >>> 3);
      const cached = lookup[bin];
      if (cached >= 0) return cached;
      let best = 0;
      let bestDistance = Infinity;
      for (let index = 0; index < colorCount; index++) {
        const paletteOffset = index * 3;
        const redDifference = red - palette[paletteOffset];
        const greenDifference = green - palette[paletteOffset + 1];
        const blueDifference = blue - palette[paletteOffset + 2];
        const distance = redDifference * redDifference * 3 +
          greenDifference * greenDifference * 4 +
          blueDifference * blueDifference * 2;
        if (distance < bestDistance) {
          bestDistance = distance;
          best = index;
        }
      }
      lookup[bin] = best;
      return best;
    };

    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const pixel = y * width + x;
        const source = pixel * 4;
        const errorIndex = x + 1;
        const red = Math.round(clamp(rgba[source] + currentRed[errorIndex]));
        const green = Math.round(clamp(rgba[source + 1] + currentGreen[errorIndex]));
        const blue = Math.round(clamp(rgba[source + 2] + currentBlue[errorIndex]));
        const paletteIndex = nearestColor(red, green, blue);
        indexed[pixel] = paletteIndex;
        const paletteOffset = paletteIndex * 3;
        const redError = red - palette[paletteOffset];
        const greenError = green - palette[paletteOffset + 1];
        const blueError = blue - palette[paletteOffset + 2];

        currentRed[errorIndex + 1] += redError * 7 / 16;
        currentGreen[errorIndex + 1] += greenError * 7 / 16;
        currentBlue[errorIndex + 1] += blueError * 7 / 16;
        nextRed[errorIndex - 1] += redError * 3 / 16;
        nextGreen[errorIndex - 1] += greenError * 3 / 16;
        nextBlue[errorIndex - 1] += blueError * 3 / 16;
        nextRed[errorIndex] += redError * 5 / 16;
        nextGreen[errorIndex] += greenError * 5 / 16;
        nextBlue[errorIndex] += blueError * 5 / 16;
        nextRed[errorIndex + 1] += redError / 16;
        nextGreen[errorIndex + 1] += greenError / 16;
        nextBlue[errorIndex + 1] += blueError / 16;
      }
      [currentRed, nextRed] = [nextRed, currentRed]; nextRed.fill(0);
      [currentGreen, nextGreen] = [nextGreen, currentGreen]; nextGreen.fill(0);
      [currentBlue, nextBlue] = [nextBlue, currentBlue]; nextBlue.fill(0);
    }
    return indexed;
  }

  function lzwBlocks(indexed) {
    const clearCode = 256;
    const endCode = 257;
    let nextCode = 258;
    let codeSize = 9;
    let dictionary = new Map();
    const compressed = [];
    let accumulator = 0;
    let bitCount = 0;

    const writeCode = code => {
      accumulator |= code << bitCount;
      bitCount += codeSize;
      while (bitCount >= 8) {
        compressed.push(accumulator & 255);
        accumulator >>>= 8;
        bitCount -= 8;
      }
    };
    const reset = () => {
      dictionary = new Map();
      nextCode = 258;
      codeSize = 9;
    };

    writeCode(clearCode);
    if (indexed.length) {
      let prefix = indexed[0];
      for (let offset = 1; offset < indexed.length; offset++) {
        const suffix = indexed[offset];
        const key = prefix * 256 + suffix;
        const found = dictionary.get(key);
        if (found !== undefined) {
          prefix = found;
          continue;
        }
        writeCode(prefix);
        if (nextCode < 4096) {
          dictionary.set(key, nextCode++);
          // The decoder adds this entry after it reads the next emitted code.
          if (nextCode > (1 << codeSize) && codeSize < 12) codeSize++;
        } else {
          writeCode(clearCode);
          reset();
        }
        prefix = suffix;
      }
      writeCode(prefix);
    }
    writeCode(endCode);
    if (bitCount) compressed.push(accumulator & 255);

    const blocks = [bytes(8)];
    for (let offset = 0; offset < compressed.length; offset += 255) {
      const block = compressed.slice(offset, offset + 255);
      blocks.push(bytes(block.length), Uint8Array.from(block));
    }
    blocks.push(bytes(0));
    return blocks;
  }

  function beginGif() {
    parts = [
      textBytes('GIF89a'), littleEndian(width), littleEndian(height),
      bytes(0x70, 0, 0),
      bytes(0x21, 0xff, 0x0b), textBytes('NETSCAPE2.0'),
      bytes(3, 1, 0, 0, 0)
    ];
  }

  function addFrame(rgba, delayCs) {
    const quantized = adaptivePalette(rgba);
    const indexed = ditherAndIndex(rgba, quantized.palette, quantized.colorCount);
    lastDelayCs = Math.max(1, Math.min(65535, delayCs));
    lastDelayBytes = littleEndian(lastDelayCs);
    parts.push(
      bytes(0x21, 0xf9, 4, 0), lastDelayBytes, bytes(0, 0),
      bytes(0x2c), littleEndian(0), littleEndian(0), littleEndian(width), littleEndian(height), bytes(0x87),
      quantized.palette,
      ...lzwBlocks(indexed)
    );
  }

  self.onmessage = event => {
    const message = event.data;
    if (message.type === 'init') {
      width = message.width;
      height = message.height;
      beginGif();
    } else if (message.type === 'frame') {
      addFrame(new Uint8ClampedArray(message.buffer), message.delayCs);
      self.postMessage({ type: 'ready' });
    } else if (message.type === 'extend' && lastDelayBytes) {
      lastDelayCs = Math.min(65535, lastDelayCs + message.delayCs);
      lastDelayBytes[0] = lastDelayCs & 255;
      lastDelayBytes[1] = (lastDelayCs >>> 8) & 255;
    } else if (message.type === 'finish') {
      parts.push(bytes(0x3b));
      self.postMessage({ type: 'done', blob: new Blob(parts, { type: 'image/gif' }) });
    }
  };
}
const gifWorkerSource = `(${gifWorkerMain.toString()})()`;
window.__editKiroGifWorkerSource = gifWorkerSource;

function finishGifWhenReady(session) {
  if (session.gifStopping && session.gifPending === 0 && !session.gifFinishing) {
    session.gifFinishing = true;
    session.gifWorker.postMessage({ type: 'finish' });
  }
}

async function queueRecordingBlob(session, blob) {
  for (let offset = 0; offset < blob.size; offset += 192 * 1024) {
    if (recordingSession !== session) throw new Error('録画の保存を中断しました');
    const encoded = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1]);
      reader.onerror = reader.onabort = () => reject(new Error('録画データを読み出せませんでした'));
      reader.readAsDataURL(blob.slice(offset, offset + 192 * 1024));
    });
    if (recordingSession !== session ||
        !window.Android?.appendRecordingChunk(runToken, session.transferId, encoded)) {
      throw new Error('録画を保存できませんでした');
    }
    session.transferred += Math.min(192 * 1024, blob.size - offset);
  }
}

function finishRecordingTransfer(session, mimeType) {
  if (recordingSession !== session) return;
  if (!session.transferred) throw new Error('録画データが空です。少し長めに録画してください');
  notifyAndroid('finishRecordingTransfer', session.transferId, mimeType);
  releaseRecording(session);
  notifyAndroid('onRecordingStatusChanged', false);
}

function startGifRecording(session, canvas) {
  const longestSide = Math.max(canvas.width, canvas.height);
  const scale = Math.min(1, 720 / Math.max(1, longestSide));
  const width = Math.max(1, Math.round(canvas.width * scale));
  const height = Math.max(1, Math.round(canvas.height * scale));
  const captureCanvas = document.createElement('canvas');
  captureCanvas.width = width;
  captureCanvas.height = height;
  const captureContext = captureCanvas.getContext('2d', { alpha: false, willReadFrequently: true });
  if (!captureContext || typeof Worker === 'undefined') {
    throw new Error('この端末はGIF録画に対応していません');
  }

  session.format = 'gif';
  session.gifPending = 0;
  session.gifStopping = false;
  session.gifFinishing = false;
  session.gifFrameIndex = 0;
  session.gifTickIndex = 0;
  session.gifPendingDelayCs = 0;
  session.gifWorkerUrl = URL.createObjectURL(new Blob(
    [gifWorkerSource],
    { type: 'text/javascript' }
  ));
  session.gifWorker = new Worker(session.gifWorkerUrl);
  session.gifWorker.onerror = event => failRecording(
    session,
    event.message || 'GIFの作成中にエラーが発生しました'
  );
  session.gifWorker.onmessage = event => {
    if (recordingSession !== session) return;
    if (event.data.type === 'ready') {
      session.gifPending = Math.max(0, session.gifPending - 1);
      finishGifWhenReady(session);
    } else if (event.data.type === 'done') {
      session.pending = queueRecordingBlob(session, event.data.blob);
      session.pending.then(() => finishRecordingTransfer(session, 'image/gif'))
        .catch(error => failRecording(session, error.message));
    }
  };
  session.gifWorker.postMessage({ type: 'init', width, height });

  const captureFrame = () => {
    if (recordingSession !== session || session.gifStopping) return;
    session.gifPendingDelayCs += session.gifTickIndex % 3 === 2 ? 4 : 3;
    session.gifTickIndex++;
    if (session.gifPending >= 2) return;
    try {
      captureContext.drawImage(canvas, 0, 0, width, height);
      const image = captureContext.getImageData(0, 0, width, height);
      // GIF timing uses 1/100-second units. 3, 3, 4 averages exactly 30 fps.
      // If encoding falls behind, retain elapsed time instead of shortening the clip.
      const delayCs = session.gifPendingDelayCs;
      session.gifPendingDelayCs = 0;
      session.gifFrameIndex++;
      session.gifPending++;
      session.gifWorker.postMessage(
        { type: 'frame', buffer: image.data.buffer, delayCs },
        [image.data.buffer]
      );
    } catch (error) {
      failRecording(session, error?.message || String(error));
    }
  };
  captureFrame();
  session.captureTimer = setInterval(captureFrame, 1000 / 30);
  session.timer = setTimeout(() => window.__editKiroStopRecording(), 15000);
}

window.__editKiroStartRecording = (
  requestedFormat = 'webm',
  requestedVideoBitsPerSecond = 5000000
) => {
  // Keep ownership through FileReader completion so a rapid restart cannot mix clips.
  if (recordingSession) return;
  const session = {
    recorder: null, stream: null, timer: 0, captureTimer: 0,
    gifWorker: null, gifWorkerUrl: '', format: requestedFormat,
    pending: Promise.resolve(), queuedBytes: 0, transferred: 0,
    transferId: Date.now().toString(36) + Math.random().toString(36).slice(2)
  };
  try {
    const canvas = sketchCanvas();
    if (!canvas) throw new Error('キャンバスが見つかりません');
    recordingSession = session;
    if (compositeCanvas && canvasEntries().length > 1 && !compositeFrame) compositeFrame = hostFrame(updateRecordingComposite);
    if (window.Android?.beginRecordingTransfer &&
        !window.Android.beginRecordingTransfer(runToken, session.transferId)) {
      throw new Error('録画の保存先を準備できませんでした');
    }
    if (requestedFormat === 'gif') {
      startGifRecording(session, canvas);
      notifyAndroid('onRecordingStatusChanged', true);
      return;
    }
    if (!canvas.captureStream || typeof MediaRecorder === 'undefined') {
      throw new Error('この端末はキャンバス録画に対応していません');
    }
    const candidates = requestedFormat === 'mp4' ? [
      'video/mp4;codecs=avc1.42E01E',
      'video/mp4;codecs=avc1',
      'video/mp4'
    ] : [
      'video/webm;codecs=vp9',
      'video/webm;codecs=vp8',
      'video/webm'
    ];
    const mimeType = candidates.find(type => MediaRecorder.isTypeSupported(type));
    if (!mimeType && requestedFormat === 'mp4') {
      throw new Error('この端末はMP4録画に対応していません');
    }
    const numericBitrate = Number(requestedVideoBitsPerSecond);
    const videoBitsPerSecond = Number.isFinite(numericBitrate)
      ? Math.max(500000, Math.min(20000000, Math.round(numericBitrate)))
      : 5000000;
    session.stream = canvas.captureStream(30);
    const recorder = new MediaRecorder(
      session.stream,
      mimeType ? { mimeType, videoBitsPerSecond } : undefined
    );
    session.recorder = recorder;
    recorder.ondataavailable = event => {
      if (event.data && event.data.size > 0) {
        session.queuedBytes += event.data.size;
        if (session.queuedBytes > 16 * 1024 * 1024) {
          failRecording(session, '保存処理が追いつきません。ビットレートを下げてください');
          return;
        }
        session.pending = session.pending.then(() => queueRecordingBlob(session, event.data))
          .then(() => { session.queuedBytes -= event.data.size; });
        session.pending.catch(error => failRecording(session, error.message));
      }
    };
    recorder.onerror = event => {
      failRecording(session, event.error?.message || '録画中にエラーが発生しました');
    };
    recorder.onstop = () => {
      clearTimeout(session.timer);
      session.stream.getTracks().forEach(track => track.stop());
      try {
        const type = recorder.mimeType || mimeType || 'video/webm';
        notifyAndroid('onRecordingSaving');
        session.pending.then(() => finishRecordingTransfer(session, type))
          .catch(error => failRecording(session, error.message));
      } catch (error) {
        failRecording(session, error?.message || String(error));
      }
    };
    recorder.start(1000);
    notifyAndroid('onRecordingStatusChanged', true);
    session.timer = setTimeout(() => window.__editKiroStopRecording(), 60000);
  } catch (error) {
    failRecording(session, error?.message || String(error));
  }
};

window.__editKiroStopRecording = () => {
  const session = recordingSession;
  if (!session) return;
  try {
    clearTimeout(session.timer);
    notifyAndroid('onRecordingSaving');
    if (session.format === 'gif') {
      clearInterval(session.captureTimer);
      session.gifStopping = true;
      if (session.gifFrameIndex === 0) {
        throw new Error('録画データが空です。少し長めに録画してください');
      }
      if (session.gifPendingDelayCs > 0) {
        session.gifWorker.postMessage({ type: 'extend', delayCs: session.gifPendingDelayCs });
        session.gifPendingDelayCs = 0;
      }
      finishGifWhenReady(session);
      return;
    }
    if (session.recorder.state !== 'inactive') {
      session.recorder.stop();
    }
  } catch (error) {
    failRecording(session, error?.message || String(error));
  }
};

const onViewportResize = () => scheduleFit(false);
window.addEventListener('resize', onViewportResize, { passive: true });
window.visualViewport?.addEventListener('resize', onViewportResize, { passive: true });

const resizeObserver =
  typeof ResizeObserver === 'function'
    ? new ResizeObserver(onViewportResize)
    : null;
resizeObserver?.observe(document.documentElement);

// Setup completion can precede the first canvas draw. Signal the visual handoff separately.
function maybeVisualReady() {
  if (!readyReported || visualReadyReported || visualReadyPending || window.__editKiroRuntimeHasError) return;
  if ([...instances].some(([instance, state]) => !state.ready ||
      (!state.drawn && typeof drawCallback(instance) === 'function'))) return;
  visualReadyPending = true;
  hostFrame(() => {
    visualReadyPending = false;
    if (visualReadyReported || window.__editKiroRuntimeHasError ||
        [...instances].some(([instance, state]) => !state.ready ||
      (!state.drawn && typeof drawCallback(instance) === 'function'))) return;
    visualReadyReported = true;
    notifyAndroid('onPreviewVisualReady');
  });
}
function maybeReady() {
  if (window.__editKiroRuntimeHasError || readyReported) return;
  if ([...instances.values()].some(state => !state.ready)) return;
  if (!instances.size && !(config.documentMode && pageLoaded || config.moduleMode && moduleFinished)) return;
  sketchReady = true;
  document.getElementById('boot')?.remove();
  scheduleFit(true);
  notifyAndroid('onStatusChanged', hostPaused ? '一時停止中' : '実行中');
  hostFrame(() => {
    if (readyReported || window.__editKiroRuntimeHasError || [...instances.values()].some(state => !state.ready)) return;
    readyReported = true; notifyAndroid('onPreviewReady'); maybeVisualReady();
  });
}
async function afterSetup() {
  // p5 1 global lifecycle hooks bind `this` to window, while init/remove
  // and p5 2 bind it to the actual p5 instance.
  const instance = this === window ? window.p5?.instance || [...instances.keys()].find(value => value._isGlobal) || this : this;
  if (instance?._renderer?.contextReady) {
    try { await instance._renderer.contextReady; } catch (_) {}
  }
  const state = track(instance); state.ready = true;
  sketchReady = true;
  if (hostPaused) { const api = scope(instance); state.pausedLoop = api.isLooping?.() !== false; api.noLoop?.(); }
  const canvas = instanceCanvas(instance) || (instance._isGlobal && canvasEntries()[0]?.canvas);
  if (canvas && typeof MutationObserver === 'function' && !canvasSizeObservers.has(canvas)) {
    const observer = new MutationObserver(() => scheduleFit(true));
    observer.observe(canvas, { attributes: true, attributeFilter: ['width', 'height'] });
    canvasSizeObservers.set(canvas, observer);
  }
  scheduleFit(true); maybeReady();
}
function afterDraw() {
  const instance = this === window ? window.p5?.instance || [...instances.keys()].find(value => value._isGlobal) || this : this;
  track(instance).drawn = true;
  maybeVisualReady();
}
function removeInstance() {
  const canvas = instanceCanvas(this);
  canvasSizeObservers.get(canvas)?.disconnect(); canvasSizeObservers.delete(canvas);
  instances.delete(this); scheduleFit(true); maybeReady(); maybeVisualReady();
}
const hookedConstructors = new WeakSet();
function attachP5(constructor) {
  if (typeof constructor !== 'function' || !constructor.prototype || hookedConstructors.has(constructor)) return;
  hookedConstructors.add(constructor);
  if (typeof constructor.registerAddon === 'function') {
    constructor.registerAddon((p5, prototype, hooks) => {
      hooks.presetup = function () { track(this); };
      hooks.postsetup = afterSetup; hooks.remove = removeInstance;
      hooks.postdraw = afterDraw;
    });
  } else if (typeof constructor.prototype.registerMethod === 'function') {
    constructor.prototype.registerMethod('init', function () { track(this); });
    constructor.prototype.registerMethod('afterSetup', afterSetup);
    constructor.prototype.registerMethod('post', afterDraw);
    constructor.prototype.registerMethod('remove', removeInstance);
  }
  const origDraw = constructor.prototype._draw;
  if (typeof origDraw === 'function') {
    constructor.prototype._draw = async function (...args) {
      if (this._renderer?.contextReady) {
        try { await this._renderer.contextReady; } catch (_) {}
      }
      return origDraw.apply(this, args);
    };
  }
  const download = constructor.prototype.downloadFile;
  if (typeof download === 'function') constructor.prototype.downloadFile = function (data, name, extension) {
    const transfer = window.__editRinDownload(data, name, extension);
    return transfer || download.call(this, data, name, extension);
  };
  const createCanvas = constructor.prototype.createCanvas;
  if (typeof createCanvas === 'function') constructor.prototype.createCanvas = function (...args) {
    const webgpu = args[2] === 'webgpu';
    if (webgpu && !window.navigator?.gpu) throw new Error('この端末・WebViewではWEBGPUを利用できません。対応するGPUとブラウザーが必要です');
    const result = createCanvas.apply(this, args);
    if (webgpu && result?.catch) return result.catch(error => { throw new Error(`WEBGPUを初期化できませんでした: ${error.message}`); });
    return result;
  };
}
let loadedP5 = window.p5;
attachP5(loadedP5);
try {
  const property = Object.getOwnPropertyDescriptor(window, 'p5');
  if (!property || property.configurable) Object.defineProperty(window, 'p5', { configurable: true, enumerable: true,
    get: () => loadedP5, set: value => { loadedP5 = value; attachP5(value); } });
} catch (_) {}
document.addEventListener?.('load', event => { if (event.target?.tagName === 'SCRIPT') attachP5(window.p5); }, true);
let moduleFinished = false;
window.__editRinRegisterModuleCallbacks = callbacks => {
  for (const [name, callback] of Object.entries(callbacks || {})) if (typeof callback === 'function') window[name] = callback;
  if ((typeof callbacks?.setup === 'function' || typeof callbacks?.draw === 'function') && window.p5 && !window.p5.instance) new window.p5();
};
window.__editRinModuleReady = () => { moduleFinished = true; maybeReady(); };
const unlockAudio = async () => {
  if (!window.__editKiroSoundEnabled && typeof window.getAudioContext !== 'function') return;
  try {
    window.userStartAudio?.();
    if (typeof window.getAudioContext === 'function') await window.getAudioContext().resume();
    window.removeEventListener('pointerdown', unlockAudio, true);
  } catch (error) { window.__editKiroReportError(error.message); }
};
window.addEventListener('pointerdown', unlockAudio, true);
window.addEventListener('load', () => {
  pageLoaded = true;
  hostFrame(() => {
    if (window.__editKiroRuntimeHasError) { document.getElementById('boot')?.remove(); return; }
    attachP5(window.p5);
    if (config.documentMode || config.moduleMode) { maybeReady(); return; }
    if (!window.p5) window.__editKiroReportError('p5.js の初期化に失敗しました');
    else if (!instances.size && typeof window.setup !== 'function' && typeof window.draw !== 'function') {
      window.__editKiroReportError('setup() または draw() が見つかりません');
    }
  });
});

window.addEventListener('beforeunload', () => {
  document.getElementById('boot')?.remove();
  if (recordingSession) {
    const session = recordingSession;
    const recorder = session.recorder;
    notifyAndroid('abortRecordingTransfer', session.transferId);
    releaseRecording(session);
    if (recorder && recorder.state !== 'inactive') recorder.stop();
  }
  resizeObserver?.disconnect();
  for (const observer of canvasSizeObservers.values()) observer.disconnect();
  canvasSizeObservers.clear();
  cancelHostFrame(resizeFrame); cancelHostFrame(compositeFrame);
  for (const entry of artworkFrames.values()) if (entry.native) cancelHostFrame(entry.native);
  artworkFrames.clear();
}, { once: true });
})();
  
