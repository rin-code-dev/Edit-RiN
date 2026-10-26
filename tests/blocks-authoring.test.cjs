'use strict';
const assert=require('node:assert/strict');
const {test}=require('node:test');
const vm=require('node:vm');
const core=require('../www/blocks/core.js');
const authoring=require('../www/blocks/authoring.js');
const catalogue=require('../www/blocks/catalogue.js');
const assets=[{name:'image.png',mime:'image/png'},{name:'audio.wav',mime:'audio/wav'},{name:'wrong.txt',mime:'text/plain'}];
const env=(version='2.3.4',extra={})=>({p5Version:version,soundEnabled:true,assets,...extra});
function parse(source='',version='2.3.4',extra={}){const p=core.parse(source,{p5Version:version,...extra});assert.equal(p.ok,true,JSON.stringify(p.diagnostics));return p;}
function plan(projection,request,environment){const r=authoring.plan(projection,request,environment||env());assert.equal(r.ok,true,r.errorCode+': '+r.error);assert.equal(core.applyPatches(projection.source,r.patches),r.source);assert.equal(r.projection.source,r.source);return r;}
function rejected(projection,request,code,environment){const r=authoring.plan(projection,request,environment||env());assert.equal(r.ok,false);assert.equal(r.errorCode,code,r.error);assert.deepEqual(r.patches,[]);assert.equal(r.source,projection.source);return r;}
function load(projection,kind='image',environment=env(),assetName=kind==='image'?'image.png':'audio.wav'){return plan(projection,{type:'loadAsset',kind,assetName},environment);}

for(const version of ['1.11.5','2.3.4']){
 test(version+' creates global image/sound bindings and correct versioned lifecycle',()=>{
  let p=parse('',version);const environment=env(version);
  for(const kind of ['image','sound']){
   const r=load(p,kind,environment);p=r.projection;
   const info=authoring.inspect(p,environment),resource=info.resources.find(x=>x.name===r.resourceName);
   assert.equal(resource.kind,kind);
   assert.equal(resource.assetName,kind==='image'?'image.png':'audio.wav');
   assert.match(p.source,new RegExp('let '+r.resourceName+';'));
   const owner=info.events.find(e=>e.name===(version[0]==='1'?'preload':'setup'));
   assert.ok(owner);assert.equal(owner.async,version[0]!=='1');
   assert.equal(p.nodes.filter(n=>n.kind==='function'&&!n.parentId&&n.fields.name.value===owner.name).length,1);
  }
  assert.ok(!/const (rinImage|rinSound) =/.test(p.source));
  if(version[0]==='1')assert.doesNotMatch(p.source,/await|async function/);else assert.match(p.source,/await loadImage/);
 });
 test(version+' resource actions keep handler return semantics and oscillator identity',async()=>{
  const environment=env(version);let p=parse('function mousePressed(){ return false; }',version);
  const tone=plan(p,{type:'createOscillator'},environment);p=tone.projection;
  assert.equal(authoring.inspect(p,environment).events.some(e=>e.name==='mousePressed'),true);
  for(const action of ['toneStart','toneFreq','toneVolume','toneStop'])p=plan(p,{type:'useResource',action,name:tone.resourceName,targetEvent:action==='toneStop'?'mouseReleased':'mousePressed'},environment).projection;
  const calls={instances:0,starts:0,amplitudes:[]};
  const context={createCanvas(){},userStartAudio:()=>Promise.resolve(),Promise,p5:{Oscillator:class{constructor(){calls.instances++;}amp(v){calls.amplitudes.push(v);}start(){calls.starts++;}freq(){}}}};
  vm.createContext(context);vm.runInContext(p.source+'\nglobalThis.actions={setup,mousePressed,mouseReleased};',context);
  await context.actions.setup();assert.equal(calls.instances,1);assert.equal(calls.amplitudes[0],0);
  for(let i=0;i<3;i++){assert.equal(context.actions.mousePressed(),false);await new Promise(resolve=>setImmediate(resolve));context.actions.mouseReleased();}
  assert.equal(calls.instances,1);assert.equal(calls.starts,1);assert.equal(calls.amplitudes.at(-1),0);
 });
 test(version+' sound methods use matching API and user gesture without async promotion',()=>{
  const environment=env(version);let r=load(parse('',version),'sound',environment),p=r.projection;
  for(const action of ['soundPlay','soundLoop','soundStop','soundVolume','soundRate','soundPan'])p=plan(p,{type:'useResource',action,name:r.resourceName,targetEvent:'mousePressed'},environment).projection;
  assert.doesNotMatch(p.source,/async function mousePressed/);
  assert.match(p.source,/Promise\.resolve\(userStartAudio\(\)\)/);
  if(version[0]==='1'){assert.match(p.source,/\.setVolume\(0\.5\)/);assert.match(p.source,/\.pan\(0\)/);assert.doesNotMatch(p.source,/new p5\.Panner/);}
  else {assert.match(p.source,/\.amp\(0\.5\)/);assert.equal((p.source.match(/new p5\.Panner\(0\)/g)||[]).length,1);assert.match(p.source,/\.loop\(\);\s+rinSound\.play\(\)/);}
 });
}

