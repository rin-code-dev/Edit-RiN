// Real Chromium checks with synthetic media; does not verify Android permissions/hardware.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const fs = require('node:fs'), http = require('node:http'), path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../www');
const samples = ['Halo', 'Shapes', 'Touch', 'Gravity', 'wave Parameter', 'WebGPU', 'Sound', 'Camera', 'Microphone', 'Sensor'];
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  try {
    const relative = path.basename(url.pathname);
    const file = path.join(root, relative);
    res.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : 'text/html');
    res.end(fs.readFileSync(file));
  } catch (_) { res.statusCode = 404; res.end('missing'); }
});
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || '/opt/brave.com/brave/brave',
    headless: true, args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
      '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
  try {
    const context = await browser.newContext({ viewport: { width: 600, height: 600 } });
    await context.grantPermissions(['camera', 'microphone'], { origin });
    for (const version of (process.env.SAMPLE_VERSIONS || '2.3.4,1.11.5').split(',')) {
      for (const name of samples.filter(name => version === '2.3.4' || name !== 'WebGPU')) {
        const page = await context.newPage();
        const source = fs.readFileSync(path.join(root, 'samples', name + '.js'), 'utf8');
        await page.addInitScript(({ source, version }) => {
          window.errors = []; window.ready = false;
          const values = {};
          for (const m of source.matchAll(/\/\/ @rin number (\w+) "[^"]+" \S+ \S+ (\S+)/g)) values[m[1]] = Number(m[2]);
          for (const m of source.matchAll(/\/\/ @rin color (\w+) "[^"]+" (#[0-9a-fA-F]+)/g)) values[m[1]] = m[2];
          window.Android = {
            getSketchCode: () => source, getP5Version: () => version,
            isP5SoundEnabled: () => false, getWorkLibraries: () => '{}', getWorkParameters: () => JSON.stringify(values), getWorkShaders: () => '{}',
            onPreviewReady: () => { ready = true; }, onStatusChanged: (_, status) => { if (status === '実行中') ready = true; },
            onError: (_, error) => errors.push(error), onRuntimeError: (_, error) => errors.push(error)
          };
        }, { source, version });
        const runtimeErrors = [];
        page.on('pageerror', error => runtimeErrors.push(String(error)));
        await page.goto(origin + '/project/sample-test/p5_runner.html');
        await page.waitForFunction(() => ready || errors.length, {}, { timeout: 20000 });
        assert.deepEqual(await page.evaluate(() => errors), [], name);
        if (name === 'Sound') {
          await page.mouse.move(360, 240); await page.mouse.down();
          await page.waitForFunction(() => active && audio?.state === 'running');
          await page.mouse.up(); assert.equal(await page.evaluate(() => active), false);
        } else if (name === 'Camera' || name === 'Microphone') {
          await page.mouse.click(300, 300); await page.waitForFunction(() => started, {}, { timeout: 10000 });
          await page.waitForTimeout(200);
          if (process.env.SAMPLE_ARTIFACT_DIR && version === '2.3.4') {
            fs.mkdirSync(process.env.SAMPLE_ARTIFACT_DIR, { recursive: true });
            await page.locator('canvas.p5Canvas').screenshot({ path: path.join(process.env.SAMPLE_ARTIFACT_DIR, name + '.png') });
          }
          assert.deepEqual(await page.evaluate(() => errors), [], name);
          await page.mouse.click(300, 300); assert.equal(await page.evaluate(() => started), false);
        } else if (name === 'Shapes') {
          await page.mouse.click(300, 300); assert.equal(await page.evaluate(() => paletteIndex), 1);
        } else if (name === 'Touch') {
          await page.mouse.click(420, 200); assert.ok(await page.evaluate(() => ripples.some(r => r.x === 420)));
        }
        await page.waitForTimeout(700);
        assert.deepEqual(await page.evaluate(() => errors), [], name);
        if (process.env.SAMPLE_ARTIFACT_DIR && version === '2.3.4' && name !== 'Camera' && name !== 'Microphone') {
          fs.mkdirSync(process.env.SAMPLE_ARTIFACT_DIR, { recursive: true });
          await page.locator('canvas.p5Canvas').screenshot({ path: path.join(process.env.SAMPLE_ARTIFACT_DIR, name + '.png') });
        }
        await page.evaluate(() => pauseSketch());
        await page.waitForTimeout(300);
        if (name === 'Sound' || name === 'Microphone') assert.equal(await page.evaluate(() => audio?.state), 'suspended');
        await page.evaluate(() => resumeSketch());
        assert.deepEqual(runtimeErrors, [], name + ' browser runtime');
        console.log('PASS', version, name);
        await page.close();
      }
    }
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; server.close(); });
