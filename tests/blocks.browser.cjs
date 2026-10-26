'use strict';
// Exercises the real offline page, worker, Blockly fields and native acknowledgement protocol.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(process.env.EDIT_RIN_TEST_ROOT || path.join(__dirname, '..'));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const results = [];
const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname;
  if (!pathname.startsWith('/blocks/') || pathname.includes('..')) { response.writeHead(403); return response.end(); }
  const file = path.join(root, 'www', pathname);
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); return response.end(); }
  const extension = path.extname(file);
  // Keep this aligned with BlockEditorWebClient: Workers inherit their script response policy.
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; worker-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; media-src 'self'; connect-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'");
  response.setHeader('Content-Type', ({'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.json':'application/json'})[extension] || 'application/octet-stream');
  response.end(fs.readFileSync(file));
});

async function run() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edit-rin-blocks-browser-'));
  const child = spawn(process.env.BROWSER_BIN || '/opt/brave.com/brave/brave', [
    '--headless','--no-sandbox','--disable-dev-shm-usage','--disable-background-networking',
    '--no-first-run','--no-default-browser-check','--user-data-dir='+profile,
    '--remote-debugging-port=0','http://127.0.0.1:'+server.address().port+'/blocks/index.html'
  ]);
  let stderr = '', spawnError, socket, passed = false;
  child.stderr.on('data', data => stderr += data);
  child.once('error', error => spawnError = error);
  try {
    for (let i=0;i<100&&!spawnError&&!fs.existsSync(path.join(profile,'DevToolsActivePort'));i++) await sleep(100);
    if (spawnError) throw spawnError;
    const port = fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];
    const targets = await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();
    socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve,reject) => {socket.onopen=resolve;socket.onerror=reject;});
    let sequence = 0;
    const pending = new Map();
    socket.onmessage = event => {
      const data=JSON.parse(event.data), request=pending.get(data.id);
      if (request) {pending.delete(data.id);request(data);}
    };
    const evaluate = expression => new Promise((resolve,reject) => {
      const id=++sequence, timeout=setTimeout(() => {pending.delete(id);reject(new Error('CDP evaluation timeout'));},15000);
      pending.set(id, data => {
        clearTimeout(timeout);
        if (data.error || data.result?.exceptionDetails) reject(new Error(JSON.stringify(data.error || data.result.exceptionDetails)));
        else resolve(data.result.result.value);
      });
      socket.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true,userGesture:true}}));
    });
    const command = (method,params) => new Promise((resolve,reject) => {
      const id=++sequence, timer=setTimeout(()=>{pending.delete(id);reject(new Error(method+' timed out'));},10000);
      pending.set(id,data=>{clearTimeout(timer);data.error?reject(new Error(JSON.stringify(data.error))):resolve(data.result);});
      socket.send(JSON.stringify({id,method,params}));
    });
    await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
    await command('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:5});
    await command('Page.reload',{ignoreCache:true});
    const waitFor = async (expression, label) => {
      for(let i=0;i<100;i++){if(await evaluate(expression))return;await sleep(80);}
      throw new Error('Timed out: '+label+'; '+await evaluate('document.getElementById("status")?.textContent'));
    };
    const check = async (name, action) => {
      await waitFor('!RinBlocksEditor.document.pending && !RinBlocksEditor.document.loading && !RinBlocksEditor.document.processing && RinBlocksEditor.workspace.getAllBlocks(false).filter(b=>b.id!=="rin_program").every(b=>b.isEditable()===RinBlocksEditor.document.editable)','settled editable projection');
      await action();results.push({name,ok:true});console.log('PASS: '+name);
    };
    await waitFor('!!window.RinBlocksEditor', 'editor API');
    const leadingComment='色や円の大きさを変えてみましょう。 / Try changing the color or circle size. '.repeat(3);
    const initialSource = '// '+leadingComment+'\n'+'function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  // @rin preserve\n  background(240);\n  circle(mouseX, mouseY, 80);\n}\n';
    await evaluate(`(() => {
      window.__native={source:${JSON.stringify(initialSource)},metadata:null,generation:0,revision:'test-0',edits:[],actions:[],messages:[],rejectNext:false,flushReplies:{},flushSources:[]};
      const channel=new MessageChannel();window.__port=channel.port1;
      window.__load=overrides=>{
        window.__document=Object.assign({type:'load',sessionId:'browser-session',workId:'work-one',file:'sketch.js',source:window.__native.source,revision:'test-'+(++window.__native.generation),metadata:window.__native.metadata,language:'ja',dark:false,readOnly:false,sourceType:'script',p5Version:'2.3.4',soundEnabled:true,assets:[{name:'image.png',mime:'image/png'},{name:'sound.wav',mime:'audio/wav'}],errors:[]},overrides||{});
        window.__native.revision=window.__document.revision;
        window.__port.postMessage(JSON.stringify(window.__document));
      };
      channel.port1.onmessage=event=>{
        const message=JSON.parse(event.data);window.__native.actions.push(message.type);window.__native.messages.push(message);
        if(message.type==='flushed'){window.__lastFlush=message;window.__native.draft=message.ok?message.draft:null;window.__native.flushSources.push(window.__native.source);window.__native.flushReplies[message.requestId]?.(message.ok);delete window.__native.flushReplies[message.requestId];return;}
        if(message.type!=='edit')return;
        let accepted=message.expectedSource===window.__native.source&&message.expectedRevision===window.__native.revision&&typeof message.requestId==='string'&&!!message.requestId&&!window.__document.readOnly&&!window.__native.rejectNext;
        window.__native.rejectNext=false;
        if(accepted){let source=window.__native.source;for(const patch of message.patches.slice().sort((a,b)=>b.start-a.start))source=source.slice(0,patch.start)+patch.text+source.slice(patch.end);window.__native.source=source;window.__native.metadata=message.metadata;window.__native.edits.push(message);window.__native.revision='test-'+(++window.__native.generation);}
        const ack={type:'ack',sessionId:'browser-session',workId:'work-one',requestId:message.requestId,accepted,source:window.__native.source,metadata:window.__native.metadata,revision:window.__native.revision};
        if(window.__native.holdNextAck){window.__native.holdNextAck=false;window.__native.heldAck=ack;}else channel.port1.postMessage(JSON.stringify(ack));
      };
      window.__flush=(hideChaff=true)=>new Promise(resolve=>{const requestId='flush-'+Date.now()+'-'+Math.random();window.__native.flushReplies[requestId]=resolve;channel.port1.postMessage(JSON.stringify({type:'flush',sessionId:'browser-session',requestId,hideChaff}));});
      channel.port1.start();
      // Native WebView.postWebMessage supplies an empty source origin and a transferred port.
      // Transfer ownership first, then deliver the same receiving-side MessageEvent shape.
      const transferredPort=structuredClone(channel.port2,{transfer:[channel.port2]});
      const connectEvent=new MessageEvent('message',{data:JSON.stringify({type:'connect',sessionId:'browser-session'}),origin:'',source:null,ports:[transferredPort]});
      window.__native.bootstrap={origin:connectEvent.origin,sourceNull:connectEvent.source===null,ports:connectEvent.ports.length};
      window.dispatchEvent(connectEvent);
      // Do not hide ordering issues with the old 20ms first-load delay.
      window.__load();
    })()`);
    await waitFor('window.RinBlocksEditor.projection?.ok && window.RinBlocksEditor.workspace?.getAllBlocks(false).length>5','initial projection');
    await check('native empty-origin handshake and immediate load render under native CSP',async()=>{
      assert.deepEqual(await evaluate('__native.bootstrap'),{origin:'',sourceNull:true,ports:1});
      assert.equal(await evaluate('__native.messages.some(message=>message.type==="ready")'),true);
      assert.equal(await evaluate('RinBlocksEditor.document.sessionId'), 'browser-session');
      assert.equal(await evaluate('RinBlocksEditor.workspace.getAllBlocks(false).length>5'),true);
      assert.equal(await evaluate('RinBlocksEditor.document.editable'),true);
      assert.ok(await evaluate('RinBlocksEditor.workspace.scale>=0.55'));
      const comment=await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='comment');const field=RinBlocksEditor.workspace.getBlockById('bound_'+node.id).getField('F_text');return {value:field.getValue(),limit:field.maxDisplayLength,label:field.getDisplayText_()};})()`);
      assert.equal(comment.value,' '+leadingComment);
      assert.equal(comment.limit,24);
      assert.ok(comment.label.length<comment.value.length);
      const sizes=await evaluate(`({inner:innerHeight,html:document.documentElement.getBoundingClientRect().height,body:document.body.getBoundingClientRect().height,editor:document.getElementById('editor').getBoundingClientRect().height,workspace:document.getElementById('workspace').getBoundingClientRect().height,svg:document.querySelector('#workspace .blocklySvg').getBoundingClientRect().height})`);
      assert.ok(sizes.html>0&&sizes.body>0&&sizes.editor>0&&sizes.workspace>80&&sizes.svg>80,JSON.stringify(sizes));
      assert.ok(Math.abs(sizes.html-sizes.inner)<=1,JSON.stringify(sizes));
      assert.ok(Math.abs(sizes.svg-sizes.workspace)<=1,JSON.stringify(sizes));
    });
    await check('full screen requests and native view state preserve the document and readable scale',async()=>{
      const original=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      assert.equal(await evaluate('RinBlocksEditor.workspace.scale'),1);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getRenderer().name'),'edit_rin');
      await evaluate("document.getElementById('fullscreen').click()");
      await waitFor('__native.messages.some(m=>m.type==="toggleFullscreen")','full screen request');
      const request=await evaluate('__native.messages.find(m=>m.type==="toggleFullscreen")');
      assert.equal(request.workId,'work-one');
      assert.equal(request.expectedRevision,await evaluate('__native.revision'));
      await evaluate(`__port.postMessage(JSON.stringify({type:'viewState',sessionId:'browser-session',fullscreen:true}))`);
      await waitFor(`document.getElementById('fullscreen').getAttribute('aria-pressed')==='true'`,'native full screen state');
      assert.equal(await evaluate(`document.getElementById('fullscreen').textContent`),'分割表示');
      await evaluate(`__port.postMessage(JSON.stringify({type:'viewState',sessionId:'wrong-session',fullscreen:false}))`);
      await sleep(100);
      assert.equal(await evaluate(`document.getElementById('fullscreen').getAttribute('aria-pressed')`),'true');
      await evaluate(`__port.postMessage(JSON.stringify({type:'viewState',sessionId:'browser-session',fullscreen:false}))`);
      await waitFor(`document.getElementById('fullscreen').getAttribute('aria-pressed')==='false'`,'return to split view');
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),original);
      if(process.env.BLOCKS_INITIAL_SCREENSHOT_PATH){const shot=await command('Page.captureScreenshot',{format:'png'});fs.writeFileSync(process.env.BLOCKS_INITIAL_SCREENSHOT_PATH,Buffer.from(shot.data,'base64'));}
    });
    await check('a resize restores a collapsed WebView document without authoring edits',async()=>{
      const original=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      const collapsed=await evaluate(`(() => {document.documentElement.style.height='0px';return {html:document.documentElement.getBoundingClientRect().height,workspace:document.getElementById('workspace').getBoundingClientRect().height};})()`);
      assert.equal(collapsed.html,0);
      assert.equal(collapsed.workspace,0);
      await evaluate(`window.dispatchEvent(new Event('resize'))`);
      await waitFor(`document.documentElement.getBoundingClientRect().height>0 && document.getElementById('workspace').getBoundingClientRect().height>80 && Math.abs(document.documentElement.getBoundingClientRect().height-innerHeight)<=1`,'collapsed document resize recovery');
      await sleep(180);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),original);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getAllBlocks(false).length>5'),true);
    });
    await check('viewport resizing and fit keep the workspace visible without editing the work',async()=>{
      const original=await evaluate(`({source:__native.source,metadata:__native.metadata,edits:__native.edits.length,height:innerHeight,workspace:document.getElementById('workspace').getBoundingClientRect().height})`);
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:620,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerHeight===620 && Math.abs(document.documentElement.getBoundingClientRect().height-innerHeight)<=1 && document.getElementById('workspace').getBoundingClientRect().height>80 && Math.abs(document.querySelector('#workspace .blocklySvg').getBoundingClientRect().height-document.getElementById('workspace').getBoundingClientRect().height)<=1`,'shorter viewport layout');
      const shorter=await evaluate(`({inner:innerHeight,html:document.documentElement.getBoundingClientRect().height,workspace:document.getElementById('workspace').getBoundingClientRect().height,svg:document.querySelector('#workspace .blocklySvg').getBoundingClientRect().height})`);
      assert.ok(shorter.workspace<original.workspace,JSON.stringify({original,shorter}));
      assert.ok(Math.abs((original.workspace-shorter.workspace)-(original.height-shorter.inner))<=1,JSON.stringify({original,shorter}));
      await evaluate(`document.getElementById('fit').click()`);
      await sleep(180);
      assert.equal(await evaluate(`Number.isFinite(RinBlocksEditor.workspace.scale)&&RinBlocksEditor.workspace.scale>0&&document.querySelector('#workspace .blocklySvg').getBoundingClientRect().height>80`),true);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),{source:original.source,metadata:original.metadata,edits:original.edits});
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:original.height,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerHeight===${original.height} && Math.abs(document.documentElement.getBoundingClientRect().height-innerHeight)<=1 && Math.abs(document.getElementById('workspace').getBoundingClientRect().height-${original.workspace})<=1`,'original viewport restoration');
    });
    await check('a short touch pane keeps controls visible and distinct with thin scrollbar paint',async()=>{
      const original=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      await command('Emulation.setDeviceMetricsOverride',{width:339,height:250,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerHeight===250 && Math.abs(document.documentElement.getBoundingClientRect().height-250)<=1 && document.getElementById('workspace').getBoundingClientRect().height>175`,'short touch pane sizing');
      const geometry=await evaluate(`(() => {const rect=r=>({top:r.top,bottom:r.bottom,left:r.left,right:r.right,width:r.right-r.left,height:r.bottom-r.top});const w=document.getElementById('workspace');const manager=RinBlocksEditor.workspace.getComponentManager();const trashEl=document.querySelector('.blocklyTrash');return {workspace:rect(w.getBoundingClientRect()),hasZoomControls:!!manager.getComponent('zoomControls'),pinch:!!RinBlocksEditor.workspace.options.zoomOptions?.pinch,trash:rect(manager.getComponent('trashcan').getBoundingRectangle()),trashOpacity:getComputedStyle(trashEl).opacity,touch:Blockly.Touch.TOUCH_ENABLED,thickness:Blockly.Scrollbar.scrollbarThickness,handle:document.querySelector('.blocklyScrollbarVertical .blocklyScrollbarHandle').getBoundingClientRect().width,hit:document.querySelector('.blocklyScrollbarVertical').getBoundingClientRect().width,status:document.getElementById('status').getBoundingClientRect().height,statusText:document.getElementById('status').textContent,statusOverflow:getComputedStyle(document.getElementById('status')).textOverflow};})()`);
      assert.equal(geometry.touch,true);
      assert.equal(geometry.hasZoomControls,false);
      assert.equal(geometry.pinch,true);
      assert.equal(geometry.trashOpacity,'0');
      assert.equal(geometry.thickness,25);
      assert.equal(geometry.hit,25);
      assert.equal(geometry.handle,20);
      const stripe=await evaluate(`(() => {const handle=document.querySelector('.blocklyScrollbarVertical .blocklyScrollbarHandle'),r=handle.getBoundingClientRect();const gradient=document.querySelector('#workspace linearGradient[id$=vertical]');const stops=Array.from(gradient.children).map(stop=>({offset:Number.parseFloat(stop.getAttribute('offset')),opacity:stop.getAttribute('stop-opacity')}));const point={x:r.left+1,y:(r.top+r.bottom)/2};return {fill:getComputedStyle(handle).fill,events:getComputedStyle(handle).pointerEvents,stops,point,transparentEdgeHitsHandle:document.elementFromPoint(point.x,point.y)===handle,scrollY:RinBlocksEditor.workspace.scrollY};})()`);
      assert.ok(stripe.fill.includes('rin_scrollbar_'));
      assert.equal(stripe.events,'all');
      assert.equal(stripe.transparentEdgeHitsHandle,true);
      const opaque=stripe.stops.filter(stop=>stop.opacity==='1');
      assert.equal((opaque[1].offset-opaque[0].offset)/100*geometry.handle,6);
      await command('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:stripe.point.x,y:stripe.point.y}]});
      await command('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:stripe.point.x,y:stripe.point.y+16}]});
      await command('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
      await waitFor(`RinBlocksEditor.workspace.scrollY!==${stripe.scrollY}`,'drag through transparent scrollbar edge');
      assert.equal(geometry.status,22);
      assert.equal(geometry.statusOverflow,'ellipsis');
      assert.ok(geometry.statusText.includes('同じ作品のコードを編集'));
      const r=geometry.trash;assert.ok(r.top>=0&&r.left>=0&&r.right<=geometry.workspace.width&&r.bottom<=geometry.workspace.height,JSON.stringify(geometry));
      const readyText=await evaluate(`document.getElementById('status').textContent`);
      await evaluate(`(() => {const status=document.getElementById('status');status.textContent='構文エラーの詳細を確認してください。'.repeat(12);status.classList.add('error');})()`);
      await waitFor(`document.getElementById('status').scrollHeight>document.getElementById('status').clientHeight && RinBlocksEditor.workspace.getComponentManager().getComponent('trashcan').getBoundingRectangle().top>=0`,'compact scrollable diagnostic');
      assert.notEqual(await evaluate(`getComputedStyle(document.getElementById('status')).whiteSpace`),'nowrap');
      await evaluate(`(() => {const status=document.getElementById('status');status.textContent=${JSON.stringify(readyText)};status.classList.remove('error');})()`);
      await waitFor(`document.getElementById('status').getBoundingClientRect().height===22`,'ready status restoration');
      await evaluate(`RinBlocksEditor.workspace.setScale(0.75);document.getElementById('more').click()`);
      assert.equal(await evaluate(`document.getElementById('moreMenu').hidden`),false);
      await evaluate(`document.getElementById('zoomReset').click()`);
      assert.equal(await evaluate('RinBlocksEditor.workspace.scale'),1);
      assert.equal(await evaluate(`document.getElementById('moreMenu').hidden`),true);
      await evaluate('RinBlocksEditor.workspace.scroll(-10,-15)');
      await sleep(180);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),original);
      if(process.env.BLOCKS_SHORT_SCREENSHOT_PATH){const screenshot=await command('Page.captureScreenshot',{format:'png'});fs.writeFileSync(process.env.BLOCKS_SHORT_SCREENSHOT_PATH,Buffer.from(screenshot.data,'base64'));}
    });
    await check('touch swipes over blocks pan the workspace while holding grabs the block and tapping shows actions',async()=>{
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
      await waitFor('innerHeight===844 && document.getElementById("workspace").getBoundingClientRect().height>700','full pane for touch test');
      const original=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      const target=await evaluate(`(() => {RinBlocksEditor.workspace.scroll(0,0);const b=RinBlocksEditor.workspace.getBlockById('rin_program');const r=b.getSvgRoot().querySelector('.blocklyPath').getBoundingClientRect();const xy=b.getRelativeToSurfaceXY();return {x:Math.round(r.left+28),y:Math.round(r.top+14),bx:xy.x,by:xy.y,scrollX:RinBlocksEditor.workspace.scrollX,scrollY:RinBlocksEditor.workspace.scrollY};})()`);
      await command('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:target.x,y:target.y}]});
      await command('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:target.x,y:target.y+40}]});
      await command('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
      const afterSwipe=await evaluate(`(() => {const b=RinBlocksEditor.workspace.getBlockById('rin_program');const xy=b.getRelativeToSurfaceXY();return {bx:xy.x,by:xy.y,scrollY:RinBlocksEditor.workspace.scrollY,actionsHidden:document.getElementById('blockActions').hidden};})()`);
      assert.equal(afterSwipe.bx,target.bx);
      assert.equal(afterSwipe.by,target.by);
      assert.notEqual(afterSwipe.scrollY,target.scrollY);
      assert.equal(afterSwipe.actionsHidden,true);
      await sleep(180);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),original);
      const tapPoint=await evaluate(`(() => {RinBlocksEditor.workspace.scroll(0,0);const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='circle');const b=RinBlocksEditor.workspace.getBlockById('bound_'+node.id);const r=b.getSvgRoot().querySelector('.blocklyPath').getBoundingClientRect();return {x:Math.round(r.left+20),y:Math.round(r.top+12)};})()`);
      await command('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:tapPoint.x,y:tapPoint.y}]});
      await command('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
      await waitFor(`!document.getElementById('blockActions').hidden`,'block actions shown on tap');
      const labels=await evaluate(`Array.from(document.querySelectorAll('#blockActions button span')).map(s=>s.textContent)`);
      assert.ok(labels.includes('項目を編集')&&labels.includes('コード')&&labels.includes('複製')&&labels.includes('削除'),JSON.stringify(labels));
      await command('Emulation.setDeviceMetricsOverride',{width:339,height:250,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerHeight===250`,'return to short pane');
    });
    await check('the palette and help dialog scroll within a short touch pane',async()=>{
      const original=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      await evaluate(`document.getElementById('paletteToggle').click()`);
      const palette=await evaluate(`(() => {const p=document.getElementById('palette'),items=document.getElementById('paletteItems');items.scrollTop=items.scrollHeight;const last=items.lastElementChild.getBoundingClientRect(),r=p.getBoundingClientRect();return {hidden:p.hidden,top:r.top,bottom:r.bottom,height:r.height,listHeight:items.clientHeight,listScroll:items.scrollHeight,lastBottom:last.bottom};})()`);
      assert.equal(palette.hidden,false);
      assert.ok(palette.top>=0&&palette.bottom<=250&&palette.listHeight>0&&palette.listScroll>palette.listHeight,JSON.stringify(palette));
      assert.ok(palette.lastBottom<=palette.bottom,JSON.stringify(palette));
      await evaluate(`document.getElementById('paletteToggle').click();document.getElementById('help').click()`);
      await waitFor(`document.getElementById('actionDialog').open`,'short help dialog');
      const dialog=await evaluate(`(() => {const d=document.getElementById('actionDialog');d.scrollTop=d.scrollHeight;const r=d.getBoundingClientRect(),cancel=document.getElementById('dialogCancel').getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height,client:d.clientHeight,scroll:d.scrollHeight,cancelTop:cancel.top,cancelBottom:cancel.bottom};})()`);
      assert.ok(dialog.top>=0&&dialog.bottom<=250&&dialog.scroll>dialog.client,JSON.stringify(dialog));
      assert.ok(dialog.cancelTop>=dialog.top&&dialog.cancelBottom<=dialog.bottom,JSON.stringify(dialog));
      await evaluate(`document.getElementById('dialogCancel').click()`);
      await waitFor(`!document.getElementById('actionDialog').open`,'help dismissal');
      await sleep(180);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),original);
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
      await waitFor('innerHeight===844 && document.getElementById("workspace").getBoundingClientRect().height>700','restore long pane');
    });
    await check('opening the real worker-backed page does not edit source or metadata',async()=>{
      await sleep(150);
      assert.equal(await evaluate('__native.edits.length'),0);
      assert.equal(await evaluate('RinBlocksEditor.projection.source===__native.source'),true);
      if(process.env.BLOCKS_SCREENSHOT_PATH){
        const screenshot=await command('Page.captureScreenshot',{format:'png'});
        fs.writeFileSync(process.env.BLOCKS_SCREENSHOT_PATH,Buffer.from(screenshot.data,'base64'));
      }
    });
    await check('an example dialog cannot replace newer native edits',async()=>{
      await evaluate(`document.getElementById('examples').click()`);
      await waitFor(`document.getElementById('actionDialog').open`,'example chooser');
      await evaluate(`document.querySelector('#dialogChoices button').click()`);
      await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent.includes('置き換')`,'example confirmation');
      const newer=initialSource+'// newer native edit\n';
      await evaluate(`__native.source=${JSON.stringify(newer)};__load()`);
      await waitFor(`RinBlocksEditor.document.source===${JSON.stringify(newer)} && !RinBlocksEditor.document.loading`,'native edit while chooser open');
      await evaluate(`document.querySelector('#dialogChoices button').click()`);
      await sleep(250);
      assert.equal(await evaluate('__native.source'),newer);
      assert.equal(await evaluate('__native.edits.length'),0);
      await evaluate(`__native.source=${JSON.stringify(initialSource)};__load()`);
    });
    await check('Blockly numeric field edits preserve unrelated source and annotations',async()=>{
      const original=await evaluate('__native.source');
      await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value===80);const block=RinBlocksEditor.workspace.getBlockById('bound_'+node.id);block.setFieldValue('96','F_value');})()`);
      await waitFor('__native.source.includes("mouseY, 96")','field commit');
      assert.equal(await evaluate('__native.source'),original.replace('mouseY, 80','mouseY, 96'));
    });
    await check('a stale native rejection restores acknowledged source',async()=>{
      await evaluate('__native.rejectNext=true');
      await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&Number(n.fields.value.value)===96);RinBlocksEditor.workspace.getBlockById('bound_'+node.id).setFieldValue('97','F_value');})()`);
      await sleep(450);
      assert.equal(await evaluate('__native.source.includes("mouseY, 96")'),true);
      assert.equal(await evaluate('RinBlocksEditor.projection.source===__native.source'),true);
      assert.equal(await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&Number(n.fields.value.value)===96);return Number(RinBlocksEditor.workspace.getBlockById('bound_'+node.id).getFieldValue('F_value'));})()`),96);
    });
    await check('layout-only acknowledgements advance revision and unrelated replies cannot finish an edit',async()=>{
      const before=await evaluate('({source:__native.source,revision:RinBlocksEditor.document.revision})');
      await evaluate(`__native.holdNextAck=true;RinBlocksEditor.workspace.getBlockById('rin_program').moveBy(3,0)`);
      await waitFor('!!__native.heldAck && RinBlocksEditor.document.pending','held layout acknowledgement');
      await evaluate(`__port.postMessage(JSON.stringify(Object.assign({},__native.heldAck,{requestId:'old-request',source:'corrupt source',revision:'old-revision'})))`);
      await sleep(80);
      assert.equal(await evaluate('RinBlocksEditor.document.pending'),true);
      assert.equal(await evaluate('RinBlocksEditor.document.source'),before.source);
      await evaluate(`__port.postMessage(JSON.stringify(__native.heldAck));__native.heldAck=null`);
      await waitFor('!RinBlocksEditor.document.pending && RinBlocksEditor.document.revision===__native.revision','layout revision applied');
      assert.notEqual(await evaluate('RinBlocksEditor.document.revision'),before.revision);
      assert.equal(await evaluate('__native.source'),before.source);
    });
    await check('a newer same-source layout supersedes a pending old operation and its late reply',async()=>{
      const source=await evaluate('__native.source');
      await evaluate(`__native.holdNextAck=true;__native.rejectNext=true;RinBlocksEditor.workspace.getBlockById('rin_program').moveBy(4,0)`);
      await waitFor('!!__native.heldAck && RinBlocksEditor.document.pending','held stale layout');
      await evaluate(`(() => {const m=JSON.parse(__native.metadata);m.positions.program.x+=7;__native.metadata=JSON.stringify(m);__load();})()`);
      await waitFor('!RinBlocksEditor.document.pending && !RinBlocksEditor.document.loading && RinBlocksEditor.document.revision===__native.revision','new same-source native layout');
      const revision=await evaluate('RinBlocksEditor.document.revision');
      await evaluate(`__port.postMessage(JSON.stringify(__native.heldAck));__native.heldAck=null`);
      await sleep(100);
      assert.equal(await evaluate('RinBlocksEditor.document.revision'),revision);
      assert.equal(await evaluate('RinBlocksEditor.document.metadata'),await evaluate('__native.metadata'));
      assert.equal(await evaluate('__native.source'),source);
      assert.equal(await evaluate('RinBlocksEditor.document.editable'),true);
    });
    await check('the tap palette inserts a statement inside the selected function',async()=>{
      await evaluate(`(() => {const statement=RinBlocksEditor.projection.nodes.find(n=>n.kind==='expression_statement'&&n.raw==='background(240);');window.__insertBlock=RinBlocksEditor.workspace.getBlockById('bound_'+statement.id);})()`);
      await evaluate(`RinBlocksEditor.addTemplate('rect','after',window.__insertBlock)`);
      await waitFor('__native.source.includes("rect(")','tap insertion');
      assert.equal(await evaluate('__native.source.includes("// @rin preserve")'),true);
      await waitFor('RinBlocksEditor.projection.source===__native.source','insert projection');
      assert.equal(await evaluate(`(() => {const statement=RinBlocksEditor.projection.nodes.find(n=>n.kind==='expression_statement'&&n.raw.startsWith('rect('));return RinBlocksEditor.projection.nodes.find(n=>n.id===statement.parentId)?.fields.name?.value==='draw';})()`),true);
    });
    await check('a native flush waits for a field edit queued on the same turn',async()=>{
      const result=await evaluate(`(async () => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&Number(n.fields.value.value)===96);RinBlocksEditor.workspace.getBlockById('bound_'+node.id).setFieldValue('99','F_value');const replies=await Promise.all([__flush(),__flush()]);return {ok:replies.every(Boolean),status:RinBlocksEditor.status,document:RinBlocksEditor.document,source:__native.source,actions:__native.actions.slice(-8)};})()`);
      assert.equal(result.ok,true,JSON.stringify(result));
      assert.equal(await evaluate('__native.source.includes("mouseY, 99")'),true,JSON.stringify(result));
      assert.equal(await evaluate('__native.flushSources.slice(-2).every(source=>source.includes("mouseY, 99"))'),true);
      assert.equal(await evaluate('RinBlocksEditor.projection.source===__native.source'),true);
    });
    await check('auto-draft captures open field values without closing the input or committing history',async()=>{
      const before=await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})');
      assert.equal(await evaluate('__flush(false)'),true);
      assert.equal(await evaluate('__native.draft'),null,'an idle workspace must not invent an input draft');
      await evaluate(`(()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&Number(n.fields.value.value)===99);window.__draftField=RinBlocksEditor.workspace.getBlockById('bound_'+node.id).getField('F_value');__draftField.showEditor();const input=document.querySelector('.blocklyHtmlInput');input.value='123';input.dispatchEvent(new Event('input',{bubbles:true}));})()`);
      await waitFor(`__native.messages.some(m=>m.type==='draftChanged')`,'open input schedules auto-draft');
      for(const value of ['123','124']){
        await evaluate(`(()=>{const input=document.querySelector('.blocklyHtmlInput');input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}));})()`);
        assert.equal(await evaluate('__flush(false)'),true);
        const draft=await evaluate('__native.draft');
        assert.equal(draft.expectedSource,before.source);
        let source=draft.expectedSource;
        for(const patch of draft.patches.slice().sort((a,b)=>b.start-a.start))source=source.slice(0,patch.start)+patch.text+source.slice(patch.end);
        assert.ok(source.includes('mouseY, '+value),source);
        assert.equal(await evaluate('Blockly.WidgetDiv.isVisible()'),true);
        assert.equal(await evaluate(`document.querySelector('.blocklyHtmlInput').value`),value);
        assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),before);
      }
      await evaluate(`(()=>{const input=document.querySelector('.blocklyHtmlInput');input.value='-';input.dispatchEvent(new Event('input',{bubbles:true}));})()`);
      await waitFor(`!!document.querySelector('.blocklyHtmlInput.blocklyInvalidInput')`,'incomplete number remains in the editor');
      assert.equal(await evaluate('__flush(false)'),false,'an incomplete number cannot be reported as a valid snapshot');
      assert.equal(await evaluate(`document.querySelector('.blocklyHtmlInput').value`),'-');
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),before);
      await evaluate(`document.querySelector('.blocklyHtmlInput').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))`);
      assert.equal(await evaluate('__flush(false)'),true);
      assert.equal(await evaluate('__native.draft'),null,'Escape must not recover the cancelled input');
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})'),before);
    });
    await check('detached parts and their moved positions survive reload without editing source',async()=>{
      const source=await evaluate('__native.source');
      await evaluate('RinBlocksEditor.addTemplate("rect","tray")');
      await waitFor('JSON.parse(__native.metadata||"{}").orphans?.length===1','orphan save');
      assert.equal(await evaluate('__native.source'),source);
      const before=await evaluate('JSON.parse(__native.metadata).orphans[0].x');
      await evaluate('RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.id!=="rin_program").moveBy(40,20)');
      assert.equal(await evaluate('__flush()'),true);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans[0].x'),before+40);
      await evaluate('__load()');
      await waitFor('RinBlocksEditor.workspace.getTopBlocks(false).length===2','orphan reload');
      assert.equal(await evaluate('RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.id!=="rin_program").getRelativeToSurfaceXY().x'),before+40);
      assert.equal(await evaluate('__native.source'),source);
    });
    await check('pan and zoom do not create authoring edits',async()=>{
      await evaluate('__flush()');
      const edits=await evaluate('__native.edits.length');
      await evaluate('RinBlocksEditor.workspace.scroll(-20,-25);RinBlocksEditor.workspace.setScale(0.75)');
      await sleep(180);
      assert.equal(await evaluate('__native.edits.length'),edits);
    });
    await check('collapsed blocks persist as authoring metadata without changing source',async()=>{
      const source=await evaluate('__native.source');
      assert.equal(await evaluate(`(async()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function'&&n.fields.name.value==='setup');window.__foldId='bound_'+node.id;RinBlocksEditor.workspace.getBlockById(__foldId).setCollapsed(true);return __flush();})()`),true);
      assert.equal(await evaluate('JSON.parse(__native.metadata).collapsed.includes(__foldId)'),true);
      await evaluate('__load()');
      await waitFor('RinBlocksEditor.workspace.getBlockById(__foldId)?.isCollapsed()','collapsed reload');
      assert.equal(await evaluate('__native.source'),source);
      await evaluate('RinBlocksEditor.workspace.getBlockById(__foldId).setCollapsed(false);__flush()');
    });
    await check('a detached value connects into an occupied socket and preserves the displaced part',async()=>{
      await evaluate('RinBlocksEditor.addTemplate("number","tray")');
      await waitFor('JSON.parse(__native.metadata).orphans.length===2','value tray');
      await waitFor('RinBlocksEditor.document.pending===false && RinBlocksEditor.workspace.getTopBlocks(false).some(b=>b.isEditable()&&b.outputConnection&&Number(b.getFieldValue("F_value"))===80)','value projection');
      const result=await evaluate(`(async () => {window.__moves=[];RinBlocksEditor.workspace.addChangeListener(e=>{if(e.type==='move')__moves.push(e.toJson());});const call=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='circle');const value=RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.outputConnection&&Number(b.getFieldValue('F_value'))===80);RinBlocksEditor.workspace.getBlockById('bound_'+call.id).getInput('I_args_2').connection.connect(value.outputConnection);const ok=await __flush();return {ok,status:RinBlocksEditor.status,source:__native.source,moves:__moves,flush:window.__lastFlush};})()`);
      assert.equal(result.ok,true,JSON.stringify(result));
      assert.equal(await evaluate('__native.source.includes("mouseY, 80")'),true);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.some(x=>x.expression&&x.source==="99")'),true);
    });
    await check('moving a connected value swaps sockets without creating invalid intermediate code',async()=>{
      assert.equal(await evaluate(`(() => {const canvas=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='createCanvas');const circle=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='circle');const value=RinBlocksEditor.workspace.getBlockById('bound_'+canvas.inputs.args[1]);value.unplug();RinBlocksEditor.workspace.getBlockById('bound_'+circle.id).getInput('I_args_2').connection.connect(value.outputConnection);return __flush();})()`),true);
      assert.equal(await evaluate('__native.source.includes("createCanvas(400, 80)")'),true);
      assert.equal(await evaluate('__native.source.includes("mouseY, 400)")'),true);
    });
    await check('the image loader chooses a native asset and prepares async setup',async()=>{
      await evaluate(`void RinBlocksEditor.addTemplate('imageLoader','inside').catch(e=>window.__menuError=e.message)`);
      await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='読み込む画像'`,'image picker');
      assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('#dialogChoices button')).map(b=>b.textContent)`),['image.png']);
      await evaluate(`document.querySelector('#dialogChoices button').click()`);await waitFor('__native.source.includes("await loadImage(")','async loader');
      assert.equal(await evaluate('__native.source.includes("async function setup()")'),true);
      assert.equal(await evaluate(`__native.source.includes('loadImage("image.png")')`),true);
    });
    const loadSource=async source=>{await evaluate(`__native.source=${JSON.stringify(source)};__native.metadata=null;__load({language:'ja',dark:false,readOnly:false})`);await waitFor('RinBlocksEditor.document.source===__native.source && RinBlocksEditor.document.revision===__native.revision && !RinBlocksEditor.document.loading && !RinBlocksEditor.document.processing && RinBlocksEditor.document.editable','test document');};
    const openItems=async predicate=>{await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>${predicate});RinBlocksEditor.editStructure(RinBlocksEditor.workspace.getBlockById('bound_'+node.id)).catch(e=>window.__menuError=e.message);})()`);await waitFor(`document.getElementById('actionDialog').open`,'item menu');};
    const clickChoice=async text=>{await evaluate(`(() => {const b=Array.from(document.querySelectorAll('#dialogChoices button')).find(b=>b.textContent.includes(${JSON.stringify(text)}));if(!b)throw new Error('Choice missing: '+${JSON.stringify(text)});b.click();})()`);};
    const applyText=async text=>{await waitFor(`document.getElementById('actionDialog').open && !!document.querySelector('#dialogChoices input[type=text]')`,'name entry');await evaluate(`document.querySelector('#dialogChoices input[type=text]').value=${JSON.stringify(text)}`);await clickChoice('適用');};
    const settled=()=>waitFor('!RinBlocksEditor.document.pending && !RinBlocksEditor.document.loading && !RinBlocksEditor.document.processing && RinBlocksEditor.document.revision===__native.revision && RinBlocksEditor.projection.source===__native.source','semantic edit');
    await check('a declaration rename updates matching uses while preserving shadowed names and comments',async()=>{
      const source='// size must stay in this comment\nlet size = 80;\nfunction draw(){circle(10,20,size);}\nfunction other(size){return size;}\nconst label="size";\n';await loadSource(source);
      assert.equal(await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='identifier'&&n.declaration&&n.fields.name.value==='size');RinBlocksEditor.workspace.getBlockById('bound_'+n.id).setFieldValue('diameter','F_name');return __flush();})()`),true);
      const result=await evaluate('__native.source');assert.ok(result.includes('let diameter = 80;'));assert.ok(result.includes('circle(10,20,diameter)'));assert.ok(result.includes('function other(size){return size;}'));assert.ok(result.includes('// size must stay'));assert.ok(result.includes('"size"'));
      await openItems(`n.kind==='identifier'&&n.declaration&&n.fields.name.value==='diameter'`);await clickChoice('まとめて');await applyText('label');await sleep(250);assert.equal(await evaluate('__native.source'),result);assert.ok(await evaluate('!!window.__menuError'));await evaluate('__menuError=null');
    });
    await check('array and call item menus add and remove sockets without losing comments',async()=>{
      await loadSource('const values = [10, /* keep item */ 20];\nfunction draw(){circle(10, 20, 30);}\n');
      await openItems(`n.kind==='array'`);await clickChoice('追加');await waitFor(`RinBlocksEditor.projection.nodes.find(n=>n.kind==='array').inputs.elements.length===3`,'added array slot');await settled();
      assert.ok((await evaluate('__native.source')).includes('/* keep item */'));
      await openItems(`n.kind==='array'`);await clickChoice('3番目');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='項目を削除'`,'delete item confirmation');await clickChoice('削除する');await waitFor(`RinBlocksEditor.projection.nodes.find(n=>n.kind==='array').inputs.elements.length===2`,'removed array slot');await settled();
      await openItems(`n.kind==='p5_call'&&n.calleeName==='circle'`);await clickChoice('追加');await waitFor(`RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='circle').inputs.args.length===4`,'added call slot');await settled();
      assert.ok((await evaluate('__native.source')).includes('/* keep item */'));
    });
    await check('function parameter menus synchronize calls and rename only their own uses',async()=>{
      await loadSource('function shape(x){return x+1;}\nfunction draw(){shape(4);}\n');
      await openItems(`n.kind==='function'&&n.fields.name.value==='shape'`);await clickChoice('受け取る値を追加');await applyText('amount');await waitFor(`__native.source.includes('shape(4, 0)')`,'parameter caller sync');await settled();
      await openItems(`n.kind==='function'&&n.fields.name.value==='shape'`);await clickChoice('x の名前');await applyText('position');await waitFor(`__native.source.includes('return position+1')`,'parameter use rename');await settled();
      await openItems(`n.kind==='function'&&n.fields.name.value==='shape'`);await clickChoice('amount を削除');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='受け取る値を削除'`,'parameter removal confirmation');await clickChoice('削除する');await waitFor(`RinBlocksEditor.projection.nodes.find(n=>n.kind==='function'&&n.fields.name.value==='shape').parameters.length===1`,'parameter removal');await settled();
      assert.match(await evaluate('__native.source'),/shape\(4\s*\)/);
      await openItems(`n.kind==='function'&&n.fields.name.value==='shape'`);await clickChoice('まとめて');await applyText('drawShape');await waitFor(`RinBlocksEditor.projection.nodes.some(n=>n.kind==='call'&&n.calleeName==='drawShape'&&n.inputs.args.length===1)`,'function caller rename');await settled();
    });
    await check('a call can choose a visible custom function and resize its arguments atomically',async()=>{
      await loadSource('function one(x){return x;}\nfunction two(x,y){return x+y;}\nfunction draw(){one(12);}\n');
      await openItems(`n.kind==='call'&&n.calleeName==='one'`);await clickChoice('呼び出す関数を選ぶ');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='呼び出す関数'`,'function picker');await clickChoice('two(');await waitFor(`__native.source.includes('two(12, 0)')`,'function picker edit');await settled();
    });
    await check('else can be added, filled through tap insertion and removed through confirmation',async()=>{
      await loadSource('function draw(){if(mouseIsPressed){circle(1,2,3);}}\n');
      await openItems(`n.kind==='if'`);await clickChoice('追加');await waitFor(`!!RinBlocksEditor.projection.nodes.find(n=>n.kind==='if').inputs.alternate`,'else insertion');await settled();
      await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='if');return RinBlocksEditor.addTemplate('rect','inside',RinBlocksEditor.workspace.getBlockById('bound_'+n.inputs.alternate));})()`);await waitFor(`__native.source.includes('rect(')`,'inside else insertion');await settled();
      await openItems(`n.kind==='if'`);await clickChoice('削除');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='そうでなければを削除'`,'else delete confirmation');await clickChoice('削除する');await waitFor(`!RinBlocksEditor.projection.nodes.find(n=>n.kind==='if').inputs.alternate`,'else removal');await settled();
      assert.equal((await evaluate('__native.source')).includes('rect('),false);assert.ok((await evaluate('__native.source')).includes('circle(1,2,3)'));
    });
    await check('detached item edits update only saved parts and accept the name cancel',async()=>{
      await loadSource('function draw(){background(240); }\n');const source=await evaluate('__native.source');
      await evaluate('RinBlocksEditor.addTemplate("array","tray")');await settled();
      await evaluate(`void RinBlocksEditor.editStructure(RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.outputConnection)).catch(e=>__menuError=e.message)`);await waitFor(`document.getElementById('actionDialog').open`,'detached array menu');await clickChoice('追加');await waitFor(`JSON.parse(__native.metadata).orphans.some(o=>o.expression&&o.source.includes('0]'))`,'detached array increase');await settled();assert.equal(await evaluate('__native.source'),source);
      await evaluate('RinBlocksEditor.addTemplate("function","tray")');await settled();
      await evaluate(`void RinBlocksEditor.editStructure(RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.getField('F_name')?.getValue()==='drawShape')).catch(e=>__menuError=e.message)`);await waitFor(`document.getElementById('actionDialog').open`,'detached function menu');await clickChoice('x の名前');await applyText('cancel');await waitFor(`JSON.parse(__native.metadata).orphans.some(o=>o.source.includes('circle(cancel, y'))`,'detached parameter rename');await settled();assert.equal(await evaluate('__native.source'),source);
    });
    const author=async id=>{await evaluate(`void RinBlocksEditor.addTemplate(${JSON.stringify(id)},'append').catch(e=>window.__menuError=e.message)`);};
    await check('event additions stay top-level, reuse existing handlers and respect runtime support',async()=>{
      await loadSource('function outer(){function keyPressed(){return 1;}}\nfunction setup(){createCanvas(100,100);}\n');
      await evaluate(`RinBlocksEditor.addTemplate('keyPressed','append')`);await settled();
      const events=await evaluate(`RinBlocksEditor.projection.nodes.filter(n=>n.kind==='function'&&n.fields.name.value==='keyPressed').map(n=>({event:n.event,parent:n.parentId||null}))`);assert.equal(events.length,2);assert.equal(events.filter(n=>n.event&&!n.parent).length,1);
      const edits=await evaluate('__native.edits.length');await evaluate(`RinBlocksEditor.addTemplate('keyPressed','append')`);await settled();assert.equal(await evaluate('__native.edits.length'),edits);
      assert.equal(await evaluate(`RinBlocksEditor.addTemplate('touchStarted','append').then(()=>false,()=>true)`),true);
      await evaluate(`document.getElementById('paletteToggle').click();document.getElementById('search').value='touchStarted';document.getElementById('search').dispatchEvent(new Event('input'))`);assert.equal(await evaluate(`document.querySelectorAll('#paletteItems .palette-item').length`),0);await evaluate(`document.getElementById('paletteToggle').click();document.getElementById('search').value='';document.getElementById('search').dispatchEvent(new Event('input'))`);
    });
    await check('loaded image selection creates a global resource usable from draw and filters paths by MIME',async()=>{
      await loadSource('// keep image note\nfunction setup(){createCanvas(100,100);}\nfunction draw(){background(240);}\n');
      await author('imageLoader');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='読み込む画像'`,'image file chooser');await clickChoice('image.png');await waitFor(`__native.source.includes('loadImage("image.png")')`,'exact image path');await settled();
      const info=await evaluate('RinBlocksEditor.authoringInspect()');assert.equal(info.resources.filter(r=>r.kind==='image').length,1);const resource=info.resources.find(r=>r.kind==='image').name;
      assert.equal(await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value==='image.png');return RinBlocksEditor.workspace.getBlockById('bound_'+n.id).getField('F_value').getOptions(false).some(x=>x[1]==='sound.wav');})()`),false);
      await author('image');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='使う素材・音'`,'image variable chooser');await clickChoice(resource);await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='いつ実行するか'`,'image handler chooser');await clickChoice('毎フレーム');await waitFor(`__native.source.includes('image('+${JSON.stringify(resource)})`,'draw loaded image');await settled();
      assert.ok((await evaluate('__native.source')).includes('// keep image note'));assert.equal((await evaluate('RinBlocksEditor.authoringInspect()')).resources.find(r=>r.name===resource).kind,'image');
    });
    await check('audio settings stay explicit and p5 1 audio loading uses preload and a chosen input event',async()=>{
      await loadSource('function setup(){createCanvas(100,100);}\n');await evaluate(`__load({soundEnabled:false})`);await settled();const source=await evaluate('__native.source');
      await author('soundLoader');await waitFor(`document.getElementById('actionDialog').open`,'disabled audio hint');await clickChoice('実行環境を開く');await waitFor(`__native.messages.some(m=>m.type==='openRuntime')`,'runtime navigation');assert.equal(await evaluate('__native.source'),source);
      await evaluate(`__load({p5Version:'1.11.5',soundEnabled:true})`);await settled();await author('soundLoader');await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='読み込む音'`,'audio file chooser');assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('#dialogChoices button')).map(b=>b.textContent)`),['sound.wav']);await clickChoice('sound.wav');await waitFor(`__native.source.includes('loadSound("sound.wav")')`,'legacy audio load');await settled();
      assert.ok((await evaluate('__native.source')).includes('function preload()'));assert.equal((await evaluate('__native.source')).includes('await loadSound'),false);
      const resource=(await evaluate('RinBlocksEditor.authoringInspect()')).resources.find(r=>r.kind==='sound').name;
      await author('soundPlay');await waitFor(`document.getElementById('actionDialog').open`,'audio resource chooser');await clickChoice(resource);await waitFor(`document.getElementById('actionDialog').open && document.getElementById('dialogTitle').textContent==='いつ実行するか'`,'audio handler chooser');await clickChoice('mousePressed');await waitFor(`__native.source.includes('.play(')`,'audio playback');await settled();assert.ok((await evaluate('__native.source')).includes('userStartAudio'));
    });
    await check('3D requires confirmation and preserves the original canvas dimensions',async()=>{
      await loadSource('function setup(){createCanvas(120,90);}\nfunction draw(){background(24);}\n');const source=await evaluate('__native.source');
      await author('webglCanvas');await waitFor(`document.getElementById('actionDialog').open`,'3D confirmation');await evaluate(`document.getElementById('dialogCancel').click()`);await sleep(120);assert.equal(await evaluate('__native.source'),source);
      await author('webglCanvas');await waitFor(`document.getElementById('actionDialog').open`,'3D confirmation again');await clickChoice('3Dに変更');await waitFor(`__native.source.includes('WEBGL')`,'WEBGL canvas');await settled();const info=await evaluate('RinBlocksEditor.authoringInspect()');assert.equal(info.webgl,true);assert.match(await evaluate('__native.source'),/createCanvas\(120,90,\s*WEBGL\)/);
      await evaluate(`(() => {const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function'&&n.fields.name.value==='draw');return RinBlocksEditor.addTemplate('box','inside',RinBlocksEditor.workspace.getBlockById('bound_'+fn.id));})()`);await waitFor(`__native.source.includes('box(100)')`,'3D box insertion');await settled();
    });
    await check('a syntax error keeps source intact and locks block editing',async()=>{
      await evaluate('__load({source:"function draw( {",revision:"broken"})');
      await waitFor('RinBlocksEditor.document.editable===false','syntax lock');
      const edits=await evaluate('__native.edits.length');
      await sleep(100);
      assert.equal(await evaluate('__native.edits.length'),edits);
    });
    await check('read-only samples remain navigable without editing',async()=>{
      await evaluate('__load({readOnly:true,language:"en"})');
      await waitFor('RinBlocksEditor.document.editable===false && RinBlocksEditor.projection?.ok','read-only load');
      const count=await evaluate('__native.edits.length');
      assert.equal(await evaluate('RinBlocksEditor.addTemplate("rect","append").then(()=>false,()=>true)'),true);
      await sleep(150);
      assert.equal(await evaluate('__native.edits.length'),count);
    });
    await check('Chinese, dark theme, custom source and sound/3D templates load offline',async()=>{
      await evaluate(`__native.source+=${JSON.stringify('\nclass Particle { move() { this.x++; } }')};__load({language:"zh",dark:true,revision:"custom"})`);
      await waitFor('RinBlocksEditor.projection?.source.includes("class Particle")','custom projection');
      assert.equal(await evaluate('RinBlocksEditor.projection.nodes.some(n=>n.kind==="raw_statement")'),true);
      assert.equal(await evaluate('EditRinBlockCatalogue.EXAMPLES.some(x=>x.id==="sound") && EditRinBlockCatalogue.EXAMPLES.some(x=>x.id==="three")'),true);
      assert.equal(await evaluate('document.documentElement.lang'), 'zh');
      if(process.env.BLOCKS_DARK_SCREENSHOT_PATH){const shot=await command('Page.captureScreenshot',{format:'png'});fs.writeFileSync(process.env.BLOCKS_DARK_SCREENSHOT_PATH,Buffer.from(shot.data,'base64'));}
      await evaluate(`(() => {const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='raw_statement'&&n.raw.startsWith('class Particle'));return Blockly.ContextMenuRegistry.registry.getItem('rin_edit_source').callback({block:RinBlocksEditor.workspace.getBlockById('bound_'+node.id)});})()`);
      await waitFor('__native.messages.some(m=>m.type==="openCode")','custom code navigation');
      assert.equal(await evaluate('__native.messages.filter(m=>m.type==="openCode").at(-1).start===__native.source.indexOf("class Particle")'),true);
    });
    await check('an invalid connection flushed immediately restores the acknowledged workspace',async()=>{
      await loadSource('circle(1,2,3);');
      const before=await evaluate('({source:__native.source,metadata:__native.metadata})');
      const reply=await evaluate(`(async()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value===1);RinBlocksEditor.workspace.getBlockById('bound_'+node.id).unplug();return __flush();})()`);
      assert.equal(reply,false);
      assert.deepEqual(await evaluate('({source:__native.source,metadata:__native.metadata})'),before);
      assert.equal(await evaluate(`(()=>{const call=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call');return !!RinBlocksEditor.workspace.getBlockById('bound_'+call.id).getInputTargetBlock('I_args_0');})()`),true);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getTopBlocks(false).length'),1);
    });
    await check('rebuilding edited text releases obsolete block definitions',async()=>{
      await loadSource('text("definition-a",0,0);');
      const count=await evaluate(`Object.keys(Blockly.Blocks).filter(k=>/^rin_[0-9a-z]+$/.test(k)).length`);
      for(const value of ['definition-b','definition-c','definition-d'])await loadSource('text('+JSON.stringify(value)+',0,0);');
      assert.equal(await evaluate(`Object.keys(Blockly.Blocks).filter(k=>/^rin_[0-9a-z]+$/.test(k)).length`),count);
    });
    await check('connecting a detached statement stack preserves every statement in order',async()=>{
      for(const tail of ['', 'rect(4,5,6,7);']){
      await loadSource('function draw(){'+tail+'}');
      await evaluate(`__native.metadata=JSON.stringify({version:1,orphans:[{id:'stack',source:'background(12);\\ncircle(1,2,3);',expression:false,x:300,y:60}]});__load()`);
      await waitFor(`!RinBlocksEditor.document.loading && RinBlocksEditor.workspace.getTopBlocks(false).some(b=>b.rinOrphanId==='stack'&&b.getNextBlock())`,'detached statement stack');
      const connected=await evaluate(`(async()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');const top=RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.rinOrphanId==='stack');RinBlocksEditor.workspace.getBlockById('bound_'+node.id).getInput('I_body').connection.connect(top.previousConnection);const ok=await __flush();return {ok,status:RinBlocksEditor.status,source:__native.source,flush:__lastFlush};})()`);
      assert.equal(connected.ok,true,JSON.stringify({tail,connected}));
      assert.match(await evaluate('__native.source'),/background\(12\);[\s\S]*circle\(1,2,3\);/);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),0);
      if(tail){const source=await evaluate('__native.source');assert.ok(source.indexOf('circle(1,2,3);')<source.indexOf(tail));assert.equal(source.split(tail).length-1,1);}
      }
    });
    await check('dragging a connected stack into the workspace moves it without leaving a source copy',async()=>{
      await loadSource('function draw(){circle(10,20,30);rect(40,50,60,70);}');
      const detached=await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='expression_statement'&&n.raw.startsWith('circle('));const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);block.unplug(false);block.moveBy(100,50);const ok=await __flush();return {ok,source:__native.source,parts:JSON.parse(__native.metadata).orphans};})()`);
      assert.equal(detached.ok,true);assert.equal(detached.source.includes('circle('),false);assert.equal(detached.source.includes('rect('),false);
      assert.equal(detached.parts.length,1);assert.equal(detached.parts[0].source,'circle(10,20,30);\nrect(40,50,60,70);');
      assert.equal(await evaluate(`RinBlocksEditor.workspace.getAllBlocks(false).filter(block=>block.getInput('TITLE')?.fieldRow.some(field=>field.getText()?.includes('circle'))).length`),1);
      await evaluate('__load()');await settled();
      assert.equal(await evaluate(`RinBlocksEditor.workspace.getTopBlocks(false).filter(block=>block.id!=='rin_program').length`),1);
      const source=await evaluate('__native.source');
      assert.equal(await evaluate(`(async()=>{RinBlocksEditor.workspace.getTopBlocks(false).find(block=>block.id!=='rin_program').moveBy(40,20);return __flush();})()`),true);
      assert.equal(await evaluate('__native.source'),source);assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),1);
      assert.equal(await evaluate(`(async()=>{const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');const part=RinBlocksEditor.workspace.getTopBlocks(false).find(block=>block.id!=='rin_program');RinBlocksEditor.workspace.getBlockById('bound_'+fn.id).getInput('I_body').connection.connect(part.previousConnection);return __flush();})()`),true);
      const reconnected=await evaluate('__native.source');assert.equal(reconnected.split('circle(10,20,30)').length-1,1);assert.equal(reconnected.split('rect(40,50,60,70)').length-1,1);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),0);
    });
    await check('a final coordinate event reconciles detached source blocks even without a disconnect notification',async()=>{
      for(const wholeFunction of [false,true]){
        await loadSource('function draw(){background(240);circle(10,20,30);rect(40,50,60,70);}');
        const detached=await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>${wholeFunction?"n.kind==='function'":"n.kind==='expression_statement'&&n.raw.startsWith('circle(')"});const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);Blockly.Events.disable();try{block.unplug(false);}finally{Blockly.Events.enable();}block.moveBy(100,50);const ok=await __flush();return {ok,source:__native.source,parts:JSON.parse(__native.metadata).orphans};})()`);
        assert.equal(detached.ok,true);assert.equal(detached.source.includes('circle('),false);assert.equal(detached.source.includes('rect('),false);
        assert.equal(detached.parts.length,1);assert.match(detached.parts[0].source,/circle\(10,20,30\);[\s\S]*rect\(40,50,60,70\);/);
        assert.equal(detached.source.includes('background('),!wholeFunction);
        await evaluate('__load()');await settled();
        assert.equal(await evaluate(`RinBlocksEditor.workspace.getAllBlocks(false).filter(block=>block.getInput('TITLE')?.fieldRow.some(field=>field.getText()?.includes('circle'))).length`),1);
        assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),1);
      }
    });
    await check('saving another edit reconciles a detached source stack whose move notifications are absent',async()=>{
      await loadSource('function draw(){background(240);circle(10,20,30);rect(40,50,60,70);}');
      const result=await evaluate(`(async()=>{const circle=RinBlocksEditor.projection.nodes.find(n=>n.kind==='expression_statement'&&n.raw.startsWith('circle('));const block=RinBlocksEditor.workspace.getBlockById('bound_'+circle.id);Blockly.Events.disable();try{block.unplug(false);block.moveBy(100,50);}finally{Blockly.Events.enable();}const background=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value===240);RinBlocksEditor.workspace.getBlockById('bound_'+background.id).setFieldValue('128','F_value');const ok=await __flush();return {ok,source:__native.source,parts:JSON.parse(__native.metadata).orphans};})()`);
      assert.equal(result.ok,true);assert.match(result.source,/background\(128\)/);assert.equal(result.source.includes('circle('),false);
      assert.equal(result.source.includes('rect('),false);assert.equal(result.parts.length,1);
      assert.match(result.parts[0].source,/circle\(10,20,30\);[\s\S]*rect\(40,50,60,70\);/);
    });
    await check('a disconnected required value without its old socket notification rolls back instead of saving a duplicate',async()=>{
      const source='function draw(){circle(10,20,30);}';await loadSource(source);
      assert.equal(await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value===10);const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);Blockly.Events.disable();try{block.unplug(false);}finally{Blockly.Events.enable();}block.moveBy(100,50);return __flush();})()`),false);
      assert.equal(await evaluate('__native.source'),source);assert.equal(await evaluate('__native.metadata'),null);
      assert.equal(await evaluate(`RinBlocksEditor.workspace.getTopBlocks(false).length`),1);
    });
    await check('flush reconciles a detached source function even when no move events were queued',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      const result=await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);Blockly.Events.disable();try{block.unplug(false);block.moveBy(100,50);}finally{Blockly.Events.enable();}const ok=await __flush();return {ok,source:__native.source,parts:JSON.parse(__native.metadata).orphans};})()`);
      assert.equal(result.ok,true);assert.equal(result.source,'');assert.equal(result.parts.length,1);
      assert.equal(result.parts[0].source,'function draw(){circle(10,20,30);}');
    });
    await check('missing value move notifications still distinguish a real socket replacement from a detached value',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      await evaluate(`RinBlocksEditor.addTemplate('number','tray')`);await settled();
      const result=await evaluate(`(async()=>{const call=RinBlocksEditor.projection.nodes.find(n=>n.kind==='p5_call'&&n.calleeName==='circle');const value=RinBlocksEditor.workspace.getTopBlocks(false).find(block=>block.outputConnection);Blockly.Events.disable();try{RinBlocksEditor.workspace.getBlockById('bound_'+call.id).getInput('I_args_0').connection.connect(value.outputConnection);}finally{Blockly.Events.enable();}const ok=await __flush();return {ok,source:__native.source,parts:JSON.parse(__native.metadata).orphans};})()`);
      assert.equal(result.ok,true);assert.match(result.source,/circle\(80,20,30\)/);
      assert.equal(result.parts.length,1);assert.equal(result.parts[0].source,'10');
    });
    await check('holding and dragging a function on touch does not leave a copy at its original socket',async()=>{
      await loadSource('function draw(){circle(1,2,3);}');
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
      await waitFor('innerHeight===844','touch drag viewport');
      const point=await evaluate(`(() => {RinBlocksEditor.workspace.scroll(0,0);const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');const block=RinBlocksEditor.workspace.getBlockById('bound_'+fn.id);const r=block.getSvgRoot().querySelector('.blocklyPath').getBoundingClientRect();return {x:Math.round(r.left+20),y:Math.round(r.top+12)};})()`);
      await command('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[point]});
      await sleep(280);
      for(let step=1;step<=5;step++)await command('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:point.x+20*step,y:point.y+24*step}]});
      await command('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
      assert.equal(await evaluate('__flush()'),true);
      assert.equal(await evaluate('__native.source.includes("function draw")'),false);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),1);
      assert.equal(await evaluate(`RinBlocksEditor.workspace.getBlockById('rin_program').getInputTargetBlock('BODY')===null`),true);
      assert.equal(await evaluate(`JSON.parse(__native.metadata).orphans[0].source`),'function draw(){circle(1,2,3);}');
    });
    await check('dragging a whole function out and back preserves one authored function',async()=>{
      await loadSource('function draw(){circle(1,2,3);}');
      assert.equal(await evaluate(`(async()=>{const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');const block=RinBlocksEditor.workspace.getBlockById('bound_'+fn.id);block.unplug(false);block.moveBy(150,40);return __flush();})()`),true);
      assert.equal(await evaluate('__native.source.includes("function draw")'),false);assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),1);
      assert.equal(await evaluate(`(async()=>{const block=RinBlocksEditor.workspace.getTopBlocks(false).find(block=>block.id!=='rin_program');RinBlocksEditor.workspace.getBlockById('rin_program').getInput('BODY').connection.connect(block.previousConnection);return __flush();})()`),true);
      assert.equal(await evaluate('__native.source.split("function draw").length-1'),1);assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),0);
    });
    await check('a workspace with 500 rendered blocks still accepts source edits',async()=>{
      const source='function draw(){\n'+Array.from({length:100},(_,i)=>'circle('+i+',2,3);').join('\n')+'\n}';
      const started=Date.now();
      await evaluate(`__native.source=${JSON.stringify(source)};__native.metadata=null;__load()`);
      await waitFor('RinBlocksEditor.projection?.source===__native.source && RinBlocksEditor.workspace.getAllBlocks(false).length>=500 && RinBlocksEditor.workspace.getAllBlocks(false).filter(b=>b.id!=="rin_program").every(b=>b.isEditable())','500 rendered blocks');
      const blocks=await evaluate('RinBlocksEditor.workspace.getAllBlocks(false).length');
      assert.equal(await evaluate(`(async()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.fields.value.value===0);window.__editedBlock=RinBlocksEditor.workspace.getBlockById('bound_'+node.id);window.__unrelatedBlock=RinBlocksEditor.workspace.getBlockById('bound_'+RinBlocksEditor.projection.rootIds[0]);__editedBlock.setFieldValue('7','F_value');return __flush();})()`),true);
      assert.equal(await evaluate('__native.source.includes("circle(7,2,3)")'),true);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getBlockById(__editedBlock.id)===__editedBlock && RinBlocksEditor.workspace.getBlockById(__unrelatedBlock.id)===__unrelatedBlock'),true,'accepted numeric edit must retain Blockly objects');
      assert.equal(await evaluate(`(async()=>{__editedBlock.setFieldValue('1007','F_value');return __flush();})()`),true);
      assert.equal(await evaluate('__native.source.includes("circle(1007,2,3)")'),true,'a second edit must use the updated authoritative source ranges');
      console.log('BLOCK WORKSPACE: '+blocks+' blocks loaded and edited in '+(Date.now()-started)+'ms');
    });
    await check('haptic feedback vibrate is called safely on hold and block connection',async()=>{
      await loadSource('function setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  background(220);\n}');
      await evaluate(`(() => {
        window.__vibrations = [];
        navigator.vibrate = pattern => { window.__vibrations.push(pattern); return true; };
      })()`);
      // Direct call
      await evaluate(`RinBlocksEditor.safeVibrate([15])`);
      assert.deepEqual(await evaluate(`window.__vibrations`), [[15]]);

      // Resilience against error or undefined
      await evaluate(`(() => {
        const orig = navigator.vibrate;
        delete navigator.vibrate;
        RinBlocksEditor.safeVibrate([15]);
        navigator.vibrate = () => { throw new Error('Hardware error'); };
        RinBlocksEditor.safeVibrate([15]);
        navigator.vibrate = orig;
      })()`);

      // Touch hold (220ms) on a workspace block triggers vibration
      await evaluate(`window.__vibrations = []`);
      const blockPoint = await evaluate(`(() => {
        RinBlocksEditor.workspace.scroll(0,0);
        const node = RinBlocksEditor.projection.nodes.find(n => n.calleeName === 'background');
        const b = RinBlocksEditor.workspace.getBlockById('bound_' + node.id);
        const r = b.getSvgRoot().querySelector('.blocklyPath').getBoundingClientRect();
        return { x: Math.round(r.left + 20), y: Math.round(r.top + 12), id: b.getParent().id };
      })()`);
      await command('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ x: blockPoint.x, y: blockPoint.y }]
      });
      await sleep(260);
      const isGrabReady = await evaluate(`(() => {
        const b = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(blockPoint.id)});
        return b.getSvgRoot().classList.contains('rin-grab-ready');
      })()`);
      assert.equal(isGrabReady, true, 'block should have rin-grab-ready class on 220ms touch hold');
      assert.deepEqual(await evaluate(`window.__vibrations`), [[15]], 'vibrate([15]) should be triggered on touch hold');
      await command('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });

      // BLOCK_MOVE event connecting to a parent triggers vibration
      await evaluate(`window.__vibrations = []`);
      await evaluate(`(() => {
        const b = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(blockPoint.id)});
        const event = new Blockly.Events.BlockMove(b);
        event.oldParentId = undefined;
        event.newParentId = 'rin_program';
        event.newInputName = 'BODY';
        Blockly.Events.fire(event);
      })()`);
      await waitFor(`window.__vibrations.length > 0`, 'connection vibration');
      assert.deepEqual(await evaluate(`window.__vibrations`), [[15]], 'vibrate([15]) should be triggered on block connection');

      // BLOCK_MOVE event without parent (e.g. unattached drag) does NOT vibrate
      await evaluate(`window.__vibrations = []`);
      await evaluate(`(() => {
        const b = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(blockPoint.id)});
        const event = new Blockly.Events.BlockMove(b);
        event.oldParentId = undefined;
        event.newParentId = undefined;
        Blockly.Events.fire(event);
      })()`);
      await sleep(60);
      assert.deepEqual(await evaluate(`window.__vibrations`), [], 'move without parent should not vibrate');
    });
    await check('code preview opens from toolbar and block actions with accurate snippet and entire code toggle',async()=>{
      await loadSource('function setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  background(220);\n}');
      // Open from toolbar
      await evaluate(`document.getElementById('codePreview').click()`);
      assert.equal(await evaluate(`document.getElementById('codeDialog').open`), true);
      assert.equal(await evaluate(`document.getElementById('codeDialogContent').textContent.includes('createCanvas(400, 400)')`), true);

      // Dismiss via backdrop click (click outside dialog boundary, e.g. at 2, 2)
      await evaluate(`(() => {
        const dialog = document.getElementById('codeDialog');
        dialog.dispatchEvent(new MouseEvent('click', { clientX: 2, clientY: 2, bubbles: true }));
      })()`);
      assert.equal(await evaluate(`document.getElementById('codeDialog').open`), false, 'backdrop click should dismiss codeDialog');

      // Open from block actions
      const blockId = await evaluate(`(() => {
        const node = RinBlocksEditor.projection.nodes.find(n => n.calleeName === 'background');
        return 'bound_' + node.id;
      })()`);
      await evaluate(`(() => {
        const block = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(blockId)});
        RinBlocksEditor.showCodePreview(block);
      })()`);
      assert.equal(await evaluate(`document.getElementById('codeDialog').open`), true);
      assert.equal(await evaluate(`document.getElementById('codeScopeToggle').hidden`), false);
      assert.ok(await evaluate(`document.getElementById('codeDialogContent').textContent.trim().startsWith('background(220)')`));

      // Toggle to entire code
      await evaluate(`document.getElementById('codeScopeAll').click()`);
      assert.equal(await evaluate(`document.getElementById('codeDialogContent').textContent.includes('createCanvas')`), true);

      // Toggle back to block
      await evaluate(`document.getElementById('codeScopeBlock').click()`);
      assert.ok(await evaluate(`document.getElementById('codeDialogContent').textContent.trim().startsWith('background(220)')`));

      // Verify copy with fallback when writeText throws
      await evaluate(`(() => {
        navigator.clipboard.writeText = () => Promise.reject(new Error('Permission denied'));
        document.execCommand = () => true;
      })()`);
      await evaluate(`document.getElementById('codeDialogCopy').click()`);
      await sleep(50);
      assert.equal(await evaluate(`document.getElementById('codeDialogCopy').textContent.trim()`), 'コピーしました');

      await evaluate(`document.getElementById('codeDialogClose').click()`);
      assert.equal(await evaluate(`document.getElementById('codeDialog').open`), false);
    });
    await check('palette expands as a left side drawer in wide landscape mode',async()=>{
      await command('Emulation.setDeviceMetricsOverride',{width:800,height:450,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerWidth===800 && innerHeight===450`,'wide landscape pane');
      await evaluate(`document.getElementById('paletteToggle').click()`);
      await waitFor(`(() => {const p=document.getElementById('palette');return !p.hidden&&p.getAnimations().every(a=>a.playState==='finished')&&Math.abs(p.getBoundingClientRect().left)<0.01;})()`,'side drawer animation finished');
      const drawer = await evaluate(`(() => {
        const p = document.getElementById('palette');
        const handle = p.querySelector('.sheet-handle');
        const r = p.getBoundingClientRect();
        const handleStyle = getComputedStyle(handle);
        return {
          hidden: p.hidden,
          left: r.left,
          top: r.top,
          bottom: r.bottom,
          height: r.height,
          width: r.width,
          handleDisplay: handleStyle.display
        };
      })()`);
      assert.equal(drawer.hidden, false);
      assert.equal(drawer.left, 0);
      assert.equal(drawer.top, 0);
      assert.equal(drawer.height, 450);
      assert.ok(drawer.width <= 380 && drawer.width >= 280, 'drawer width: ' + drawer.width);
      assert.equal(drawer.handleDisplay, 'none');
      await evaluate(`document.getElementById('paletteToggle').click()`);
      // Restore default mobile dimensions
      await command('Emulation.setDeviceMetricsOverride',{width:375,height:667,deviceScaleFactor:1,mobile:true});
      await waitFor(`innerWidth===375 && innerHeight===667`,'restore portrait pane');
    });
    await check('visual color picker opens from fill block and updates color via preset and RGB/HSB modes',async()=>{
      await loadSource('function draw() {\n  fill("#6c5ce7");\n  circle(100, 100, 50);\n}');
      const fillBlockId = await evaluate(`(() => {
        const node = RinBlocksEditor.projection.nodes.find(n => n.calleeName === 'fill');
        return 'bound_' + node.id;
      })()`);
      await evaluate(`(() => {
        const block = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(fillBlockId)});
        RinBlocksEditor.showBlockActions(block);
      })()`);
      const actionButtons = await evaluate(`(() => {
        return Array.from(document.querySelectorAll('#blockActions button')).map(b => ({
          text: b.textContent.trim(),
          hasSwatch: !!b.querySelector('.swatch')
        }));
      })()`);
      assert.ok(actionButtons.some(b => b.text === '色' && b.hasSwatch), 'Action bar on fill block must include color button with swatch');

      await evaluate(`(() => {
        const btn = Array.from(document.querySelectorAll('#blockActions button')).find(b => b.textContent.trim() === '色');
        btn.click();
      })()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Color picker dialog should open');
      assert.equal(await evaluate(`document.querySelectorAll('.rin-color-preset-btn').length >= 16`), true, 'Preset buttons should be present');

      await evaluate(`(() => {
        const preset = Array.from(document.querySelectorAll('.rin-color-preset-btn')).find(b => b.dataset.color.toLowerCase() === '#ff7675');
        preset.click();
      })()`);
      assert.equal(await evaluate(`document.querySelector('.rin-color-hex-input').value.toUpperCase()`), '#FF7675');

      await evaluate(`(() => {
        const hsbBtn = Array.from(document.querySelectorAll('.rin-color-mode-btn')).find(b => b.textContent.trim() === 'HSB');
        hsbBtn.click();
      })()`);
      assert.equal(await evaluate(`document.querySelectorAll('.rin-color-slider-row').length`), 3, 'Three HSB slider rows');

      await evaluate(`(() => {
        const applyBtn = document.querySelector('#dialogChoices button.filled');
        applyBtn.click();
      })()`);
      await waitFor(`__native.source.includes('#ff7675')`, 'color applied to source');
      await settled();
      assert.ok((await evaluate(`__native.source`)).includes('fill("#ff7675")'), 'Source should be updated with new hex color');
    });
    await check('visual color picker supports background(240) and backdrop dismissal',async()=>{
      await loadSource('function draw() {\n  background(240);\n}');
      const stmtBlockId = await evaluate(`(() => {
        const node = RinBlocksEditor.projection.nodes.find(n => n.kind === 'expression_statement');
        return 'bound_' + node.id;
      })()`);
      await evaluate(`(() => {
        const block = RinBlocksEditor.workspace.getBlockById(${JSON.stringify(stmtBlockId)});
        RinBlocksEditor.showBlockActions(block);
      })()`);
      const buttons = await evaluate(`(() => {
        return Array.from(document.querySelectorAll('#blockActions button')).map(b => ({
          text: b.textContent.trim(),
          hasSwatch: !!b.querySelector('.swatch')
        }));
      })()`);
      assert.ok(buttons.some(b => b.text === '色' && b.hasSwatch), 'Action bar on background statement must include color button');

      await evaluate(`(() => {
        const btn = Array.from(document.querySelectorAll('#blockActions button')).find(b => b.textContent.trim() === '色');
        btn.click();
      })()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Dialog should open for background');

      await evaluate(`(() => {
        const preset = Array.from(document.querySelectorAll('.rin-color-preset-btn')).find(b => b.dataset.color.toLowerCase() === '#0984e3');
        preset.click();
        const applyBtn = document.querySelector('#dialogChoices button.filled');
        applyBtn.click();
      })()`);
      await waitFor(`__native.source.includes('#0984e3')`, 'background updated with hex color');
      await settled();
      assert.ok((await evaluate(`__native.source`)).includes('background("#0984e3")'), 'Source should contain background("#0984e3")');

      // Test backdrop dismissal
      await evaluate(`(() => {
        const stmtNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'expression_statement');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + stmtNode.id);
        RinBlocksEditor.showBlockActions(block);
        const btn = Array.from(document.querySelectorAll('#blockActions button')).find(b => b.textContent.trim() === '色');
        btn.click();
      })()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Dialog should be open again');
      await evaluate(`(() => {
        const dialog = document.getElementById('actionDialog');
        const event = new MouseEvent('click', { clientX: 2, clientY: 2, bubbles: true });
        dialog.dispatchEvent(event);
      })()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), false, 'Dialog should close on backdrop click');
    });
    await check('palette provides Phase 2 math blocks and mobile inputs',async()=>{
      await evaluate(`document.getElementById('paletteToggle').click();document.getElementById('search').value='dist';document.getElementById('search').dispatchEvent(new Event('input'))`);
      assert.ok(await evaluate(`document.querySelectorAll('#paletteItems .palette-item').length > 0`), 'dist found in palette');

      await evaluate(`document.getElementById('search').value='accelerationX';document.getElementById('search').dispatchEvent(new Event('input'))`);
      assert.ok(await evaluate(`document.querySelectorAll('#paletteItems .palette-item').length > 0`), 'accelerationX found in palette');

      await evaluate(`document.getElementById('search').value='';document.getElementById('search').dispatchEvent(new Event('input'));document.getElementById('paletteToggle').click();`);
    });
    await check('Phase 3 variable management creates variables and supports dynamic blocks and dropdown switching', async () => {
      await loadSource('function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(220);\n}');

      // Open palette under 'variables' category
      await evaluate(`(() => {
        RinBlocksEditor.workspace.setScale(1);
        document.getElementById('paletteToggle').click();
        const catBtn = Array.from(document.querySelectorAll('#categories button')).find(b => b.textContent.includes('変数') || b.textContent.includes('Variable'));
        if (catBtn) catBtn.click();
      })()`);

      // Check '＋ 変数を作る' button is present
      const hasMakeVarBtn = await evaluate(`!!document.querySelector('.palette-make-var-btn')`);
      assert.equal(hasMakeVarBtn, true, 'Palette must contain make-var button in variables category');

      // Click '＋ 変数を作る' to open modal dialog
      await evaluate(`document.querySelector('.palette-make-var-btn').click()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Variable dialog should open');
      assert.ok(await evaluate(`!!document.getElementById('varNameInput')`), 'Variable name input should exist');
      assert.ok(await evaluate(`!!document.getElementById('varCancelBtn')`), 'Variable cancel button should exist');
      assert.equal(await evaluate(`document.getElementById('varInitInput').value`), '0', 'Initial value defaults to 0');

      // Test Cancel button dismisses without modifying source
      await evaluate(`document.getElementById('varCancelBtn').click()`);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), false, 'Variable dialog should close on cancel');
      assert.equal(await evaluate(`__native.source.includes('スコア')`), false, 'No variable added on cancel');

      // Re-open and test in-dialog validation for invalid variable name
      await evaluate(`document.querySelector('.palette-make-var-btn').click()`);
      await evaluate(`(() => {
        const nameInput = document.getElementById('varNameInput');
        nameInput.value = '123invalid';
        document.getElementById('varSubmitBtn').click();
      })()`);
      await sleep(100);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Dialog must remain open on invalid name');
      const hasErrorVisible = await evaluate(`!document.getElementById('varErrorMsg').hidden`);
      assert.equal(hasErrorVisible, true, 'Error message must be shown in dialog on invalid name');

      // Fill in valid Japanese variable name 'スコア' and initial value '10'
      await evaluate(`(() => {
        const nameInput = document.getElementById('varNameInput');
        const initInput = document.getElementById('varInitInput');
        nameInput.value = 'スコア';
        initInput.value = '10';
        document.getElementById('varSubmitBtn').click();
      })()`);

      // Wait for source update
      await waitFor(`__native.source.includes('let スコア = 10;')`, 'source contains let スコア = 10;');
      await settled();
      assert.ok((await evaluate(`__native.source`)).includes('let スコア = 10;'), 'let スコア = 10; inserted into sketch');

      // Check that palette now shows dynamic variable templates for 'スコア'
      await evaluate(`document.getElementById('paletteToggle').click();`);
      const dynamicItems = await evaluate(`(() => {
        const catBtn = Array.from(document.querySelectorAll('#categories button')).find(b => b.textContent.includes('変数') || b.textContent.includes('Variable'));
        if (catBtn) catBtn.click();
        return Array.from(document.querySelectorAll('#paletteItems .palette-item .title')).map(el => el.textContent.trim());
      })()`);
      assert.ok(dynamicItems.includes('スコア'), 'Palette must list reporter for スコア');
      assert.ok(dynamicItems.some(t => t.includes('スコア を 10 にする')), 'Palette must list setter for スコア');
      assert.ok(dynamicItems.some(t => t.includes('スコア を 1 ずつ変える')), 'Palette must list increment for スコア');

      // Close palette
      await evaluate(`document.getElementById('paletteClose').click()`);

      // Test duplicate name validation in dialog: attempting to create 'スコア' again keeps dialog open with error
      await evaluate(`RinBlocksEditor.showCreateVariableDialog()`);
      await evaluate(`(() => {
        document.getElementById('varNameInput').value = 'スコア';
        document.getElementById('varSubmitBtn').click();
      })()`);
      await sleep(100);
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`), true, 'Dialog must remain open on duplicate name');
      assert.ok((await evaluate(`document.getElementById('varErrorMsg').textContent`)).includes('スコア'), 'Error should mention duplicate variable name');

      // Correct name to 'スピード' = 5 and submit
      await evaluate(`(() => {
        document.getElementById('varNameInput').value = 'スピード';
        document.getElementById('varInitInput').value = '5';
        document.getElementById('varSubmitBtn').click();
      })()`);
      await waitFor(`__native.source.includes('let スピード = 5;')`, 'source contains let スピード = 5;');
      await settled();

      // Add a statement: スコア を 10 にする into draw
      await evaluate(`(() => {
        const drawNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'function' && n.fields.name.value === 'draw');
        const drawBlock = RinBlocksEditor.workspace.getBlockById('bound_' + drawNode.id);
        return RinBlocksEditor.addTemplate('var_set_スコア', 'inside', drawBlock);
      })()`);
      await waitFor(`__native.source.includes('スコア = 10;')`, 'source contains assignment スコア = 10;');
      await settled();

      // Find the identifier block for 'スコア' and verify dropdown has both variables and switches to 'スピード'
      const dropdownOptions = await evaluate(`(() => {
        const assignNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'assignment');
        const leftNode = RinBlocksEditor.projection.nodes.find(n => n.parentId === assignNode.id && n.kind === 'identifier');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + leftNode.id);
        const field = block.getField('F_name');
        return field ? field.getOptions().map(opt => opt[1]) : null;
      })()`);
      assert.ok(Array.isArray(dropdownOptions), 'Variable identifier field should be a dropdown');
      assert.ok(dropdownOptions.includes('スコア'), 'Dropdown options should include スコア');
      assert.ok(dropdownOptions.includes('スピード'), 'Dropdown options should include スピード');

      // Switch dropdown to 'スピード'
      await evaluate(`(() => {
        const assignNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'assignment');
        const leftNode = RinBlocksEditor.projection.nodes.find(n => n.parentId === assignNode.id && n.kind === 'identifier');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + leftNode.id);
        block.setFieldValue('スピード', 'F_name');
      })()`);
      await waitFor(`__native.source.includes('スピード = 10;')`, 'source updated to スピード = 10;');
      await settled();
      assert.ok((await evaluate(`__native.source`)).includes('スピード = 10;'), 'Source changed from スコア = 10; to スピード = 10;');
    });
    await check('Phase 4 error visualization highlights error block with red outline, friendly Japanese explanation, and auto-clears', async () => {
      const errorSource = 'function setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  background(240);\n  circle(mouseX, mouseY, 80);\n}\n';
      await evaluate(`(() => {
        __native.source = ${JSON.stringify(errorSource)};
        __load({ source: __native.source, errors: [{ line: 6, message: 'ReferenceError: foo is not defined' }] });
      })()`);
      await waitFor('window.RinBlocksEditor.workspace?.getAllBlocks(false).some(b => b.rinRuntimeError)', 'error block attached');
      await settled();

      const blockState = await evaluate(`(() => {
        const circleNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'p5_call' && n.calleeName === 'circle');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + circleNode.id);
        const svg = block.getSvgRoot();
        return {
          hasErrorClass: svg.classList.contains('rin-error-block'),
          hasRuntimeError: !!block.rinRuntimeError,
          errorLine: block.rinRuntimeError?.line,
          statusText: document.getElementById('status').textContent,
          statusHasError: document.getElementById('status').classList.contains('error')
        };
      })()`);
      assert.equal(blockState.hasErrorClass, true, 'Circle block should have rin-error-block class');
      assert.equal(blockState.hasRuntimeError, true, 'Circle block should have rinRuntimeError attached');
      assert.equal(blockState.errorLine, 6, 'Error line should be 6');
      assert.ok(blockState.statusText && blockState.statusText.includes('行 6: 変数または関数「foo」が見つかりません'), 'Status bar should show friendly explanation');
      assert.equal(blockState.statusHasError, true, 'Status bar should have error class');

      await evaluate(`(() => {
        const circleNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'p5_call' && n.calleeName === 'circle');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + circleNode.id);
        RinBlocksEditor.showBlockActions(block);
      })()`);
      await waitFor('!document.getElementById("blockActions").hidden', 'blockActions visible');
      const actionState = await evaluate(`(() => {
        const btn = document.querySelector('#blockActions button.error-action');
        return {
          exists: !!btn,
          text: btn?.textContent,
          isDanger: btn?.classList.contains('danger')
        };
      })()`);
      assert.equal(actionState.exists, true, 'Error action button should exist in blockActions');
      assert.equal(actionState.text, '行 6', 'Error action button should show line 6');
      assert.equal(actionState.isDanger, true, 'Error action button should have danger class');

      await evaluate(`document.querySelector('#blockActions button.error-action').click()`);
      await waitFor('document.getElementById("actionDialog").open', 'actionDialog open');
      const dialogState = await evaluate(`(() => {
        const dialog = document.getElementById('actionDialog');
        return {
          title: document.getElementById('dialogTitle').textContent,
          desc: document.getElementById('dialogDescription').textContent,
          hasJump: Array.from(dialog.querySelectorAll('button')).some(b => b.textContent === 'コードで位置を確認')
        };
      })()`);
      assert.equal(dialogState.title, '行 6 のエラー', 'Dialog title should be 行 6 のエラー');
      assert.ok(dialogState.desc.includes('変数または関数「foo」が見つかりません'), 'Dialog desc should contain friendly text');
      assert.ok(dialogState.desc.includes('ReferenceError: foo is not defined'), 'Dialog desc should contain technical detail');
      assert.equal(dialogState.hasJump, true, 'Dialog should have jump to code button');

      await evaluate(`Array.from(document.querySelectorAll('#actionDialog button')).find(b => b.textContent === 'コードで位置を確認').click()`);
      await waitFor('!document.getElementById("actionDialog").open', 'actionDialog closed');
      const jumpMsg = await evaluate(`__native.messages.find(m => m.type === 'openCode')`);
      assert.ok(jumpMsg, 'openCode message should have been sent');

      // Tap non-error block: blockActions should NOT have error button, but status bar should still show error
      await evaluate(`(() => {
        const bgNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'p5_call' && n.calleeName === 'background');
        const bgBlock = RinBlocksEditor.workspace.getBlockById('bound_' + bgNode.id);
        RinBlocksEditor.showBlockActions(bgBlock);
      })()`);
      await settled();
      const nonErrorState = await evaluate(`(() => {
        const btn = document.querySelector('#blockActions button.error-action');
        return {
          hasErrorBtn: !!btn,
          statusText: document.getElementById('status').textContent,
          statusHasError: document.getElementById('status').classList.contains('error')
        };
      })()`);
      assert.equal(nonErrorState.hasErrorBtn, false, 'Non-error block should not show error-action button');
      assert.equal(nonErrorState.statusHasError, true, 'Status should still indicate error');

      // Clicking status bar while non-error block is selected should open dialog for actual error block
      await evaluate(`document.getElementById('status').click()`);
      await waitFor('document.getElementById("actionDialog").open', 'actionDialog opened from status bar');
      const statusTitle = await evaluate(`document.getElementById('dialogTitle').textContent`);
      assert.equal(statusTitle, '行 6 のエラー', 'Status click must target line 6 error, not selected block');
      await evaluate(`document.getElementById('dialogCancel').click()`);
      await waitFor('!document.getElementById("actionDialog").open', 'actionDialog closed');

      await evaluate(`__load({ errors: [] })`);
      await waitFor('!document.getElementById("status").classList.contains("error")', 'status error cleared');
      const clearedState = await evaluate(`(() => {
        const circleNode = RinBlocksEditor.projection.nodes.find(n => n.kind === 'p5_call' && n.calleeName === 'circle');
        const block = RinBlocksEditor.workspace.getBlockById('bound_' + circleNode.id);
        const svg = block.getSvgRoot();
        return {
          hasErrorClass: svg.classList.contains('rin-error-block'),
          hasRuntimeError: !!block.rinRuntimeError,
          statusHasError: document.getElementById('status').classList.contains('error'),
          hasErrorAction: !!document.querySelector('#blockActions button.error-action')
        };
      })()`);
      assert.equal(clearedState.hasErrorClass, false, 'rin-error-block should be removed');
      assert.equal(clearedState.hasRuntimeError, false, 'Runtime error should be cleared');
      assert.equal(clearedState.statusHasError, false, 'Status error class should be removed');
      assert.equal(clearedState.hasErrorAction, false, 'Error action button should be removed');
    });
    await check('palette pointer navigation retains the selected value through search and replacement',async()=>{
      await loadSource('let result = 2 * 3;');
      await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='3');Blockly.getFocusManager().focusNode(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));})()`);
      const point=await evaluate(`(() => {const r=document.getElementById('paletteToggle').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
      await command('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...point});
      await command('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...point});
      assert.equal(await evaluate('Blockly.getSelected()===null'),true,'toolbar focus must leave the Blockly focus tree');
      await evaluate(`document.querySelector('#categories button').click();document.getElementById('search').focus();document.getElementById('search').value='計算';document.getElementById('search').dispatchEvent(new Event('input'));document.querySelector('#paletteItems .palette-item').click()`);
      await waitFor('document.getElementById("actionDialog").open','replacement location');
      await clickChoice('選択した値を置き換える');
      await waitFor('__native.source.includes("(10 + 20)")','grouped calculation replacement');await settled();
      assert.equal(await evaluate('__native.source'),'let result = 2 * (10 + 20);');
      await evaluate(`Blockly.getFocusManager().focusNode(RinBlocksEditor.workspace.getBlockById('rin_program'));document.getElementById('paletteToggle').click();document.querySelector('#categories button').click();document.getElementById('search').value='計算';document.getElementById('search').dispatchEvent(new Event('input'));document.querySelector('#paletteItems .palette-item').click()`);
      await waitFor('document.getElementById("actionDialog").open','program selection');
      assert.equal(await evaluate(`Array.from(document.querySelectorAll('#dialogChoices button')).some(button=>button.textContent.includes('置き換える'))`),false,'program selection must clear the previous value target');
      await evaluate(`document.getElementById('dialogCancel').click();document.getElementById('paletteClose').click()`);
    });
    await check('name dropdowns use each lexical scope and accept names from the action chooser',async()=>{
      await loadSource('let x=1;let y=2;function draw(){let local=3;circle(x,local,10);}function other(){let second=4;circle(x,second,20);}');
      const choices=await evaluate(`RinBlocksEditor.projection.nodes.filter(n=>n.kind==='identifier'&&n.raw==='x'&&!n.declaration).map(n=>RinBlocksEditor.workspace.getBlockById('bound_'+n.id).getField('F_name').getOptions().map(x=>x[1]))`);
      assert.ok(choices[0].includes('local')&&!choices[0].includes('second'));assert.ok(choices[1].includes('second')&&!choices[1].includes('local'));
      await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='identifier'&&n.raw==='x'&&!n.declaration);RinBlocksEditor.showBlockActions(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));Array.from(document.querySelectorAll('#blockActions button')).find(b=>b.textContent==='名前').click();})()`);
      await waitFor('document.getElementById("actionDialog").open','visible names');await clickChoice('local');
      assert.equal(await evaluate('__flush()'),true);await settled();
      const changedSource=await evaluate('__native.source');assert.ok(changedSource.includes('circle(local,local,10)'),changedSource);
      assert.ok((await evaluate('__native.source')).includes('circle(x,second,20)'));
    });
    await check('color changes keep alpha expressions and comments, expose labelled sliders, and reject stale dialogs',async()=>{
      for(const args of ['255, 0, 0, /* keep alpha */ opacity()','200, opacity()']){
        await loadSource('function draw(){fill('+args+');}');
        await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='fill');RinBlocksEditor.chooseColor(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));})()`);
        const sliders=await evaluate(`Array.from(document.querySelectorAll('.rin-color-slider')).map(input=>input.getAttribute('aria-label'))`);assert.deepEqual(sliders,['R','G','B']);
        await evaluate(`Array.from(document.querySelectorAll('.rin-color-mode-btn')).find(b=>b.textContent==='HSB').click()`);
        assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('.rin-color-slider')).map(input=>input.getAttribute('aria-label'))`),['H','S','B']);
        await evaluate(`document.querySelector('.rin-color-preset-btn[data-color="#3498db"]').click();document.querySelector('#dialogChoices button.filled').click()`);
        await waitFor('__native.source.includes("setAlpha")','alpha-preserving color change');await settled();
        const call=await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='fill');return {raw:n.raw,args:n.inputs.args.map(id=>RinBlocksEditor.projection.nodes.find(n=>n.id===id).raw)};})()`);
        assert.equal(call.args.length,1);assert.ok(call.args[0].includes('color("#3498db"), opacity()'));if(args.includes('/* keep alpha */'))assert.ok(call.raw.includes('/* keep alpha */'));
        await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='fill');RinBlocksEditor.chooseColor(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));})()`);
        assert.equal(await evaluate(`document.querySelector('.rin-color-hex-input').value`),'#3498DB');
        await evaluate(`document.querySelector('.rin-color-preset-btn[data-color="#e74c3c"]').click();document.querySelector('#dialogChoices button.filled').click()`);
        await waitFor('__native.source.includes("#e74c3c")','second colour change');await settled();assert.ok((await evaluate('__native.source')).includes('opacity()'));
      }
      await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='fill');RinBlocksEditor.chooseColor(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));})()`);
      const newer='function draw(){background(0);}';await loadSource(newer);
      await evaluate(`document.querySelector('#dialogChoices button.filled').click()`);await sleep(150);assert.equal(await evaluate('__native.source'),newer);
      await loadSource('function fill(first,second){} function draw(){fill(1,2);clear();}');
      assert.equal(await evaluate(`['fill','clear'].every(name=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName===name);RinBlocksEditor.showBlockActions(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));return !Array.from(document.querySelectorAll('#blockActions button')).some(button=>button.textContent==='色');})`),true,'custom functions and clear must not receive the p5 colour-argument rewrite');
    });
    await check('short split panes retain visible action labels and field ACKs preserve detached blocks and collapsed state',async()=>{
      await loadSource('function draw(){circle(1,2,3);}');
      await evaluate(`RinBlocksEditor.addTemplate('rect','tray')`);await settled();
      await evaluate(`(() => {const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');RinBlocksEditor.workspace.getBlockById('bound_'+fn.id).setCollapsed(true);})()`);
      assert.equal(await evaluate('__flush()'),true);await settled();
      const original=await evaluate(`(() => {window.__savedPart=RinBlocksEditor.workspace.getTopBlocks(false).find(b=>b.id!=='rin_program');return JSON.parse(__native.metadata);})()`);
      assert.equal(await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='3');RinBlocksEditor.workspace.getBlockById('bound_'+n.id).setFieldValue('30','F_value');return __flush();})()`),true);await settled();
      assert.equal(await evaluate('RinBlocksEditor.workspace.getBlockById(__savedPart.id)===__savedPart'),true);
      const after=await evaluate('JSON.parse(__native.metadata)');assert.deepEqual(after.orphans,original.orphans);assert.deepEqual(after.collapsed,original.collapsed);
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:320,deviceScaleFactor:1,mobile:true});
      await evaluate(`(() => {const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');RinBlocksEditor.showBlockActions(RinBlocksEditor.workspace.getBlockById('bound_'+n.id));})()`);
      assert.equal(await evaluate(`Array.from(document.querySelectorAll('#blockActions button span')).every(span=>getComputedStyle(span).display!=='none')`),true);
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
    });
    await check('rejected connections keep an actionable message until dismissed',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      assert.equal(await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='10');const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);block.unplug(false);block.moveBy(100,50);return __flush();})()`),false);
      assert.equal(await evaluate(`document.getElementById('status').classList.contains('error')&&!document.getElementById('statusDismiss').hidden`),true);
      assert.match(await evaluate('RinBlocksEditor.status'),/安全にコードへ反映できません/);
      await evaluate('RinBlocksEditor.highlightRuntimeErrors()');assert.match(await evaluate('RinBlocksEditor.status'),/安全にコードへ反映できません/);
      await evaluate(`document.getElementById('statusDismiss').click()`);
      assert.equal(await evaluate(`document.getElementById('status').classList.contains('error')`),false);
    });
    await check('dragging a standalone call caption moves its whole statement instead of detaching a required value',async()=>{
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
      await loadSource('function draw(){circle(10,20,30);}');
      const point=await evaluate(`(()=>{RinBlocksEditor.workspace.scroll(0,0);const n=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='circle');const block=RinBlocksEditor.workspace.getBlockById('bound_'+n.id),rect=block.getSvgRoot().querySelector('.blocklyPath').getBoundingClientRect();return {x:rect.left+20,y:rect.top+12,hasGrip:!!block.getParent().getInput('I_expression').fieldRow.find(field=>field instanceof Blockly.FieldImage)};})()`);
      assert.equal(point.hasGrip,true);
      await command('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:point.x,y:point.y}]});await sleep(280);
      for(let step=1;step<=5;step++)await command('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:point.x+20*step,y:point.y+24*step}]});
      await command('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert.equal(await evaluate('__flush()'),true);
      assert.equal(await evaluate('__native.source.includes("circle(")'),false);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans.length'),1);
      assert.equal(await evaluate('JSON.parse(__native.metadata).orphans[0].source'),'circle(10,20,30);');
    });
    await check('detached parts have a visible state, avoid the program and can be located from their list',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');await evaluate(`RinBlocksEditor.addTemplate('rect','tray')`);await settled();
      const result=await evaluate(`(()=>{const parts=RinBlocksEditor.workspace.getTopBlocks(false).filter(b=>b.id!=='rin_program'),part=parts[0],a=part.getBoundingRectangle(),b=RinBlocksEditor.workspace.getBlockById('rin_program').getBoundingRectangle();return {overlap:a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top,label:part.getInput('DETACHED_STATUS').fieldRow[0].getText(),badge:document.getElementById('partsBadge').textContent,source:__native.source,edits:__native.edits.length};})()`);
      assert.equal(result.overlap,false);assert.match(result.label,/未接続/);assert.match(result.badge,/1/);
      await evaluate(`document.getElementById('partsBadge').click()`);assert.equal(await evaluate(`document.getElementById('navigatorDialog').open`),true);
      assert.equal(await evaluate(`document.querySelectorAll('#navigatorResults button').length`),1);
      await evaluate(`document.querySelector('#navigatorResults button').click()`);
      assert.equal(await evaluate(`Blockly.getSelected()?.getRootBlock().id!=='rin_program'`),true);
      assert.equal(await evaluate('__native.source'),result.source);assert.equal(await evaluate('__native.edits.length'),result.edits);
    });
    await check('work search and the function list locate existing blocks without authoring changes',async()=>{
      await loadSource('function setup(){createCanvas(400,400);} function draw(){circle(10,20,30);}');
      const before=await evaluate('__native.edits.length');
      await evaluate(`document.getElementById('workspaceSearch').click();document.getElementById('navigatorSearch').value='circle';document.getElementById('navigatorSearch').dispatchEvent(new Event('input'))`);
      assert.equal(await evaluate(`document.querySelectorAll('#navigatorResults button').length>0`),true);
      await evaluate(`Array.from(document.querySelectorAll('#navigatorResults button')).find(button=>button.textContent.includes('円')).click()`);
      assert.equal(await evaluate(`RinBlocksEditor.projection.nodes.find(n=>'bound_'+n.id===Blockly.getSelected()?.id)?.calleeName`),'circle');
      await evaluate(`document.getElementById('outline').click()`);assert.equal(await evaluate(`document.querySelectorAll('#navigatorResults button').length`),2);
      await evaluate(`Array.from(document.querySelectorAll('#navigatorResults button')).find(button=>button.textContent.includes('draw')).click()`);
      assert.equal(await evaluate(`RinBlocksEditor.projection.nodes.find(node=>'bound_'+node.id===Blockly.getSelected()?.id)?.fields.name?.value`),'draw');assert.equal(await evaluate('__native.edits.length'),before);
    });
    await check('compact toolbars keep every primary action visible and offer secondary actions in the menu',async()=>{
      for(const language of ['ja','en','zh'])for(const width of [320,339,390]){
        await command('Emulation.setDeviceMetricsOverride',{width,height:250,deviceScaleFactor:1,mobile:true});
        await evaluate(`__load({language:${JSON.stringify(language)}})`);await settled();
        const geometry=await evaluate(`(()=>{const ids=['paletteToggle','undo','redo','workspaceSearch','fullscreen','more'];return {width:innerWidth,scroll:document.getElementById('tools').scrollWidth,client:document.getElementById('tools').clientWidth,buttons:ids.map(id=>{const r=document.getElementById(id).getBoundingClientRect();return {id,left:r.left,right:r.right};})};})()`);
        assert.ok(geometry.scroll<=geometry.client+1,JSON.stringify({language,width,geometry}));
        for(const button of geometry.buttons)assert.ok(button.left>=0&&button.right<=width,JSON.stringify({language,width,button}));
      }
      await evaluate(`document.getElementById('more').click()`);assert.equal(await evaluate(`getComputedStyle(document.getElementById('menuFit')).display`),'flex');
      assert.equal(await evaluate(`getComputedStyle(document.getElementById('codePreview')).display`),'none');
      await evaluate(`document.getElementById('menuFit').click()`);
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await evaluate(`__load({language:'ja'})`);await settled();
    });
    await check('display settings enlarge text and hit areas without changing source, metadata or history',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      const before=await evaluate(`(()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal');return {height:RinBlocksEditor.workspace.getBlockById('bound_'+n.id).getSvgRoot().getBoundingClientRect().height,source:__native.source,metadata:__native.metadata,edits:__native.edits.length};})()`);
      await evaluate(`document.getElementById('displaySettings').click()`);await waitFor(`document.getElementById('actionDialog').open`,'display settings');
      await evaluate(`const slider=document.querySelector('#dialogChoices input[type=range]');slider.value='18';slider.dispatchEvent(new Event('input'));document.querySelector('#dialogChoices select').value='comfortable';document.querySelector('#dialogChoices button.filled').click()`);
      await waitFor(`RinBlocksEditor.workspace.getRenderer().getConstants().FIELD_TEXT_FONTSIZE===18`,'larger typography');
      const after=await evaluate(`(()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal');return {height:RinBlocksEditor.workspace.getBlockById('bound_'+n.id).getSvgRoot().getBoundingClientRect().height,source:__native.source,metadata:__native.metadata,edits:__native.edits.length,preference:__native.messages.filter(message=>message.type==='preferences').at(-1)?.preferences};})()`);
      assert.ok(after.height>before.height);assert.equal(after.source,before.source);assert.equal(after.metadata,before.metadata);assert.equal(after.edits,before.edits);
      await waitFor(`__native.messages.some(message=>message.type==='preferences')`,'preference bridge');
      assert.deepEqual(await evaluate(`__native.messages.filter(message=>message.type==='preferences').at(-1).preferences`),{fontSize:18,density:'comfortable'});
      await evaluate(`__load({preferences:{fontSize:12.5,density:'standard'}})`);await settled();
    });
    await check('a restored native viewport and selection round-trip as view messages without source edits',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      const selected=await evaluate(`'bound_'+RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='circle').id`),edits=await evaluate('__native.edits.length');
      const saved=await evaluate(`(()=>{const workspace=RinBlocksEditor.workspace;workspace.setScale(1.5);workspace.scroll(-240,-300);return {x:workspace.scrollX,y:workspace.scrollY,scale:workspace.scale,selected:${JSON.stringify(selected)}};})()`);
      await evaluate(`__load({workId:'view-work',viewport:${JSON.stringify(saved)}})`);await settled();
      assert.equal(await evaluate('RinBlocksEditor.workspace.scale'),1.5);assert.equal(await evaluate('Blockly.getSelected()?.id'),selected);
      const restored=await evaluate(`({x:RinBlocksEditor.workspace.scrollX,y:RinBlocksEditor.workspace.scrollY})`);
      assert.ok(Math.abs(restored.x-saved.x)<=1,JSON.stringify({saved,restored}));assert.ok(Math.abs(restored.y-saved.y)<=1,JSON.stringify({saved,restored}));
      await evaluate('__flush()');assert.equal(await evaluate('__native.edits.length'),edits);
      const view=await evaluate(`__native.messages.filter(message=>message.type==='view').at(-1)`);assert.equal(view.workId,'view-work');assert.equal(view.viewport.scale,1.5);assert.equal(view.viewport.selected,selected);
      await evaluate(`__load({workId:'work-one',viewport:null})`);await settled();
    });
    await check('structural inserts and removals preserve unrelated blocks and release disposed definitions',async()=>{
      await loadSource('function setup(){createCanvas(400,400);} function draw(){circle(10,20,30);}');
      await evaluate(`(()=>{const fn=name=>RinBlocksEditor.workspace.getBlockById('bound_'+RinBlocksEditor.projection.nodes.find(n=>n.kind==='function'&&n.fields.name.value===name).id);window.__keptSetup=fn('setup');window.__keptDraw=fn('draw');const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='10');window.__keptLiteral=RinBlocksEditor.workspace.getBlockById('bound_'+n.id);})()`);
      const count=await evaluate(`Object.keys(Blockly.Blocks).filter(key=>/^rin_[0-9a-z]+$/.test(key)).length`);
      for(let i=0;i<3;i++){
        await evaluate(`RinBlocksEditor.addTemplate('rect','inside',__keptDraw)`);await settled();
        assert.equal(await evaluate(`RinBlocksEditor.workspace.getBlockById(__keptSetup.id)===__keptSetup&&RinBlocksEditor.workspace.getBlockById(__keptDraw.id)===__keptDraw&&RinBlocksEditor.workspace.getBlockById(__keptLiteral.id)===__keptLiteral`),true);
        assert.equal(await evaluate(`(async()=>{const n=RinBlocksEditor.projection.nodes.find(n=>n.kind==='expression_statement'&&n.raw.startsWith('rect('));RinBlocksEditor.workspace.getBlockById('bound_'+n.id).checkAndDelete();return __flush();})()`),true);
        assert.equal(await evaluate(`RinBlocksEditor.workspace.getBlockById(__keptLiteral.id)===__keptLiteral`),true);
      }
      assert.equal(await evaluate(`Object.keys(Blockly.Blocks).filter(key=>/^rin_[0-9a-z]+$/.test(key)).length`),count);
      assert.match(await evaluate('__native.source'),/circle\(10,20,30\)/);assert.equal(await evaluate('__native.source.includes("rect(")'),false);
    });
    await check('a late incremental render cannot replace a newer native document',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      await evaluate(`(()=>{const original=Blockly.renderManagement.finishQueuedRenders;window.__originalRenderFinish=original;let hold=true;Blockly.renderManagement.finishQueuedRenders=function(){const finished=original.apply(this,arguments);if(!hold)return finished;hold=false;return finished.then(()=>new Promise(resolve=>{window.__releaseOldRender=resolve;}));};const fn=RinBlocksEditor.projection.nodes.find(n=>n.kind==='function');void RinBlocksEditor.addTemplate('rect','inside',RinBlocksEditor.workspace.getBlockById('bound_'+fn.id)).catch(error=>{window.__lateRenderError=error.message;});})()`);
      await waitFor('typeof __releaseOldRender==="function"','held old render');
      const newer='function draw(){background(64);}';
      await evaluate(`__native.source=${JSON.stringify(newer)};__native.metadata=null;__load({workId:'newer-work'})`);await settled();
      await evaluate('__releaseOldRender()');await sleep(120);
      assert.equal(await evaluate('RinBlocksEditor.document.workId'),'newer-work');assert.equal(await evaluate('RinBlocksEditor.projection.source'),newer);
      assert.equal(await evaluate('RinBlocksEditor.projection.nodes.some(node=>node.calleeName==="rect")'),false);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getAllBlocks(false).some(block=>String(block.getFieldValue("F_value"))==="64")'),true);
      await evaluate('Blockly.renderManagement.finishQueuedRenders=__originalRenderFinish;delete window.__releaseOldRender');
    });
    await check('an open work locator refreshes after a native source replacement',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      await evaluate(`document.getElementById('workspaceSearch').click();document.getElementById('navigatorSearch').value='circle';document.getElementById('navigatorSearch').dispatchEvent(new Event('input'))`);
      assert.equal(await evaluate(`document.querySelectorAll('#navigatorResults button').length>0`),true);
      const changed='function draw(){background(64);}';await loadSource(changed);
      assert.equal(await evaluate(`document.getElementById('navigatorDialog').open`),true);
      assert.equal(await evaluate(`document.querySelectorAll('#navigatorResults button').length`),0);
      await evaluate(`document.getElementById('navigatorSearch').value='background';document.getElementById('navigatorSearch').dispatchEvent(new Event('input'));Array.from(document.querySelectorAll('#navigatorResults button')).find(button=>button.textContent.includes('背景')).click()`);
      assert.equal(await evaluate(`RinBlocksEditor.projection.nodes.find(node=>'bound_'+node.id===Blockly.getSelected()?.id)?.calleeName`),'background');
      assert.equal(await evaluate('__native.source'),changed);
    });
    const nativeCommand=async(action,values={})=>evaluate(`__port.postMessage(JSON.stringify(Object.assign({type:'uiCommand',sessionId:'browser-session',workId:RinBlocksEditor.document.workId,expectedRevision:__native.revision,action:${JSON.stringify(action)}},${JSON.stringify(values)})))`);
    await check('native chrome provides one canvas and revision-scoped search without authoring edits',async()=>{
      await loadSource('function draw(){circle(10,20,30);}');
      await evaluate(`__load({nativeChrome:true})`);await settled();
      await waitFor(`__native.messages.some(m=>m.type==='chrome'&&!m.busy&&m.editable)`,'native chrome ready');
      const before=await evaluate(`({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})`);
      const geometry=await evaluate(`({tools:getComputedStyle(document.getElementById('tools')).display,status:getComputedStyle(document.getElementById('statusArea')).display,height:document.getElementById('workspace').getBoundingClientRect().height,inner:innerHeight})`);
      assert.equal(geometry.tools,'none');assert.equal(geometry.status,'none');assert.ok(Math.abs(geometry.height-geometry.inner)<2);
      await nativeCommand('search',{mode:'search',query:'circle',requestId:'native-find'});
      await waitFor(`__native.messages.some(m=>m.type==='searchResults'&&m.requestId==='native-find')`,'native search response');
      const reply=await evaluate(`__native.messages.find(m=>m.type==='searchResults'&&m.requestId==='native-find')`);
      assert.equal(reply.expectedRevision,await evaluate('__native.revision'));assert.ok(reply.total>0);
      assert.equal(await evaluate(`document.getElementById('navigatorDialog').open`),false);
      const entry=reply.entries.find(entry=>entry.title.includes('円'));assert.ok(entry);
      await nativeCommand('focus',{id:entry.id});await waitFor(`Blockly.getSelected()?.id===${JSON.stringify(entry.id)}`,'native locate');
      assert.deepEqual(await evaluate(`({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})`),before);
    });
    await check('native add keeps selection and back closes overlays before leaving the editor',async()=>{
      await nativeCommand('add');await waitFor(`!document.getElementById('palette').hidden`,'native add');
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.overlay===true`,'native overlay state');
      assert.equal(await evaluate(`document.querySelectorAll('#paletteItems .palette-item').length>0`),true);
      await nativeCommand('dismiss');await waitFor(`document.getElementById('palette').hidden`,'back closes palette');
      await nativeCommand('codePreview');await waitFor(`document.getElementById('codeDialog').open`,'native code preview');
      await nativeCommand('dismiss');await waitFor(`!document.getElementById('codeDialog').open`,'back closes preview');
      await evaluate(`(()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='30');RinBlocksEditor.workspace.getBlockById('bound_'+node.id).getField('F_value').showEditor();})()`);
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.overlay===true`,'native field overlay state');
      await nativeCommand('dismiss');
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.overlay===false`,'back closes field editor');
      await evaluate(`document.getElementById('workspace').dispatchEvent(new KeyboardEvent('keydown',{key:'f',ctrlKey:true,bubbles:true}))`);
      await waitFor(`__native.messages.some(m=>m.type==='openSearch')`,'native keyboard search');
      assert.equal(await evaluate(`document.getElementById('navigatorDialog').open`),false);
    });
    await check('native commands reject stale actions and keep display settings out of source and history',async()=>{
      const before=await evaluate(`({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})`);
      await nativeCommand('add',{expectedRevision:'obsolete'});await sleep(80);
      assert.equal(await evaluate(`document.getElementById('palette').hidden`),true);
      await nativeCommand('displayPreferences',{preferences:{fontSize:18,density:'comfortable'}});
      await waitFor(`RinBlocksEditor.workspace.getRenderer().getConstants().FIELD_TEXT_FONTSIZE===18`,'native display settings');
      assert.deepEqual(await evaluate(`({source:__native.source,metadata:__native.metadata,edits:__native.edits.length})`),before);
      await nativeCommand('displayPreferences',{preferences:{fontSize:'22',density:'comfortable'}});await sleep(80);
      assert.equal(await evaluate('RinBlocksEditor.workspace.getRenderer().getConstants().FIELD_TEXT_FONTSIZE'),18);
      await evaluate(`(()=>{const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='30');RinBlocksEditor.workspace.getBlockById('bound_'+node.id).setFieldValue('35','F_value');})()`);await waitFor(`__native.source.includes('circle(10,20,35)')`,'native mode field ACK');await settled();
      assert.match(await evaluate('__native.source'),/35/);assert.equal(await evaluate(`document.body.classList.contains('native-chrome')`),true);
      await evaluate(`__load({nativeChrome:true,readOnly:true})`);await waitFor('RinBlocksEditor.document.readOnly&&!RinBlocksEditor.document.loading','native read only');
      await nativeCommand('add');await sleep(80);assert.equal(await evaluate(`document.getElementById('palette').hidden`),true);
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.notice===true`,'read only notice');
      await evaluate(`__load({nativeChrome:false,readOnly:false,preferences:{fontSize:12.5,density:'standard'}})`);await settled();
      assert.equal(await evaluate(`getComputedStyle(document.getElementById('tools')).display`),'flex');
    });
    await check('a suspended animation frame rejects flush without losing queued edits',async()=>{
      await evaluate(`__native.source='circle(10,20,30);';__native.metadata=null;__load()`);
      await settled();
      await evaluate(`(()=>{
        window.__savedRaf=requestAnimationFrame;window.__pausedFrames=[];
        window.requestAnimationFrame=callback=>{__pausedFrames.push(callback);return 0;};
        const node=RinBlocksEditor.projection.nodes.find(n=>n.kind==='literal'&&n.raw==='30');
        RinBlocksEditor.workspace.getBlockById('bound_'+node.id).setFieldValue('35','F_value');
      })()`);
      try {
        assert.equal(await evaluate('__flush()'),false);
        assert.equal(await evaluate('__native.source'),'circle(10,20,30);');
      } finally {
        await evaluate(`window.requestAnimationFrame=__savedRaf;for(const callback of __pausedFrames)__savedRaf(callback);void 0;`);
      }
      assert.equal(await evaluate('__flush()'),true);
      await settled();
      assert.match(await evaluate('__native.source'),/circle\(10,20,35\)/);
    });
    await check('half-sheet palette inserts statements in 1 tap and exposes inline color swatches on p5 color calls',async()=>{
      await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
      await loadSource('function draw(){\n  background(240);\n}');
      await evaluate(`__load({nativeChrome:true})`);await settled();
      const hasInlineSwatch=await evaluate(`(()=>{
        const bgNode=RinBlocksEditor.projection.nodes.find(n=>n.calleeName==='background');
        const block=RinBlocksEditor.workspace.getBlockById('bound_'+bgNode.id);
        return !!block?.getField('SWATCH_COLOR');
      })()`);
      assert.equal(hasInlineSwatch,true,'background(240) block must have an inline clickable SWATCH_COLOR field');
      await nativeCommand('add');
      await waitFor(`!document.getElementById('palette').hidden`,'half-sheet palette open');
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.palette===true`,'chrome palette true');
      const sheetInfo=await evaluate(`(()=>{
        const r=document.getElementById('palette').getBoundingClientRect();
        const targetText=document.getElementById('paletteTargetLabel')?.textContent||'';
        return {height:r.height,inner:innerHeight,targetText};
      })()`);
      assert.ok(sheetInfo.height<=sheetInfo.inner*0.6,'half-sheet keeps upper canvas visible: '+JSON.stringify(sheetInfo));
      assert.ok(sheetInfo.targetText.startsWith('挿入先: '),'target bar shows active insertion target: '+sheetInfo.targetText);
      await evaluate(`(()=>{
        document.getElementById('search').value='circle';
        document.getElementById('search').dispatchEvent(new Event('input'));
        document.querySelector('#paletteItems .palette-item').click();
      })()`);
      await waitFor(`__native.source.includes('circle(')`,'1-tap statement insertion without dialog');
      await settled();
      assert.equal(await evaluate(`document.getElementById('actionDialog').open`),false,'1-tap insertion must not open actionDialog');
      assert.equal(await evaluate(`document.getElementById('palette').hidden`),true,'palette closes after 1-tap insertion');
      await waitFor(`__native.messages.filter(m=>m.type==='chrome').at(-1)?.palette===false`,'chrome palette false after insertion');
      await evaluate(`document.getElementById('search').value='';document.getElementById('search').dispatchEvent(new Event('input'));__load({nativeChrome:false})`);
      await settled();
    });
    passed=true;
    console.log('BLOCKS BROWSER: '+results.length+' passed');
  } catch(error) {
    process.exitCode=1;
    console.error(error.stack || error);
  } finally {
    socket?.close();
    if(child.pid&&child.exitCode===null&&child.signalCode===null)await new Promise(resolve=>{child.once('exit',resolve);child.kill('SIGKILL');});
    await new Promise(resolve=>server.close(resolve));
    if(passed)fs.rmSync(profile,{recursive:true,force:true,maxRetries:10,retryDelay:100});
    else {fs.writeFileSync(path.join(profile,'results.json'),JSON.stringify(results,null,2));fs.writeFileSync(path.join(profile,'browser.stderr.log'),stderr);console.log('BLOCKS DIAGNOSTICS: '+profile);}
  }
}
run().catch(error=>{console.error(error);process.exitCode=1;server.close();});
