/* The default JS/module runner. Real HTML documents include only p5_host.js. */
(() => {
  const token = window.__editRinRunToken;
  const config = window.__editRinProjectConfig || {};
  const version = /^2(?:\.|$)/.test(window.Android?.getP5Version?.(token) || '') ? '2' : '1';
  const attribute = value => String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
  const load = (url, module = false) => document.write(`<script ${module ? 'type="module" ' : ''}src="${attribute(url)}"><\/script>`);
  window.__editKiroSoundEnabled = window.Android?.isP5SoundEnabled?.(token) === true;
  load(config.p5Url || `p5-v${version}.min.js`);
  let libraries = {};
  try { libraries = JSON.parse(window.Android?.getWorkLibraries?.(token) || '{}'); } catch (_) {}
  window.__editRinLoadP5Addons = () => {
    // This parser stage runs after the core script has loaded. Custom URLs may
    // provide a different major version than the work's bundled-version setting.
    const actualVersion = String(window.p5?.VERSION || '');
    const actualMajor = actualVersion.split('.')[0];
    if (actualMajor === '2') load('p5.webgpu.js');
    if (window.__editKiroSoundEnabled) {
      if (actualMajor === '1' || actualMajor === '2') load(actualMajor === '1' ? 'p5.sound-v1.min.js' : 'p5.sound.min.js');
      else window.__editKiroReportError('p5.soundの対応を確認できません。p5.VERSIONを持つp5.js 1.xまたは2.xを指定してください');
    }
    if (libraries['p5.brush'] === '2.2.1') {
      if (actualMajor === '2') load('p5.brush-2.2.1.js');
      else window.__editKiroReportError('p5.brush 2.2.1 requires p5.js 2.x');
    }
    if (libraries['matter-js'] === '0.20.0') load('matter-0.20.0.min.js');
    for (const library of config.libraries || []) load(library.url, library.type === 'module');
  };
  document.write('<script>window.__editRinLoadP5Addons();<\/script>');
  if (config.moduleMode && config.moduleEntry) {
    load(config.moduleEntry, true);
  } else if (config.classicScripts?.length) {
    for (const url of config.classicScripts) load(url);
  } else {
    // Execute after parser-blocking libraries, preserving legacy combined source lines.
    document.write('<script src="__edit-rin__/p5_sketch.js"><\/script>');
  }
})();
