// Optional integration check with a real Chromium browser (Node.js 22+).
// BROWSER_BIN=/path/to/chromium node tests/authoring.browser.cjs
// BROWSER_ARTIFACT_DIR=/path/to/output retains results and browser diagnostics.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const kotlin = fs.readFileSync(path.join(root, 'android/app/src/main/java/com/hikariatelier/app/WorkTemplate.kt'), 'utf8');
const templates = {};
for (const name of ['WEBGL', 'SHADER', 'MATTER', 'ANIMATION', 'INPUT', 'WAVE_PARAMETERS']) {
  templates[name] = kotlin.match(new RegExp(`internal val ${name}_TEMPLATE = """([\\s\\S]*?)"""`))[1].trim() + '\n';
}
templates.CIRCLE = JSON.parse(kotlin.match(/internal const val CIRCLE_TEMPLATE = (".*")/)[1]);
const runner = fs.readFileSync(path.join(root, 'www/p5_runner.html'), 'utf8');
const baseCases = ['1.11.5', '2.3.4'].flatMap(version => Object.keys(templates).flatMap(kind => ['fixed', 'responsive'].map(mode => ({ version, kind, mode }))));
const cases = baseCases.flatMap(c => [false, true].map(chunked => ({...c, chunked})));
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
    // Exercise a real browser exception after an apparent URL change: the page keeps its owner.
    const owner = window.__editRinRunToken;
    history.replaceState(null, '', '/project/newer-run/p5_runner.html');
    window.__editRinRunToken = 'newer-run';
    if (window.__editRinRunToken !== owner) throw new Error('run identity changed');
    const probe = document.createElement('script');
    probe.textContent = '\\nthrow new Error("bridge probe");\\n//# sourceURL=sketch.js?run=' + owner;
    document.head.appendChild(probe);
    if (window.__test.runtimeErrors.length !== 1 || window.__test.runtimeErrors[0].line !== 2 || window.__test.runtimeErrors[0].owner !== owner) throw new Error('runtime location/owner missing');
    result = {...result, ok: true, exports};
  } catch (e) { result.error = String(e); }
  parent.postMessage(result, '*');
})();
</script>`;
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (/^\/project\/case-\d+\/p5_runner\.html$/.test(url.pathname)) {
    const c = cases[Number(url.searchParams.get('i'))];
    let code = templates[c.kind];
    if (c.mode === 'fixed') code = code.split('\nfunction windowResized()')[0].replace('createCanvas(windowWidth, windowHeight', 'createCanvas(200, 120');
    const mock = `<script>
      window.__case = ${JSON.stringify(c)};
      window.__test = {ready:false,errors:[],bridgeCalls:[],runtimeErrors:[]};
      const expectedOwner = location.pathname.split('/')[2];
      const checkOwner = owner => {
        window.__test.bridgeCalls.push(owner);
        if (owner !== expectedOwner) throw new Error('wrong bridge owner: ' + owner);
      };
      window.Android = {
        getSketchCode: owner => {checkOwner(owner);return ${JSON.stringify(code)};},
        getP5Version: owner => {checkOwner(owner);return ${JSON.stringify(c.version)};},
        isP5SoundEnabled: owner => {checkOwner(owner);return false;},
        getWorkLibraries: owner => {checkOwner(owner);return ${JSON.stringify(c.kind === 'MATTER' ? '{"matter-js":"0.20.0"}' : '{}')};},
        getWorkParameters: owner => {checkOwner(owner);return ${JSON.stringify(c.kind === 'WAVE_PARAMETERS' ? '{"size":0.3,"ink":"#A8C7FA"}' : '{}')};}, getWorkShaders: owner => {checkOwner(owner);return '{}';},
        onStatusChanged: (owner,status) => {checkOwner(owner);if (status === '実行中') window.__test.ready = true;},
        onError: (owner,error) => {checkOwner(owner);window.__test.errors.push(error);}, onRuntimeError: (owner,error,line) => {checkOwner(owner);window.__test.errors.push(error);window.__test.runtimeErrors.push({owner,line});},
        onCaptureError: (owner,error) => {checkOwner(owner);window.__test.errors.push(error);},
        onScreenshotExportReady: (owner,data,width,height) => {checkOwner(owner);window.__test.exported={data,width,height};}
      };
      if (window.__case.chunked) {
        let id, chunks;
        Object.assign(window.Android, {
          beginScreenshotTransfer: (owner,token) => {checkOwner(owner);id=token;chunks=[];return true;},
          appendScreenshotChunk: (owner,token, data) => {
            checkOwner(owner);
            if(token!==id || atob(data).length>192*1024) return false;
            chunks.push(atob(data));return true;
          },
          abortScreenshotTransfer: (owner) => {checkOwner(owner);chunks=[];},
          finishScreenshotTransfer: (owner,token,width,height) => {
            checkOwner(owner);
            if(token!==id) throw new Error('wrong PNG token');
            window.__test.exported={data:'data:image/png;base64,'+btoa(chunks.join('')),width,height};
          }
        });
      }
    </script>`;
    res.setHeader('Content-Type', 'text/html');
    return res.end(runner.replace('<head>', '<head>' + mock).replace('</body>', `<script>window.addEventListener('load', () => {const script=document.createElement('script');script.textContent=${JSON.stringify(caseTest.replace(/^<script>\n|<\/script>$/g, ''))};document.body.appendChild(script);});</script></body>`));
  }
  if (url.pathname === '/') {
    res.setHeader('Content-Type','text/html');
    return res.end(`<body><pre id="results">RUNNING</pre><script>
      const results=window.__browserResults=[]; let frame, index=0;
      function next(){ if(frame)frame.remove();if(index===${cases.length}){document.querySelector('pre').textContent=JSON.stringify(results);return;}frame=document.createElement('iframe');frame.style='width:200px;height:120px';frame.src='/project/case-'+index+'/p5_runner.html?i='+index++;document.body.appendChild(frame);}
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
  const child=spawn(process.env.BROWSER_BIN || '/opt/brave.com/brave/brave',args);let errors='',spawnError;
  child.stderr.on('data',d=>errors+=d);
  child.once('error',error=>{spawnError=error;errors+=String(error);});
  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  let ws,results=[],passed=false;
  try {
    for(let i=0;i<100&&!spawnError&&!fs.existsSync(profile+'/DevToolsActivePort');i++)await sleep(100);
    if(spawnError)throw spawnError;
    const port=fs.readFileSync(profile+'/DevToolsActivePort','utf8').split('\n')[0];
    const tabs=await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();
    ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
    await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
    let id=0;const pending=new Map();
    ws.onmessage=e=>{const data=JSON.parse(e.data);if(pending.has(data.id)){pending.get(data.id)(data.result);pending.delete(data.id);}};
    const evaluate=expression=>new Promise((resolve,reject)=>{const key=++id;const timer=setTimeout(()=>{pending.delete(key);reject(new Error('Browser evaluation timeout'));},5000);pending.set(key,value=>{clearTimeout(timer);resolve(value);});ws.send(JSON.stringify({id:key,method:'Runtime.evaluate',params:{expression,returnByValue:true}}));});
    let last=0;
    for(let i=0;i<cases.length*16;i++){
      const r=await evaluate('JSON.stringify(window.__browserResults || [])');
      results=JSON.parse(r.result.value);
      if(results.length>last){console.log(JSON.stringify(results.slice(last)));last=results.length;}
      if(results.length===cases.length)break;
      await sleep(250);
    }
    passed=results.length===cases.length&&results.every(x=>x.ok);
    if(!passed)process.exitCode=1;
    console.log('BROWSER: '+results.filter(x=>x.ok).length+'/'+cases.length+' passed');
    if(results.length!==cases.length)console.log((await evaluate('document.querySelector("iframe")?.contentWindow.__test')).result);
  }catch(e){passed=false;console.log(String(e),errors.slice(-600));process.exitCode=1;}
  finally {
    ws?.close();
    // Wait for Chromium to release the profile before deleting a successful run.
    if(child.pid&&child.exitCode===null&&child.signalCode===null)await new Promise(resolve=>{
      child.once('exit',resolve);child.kill('SIGKILL');
    });
    server.close();
    const writeDiagnostics=directory=>{
      fs.mkdirSync(directory,{recursive:true});
      fs.writeFileSync(path.join(directory,'results.json'),JSON.stringify(results,null,2));
      fs.writeFileSync(path.join(directory,'browser.stderr.log'),errors);
    };
    try {
      if(process.env.BROWSER_ARTIFACT_DIR){
        const directory=path.resolve(process.env.BROWSER_ARTIFACT_DIR);
        fs.mkdirSync(directory,{recursive:true});
        const output=fs.mkdtempSync(path.join(directory,'authoring-'));
        writeDiagnostics(output);console.log('BROWSER ARTIFACTS: '+output);
      }
    }catch(error){passed=false;process.exitCode=1;errors+='\n'+String(error);console.log(String(error));}
    if(passed)fs.rmSync(profile,{recursive:true,force:true});
    else {writeDiagnostics(profile);console.log('BROWSER DIAGNOSTICS: '+profile);}
  }
});
