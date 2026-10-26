// Real p5 runtime checks for source changed through the block engine (Node.js 22+).
// BROWSER_BIN=/opt/brave.com/brave/brave node tests/blocks-runtime.browser.cjs
// Optional: BLOCKS_REPO_ROOT, BLOCKS_CATALOGUE, BLOCKS_AUTHORING, BROWSER_ARTIFACT_DIR (diagnostics only).
'use strict';
const assert=require('node:assert/strict');
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const vm=require('node:vm');
const zlib=require('node:zlib');
const {spawn}=require('node:child_process');
const root=process.env.BLOCKS_REPO_ROOT?path.resolve(process.env.BLOCKS_REPO_ROOT):path.resolve(__dirname,'..');
const core=require(path.join(root,'www/blocks/core.js'));
const catalogueFile=process.env.BLOCKS_CATALOGUE||path.join(root,'www/blocks/catalogue.js');
const catalogueContext=vm.createContext({});
vm.runInContext(fs.readFileSync(catalogueFile,'utf8'),catalogueContext,{filename:catalogueFile});
const catalogue=catalogueContext.EditRinBlockCatalogue;
assert.ok(Array.isArray(catalogue.EXAMPLES)&&catalogue.EXAMPLES.length>=4,'Missing block examples');
const versions=['1.11.5','2.3.4'];
const imageSource=version=>version.startsWith('1.')?
 'let img;\nfunction preload(){img=loadImage(window.__imageData);}\nfunction setup(){createCanvas(96,64);noLoop();}\nfunction draw(){background(0);image(img,0,0,width,height);}\n':
 'let img;\nasync function setup(){img=await loadImage(window.__imageData);createCanvas(96,64);noLoop();}\nfunction draw(){background(0);image(img,0,0,width,height);}\n';