test('guided palette descriptors cover every loader/resource/event and shared p5 vocabulary',()=>{
 for(const id of ['loadImage','imageLoader','asyncSetup'])assert.deepEqual(catalogue.TEMPLATES.find(t=>t.id===id).authoringAction,{type:'loadAsset',kind:'image'});
 for(const id of ['loadSound','soundLoader'])assert.deepEqual(catalogue.TEMPLATES.find(t=>t.id===id).authoringAction,{type:'loadAsset',kind:'sound'});
 for(const event of catalogue.EVENTS)assert.deepEqual(catalogue.TEMPLATES.find(t=>t.id===event.name).authoringAction,{type:'ensureEvent',name:event.name});
 for(const name of Object.keys(catalogue.P5_CALLS))assert.ok(catalogue.P5_NAMES.includes(name),name);
 assert.equal(new Set(catalogue.EVENT_NAMES).size,20);
 for(const id of ['image','texture','soundPan','toneStart','toneStop','toneFreq','toneVolume'])assert.equal(catalogue.TEMPLATES.find(t=>t.id===id).authoringAction.type,'useResource');
});

test('all supported events are top-level and duplicate requests only focus the existing function',()=>{
 for(const version of ['1.11.5','2.3.4']){
  const environment=env(version);let p=parse('function helper(){function setup(){}}',version);
  for(const event of catalogue.EVENTS){
   if(!event.versions.includes(version[0]==='1'?1:2)){rejected(p,{type:'ensureEvent',name:event.name},'event_unavailable',environment);continue;}
   const r=plan(p,{type:'ensureEvent',name:event.name},environment);p=r.projection;
   const node=p.nodes.find(n=>n.id===r.focusId);assert.equal(node.kind,'function');assert.ok(!node.parentId);assert.equal(node.fields.name.value,event.name);
   const unchanged=plan(p,{type:'ensureEvent',name:event.name},environment);assert.deepEqual(unchanged.patches,[]);assert.equal(unchanged.focusId,r.focusId);
  }
 }
});

test('custom/duplicate/indirect callback bindings are preserved and blocked',()=>{
 for(const source of [
  'function setup(){} function setup(){}', 'let setup = () => {};',
  'setup = function(){};', 'window.setup=function(){};', 'window["setup"]=function(){};',
  'self.setup=function(){};', 'globalThis["setup"]=()=>{};', 'this.setup=function(){};',
  'if(false){function setup(){}}', 'function setup(){} if(false){var setup=()=>{};}', '({setup}=callbacks);', 'function* setup(){}'
 ])rejected(parse(source),{type:'ensureEvent',name:'setup'},'callback_conflict');
 rejected(parse('export function setup(){}','2.3.4',{sourceType:'module'}),{type:'ensureEvent',name:'setup'},'callback_conflict');
});

test('new globals avoid names in local scopes, imports and unsupported code',()=>{
 const source='function helper(rinImage){ class Custom { method(){const rinImage2=1;return `${rinImage2}`;} } }\n';
 const p=parse(source),r=load(p);assert.equal(r.resourceName,'rinImage3');assert.ok(r.source.includes(source));
 const moduleSource='import rinImage from "./custom.mjs";\nfunction draw(){}';
 assert.equal(load(parse(moduleSource,'2.3.4',{sourceType:'module'})).resourceName,'rinImage2');
 assert.equal(load(parse('window["rinImage"]=custom;')).resourceName,'rinImage2');
});

