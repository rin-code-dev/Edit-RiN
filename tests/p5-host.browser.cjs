// Real browser integration for the shared document/instance/module/download host.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../www');
const prefix = '/project/browser-test/';
const callbacks = ['setup','draw','preload','windowResized','mousePressed','mouseReleased','mouseClicked','mouseMoved','mouseDragged','keyPressed','keyReleased','keyTyped','touchStarted','touchMoved','touchEnded'];
const tail = '\nwindow.__editRinRegisterModuleCallbacks?.({' + callbacks.map(name => `${name}:typeof ${name}==='function'?${name}:undefined`).join(',') + '});\nwindow.__editRinModuleReady?.();';
const instancesCode = `window.instances = [0,1].map(index => new p5(p => {
  p.setup = () => { p.createCanvas(120,80); p.pixelDensity(index+1); if(index) p.noLoop(); };
  p.draw = () => p.background(index ? 'blue' : (window.makeGreen ? 'lime' : 'red'));
}));`;
const fixtures = {
  'nested/index.html': `<!doctype html><html><head><meta charset="utf-8">
    <script src="${prefix}__edit-rin__/p5_host.js"></script>
    <style>body{margin:17px}canvas{position:relative!important;width:120px!important;height:80px!important;left:7px!important}#label{color:purple}</style>
    <script defer src="../p5-v2.min.js"></script><script defer src="../html-sketch.js"></script>
    </head><body><h1 id="label">Authored layout</h1><main></main></body></html>`,
  'html-sketch.js': instancesCode,
  'main.mjs': `import { width } from './lib/math.mjs';
await new Promise(resolve=>setTimeout(resolve,30));
export function setup(){createCanvas(width,80);window.moduleLoaded=true;noLoop();}
export function draw(){background(60,90,210);}
` + tail,
  'lib/math.mjs': 'export const width = 120;',
  'lib/fail.mjs': 'export function fail(){\n  throw new Error("module helper failed");\n}',
  'plain.html': `<!doctype html><html><head><meta charset="utf-8"><script src="${prefix}__edit-rin__/p5_host.js"></script>
    <style>#moving{animation:slide 1s linear infinite}@keyframes slide{to{transform:translateX(10px)}}</style></head>
    <body><div id="moving">HTML</div><canvas width="40" height="30"></canvas><script>
    window.ticks=0;function tick(){ticks++;requestAnimationFrame(tick)}requestAnimationFrame(tick);
    document.querySelector('canvas').getContext('2d').fillRect(0,0,40,30);
    </script></body></html>`
};
const server = http.createServer((request,response) => {
  const url = new URL(request.url, 'http://localhost');
  let filename = url.pathname.replace(/^\/project\/[^/]+\//,'');
  if (filename.startsWith('__edit-rin__/')) filename = filename.slice('__edit-rin__/'.length);
  try {
    response.setHeader('Content-Type', /\.(?:js|mjs)$/.test(filename) ? 'application/javascript; charset=utf-8' : 'text/html; charset=utf-8');
    response.end(fixtures[filename] ?? fs.readFileSync(path.join(root, filename)));
  } catch (_) { response.statusCode=404;response.end('missing'); }
});
async function pageWithBridge(browser, { version='2.3.4', code='', config={} }={}) {
  const page = await browser.newPage({ viewport:{width:360,height:240}, deviceScaleFactor:1 });
  await page.addInitScript(({version,code,config}) => {
    window.testState={errors:[],statuses:[],ready:0,visualReady:0,files:[],recordings:[],recordingStatuses:[],chunks:[],owners:[],thumbnails:[]};
    const state=window.testState;
    let fileTransfer=null, recording=null;
    const owner=token=>{state.owners.push(token);if(token!=='browser-test')throw new Error('wrong owner '+token)};
    window.Android={
      getP5Version:token=>{owner(token);return version}, getSketchCode:()=>code,
      getProjectConfig:()=>JSON.stringify(config), isP5SoundEnabled:()=>false,
      getWorkParameters:()=> '{}',getWorkShaders:()=> '{}',getWorkLibraries:()=> '{}',
      onStatusChanged:(token,status)=>{owner(token);state.statuses.push(status)},
      onPreviewReady:token=>{owner(token);state.ready++;if(config.thumbnailOnly){state.readyFrame=window.frameCount;window.__editRinCaptureThumbnail();}},
      onPreviewVisualReady:token=>{owner(token);state.visualReady++;state.visualFrame=window.frameCount;},
      onThumbnailReady:(token,data)=>{owner(token);state.thumbnails.push({data,frame:window.frameCount})},
      onError:(token,message)=>{owner(token);state.errors.push(message)},
      onRuntimeError:(token,message,line)=>{owner(token);state.errors.push(message)},
      onRuntimeErrorFile:(token,message,file,line)=>{owner(token);state.errors.push({message,file,line})},
      onCaptureError:(token,message)=>{owner(token);state.errors.push(message)},
      onScreenshotExportReady:(token,data,width,height)=>{owner(token);state.exported={data,width,height}},
      beginFileDownload:(token,id,name,mime)=>{owner(token);if(fileTransfer)throw new Error('overlapping file transfers');fileTransfer={id,name,mime,bytes:''};return true},
      appendFileDownloadChunk:(token,id,data)=>{owner(token);if(fileTransfer?.id!==id)return false;const bytes=atob(data);if(bytes.length>192*1024)throw new Error('oversized file chunk');fileTransfer.bytes+=bytes;return true},
      finishFileDownload:(token,id)=>{owner(token);if(fileTransfer?.id!==id)throw new Error('wrong finish');state.files.push({...fileTransfer,bytes:btoa(fileTransfer.bytes)});fileTransfer=null},
      abortFileDownload:()=>{fileTransfer=null},
      beginRecordingTransfer:(token,id)=>{owner(token);recording={id,bytes:''};return true},
      appendRecordingChunk:(token,id,data)=>{owner(token);const bytes=atob(data);if(recording?.id!==id||bytes.length>192*1024)return false;state.chunks.push(bytes.length);recording.bytes+=bytes;return true},
      finishRecordingTransfer:(token,id,mime)=>{owner(token);if(recording?.id!==id)throw new Error('wrong recording');state.recordings.push({mime,bytes:btoa(recording.bytes)});recording=null},
      abortRecordingTransfer:()=>{recording=null},
      onRecordingStatusChanged:(token,status)=>{owner(token);state.recordingStatuses.push(status)},onRecordingSaving:()=>{}
    };
  }, {version,code,config});
  return page;
}
async function ready(page) {
  await page.waitForFunction(()=>testState.ready>0||testState.errors.length>0,{}, {timeout:10000});
  assert.deepEqual(await page.evaluate(()=>testState.errors),[]);
  assert.equal(await page.evaluate(()=>testState.ready),1);
  await page.waitForFunction(()=>testState.visualReady===1,{}, {timeout:10000});
}
async function screenshot(page, scale) {
  await page.evaluate(async scale => { testState.exported=null;await __editKiroCaptureScreenshot(scale); }, scale);
  return page.evaluate(async()=>{
    if(!testState.exported)throw new Error('no PNG: '+JSON.stringify(testState.errors));
    const image=new Image();image.src=testState.exported.data;await image.decode();
    const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
    const context=canvas.getContext('2d');context.drawImage(image,0,0);
    const data=context.getImageData(0,0,canvas.width,canvas.height).data;
    let red=false,blue=false,green=false;
    for(let i=0;i<data.length;i+=4){red||=data[i]===255&&data[i+1]===0&&data[i+2]===0;blue||=data[i]===0&&data[i+1]===0&&data[i+2]===255;green||=data[i]===0&&data[i+1]===255&&data[i+2]===0;}
    return {width:image.width,height:image.height,red,blue,green};
  });
}
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+server.address().port;
  const browser=await chromium.launch({executablePath:process.env.BROWSER_PATH,headless:true});
  try {
    for (const version of ['1.11.5','2.3.4']) {
      for (const scenario of ['slow-first-draw','no-loop-webgl','setup-only','transparent']) {
        const code = {
          'slow-first-draw': 'function setup(){createCanvas(96,80);frameRate(2);}function draw(){background(200,30,40);}',
          'no-loop-webgl': 'function setup(){createCanvas(96,80,WEBGL);setAttributes({preserveDrawingBuffer:false});noLoop();}function draw(){background(200,30,40);}',
          'setup-only': 'function setup(){createCanvas(96,80);background(200,30,40);noLoop();}',
          'transparent': 'function setup(){createCanvas(96,80);noLoop();}function draw(){clear();}'
        }[scenario];
        const page = await pageWithBridge(browser,{version,code,config:{thumbnailOnly:true}});
        await page.goto(origin+prefix+'p5_runner.html');
        await page.waitForFunction(()=>testState.thumbnails.length||testState.errors.length,{}, {timeout:10000});
        assert.deepEqual(await page.evaluate(()=>testState.errors),[],`${version} ${scenario}`);
        const result = await page.evaluate(async()=>{
          const {data,frame}=testState.thumbnails[0];
          if (!data) return {empty:true,frame};
          const image=new Image();image.src='data:image/png;base64,'+data;await image.decode();
          const copy=document.createElement('canvas');copy.width=image.width;copy.height=image.height;
          const context=copy.getContext('2d');context.drawImage(image,0,0);
          return {empty:false,frame,width:image.width,height:image.height,pixel:Array.from(context.getImageData(10,10,1,1).data)};
        });
        if (scenario==='transparent') assert.equal(result.empty,true);
        else {
          assert.equal(result.empty,false,`${version} ${scenario}`);
          assert.equal(result.pixel[3],255);
          [200,30,40].forEach((value,index)=>assert.ok(Math.abs(result.pixel[index]-value)<=2,`${version} ${scenario}: color ${result.pixel}`));
          assert.equal(result.width,96);assert.equal(result.height,80);
          if(scenario!=='setup-only') assert.ok(result.frame>=1);
        }
        await page.waitForFunction(()=>testState.visualReady===1);
        if(scenario!=='setup-only') assert.ok(await page.evaluate(()=>testState.visualFrame>=1));
        if(scenario==='slow-first-draw') assert.equal(await page.evaluate(()=>testState.readyFrame),0);
        await page.close();
      }
    }
    {
      const page = await pageWithBridge(browser,{code:'async function setup(){createCanvas(96,80);noLoop();}async function draw(){await new Promise(resolve=>setTimeout(resolve,80));background(200,30,40);}',config:{thumbnailOnly:true}});
      await page.goto(origin+prefix+'p5_runner.html');
      await page.waitForFunction(()=>testState.thumbnails.length||testState.errors.length,{}, {timeout:10000});
      assert.deepEqual(await page.evaluate(()=>testState.errors),[]);
      assert.ok(await page.evaluate(()=>testState.thumbnails[0].data));
      assert.equal(await page.evaluate(()=>testState.thumbnails.length),1);
      await page.close();
    }
    for(const version of ['1.11.5','2.3.4']) {
      const page=await pageWithBridge(browser,{version,code:instancesCode});
      await page.goto(origin+prefix+'p5_runner.html');await ready(page);
      await page.waitForFunction(()=>instances.every(instance=>instance.frameCount>=1));
      const before=await page.evaluate(()=>instances.map(instance=>({density:instance.pixelDensity(),loop:instance.isLooping(),width:instance.canvas.width})));
      let base;
      for(const factor of [1,2,4]) {
        const frame=await screenshot(page,factor);base ||= frame;
        assert.equal(frame.width,base.width*factor);assert.equal(frame.height,base.height*factor);
        assert.ok(frame.red&&frame.blue,JSON.stringify(frame));
        assert.deepEqual(await page.evaluate(()=>instances.map(instance=>({density:instance.pixelDensity(),loop:instance.isLooping(),width:instance.canvas.width}))),before);
      }
      await page.evaluate(()=>pauseSketch());
      assert.deepEqual(await page.evaluate(()=>instances.map(instance=>instance.isLooping())),[false,false]);
      await page.evaluate(()=>resumeSketch());
      assert.deepEqual(await page.evaluate(()=>instances.map(instance=>instance.isLooping())),[true,false]);
      await page.evaluate(()=>{instances[0].saveJSON({ok:true},'settings');instances[0].saveStrings(['first','second'],'lines');instances[0].saveCanvas('photo','jpg');const a=document.createElement('a');a.download='custom.csv';a.href=URL.createObjectURL(new Blob(['x,y\n1,2'],{type:'text/csv'}));a.click();URL.revokeObjectURL(a.href);});
      await page.waitForFunction(()=>testState.files.length===4||testState.errors.length,{}, {timeout:10000});
      const files=await page.evaluate(()=>testState.files);
      // Canvas encoding is asynchronous; FIFO starts when each payload exists.
      const byName=Object.fromEntries(files.map(file=>[file.name,file]));
      assert.deepEqual(Object.keys(byName).sort(),['custom.csv','lines.txt','photo.jpg','settings.json']);
      assert.deepEqual(Object.fromEntries(files.map(file=>[file.name,file.mime])),
        {'settings.json':'application/json','lines.txt':'text/plain','custom.csv':'text/csv','photo.jpg':'image/jpeg'});
      assert.deepEqual(JSON.parse(Buffer.from(byName['settings.json'].bytes,'base64').toString()),{ok:true});
      assert.equal(Buffer.from(byName['photo.jpg'].bytes,'base64').readUInt16BE(0),0xffd8);
      assert.equal(Buffer.from(byName['custom.csv'].bytes,'base64').toString(),'x,y\n1,2');
      await page.evaluate(()=>__editKiroStartRecording('webm'));
      await page.waitForTimeout(180);await page.evaluate(()=>{window.makeGreen=true});await page.waitForTimeout(180);
      const frame=await screenshot(page,1);assert.ok(frame.green&&frame.blue);
      await page.evaluate(()=>__editKiroStopRecording());
      await page.waitForFunction(()=>testState.recordings.length||testState.errors.length,{}, {timeout:10000});
      const clip=await page.evaluate(()=>testState.recordings[0]);
      assert.match(clip.mime,/video\/webm/);assert.equal(Buffer.from(clip.bytes,'base64').readUInt32BE(0),0x1a45dfa3);
      assert.deepEqual(await page.evaluate(()=>testState.errors),[]);
      console.log('PASS multiple-instance fit/PNG/playback/standard-save/recording',version);await page.close();
    }
    {
      const page=await pageWithBridge(browser,{config:{documentMode:true}});
      await page.goto(origin+prefix+'nested/index.html');await ready(page);await page.waitForFunction(()=>instances.every(instance=>instance.frameCount>=1));
      const css=await page.locator('canvas').first().evaluate(canvas=>({position:getComputedStyle(canvas).position,width:getComputedStyle(canvas).width,left:getComputedStyle(canvas).left}));
      assert.deepEqual(css,{position:'relative',width:'120px',left:'7px'});
      const frame=await screenshot(page,2);assert.ok(frame.red&&frame.blue);
      assert.deepEqual(await page.locator('canvas').first().evaluate(canvas=>({position:getComputedStyle(canvas).position,width:getComputedStyle(canvas).width,left:getComputedStyle(canvas).left})),css);
      assert.equal(await page.locator('#label').textContent(),'Authored layout');
      console.log('PASS HTML author CSS/defer/instance PNG');await page.close();
    }
    for(const version of ['1.11.5','2.3.4']) {
      const page=await pageWithBridge(browser,{version,config:{moduleMode:true,moduleEntry:prefix+'main.mjs'}});
      await page.goto(origin+prefix+'p5_runner.html');await ready(page);
      assert.equal(await page.evaluate(()=>moduleLoaded),true);
      assert.equal(await page.locator('canvas').count(),1);
      await page.waitForFunction(()=>frameCount>=1);
      assert.equal((await screenshot(page,2)).width,240);
      await page.evaluate(async()=>{const {fail}=await import('./lib/fail.mjs');try{fail()}catch(error){Promise.reject(error)}});
      await page.waitForFunction(()=>testState.errors.length);
      assert.deepEqual(await page.evaluate(()=>testState.errors[0]),{message:'module helper failed',file:'lib/fail.mjs',line:2});
      console.log('PASS ESModule imports/exported callbacks/source location',version);await page.close();
    }
    {
      const page=await pageWithBridge(browser,{config:{documentMode:true}});
      await page.goto(origin+prefix+'plain.html');await ready(page);await page.waitForFunction(()=>ticks>2);
      await page.evaluate(()=>pauseSketch());const paused=await page.evaluate(()=>ticks);await page.waitForTimeout(100);
      assert.equal(await page.evaluate(()=>ticks),paused);
      assert.equal(await page.evaluate(()=>document.getAnimations()[0].playState),'paused');
      await page.evaluate(()=>resumeSketch());await page.waitForFunction(paused=>ticks>paused,paused);
      assert.equal(await page.evaluate(()=>document.getAnimations()[0].playState),'running');
      assert.equal((await screenshot(page,1)).width,40);
      console.log('PASS plain HTML RAF/CSS pause/resume/capture');await page.close();
    }
    {
      const page=await pageWithBridge(browser,{code:'async function setup(){await createCanvas(40,30,WEBGPU)}'});
      await page.addInitScript(()=>Object.defineProperty(navigator,'gpu',{value:undefined,configurable:true}));
      await page.goto(origin+prefix+'p5_runner.html');await page.waitForFunction(()=>testState.errors.length);
      assert.match((await page.evaluate(()=>testState.errors)).join(' '),/WEBGPU/);
      assert.equal(await page.evaluate(()=>testState.ready),0);
      assert.equal(await page.evaluate(()=>p5.VERSION),'2.3.4');
      console.log('PASS offline WebGPU addon and unavailable GPU error');await page.close();
    }
  } finally {await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1});
