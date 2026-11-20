'use strict';
const assert=require('node:assert/strict');
const {test}=require('node:test');
const core=require('../www/blocks/core.js');
const catalogue=require('../www/blocks/catalogue.js');
const vm=require('node:vm');
const parse=(s,opts)=>{const p=core.parse(s,opts);assert.equal(p.ok,true,JSON.stringify(p.diagnostics));return p;};
const node=(p,kind,predicate=()=>true)=>{const n=p.nodes.find(n=>n.kind===kind&&predicate(n));assert.ok(n,kind+' not projected');return n;};
const field=(p,n,key,value)=>{const r=core.proposePatch(p,{type:'setField',id:n.id,field:key,value});assert.equal(r.ok,true,r.error);return r;};

test('replacing a nested expression preserves its grouping and call target',()=>{
 for(const [source,pick,replacement,expected]of [
  ['2 * 3',n=>n.raw==='3','10 + 20',60],
  ['2 ** 3',n=>n.raw==='2','-2',-8],
  ['!(true)',n=>n.raw==='(true)','false || true',false]
 ]){
  const p=parse('globalThis.result = '+source+';'),target=p.nodes.find(pick);
  const result=core.proposePatch(p,{type:'replace',id:target.id,source:replacement});assert.equal(result.ok,true,result.error);
  const context={};vm.runInNewContext(result.source,context);assert.equal(context.result,expected,result.source);
 }
 const p=parse('f(4);'),target=node(p,'identifier',n=>n.raw==='f');
 const result=core.proposePatch(p,{type:'replace',id:target.id,source:'ok ? first : second'});
 assert.equal(result.ok,true,result.error);assert.equal(result.source,'(ok ? first : second)(4);');
});

test('inserted statements remain separate across expression braces, comments and unbraced control flow',()=>{
 for(const previous of ['let a = {}','const a = function(){}','const a = ()=>{}','if (true) globalThis.a = {}','while (false) globalThis.a = {}','globalThis.a = {}\n// keep boundary']){
  const source=previous+'\nfunction barrier(){}',p=parse(source);
  const result=core.proposePatch(p,{type:'insert',parentId:null,input:'root',index:p.rootIds.length-1,source:'[1, 2].forEach(value => output.push(value)) // keep inserted'});
  assert.equal(result.ok,true,result.error);
  const context={output:[]};vm.runInNewContext(result.source,context);assert.deepEqual(context.output,[1,2],result.source);
  assert.ok(result.source.includes('// keep inserted'));assert.equal(result.projection.rootIds.map(id=>result.projection.nodes.find(n=>n.id===id)).filter(n=>n.kind==='empty').length,0);
 }
 for(const inserted of ['globalThis.a = {} // keep tail','if(true) globalThis.a = {}\n// keep tail']){
  const p=parse('[1,2].forEach(value=>output.push(value));');
  const result=core.proposePatch(p,{type:'insert',parentId:null,input:'root',index:0,source:inserted});assert.equal(result.ok,true,result.error);
  const context={output:[]};vm.runInNewContext(result.source,context);assert.deepEqual(context.output,[1,2],result.source);assert.ok(result.source.includes('// keep tail'));
 }
});

test('moving and deleting a separating statement preserve the remaining source boundaries',()=>{
 for(const operation of ['delete','move']){
  const p=parse('let a = {} // keep boundary\nfunction barrier(){}\n[1,2].forEach(value=>output.push(value));');
  const target=node(p,'function'),op=operation==='delete'?{type:'delete',id:target.id}:{type:'move',ids:[target.id],parentId:null,input:'root',index:0};
  const result=core.proposePatch(p,op);assert.equal(result.ok,true,result.error);
  const context={output:[]};vm.runInNewContext(result.source,context);assert.deepEqual(context.output,[1,2],result.source);assert.ok(result.source.includes('// keep boundary'));
 }
 const p=parse('let a = {}\nlet b = 1;\n[1,2].forEach(value=>output.push(value));'),target=node(p,'expression_statement');
 const result=core.proposePatch(p,{type:'move',ids:[target.id],parentId:null,input:'root',index:1});assert.equal(result.ok,true,result.error);
 const context={output:[]};vm.runInNewContext(result.source,context);assert.deepEqual(context.output,[1,2],result.source);
});

test('colour edits preserve one alpha evaluation and do not capture surrounding names',()=>{
 const p=parse('function draw(c,a){fill(255,0,0,/* keep alpha */ opacity(c,a));} draw(7,8);'),call=node(p,'p5_call',n=>n.calleeName==='fill');
 const result=core.proposePatch(p,{type:'setColor',id:call.id,hex:'#3498db'});assert.equal(result.ok,true,result.error);assert.ok(result.source.includes('/* keep alpha */'));
 const values=[],context={fill:value=>values.push(value),alpha:()=>1,opacity:(c,a)=>{assert.equal(c,7);assert.equal(a,8);return 0.5;},color:hex=>({hex,setAlpha(alpha){this.alpha=alpha;}})};
 vm.runInNewContext(result.source,context);assert.equal(values.length,1);assert.equal(values[0].hex,'#3498db');assert.equal(values[0].alpha,0.5);
 const second=core.proposePatch(result.projection,{type:'setColor',id:node(result.projection,'p5_call',n=>n.calleeName==='fill').id,hex:'#ff0000'});assert.equal(second.ok,true,second.error);
 assert.ok(second.source.includes('opacity(c,a)'));assert.ok(second.source.includes('setAlpha'));assert.equal(node(second.projection,'p5_call',n=>n.calleeName==='fill').colorSwatch,'#ff0000');
 vm.runInNewContext(second.source,context);assert.equal(values[1].hex,'#ff0000');assert.equal(values[1].alpha,0.5);
 for(const source of ['function draw(color){fill(255,0,0,40);}','function fill(a,b){};fill(100,40);']){
  const q=parse(source),target=q.nodes.find(n=>['call','p5_call'].includes(n.kind)&&n.calleeName==='fill'),rejected=core.proposePatch(q,{type:'setColor',id:target.id,hex:'#ff0000'});
  assert.equal(rejected.ok,false);assert.equal(rejected.source,source);
 }
});

