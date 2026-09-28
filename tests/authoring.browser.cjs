// Optional integration check with a real Chromium browser (Node.js 22+).
// BROWSER_BIN=/path/to/chromium node tests/authoring.browser.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const kotlin = fs.readFileSync(path.join(root, 'android/app/src/main/java/com/hikariatelier/app/WorkTemplate.kt'), 'utf8');
const templates = {};
for (const name of ['WEBGL', 'SHADER', 'MATTER']) {
  templates[name] = kotlin.match(new RegExp(`internal val ${name}_TEMPLATE = """([\\s\\S]*?)"""`))[1].trim() + '\n';
}
templates.CIRCLE = JSON.parse(kotlin.match(/internal const val CIRCLE_TEMPLATE = (".*")/)[1]);
const runner = fs.readFileSync(path.join(root, 'www/p5_runner.html'), 'utf8');
const cases = ['1.11.5', '2.3.3'].flatMap(version => Object.keys(templates).flatMap(kind => ['fixed', 'responsive'].map(mode => ({ version, kind, mode }))));
const caseTest = `<script>
(async () => {
  let result = {case: window.__case, ok: false};
  try {
    for (let i = 0; i < 80 && !window.__test.ready && !window.__test.errors.length; i++) await new Promise(r => setTimeout(r, 50));
    if (!window.__test.ready) throw new Error('not ready: ' + window.__test.errors.join(';'));
    for (let i = 0; i < 80 && frameCount < 1; i++) await new Promise(r => setTimeout(r, 50));
    if (frameCount < 1) throw new Error('no first frame');
    window.pauseSketch();
    const canvas = document.querySelector('canvas.p5Canvas');
    const baseW = canvas.width, baseH = canvas.height, baseD = pixelDensity();
    const exports = [];
    for (const factor of [1, 2, 4]) {
      window.__test.exported = null;
      await window.__editKiroCaptureScreenshot(factor);
      const png = window.__test.exported;
      if (!png) throw new Error('export missing: ' + window.__test.errors.join(';'));
      const img = new Image(); img.src = png.data; await img.decode();
      if (img.width !== baseW * factor || img.height !== baseH * factor) throw new Error('wrong PNG dimensions');
      const sample = document.createElement('canvas'); sample.width = 16; sample.height = 16;
      const ctx = sample.getContext('2d'); ctx.drawImage(img, 0, 0, 16, 16);
      if (!ctx.getImageData(0, 0, 16, 16).data.some((v, i) => i % 4 === 3 && v > 0)) throw new Error('transparent export at '+factor+'x, frame='+frameCount);
      if (isLooping() || pixelDensity() !== baseD || canvas.width !== baseW || canvas.height !== baseH) throw new Error('paused state/density not restored');
      exports.push([img.width, img.height]);
    }
    window.resumeSketch();
    await window.__editKiroCaptureScreenshot(2);
    if (!isLooping() || pixelDensity() !== baseD) throw new Error('running state not restored');
    if (window.__test.errors.length) throw new Error(window.__test.errors.join(';'));
    result = {...result, ok: true, exports};
  } catch (e) { result.error = String(e); }
  parent.postMessage(result, '*');
})();
</script>`;
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/case') {
    const c = cases[Number(url.searchParams.get('i'))];
    let code = templates[c.kind];
    if (c.mode === 'fixed') code = code.split('\nfunction windowResized()')[0].replace('createCanvas(windowWidth, windowHeight', 'createCanvas(200, 120');
    const mock = `<script>
      window.__case = ${JSON.stringify(c)};
      window.__test = {ready:false,errors:[]};
      window.Android = {
        getSketchCode: () => ${JSON.stringify(code)},
        getP5Version: () => ${JSON.stringify(c.version)},
        isP5SoundEnabled: () => false,
        getWorkLibraries: () => ${JSON.stringify(c.kind === 'MATTER' ? '{"matter-js":"0.20.0"}' : '{}')},
        getWorkParameters: () => '{}', getWorkShaders: () => '{}',
        onStatusChanged: status => {if (status === '実行中') window.__test.ready = true;},
        onError: error => window.__test.errors.push(error), onRuntimeError: error => window.__test.errors.push(error),
        onCaptureError: error => window.__test.errors.push(error),
        onScreenshotExportReady: (data,width,height) => {window.__test.exported={data,width,height};}
      };
    </script>`;
    res.setHeader('Content-Type', 'text/html');
    return res.end(runner.replace('<head>', '<head>' + mock).replace('</body>', `<script>window.addEventListener('load', () => {const script=document.createElement('script');script.textContent=${JSON.stringify(caseTest.replace(/^<script>\n|<\/script>$/g, ''))};document.body.appendChild(script);});</script></body>`));
  }
  if (url.pathname === '/') {
    res.setHeader('Content-Type','text/html');
    return res.end(`<body><pre id="results">RUNNING</pre><script>
      const results=window.__browserResults=[]; let frame, index=0;
      function next(){ if(frame)frame.remove();if(index===${cases.length}){document.querySelector('pre').textContent=JSON.stringify(results);return;}frame=document.createElement('iframe');frame.style='width:200px;height:120px';frame.src='/case?i='+index++;document.body.appendChild(frame);}
      addEventListener('message',e=>{results.push(e.data);next();});next();
    </script></body>`);
  }
  const file = path.join(root, 'www', path.basename(url.pathname));
  if (fs.existsSync(file)) { res.setHeader('Content-Type','application/javascript'); res.end(fs.readFileSync(file)); }
  else {res.statusCode=404;res.end('missing');}
});
server.listen(0,'127.0.0.1',async ()=>{
  const profile=fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'edit-rin-browser-'));
  const args=['--headless','--no-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-background-networking','--no-first-run','--no-default-browser-check','--user-data-dir='+profile,'--remote-debugging-port=0',`http://127.0.0.1:${server.address().port}`];
  const child=spawn(process.env.BROWSER_BIN || '/opt/brave.com/brave/brave',args);let errors='';
  child.stderr.on('data',d=>errors+=d);
  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  let ws;
  try {
    for(let i=0;i<100&&!fs.existsSync(profile+'/DevToolsActivePort');i++)await sleep(100);
    const port=fs.readFileSync(profile+'/DevToolsActivePort','utf8').split('\n')[0];
    const tabs=await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();
    ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
    await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
    let id=0;const pending=new Map();
    ws.onmessage=e=>{const data=JSON.parse(e.data);if(pending.has(data.id)){pending.get(data.id)(data.result);pending.delete(data.id);}};
    const evaluate=expression=>new Promise((resolve,reject)=>{const key=++id;const timer=setTimeout(()=>{pending.delete(key);reject(new Error('Browser evaluation timeout'));},5000);pending.set(key,value=>{clearTimeout(timer);resolve(value);});ws.send(JSON.stringify({id:key,method:'Runtime.evaluate',params:{expression,returnByValue:true}}));});
    let results=[],last=0;
    for(let i=0;i<240;i++){
      const r=await evaluate('JSON.stringify(window.__browserResults || [])');
      results=JSON.parse(r.result.value);
      if(results.length>last){console.log(JSON.stringify(results.slice(last)));last=results.length;}
      if(results.length===cases.length)break;
      await sleep(250);
    }
    fs.writeFileSync(path.join(profile, 'results.json'),JSON.stringify(results,null,2));
    if(results.length!==cases.length||results.some(x=>!x.ok))process.exitCode=1;
    console.log('BROWSER: '+results.filter(x=>x.ok).length+'/'+cases.length+' passed');
    if(results.length!==cases.length)console.log((await evaluate('document.querySelector("iframe")?.contentWindow.__test')).result);
  }catch(e){console.log(String(e),errors.slice(-600));process.exitCode=1;}
  finally {ws?.close();child.kill('SIGKILL');server.close();}
});
