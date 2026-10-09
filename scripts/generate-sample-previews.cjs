#!/usr/bin/env node
// Regenerate bundled canvas previews after changing a canonical sample.
// Requires Playwright and its Chromium headless browser. Run from the repository root.
const { chromium } = require('playwright');
const { createServer } = require('node:http');
const { readFileSync, mkdirSync, writeFileSync } = require('node:fs');
const { join, resolve } = require('node:path');
const { createHash } = require('node:crypto');
const root = resolve(__dirname, '..'), web = join(root, 'www');
const out = join(web, 'sample-previews');
const p5Version = readFileSync(join(root, 'android/app/src/main/java/com/hikariatelier/app/EditorSessionViewModel.kt'), 'utf8')
  .match(/const val P5_VERSION_CURRENT = "([^"]+)"/)[1];
const definitions = [
  ['halo', 'Halo.js'], ['shapes', 'Shapes.js'], ['touch', 'Touch.js'], ['gravity', 'Gravity.js'],
  ['wave-parameter', 'wave Parameter.js'], ['webgpu', 'WebGPU.js'], ['sound', 'Sound.js'],
  ['camera', 'Camera.js'], ['microphone', 'Microphone.js'], ['sensor', 'Sensor.js']
];
const server = createServer((req, res) => {
  const relative = decodeURIComponent(req.url.split('?')[0]).replace(/^\/project\/[^/]+\//, '').replace(/^__edit-rin__\//, '');
  if (!/^[\w .-]+$/.test(relative)) { res.writeHead(404); res.end(); return; }
  try {
    res.setHeader('Content-Type', relative.endsWith('.html') ? 'text/html; charset=utf-8' : 'application/javascript');
    res.end(readFileSync(join(web, relative)));
  } catch { res.writeHead(404); res.end(); }
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    mkdirSync(out, { recursive: true });
    const manifest = {};
    for (const [id, file] of definitions) {
      const source = readFileSync(join(web, 'samples', file), 'utf8');
      const parameters = {};
      for (const line of source.split('\n')) {
        const number = line.match(/^\/\/ @rin number (\w+) "[^"]+" \S+ \S+ (\S+)/);
        const color = line.match(/^\/\/ @rin color (\w+) "[^"]+" (#[\da-fA-F]+)/);
        if (number) parameters[number[1]] = Number(number[2]);
        if (color) parameters[color[1]] = color[2];
      }
      const page = await browser.newPage({ viewport: { width: 480, height: 480 }, deviceScaleFactor: 1 });
      await page.route('**/*', route => route.request().url().startsWith('http://127.0.0.1:') ? route.continue() : route.abort());
      await page.addInitScript(({ source, p5Version, parameters }) => {
        window.Android = {
          getSketchCode: () => source, getP5Version: () => p5Version,
          isP5SoundEnabled: () => false, getWorkLibraries: () => '{}',
          getWorkParameters: () => JSON.stringify(parameters), getWorkShaders: () => '{}',
          getProjectConfig: () => JSON.stringify({ thumbnailOnly: true }),
          onPreviewReady: () => { window.__sampleReady = true; },
          onError: (_, error) => { window.__sampleError = error; },
          onRuntimeError: (_, error) => { window.__sampleError = error; }
        };
      }, { source, p5Version, parameters });
      await page.goto(`http://127.0.0.1:${server.address().port}/project/${id}/p5_runner.html`);
      await page.waitForFunction(() => window.__sampleReady || window.__sampleError, { timeout: 15000 });
      const error = await page.evaluate(() => window.__sampleError);
      if (error) throw new Error(`${id}: ${error}`);
      const data = await page.evaluate(async () => {
        // Match the app's capture after readiness, with a real rendered frame rather than a timed sleep.
        await new Promise(resolve => requestAnimationFrame(resolve));
        const source = document.querySelector('canvas.p5Canvas') || document.querySelector('canvas');
        const copy = document.createElement('canvas');
        const scale = Math.min(1, 480 / Math.max(source.width, source.height));
        copy.width = Math.max(1, Math.round(source.width * scale));
        copy.height = Math.max(1, Math.round(source.height * scale));
        copy.getContext('2d').drawImage(source, 0, 0, copy.width, copy.height);
        return copy.toDataURL('image/png').split(',')[1];
      });
      writeFileSync(join(out, `${id}.png`), Buffer.from(data, 'base64'));
      manifest[id] = { source: file, sha256: createHash('sha256').update(source).digest('hex'), p5Version };
      console.log(`Captured ${id}`);
      await page.close();
    }
    writeFileSync(join(out, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