test('noncontiguous move fragments stay separate and removed tails leave no empty statements',()=>{
 const p=parse('let a={}\nfunction barrier(){}\n[1].forEach(value=>output.push(value));'),first=node(p,'variable_declaration'),last=node(p,'expression_statement');
 const result=core.proposePatch(p,{type:'move',ids:[first.id,last.id],parentId:null,input:'root',index:0});assert.equal(result.ok,true,result.error);
 const context={output:[]};vm.runInNewContext(result.source,context);assert.deepEqual(context.output,[1]);
 const q=parse('f()\n// keep\ng();'),moved=core.proposePatch(q,{type:'move',id:q.rootIds[0],parentId:null,input:'root',index:2});assert.equal(moved.ok,true,moved.error);assert.equal(moved.projection.nodes.some(n=>n.kind==='empty'),false,moved.source);
});

test('identical statement stacks can be removed atomically without remapping intermediate IDs',()=>{
 const p=parse('function draw(){circle(1,2,3);circle(1,2,3);circle(1,2,3);}');
 const ids=p.nodes.filter(n=>n.kind==='expression_statement').map(n=>n.id);
 const r=core.proposePatch(p,{type:'delete',ids:ids.slice(1)});assert.equal(r.ok,true,r.error);
 assert.equal(r.projection.nodes.filter(n=>n.kind==='expression_statement').length,1);
 assert.equal(r.source,'function draw(){circle(1,2,3);}');
});

test('only top-level p5 lifecycle functions are labelled as events',()=>{
 const p=parse('function setup(){} function helper(){ function draw(){} }');
 assert.equal(node(p,'function',n=>n.fields.name.value==='setup').event,true);
 assert.equal(node(p,'function',n=>n.fields.name.value==='draw').event,false);
});

test('view projection is lossless for comments, CRLF, directives, regex, template and custom syntax',()=>{
 const source='// @rin speed min=0 max=10\r\n"use strict";\r\nlet speed = 3; // trailing\r\nconst pattern = /a\\/b/giu;\r\nconst template = `hello ${speed}`;\r\nclass Sprite { move(){ this.x++; } }\r\nfunction draw() { while (false) { break; } circle(1,2,speed); }\r\n';
 const p=parse(source);assert.equal(p.source,source);assert.equal(core.applyPatches(source,[]),source);assert.ok(p.nodes.some(n=>n.kind==='raw_statement'&&n.raw.startsWith('class')));assert.ok(p.nodes.some(n=>n.kind==='raw_statement'&&n.raw.startsWith('while')));assert.ok(p.nodes.some(n=>n.kind==='raw_expression'&&n.raw.startsWith('`')));assert.ok(p.comments.some(c=>c.text.includes('@rin')));
});

test('field edits touch only their original bytes and retain stable block IDs',()=>{
 const source='// @rin count\r\nlet count = 10;\r\nfunction draw() {\r\n  circle(0, 0, count); // keep\r\n}\r\n';const p=parse(source),literal=node(p,'literal',n=>n.fields.value.value===10);
 const r=field(p,literal,'value',25);assert.equal(r.source,source.replace('= 10','= 25'));assert.deepEqual(r.patches,[{start:source.indexOf('10'),end:source.indexOf('10')+2,text:'25'}]);assert.ok(r.projection.nodes.some(n=>n.id===literal.id&&n.fields.value.value===25));assert.equal(p.source,source);
});

test('lexical scope disambiguates p5 calls, including hoisted vars, params, block bindings and imports',()=>{
 const p=parse('circle(0,0,1); function custom(circle) { circle(1); } function f(){ box(1); var box; } { let sphere; sphere(1); } background(0);');
 assert.equal(p.nodes.filter(n=>n.kind==='p5_call').length,2);assert.equal(p.nodes.filter(n=>n.kind==='call').length,3);const custom=node(p,'call',n=>n.calleeName==='circle');assert.ok(custom.visibleNames.includes('circle'));
 const m=parse('import { circle } from "library"; circle(1);',{sourceType:'module'});assert.equal(node(m,'call').calleeName,'circle');assert.ok(!m.nodes.some(n=>n.kind==='p5_call'));
});

test('variables retain JavaScript declaration kind and array indices remain zero-based',()=>{
 const p=parse('let a=[10,20]; const b=a[0]; var c=a.length;');assert.deepEqual(p.nodes.filter(n=>n.kind==='variable_declaration').map(n=>n.fields.kind.value),['let','const','var']);const zero=node(p,'literal',n=>n.fields.value.value===0);assert.equal(core.proposePatch(p,{type:'setField',id:zero.id,field:'value',value:1}).source,'let a=[10,20]; const b=a[1]; var c=a.length;');
});

test('control, assignments, return, async functions and sound/3D nodes project without executing',()=>{
 const source='let sound; async function setup(){ createCanvas(400,400,WEBGL); sound = await loadSound("tone.wav"); const osc = new p5.Oscillator("sine"); osc.start(); } function draw(){ for(let i=0;i<4;i++){if(i>1){rotateY(0.2);box(30);}else{sphere(20);}} for(const x of [1,2]){text(x,0,0);} } function twice(x){return x*2;}';
 const p=parse(source);for(const k of ['function','for','for_of','if','block','assignment','await','new','return','binary','update','array'])node(p,k);assert.ok(node(p,'p5_call',n=>n.calleeName==='loadSound').requirements.sound);assert.ok(node(p,'p5_call',n=>n.calleeName==='box').requirements.webgl);assert.equal(node(p,'function',n=>n.fields.name.value==='setup').async,true);assert.equal(p.source,source);
});

test('unknown expressions and comments survive unrelated nested edits',()=>{
 const source='function draw(){ const object={ x:1, y:()=>2 };\n const s=`${object.x}`;\n circle(/* x */ 12, /* y */ 9, 80);\n // keep end\n}';const p=parse(source),lit=node(p,'literal',n=>n.fields.value.value===80);assert.equal(field(p,lit,'value',90).source,source.replace('80','90'));
});

