// NODE_PATH=/path/to/node_modules BROWSER_BIN=/path/to/chromium node tests/guide.browser.cjs
// GUIDE_ARTIFACT_DIR=/path/to/output saves screenshots for visual review.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {spawnSync} = require('node:child_process');
const {chromium} = require('playwright');
const root = path.resolve(__dirname, '../website');
const repo = path.resolve(__dirname, '..');
const content = JSON.parse(fs.readFileSync(path.join(repo, 'scripts/guide-content.json')));
const topics = content.chapters.flatMap(c => c.topics);
const legacy = ['samples','create','imports','input-tools','number-scrub','fold-wrap','search-format','diagnostics','p5-reference','run-pause','layout','pip-controls','preview-ratio','watch','inline','rin-declarations','parameter-rules','parameter-panel','project-files','runtime-versions','loading','limits','asset-inspector','bundled-libraries','canvas-modes','webgpu','touch-input','instances','camera','sound','microphone','orientation','file-fullscreen','save-history','draft-recovery','organize','backup','png','recording','project-export','downloads','tweet-golf','tweet-options','appearance','tours-updates','troubleshooting'];
const server = http.createServer((req, res) => {
  const target = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
  if (!target.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
  fs.readFile(target, (error, data) => {
    if (error) { res.writeHead(404); return res.end(); }
    res.setHeader('Content-Type', ({'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8','.woff2':'font/woff2'})[path.extname(target)] || 'application/octet-stream');
    res.end(data);
  });
});
(async () => {
  const generated = spawnSync('python3', ['scripts/build-guide.py', '--check'], {cwd: repo, encoding: 'utf8'});
  assert.equal(generated.status, 0, generated.stderr + generated.stdout);
  for (const topic of topics) {
    assert(topic.sources.length, `${topic.id} has implementation evidence`);
    for (const source of topic.sources) {
      const file = source.startsWith('@') ? path.join(repo, source.slice(1)) : path.join(repo, 'android/app/src/main/java/com/hikariatelier/app', source);
      assert(fs.existsSync(file), `${topic.id}: source exists: ${source}`);
    }
    for (const example of topic.examples || []) {
      const moduleCode = /^(import |export )/m.test(example.code);
      const parsed = spawnSync(process.execPath, ['--check', ...(moduleCode ? ['--input-type=module'] : [])], {input: example.code, encoding:'utf8'});
      assert.equal(parsed.status, 0, `${topic.id}: ${parsed.stderr}`);
    }
  }
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({headless:true, ...(process.env.BROWSER_BIN ? {executablePath:process.env.BROWSER_BIN} : {}), args:['--no-sandbox','--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
    const context = await browser.newContext({viewport:{width:1440,height:1000}});
    const page = await context.newPage();
    const errors = [], requests = [], failedResponses = [];
    page.on('pageerror', error => errors.push(String(error)));
    page.on('request', request => requests.push(request.url()));
    page.on('response', response => { if (response.status() >= 400) failedResponses.push([response.status(),response.url()]); });
    await context.addInitScript(() => {
      if (navigator.mediaDevices) navigator.mediaDevices.getUserMedia = () => { throw Error('Unexpected media request'); };
      window.open = () => { throw Error('Unexpected popup'); };
    });
    const url = `http://127.0.0.1:${server.address().port}/guide.html`;
    const goto = async id => { await page.goto(url + (id ? '#' + id : '')); await page.locator('.js').waitFor(); };
    const open = async id => {
      await page.evaluate(id => document.querySelector(`a[href="#${id}"]`).click(), id);
      await page.locator(`#${id}`).waitFor({state:'visible'});
    };
    const click = async (host, name) => {
      // Follow the app's settings tray before operating the relevant sheet.
      const target=host.locator(`[data-action="${name}"]`);
      if(!await target.isVisible()) {
        const routes={record:'show-record',png:'show-png',tag:'show-tag',move:'show-move',resolve:'show-runtime'};
        if(routes[name])await host.locator(`[data-ui="${routes[name]}"]`).click();
      }
      await target.click();
    };
    const range = (host, name, value) => host.locator(`[data-control="${name}"]`).evaluate((el,v) => { el.value = String(v); el.dispatchEvent(new Event('input',{bubbles:true})); }, value);
    const sim = kind => page.locator(`[data-sim="${kind}"]`);
    const live = kind => page.locator(`[data-live="${kind}"]`);
    const frames = host => host.getAttribute('data-frames').then(Number);
    const pixels = host => host.getAttribute('data-pixels').then(JSON.parse);
    const start = async kind => {
      const host = live(kind);
      await host.scrollIntoViewIfNeeded();
      await click(host, 'start');
      await page.waitForFunction(kind => Number(document.querySelector(`[data-live="${kind}"]`).dataset.frames) > 0, kind, {timeout:20000});
      assert.equal(await host.locator('iframe').getAttribute('sandbox'), 'allow-scripts');
      return host;
    };
    await goto();
    assert.equal(await page.locator('[data-topic]').count(), topics.length);
    assert.equal(await page.locator('[data-chapter]:visible').count(), 1);
    assert.equal(await page.locator('iframe').count(), 0, 'libraries are lazy; no initial iframes');
    assert(!requests.some(r => /vendor\//.test(r)), 'libraries are not downloaded until Run');
    assert.equal(await page.locator('main [lang=en]:visible').count(), 0);
    assert.deepEqual(await page.evaluate(() => {
      const ids=[...document.querySelectorAll('[id]')].map(el=>el.id);
      return ids.filter((id,i)=>ids.indexOf(id)!==i);
    }), [], 'unique anchors');
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('a[href^="#"]')].filter(a => !document.getElementById(a.hash.slice(1))).map(a=>a.hash)), [], 'valid anchors');
    // Every old public feature and chapter URL still opens its chapter and topic.
    for (const id of [...legacy, 'quickstart','editor','preview-pip','parameters','files-assets','drawing','device','storage-safety','export-media','settings']) {
      await open(id);
      assert(await page.locator(`#${id}`).isVisible(), `legacy #${id}`);
      assert.equal(await page.locator('[data-chapter]:visible').count(), 1);
    }
    console.log('PASS: static content and legacy anchors');
    // Search results span all chapters, include context, and restore after browser Back.
    await goto();
    await page.locator('#guide-search').fill('deviceShaken');
    assert(await page.locator('#result-list a[href="#orientation"]').isVisible());
    assert(await page.locator('#result-list a small').first().innerText());
    assert(await page.locator('#result-list a p').first().innerText());
    await page.evaluate(() => scrollTo(0, 100));
    await page.waitForTimeout(180);
    await page.locator('#result-list a[href="#orientation"]').click();
    assert(await page.locator('#orientation').isVisible());
    await page.goBack();
    await page.waitForTimeout(200);
    assert.equal(await page.locator('#guide-search').inputValue(), 'deviceShaken');
    assert(await page.locator('#quickstart').isVisible());
    await page.goForward();
    assert(await page.locator('#orientation').isVisible());
    await page.locator('#guide-search').fill('unmatched-feature-1234');
    assert.match(await page.locator('#search-status').innerText(), /一致する項目がありません/);
    await page.locator('#clear-search').click();
    assert.equal(await page.locator('#search-results').isVisible(), false);
    // Clipboard failure leaves selectable source instead of losing the example.
    await open('watch');
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', {configurable:true,value:{writeText:async s=>{window.__copied=s;}}}));
    await page.locator('#watch .copy').click();
    assert.match(await page.evaluate(() => window.__copied), /let x/);
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', {configurable:true,value:{writeText:async()=>{throw Error('blocked');}}}));
    await page.locator('#watch .copy').click();
    assert.match(await page.evaluate(() => getSelection().toString()), /function draw/);
    // Live code: verify actual pixels, not just an iframe or loading message.
    await open('basic-drawing');
    const basic = await start('basic');
    assert((await pixels(basic)).every(p => p.length === 4 && p[3] > 0));
    await click(basic, 'stop');
    await page.waitForTimeout(250); const stopped = await frames(basic);
    await page.waitForTimeout(300); assert.equal(await frames(basic), stopped, 'Stop freezes drawing');
    await click(basic, 'start');
    await page.waitForFunction(n => Number(document.querySelector('[data-live=basic]').dataset.frames)>n, stopped);
    await open('run-pause');
    assert.equal(await basic.locator('iframe').count(), 0, 'leaving chapter destroys runtime');
    const run = await start('run');
    const smallPixels = await pixels(run);
    await range(run, 'diameter', 200);
    await page.waitForTimeout(250);
    assert.deepEqual(await pixels(run), smallPixels, 'manual source edit waits for Run');
    await click(run, 'start');
    await page.waitForFunction(() => {const p=JSON.parse(document.querySelector('[data-live=run]').dataset.pixels || '[]')[2];return p && p[0]<220 && p[1]<150;}, null, {timeout:20000});
    await run.locator('[data-control=auto]').check();
    await range(run, 'diameter', 20);
    await page.waitForFunction(() => JSON.parse(document.querySelector('[data-live=run]').dataset.pixels || '[]')[2]?.[1]>220, null, {timeout:20000});
    // Cancelling Auto-Run or stopping must also cancel a queued rerun.
    const runningFrame=await run.locator('iframe').elementHandle();
    await run.evaluate(host=>{
      const diameter=host.querySelector('[data-control=diameter]'),auto=host.querySelector('[data-control=auto]');
      diameter.value='180';diameter.dispatchEvent(new Event('input',{bubbles:true}));
      auto.checked=false;auto.dispatchEvent(new Event('input',{bubbles:true}));
    });
    await page.waitForTimeout(500);
    assert(await runningFrame.evaluate(el=>el.isConnected),'turning Auto-Run off cancels its pending rerun');
    await run.evaluate(host=>{
      const auto=host.querySelector('[data-control=auto]');auto.checked=true;auto.dispatchEvent(new Event('input',{bubbles:true}));
      host.querySelector('[data-action=stop]').click();
    });
    await page.waitForTimeout(500);
    assert(await runningFrame.evaluate(el=>el.isConnected),'Stop cancels the pending rerun');
    assert.equal(await run.getAttribute('data-runtime'),'paused');
    console.log('PASS: live drawing, manual Run, and Auto-Run');
    // Parameters use all five inline types. Source and running values agree.
    await open('inline');
    const inline = await start('inline');
    await range(inline, 'count', 1); await range(inline, 'size', 200);
    await inline.locator('[data-control=ink]').evaluate(el => { el.value='#123456'; el.dispatchEvent(new Event('input',{bubbles:true})); });
    await inline.locator('[data-value=square]').click();
    await page.waitForFunction(() => {const p=JSON.parse(document.querySelector('[data-live=inline]').dataset.pixels)[1];return p && p.slice(0,3).every((n,i)=>Math.abs(n-[18,52,86][i])<4);});
    assert.match(await page.locator('[data-example=inline] code').innerText(), /let ink = '#123456'/);
    await inline.locator('[data-control=pin-size]').check();
    await click(inline, 'random'); assert.equal(await inline.locator('[data-control=size]').inputValue(), '200');
    await click(inline, 'set-defaults');
    assert(await inline.locator('[data-action=random]').evaluate(el=>el===document.activeElement),'hidden default buttons preserve a visible focus target');
    await range(inline, 'size', 20); await click(inline, 'defaults');
    assert.equal(await inline.locator('[data-control=size]').inputValue(), '200');
    await inline.locator('[data-control=moving]').check();
    await click(inline, 'reset');
    assert.equal(await inline.locator('iframe').count(), 0);
    assert.equal(await inline.locator('[data-control=size]').inputValue(), '100');
    assert.equal(await inline.locator('[data-control=moving]').isChecked(), false);
    await open('rin-declarations');
    const params = await start('params');
    await range(params, 'count', 1); await range(params, 'size', 200);
    const beforeLanguage = await frames(params);
    const hash = await page.evaluate(()=>location.hash);
    await page.locator('[data-lang=en]').click();
    assert.equal(await page.locator('html').getAttribute('lang'), 'en');
    assert.equal(await page.evaluate(()=>location.hash), hash);
    assert.equal(await params.locator('[data-control=size]').inputValue(), '200');
    assert.equal(await page.locator('main [lang=ja]:visible').count(), 0);
    assert(await frames(params) >= beforeLanguage, 'language switch retains live runtime');
    await page.locator('[data-lang=ja]').click();
    await params.locator('[data-action=compare]').focus(); await page.keyboard.down('Space');
    assert.equal(await params.locator('[data-action=compare]').getAttribute('aria-pressed'), 'true');
    await page.keyboard.up('Space');
    assert.equal(await params.locator('[data-action=compare]').getAttribute('aria-pressed'), 'false');
    await open('watch');
    const watch = await start('watch');
    await page.waitForFunction(()=>/\d/.test(document.querySelector('[data-live=watch] [data-watch]').textContent));
    const v=await watch.locator('[data-watch]').innerText(); await page.waitForTimeout(350); assert.notEqual(await watch.locator('[data-watch]').innerText(),v);
    for (const [id,kind] of [['canvas-modes','webgl'],['shaders','shader'],['physics','physics']]) {
      await open(id); const host=await start(kind);
      const colors=await pixels(host); assert(colors.some(c=>c[3]>0),`${kind} draws nontransparent pixels`);
      if (kind==='shader') assert.notDeepEqual(colors[0],colors[1],'shader produces varying colors');
      if (kind==='physics') {
        const y=Number(await host.getAttribute('data-physics-y')); await page.waitForTimeout(400);
        assert(Number(await host.getAttribute('data-physics-y'))>y,'Matter engine advances the falling body');
      }
      await click(host,'stop');
    }
    // Tunable drawing examples execute the exact updated code shown above the preview.
    for (const [id,kind,value,expected] of [['basic-drawing','basic',50,'* 50;'],['canvas-modes','webgl',150,'box(150)'],['shaders','shader',1,'uv.x, 1.00, uv.y'],['physics','physics',.2,'restitution: 0.2']]) {
      await open(id); const host=live(kind); await range(host,'tuning',value);
      assert((await page.locator(`#${id} .code-example code`).innerText()).includes(expected));
      await click(host,'start'); await page.waitForFunction(k=>Number(document.querySelector(`[data-live=${k}]`).dataset.frames)>0,kind);
      if(kind==='shader')assert((await pixels(host)).every(p=>p[1]>250),'green channel changes actual shader pixels');
      assert.equal(await host.getAttribute('data-runtime'),'running');
      const runtimeSource=await host.locator('iframe').getAttribute('srcdoc');
      await click(host,'stop'); await page.waitForFunction(k=>document.querySelector(`[data-live=${k}]`).dataset.runtime==='paused',kind);
      await click(host,'start'); assert.equal(await host.locator('iframe').getAttribute('srcdoc'),runtimeSource,'unchanged code resumes the same runtime');
      await click(host,'reset'); assert.equal(await host.getAttribute('data-runtime'),'idle');
      assert.equal(await host.locator('iframe').count(),0);
    }
    await open('touch-input'); const touch=await start('touch');
    const frame=touch.frameLocator('iframe'); const canvasBox=await frame.locator('canvas').boundingBox(); await frame.locator('canvas').click({position:{x:canvasBox.width/2,y:canvasBox.height/2}});
    await page.waitForFunction(()=>{const p=JSON.parse(document.querySelector('[data-live=touch]').dataset.pixels)[1];return p && p.slice(0,3).every((n,i)=>Math.abs(n-[66,107,93][i])<4);});
    console.log('PASS: parameters, watch, WebGL, shader, physics, and touch');
    // Simulated controls change state without writing files or requesting hardware.
    await open('samples'); await click(sim('gallery'),'weave'); await click(sim('gallery'),'copy'); assert.match(await sim('gallery').innerText(),/自分の作品.*Weave/);
    await open('input-tools'); await range(sim('editor'),'number',150); assert.match(await sim('editor').locator('[data-source]').innerText(),/150/); await click(sim('editor'),'undo'); assert.match(await sim('editor').locator('[data-source]').innerText(),/100/); await click(sim('editor'),'fold'); assert.match(await sim('editor').locator('[data-source]').innerText(),/…/);
    await open('pip-controls'); for (const a of ['corner','size','ghost','minimize']) await click(sim('pip'),a); assert(await sim('pip').locator('.pip-window.large.ghost.minimized').isVisible());
    await open('project-files'); await sim('files').locator('[data-ui=show-runtime]').click(); await sim('files').locator('[data-control=mode]').selectOption('module'); await click(sim('files'),'resolve'); assert.match(await sim('files').locator('[data-result]').innerText(),/lib\/math.mjs/);
    await open('camera'); await click(sim('camera'),'camera'); await click(sim('camera'),'mirror'); assert(await sim('camera').locator('.camera-diagram.mirror:not(.off)').isVisible());
    await open('microphone'); await range(sim('mic'),'level',.75); assert.equal(await sim('mic').locator('.meter-fill').evaluate(el=>el.style.width),'75%');
    await open('orientation'); await range(sim('sensors'),'ry',30); await click(sim('sensors'),'shake'); assert.match(await sim('sensors').locator('[data-result]').innerText(),/deviceShaken/);
    await open('save-history'); const history=sim('history'); await range(history,'value',150); await click(history,'save'); await click(history,'snapshot'); await range(history,'value',60); await click(history,'revert'); assert.equal(await history.locator('[data-edited]').innerText(),'150'); await range(history,'value',80); await click(history,'restore-0'); assert.equal(await history.locator('[data-edited]').innerText(),'150');
    await open('draft-recovery'); await click(sim('recovery'),'recover'); await click(sim('recovery'),'fail'); assert.match(await sim('recovery').locator('[data-result]').innerText(),/150/); await click(sim('recovery'),'retry'); assert.match(await sim('recovery').locator('[data-result]').innerText(),/模擬保存/);
    await open('png'); const exp=sim('export'); await exp.locator('[data-ui=show-png]').click(); await exp.locator('[data-value="4"]').click(); await click(exp,'png'); assert.match(await exp.locator('[data-result]').innerText(),/1600 × 1120/); await click(exp,'record'); await click(exp,'finish'); await click(exp,'fail'); assert.match(await exp.locator('[data-result]').innerText(),/保留/); await click(exp,'destination'); assert.match(await exp.locator('[data-result]').innerText(),/模擬保存/);
    await open('organize'); await sim('organize').locator('[data-control=a]').check(); await click(sim('organize'),'move'); await click(sim('organize'),'tag'); assert.match(await sim('organize').locator('[data-location]').innerText(),/A → 練習.*#study/); await click(sim('organize'),'delete-folder'); assert.match(await sim('organize').locator('[data-location]').innerText(),/A → 未分類/);
    await open('backup'); await click(sim('backup'),'backup'); await click(sim('backup'),'clear'); await click(sim('backup'),'restore'); assert.match(await sim('backup').locator('[data-result]').innerText(),/作品A・作品B/);
    await open('share-cards'); await sim('share').locator('[data-control=author]').fill('Artist'); assert.equal(await sim('share').locator('[data-author]').innerText(),'Artist'); await sim('share').locator('[data-control=show-code]').uncheck(); assert.equal(await sim('share').locator('[data-card-code]').isVisible(),false);
    await sim('share').locator('[data-value=sumi]').click(); assert(await sim('share').locator('[data-share-card].sumi-card').isVisible());
    await sim('share').locator('[data-value=light]').click(); assert(await sim('share').locator('[data-share-card].light-card').isVisible());
    // Every simulation resets and stays explicitly labelled as a simulation.
    for (const host of await page.locator('[data-sim]').all()) {
      const id=await host.evaluate(el=>el.closest('[data-topic]').id); await open(id); await click(host,'reset'); assert.match(await host.locator('.demo-tag').innerText(),/模擬操作/);
    }
    // Redesigned mockups expose the result visually, and Reset clears those states.
    await open('samples'); const gallery=sim('gallery');
    await click(gallery,'weave'); await click(gallery,'copy');
    assert(await gallery.locator('.gallery-copy .mini-art.weave').isVisible());
    assert.equal(await gallery.locator('.practice-steps .complete').count(),3);
    await click(gallery,'reset'); assert.equal(await gallery.locator('.gallery-copy').isVisible(),false);
    assert.equal(await gallery.locator('.practice-steps .complete').count(),0);
    await open('project-files'); const files=sim('files'); await files.locator('[data-ui=show-files]').click();
    await files.locator('[data-file="2"]').click(); assert.match(await files.locator('[data-file-preview]').innerText(),/export const twice/);
    assert.equal(await files.locator('[data-file="2"]').getAttribute('aria-pressed'),'true');
    await click(files,'reset'); assert.match(await files.locator('[data-file-preview]').innerText(),/createCanvas/);
    await open('save-history'); await range(history,'value',180);
    assert.equal(await history.getAttribute('data-dirty'),'true');
    assert.notEqual(await history.locator('[data-edit-circle]').getAttribute('style'),await history.locator('[data-save-circle]').getAttribute('style'));
    await click(history,'save'); assert.equal(await history.getAttribute('data-dirty'),'false');
    assert.equal(await history.locator('[data-edit-circle]').getAttribute('style'),await history.locator('[data-save-circle]').getAttribute('style'));
    await open('draft-recovery'); await click(sim('recovery'),'recover'); await click(sim('recovery'),'fail');
    assert(await sim('recovery').locator('.save-failed').isVisible()); await click(sim('recovery'),'retry'); assert.equal(await sim('recovery').locator('.save-failed').count(),0);
    await open('backup'); const backup=sim('backup'); await click(backup,'backup'); await click(backup,'clear');
    assert(await backup.locator('[data-collection].empty').isVisible()); assert.equal(await backup.locator('[data-collection] > span').innerText(),'空の移行先'); assert(!/A · B/.test(await backup.locator('[data-collection]').innerText())); assert(await backup.locator('[data-archive].ready').isVisible());
    await click(backup,'restore'); assert.equal(await backup.locator('[data-collection].empty').count(),0);
    await click(backup,'reset'); assert.equal(await backup.locator('[data-archive].ready').count(),0);
    await open('png'); await exp.locator('[data-ui=show-png]').click(); await exp.locator('[data-value="4"]').click(); await click(exp,'png');
    assert.match(await exp.locator('.export-receipt').innerText(),/1600 × 1120/);
    await click(exp,'record'); assert(await exp.locator('.record-indicator').isVisible()); await click(exp,'finish');
    assert.equal(await exp.locator('.record-indicator').isVisible(),false);
    await click(exp,'fail'); assert(await exp.locator('.export-receipt.pending').isVisible());
    await click(exp,'retry'); assert.equal(await exp.locator('.export-receipt.pending').count(),0);
    await click(exp,'reset'); assert.equal(await exp.locator('.export-receipt').isVisible(),false);
    await open('camera'); await click(sim('camera'),'reset'); await click(sim('camera'),'camera');
    assert.equal(await sim('camera').locator('.practice-steps .complete').count(),1);
    await click(sim('camera'),'camera'); assert.equal(await sim('camera').locator('.practice-steps .complete').count(),2);
    await open('rin-declarations'); await live('params').locator('[data-control=moving]').check();
    assert(await live('params').getByText('回転する',{exact:true}).isVisible());
    assert.equal(await live('params').locator('.parameter-pin').count(),5);
    // Device-style controls: sheets, picker, parameter stepping, and snapshot diff.
    await open('rin-declarations'); const appParams=live('params');
    await click(appParams,'reset');
    assert(await page.locator('symbol[id^="app-icon-"]').count()>=24,'inline SVG sprite symbols are present');
    await page.locator('[data-lang=en]').click();
    assert.equal(await appParams.locator('.app-color-swatch').getAttribute('aria-label'),'Edit color');
    await page.locator('[data-lang=ja]').click();
    assert.equal(await appParams.locator('.app-color-swatch').getAttribute('aria-label'),'色を編集');
    await appParams.locator('[data-control=size]').locator('..').locator('button').last().click();
    assert.equal(await appParams.locator('[data-control=size]').inputValue(),'110');
    await appParams.locator('.app-color-swatch').click();
    assert(await appParams.locator('dialog[open]').isVisible());
    await appParams.locator('[data-color-tab=rgb]').click();
    await appParams.locator('[data-picker-hex]').fill('#123456');
    await appParams.locator('.app-color-apply').click();
    await page.waitForFunction(()=>document.querySelector('[data-live=params] [data-control=ink]').value==='#123456');
    assert.equal(await appParams.locator('[data-control=ink]').inputValue(),'#123456');
    await appParams.locator('.app-color-swatch').click();
    await appParams.locator('[data-picker-hex]').fill('#654321');await appParams.locator('[data-picker-hex]').press('Enter');
    await page.waitForFunction(()=>document.querySelector('[data-live=params] [data-control=ink]').value==='#654321');
    await appParams.locator('.app-color-swatch').click();await appParams.locator('[data-picker-hex]').fill('invalid');
    await appParams.locator('[data-color-cancel]').click();
    assert.equal(await appParams.locator('[data-control=ink]').inputValue(),'#654321','Cancel discards invalid edits');
    await appParams.locator('.app-color-swatch').click(); await page.keyboard.press('Escape');
    assert.equal(await appParams.locator('dialog[open]').count(),0);
    await appParams.locator('[data-ui=close-parameters]').click(); assert.equal(await appParams.locator('.app-parameters').isVisible(),false);
    await appParams.locator('[data-ui=toggle-tools]').click(); assert(await appParams.locator('.app-parameters').isVisible());
    await open('project-files'); await files.locator('[data-ui=show-assets]').click(); await files.locator('[data-ui=asset-row]').click();
    assert(await files.locator('.app-asset-preview').isVisible());
    await files.locator('[data-ui=asset-preview]').click();
    assert.equal(await files.locator('.app-asset-preview').isVisible(),false);
    await files.locator('[data-ui=asset-preview]').click();
    assert(await files.locator('.app-asset-preview').isVisible());
    await files.locator('[data-ui=show-runtime]').click(); await files.locator('input[value="1.11.5"]').check(); await click(files,'resolve'); assert.match(await files.locator('[data-result]').innerText(),/1.11.5/);
    await open('save-history'); await click(history,'reset'); await range(history,'value',170); await click(history,'snapshot');
    await range(history,'value',60); await history.locator('[data-ui=view-diff]').click();
    assert.match(await history.locator('.app-diff-panel').innerText(),/60/); assert.match(await history.locator('.app-diff-panel').innerText(),/170/);
    await page.keyboard.press('Escape'); assert.equal(await history.locator('.app-diff-panel').isVisible(),false);
    await history.locator('[data-ui=view-diff]').click();
    await history.locator('[data-ui=close-diff]').click();
    assert(await history.locator('[data-ui=view-diff]').evaluate(el=>el===document.activeElement),'closing diff restores its opener');
    await click(history,'reset');
    assert(await history.locator('[data-action=reset]').evaluate(el=>el===document.activeElement),'reset retains keyboard focus');
    await open('samples');await click(gallery,'copy');await click(gallery,'palette');
    assert.match(await gallery.locator('.app-readonly').innerText(),/読み取り専用/);
    await open('png');await click(exp,'reset');await click(exp,'record');
    assert(await exp.locator('[data-action=finish]').evaluate(el=>el===document.activeElement),'recording moves focus to Stop');
    await exp.locator('[data-ui=show-record]').click();
    assert.equal(await exp.locator('[data-value=GIF]').isDisabled(),true,'recording locks the visible format control');
    await click(exp,'finish');
    assert(await exp.locator('[data-ui=show-record]').evaluate(el=>el===document.activeElement),'stopping restores the recording launcher');
    assert.equal(await exp.locator('[data-value=GIF]').isEnabled(),true);
    await exp.locator('[data-value=GIF]').click();
    assert.equal(await exp.locator('[data-control=format]').inputValue(),'GIF');
    const brokenLabels=await page.locator('.app-range input').evaluateAll(inputs=>inputs.filter(el=>!el.labels.length||el.labels[0].control!==el).length);
    assert.equal(brokenLabels,0,'range labels stay associated with their input after adding step buttons');
    assert.equal(await page.locator('.app-native-select:not([tabindex="-1"])').count(),0,'replacement selects do not create invisible tab stops');
    await page.emulateMedia({media:'print'});
    assert.equal(await page.locator('.enhanced:visible').count(),0,'print hides interactive controls');
    assert.equal(await page.locator('[data-chapter]:visible').count(),10,'print includes every chapter');
    await page.emulateMedia({media:'screen'});
    console.log('PASS: simulation state transitions and visual state indicators');
    // Responsive checks cover every topic in both languages and four required widths.
    const artifacts=process.env.GUIDE_ARTIFACT_DIR;
    if(artifacts)fs.mkdirSync(artifacts,{recursive:true});
    for(const width of [320,390,768,1440]) {
      await page.setViewportSize({width,height:1000}); await goto();
      assert((await page.locator('#guide-search').boundingBox()).y<240,'search is close to the top');
      for(const lang of ['ja','en']) {
        await page.locator(`[data-lang=${lang}]`).click();
        for(const chapter of content.chapters) {
          await open(chapter.id);
          assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`${chapter.id} ${lang} at ${width}`);
          for(const topic of chapter.topics) assert(await page.locator(`#${topic.id}`).isVisible());
        }
      }
      if(width<760) {
        await page.locator('#menu-toggle').click(); assert.equal(await page.locator('#menu-toggle').getAttribute('aria-expanded'),'true');
        await page.keyboard.press('Escape'); assert.equal(await page.locator('#menu-toggle').getAttribute('aria-expanded'),'false');
      }
      if(artifacts) {
        await page.addStyleTag({content:'.topbar{position:relative!important}'});
        await page.locator('[data-lang=ja]').click(); await goto();
        await page.screenshot({path:path.join(artifacts,`guide-${width}-home.png`)});
        await open('rin-declarations'); await live('params').scrollIntoViewIfNeeded();
        await live('params').screenshot({path:path.join(artifacts,`guide-${width}-parameters.png`)});
        await click(live('params'),'start'); await page.waitForFunction(()=>Number(document.querySelector('[data-live=params]').dataset.frames)>0);
        await live('params').screenshot({path:path.join(artifacts,`guide-${width}-parameters-running.png`)});
        for(const [id,kind,actions] of [['samples','gallery',['weave','copy']],['input-tools','editor',[]],['save-history','history',['snapshot']],['backup','backup',['backup','clear']],['png','export',['png']],['project-files','files',[]]]) {
          await open(id); for(const a of actions)await click(sim(kind),a);
          await sim(kind).screenshot({path:path.join(artifacts,`guide-${width}-${kind}.png`)});
        }
      }
    }
    // Motion preference yields a real, static canvas and still allows explicit Run.
    await page.emulateMedia({reducedMotion:'reduce'}); await open('basic-drawing'); const reduced=await start('basic');
    await page.waitForTimeout(400); const reducedFrames=await frames(reduced); await page.waitForTimeout(300); assert.equal(await frames(reduced),reducedFrames);
    assert((await pixels(reduced)).some(p=>p[3]>0));
    await page.emulateMedia({reducedMotion:'no-preference'});
    // Unknown hashes and malformed encodings fall back safely.
    await goto('%E0%A4%A'); assert(await page.locator('#quickstart').isVisible());
    // No JavaScript retains the complete manual and hides all interactive-only controls.
    const plainContext=await browser.newContext({javaScriptEnabled:false}); const plain=await plainContext.newPage(); await plain.goto(url);
    assert.equal(await plain.locator('[data-chapter]:visible').count(),10);
    assert.equal(await plain.locator('[data-topic]:visible').count(),topics.length);
    assert(await plain.locator('h3 [lang=en]').first().isVisible());
    assert.equal(await plain.locator('[data-live]:visible').count(),0); await plainContext.close();
    const blockedContext=await browser.newContext(); await blockedContext.addInitScript(()=>Object.defineProperty(window,'localStorage',{get(){throw Error('blocked');}})); const blocked=await blockedContext.newPage(); await blocked.goto(url); await blocked.locator('[data-lang=en]').click(); assert.equal(await blocked.locator('html').getAttribute('lang'),'en'); await blockedContext.close();
    const brokenContext=await browser.newContext(); await brokenContext.route('**/vendor/p5-2.3.4.min.js',route=>route.abort()); const broken=await brokenContext.newPage(); await broken.goto(url+'#basic-drawing'); await broken.locator('[data-live=basic] [data-action=start]').click(); await broken.waitForFunction(()=>document.querySelector('[data-live=basic] .demo-status').textContent.includes('実行できませんでした')); await brokenContext.close();
    assert.deepEqual(errors, [], 'no page errors');
    assert.deepEqual(failedResponses, [], 'all local assets load');
    assert(requests.every(req=>new URL(req).hostname==='127.0.0.1'),'no runtime external requests');
    console.log('PASS: 61 topics, 10 chapters, 46 legacy topic URLs, 9 real drawing examples, 13 simulations, pixel and physics checks, navigation/history/search, JA/EN × 4 widths, clipboard, reduced motion, no-JS, blocked storage, library failure.');
  } finally {
    if(browser)await browser.close();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