test('global declarations preserve hashbang, strict directives, comments and parameter annotations',()=>{
 const source='#!/usr/bin/env node\r\n"use strict";\r\n// @rin slider(0, 10)\r\nlet size=2;\r\nfunction setup(){ createCanvas(100,100); }\r\n';
 const r=load(parse(source));assert.ok(r.source.startsWith('#!/usr/bin/env node\r\n"use strict";'));
 assert.ok(r.source.includes('// @rin slider(0, 10)\r\nlet size=2;'));
 assert.ok(r.source.includes('createCanvas(100,100);'));
 assert.equal((r.source.match(/function setup/g)||[]).length,1);assert.match(r.source,/async function setup/);
 const p=parse('"use strict"; // 😀保持\nfunction setup(){"use strict";return false;}');
 const loaded=load(p);assert.ok(loaded.source.startsWith('"use strict";'));assert.ok(loaded.source.includes('// 😀保持'));
 assert.ok(loaded.source.indexOf('"use strict";',loaded.source.indexOf('function setup'))<loaded.source.indexOf('await loadImage'));
});

test('MIME matching and JSON escaping use exact asset identity and reuse legacy asset aliases',()=>{
 const assetName='画像 "赤" test.png',environment=env('2.3.4',{assets:[{name:assetName,mime:'image/png'}]});
 const r=load(parse(''),'image',environment,assetName);assert.ok(r.source.includes(JSON.stringify(assetName)));
 rejected(parse(''),{type:'loadAsset',kind:'image',assetName:'audio.wav'},'asset_missing');
 rejected(parse(''),{type:'loadAsset',kind:'sound',assetName:'image.png'},'asset_missing');
 const p=parse('let img; async function setup(){ img = await loadImage("assets/image.png"); }');
 const reused=load(p);assert.equal(reused.resourceName,'img');assert.deepEqual(reused.patches,[]);
 assert.equal(authoring.inspect(reused.projection,env()).resources[0].assetName,'image.png');
});

test('only proven globals with valid lifecycle loading are offered; local and mutated names are excluded',()=>{
 const source='let globalImage; async function setup(){globalImage=await loadImage("image.png"); const localImage=await loadImage("image.png");}';
 assert.deepEqual(authoring.inspect(parse(source),env()).resources.map(r=>r.name),['globalImage']);
 for(const suffix of ['globalImage++;','({globalImage} = data);','[globalImage] = values;','globalImage = other;']){
  const p=parse(source+'\nfunction mutate(){'+suffix+'}');assert.deepEqual(authoring.inspect(p,env()).resources,[]);
 }
 assert.deepEqual(authoring.inspect(parse('let img;function setup(){img=loadImage("image.png");}'),env()).resources,[]);
});

test('user-defined p5 API and destination-local resource shadows are never silently called',()=>{
 for(const source of ['function loadImage(){}','let loadImage;','loadImage=custom;','window["loadImage"]=custom;','self.loadImage=custom;','function setup(loadImage){}','function setup(){const loadImage=()=>{};}'])rejected(parse(source),{type:'loadAsset',kind:'image',assetName:'image.png'},'runtime_shadowed');
 const loaded=load(parse('function draw(){let rinImage;}'));
 // The generated name intentionally avoids the local name; use a hand-authored global for the shadow rejection.
 assert.equal(loaded.resourceName,'rinImage2');
 const p=parse('let img;async function setup(){img=await loadImage("image.png");}function draw(){let img;}');
 rejected(p,{type:'useResource',action:'image',name:'img',targetEvent:'draw'},'resource_shadowed');
 const p2=parse('let img;async function setup(){img=await loadImage("image.png");}function draw(width){}');
 rejected(p2,{type:'useResource',action:'image',name:'img',targetEvent:'draw'},'runtime_shadowed');
});

test('sound requires explicit runtime enabling and play actions require an input event',()=>{
 rejected(parse(''),{type:'loadAsset',kind:'sound',assetName:'audio.wav'},'sound_disabled',env('2.3.4',{soundEnabled:false}));
 rejected(parse(''),{type:'createOscillator'},'sound_disabled',env('2.3.4',{soundEnabled:false}));
 const loaded=load(parse(''),'sound');
 for(const targetEvent of ['setup','draw','windowResized'])rejected(loaded.projection,{type:'useResource',action:'soundPlay',name:loaded.resourceName,targetEvent},'gesture_required');
});

test('persistent panning reuses its graph and refuses a custom routing chain',()=>{
 let loaded=load(parse(''),'sound'),p=loaded.projection;
 p=plan(p,{type:'useResource',action:'soundPan',name:loaded.resourceName,targetEvent:'setup'}).projection;
 p=plan(p,{type:'useResource',action:'soundPan',name:loaded.resourceName,targetEvent:'mousePressed'}).projection;
 assert.equal((p.source.match(/new p5\.Panner/g)||[]).length,1);
 const custom=parse('let sound;async function setup(){sound=await loadSound("audio.wav");sound.connect(filter);}');
 rejected(custom,{type:'useResource',action:'soundPan',name:'sound',targetEvent:'setup'},'custom_audio_routing');
});