test('operator edits preserve explicit tree grouping and comments',()=>{
 const p=parse('let x = a + b /* keep */ * c;'),mul=node(p,'binary',n=>n.fields.operator.value==='*');const r=field(p,mul,'operator','+');assert.ok(r.source.includes('/* keep */'));const outer=node(r.projection,'binary',n=>n.fields.operator.value==='+');const right=r.projection.nodes.find(n=>n.id===outer.inputs.right);assert.equal(right.kind,'parenthesized');assert.match(r.source,/a \+ \(\(b\)/);
});

test('invalid identifier, operator, literal, and invalid move never alter the projection',()=>{
 const p=parse('let size=40; function draw(){circle(1,2,size);}');const id=node(p,'identifier',n=>n.fields.name.value==='size');for(const op of [{type:'setField',id:id.id,field:'name',value:'bad name'},{type:'setField',id:node(p,'literal').id,field:'value',value:'not-number'},{type:'setField',id:id.id,field:'unknown',value:'x'},{type:'move',id:node(p,'function').id,parentId:node(p,'function').id,input:'body',index:0}]){const r=core.proposePatch(p,op);assert.equal(r.ok,false);assert.equal(r.source,p.source);assert.strictEqual(r.projection,p);assert.deepEqual(r.patches,[]);}
});

test('statement insertion, deletion, duplication and explicit reorder are syntactically safe',()=>{
 let p=parse('function draw(){\n  background(0);\n  circle(1,2,3);\n}');let f=node(p,'function');let r=core.proposePatch(p,{type:'insert',parentId:f.id,input:'body',index:1,source:'box(30);'});assert.equal(r.ok,true,r.error);p=r.projection;assert.deepEqual(p.nodes.find(n=>n.id===f.id).inputs.body.map(id=>p.nodes.find(n=>n.id===id).raw),['background(0);','box(30);','circle(1,2,3);']);
 const box=node(p,'expression_statement',n=>n.raw==='box(30);');r=core.proposePatch(p,{type:'duplicate',id:box.id});assert.equal(r.ok,true,r.error);assert.equal(r.projection.nodes.filter(n=>n.kind==='p5_call'&&n.calleeName==='box').length,2);
 r=core.proposePatch(p,{type:'delete',id:box.id});assert.equal(r.ok,true,r.error);assert.ok(!r.source.includes('box'));
 p=parse('let a=1;\nfunction draw(){}\nlet b=2;');const b=node(p,'variable_declaration',n=>n.raw.includes('b='));r=core.proposePatch(p,{type:'move',id:b.id,parentId:null,index:0});assert.equal(r.ok,true,r.error);assert.equal(r.projection.rootIds.map(id=>r.projection.nodes.find(n=>n.id===id).raw).join('|'),'let b=2;|let a=1;|function draw(){}');
});

test('empty bodies and statement boundaries permit safe insertions with CRLF preserved',()=>{
 const p=parse('function draw() {\r\n}\r\n');const f=node(p,'function');const r=core.proposePatch(p,{type:'insert',parentId:f.id,input:'body',index:0,source:'circle(0,0,10)'});assert.equal(r.ok,true,r.error);assert.match(r.source,/circle\(0,0,10\);/);assert.equal(r.source.replace(/\r\n/g,'' ).includes('\n'),false);
 const q=parse('f()');const next=core.proposePatch(q,{type:'insert',parentId:null,index:1,source:'[1].forEach(g)'});assert.equal(next.ok,true,next.error);assert.equal(next.projection.rootIds.length,2);
});

test('renderNode edits detached blocks and input expressions without evaluating source',()=>{
 const p=parse('circle(1,2,3);'),call=node(p,'p5_call');const rendered=core.renderNode(p,call.id,{inputs:{args:['mouseX','mouseY','80']}});assert.equal(rendered.ok,true,rendered.error);assert.equal(rendered.source,'circle(mouseX,mouseY,80)');const literal=node(p,'literal');const r=core.proposePatch(p,{type:'replace',id:literal.id,source:'Math.random()'});assert.equal(r.ok,true,r.error);assert.equal(r.source,'circle((Math.random()),2,3);');
});

test('string edits preserve quote style and escape content safely',()=>{
 const p=parse("text('old',1,2);");const n=node(p,'literal',n=>n.valueType==='string');const r=field(p,n,'value',"it's\nnew\\text");assert.equal(node(r.projection,'literal',n=>n.valueType==='string').fields.value.value,"it's\nnew\\text");assert.match(r.source,/text\('/);
});

test('parse errors are read-only, and stale revision operations are rejected',()=>{
 const p=core.parse('function draw( {');assert.equal(p.ok,false);assert.equal(p.diagnostics[0].severity,'error');assert.equal(core.proposePatch(p,{type:'insert',source:'x();'}).ok,false);const q=parse('circle(1);');const r=core.proposePatch(q,{type:'setField',id:node(q,'literal').id,field:'value',value:2,expectedRevision:'old'});assert.equal(r.ok,false);assert.equal(r.source,q.source);
});

test('500-block projection remains source-faithful and metadata serializes cleanly',()=>{
 const source='function draw(){\n'+Array.from({length:500},(_,i)=>'circle('+i+',2,3);').join('\n')+'\n}';const p=parse(source);assert.equal(p.nodes.filter(n=>n.kind==='p5_call').length,500);assert.equal(JSON.parse(JSON.stringify(p)).source,source);const restored=parse(source,{metadata:p.metadata});assert.deepEqual(restored.nodes.map(n=>n.id),p.nodes.map(n=>n.id));
});

test('input list replacements preserve comments and exact separators',()=>{
 const p=parse('circle(/* x */ 1,\n /* y */ 2, /* @rin r */ 3);');const call=node(p,'p5_call');const r=core.proposePatch(p,{type:'setInput',id:call.id,input:'args',source:['mouseX','mouseY','40']});assert.equal(r.ok,true,r.error);assert.equal(r.source,'circle(/* x */ mouseX,\n /* y */ mouseY, /* @rin r */ 40);');
 const more=core.proposePatch(p,{type:'setInput',id:call.id,input:'args',source:['10','20','30','40']});assert.equal(more.ok,true,more.error);for(const comment of ['/* x */','/* y */','/* @rin r */'])assert.ok(more.source.includes(comment));
});

test('missing loop slots and optional initializers can be edited without rebuilding the function',()=>{
 const p=parse('function draw(){for(;;){circle(1);}}');const loop=node(p,'for');let r=core.proposePatch(p,{type:'setInput',id:loop.id,input:'init',source:'let i=0'});assert.equal(r.ok,true,r.error);r=core.proposePatch(r.projection,{type:'setInput',id:loop.id,input:'test',source:'i<5'});assert.equal(r.ok,true,r.error);assert.ok(r.source.includes('for(let i=0;i<5;)'));
 const q=parse('let a /* keep */ = /* init */ 1;');const decl=node(q,'variable_declarator');r=core.proposePatch(q,{type:'setInput',id:decl.id,input:'init',source:''});assert.equal(r.ok,true,r.error);assert.ok(r.source.includes('/* keep */'));assert.ok(r.source.includes('/* init */'));
});

test('@rin annotations travel with moved declarations and are removed with deleted declarations',()=>{
 const p=parse('// @rin amount min=0 max=10\nlet amount=5;\nlet other=2;');const decl=node(p,'variable_declaration',n=>n.raw.includes('amount'));
 const moved=core.proposePatch(p,{type:'move',id:decl.id,parentId:null,index:p.rootIds.length});assert.equal(moved.ok,true,moved.error);assert.match(moved.source,/let other=2;\n\/\/ @rin amount min=0 max=10\nlet amount=5;/);
 const removed=core.proposePatch(p,{type:'delete',id:decl.id});assert.equal(removed.ok,true,removed.error);assert.ok(!removed.source.includes('@rin'));assert.ok(removed.source.includes('let other=2;'));
});

test('emoji changes produce whole UTF-16 source patches compatible with native validation',()=>{
 const p=parse("text('😀',1,2);");const literal=node(p,'literal',n=>n.valueType==='string');const r=field(p,literal,'value','😁');assert.equal(r.source,"text('😁',1,2);");const start=p.source.indexOf('😀');assert.deepEqual(r.patches,[{start,end:start+2,text:'😁'}]);assert.equal(core.applyPatches(p.source,r.patches),r.source);assert.throws(()=>core.applyPatches(p.source,[{start:start+1,end:start+2,text:'x'}]),/UTF-16/);assert.throws(()=>core.applyPatches('x',[{start:0,end:0,text:'a'},{start:0,end:0,text:'b'}]),/Coincident/);
});

test('a connected statement stack moves atomically using the final destination index',()=>{
 const p=parse('function draw(){background(0);circle(1,2,3);box(4);sphere(5);}');const f=node(p,'function'),body=f.inputs.body;
 const r=core.proposePatch(p,{type:'move',ids:[body[1],body[2]],parentId:f.id,input:'body',index:2});assert.equal(r.ok,true,r.error);const moved=r.projection.nodes.find(n=>n.id===f.id);assert.deepEqual(moved.inputs.body.map(id=>r.projection.nodes.find(n=>n.id===id).raw),['background(0);','sphere(5);','circle(1,2,3);','box(4);']);
 const invalid=core.proposePatch(p,{type:'move',ids:[f.id,body[0]],parentId:null,index:0});assert.equal(invalid.ok,false);assert.equal(invalid.source,p.source);
});

test('dragging an annotation and owner together does not duplicate or lose annotation text',()=>{
 const p=parse('// @rin amount\nlet amount=5;\nlet other=2;');const annotation=node(p,'comment'),declaration=node(p,'variable_declaration',n=>n.raw.includes('amount'));
 const r=core.proposePatch(p,{type:'move',ids:[annotation.id,declaration.id],parentId:null,index:1});assert.equal(r.ok,true,r.error);assert.equal((r.source.match(/@rin/g)||[]).length,1);assert.match(r.source,/let other=2;\n\/\/ @rin amount\nlet amount=5;/);
});

test('binding renames update resolved uses, preserve shadows, keys, strings, and custom-code references',()=>{
 const source='// @rin size min=0 max=10\r\nlet size = 3;\r\nconst object={size, value:size, "size":"size"};\r\nfunction helper(size){return size;}\r\nfunction draw(){text(`${size}`,0,0); object.size=size;}\r\n';
 const p=parse(source),declaration=node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size');
 assert.equal(declaration.renameable,true);
 const r=core.proposePatch(p,{type:'renameBinding',id:declaration.id,name:'diameter'});assert.equal(r.ok,true,r.error);
 assert.equal(r.source,'// @rin size min=0 max=10\r\nlet diameter = 3;\r\nconst object={size: diameter, value:diameter, "size":"size"};\r\nfunction helper(size){return size;}\r\nfunction draw(){text(`${diameter}`,0,0); object.size=diameter;}\r\n');
 assert.equal(core.applyPatches(source,r.patches),r.source);
});

test('renaming from a use resolves the correct let, var, loop, catch and parameter bindings',()=>{
 for(const [source,pick,expected] of [
  ['let size=1; function f(){var size=2; {size++;}} size++;',n=>n.raw==='size'&&n.start>30,'let size=1; function f(){var local=2; {local++;}} size++;'],
  ['let size=1; for(let size=0;size<2;size++){circle(size);} circle(size);',n=>n.raw==='size'&&n.start>30&&n.start<50,'let size=1; for(let local=0;local<2;local++){circle(local);} circle(size);'],
  ['let size=1; try{fail();}catch(size){text(size);} text(size);',n=>n.raw==='size'&&n.start>49,'let local=1; try{fail();}catch(size){text(size);} text(local);'],
  ['let size=1; function f(size){var size; return size;} text(size);',n=>n.raw==='size'&&n.start>40&&n.start<50,'let size=1; function f(local){var local; return local;} text(size);']
 ]){const p=parse(source),target=node(p,'identifier',pick),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'local'});assert.equal(r.ok,true,r.error);assert.equal(r.source,expected);}
});

test('binding rename keeps destructuring property keys and object shorthand meaning',()=>{
 const source='const {size=3, other: value} = object; const x={size}; text(size+value);';
 const p=parse(source),use=node(p,'identifier',n=>n.fields.name.value==='size'&&!n.declaration);
 const r=core.proposePatch(p,{type:'renameBinding',id:use.id,name:'diameter'});assert.equal(r.ok,true,r.error);
 assert.equal(r.source,'const {size: diameter=3, other: value} = object; const x={size: diameter}; text(diameter+value);');
});

test('renames reject existing bindings, inner capture, newly captured globals, eval, and with',()=>{
 for(const [source,name] of [
  ['let size=1, diameter=2; circle(size);','diameter'],
  ['let size=1; function f(diameter){circle(size);}','diameter'],
  ['let size=1; function f(){circle(width);} circle(size);','width'],
  ['let size=1; function f(){eval("size");} circle(size);','diameter'],
  ['let size=1; with(object){circle(size);}','diameter'],
  ['let size=1; if(true){function size(){}} circle(size);','diameter']
 ]){const p=parse(source),target=node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size'),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name});assert.equal(r.ok,false,source);assert.ok(r.errorCode,source);assert.equal(r.source,source);assert.deepEqual(r.patches,[]);}
});

test('imports, exported names, and unresolved references are not silently renamed',()=>{
 for(const [source,opts] of [['import {size} from "x"; circle(size);',{sourceType:'module'}],['export let size=1; circle(size);',{sourceType:'module'}],['circle(size);',{}]]){const p=parse(source,opts),target=node(p,'identifier',n=>n.fields.name.value==='size'),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'diameter'});assert.equal(r.ok,false);assert.equal(target.renameable,false);assert.equal(r.source,source);}
});