const soundFileSource=version=>{
 const template=id=>{const t=catalogue.TEMPLATES.find(t=>t.id===id);assert.ok(t,id);return version.startsWith('1.')&&t.sourceV1?t.sourceV1:t.source;};
 const loading=version.startsWith('1.')?
  'function preload(){sound=loadSound("tone.wav");}\nfunction setup(){createCanvas(96,64);}\n':
  'async function setup(){sound=await loadSound("tone.wav");createCanvas(96,64);}\n';
 return 'let sound;\n'+loading+'function draw(){background(30);circle(40,32,20);}\nfunction mousePressed(){userStartAudio();\n'+
  ['soundVolume','soundRate','soundPan','soundLoop'].map(template).join('\n')+'\n}\nfunction mouseReleased(){sound.pause();sound.stop();}\n';
};
const cases=versions.flatMap(version=>[
 ...catalogue.EXAMPLES.map(example=>({version,kind:example.id,sound:!!example.requiresSound,source:example.source})),
 {version,kind:'image',sound:false,source:imageSource(version)},
 {version,kind:'soundFile',sound:true,source:soundFileSource(version)},
]);
const authoringFile=process.env.BLOCKS_AUTHORING||path.join(root,'www/blocks/authoring.js');
catalogueContext.EditRinBlocks=core;
catalogueContext.acorn=require(path.join(root,'www/blocks/vendor/acorn.js'));
vm.runInContext(fs.readFileSync(authoringFile,'utf8'),catalogueContext,{filename:authoringFile});
const authoring=catalogueContext.EditRinBlockAuthoring;
assert.ok(authoring&&typeof authoring.plan==='function','Missing block authoring planner');
function authoringCases(version){
 const env={p5Version:version,soundEnabled:true,assets:[{name:'assets/block-test.png',mime:'image/png'},{name:'assets/tone.wav',mime:'audio/wav'}]};
 const start=()=>core.parse('function setup(){createCanvas(96,64);noLoop();}\nfunction draw(){background(0);}\n',{p5Version:version,sourceType:'script'});
 const plan=(projection,request)=>{const result=authoring.plan(projection,request,env);assert.equal(result.ok,true,version+' '+request.type+': '+result.error);assert.equal(core.applyPatches(projection.source,result.patches),result.source);return result;};
 const resource=(result,kind)=>result.resourceName||authoring.inspect(result.projection,env).resources.find(resource=>resource.kind===kind)?.name;
 let loaded=plan(start(),{type:'loadAsset',kind:'image',assetName:'assets/block-test.png'}),name=resource(loaded,'image');assert.ok(name);
 let drawn=plan(loaded.projection,{type:'useResource',action:'image',name,targetEvent:'draw'});
 const image=drawn.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='image');assert.ok(image,'Image workflow did not insert a display block');
 const fullImage=core.proposePatch(drawn.projection,{type:'setInput',id:image.id,input:'args',source:[name,'0','0','width','height']});assert.equal(fullImage.ok,true,fullImage.error);
 const imageCase={version,kind:'authoringImage',sound:false,source:fullImage.source};
 loaded=plan(start(),{type:'loadAsset',kind:'sound',assetName:'assets/tone.wav'});name=resource(loaded,'sound');assert.ok(name);
 drawn=plan(loaded.projection,{type:'useResource',action:'soundPan',name,targetEvent:'setup'});
 drawn=plan(drawn.projection,{type:'useResource',action:'soundPlay',name,targetEvent:'mousePressed'});
 const soundCase={version,kind:'authoringSound',sound:true,source:drawn.source};
 loaded=plan(start(),{type:'createOscillator'});name=resource(loaded,'oscillator');assert.ok(name);
 drawn=plan(loaded.projection,{type:'useResource',action:'toneStart',name,targetEvent:'mousePressed'});
 drawn=plan(drawn.projection,{type:'useResource',action:'toneStop',name,targetEvent:'mouseReleased'});
 // This accessor is test instrumentation only. Every declaration and input-event
 // operation for the resource above comes from the same helper as the palette.
 assert.match(name,/^[A-Za-z_$][\w$]*$/);
 const toneCase={version,kind:'authoringOscillator',sound:true,repeatGestures:3,source:drawn.source+'\nwindow.__getBlockResource=()=>'+name+';\n'};
 const webgl=plan(start(),{type:'ensureWebGL',confirmed:true}),draw=webgl.projection.nodes.find(n=>n.kind==='function'&&n.fields.name.value==='draw');assert.ok(draw);
 const shape=core.proposePatch(webgl.projection,{type:'insert',parentId:draw.id,input:'body',index:draw.inputs.body.length,source:catalogue.P5_CALLS.normalMaterial.source+'\n'+catalogue.P5_CALLS.box.source});assert.equal(shape.ok,true,shape.error);
 const box=shape.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='box');assert.ok(box);
 const smallBox=core.proposePatch(shape.projection,{type:'setInput',id:box.id,input:'args',source:['35']});assert.equal(smallBox.ok,true,smallBox.error);
 const threeCase={version,kind:'authoringThree',sound:false,source:smallBox.source};
 return [imageCase,soundCase,toneCase,threeCase];
}
cases.push(...versions.flatMap(authoringCases));
for(const version of versions)for(const mode of ['RGB','HSB']){
 const source='function setup(){createCanvas(96,64);pixelDensity(1);noLoop();'+(mode==='HSB'?'colorMode(HSB,360,100,100,1);':'')+'noStroke();}\nfunction draw(){clear();fill('+(mode==='HSB'?'0,100,100,0.5':'255,0,0,128')+');rect(0,0,width,height);}\n';
 const p=core.parse(source,{p5Version:version}),call=p.nodes.find(n=>n.calleeName==='fill'),first=core.proposePatch(p,{type:'setColor',id:call.id,hex:'#ff0000'});assert.equal(first.ok,true,first.error);
 const updated=first.projection.nodes.find(n=>n.calleeName==='fill'),changed=core.proposePatch(first.projection,{type:'setColor',id:updated.id,hex:'#3498db'});assert.equal(changed.ok,true,changed.error);
 cases.push({version,kind:'alpha'+mode,sound:false,source:changed.source,expectedPixel:[52,152,219,128]});
}
for(const item of cases){
 const projection=core.parse(item.source,{p5Version:item.version});assert.equal(projection.ok,true,JSON.stringify(projection.diagnostics));
 const literal=projection.nodes.find(n=>n.kind==='literal'&&n.valueType==='number'&&n.fields.value.value>0);
 assert.ok(literal,'Example needs an editable numeric block');
 const changed=core.proposePatch(projection,{type:'setField',id:literal.id,field:'value',value:literal.fields.value.value+1});
 assert.equal(changed.ok,true,changed.error);assert.notEqual(changed.source,item.source);assert.equal(core.applyPatches(item.source,changed.patches),changed.source);
 item.source=changed.source;item.patches=changed.patches.length;
}
const runner=fs.readFileSync(path.join(root,'www/p5_runner.html'),'utf8');
const completeScript=`
(async()=>{
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 let result={version:window.__case.version,kind:window.__case.kind,ok:false,sourceChanged:true};
 try{
  for(let i=0;i<120&&!window.__test.ready&&!window.__test.errors.length;i++)await wait(50);
  if(!window.__test.ready)throw new Error('Preview not ready: '+window.__test.errors.join(';'));
  for(let i=0;i<100&&(typeof frameCount==='undefined'||frameCount<1);i++)await wait(50);
  if(typeof frameCount==='undefined'||frameCount<1)throw new Error('No drawn frame');
  window.__test.awaitingGesture=true;
  if(window.__case.sound){
   for(let i=0;i<100&&!window.__test.gestureReceived;i++)await wait(50);
   if(!window.__test.gestureReceived)throw new Error('No trusted input gesture');
   let peak=0,running=false;
   for(let i=0;i<100;i++){
    for(const probe of window.__test.audioProbes){
     running=running||probe.context.state==='running';
     const data=new Float32Array(probe.node.fftSize);probe.node.getFloatTimeDomainData(data);
     for(const v of data)peak=Math.max(peak,Math.abs(v));
    }
    if(running&&peak>0.00001)break;await wait(50);
   }
   if(!running||peak<=0.00001)throw new Error('No real oscillator signal after gesture; contexts='+[...window.__editRinActiveAudioContexts].map(c=>c.state).join(',')+'; probes='+window.__test.audioProbes.length+'; peak='+peak);
   result.audio={running,peak};
   if(window.__case.repeatGestures>1){
    const first=window.__getBlockResource?.();if(!first)throw new Error('Generated oscillator resource was not created');
    window.__test.repeatReady=true;
    for(let count=2;count<=window.__case.repeatGestures;count++){
     for(let i=0;i<100&&window.__test.gestureCount<count;i++)await wait(50);
     if(window.__test.gestureCount<count)throw new Error('Missing repeated trusted input gesture '+count);
     await wait(100);if(window.__getBlockResource()!==first)throw new Error('A new oscillator was created for each tap');
    }
    result.sameResourceAfterTaps=window.__test.gestureCount;
   }
   // Release while the sound source still belongs to this page.
   if(typeof mouseReleased==='function')mouseReleased();
  }
  window.pauseSketch();window.__test.exported=null;
  await window.__editKiroCaptureScreenshot(1);
  const png=window.__test.exported;if(!png)throw new Error('PNG capture missing');
  const img=new Image();img.src=png.data;await img.decode();
  const sample=document.createElement('canvas');sample.width=16;sample.height=16;
  const ctx=sample.getContext('2d');ctx.drawImage(img,0,0,16,16);
  const pixels=ctx.getImageData(0,0,16,16).data;
  if(!pixels.some((v,i)=>i%4===3&&v>0))throw new Error('Actual rendered canvas is transparent');
  if(window.__case.kind==='three'||window.__case.kind==='authoringThree'){
   const colors=new Set();for(let i=0;i<pixels.length;i+=4)if(pixels[i+3])colors.add(pixels[i]+','+pixels[i+1]+','+pixels[i+2]);
   if(colors.size<2)throw new Error('3D canvas has only background pixels');result.renderedColors=colors.size;
  }
  if(window.__case.kind==='image'||window.__case.kind==='authoringImage'){
   const offset=(8*16+8)*4;
   if(pixels[offset]<240||pixels[offset+1]>20||pixels[offset+2]>20||pixels[offset+3]<240)throw new Error('Loaded image did not draw red pixels: '+[...pixels.slice(offset,offset+4)]);
   result.imagePixel=[...pixels.slice(offset,offset+4)];
  }
  if(window.__case.expectedPixel){
   const offset=(8*16+8)*4,actual=[...pixels.slice(offset,offset+4)];
   if(actual.some((value,index)=>Math.abs(value-window.__case.expectedPixel[index])>3))throw new Error('Picked colour or alpha changed: '+actual);
   result.colorPixel=actual;
  }
  if(window.__test.errors.length)throw new Error(window.__test.errors.join(';'));
  result={...result,ok:true,frames:frameCount,width:img.width,height:img.height,p5:p5.VERSION};
 }catch(e){result.error=String(e);result.errors=window.__test.errors.slice();}
 parent.postMessage({type:'runtime-result',result},'*');
})();`;
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 if(url.pathname.endsWith('/assets/block-test.png')){
  // A real four-pixel-square PNG exercises the same relative asset path used by
  // native works. No data URL bypass is used in the authoring workflow cases.
  const crc=bytes=>{let value=0xffffffff;for(const b of bytes){value^=b;for(let bit=0;bit<8;bit++)value=value&1?(value>>>1)^0xedb88320:value>>>1;}return (value^0xffffffff)>>>0;};
  const chunk=(name,data)=>{const type=Buffer.from(name),result=Buffer.alloc(12+data.length);result.writeUInt32BE(data.length,0);type.copy(result,4);data.copy(result,8);result.writeUInt32BE(crc(Buffer.concat([type,data])),8+data.length);return result;};
  const header=Buffer.alloc(13);header.writeUInt32BE(4,0);header.writeUInt32BE(4,4);header[8]=8;header[9]=6;
  const scanlines=Buffer.concat(Array.from({length:4},()=>Buffer.from([0,...Array.from({length:4},()=>[255,0,0,255]).flat()])));
  res.setHeader('Content-Type','image/png');res.end(Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(scanlines)),chunk('IEND',Buffer.alloc(0))]));return;
 }
 if(url.pathname.endsWith('/tone.wav')){
  const rate=22050,count=rate,pcm=Buffer.alloc(44+count*2);
  pcm.write('RIFF',0);pcm.writeUInt32LE(pcm.length-8,4);pcm.write('WAVEfmt ',8);pcm.writeUInt32LE(16,16);
  pcm.writeUInt16LE(1,20);pcm.writeUInt16LE(1,22);pcm.writeUInt32LE(rate,24);pcm.writeUInt32LE(rate*2,28);
  pcm.writeUInt16LE(2,32);pcm.writeUInt16LE(16,34);pcm.write('data',36);pcm.writeUInt32LE(count*2,40);
  for(let i=0;i<count;i++)pcm.writeInt16LE(Math.round(Math.sin(i*440*Math.PI*2/rate)*6500),44+i*2);
  res.setHeader('Content-Type','audio/wav');res.end(pcm);return;
 }
 if(/^\/project\/case-\d+\/p5_runner\.html$/.test(url.pathname)){
  const index=Number(url.searchParams.get('i')),item=cases[index];
  if(!item){res.writeHead(404);res.end('Unknown case');return;}
  const mock=`<script>
   window.__case=${JSON.stringify({index,version:item.version,kind:item.kind,sound:item.sound,repeatGestures:item.repeatGestures||1,expectedPixel:item.expectedPixel})};
   window.__test={ready:false,errors:[],audioProbes:[],awaitingGesture:false,gestureReceived:false,gestureCount:0,repeatReady:false};
   addEventListener('error',e=>window.__test.errors.push(e.message));
   addEventListener('unhandledrejection',e=>window.__test.errors.push(String(e.reason)));
   addEventListener('pointerdown',e=>{if(e.isTrusted){window.__test.gestureReceived=true;window.__test.gestureCount++;}},true);
   const imageCanvas=document.createElement('canvas');imageCanvas.width=4;imageCanvas.height=4;
   const imageContext=imageCanvas.getContext('2d');imageContext.fillStyle='#ff0000';imageContext.fillRect(0,0,4,4);window.__imageData=imageCanvas.toDataURL();
   // Tap the actual audio graph immediately before its hardware destination.
   const originalConnect=AudioNode.prototype.connect;
   AudioNode.prototype.connect=function(destination,...args){
    const result=originalConnect.call(this,destination,...args);
    if(destination instanceof AudioDestinationNode&&!window.__test.audioProbes.some(p=>p.source===this)){
     const analyser=this.context.createAnalyser();analyser.fftSize=2048;
     originalConnect.call(this,analyser);
     window.__test.audioProbes.push({source:this,context:this.context,node:analyser});
    }
    return result;
   };
   window.Android={
    getSketchCode:()=>${JSON.stringify(item.source)},getP5Version:()=>${JSON.stringify(item.version)},isP5SoundEnabled:()=>${JSON.stringify(item.sound)},getWorkLibraries:()=>'{}',getWorkParameters:()=>'{}',getWorkShaders:()=>'{}',
    onStatusChanged:(_owner,status)=>{if(status==='実行中')window.__test.ready=true;},
    onError:(_owner,error)=>window.__test.errors.push(error),onRuntimeError:(_owner,error)=>window.__test.errors.push(error),onCaptureError:(_owner,error)=>window.__test.errors.push(error),
    onScreenshotExportReady:(_owner,data,width,height)=>{window.__test.exported={data,width,height};}
   };
  </script>`;
  const boot=`<script>addEventListener('load',()=>{const script=document.createElement('script');script.textContent=${JSON.stringify(completeScript)};document.body.appendChild(script);});</script>`;
  res.setHeader('Content-Type','text/html');res.end(runner.replace('<head>','<head>'+mock).replace('</body>',boot+'</body>'));return;
 }
 if(url.pathname==='/'){
  res.setHeader('Content-Type','text/html');res.end(`<body><pre id="results"></pre><script>
   const results=window.__browserResults=[];let index=0,frame;window.__runtimeIndex=0;
   function next(){if(frame)frame.remove();if(index===${cases.length}){document.querySelector('pre').textContent=JSON.stringify(results);return;}window.__runtimeIndex=index;frame=document.createElement('iframe');frame.style='position:absolute;left:0;top:0;width:420px;height:420px;border:0';frame.src='/project/case-'+index+'/p5_runner.html?i='+index++;document.body.appendChild(frame);}
   addEventListener('message',e=>{if(e.source===frame?.contentWindow&&e.data?.type==='runtime-result'){results.push(e.data.result);next();}});next();
  </script></body>`);return;
 }
 const file=path.join(root,'www',path.basename(url.pathname));
 if(fs.existsSync(file)&&fs.statSync(file).isFile()){res.setHeader('Content-Type','application/javascript');res.end(fs.readFileSync(file));}
 else{res.writeHead(404);res.end('missing');}
});
async function main(){
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const profile=fs.mkdtempSync(path.join(os.tmpdir(),'edit-rin-block-runtime-'));
 const child=spawn(process.env.BROWSER_BIN||'/opt/brave.com/brave/brave',['--headless','--no-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-background-networking','--no-first-run','--no-default-browser-check','--mute-audio','--user-data-dir='+profile,'--remote-debugging-port=0','http://127.0.0.1:'+server.address().port]);
 let stderr='',spawnError,ws,passed=false,results=[];
 child.stderr.on('data',d=>stderr+=d);child.on('error',e=>{spawnError=e;});const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 try{
  for(let i=0;i<100&&!spawnError&&!fs.existsSync(profile+'/DevToolsActivePort');i++)await sleep(100);
  if(spawnError)throw spawnError;
  const port=fs.readFileSync(profile+'/DevToolsActivePort','utf8').split('\n')[0];
  const tabs=await(await fetch('http://127.0.0.1:'+port+'/json/list')).json();
  ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  let id=0;const pending=new Map();
  ws.onmessage=e=>{const message=JSON.parse(e.data),task=pending.get(message.id);if(task){pending.delete(message.id);message.error?task.reject(new Error(message.error.message)):task.resolve(message.result);}};
  const send=(method,params)=>new Promise((resolve,reject)=>{const key=++id,timer=setTimeout(()=>{pending.delete(key);reject(new Error('CDP request timed out: '+method));},5000);pending.set(key,{resolve:value=>{clearTimeout(timer);resolve(value);},reject:e=>{clearTimeout(timer);reject(e);}});ws.send(JSON.stringify({id:key,method,params}));});
  const evaluate=async expression=>{const response=await send('Runtime.evaluate',{expression,returnByValue:true});if(response.exceptionDetails)throw new Error(JSON.stringify(response.exceptionDetails));return response.result.value;};
  let last=0;const gesturesSent=new Map();
  const deadline=Date.now()+Math.max(90000,cases.length*12000);
  while(Date.now()<deadline){
   results=JSON.parse(await evaluate('JSON.stringify(window.__browserResults||[])'));
   if(results.length>last){for(const r of results.slice(last))console.log(JSON.stringify(r));last=results.length;}
   if(results.length===cases.length)break;
   const state=JSON.parse(await evaluate('JSON.stringify((()=>{const w=document.querySelector("iframe")?.contentWindow;return {index:window.__runtimeIndex,awaiting:!!w?.__test?.awaitingGesture,sound:!!w?.__case?.sound,repeatReady:!!w?.__test?.repeatReady,count:w?.__test?.gestureCount||0,target:w?.__case?.repeatGestures||1};})())'));
   const sent=gesturesSent.get(state.index)||0;
   if(state.awaiting&&state.sound&&(!sent||state.repeatReady&&sent<state.target&&state.count>=sent)){
    gesturesSent.set(state.index,sent+1);
    if(sent)await send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,x:40,y:40});
    await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:40,y:40});
    await send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,x:40,y:40});
    // Keep the oscillator audible until the case samples its actual graph.
   }
   await sleep(100);
  }
  passed=results.length===cases.length&&results.every(r=>r.ok);
  if(!passed&&results.length!==cases.length)console.log('UNFINISHED: '+await evaluate('JSON.stringify(document.querySelector("iframe")?.contentWindow.__test, (k,v)=>k==="audioProbes"?v.length:v)'));
  console.log('BLOCK RUNTIME: '+results.filter(r=>r.ok).length+'/'+cases.length+' passed (both bundled p5 versions; transformed code)');
  if(!passed)process.exitCode=1;
 }catch(e){stderr+='\n'+String(e);console.error(e);process.exitCode=1;}
 finally{
  ws?.close();
  if(child.pid&&child.exitCode===null&&child.signalCode===null)await new Promise(resolve=>{child.once('exit',resolve);child.kill('SIGKILL');});
  server.close();
  const write=directory=>{fs.mkdirSync(directory,{recursive:true});fs.writeFileSync(path.join(directory,'results.json'),JSON.stringify(results,null,2));fs.writeFileSync(path.join(directory,'browser.stderr.log'),stderr);};
  if(process.env.BROWSER_ARTIFACT_DIR){fs.mkdirSync(path.resolve(process.env.BROWSER_ARTIFACT_DIR),{recursive:true});const output=fs.mkdtempSync(path.join(path.resolve(process.env.BROWSER_ARTIFACT_DIR),'blocks-runtime-'));write(output);console.log('BROWSER ARTIFACTS: '+output);}
  if(passed)fs.rmSync(profile,{recursive:true,force:true,maxRetries:10,retryDelay:100});else{write(profile);console.log('BROWSER DIAGNOSTICS: '+profile);}
 }
}
main().catch(e=>{console.error(e);server.close();process.exitCode=1;});