test('WEBGL changes are explicit, preserve dimensions and other arguments, and are idempotent',()=>{
 const p=parse('function setup(){createCanvas(windowWidth, 333, P2D, canvasElement);}');
 rejected(p,{type:'ensureWebGL'},'webgl_confirmation');
 const result=plan(p,{type:'ensureWebGL',confirmed:true});assert.match(result.source,/createCanvas\(windowWidth, 333, WEBGL, canvasElement\)/);
 assert.equal(authoring.inspect(result.projection,env()).webgl,true);
 assert.deepEqual(plan(result.projection,{type:'ensureWebGL',confirmed:true}).patches,[]);
 const fresh=plan(parse(''),{type:'ensureWebGL',confirmed:true});assert.equal((fresh.source.match(/createCanvas/g)||[]).length,1);
 assert.match(fresh.source,/createCanvas\(400, 400, WEBGL\)/);
});

test('ambiguous, nested, conditional and shadowed canvases are refused without changing code',()=>{
 for(const source of [
  'function setup(){function nested(createCanvas){createCanvas(4,5);}}',
  'function setup(){if(false)createCanvas(4,5,WEBGL);}',
  'function setup(){createCanvas(4,5);createCanvas(4,5);}',
  'function setup(createCanvas){createCanvas(4,5);}',
  'function setup(){const canvas=createCanvas(4,5);}',
  'function setup(){let WEBGL="custom";createCanvas(4,5,WEBGL);}'
 ]){const p=parse(source);assert.equal(authoring.inspect(p,env()).webgl,false);rejected(p,{type:'ensureWebGL',confirmed:true},'ambiguous_canvas');}
});

test('texture requires explicit 3D and is inserted before an existing mesh',()=>{
 let loaded=load(parse('function draw(){background(0);box(30);}')),p=loaded.projection;
 rejected(p,{type:'useResource',action:'texture',name:loaded.resourceName},'webgl_required');
 p=plan(p,{type:'ensureWebGL',confirmed:true}).projection;
 p=plan(p,{type:'useResource',action:'texture',name:loaded.resourceName}).projection;
 assert.ok(p.source.indexOf('texture('+loaded.resourceName+')')<p.source.indexOf('box(30)'));
});

test('instance-mode and dynamically bound works keep their original code',()=>{
 rejected(parse('new p5(p=>{p.setup=()=>p.createCanvas(100,100);});'),{type:'ensureEvent',name:'setup'},'instance_mode');
 rejected(parse('eval("let dynamic=1");'),{type:'createOscillator'},'dynamic_scope');
 rejected(parse('(eval)("var setup=function(){}");'),{type:'ensureEvent',name:'setup'},'dynamic_scope');
 rejected(parse('window[callbackName]=function(){};'),{type:'ensureEvent',name:'setup'},'dynamic_scope');
});

test('code-side binding renames stay usable in guided image selection in both runtimes',()=>{
 for(const version of ['1.11.5','2.3.4']){
  const environment=env(version),loaded=load(parse('',version),'image',environment);
  const declaration=loaded.projection.nodes.find(n=>n.kind==='identifier'&&n.declaration&&n.fields.name.value===loaded.resourceName);
  const renamed=core.proposePatch(loaded.projection,{type:'renameBinding',id:declaration.id,name:'picture'});assert.equal(renamed.ok,true,renamed.error);
  assert.equal(authoring.inspect(renamed.projection,environment).resources.find(r=>r.kind==='image').name,'picture');
  const used=plan(renamed.projection,{type:'useResource',action:'image',name:'picture',targetEvent:'draw'},environment);assert.ok(used.source.includes('image(picture, 0, 0, width, height)'));
 }
});