test('class static var bindings and default parameter lookups do not leak into outer renames',()=>{
 const source='let size=1; class C{static{var size=2; text(size);} m(){return size;}} function f(value=size){var size=3; return value;} text(size);';
 const p=parse(source),target=node(p,'identifier',n=>n.declaration&&n.start===4),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'diameter'});assert.equal(r.ok,true,r.error);
 assert.equal(r.source,'let diameter=1; class C{static{var size=2; text(size);} m(){return diameter;}} function f(value=diameter){var size=3; return value;} text(diameter);');
});

test('computed destructuring keys resolve where the var declaration is evaluated',()=>{
 const source='let key="outer"; function f(){ {let key="inner"; var {[key]: value}=object; text(key);} return value;}';
 const p=parse(source),target=node(p,'identifier',n=>n.declaration&&n.start>30&&n.fields.name.value==='key');
 const r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'localKey'});assert.equal(r.ok,true,r.error);assert.equal(r.source,source.replace('let key="inner"','let localKey="inner"').replace('[key]','[localKey]').replace('text(key)','text(localKey)'));
});

test('function and parameter renames include recursion and keep same-named helpers separate',()=>{
 const source='function shape(x){if(x>0)return shape(x-1);} function other(){function shape(x){return x;} return shape(2);} shape(3);';
 const p=parse(source),fn=node(p,'function',n=>n.fields.name.value==='shape'&&!n.parentId);
 assert.equal(fn.parametersEditable,true);assert.deepEqual(fn.parameters.map(p=>p.name),['x']);
 let r=core.proposePatch(p,{type:'renameBinding',id:fn.id,name:'paint'});assert.equal(r.ok,true,r.error);assert.equal(r.source,source.replace('function shape(x){if','function paint(x){if').replace('return shape(x-1)','return paint(x-1)').replace('} shape(3);','} paint(3);'));
 const renamed=node(r.projection,'function',n=>n.fields.name.value==='paint');r=core.proposePatch(r.projection,{type:'renameBinding',id:renamed.id,parameterIndex:0,name:'count'});assert.equal(r.ok,true,r.error);assert.match(r.source,/function paint\(count\)\{if\(count>0\)return paint\(count-1\);\}/);assert.match(r.source,/function shape\(x\)\{return x;\}/);
});

