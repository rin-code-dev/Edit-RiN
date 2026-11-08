// Exercises the actual bundled template source in the offline p5 runner.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const text = fs.readFileSync(path.join(root, 'android/app/src/main/java/com/hikariatelier/app/WorkTemplate.kt'), 'utf8');
const names = ['CIRCLE','ANIMATION','INPUT','PARAMETERS','PATTERN','PARTICLES','WEBGL','SHADER','MATTER','WEBGPU'];
function source(name) {
  const id = name === 'PARAMETERS' ? 'WAVE_PARAMETERS' : name;
  if (name === 'CIRCLE') {
    const value = text.match(/internal const val CIRCLE_TEMPLATE = ("(?:[^"\\]|\\.)*")/);
    assert(value); return JSON.parse(value[1]);
  }
  const value = text.match(new RegExp('internal val '+id+'_TEMPLATE = """([\\s\\S]*?)"""\\.trimIndent\\(\\)'));
  assert(value, id); return value[1].replace(/^\n/, '').replace(/\n\s*$/, '') + '\n';
}
const sources = Object.fromEntries(names.map(name => [name, source(name)]));
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const match = /^\/project\/preview\/([^/]+)$/.exec(url.pathname.replace('/__edit-rin__/', '/'));
  if (!match) { res.writeHead(404); return res.end(); }
  const file = path.join(root, 'www', match[1]);
  try { const data = fs.readFileSync(file); res.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : 'text/html'); res.end(data); }
  catch { res.writeHead(404); res.end(); }
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_BIN || '/opt/brave.com/brave/brave', headless: true,
    args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--mute-audio'] });
  try {
    for (const name of names) for (const mode of ['fixed', 'responsive']) {
      const original = sources[name];
      const code = mode === 'fixed' ? original.split('\nfunction windowResized()')[0].trimEnd().replace('createCanvas(windowWidth, windowHeight', 'createCanvas(400, 400') + '\n' : original;
      const page = await browser.newPage({ viewport: { width: 400, height: 400 } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.addInitScript(({ code, name }) => {
        window.__template = { ready: false, errors: [] };
        window.Android = {
          getSketchCode: () => code, getP5Version: () => '2.3.4', isP5SoundEnabled: () => false,
          getWorkLibraries: () => JSON.stringify(name === 'MATTER' ? { 'matter-js': '0.20.0' } : {}),
          getWorkParameters: () => JSON.stringify({ size: 0.3, ink: '#A8C7FA' }), getWorkShaders: () => '{}',
          getProjectConfig: () => JSON.stringify({ thumbnailOnly: true, documentMode: false, moduleMode: false, classicScripts: [], libraries: [] }),
          onPreviewVisualReady: () => { window.__template.ready = true; },
          onError: (_, error) => window.__template.errors.push(error),
          onRuntimeError: (_, error) => window.__template.errors.push(error),
          onRuntimeErrorFile: (_, error) => window.__template.errors.push(error)
        };
      }, { code, name });
      await page.goto(`http://127.0.0.1:${server.address().port}/project/preview/p5_runner.html`);
      try {
        await page.waitForFunction(() => window.__template.ready || window.__template.errors.length, null, { timeout: 15000 });
        const state = await page.evaluate(() => ({ ready: window.__template.ready, errors: window.__template.errors, canvas: !!document.querySelector('canvas') }));
        assert.equal(state.ready, true, `${name}/${mode}: ${state.errors.join('; ')}`);
        assert.equal(state.canvas, true);
        const frame = await page.evaluate(() => frameCount);
        await page.waitForFunction(frame => frameCount > frame + 2, frame);
        await page.setViewportSize({ width: 320, height: 480 });
        await page.waitForFunction(({ mode }) => width === (mode === 'fixed' ? 400 : 320) && height === (mode === 'fixed' ? 400 : 480), { mode });
        assert.deepEqual(await page.evaluate(() => window.__template.errors), []);
        await page.evaluate(() => { window.__template.instance = window.p5.instance; window.rinDispose(); });
        const stoppedFrame = await page.evaluate(() => window.__template.instance.frameCount);
        await page.waitForTimeout(150);
        assert.equal(await page.evaluate(() => window.__template.instance.frameCount), stoppedFrame);
        assert.equal(await page.locator('canvas').count(), 0);
        assert.deepEqual(errors, []);
        console.log(`PASS ${name} ${mode}`);
      } finally { await page.close(); }
    }
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