test('renaming an oscillator and its started flag preserves reuse across input handlers',()=>{
 for(const version of ['1.11.5','2.3.4']){
  const environment=env(version),created=plan(parse('',version),{type:'createOscillator'},environment);
  let p=plan(created.projection,{type:'useResource',action:'toneStart',name:created.resourceName,targetEvent:'mousePressed'},environment).projection;
  for(const [oldName,newName]of [[created.resourceName,'instrument'],[created.resourceName+'Started','startedFlag']]){const n=p.nodes.find(n=>n.kind==='identifier'&&n.declaration&&n.fields.name.value===oldName);assert.ok(n);const r=core.proposePatch(p,{type:'renameBinding',id:n.id,name:newName});assert.equal(r.ok,true,r.error);p=r.projection;}
  const used=plan(p,{type:'useResource',action:'toneStart',name:'instrument',targetEvent:'keyPressed'},environment);
  assert.equal(used.projection.nodes.filter(n=>n.kind==='new'&&n.calleeName==='p5.Oscillator').length,1);
  assert.equal(used.projection.nodes.filter(n=>n.kind==='identifier'&&n.declaration&&n.fields.name.value==='startedFlag').length,1);
  assert.equal(used.source.includes('instrumentStarted'),false);
 }
});

test('Phase 3 variable inspection detects global let/const variables and excludes locals', () => {
  const source = `let score = 0;\nconst スピード = 5;\nlet a = 1, b = 2;\nfunction draw() {\n  let localVar = 10;\n  circle(mouseX, mouseY, score);\n}`;
  const p = parse(source);
  const info = authoring.inspect(p, env());
  const vars = info.variables;
  assert.equal(vars.length, 4);
  assert.deepEqual(vars.map(v => v.name), ['score', 'スピード', 'a', 'b']);
  assert.equal(vars.find(v => v.name === 'score').kind, 'let');
  assert.equal(vars.find(v => v.name === 'score').init, '0');
  assert.equal(vars.find(v => v.name === 'スピード').kind, 'const');
  assert.equal(vars.find(v => v.name === 'スピード').init, '5');
  assert.ok(!vars.some(v => v.name === 'localVar'));

  // Test module exports
  const modP = core.parse('export let exportedVar = 100;', {sourceType: 'module'});
  const modVars = authoring.inspect(modP, env()).variables;
  assert.equal(modVars.length, 1);
  assert.equal(modVars[0].name, 'exportedVar');
});

test('Phase 3 createVariable validates names, initializers, duplicates, and placement', () => {
  // Test 1: Empty sketch creation
  let p = parse('');
  let r = plan(p, {type: 'createVariable', name: 'スコア', init: '10'});
  assert.match(r.source, /let スコア = 10;/);
  assert.equal(r.variableName, 'スコア');
  assert.ok(r.focusId);

  // Test 2: Inserting after directives and hashbang
  const strictSource = '#!/usr/bin/env node\r\n"use strict";\r\nfunction setup() {}\r\n';
  p = parse(strictSource);
  r = plan(p, {type: 'createVariable', name: 'count'});
  assert.match(r.source, /#!\/usr\/bin\/env node\r\n"use strict";\r\nlet count = 0;\r\n/);

  // Test 3: Inserting immediately after existing variables
  const existingSource = 'let first = 1;\nlet second = 2;\nfunction draw() {}\n';
  p = parse(existingSource);
  r = plan(p, {type: 'createVariable', name: 'third', init: '3'});
  assert.equal(r.source, 'let first = 1;\nlet second = 2;\nlet third = 3;\nfunction draw() {}\n');

  // Test 4: Default initializer when omitted or empty string
  p = parse('');
  r = plan(p, {type: 'createVariable', name: 'x'});
  assert.match(r.source, /let x = 0;/);
  r = plan(p, {type: 'createVariable', name: 'y', init: ''});
  assert.match(r.source, /let y = 0;/);

  // Test 5: Rejection of invalid names
  p = parse('');
  rejected(p, {type: 'createVariable', name: '123invalid'}, 'invalid_name');
  rejected(p, {type: 'createVariable', name: 'my var'}, 'invalid_name');
  rejected(p, {type: 'createVariable', name: 'a-b'}, 'invalid_name');
  rejected(p, {type: 'createVariable', name: 'let'}, 'invalid_name');
  rejected(p, {type: 'createVariable', name: ''}, 'invalid_name');

  // Test 6: Rejection of duplicate names
  p = parse('let existing = 0;\nfunction setup() {}');
  rejected(p, {type: 'createVariable', name: 'existing'}, 'name_in_use');
  rejected(p, {type: 'createVariable', name: 'setup'}, 'name_in_use');

  // Test 7: Rejection of invalid initializers and statement injection attempts
  p = parse('');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '++'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '1 +'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: 'let x = 1'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '}'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '0); alert(1); //'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '0); //'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '0; alert(1)'}, 'invalid_init');
  rejected(p, {type: 'createVariable', name: 'badInit', init: '0;'}, 'invalid_init');
});