test('call argument and array item count changes preserve all untouched separators and comments',()=>{
 for(const [source,kind,input] of [['circle(/* x */ 1,\r\n /* y */ 2, /* radius */ 3,);','p5_call','args'],['const values=[/* x */ 1,\r\n /* y */ 2, /* last */ 3,];','array','elements']]){
  const p=parse(source),target=node(p,kind),added=core.proposePatch(p,{type:'insertInput',id:target.id,input,index:1,source:'mouseX'});assert.equal(added.ok,true,added.error);assert.equal(added.source,source.replace(',\r\n /* y */',',mouseX, \r\n /* y */'));
  const next=node(added.projection,kind),removed=core.proposePatch(added.projection,{type:'removeInput',id:next.id,input,index:1});assert.equal(removed.ok,true,removed.error);for(const c of ['/* x */','/* y */','/* last */','/* radius */'])if(source.includes(c))assert.ok(removed.source.includes(c));assert.equal(node(removed.projection,kind).inputs[input].length,3);
 }
});

test('array holes, empty lists, nested expressions, trailing commas and line comments stay valid',()=>{
 for(const source of ['let a=[];','let a=[,];','let a=[,,];','let a=[1,,];','let a=[f(1,2),{x:3,y:4},];','let a=[1 //keep\n];']){
  const p=parse(source),array=node(p,'array'),count=array.inputs.elements.length,r=core.proposePatch(p,{type:'insertInput',id:array.id,input:'elements',index:count,source:'9'});assert.equal(r.ok,true,r.error);const next=node(r.projection,'array');assert.equal(next.inputs.elements.length,count+1);assert.equal(r.projection.nodes.find(n=>n.id===next.inputs.elements[count]).raw,'9');
  for(let i=0;i<next.inputs.elements.length;i++){const q=core.proposePatch(r.projection,{type:'removeInput',id:next.id,input:'elements',index:i});assert.equal(q.ok,true,q.error);assert.equal(node(q.projection,'array').inputs.elements.length,count);if(source.includes('//keep'))assert.ok(q.source.includes('//keep'));}
 }
});

