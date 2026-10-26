/* Edit:RiN source-preserving block projection. Never evaluates user JavaScript. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./vendor/acorn.js'), require('./catalogue.js'));
  else root.EditRinBlocks = root.RinBlocksCore = factory(root.acorn, root.EditRinBlockCatalogue);
}(typeof self !== 'undefined' ? self : globalThis, function (acorn, catalogue) {
  'use strict';
  const SCHEMA_VERSION = 1;
  const P5_NAMES = new Set(catalogue.P5_NAMES);
  const EVENT_NAMES = new Set(catalogue.EVENT_NAMES);
  const BINARY = ['+','-','*','/','%','**','==','!=','===','!==','<','<=','>','>=','<<','>>','>>>','|','^','&','in','instanceof'];
  const LOGICAL = ['&&','||','??'];
  const own = (o,k) => Object.prototype.hasOwnProperty.call(o,k);
  const hash = text => { let h=2166136261; for(let i=0;i<text.length;i++){h^=text.charCodeAt(i);h=Math.imul(h,16777619);}return(h>>>0).toString(16).padStart(8,'0'); };
  const descriptor = (value,type,options) => Object.assign({value,type:type||'text'},options?{options}:{});
  const unwrapParens=node=>node?.type==='ParenthesizedExpression'?unwrapParens(node.expression):node;
  function wrappedColorLiteral(expression){
    const call=unwrapParens(expression),fn=unwrapParens(call?.callee);
    if(call?.type!=='CallExpression'||call.arguments.length!==2||fn?.type!=='ArrowFunctionExpression'||fn.params.length!==2||!fn.params.every(p=>p.type==='Identifier'))return null;
    const body=unwrapParens(fn.body),[colorName,alphaName]=fn.params.map(p=>p.name);
    if(body?.type!=='SequenceExpression'||body.expressions.length!==2)return null;
    const [set,result]=body.expressions,method=set?.callee;
    if(set?.type!=='CallExpression'||method?.type!=='MemberExpression'||method.computed||method.object.name!==colorName||method.property.name!=='setAlpha'||set.arguments[0]?.name!==alphaName||result.type!=='Identifier'||result.name!==colorName)return null;
    const color=unwrapParens(call.arguments[0]),literal=color?.arguments?.[0];
    return color?.type==='CallExpression'&&color.callee.type==='Identifier'&&color.callee.name==='color'&&color.arguments.length===1&&literal?.type==='Literal'&&/^#[0-9a-f]{6}$/i.test(literal.value)?literal:null;
  }
  function walk(node, callback) {
    if (!node || typeof node.type !== 'string') return;
    callback(node);
    for(const key of Object.keys(node)) {
      if(key==='loc'||key==='range')continue;
      const value=node[key];
      if(Array.isArray(value))value.forEach(v=>walk(v,callback));
      else if(value&&typeof value.type==='string')walk(value,callback);
    }
  }
  function boundNames(pattern, names) {
    if(!pattern)return;
    if(pattern.type==='Identifier')names.add(pattern.name);
    else if(pattern.type==='RestElement')boundNames(pattern.argument,names);
    else if(pattern.type==='AssignmentPattern')boundNames(pattern.left,names);
    else if(pattern.type==='ArrayPattern')pattern.elements.forEach(x=>boundNames(x,names));
    else if(pattern.type==='ObjectPattern')pattern.properties.forEach(x=>boundNames(x.value||x.argument,names));
  }
  function analyzeScopes(ast) {
    const scopes=[], nodeScope=new WeakMap();
    function scope(parent,kind){const s={id:'scope'+scopes.length,parent,kind,names:new Set()};scopes.push(s);return s;}
    const global=scope(null,'program');
    function visit(n,s){
      if(!n||typeof n.type!=='string')return;
      if(n.type==='FunctionDeclaration'&&n.id)s.names.add(n.id.name);
      if(n.type==='ClassDeclaration'&&n.id)s.names.add(n.id.name);
      if(n.type==='ImportDeclaration')n.specifiers.forEach(x=>s.names.add(x.local.name));
      if(n.type==='FunctionDeclaration'||n.type==='FunctionExpression'||n.type==='ArrowFunctionExpression'){
        const inner=scope(s,'function');if(n.id)inner.names.add(n.id.name);n.params.forEach(p=>boundNames(p,inner.names));
        nodeScope.set(n,s);n.params.forEach(p=>visit(p,inner));visit(n.body,inner);return;
      }
      if(n.type==='BlockStatement'||n.type==='ForStatement'||n.type==='ForOfStatement'||n.type==='ForInStatement'||n.type==='CatchClause')s=scope(s,n.type==='CatchClause'?'catch':'block');
      nodeScope.set(n,s);
      if(n.type==='VariableDeclaration'){
        let target=s;if(n.kind==='var')while(target.parent&&target.kind!=='function'&&target.kind!=='program')target=target.parent;
        n.declarations.forEach(d=>boundNames(d.id,target.names));
      }
      if(n.type==='CatchClause')boundNames(n.param,s.names);
      for(const key of Object.keys(n)){
        if(key==='loc'||key==='range')continue;const v=n[key];
        if(Array.isArray(v))v.forEach(x=>{if(x&&x.type)visit(x,s);});else if(v&&v.type)visit(v,s);
      }
    }
    visit(ast,global);
    return {scopes,nodeScope,resolve(n,name){let s=nodeScope.get(n)||global;while(s){if(s.names.has(name))return s;s=s.parent;}return null;}};
  }
  // Binding information is used only to propose text edits, never to run a work.
  // It also visits syntax shown as custom code, so a rename cannot miss a use in
  // a template literal, an object method, or another unsupported visual block.
  function analyzeBindings(ast) {
    const scopes=[],bindings=[],identifiers=[],parents=new WeakMap(),nodeScope=new WeakMap(),roles=new WeakMap(),functionScopes=new WeakMap();
    let dynamic=false;const ambiguousNames=new Set();
    const makeScope=(parent,kind,owner)=>{const s={parent,kind,owner,names:new Map()};scopes.push(s);return s;};
    const global=makeScope(null,'program',ast);
    function define(id,s,kind,exported){
      let binding=s.names.get(id.name);
      if(!binding&&kind==='var'&&s.kind==='function-body'&&s.parent.names.has(id.name))binding=s.parent.names.get(id.name);
      if(!binding){binding={name:id.name,scope:s,kind,declarations:[],references:[],exported:!!exported,unsupported:false};bindings.push(binding);}
      binding.exported=binding.exported||!!exported;s.names.set(id.name,binding);binding.declarations.push(id);roles.set(id,{declaration:true,binding});nodeScope.set(id,s);identifiers.push(id);return binding;
    }
    function child(n,key,s,exported){const v=n[key];if(Array.isArray(v))v.forEach(x=>visit(x,s,n,key,exported));else visit(v,s,n,key,exported);}
    function pattern(n,s,kind,parent,key,exported,evaluationScope){
      evaluationScope=evaluationScope||s;
      if(!n)return;parents.set(n,{node:parent,key});nodeScope.set(n,s);
      if(n.type==='Identifier'){define(n,s,kind,exported);return;}
      if(n.type==='RestElement'){pattern(n.argument,s,kind,n,'argument',exported,evaluationScope);return;}
      if(n.type==='AssignmentPattern'){pattern(n.left,s,kind,n,'left',exported,evaluationScope);visit(n.right,evaluationScope,n,'right');return;}
      if(n.type==='ArrayPattern'){n.elements.forEach(x=>pattern(x,s,kind,n,'elements',exported,evaluationScope));return;}
      if(n.type==='ObjectPattern')n.properties.forEach(p=>{
        parents.set(p,{node:n,key:'properties'});nodeScope.set(p,s);
        if(p.type==='RestElement')pattern(p.argument,s,kind,p,'argument',exported,evaluationScope);
        else{if(p.computed)visit(p.key,evaluationScope,p,'key');pattern(p.value,s,kind,p,'value',exported,evaluationScope);}
      });
    }
    function variableScope(s){while(s.parent&&s.kind!=='function-body'&&s.kind!=='program'&&s.kind!=='static-block')s=s.parent;return s;}
    function visit(n,s,parent,key,exported){
      if(!n||typeof n.type!=='string')return;if(parent)parents.set(n,{node:parent,key});nodeScope.set(n,s);
      if(n.type==='Identifier'){identifiers.push(n);return;}
      if(n.type==='WithStatement')dynamic=true;
      if(n.type==='CallExpression'){let callee=n.callee;while(callee.type==='ParenthesizedExpression')callee=callee.expression;if(callee.type==='Identifier'&&callee.name==='eval')dynamic=true;}
      if(n.type==='ExportNamedDeclaration'||n.type==='ExportDefaultDeclaration'){
        if(n.declaration)visit(n.declaration,s,n,'declaration',true);
        for(const x of n.specifiers||[])visit(x.local,s,x,'local',true);
        return;
      }
      if(n.type==='ImportDeclaration'){
        for(const spec of n.specifiers){parents.set(spec,{node:n,key:'specifiers'});define(spec.local,s,'import',false);parents.set(spec.local,{node:spec,key:'local'});}return;
      }
      if(n.type==='FunctionDeclaration'||n.type==='FunctionExpression'||n.type==='ArrowFunctionExpression'){
        if(n.type==='FunctionDeclaration'&&n.id){const binding=define(n.id,s,'function',exported);parents.set(n.id,{node:n,key:'id'});binding.functionNode=n;if(s.kind!=='program'&&s.kind!=='function-body'){binding.unsupported=true;ambiguousNames.add(n.id.name);}}
        let outer=s;
        if(n.type==='FunctionExpression'&&n.id){outer=makeScope(s,'function-name',n);define(n.id,outer,'function-name',false);parents.set(n.id,{node:n,key:'id'});}
        const params=makeScope(outer,'parameters',n),body=makeScope(params,'function-body',n);functionScopes.set(n,{params,body});
        n.params.forEach(p=>pattern(p,params,'parameter',n,'params'));
        if(n.type!=='ArrowFunctionExpression'&&!params.names.has('arguments')){const implicit={name:'arguments',scope:params,kind:'implicit',declarations:[],references:[],exported:false,unsupported:true};params.names.set('arguments',implicit);bindings.push(implicit);}
        if(n.body.type==='BlockStatement'){parents.set(n.body,{node:n,key:'body'});nodeScope.set(n.body,body);n.body.body.forEach(x=>visit(x,body,n.body,'body'));}
        else visit(n.body,body,n,'body');return;
      }
      if(n.type==='VariableDeclaration'){
        const target=n.kind==='var'?variableScope(s):s;
        for(const d of n.declarations){parents.set(d,{node:n,key:'declarations'});nodeScope.set(d,s);pattern(d.id,target,n.kind,d,'id',exported,s);visit(d.init,s,d,'init');
          if(n.kind==='var'){const names=new Set();boundNames(d.id,names);for(let inner=s;inner&&inner!==target;inner=inner.parent)if(inner.kind==='catch')for(const name of names)if(inner.names.has(name)){inner.names.get(name).unsupported=true;target.names.get(name).unsupported=true;}}
        }return;
      }
      if(n.type==='ClassDeclaration'||n.type==='ClassExpression'){
        let inside=s;
        if(n.id){if(n.type==='ClassExpression')inside=makeScope(s,'class-name',n);define(n.id,inside,'class',exported);parents.set(n.id,{node:n,key:'id'});}
        visit(n.superClass,inside,n,'superClass');visit(n.body,inside,n,'body');return;
      }
      if(n.type==='CatchClause'){
        const inner=makeScope(s,'catch',n);pattern(n.param,inner,'catch',n,'param');visit(n.body,inner,n,'body');return;
      }
      if(n.type==='BlockStatement'||n.type==='ForStatement'||n.type==='ForInStatement'||n.type==='ForOfStatement'||n.type==='SwitchStatement'||n.type==='StaticBlock'){
        if(n.type==='SwitchStatement'){visit(n.discriminant,s,n,'discriminant');s=makeScope(s,'block',n);nodeScope.set(n,s);child(n,'cases',s);return;}
        s=makeScope(s,n.type==='StaticBlock'?'static-block':'block',n);nodeScope.set(n,s);
      }
      if(n.type==='MemberExpression'){
        visit(n.object,s,n,'object');if(n.computed)visit(n.property,s,n,'property');return;
      }
      if(n.type==='Property'||n.type==='MethodDefinition'||n.type==='PropertyDefinition'){
        if(n.computed)visit(n.key,s,n,'key');visit(n.value,s,n,'value');return;
      }
      if(n.type==='LabeledStatement'){visit(n.body,s,n,'body');return;}
      if(n.type==='BreakStatement'||n.type==='ContinueStatement'||n.type==='MetaProperty')return;
      for(const k of Object.keys(n)){if(k==='loc'||k==='range')continue;const v=n[k];if(Array.isArray(v))v.forEach(x=>visit(x,s,n,k,exported));else if(v&&v.type)visit(v,s,n,k,exported);}
    }
    function resolve(id,name,rename){let s=nodeScope.get(id)||global;while(s){if(rename&&s===rename.binding.scope&&name===rename.name)return rename.binding;const b=s.names.get(name);if(b&&(!rename||b!==rename.binding||name===rename.name))return b;s=s.parent;}return null;}
    visit(ast,global);
    for(const id of identifiers){if(roles.has(id))continue;const binding=resolve(id,id.name);roles.set(id,{declaration:false,binding});if(binding)binding.references.push(id);}
    // An export specifier can appear before its declaration.
    walk(ast,n=>{if(n.type==='ExportSpecifier'){const b=resolve(n.local,n.local.name);if(b)b.exported=true;}});
    bindings.forEach(binding=>{if(ambiguousNames.has(binding.name))binding.unsupported=true;});
    return {ast,scopes,bindings,identifiers,parents,nodeScope,roles,functionScopes,resolve,dynamic};
  }
  function semanticFailure(code,message){const error=new Error(message);error.code=code;throw error;}
  function semanticParse(projection){const tokens=[];const ast=acorn.parse(projection.source,{ecmaVersion:'latest',sourceType:projection.sourceType,preserveParens:true,allowHashBang:true,onToken:tokens});return Object.assign(analyzeBindings(ast),{tokens});}
  function parse(source, options) {
    source=String(source==null?'':source);options=options||{};
    const comments=[],tokens=[],base={ok:false,schemaVersion:SCHEMA_VERSION,source,revision:hash(source),sourceType:options.sourceType||'script',p5Version:options.p5Version||'2',nodes:[],rootIds:[],comments:[],scopes:[],diagnostics:[],metadata:null};
    let ast;
    try {
      if(!acorn)throw new Error('Acorn is unavailable');
      ast=acorn.parse(source,{ecmaVersion:'latest',sourceType:base.sourceType,locations:true,ranges:true,preserveParens:true,allowHashBang:true,onComment:comments,onToken:tokens});
    } catch(e) {base.diagnostics.push({severity:'error',message:e.message,start:e.pos==null?0:e.pos,end:e.pos==null?0:e.pos,line:e.loc?e.loc.line:1,column:e.loc?e.loc.column:0});return base;}
    const scopeInfo=analyzeScopes(ast),bindingInfo=analyzeBindings(ast), nodes=[],byAst=new WeakMap(), byId=new Map(),lists=[],usedIds=new Set();
    const old=(options.metadata&&Array.isArray(options.metadata.nodes))?options.metadata.nodes:[];
    const oldBySignature=new Map(), oldByPath=new Map();
    old.forEach(x=>{const key=x.kind+':'+x.rawHash;if(!oldBySignature.has(key))oldBySignature.set(key,[]);oldBySignature.get(key).push(x);oldByPath.set(x.path,x);});
    const pending=[];
    function field(n,key,value,type,start,end,opts){n.fields[key]=descriptor(value,type,opts);if(start!=null)n.fieldRanges[key]={start,end};}
    function child(n,key,a,path,statement){n.inputs[key]=a?convert(a,path+'.'+key,statement):null;n.inputKinds[key]=statement?'statement':'value';}
    function statementList(n,key,items,start,end,path){
      const all=items.slice();
      for(const c of comments)if(c.start>=start&&c.end<=end&&!items.some(a=>c.start>=a.start&&c.end<=a.end))all.push(Object.assign({},c,{type:'RinComment'}));
      all.sort((a,b)=>a.start-b.start);
      n.inputs[key]=all.map((a,i)=>convert(a,path+'.'+key+'['+i+']',true));n.inputKinds[key]='statement';
      n.listRanges[key]={start,end};lists.push({parent:n,key,items:all});
    }
    function patternOrRaw(a,path){return convert(a,path,false);}
    function convert(a,path,statement){
      if(!a)return null;
      const n={id:null,kind:'raw_'+(statement?'statement':'expression'),type:a.type,start:a.start,end:a.end,raw:source.slice(a.start,a.end),fields:{},inputs:{},inputKinds:{},fieldRanges:{},listRanges:{},inputRanges:{},statement:!!statement,output:!statement,scopeId:(scopeInfo.nodeScope.get(a)||scopeInfo.scopes[0]).id,path,loc:a.loc||null};
      nodes.push(n);pending.push(n);byAst.set(a,n);
      switch(a.type){
        case 'RinComment': n.kind='comment';field(n,'text',a.value,'text',a.start+(a.type==='Line'?2:2),a.end-(source.slice(a.start,a.start+2)==='/*'?2:0));n.commentStyle=source.slice(a.start,a.start+2)==='/*'?'block':'line';break;
        case 'FunctionDeclaration': {
          n.kind='function';n.event=!!a.id&&EVENT_NAMES.has(a.id.name)&&scopeInfo.nodeScope.get(a)?.kind==='program';n.async=!!a.async;
          n.parameters=a.params.map(p=>({name:p.type==='Identifier'?p.name:null,start:p.start,end:p.end}));
          n.parametersEditable=!n.event&&a.params.every(p=>p.type==='Identifier');
          const binding=a.id&&bindingInfo.roles.get(a.id)?.binding;
          n.renameable=!!binding&&!binding.exported&&!binding.unsupported&&!bindingInfo.dynamic;
          if(a.id)field(n,'name',a.id.name,'text',a.id.start,a.id.end);
          const firstOpen=tokens.find(t=>t.start>=a.start&&t.end<=a.body.start&&t.type.label==='(');
          const lastClose=tokens.filter(t=>t.start>=a.start&&t.end<=a.body.start&&t.type.label===')').pop();
          if(firstOpen&&lastClose)field(n,'params',source.slice(firstOpen.end,lastClose.start),'text',firstOpen.end,lastClose.start);
          field(n,'async',!!a.async,'dropdown',null,null,['false','true']);
          statementList(n,'body',a.body.body,a.body.start+1,a.body.end-1,path);break;
        }
        case 'BlockStatement':n.kind='block';statementList(n,'body',a.body,a.start+1,a.end-1,path);break;
        case 'ExpressionStatement':n.kind='expression_statement';child(n,'expression',a.expression,path,false);break;
        case 'VariableDeclaration':n.kind='variable_declaration';field(n,'kind',a.kind,'dropdown',a.start,a.start+a.kind.length,['let','const','var']);n.inputs.declarations=a.declarations.map((d,i)=>convert(d,path+'.declarations['+i+']',false));n.inputKinds.declarations='value';break;
        case 'VariableDeclarator':n.kind='variable_declarator';child(n,'name',a.id,path,false);child(n,'init',a.init,path,false);break;
        case 'Identifier':{
          n.kind='identifier';field(n,'name',a.name,'text',a.start,a.end);n.bindingScopeId=(scopeInfo.resolve(a,a.name)||{}).id||null;
          const role=bindingInfo.roles.get(a),binding=role?.binding;n.declaration=!!role?.declaration;n.renameable=!!binding&&binding.kind!=='import'&&!binding.exported&&!binding.unsupported&&!bindingInfo.dynamic;break;
        }
        case 'Literal':{
          n.kind='literal';n.valueType=a.regex?'regexp':a.bigint?'bigint':a.value===null?'null':typeof a.value;
          if(a.regex)field(n,'value',n.raw,'text',a.start,a.end);
          else if(a.bigint)field(n,'value',a.bigint,'text',a.start,a.end);
          else field(n,'value',a.value,n.valueType==='number'?'number':n.valueType==='boolean'?'dropdown':'text',a.start,a.end,n.valueType==='boolean'?['false','true']:null);
          n.quote=(n.raw[0]==="'"||n.raw[0]==='"')?n.raw[0]:null;break;
        }
        case 'CallExpression':case 'NewExpression':{
          if(a.optional){field(n,'code',n.raw,'code',a.start,a.end);break;}
          const direct=a.callee.type==='Identifier',name=direct?a.callee.name:source.slice(a.callee.start,a.callee.end);
          const p5=direct&&P5_NAMES.has(name)&&!scopeInfo.resolve(a,name);
          n.kind=a.type==='NewExpression'?'new':p5?'p5_call':'call';n.calleeName=name;n.p5=!!p5;
          if(p5&&a.arguments.length===1&&['fill','stroke','background','color','ambientMaterial','specularMaterial'].includes(name)){
            const literal=wrappedColorLiteral(a.arguments[0]);if(literal)n.colorSwatch=literal.value;
          }
          child(n,'callee',a.callee,path,false);n.inputs.args=a.arguments.map((v,i)=>convert(v,path+'.args['+i+']',false));n.inputKinds.args='value';const ao=tokens.find(t=>t.start>=a.callee.end&&t.end<=a.end&&t.type.label==='(');if(ao)n.listRanges.args={start:ao.end,end:a.end-1,separator:', '};
          n.requirements={sound:/^(loadSound|soundFormats|userStartAudio|getAudioContext)$/.test(name)||/^p5\.(Oscillator|SoundFile|Envelope|FFT|Amplitude)$/.test(name),webgl:/^(box|sphere|cylinder|cone|torus|plane|ellipsoid|ambientLight|directionalLight|pointLight|spotLight|lights|noLights|ambientMaterial|specularMaterial|normalMaterial|emissiveMaterial|shininess|texture|textureMode|orbitControl|camera|perspective|ortho|rotateX|rotateY|rotateZ)$/.test(name)};break;
        }
        case 'BinaryExpression':case 'LogicalExpression':{
          n.kind=a.type==='LogicalExpression'?'logical':'binary';child(n,'left',a.left,path,false);child(n,'right',a.right,path,false);
          const t=tokens.find(t=>t.start>=a.left.end&&t.end<=a.right.start&&t.value===a.operator);
          field(n,'operator',a.operator,'dropdown',t?t.start:null,t?t.end:null,a.type==='LogicalExpression'?LOGICAL:BINARY);break;
        }
        case 'AssignmentExpression':{
          n.kind='assignment';child(n,'left',a.left,path,false);child(n,'right',a.right,path,false);
          const t=tokens.find(t=>t.start>=a.left.end&&t.end<=a.right.start&&t.value===a.operator);field(n,'operator',a.operator,'dropdown',t?t.start:null,t?t.end:null,['=','+=','-=','*=','/=','%=','**=','&&=','||=','??=','&=','|=','^=','<<=','>>=','>>>=']);break;
        }
        case 'UnaryExpression':case 'UpdateExpression':{
          n.kind=a.type==='UnaryExpression'?'unary':'update';child(n,'argument',a.argument,path,false);n.prefix=!!a.prefix;
          const range=a.prefix?{start:a.start,end:a.argument.start}:{start:a.argument.end,end:a.end};
          const t=tokens.find(t=>t.start>=range.start&&t.end<=range.end&&t.value===a.operator);
          field(n,'operator',a.operator,'dropdown',t?t.start:null,t?t.end:null,a.type==='UpdateExpression'?['++','--']:['!','-','+','~','typeof','void','delete']);break;
        }
        case 'ArrayExpression':n.kind='array';n.inputs.elements=a.elements.map((v,i)=>v?convert(v,path+'.elements['+i+']',false):null);n.inputKinds.elements='value';n.listRanges.elements={start:a.start+1,end:a.end-1,separator:', '};break;
        case 'MemberExpression':n.kind='member';n.computed=!!a.computed;n.optional=!!a.optional;child(n,'object',a.object,path,false);child(n,'property',a.property,path,false);break;
        case 'IfStatement':n.kind='if';child(n,'test',a.test,path,false);child(n,'consequent',a.consequent,path,true);child(n,'alternate',a.alternate,path,true);break;
        case 'ForStatement':{
          n.kind='for';child(n,'init',a.init,path,false);child(n,'test',a.test,path,false);child(n,'update',a.update,path,false);child(n,'body',a.body,path,true);
          const content=[a.init,a.test,a.update].filter(Boolean);
          const semis=tokens.filter(t=>t.start>=a.start&&t.end<=a.body.start&&t.type.label===';'&&!content.some(c=>t.start>=c.start&&t.end<=c.end));
          const opening=tokens.find(t=>t.start>=a.start&&t.end<=a.body.start&&t.type.label==='(');
          if(opening&&semis.length===2){n.inputRanges.init={start:opening.end,end:semis[0].start};n.inputRanges.test={start:semis[0].end,end:semis[1].start};n.inputRanges.update={start:semis[1].end,end:a.body.start-1};const closing=tokens.filter(t=>t.start>=semis[1].end&&t.end<=a.body.start&&t.type.label===')').pop();if(closing)n.inputRanges.update.end=closing.start;}
          break;
        }
        case 'ForOfStatement':n.kind='for_of';n.await=!!a.await;child(n,'left',a.left,path,false);child(n,'right',a.right,path,false);child(n,'body',a.body,path,true);break;
        case 'ReturnStatement':n.kind='return';child(n,'argument',a.argument,path,false);break;
        case 'BreakStatement':case 'ContinueStatement':n.kind=a.type==='BreakStatement'?'break':'continue';if(a.label)child(n,'label',a.label,path,false);break;
        case 'AwaitExpression':n.kind='await';child(n,'argument',a.argument,path,false);break;
        case 'ParenthesizedExpression':n.kind='parenthesized';child(n,'expression',a.expression,path,false);break;
        case 'ConditionalExpression':n.kind='conditional';child(n,'test',a.test,path,false);child(n,'consequent',a.consequent,path,false);child(n,'alternate',a.alternate,path,false);break;
        case 'EmptyStatement':n.kind='empty';break;
        default:field(n,'code',n.raw,'code',a.start,a.end);
      }
      n.signature=n.kind+':'+hash(n.raw);n.anchor=n.kind+':'+(n.fields.name?n.fields.name.value:n.calleeName||'');
      return n;
    }
    const virtual={inputs:{},inputKinds:{},listRanges:{}};
    statementList(virtual,'root',ast.body,0,source.length,'program');
    // Reserve uniquely unchanged nodes before positional fallback, avoiding ID theft on insertion.
    const currentCounts=new Map();pending.forEach(n=>currentCounts.set(n.signature,(currentCounts.get(n.signature)||0)+1));
    pending.forEach(n=>{const candidates=oldBySignature.get(n.signature);if(currentCounts.get(n.signature)===1&&candidates&&candidates.length===1){n.id=candidates[0].id;usedIds.add(n.id);}});
    pending.forEach(n=>{
      if(!n.id){const prior=oldByPath.get(n.path);if(prior&&prior.kind===n.kind&&prior.anchor===n.anchor&&!usedIds.has(prior.id))n.id=prior.id;}
      if(!n.id){n.id='rin_'+hash(n.path+':'+n.kind+':'+n.raw);let suffix=1,initial=n.id;while(usedIds.has(n.id))n.id=initial+'_'+suffix++;}
      usedIds.add(n.id);byId.set(n.id,n);
    });
    // Convert reference objects to IDs only after stable IDs have been assigned.
    function refs(holder){Object.keys(holder.inputs).forEach(key=>{const v=holder.inputs[key];holder.inputs[key]=Array.isArray(v)?v.map(n=>n?n.id:null):v?v.id:null;});}
    nodes.forEach(refs);refs(virtual);
    for(const n of nodes){for(const key of Object.keys(n.inputs)){const v=n.inputs[key];for(const id of Array.isArray(v)?v:[v])if(id){const c=byId.get(id);c.parentId=n.id;c.parentInput=key;}}}
    // Parameter annotations belong to their following declaration, even when a statement moves.
    for(const list of [{ids:virtual.inputs.root},...nodes.flatMap(n=>Object.keys(n.inputs).filter(k=>Array.isArray(n.inputs[k])&&n.inputKinds[k]==='statement').map(k=>({ids:n.inputs[k]})))]){
      for(let i=0;i<list.ids.length;i++){
        const n=byId.get(list.ids[i]);if(!n||n.kind==='comment')continue;
        let j=i-1,start=n.start;const attached=[];
        while(j>=0){const prior=byId.get(list.ids[j]);if(!prior||prior.kind!=='comment'||!/^[\t \r\n]*$/.test(source.slice(prior.end,start)))break;
          if(/@rin\b/.test(prior.raw)){start=prior.start;attached.unshift(prior.id);j--;}else break;
        }
        if(attached.length){n.ownedStart=start;n.attachedCommentIds=attached;}
      }
    }
    const metadata={schemaVersion:SCHEMA_VERSION,sourceHash:hash(source),nodes:nodes.map(n=>({id:n.id,kind:n.kind,path:n.path,anchor:n.anchor,rawHash:hash(n.raw),start:n.start,end:n.end}))};
    const scopes=scopeInfo.scopes.map(s=>({id:s.id,parentId:s.parent?s.parent.id:null,kind:s.kind,names:Array.from(s.names)}));
    nodes.forEach(n=>{const visible=[];let s=scopes.find(s=>s.id===n.scopeId);while(s){for(const name of s.names)if(!visible.includes(name))visible.push(name);s=scopes.find(t=>t.id===s.parentId);}n.visibleNames=visible;});
    walk(ast,a=>{const n=byAst.get(a);if(!n||!['call','p5_call','new'].includes(n.kind))return;n.functionChoices=bindingInfo.bindings.filter(b=>b.functionNode&&b.declarations.length===1&&!b.unsupported&&bindingInfo.resolve(a,b.name)===b).map(b=>{const fn=byAst.get(b.functionNode);return fn&&fn.parametersEditable?{id:fn.id,name:b.name,parameters:fn.parameters.map(p=>p.name)}:null;}).filter(Boolean);});
    return Object.assign(base,{ok:true,nodes,rootIds:virtual.inputs.root,rootRange:{start:0,end:source.length},comments:comments.map(c=>({start:c.start,end:c.end,text:c.value,kind:c.type})),scopes,metadata});
  }
  function getNode(projection,id) {
    const n=projection.nodes.find(n=>n.id===id);if(!n)throw new Error('Unknown block: '+id);return n;
  }
  const splitSurrogate=(s,i)=>i>0&&i<s.length&&s.charCodeAt(i-1)>=0xD800&&s.charCodeAt(i-1)<=0xDBFF&&s.charCodeAt(i)>=0xDC00&&s.charCodeAt(i)<=0xDFFF;
  function applyPatches(source,patches) {
    const sorted=patches.map(p=>({start:p.start,end:p.end,text:String(p.text==null?'':p.text)})).sort((a,b)=>a.start-b.start||a.end-b.end);
    let cursor=0,out='',previous=null;for(const p of sorted){if(previous&&previous.start===p.start&&previous.end===previous.start&&p.end===p.start)throw new Error('Coincident source insertions are ambiguous');if(splitSurrogate(source,p.start)||splitSurrogate(source,p.end))throw new Error('Source patches must preserve UTF-16 character boundaries');if(!Number.isInteger(p.start)||!Number.isInteger(p.end)||p.start<cursor||p.end<p.start||p.end>source.length)throw new Error('Overlapping or invalid source patches');out+=source.slice(cursor,p.start)+p.text;cursor=p.end;previous=p;}return out+source.slice(cursor);
  }
  function sourceDiff(before,after) {
    if(before===after)return [];
    let start=0,endBefore=before.length,endAfter=after.length;
    while(start<endBefore&&start<endAfter&&before[start]===after[start])start++;
    while(endBefore>start&&endAfter>start&&before[endBefore-1]===after[endAfter-1]){endBefore--;endAfter--;}
    if(splitSurrogate(before,start)||splitSurrogate(after,start))start--;
    if(splitSurrogate(before,endBefore)||splitSurrogate(after,endAfter)){endBefore++;endAfter++;}
    return [{start,end:endBefore,text:after.slice(start,endAfter)}];
  }
  function fieldText(node,key,value) {
    const f=node.fields[key];if(!f)throw new Error('Unknown field: '+key);
    if(value&&typeof value==='object'&&own(value,'value'))value=value.value;
    if(f.options&&!f.options.map(String).includes(String(value)))throw new Error('Invalid field option');
    if(node.kind==='literal'&&key==='value'){
      if(node.valueType==='string'){
        const encoded=JSON.stringify(String(value));
        return node.quote==="'"?"'"+encoded.slice(1,-1).replace(/\\"/g,'"').replace(/'/g,"\\'")+"'":encoded;
      }
      if(node.valueType==='number'){
        if(String(value).trim()===''||!Number.isFinite(Number(value)))throw new Error('A finite number is required');return String(Number(value));
      }
      if(node.valueType==='boolean'){if(value!==true&&value!==false&&value!=='true'&&value!=='false')throw new Error('A boolean is required');return String(value);}
      if(node.valueType==='null')return 'null';
      if(node.valueType==='bigint'){if(!/^[0-9]+$/.test(String(value)))throw new Error('Invalid bigint');return String(value)+'n';}
    }
    if(node.kind==='comment'&&key==='text'){
      if(node.commentStyle==='line'&&/[\r\n]/.test(String(value)))throw new Error('Line comments cannot contain a newline');
      if(node.commentStyle==='block'&&String(value).includes('*/'))throw new Error('Block comment cannot contain */');
    }
    if(key==='operator')return ' '+String(value)+' ';
    return String(value);
  }
  function inputSource(node,key,value,projection) {
    const ids=node.inputs[key];
    const asSource=v=>{if(typeof v==='string')return v;if(v&&typeof v.source==='string')return v.source;throw new Error('Input replacement must be source text');};
    if(Array.isArray(value)){
      const list=node.listRanges[key];
      if(!list)throw new Error('This input list does not support replacement');
      const values=value.map(asSource);
      if(Array.isArray(ids)&&ids.length===values.length&&ids.every(Boolean)){
        const changes=ids.map((id,i)=>{const child=getNode(projection,id);return {start:child.start-list.start,end:child.end-list.start,text:values[i]};});
        return {start:list.start,end:list.end,text:applyPatches(projection.source.slice(list.start,list.end),changes)};
      }
      if(Array.isArray(ids)&&ids.length===0)return {start:list.start,end:list.end,text:projection.source.slice(list.start,list.end)+values.join(list.separator||'\n')};
      // Keep trivia that belongs to old list gaps, including parameter annotations.
      const retainedComments=projection.comments.filter(c=>c.start>=list.start&&c.end<=list.end&&!ids.some(id=>{if(!id)return false;const child=getNode(projection,id);return c.start>=child.start&&c.end<=child.end;})).map(c=>projection.source.slice(c.start,c.end)+(c.kind==='Line'?'\n':' ')).join('');
      return {start:list.start,end:list.end,text:retainedComments+values.join(list.separator||'\n')};
    }
    const text=value==null?'':asSource(value);
    if(Array.isArray(ids))throw new Error('A list input requires an array');
    if(ids){
      const c=getNode(projection,ids);
      if(!text&&node.kind==='variable_declarator'&&key==='init'){
        const before=projection.source.slice(getNode(projection,node.inputs.name).end,c.start),equals=before.indexOf('=');
        return {start:getNode(projection,node.inputs.name).end+equals,end:c.end,text:before.slice(equals+1)};
      }
      if(!text&&node.kind==='if'&&key==='alternate'){
        const consequent=getNode(projection,node.inputs.consequent),tokens=[],ast=acorn.parse(projection.source,{ecmaVersion:'latest',sourceType:projection.sourceType,allowHashBang:true,onToken:tokens});const keyword=tokens.find(t=>t.type.label==='else'&&t.start>=consequent.end&&t.end<=c.start);
        if(!keyword)throw new Error('Cannot locate else clause');
        const parent=node.parentId&&getNode(projection,node.parentId);
        if(parent&&parent.inputKinds[node.parentInput]==='statement'&&!Array.isArray(parent.inputs[node.parentInput])){
          let hasFunction=false;walk(ast,a=>{if(a.type==='FunctionDeclaration'&&a.start>=node.start&&a.end<=node.end)hasFunction=true;});if(hasFunction)semanticFailure('ambiguous_branch','入れ子の条件内に関数の定義があります。この分岐はコード画面で変更してください。');
          const nl=projection.source.includes('\r\n')?'\r\n':'\n',indent=indentAt(projection.source,node.start),retained=projection.source.slice(node.start,keyword.start)+projection.source.slice(keyword.end,c.start);
          return {start:node.start,end:node.end,text:'{'+nl+indent+'  '+retained+nl+indent+'}'};
        }
        return {start:keyword.start,end:c.end,text:projection.source.slice(keyword.end,c.start)};
      }
      return {start:c.start,end:c.end,text:protectExpression(node,key,text)};
    }
    if(node.inputRanges&&node.inputRanges[key])return Object.assign({},node.inputRanges[key],{text});
    if(node.kind==='variable_declarator'&&key==='init')return {start:node.end,end:node.end,text:text?' = '+text:''};
    if(node.kind==='return'&&key==='argument')return {start:node.start+6,end:node.start+6,text:text?' '+text:''};
    if(node.kind==='if'&&key==='alternate'){
      if(text){
        const ast=acorn.parse(projection.source,{ecmaVersion:'latest',sourceType:projection.sourceType,preserveParens:true,allowHashBang:true});let branch;walk(ast,a=>{if(a.type==='IfStatement'&&a.start===node.start&&a.end===node.end)branch=a.consequent;});
        function openIf(a){if(!a)return false;if(a.type==='IfStatement')return !a.alternate||openIf(a.alternate);if(['WhileStatement','ForStatement','ForInStatement','ForOfStatement','LabeledStatement','WithStatement'].includes(a.type))return openIf(a.body);return false;}
        if(openIf(branch)){let hasFunction=false;walk(branch,a=>{if(a.type==='FunctionDeclaration')hasFunction=true;});if(hasFunction)semanticFailure('ambiguous_branch','入れ子の条件内に関数の定義があります。この分岐はコード画面で変更してください。');const child=getNode(projection,node.inputs.consequent),nl=projection.source.includes('\r\n')?'\r\n':'\n',indent=indentAt(projection.source,node.start);return {start:child.start,end:node.end,text:'{'+nl+indent+'  '+child.raw+nl+indent+'} else '+text};}
      }
      return {start:node.end,end:node.end,text:text?' else '+text:''};
    }
    throw new Error('Cannot add this missing input; replace the containing block');
  }
  function protectExpression(parent,key,text){
    if(!text||parent.inputKinds[key]==='statement')return text;
    if((parent.kind==='variable_declarator'&&key==='name')||(parent.kind==='member'&&!parent.computed&&key==='property')||key==='label')return text;
    if((parent.kind==='call'||parent.kind==='p5_call'||parent.kind==='new')&&key==='callee'&&/^[A-Za-z_$][\w$]*$/.test(text))return text;
    if(parent.kind==='for'&&key==='init')return text;
    if(parent.kind==='for_of'&&key==='left')return text;
    return '('+text+')';
  }
  function renderNode(projection,nodeId,overrides) {
    try{
      const node=getNode(projection,nodeId),changes=[],o=overrides||{};
      for(const key of Object.keys(o.fields||{})){
        const value=o.fields[key],range=node.fieldRanges[key];
        if(key==='async'&&node.kind==='function'){
          const enabled=value===true||value==='true'||!!(value&&value.value===true);
          if(enabled&&!node.async)changes.push({start:node.start,end:node.start,text:'async '});
          else if(!enabled&&node.async)changes.push({start:node.start,end:node.start+5,text:''});
        }else if(range)changes.push({start:range.start,end:range.end,text:fieldText(node,key,value)});
        else throw new Error('Field has no editable source range: '+key);
      }
      for(const key of Object.keys(o.inputs||{})){
        if(!own(node.inputs,key))throw new Error('Unknown input: '+key);
        changes.push(inputSource(node,key,o.inputs[key],projection));
      }
      if(o.fields&&own(o.fields,'operator')&&(node.kind==='binary'||node.kind==='logical')){
        for(const key of ['left','right']){
          if(o.inputs&&own(o.inputs,key))continue;
          const c=getNode(projection,node.inputs[key]);changes.push({start:c.start,end:c.start,text:'('},{start:c.end,end:c.end,text:')'});
        }
      }
      const local=changes.map(p=>({start:p.start-node.start,end:p.end-node.start,text:p.text}));
      let text=applyPatches(node.raw,local);
      if(o.fields&&own(o.fields,'operator')&&(node.kind==='binary'||node.kind==='logical')&&node.parentId)text='('+text+')';
      return {ok:true,source:text};
    }catch(e){return {ok:false,error:e.message};}
  }
  function listFor(projection,parentId,input){
    if(parentId==null){return {ids:projection.rootIds,range:projection.rootRange,parent:null,key:'root'};}
    const p=getNode(projection,parentId),ids=p.inputs[input],range=p.listRanges[input];
    if(!Array.isArray(ids)||!range||p.inputKinds[input]!=='statement')throw new Error('Destination must be a statement list');
    return {ids,range,parent:p,key:input};
  }
  function indentAt(source,pos){const start=source.lastIndexOf('\n',pos-1)+1;return (source.slice(start,pos).match(/^[\t ]*/)||[''])[0];}
  function indentation(projection,list,index){
    const adjacent=list.ids[Math.min(index,list.ids.length-1)];
    if(adjacent){const n=getNode(projection,adjacent);return indentAt(projection.source,n.start);}
    return list.parent?indentAt(projection.source,list.parent.start)+(projection.source.includes('\t')?'\t':'  '):'';
  }
  function normalizeStatement(text,newline,indent){
    text=String(text).trim();if(!text)throw new Error('Cannot insert an empty block');
    const lines=text.split(/\r?\n/),nonempty=lines.slice(1).filter(s=>s.trim());
    const base=nonempty.length?Math.min(...nonempty.map(s=>(s.match(/^[\t ]*/)||[''])[0].length)):0;
    return lines.map((line,i)=>i===0?line:indent+line.slice(Math.min(base,(line.match(/^[\t ]*/)||[''])[0].length))).join(newline);
  }
  // A closing brace can belong to an object, arrow or function expression.
  // Find the actual trailing statement before deciding whether ASI needs help.
  function semicolonTail(node){
    if(!node)return null;
    if(node.type==='IfStatement')return semicolonTail(node.alternate||node.consequent);
    if(['WhileStatement','ForStatement','ForInStatement','ForOfStatement','WithStatement','LabeledStatement'].includes(node.type))return semicolonTail(node.body);
    if(node.type==='ExportNamedDeclaration')return node.declaration?semicolonTail(node.declaration):node;
    if(node.type==='ExportDefaultDeclaration')return ['FunctionDeclaration','ClassDeclaration'].includes(node.declaration.type)?null:node;
    return ['ExpressionStatement','VariableDeclaration','ReturnStatement','ThrowStatement','BreakStatement','ContinueStatement','DebuggerStatement','DoWhileStatement','ImportDeclaration'].includes(node.type)?node:null;
  }
  function terminatorPatch(source,node){
    const tail=semicolonTail(node);
    return tail&&source[tail.end-1]!==';'?{start:tail.end,end:tail.end,text:';',terminator:true}:null;
  }
  function statementIndex(projection){
    const ast=acorn.parse(projection.source,{ecmaVersion:'latest',sourceType:projection.sourceType,preserveParens:true,allowHashBang:true}),nodes=new Map();
    walk(ast,node=>nodes.set(node.type+':'+node.start+':'+node.end,node));
    return node=>nodes.get(node.type+':'+node.start+':'+node.end);
  }
  function terminateFragment(text,projection,newline){
    const opts={ecmaVersion:'latest',sourceType:projection.sourceType,preserveParens:true,allowReturnOutsideFunction:true,allowAwaitOutsideFunction:true,allowSuperOutsideMethod:true};
    let body,offset=0;
    try{body=acorn.parse(text,opts).body;}catch(_){
      const prefix='async function* __rin_fragment__(){for(;;){'+newline;
      try{body=acorn.parse(prefix+text+newline+'}}',opts).body[0].body.body[0].body.body;offset=prefix.length;}
      catch(_){return text+newline+';';}
    }
    const tail=semicolonTail(body[body.length-1]);if(!tail)return text;
    const end=tail.end-offset;
    return text[end-1]===';'?text:text.slice(0,end)+';'+text.slice(end);
  }
  function mergeBoundaryPatches(patches){
    const guards=new Map(),ordinary=[];
    for(const patch of patches){if(patch.terminator)guards.set(patch.start,patch);else ordinary.push(patch);}
    for(const [position,guard]of guards){
      if(ordinary.some(p=>p.text===''&&p.start<position&&p.end>=position))continue;
      const insertions=ordinary.filter(p=>p.start===position&&p.end===position);
      const index=insertions.length===1?ordinary.findIndex(p=>p.start===position&&p.end===position):-1;
      if(index>=0)ordinary[index]={...ordinary[index],text:guard.text+ordinary[index].text};
      else ordinary.push(guard);
    }
    return ordinary;
  }
  function removalBoundaryPatches(projection,patches){
    const deletions=patches.filter(p=>p.text===''&&p.end>p.start);if(!deletions.length)return patches;
    const astForNode=statementIndex(projection),guards=[];
    const lists=[projection.rootIds,...projection.nodes.flatMap(n=>Object.keys(n.inputs).filter(k=>Array.isArray(n.inputs[k])&&n.inputKinds[k]==='statement').map(k=>n.inputs[k]))];
    const removed=node=>deletions.some(p=>p.start<=node.start&&p.end>=node.end);
    for(const ids of lists){let preceding=null,inRun=false;
      for(const id of ids){const node=getNode(projection,id);if(node.kind==='comment')continue;
        if(removed(node)){if(!inRun&&preceding){const guard=terminatorPatch(projection.source,astForNode(preceding));if(guard)guards.push(guard);}inRun=true;}
        else{preceding=node;inRun=false;}
      }
    }
    return [...guards,...patches];
  }
  function insertPatches(projection,list,index,text){
    if(!Number.isInteger(index)||index<0||index>list.ids.length)throw new Error('Invalid insertion index');
    const newline=projection.source.includes('\r\n')?'\r\n':'\n',indent=indentation(projection,list,index);
    text=terminateFragment(normalizeStatement(text,newline,indent),projection,newline);
    const next=index<list.ids.length?getNode(projection,list.ids[index]):null;
    const previous=index>0?getNode(projection,list.ids[index-1]):null;
    const pos=next?next.start:previous?previous.end:list.range.start;
    let prefix='',suffix='';
    const preceding=list.ids.slice(0,index).reverse().map(id=>getNode(projection,id)).find(node=>node.kind!=='comment');
    const guard=preceding?terminatorPatch(projection.source,statementIndex(projection)(preceding)):null;
    if(previous||list.parent)prefix+=newline+indent;
    if(next||!previous)suffix+=newline+(next?indent: list.parent?indentAt(projection.source,list.parent.start):'');
    return [...(guard?[guard]:[]),{start:pos,end:pos,text:prefix+text+suffix}];
  }
  function astFor(info,node){let found=null;walk(info.ast,a=>{if(a.start===node.start&&a.end===node.end&&a.type===node.type)found=a;});if(!found)semanticFailure('unsupported_binding','この名前はコード画面で変更してください。');return found;}
  function validName(name,sourceType){
    name=String(name==null?'':name).trim();let parsed;
    try{parsed=acorn.parse('let '+name+';',{ecmaVersion:'latest',sourceType});}catch(_){semanticFailure('invalid_name','名前には、空白や記号を含まない変数名を入力してください。');}
    const declaration=parsed.body[0];if(parsed.body.length!==1||declaration.type!=='VariableDeclaration'||declaration.declarations.length!==1||declaration.declarations[0].id.type!=='Identifier'||declaration.declarations[0].id.name!==name||declaration.declarations[0].init)semanticFailure('invalid_name','名前には、空白や記号を含まない変数名を入力してください。');return name;
  }
  function usableBinding(info,binding){
    if(!binding)semanticFailure('unbound_name','この名前の定義が見つかりません。コード画面で確認してください。');
    if(info.dynamic)semanticFailure('dynamic_scope','eval または with を使っているため、名前はコード画面で変更してください。');
    if(binding.kind==='import'||binding.exported)semanticFailure('external_binding','別のファイルと共有する名前は、コード画面で変更してください。');
    if(binding.unsupported)semanticFailure('unsupported_binding','この場所の名前は、コード画面で変更してください。');
  }
  function guardReflectiveGlobal(projection,info,binding){
    if(projection.sourceType!=='script'||binding.scope.kind!=='program'||!['var','function'].includes(binding.kind))return;
    let reflected=false;
    walk(info.ast,node=>{
      if(node.type!=='MemberExpression')return;let object=node.object;while(object.type==='ParenthesizedExpression')object=object.expression;
      if(object.type!=='Identifier'||!['window','globalThis','self'].includes(object.name)||info.resolve(object,object.name))return;
      const property=node.computed?node.property.type==='Literal'?String(node.property.value):null:node.property.type==='Identifier'?node.property.name:null;
      if(property===binding.name||node.computed&&property==null)reflected=true;
    });
    if(reflected)semanticFailure('reflective_global','window などを通して使っている名前です。名前や引数の数はコード画面で変更してください。');
  }
  function renameEdits(projection,op){
    const node=getNode(projection,op.id),info=semanticParse(projection),ast=astFor(info,node);let identifier;
    if(ast.type==='FunctionDeclaration'&&op.parameterIndex!=null){if(!Number.isInteger(op.parameterIndex)||!ast.params[op.parameterIndex]||ast.params[op.parameterIndex].type!=='Identifier')semanticFailure('complex_parameters','この引数はコード画面で変更してください。');identifier=ast.params[op.parameterIndex];}
    else identifier=ast.type==='FunctionDeclaration'?ast.id:ast.type==='Identifier'?ast:null;
    const binding=identifier&&info.roles.get(identifier)?.binding;usableBinding(info,binding);guardReflectiveGlobal(projection,info,binding);
    const name=validName(op.name,projection.sourceType);if(name===binding.name)return [];
    const other=binding.scope.names.get(name);if(other&&other!==binding)semanticFailure('name_collision','同じ場所ですでに使われている名前です。別の名前を入力してください。');
    const rename={binding,name};
    for(const ref of binding.references)if(info.resolve(ref,name,rename)!==binding)semanticFailure('name_capture','変更すると、別の変数と名前が重なります。別の名前を入力してください。');
    for(const ref of info.identifiers){const role=info.roles.get(ref);if(!role||role.declaration||ref.name!==name||role.binding===binding)continue;if(info.resolve(ref,name,rename)===binding)semanticFailure('name_capture','変更すると、すでに使っている名前の意味が変わります。別の名前を入力してください。');}
    const changes=new Map();
    for(const id of [...binding.declarations,...binding.references]){
      let parent=info.parents.get(id)?.node;
      if(parent?.type==='AssignmentPattern'&&parent.left===id)parent=info.parents.get(parent)?.node;
      const text=parent?.type==='Property'&&parent.shorthand?projection.source.slice(id.start,id.end)+': '+name:name;
      changes.set(id.start+':'+id.end,{start:id.start,end:id.end,text});
    }
    return Array.from(changes.values());
  }
  function argumentSource(source){
    const text=String(source==null?'0':source).trim();let program,call;
    try{program=acorn.parse('f('+text+')',{ecmaVersion:'latest',preserveParens:true});call=program.body[0]?.expression;}catch(_){}
    if(!program||program.body.length!==1||!call||call.type!=='CallExpression'||call.callee.type!=='Identifier'||call.callee.name!=='f'||call.arguments.length!==1)semanticFailure('invalid_argument','追加する値を一つ入力してください。');return text;
  }
  function separatedList(tokens,range,items){
    const commas=tokens.filter(t=>t.type.label===','&&t.start>=range.start&&t.end<=range.end&&!items.some(a=>a&&t.start>=a.start&&t.end<=a.end));
    return {range,items,commas};
  }
  function changeList(source,list,action,index,text){
    const {range,items,commas}=list,n=items.length;
    if(!Number.isInteger(index)||index<0||index>(action==='insert'?n:n-1))semanticFailure('invalid_list_index','変更する位置が見つかりません。ブロックを開き直してください。');
    if(action==='insert'){
      text=argumentSource(text);
      if(!n)return [{start:range.end,end:range.end,text}];
      if(index<n){const position=index?commas[index-1]?.end:range.start;if(position==null)semanticFailure('unsupported_list','この並びはコード画面で変更してください。');return [{start:position,end:position,text:text+', '}];}
      if(!items[n-1])return [{start:range.end,end:range.end,text}];
      const trailing=commas.length>=n?commas[n-1]:null;
      return [{start:trailing?trailing.start:range.end,end:trailing?trailing.start:range.end,text:', '+text}];
    }
    const edits=[];if(items[index])edits.push({start:items[index].start,end:items[index].end,text:''});
    if(n>1){const comma=index<n-1?commas[index]:commas[index-1];if(!comma)semanticFailure('unsupported_list','この並びはコード画面で変更してください。');if(!(index===n-1&&!items[index-1]&&commas.length<n))edits.push({start:comma.start,end:comma.end,text:''});}
    else for(const comma of commas)edits.push({start:comma.start,end:comma.end,text:''});
    return edits;
  }
  function inputListEdits(projection,op){
    const node=getNode(projection,op.id),range=node.listRanges[op.input],ids=node.inputs[op.input];
    if(!Array.isArray(ids)||!range||!((op.input==='args'&&['call','p5_call','new'].includes(node.kind))||(op.input==='elements'&&node.kind==='array')))semanticFailure('unsupported_list','このブロックの項目数は変更できません。');
    const info=semanticParse(projection),items=ids.map(id=>id?getNode(projection,id):null);
    return changeList(projection.source,separatedList(info.tokens,range,items),op.type==='insertInput'?'insert':'remove',op.index==null?ids.length:op.index,op.source);
  }
  function astCallList(info,call){
    const opening=info.tokens.find(t=>t.start>=call.callee.end&&t.end<=call.end&&t.type.label==='(');
    if(!opening)semanticFailure('unsupported_call','この呼び出し方はコード画面で変更してください。');return separatedList(info.tokens,{start:opening.end,end:call.end-1},call.arguments);
  }
  function parameterEdits(projection,op){
    const node=getNode(projection,op.id),info=semanticParse(projection),fn=astFor(info,node);
    if(fn.type!=='FunctionDeclaration'||!fn.id||!fn.params.every(p=>p.type==='Identifier'))semanticFailure('complex_parameters','初期値や複雑な書き方を使った引数は、コード画面で変更してください。');
    if(node.event)semanticFailure('event_parameters','イベントが受け取る引数の数は、コード画面で変更してください。');
    const binding=info.roles.get(fn.id)?.binding;usableBinding(info,binding);guardReflectiveGlobal(projection,info,binding);
    if(binding.declarations.length!==1)semanticFailure('ambiguous_function','同じ関数が複数の場所で定義されています。コード画面で確認してください。');
    const scopes=info.functionScopes.get(fn),calls=[];
    for(const ref of binding.references){let expression=ref,parent=info.parents.get(expression)?.node;while(parent?.type==='ParenthesizedExpression'){expression=parent;parent=info.parents.get(expression)?.node;}if(!parent||!['CallExpression','NewExpression'].includes(parent.type)||parent.callee!==expression)semanticFailure('escaped_function','この関数は名前だけを渡して使われています。引数の数はコード画面で変更してください。');if(parent.arguments.some(a=>a.type==='SpreadElement'))semanticFailure('spread_arguments','まとめて渡す引数があるため、引数の数はコード画面で変更してください。');calls.push(parent);}
    for(const id of info.identifiers){if(id.name!=='arguments')continue;let scope=info.nodeScope.get(id);while(scope&&scope.kind!=='parameters')scope=scope.parent;while(scope&&scope.owner.type==='ArrowFunctionExpression'){scope=scope.parent;while(scope&&scope.kind!=='parameters')scope=scope.parent;}if(scope===scopes.params)semanticFailure('arguments_object','arguments を使う関数の引数の数は、コード画面で変更してください。');}
    const action=op.type==='insertParameter'?'insert':'remove',index=op.index==null?fn.params.length:op.index;
    let name,argument;
    if(action==='insert'){
      name=validName(op.name,projection.sourceType);if(scopes.params.names.has(name)||scopes.body.names.has(name))semanticFailure('name_collision','この関数ですでに使われている名前です。別の名前を入力してください。');
      for(const ref of info.identifiers){if(ref.name!==name||info.roles.get(ref)?.declaration)continue;let scope=info.nodeScope.get(ref);while(scope&&scope!==scopes.params&&!scope.names.has(name))scope=scope.parent;if(scope===scopes.params)semanticFailure('name_capture','追加すると、すでに使っている名前の意味が変わります。別の名前を入力してください。');}
      argument=argumentSource(op.argument);
    }else{
      const parameter=fn.params[index],parameterBinding=parameter&&info.roles.get(parameter)?.binding;
      if(!parameterBinding)semanticFailure('invalid_list_index','削除する引数が見つかりません。');
      if(parameterBinding.references.length)semanticFailure('used_parameter','この引数は処理の中で使われています。先に使っている箇所を変更してください。');
    }
    const range=node.fieldRanges.params;if(!range)semanticFailure('complex_parameters','この引数はコード画面で変更してください。');
    const edits=changeList(projection.source,separatedList(info.tokens,range,fn.params),action,index,name);
    for(const call of calls){
      const list=astCallList(info,call);
      if(action==='remove'){if(call.arguments.length>index)edits.push(...changeList(projection.source,list,'remove',index));}
      else if(call.arguments.length>=index)edits.push(...changeList(projection.source,list,'insert',index,argument));
      else{
        // Fill omitted earlier arguments without evaluating or guessing a value.
        const missing=Array.from({length:index-call.arguments.length},()=> 'void 0').concat(argument).join(', ');
        if(!call.arguments.length)edits.push({start:list.range.end,end:list.range.end,text:missing});
        else{const trailing=list.commas.length>=call.arguments.length?list.commas[call.arguments.length-1]:null;edits.push({start:trailing?trailing.start:list.range.end,end:trailing?trailing.start:list.range.end,text:', '+missing});}
      }
    }
    if(action==='remove')return edits.filter(edit=>!edits.some(other=>other!==edit&&other.text===''&&other.end>other.start&&other.start<=edit.start&&other.end>=edit.end));
    return edits;
  }
  function operationPatches(projection,op){
    if(!op||typeof op!=='object')throw new Error('Invalid operation');
    if(op.expectedRevision&&op.expectedRevision!==projection.revision)throw new Error('The source has changed; reload the blocks');
    const type=op.type;
    if(type==='renameBinding')return renameEdits(projection,op);
    if(type==='insertInput'||type==='removeInput')return inputListEdits(projection,op);
    if(type==='insertParameter'||type==='removeParameter')return parameterEdits(projection,op);
    if(type==='setColor'){
      const node=getNode(projection,op.id),hex=String(op.hex||'').toLowerCase();
      if(!node.p5||!['fill','stroke','background','color','ambientMaterial','specularMaterial'].includes(node.calleeName)||!/^#[0-9a-f]{6}$/.test(hex))semanticFailure('unsupported_color','この色はコード画面で変更してください。');
      const ids=node.inputs.args,args=ids.map(id=>getNode(projection,id).raw),alpha=args.length===2?args[1]:args.length===4?args[3]:null;
      if(args.length===1){
        const info=semanticParse(projection),literal=wrappedColorLiteral(astFor(info,node).arguments[0]);
        if(literal)return [{start:literal.start,end:literal.end,text:JSON.stringify(hex)}];
      }
      let value=JSON.stringify(hex);
      if(alpha!=null){
        const info=semanticParse(projection),ast=astFor(info,node);
        const legacy=/^1(?:\.|$)/.test(projection.p5Version);
        if(info.dynamic||info.resolve(ast,'color')||!legacy&&info.resolve(ast,'alpha'))semanticFailure('unsupported_color','独自の色関数があるため、透明度を含む色はコード画面で変更してください。');
        // A p5.Color keeps the current alpha scale while CSS hex fixes the RGB
        // colour independently of HSB/HSL/custom channel ranges. Evaluate alpha
        // outside the arrow so await/yield, this and arguments keep their scope.
        // p5 2's setAlpha defaults to the RGB alpha range even under HSB/HSL.
        // Pass the current numeric colour's full alpha range explicitly.
        value='((c, a) => (c.setAlpha(a'+(legacy?'':', alpha(color(0, 0, 0))')+'), c))(color('+value+'), '+alpha+')';
      }
      const rendered=renderNode(projection,node.id,{inputs:{args:[value]}});if(!rendered.ok)throw new Error(rendered.error);
      return [{start:node.start,end:node.end,text:rendered.source}];
    }
    if(type==='setCall'){
      const node=getNode(projection,op.id);if(!['call','p5_call','new'].includes(node.kind))semanticFailure('unsupported_call','関数の呼び出しを選んでください。');
      const rendered=renderNode(projection,op.id,{inputs:{callee:String(op.callee),args:op.args}});if(!rendered.ok)throw new Error(rendered.error);
      return [{start:node.start,end:node.end,text:rendered.source}];
    }
    if(type==='setField'||type==='setInput'){
      const node=getNode(projection,op.id);
      if(type==='setField'){
        const rendered=renderNode(projection,op.id,{fields:{[op.field]:op.value}});if(!rendered.ok)throw new Error(rendered.error);
        return sourceDiff(node.raw,rendered.source).map(p=>({start:p.start+node.start,end:p.end+node.start,text:p.text}));
      }
      return [inputSource(node,op.input,own(op,'source')?op.source:op.value,projection)];
    }
    if(type==='replace'){
      const node=getNode(projection,op.id),parent=node.parentId&&getNode(projection,node.parentId),text=String(op.source);
      return [{start:node.start,end:node.end,text:!node.statement&&parent?protectExpression(parent,node.parentInput,text):text}];
    }
    if(type==='insert')return insertPatches(projection,listFor(projection,op.parentId,op.input),op.index==null?listFor(projection,op.parentId,op.input).ids.length:op.index,op.source);
    if(type==='delete'){
      const ids=Array.isArray(op.ids)?op.ids:[op.id],ranges=[];
      for(const id of new Set(ids)){
        const node=getNode(projection,id);
        if(node.parentId){const parent=getNode(projection,node.parentId);if(!Array.isArray(parent.inputs[node.parentInput])||parent.inputKinds[node.parentInput]!=='statement')throw new Error('Only whole statements can be removed; replace an expression input instead');}
        ranges.push({start:node.ownedStart==null?node.start:node.ownedStart,end:node.end,text:''});
      }
      const merged=[];
      for(const range of ranges.sort((a,b)=>a.start-b.start||b.end-a.end)){
        const prior=merged[merged.length-1];
        if(prior&&range.start<=prior.end)prior.end=Math.max(prior.end,range.end);
        else merged.push(range);
      }
      return merged;
    }
    if(type==='duplicate'){
      const node=getNode(projection,op.id),parentId=own(op,'parentId')?op.parentId:node.parentId||null,input=op.input||node.parentInput;
      const list=listFor(projection,parentId,input),index=op.index==null?list.ids.indexOf(node.id)+1:op.index;
      return insertPatches(projection,list,index,projection.source.slice(node.ownedStart==null?node.start:node.ownedStart,node.end));
    }
    if(type==='move'){
      const node=getNode(projection,op.id),list=listFor(projection,op.parentId,op.input);
      if(node.parentId){const p=getNode(projection,node.parentId);if(!Array.isArray(p.inputs[node.parentInput])||p.inputKinds[node.parentInput]!=='statement')throw new Error('Only whole statements can be moved');}
      if(list.parent&&list.parent.start>=node.start&&list.parent.end<=node.end)throw new Error('A block cannot contain itself');
      const index=op.index==null?list.ids.length:op.index;
      if(list.ids[index]===node.id||list.ids[index-1]===node.id)return [];
      const insertions=insertPatches(projection,list,index,projection.source.slice(node.ownedStart==null?node.start:node.ownedStart,node.end));
      if(insertions.some(p=>!p.terminator&&p.start>node.start&&p.start<node.end))throw new Error('Invalid move destination');
      return [{start:node.ownedStart==null?node.start:node.ownedStart,end:node.end,text:''},...insertions];
    }
    throw new Error('Unknown operation: '+type);
  }
  function moveStack(projection,op){
    if(!Array.isArray(op.ids)||!op.ids.length)throw new Error('A stack must contain at least one block');
    if(new Set(op.ids).size!==op.ids.length)throw new Error('A stack contains duplicate blocks');
    const destination=listFor(projection,op.parentId,op.input);
    const ranges=op.ids.map(id=>{
      const n=getNode(projection,id);
      if(n.parentId){const parent=getNode(projection,n.parentId);if(!Array.isArray(parent.inputs[n.parentInput])||parent.inputKinds[n.parentInput]!=='statement')throw new Error('Only whole statements can be moved');}
      if(destination.parent&&destination.parent.start>=n.start&&destination.parent.end<=n.end)throw new Error('A block cannot contain itself');
      return {start:n.ownedStart==null?n.start:n.ownedStart,end:n.end,node:n};
    }).sort((a,b)=>a.start-b.start||a.end-b.end);
    const merged=[];
    for(const range of ranges){const prior=merged[merged.length-1];if(prior&&range.start<prior.end){
      // An explicit annotation block and its owner can occur in the same dragged stack.
      if(prior.node.kind==='comment'&&range.node.attachedCommentIds&&range.node.attachedCommentIds.includes(prior.node.id)){prior.end=Math.max(prior.end,range.end);prior.node=range.node;}
      else if(range.node.kind==='comment'&&prior.node.attachedCommentIds&&prior.node.attachedCommentIds.includes(range.node.id))continue;
      else throw new Error('A stack cannot contain nested blocks');
    }else merged.push({...range});}
    const newline=projection.source.includes('\r\n')?'\r\n':'\n';
    const text=merged.map(r=>terminateFragment(projection.source.slice(r.start,r.end),projection,newline)).join(newline);
    const without=applyPatches(projection.source,mergeBoundaryPatches(removalBoundaryPatches(projection,merged.map(r=>({start:r.start,end:r.end,text:''})))));
    const intermediate=parse(without,{sourceType:projection.sourceType,p5Version:projection.p5Version,metadata:projection.metadata});
    if(!intermediate.ok)throw new Error(intermediate.diagnostics[0].message);
    const list=listFor(intermediate,op.parentId,op.input),index=op.index==null?list.ids.length:op.index;
    const candidate=applyPatches(without,mergeBoundaryPatches(insertPatches(intermediate,list,index,text)));
    const result=parse(candidate,{sourceType:projection.sourceType,p5Version:projection.p5Version,metadata:projection.metadata});
    if(!result.ok)throw new Error(result.diagnostics[0].message);
    return result;
  }
  function proposePatch(projection,operations){
    try{
      if(!projection||!projection.ok)throw new Error('Blocks are read-only until the source parses successfully');
      const original=projection.source;
      let current=projection;
      for(const op of Array.isArray(operations)?operations:[operations]){
        if(op&&op.type==='move'&&Array.isArray(op.ids)){if(op.expectedRevision&&op.expectedRevision!==current.revision)throw new Error('The source has changed; reload the blocks');current=moveStack(current,op);continue;}
        const patches=mergeBoundaryPatches(removalBoundaryPatches(current,operationPatches(current,op))),candidate=applyPatches(current.source,patches);
        const next=parse(candidate,{sourceType:current.sourceType,p5Version:current.p5Version,metadata:current.metadata});
        if(!next.ok){return {ok:false,error:next.diagnostics[0].message,diagnostics:next.diagnostics,patches:[],source:original,candidateSource:original,projection};}
        current=next;
      }
      return {ok:true,patches:sourceDiff(original,current.source),source:current.source,candidateSource:current.source,projection:current};
    }catch(e){return {ok:false,error:e.message,errorCode:e.code||null,patches:[],source:projection?projection.source:'',candidateSource:projection?projection.source:'',projection};}
  }
  function applyOperation(projection,operation){return proposePatch(projection,operation);}
  return {SCHEMA_VERSION,parse,proposePatch,applyOperation,applyPatches,renderNode,hash,P5_NAMES:Array.from(P5_NAMES),EVENT_NAMES:Array.from(EVENT_NAMES)};
}));