test('function parameter insertion synchronizes resolved local callsites, omitted arguments, comments and recursion',()=>{
 const source='function shape(x /* keep */, z){if(x)shape(x-1,z);} shape(1,2); shape(); {const shape=x=>x; shape(3);}';
 const p=parse(source),fn=node(p,'function',n=>n.fields.name.value==='shape');const r=core.proposePatch(p,{type:'insertParameter',id:fn.id,index:1,name:'y',argument:'10'});assert.equal(r.ok,true,r.error);
 assert.equal(r.source,'function shape(x /* keep */,y,  z){if(x)shape(x-1,10, z);} shape(1,10, 2); shape(void 0, 10); {const shape=x=>x; shape(3);}');
});

test('unused function parameter removal synchronizes calls and retains list comments',()=>{
 const source='function shape(x, /* remove */ unused, y){return x+y;} shape(1, /* old */ 2, 3); shape(4);';
 const p=parse(source),fn=node(p,'function');const r=core.proposePatch(p,{type:'removeParameter',id:fn.id,index:1});assert.equal(r.ok,true,r.error);assert.equal(r.source,'function shape(x, /* remove */  y){return x+y;} shape(1, /* old */  3); shape(4);');
});

test('unsafe parameter changes reject without source edits',()=>{
 for(const [source,type,index] of [
  ['function shape(x){return x;} shape(1);','removeParameter',0],
  ['function shape(x){} const callback=shape;','insertParameter',1],
  ['function shape(x){} shape(...values);','insertParameter',1],
  ['function shape(x=1){} shape();','insertParameter',1],
  ['function shape(x){return arguments[0];} shape(1);','insertParameter',1],
  ['function shape(x){return y;} shape(1);','insertParameter',1],
  ['function setup(){}','insertParameter',0]
 ]){const p=parse(source),fn=node(p,'function'),r=core.proposePatch(p,{type,id:fn.id,index,name:'y',argument:'0'});assert.equal(r.ok,false,source);assert.ok(r.errorCode);assert.equal(r.source,source);assert.deepEqual(r.patches,[]);}
});

test('atomic call selection changes callee and argument count without stale node IDs',()=>{
 const p=parse('function paint(x,y){} drawShape(/* x */ 10);'),call=node(p,'call');const r=core.proposePatch(p,{type:'setCall',id:call.id,callee:'paint',args:['10','20']});assert.equal(r.ok,true,r.error);assert.equal(r.source,'function paint(x,y){} paint(/* x */ 10, 20);');assert.equal(node(r.projection,'call').calleeName,'paint');
});

test('else insertion and removal keep original single-statement branch and surrounding code',()=>{
 const source='//before\nif(ok) circle(1); //after\ntext("done");';const p=parse(source),branch=node(p,'if'),r=core.proposePatch(p,{type:'setInput',id:branch.id,input:'alternate',source:'{\n  circle(2);\n}'});assert.equal(r.ok,true,r.error);assert.ok(r.source.includes('if(ok) circle(1); else {'));const next=node(r.projection,'if'),removed=core.proposePatch(r.projection,{type:'setInput',id:next.id,input:'alternate',source:''});assert.equal(removed.ok,true,removed.error);assert.ok(removed.source.includes('//before\nif(ok) circle(1);'));assert.ok(removed.source.includes('//after\ntext("done");'));
});

test('implicit arguments and parenthesized direct eval prevent unsafe renames',()=>{
 for(const [source,name] of [['let size=1; function f(){return size;}','arguments'],['let size=1; function f(){return ()=>size;}','arguments'],['let size=1; function f(){(eval)("size");}','diameter']]){const p=parse(source),target=node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size'),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name});assert.equal(r.ok,false);assert.equal(r.source,source);assert.ok(r.errorCode);}
});

test('nested call removal keeps one transaction and omitted arguments use unshadowable undefined',()=>{
 const p=parse('function shape(unused){} shape(shape(1));'),fn=node(p,'function');const r=core.proposePatch(p,{type:'removeParameter',id:fn.id,index:0});assert.equal(r.ok,true,r.error);assert.equal(r.source,'function shape(){} shape();');
 const q=parse('function shape(x){} {let undefined=99; shape();}'),added=core.proposePatch(q,{type:'insertParameter',id:node(q,'function').id,index:1,name:'y',argument:'0'});assert.equal(added.ok,true,added.error);assert.match(added.source,/shape\(void 0, 0\)/);
});

test('inserted input accepts one expression and rejects multiple-statement escape text',()=>{
 const p=parse('shape(1);'),call=node(p,'call');for(const source of ['0); evil(); f(1','1,2','']){const r=core.proposePatch(p,{type:'insertInput',id:call.id,input:'args',index:1,source});assert.equal(r.ok,false,source);assert.equal(r.source,p.source);assert.equal(r.errorCode,'invalid_argument');}
});

test('function choices follow lexical bindings and exclude duplicate or externally ambiguous definitions',()=>{
 const p=parse('function shape(x,y){} function run(){const shape=x=>x; text(1);} function local(){function shape(x){} text(2);} text(3);');
 const first=node(p,'p5_call',n=>n.raw==='text(1)'),second=node(p,'p5_call',n=>n.raw==='text(2)'),third=node(p,'p5_call',n=>n.raw==='text(3)');
 assert.ok(!first.functionChoices.some(f=>f.name==='shape'));assert.deepEqual(second.functionChoices.find(f=>f.name==='shape').parameters,['x']);assert.deepEqual(third.functionChoices.find(f=>f.name==='shape').parameters,['x','y']);
 for(const source of ['function shape(x){} function shape(x,y){} text(1);','var shape; function shape(x){} text(1);']){const q=parse(source);assert.ok(!node(q,'p5_call').functionChoices.some(f=>f.name==='shape'));}
});

test('script global property references conservatively block variable rename and function signature changes',()=>{
 for(const member of ['window.size','globalThis["size"]','self.size','((window)).size','window[key]']){const source='var size=1; text(size); text('+member+');',p=parse(source),target=node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size'),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'diameter'});assert.equal(r.ok,false,member);assert.equal(r.errorCode,'reflective_global');assert.equal(r.source,source);}
 for(const operation of [{type:'renameBinding',name:'paint'},{type:'insertParameter',index:1,name:'y',argument:'0'},{type:'removeParameter',index:0}]){const source='function shape(unused){} window["shape"](1);',p=parse(source),fn=node(p,'function'),r=core.proposePatch(p,{...operation,id:fn.id});assert.equal(r.ok,false,operation.type);assert.equal(r.errorCode,'reflective_global');assert.equal(r.source,source);}
});

test('shadowed window, ordinary properties, lexical globals and module variables retain independent meaning',()=>{
 for(const [source,opts,expected] of [
  ['var size=1; function f(window){return window.size+size;} text(size);',{},'var diameter=1; function f(window){return window.size+diameter;} text(diameter);'],
  ['var size=1; text(object.size+size);',{},'var diameter=1; text(object.size+diameter);'],
  ['let size=1; text(window.size+size);',{},'let diameter=1; text(window.size+diameter);'],
  ['var size=1; text(window.size+size);',{sourceType:'module'},'var diameter=1; text(window.size+diameter);']
 ]){const p=parse(source,opts),target=node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size'),r=core.proposePatch(p,{type:'renameBinding',id:target.id,name:'diameter'});assert.equal(r.ok,true,r.error);assert.equal(r.source,expected);}
 const q=parse('function shape(x){} function f(window){window.shape(1);} shape(2);'),added=core.proposePatch(q,{type:'insertParameter',id:node(q,'function',n=>n.fields.name.value==='shape').id,index:1,name:'y',argument:'0'});assert.equal(added.ok,true,added.error);assert.match(added.source,/window.shape\(1\)/);assert.match(added.source,/shape\(2, 0\)/);
});


test('else deletion identifies the keyword after comments containing else',()=>{
 for(const gap of ['/* keep else note */ ', '// keep else note\n']){
  const source='if(ok) circle(1); '+gap+'else /* keep after */ { circle(2); } text(3);';const p=parse(source),branch=node(p,'if'),r=core.proposePatch(p,{type:'setInput',id:branch.id,input:'alternate',source:''});assert.equal(r.ok,true,r.error);assert.ok(r.source.includes(gap));assert.ok(r.source.includes('/* keep after */'));assert.equal(node(r.projection,'if').inputs.alternate,null);assert.ok(r.projection.rootIds.some(id=>r.projection.nodes.find(n=>n.id===id)?.raw==='text(3);'));assert.equal(r.source.includes('circle(2)'),false);
 }
});
test('else insertion attaches to the selected outer condition around nested open branches',()=>{
 for(const consequent of ['if(inner) circle(1);','while(again) if(inner) circle(1);','for(;again;) if(inner) circle(1);','if(first) circle(1); else if(second) circle(2);']){
  const p=parse('if(outer) '+consequent+' text(3);'),outer=node(p,'if'),r=core.proposePatch(p,{type:'setInput',id:outer.id,input:'alternate',source:'{ circle(9); }'});assert.equal(r.ok,true,r.error);const next=node(r.projection,'if');assert.ok(next.inputs.alternate,r.source);assert.ok(r.projection.nodes.find(n=>n.id===next.inputs.consequent).raw.includes(consequent));assert.ok(r.projection.rootIds.some(id=>r.projection.nodes.find(n=>n.id===id)?.raw==='text(3);'));
 }
});

test('dangling sloppy function declarations are preserved when adding else is ambiguous',()=>{const p=parse('if(outer) if(inner) function f(){}'),r=core.proposePatch(p,{type:'setInput',id:node(p,'if').id,input:'alternate',source:'{}'});assert.equal(r.ok,false);assert.equal(r.errorCode,'ambiguous_branch');assert.equal(r.source,p.source);});

test('removing inner else preserves the meaning of an enclosing else',()=>{for(const prefix of ['', 'for(;again;) ']){const p=parse('if(outer) '+prefix+'if(inner) circle(1); else circle(2); else circle(3); text(4);'),inner=p.nodes.filter(n=>n.kind==='if')[1],r=core.proposePatch(p,{type:'setInput',id:inner.id,input:'alternate',source:''});assert.equal(r.ok,true,r.error);const outer=node(r.projection,'if');assert.ok(outer.inputs.alternate,r.source);assert.equal(r.projection.nodes.find(n=>n.id===outer.inputs.alternate).raw,'circle(3);');assert.equal(r.projection.nodes.filter(n=>n.kind==='if')[1].inputs.alternate,null);assert.ok(r.projection.rootIds.some(id=>r.projection.nodes.find(n=>n.id===id)?.raw==='text(4);'));}});

test('named class expression extends resolves inside the class name scope',()=>{const p=parse('let size=Object; const C=class Local extends size {};'),r=core.proposePatch(p,{type:'renameBinding',id:node(p,'identifier',n=>n.declaration&&n.fields.name.value==='size').id,name:'Local'});assert.equal(r.ok,false);assert.equal(r.errorCode,'name_capture');assert.equal(r.source,p.source);const q=parse('let Local=Object; const C=class Local extends Local {}; console.log(Local);'),safe=core.proposePatch(q,{type:'renameBinding',id:node(q,'identifier',n=>n.declaration&&n.fields.name.value==='Local').id,name:'Other'});assert.equal(safe.ok,true,safe.error);assert.equal(safe.source,'let Other=Object; const C=class Local extends Local {}; console.log(Other);');});

test('Phase 2 animation and math functions project as p5_call blocks with editable arguments',()=>{
 const source='let d = dist(0, 0, mouseX, mouseY); let smooth = lerp(0, 100, 0.5); let v = abs(-10) + floor(1.5) + round(2.3) + ceil(3.1) + min(1, 2) + max(3, 4);';
 const p=parse(source);
 for(const name of ['dist','lerp','abs','floor','round','ceil','min','max']){
  const n=node(p,'p5_call',x=>x.calleeName===name);
  assert.equal(n.p5,true);
  assert.ok(n.inputs.args.length>=1,name+' has arguments');
 }
 const distCall=node(p,'p5_call',x=>x.calleeName==='dist');
 assert.equal(distCall.inputs.args.length,4);
 const r=core.proposePatch(p,{type:'insertInput',id:distCall.id,input:'args',index:4,source:'50'});
 assert.equal(r.ok,true);
 assert.match(r.source,/dist\(0, 0, mouseX, mouseY, 50\)/);
});

test('Phase 2 mobile inputs and lifecycle events are recognized as top-level p5 members',()=>{
 const source='function touchMoved(){ return false; }\nfunction touchEnded(){ return false; }\nfunction mouseDragged(){}\nfunction mouseReleased(){}\nfunction keyPressed(){}\nfunction keyReleased(){}\nfunction windowResized(){ resizeCanvas(windowWidth, windowHeight); }\nfunction deviceMoved(){}\nfunction deviceShaken(){}\nfunction draw(){ let ax = accelerationX; let ay = accelerationY; let rx = rotationX; let t = touches.length; }';
 const p=parse(source);
 for(const eventName of ['touchMoved','touchEnded','mouseDragged','mouseReleased','keyPressed','keyReleased','windowResized','deviceMoved','deviceShaken']){
  const fn=node(p,'function',n=>n.fields.name.value===eventName);
  assert.equal(fn.event,true,eventName+' should be an event');
 }
 for(const ident of ['accelerationX','accelerationY','rotationX']){
  const idNode=node(p,'identifier',n=>n.fields.name.value===ident&&!n.declaration);
  assert.equal(idNode.declaration,false);
  assert.equal(idNode.bindingScopeId,null);
  assert.ok(catalogue.P5_NAMES.includes(ident),ident+' should be in P5_NAMES');
 }
});

test('Phase 2 array operations project member and call blocks with safe editing',()=>{
 const source='const list = [10, 20]; list.push(30); const count = list.length; const item = list[0];';
 const p=parse(source);
 const pushCall=node(p,'call',n=>n.calleeName==='list.push');
 assert.ok(pushCall);
 assert.equal(pushCall.inputs.args.length,1);
 const lenMember=node(p,'member',n=>!n.computed&&n.raw==='list.length');
 assert.ok(lenMember);
 assert.equal(lenMember.computed,false);
 const idxMember=node(p,'member',n=>n.computed&&n.raw==='list[0]');
 assert.ok(idxMember);
 assert.equal(idxMember.computed,true);
});

test('Phase 4 friendly error translation formats common JavaScript and p5 runtime errors',()=>{
 const fe=catalogue.friendlyErrorMessage;
 assert.equal(fe('ReferenceError: missing is not defined'),'変数または関数「missing」が見つかりません。名前のつづりや、定義されているか確認してください。');
 assert.equal(fe('ReferenceError: missing is not defined','en'),'Variable or function "missing" is not defined. Check spelling or declaration.');
 assert.equal(fe('ReferenceError: missing is not defined','zh'),'未定义的变量或函数 "missing"。请检查拼写或是否已声明。');
 assert.equal(fe('ReferenceError: $customVar is not defined'),'変数または関数「$customVar」が見つかりません。名前のつづりや、定義されているか確認してください。');

 assert.equal(fe("ReferenceError: Cannot access 'x' before initialization"),'「x」を使う前に定義（宣言）する必要があります。');
 assert.equal(fe('TypeError: foo is not a function'),'「foo」は関数ではないため呼び出せません。名前のスペルや関数の定義を確認してください。');
 assert.equal(fe('TypeError: (intermediate value)(...) is not a function'),'「値」は関数ではないため呼び出せません。名前のスペルや関数の定義を確認してください。');
 assert.equal(fe("TypeError: Cannot read properties of undefined (reading 'x')"),'値が空（undefined）のため、「x」を読み取れません。変数の初期値を確認してください。');
 assert.equal(fe("TypeError: Cannot read property 'x' of undefined"),'値が空（undefined）のため、「x」を読み取れません。変数の初期値を確認してください。');
 assert.equal(fe("TypeError: Cannot read properties of null (reading 'width')"),'値が空（null）のため、「width」を読み取れません。');
 assert.equal(fe("TypeError: Cannot read property 'width' of null"),'値が空（null）のため、「width」を読み取れません。');
 assert.equal(fe("TypeError: Cannot set properties of undefined (setting 'val')"),'対象が空（undefined）のため、「val」を設定できません。');
 assert.equal(fe("TypeError: Cannot set property 'val' of undefined"),'対象が空（undefined）のため、「val」を設定できません。');
 assert.equal(fe("TypeError: Cannot set property 'val' of null"),'対象が空（null）のため、「val」を設定できません。');
 assert.equal(fe("TypeError: Cannot set property 'val' of null",'en'),'Cannot set property "val" on null.');
 assert.equal(fe("TypeError: Cannot set property 'val' of null",'zh'),'无法在 null 上设置属性 "val"。');
 assert.equal(fe('TypeError: Assignment to constant variable'),'const で定義された定数は変更できません。値を変更するには let で定義してください。');
 assert.equal(fe('RangeError: Maximum call stack size exceeded'),'関数が自分自身を呼び出し続けています（無限ループ）。終了条件を確認してください。');
 assert.equal(fe('p5.js says: circle() was expecting at least 3 arguments, but received only 2'),'関数「circle」には引数が少なくとも3個必要です（渡された引数: 2個）。');
 assert.equal(fe('circle() was expecting at least 3 arguments, but received only 2','en'),'Function "circle" expects at least 3 arguments, but received only 2.');
 assert.equal(fe('SyntaxError: Unexpected token'),'文法エラー: Unexpected token');
});

test('parameters, brush, and physics templates and example project cleanly without parse errors', () => {
 for (const cat of ['parameters', 'brush', 'physics']) {
  assert.ok(catalogue.CATEGORY_LABELS[cat], `missing category label: ${cat}`);
  const items = catalogue.TEMPLATES.filter(t => t.category === cat);
  assert.ok(items.length >= 5, `expected templates in ${cat}`);
  for (const item of items) {
   const projected = core.parse(item.source);
   assert.equal(projected.ok, true, `template ${item.id} failed to project`);
  }
 }
 const paramExample = catalogue.EXAMPLES.find(e => e.id === 'parameters');
 assert.ok(paramExample);
 assert.equal(core.parse(paramExample.source).ok, true);
});
