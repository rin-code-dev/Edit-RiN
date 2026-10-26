/* Guided source edits. This module parses data and never evaluates a user's sketch. */
(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory(require('./vendor/acorn.js'),require('./core.js'),require('./catalogue.js'));
  else root.EditRinBlockAuthoring=factory(root.acorn,root.EditRinBlocks,root.EditRinBlockCatalogue);
}(typeof self!=='undefined'?self:globalThis,function(acorn,core,catalogue){
  'use strict';
  const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
  const EVENTS=catalogue.EVENTS||[];
  const EVENT_NAMES=new Set(EVENTS.map(x=>x.name));
  const GESTURES=new Set(['mousePressed','mouseReleased','mouseClicked','doubleClicked','mouseDragged','mouseWheel','touchStarted','touchMoved','touchEnded','keyPressed','keyReleased','keyTyped']);
  const SOUND_ACTIONS=new Set(['soundPlay','soundStop','soundLoop','soundVolume','soundRate','soundPan']);
  const TONE_ACTIONS=new Set(['toneStart','toneStop','toneFreq','toneVolume']);
  const MESH_CALLS=new Set(['box','sphere','cylinder','cone','torus','plane','ellipsoid','beginShape']);
  const fail=(code,message,range)=>{const e=new Error(message);e.code=code;e.range=range;throw e;};
  const major=env=>/^1(?:\.|$)/.test(String(env.p5Version||''))?1:2;
  function walk(node,visit,parent){
    if(!node||typeof node.type!=='string')return;visit(node,parent);
    for(const [key,value]of Object.entries(node)){
      if(key==='loc'||key==='range')continue;
      if(Array.isArray(value))value.forEach(child=>walk(child,visit,node));
      else if(value&&typeof value.type==='string')walk(value,visit,node);
    }
  }
  function namesInPattern(node,names){
    if(!node)return;
    if(node.type==='Identifier')names.add(node.name);
    else if(node.type==='RestElement')namesInPattern(node.argument,names);
    else if(node.type==='AssignmentPattern')namesInPattern(node.left,names);
    else if(node.type==='ArrayPattern')node.elements.forEach(n=>namesInPattern(n,names));
    else if(node.type==='ObjectPattern')node.properties.forEach(p=>namesInPattern(p.type==='RestElement'?p.argument:p.value,names));
  }
  const directCall=(node,name)=>node?.type==='CallExpression'&&!node.optional&&node.callee.type==='Identifier'&&node.callee.name===name;
  const member=(node,name,method)=>node?.type==='MemberExpression'&&!node.computed&&!node.optional&&node.object.type==='Identifier'&&node.object.name===name&&node.property.name===method;
  const callMember=(node,name,method)=>node?.type==='CallExpression'&&!node.optional&&member(node.callee,name,method);
  const unwrap=node=>node?.type==='ParenthesizedExpression'?unwrap(node.expression):node;
  function functionLocals(fn){
    const result=new Set();fn.params.forEach(p=>namesInPattern(p,result));
    for(const statement of fn.body.body){
      if(statement.type==='VariableDeclaration')statement.declarations.forEach(d=>namesInPattern(d.id,result));
      if((statement.type==='FunctionDeclaration'||statement.type==='ClassDeclaration')&&statement.id)result.add(statement.id.name);
    }
    function collect(node){
      if(!node||typeof node.type!=='string')return;
      if(['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(node.type))return;
      if(node.type==='VariableDeclaration'&&node.kind==='var')node.declarations.forEach(d=>namesInPattern(d.id,result));
      for(const [key,v]of Object.entries(node)){if(key==='loc'||key==='range')continue;if(Array.isArray(v))v.forEach(collect);else if(v&&v.type)collect(v);}
    }
    collect(fn.body);return result;
  }
  function context(projection,env){
    if(!projection?.ok||typeof projection.source!=='string')fail('invalid_source','Fix the syntax error in code before adding these blocks.');
    const ast=acorn.parse(projection.source,{ecmaVersion:'latest',sourceType:projection.sourceType||'script',allowHashBang:true,preserveParens:true});
    const ctx={projection,env,ast,source:projection.source,names:new Set(),globalNames:new Set(),globalWrites:new Set(),functions:new Map(),declarations:new Map(),callbackAssignments:new Set(),records:[],parents:new WeakMap()};
    const programScope=projection.scopes.find(s=>s.kind==='program');
    for(const name of programScope?.names||[])ctx.globalNames.add(name);
    function declare(name,node,kind){if(!ctx.declarations.has(name))ctx.declarations.set(name,[]);ctx.declarations.get(name).push({node,kind});ctx.globalNames.add(name);}
    for(const node of ast.body){
      if(node.type==='FunctionDeclaration'&&node.id){declare(node.id.name,node,'function');if(!ctx.functions.has(node.id.name))ctx.functions.set(node.id.name,[]);ctx.functions.get(node.id.name).push(node);}
      else if(node.type==='VariableDeclaration')node.declarations.forEach(d=>{const names=new Set();namesInPattern(d.id,names);for(const name of names)declare(name,d,node.kind);});
      else if(node.type==='ClassDeclaration'&&node.id)declare(node.id.name,node,'class');
      else if(node.type==='ImportDeclaration')node.specifiers.forEach(s=>declare(s.local.name,s,'import'));
      else if((node.type==='ExportNamedDeclaration'||node.type==='ExportDefaultDeclaration')&&node.declaration){
        const d=node.declaration;if(d.id)declare(d.id.name,d,'export');
        if(d.type==='VariableDeclaration')d.declarations.forEach(v=>{const names=new Set();namesInPattern(v.id,names);for(const name of names)declare(name,v,'export');});
      }
    }
    walk(ast,(node,parent)=>{
      ctx.parents.set(node,parent);
      if(node.type==='WithStatement'||node.type==='CallExpression'&&unwrap(node.callee)?.type==='Identifier'&&unwrap(node.callee).name==='eval')ctx.dynamic=true;
      if(node.type==='NewExpression'&&node.callee.type==='Identifier'&&node.callee.name==='p5')ctx.instanceMode=true;
      if(node.type==='Identifier')ctx.names.add(node.name);
      if(node.type==='AssignmentExpression'){
        const lhs=node.left;
        const written=new Set();namesInPattern(lhs,written);
        for(const name of written){
          let identifier;walk(lhs,n=>{if(n.type==='Identifier'&&n.name===name)identifier=n;});
          const projected=projection.nodes.find(n=>n.start===identifier?.start&&n.kind==='identifier');
          let owner=parent;while(owner&&!['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(owner.type))owner=ctx.parents.get(owner);
          const local=!projected&&owner?.body?.type==='BlockStatement'&&functionLocals(owner).has(name);
          if(!local&&(!projected||projected.bindingScopeId==null||projected.bindingScopeId===programScope?.id)){
            ctx.globalWrites.add(name);if(EVENT_NAMES.has(name))ctx.callbackAssignments.add(name);
          }
        }
        if(lhs.type==='MemberExpression'&&((lhs.object.type==='Identifier'&&['window','globalThis','self'].includes(lhs.object.name))||lhs.object.type==='ThisExpression')){
          const property=lhs.computed?lhs.property.type==='Literal'&&typeof lhs.property.value==='string'?lhs.property.value:null:lhs.property.name;
          if(property){ctx.globalWrites.add(property);if(EVENT_NAMES.has(property))ctx.callbackAssignments.add(property);}
          if(!property)ctx.dynamic=true;
        }
      }
      if(node.type==='VariableDeclarator'&&parent?.kind==='var'){
        let owner=parent;while(owner&&!['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(owner.type))owner=ctx.parents.get(owner);
        if(!owner&&ctx.parents.get(parent)!==ast){const names=new Set();namesInPattern(node.id,names);for(const name of names)if(EVENT_NAMES.has(name))ctx.callbackAssignments.add(name);}
      }
      // A conditional/global var or a block declaration must not make us generate a second callback.
      if(node.type==='FunctionDeclaration'&&node.id&&EVENT_NAMES.has(node.id.name)&&parent!==ast){
        let owner=parent;while(owner&&!['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(owner.type))owner=ctx.parents.get(owner);
        if(!owner)ctx.callbackAssignments.add(node.id.name);
      }
    });
    ctx.globalScopeId=programScope?.id;
    return ctx;
  }
  function eventDefinition(name,env){
    const event=EVENTS.find(e=>e.name===name);
    if(!event)fail('unknown_event','Choose a supported p5.js event.');
    if(!event.versions.includes(major(env)))fail('event_unavailable','This event belongs to p5.js 1.x. In 2.x use async setup or a mouse event.');
    return event;
  }
  function existingEvent(ctx,name){
    eventDefinition(name,ctx.env);
    const functions=ctx.functions.get(name)||[],declarations=ctx.declarations.get(name)||[];
    if(ctx.callbackAssignments.has(name)||functions.length>1||declarations.length>1||
      (ctx.globalNames.has(name)&&functions.length!==1)||declarations.some(d=>d.kind!=='function'))
      fail('callback_conflict','This callback has a custom or duplicate definition. Edit that definition in code.',declarations[0]?.node);
    const fn=functions[0];
    if(fn?.generator)fail('callback_conflict','A generator callback must be edited in code.',fn);
    return fn||null;
  }
  function available(ctx,names,fn){
    const locals=fn?functionLocals(fn):new Set();
    for(const name of names)if(ctx.globalNames.has(name)||ctx.globalWrites.has(name)||locals.has(name))fail('runtime_shadowed','The name '+name+' is defined by this work. Edit this custom code instead.');
  }
  function resourceVisible(ctx,name,fn){if(functionLocals(fn).has(name))fail('resource_shadowed','This function has a local '+name+'. Choose another event or edit it in code.',fn);}
  function projectNode(ctx,astNode){return ctx.projection.nodes.find(n=>n.start===astNode?.start&&n.end===astNode?.end)?.id||null;}
  function recognize(ctx,value,owner){
    let node=unwrap(value),awaited=false;
    if(node?.type==='AwaitExpression'){awaited=true;node=unwrap(node.argument);}
    if(!node)return null;
    for(const [call,kind]of [['loadImage','image'],['loadSound','sound']]){
      if(!directCall(node,call)||ctx.globalNames.has(call)||ctx.globalWrites.has(call)||owner&&functionLocals(owner).has(call))continue;
      const proper=major(ctx.env)===1?owner?.id?.name==='preload':owner?.id?.name==='setup'&&owner.async&&awaited;
      if(!proper)return null;
      const arg=unwrap(node.arguments[0]);
      return {kind,assetName:arg?.type==='Literal'&&typeof arg.value==='string'?arg.value:null,loadNode:node,owner};
    }
    if(node.type==='NewExpression'&&member(node.callee,'p5','Oscillator')&&!ctx.globalNames.has('p5')&&!ctx.globalWrites.has('p5')&&(!owner||!functionLocals(owner).has('p5')))return {kind:'oscillator',owner,loadNode:node};
    return null;
  }
  function assetIdentity(path,env){
    if(typeof path!=='string')return path;
    const normalized=path.replace(/^\.\//,'');
    // Match the same exact/legacy asset aliases as AssetWebClient, without changing authored URLs.
    const candidates=[normalized,normalized.startsWith('assets/')?normalized.slice(7):'assets/'+normalized];
    for(const candidate of candidates)if((env.assets||[]).some(a=>a.name===candidate))return candidate;
    return path;
  }
  function resources(ctx){
    const candidates=[];
    for(const node of ctx.ast.body)if(node.type==='VariableDeclaration')for(const declaration of node.declarations){
      if(declaration.id.type!=='Identifier')continue;const resource=recognize(ctx,declaration.init,null);
      if(resource)candidates.push({name:declaration.id.name,...resource,statement:node});
    }
    for(const [name]of ctx.functions){
      if(!['preload','setup'].includes(name))continue;
      let fn;try{fn=existingEvent(ctx,name);}catch(_){continue;}
      if(!fn)continue;const locals=functionLocals(fn);
      for(const statement of fn.body.body){
        const assignment=statement.type==='ExpressionStatement'?unwrap(statement.expression):null;
        if(assignment?.type!=='AssignmentExpression'||assignment.operator!=='='||assignment.left.type!=='Identifier')continue;
        const variable=assignment.left.name;
        if(!ctx.globalNames.has(variable)||locals.has(variable))continue;
        const resource=recognize(ctx,assignment.right,fn);
        if(resource)candidates.push({name:variable,...resource,statement});
      }
    }
    const result=[];
    for(const resource of candidates){
      if(candidates.filter(r=>r.name===resource.name).length!==1)continue;
      let unsafe=false;
      walk(ctx.ast,node=>{
        if(!['AssignmentExpression','UpdateExpression'].includes(node.type)||node.start===resource.statement.expression?.start)return;
        const target=node.type==='UpdateExpression'?node.argument:node.left,names=new Set();namesInPattern(target,names);
        if(!names.has(resource.name))return;
        let identifier=null;walk(target,n=>{if(n.type==='Identifier'&&n.name===resource.name)identifier=n;});
        const projected=identifier&&ctx.projection.nodes.find(n=>n.kind==='identifier'&&n.start===identifier.start);
        if(projected?.bindingScopeId&&projected.bindingScopeId!==ctx.globalScopeId)return;
        if(!projected){let fn=ctx.parents.get(node);while(fn&&!['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(fn.type))fn=ctx.parents.get(fn);if(fn?.body?.type==='BlockStatement'&&functionLocals(fn).has(resource.name))return;}
        unsafe=true;
      });
      if(unsafe)continue;
      if(resource.kind==='oscillator'){
        walk(ctx.ast,node=>{
          if(node.type!=='IfStatement'||node.test.type!=='UnaryExpression'||node.test.operator!=='!'||node.test.argument.type!=='Identifier')return;
          const flag=node.test.argument.name;if(!ctx.globalNames.has(flag))return;
          let starts=false,sets=false;
          walk(node.consequent,n=>{if(callMember(n,resource.name,'start'))starts=true;if(n.type==='AssignmentExpression'&&n.operator==='='&&n.left.type==='Identifier'&&n.left.name===flag&&n.right.type==='Literal'&&n.right.value===true)sets=true;});
          if(starts&&sets)resource.startedName=flag;
        });
        // Freshly created instruments have not acquired a play event yet.
        if(!resource.startedName&&resource.owner){
          const body=resource.owner.body.body,index=body.indexOf(resource.statement),next=body[index+1]?.expression;
          if(next?.type==='AssignmentExpression'&&next.operator==='='&&next.left.type==='Identifier'&&ctx.globalNames.has(next.left.name)&&next.right.type==='Literal'&&next.right.value===false)resource.startedName=next.left.name;
        }
      }
      if(resource.assetName)resource.assetName=assetIdentity(resource.assetName,ctx.env);
      result.push(resource);
    }
    return result;
  }
  function canvasInfo(ctx){
    let fn;try{fn=existingEvent(ctx,'setup');}catch(_){return {webgl:false,ambiguous:true,calls:[]};}
    if(!fn)return {webgl:false,ambiguous:false,calls:[]};
    const calls=fn.body.body.filter(n=>n.type==='ExpressionStatement').map(n=>unwrap(n.expression)).filter(n=>directCall(n,'createCanvas'));
    let indirect=false;walk(fn.body,node=>{if(directCall(node,'createCanvas')&&!calls.includes(node))indirect=true;});
    const locals=functionLocals(fn),shadowed=ctx.globalNames.has('createCanvas')||ctx.globalWrites.has('createCanvas')||locals.has('createCanvas');
    const renderer=calls.length===1&&unwrap(calls[0].arguments[2])?.type==='Identifier'?unwrap(calls[0].arguments[2]).name:null;
    const rendererShadowed=renderer&&(ctx.globalNames.has(renderer)||ctx.globalWrites.has(renderer)||locals.has(renderer));
    const ambiguous=indirect||shadowed||!!rendererShadowed||calls.length>1;
    return {webgl:!ambiguous&&calls.length===1&&['WEBGL','WEBGPU'].includes(renderer),renderer,ambiguous,calls,fn};
  }
  function inspect(projection,env){
    env=env||{};
    try{
      const ctx=context(projection,env),canvas=canvasInfo(ctx);
      const events=[];for(const name of ctx.functions.keys())if(EVENT_NAMES.has(name)){try{const fn=existingEvent(ctx,name);if(fn)events.push({name,id:projectNode(ctx,fn),async:fn.async});}catch(_){}}
      const assets={image:[],sound:[]};for(const asset of env.assets||[]){if(typeof asset.name!=='string'||typeof asset.mime!=='string')continue;if(asset.mime.toLowerCase().startsWith('image/'))assets.image.push(asset);if(asset.mime.toLowerCase().startsWith('audio/'))assets.sound.push(asset);}
      const variables=[],seenVars=new Set();
      for(const node of ctx.ast.body){
        const decl=node.type==='ExportNamedDeclaration'?node.declaration:node;
        if(decl?.type==='VariableDeclaration'&&['let','const'].includes(decl.kind)){
          for(const d of decl.declarations){
            const names=new Set();namesInPattern(d.id,names);
            for(const name of names){
              if(seenVars.has(name))continue;
              seenVars.add(name);
              const initText=d.init?ctx.source.slice(d.init.start,d.init.end):null;
              variables.push({name,kind:decl.kind,init:initText});
            }
          }
        }
      }
      return {ok:true,resources:resources(ctx).map(r=>({name:r.name,kind:r.kind,assetName:r.assetName||null})),variables,webgl:canvas.webgl,renderer:canvas.renderer||null,events,assets,supportedEvents:EVENTS.filter(e=>e.versions.includes(major(env))).map(e=>e.name)};
    }catch(error){return {ok:false,resources:[],variables:[],webgl:false,events:[],assets:{image:[],sound:[]},errorCode:error.code||'invalid_source',error:error.message};}
  }
  function sourceDiff(before,after){
    if(before===after)return [];
    let start=0;while(start<before.length&&start<after.length&&before[start]===after[start])start++;
    if(start>0&&/[\uD800-\uDBFF]/.test(before[start-1]))start--;
    let endBefore=before.length,endAfter=after.length;
    while(endBefore>start&&endAfter>start&&before[endBefore-1]===after[endAfter-1]){endBefore--;endAfter--;}
    if(endBefore<before.length&&endBefore>start&&/[\uDC00-\uDFFF]/.test(before[endBefore])){endBefore++;endAfter++;}
    return [{start,end:endBefore,text:after.slice(start,endAfter)}];
  }
  function validName(name,sourceType){
    name=String(name==null?'':name).trim();let parsed;
    try{parsed=acorn.parse('let '+name+';',{ecmaVersion:'latest',sourceType});}catch(_){fail('invalid_name','名前には、空白や記号を含まない変数名を入力してください。');}
    const declaration=parsed.body[0];
    if(parsed.body.length!==1||declaration.type!=='VariableDeclaration'||declaration.declarations.length!==1||declaration.declarations[0].id.type!=='Identifier'||declaration.declarations[0].id.name!==name||declaration.declarations[0].init)fail('invalid_name','名前には、空白や記号を含まない変数名を入力してください。');
    return name;
  }
  function validInit(name,init,sourceType){
    init=String(init==null?'0':init).trim();
    if(!init)init='0';
    let parsed,expr;
    try{
      expr=acorn.parseExpressionAt(init,0,{ecmaVersion:'latest',sourceType,preserveParens:true});
      if(init.slice(expr.end).trim()!=='')fail('invalid_init','有効な初期値を入力してください。');
      parsed=acorn.parse('let '+name+' = '+init+';',{ecmaVersion:'latest',sourceType});
    }catch(_){
      fail('invalid_init','有効な初期値を入力してください。');
    }
    const declaration=parsed.body[0];
    if(parsed.body.length!==1||declaration.type!=='VariableDeclaration'||declaration.declarations.length!==1||!declaration.declarations[0].init)fail('invalid_init','有効な初期値を入力してください。');
    return init;
  }
  class Edit {
    constructor(projection,env){this.original=projection;this.projection=projection;this.env=env;this.source=projection.source;this.newline=this.source.includes('\r\n')?'\r\n':'\n';this.refresh();if(this.ctx.instanceMode)fail('instance_mode','This work creates p5 instances. Add instance callbacks in code.');if(this.ctx.dynamic)fail('dynamic_scope','This work changes names dynamically. Add these helpers in code.');}
    refresh(){this.ctx=context(this.projection,this.env);}
    patch(start,end,text){
      this.source=this.source.slice(0,start)+text+this.source.slice(end);
      const parsed=core.parse(this.source,{sourceType:this.original.sourceType,p5Version:this.env.p5Version||this.original.p5Version,metadata:this.projection.metadata});
      if(!parsed.ok)fail('invalid_change',parsed.diagnostics?.[0]?.message||'This change cannot be represented safely.');
      this.projection=parsed;this.refresh();
    }
    unique(base){let name=base,index=2;while(this.ctx.names.has(name)||this.ctx.globalNames.has(name)||this.ctx.globalWrites.has(name))name=base+index++;return name;}
    global(name,initializer){
      let offset=0;const body=this.ctx.ast.body;
      for(const node of body){if(node.directive||node.type==='ImportDeclaration')offset=node.end;else break;}
      if(offset===0&&this.source.startsWith('#!')){const end=this.source.indexOf('\n');offset=end<0?this.source.length:end+1;}
      const prefix=offset>0&&!/[\r\n]$/.test(this.source.slice(0,offset))?this.newline:'';
      this.patch(offset,offset,prefix+'let '+name+(initializer==null?'':' = '+initializer)+';'+this.newline);
    }
    variable(name,initializer){
      let offset=0;const body=this.ctx.ast.body;
      let lastVarNode=null;
      if(this.source.startsWith('#!')){const nl=this.source.indexOf('\n');offset=nl<0?this.source.length:nl+1;}
      for(const node of body){
        if(node.directive||node.type==='ImportDeclaration'){
          offset=node.end;
          if(this.source.slice(offset,offset+2)==='\r\n')offset+=2;
          else if(this.source.slice(offset,offset+1)==='\n')offset+=1;
        }else if(node.type==='VariableDeclaration'){
          lastVarNode=node;
          offset=node.end;
        }else{
          break;
        }
      }
      let prefix='',suffix='';
      if(lastVarNode){
        if(this.source.slice(offset,offset+2)==='\r\n')offset+=2;
        else if(this.source.slice(offset,offset+1)==='\n')offset+=1;
        else prefix=this.newline;
      }else{
        if(offset>0&&!/[\r\n]$/.test(this.source.slice(0,offset)))prefix=this.newline;
        if(offset<this.source.length&&!/^[\r\n]/.test(this.source.slice(offset)))suffix=this.newline;
      }
      this.patch(offset,offset,prefix+'let '+name+(initializer==null?'':' = '+initializer)+';'+this.newline+suffix);
    }
    event(name){
      let fn=existingEvent(this.ctx,name);if(fn)return fn;
      let seed='';if(name==='setup'){available(this.ctx,['createCanvas']);seed='  createCanvas(400, 400);'+this.newline;}
      this.patch(this.source.length,this.source.length,(this.source&&!/[\r\n]$/.test(this.source)?this.newline:'')+this.newline+'function '+name+'() {'+this.newline+seed+'}'+this.newline);
      return existingEvent(this.ctx,name);
    }
    body(name,code,placement){
      let fn=this.event(name),offset;
      const directives=fn.body.body.filter((node,index,body)=>node.directive&&body.slice(0,index).every(x=>x.directive));
      if(placement==='start')offset=directives.length?directives[directives.length-1].end:fn.body.start+1;
      else if(placement&&typeof placement==='object')offset=placement.offset;
      else offset=fn.body.body.find(n=>n.type==='ReturnStatement'||n.type==='ThrowStatement')?.start??fn.body.end-1;
      const indent=(this.source.slice(this.source.lastIndexOf('\n',fn.start-1)+1,fn.start).match(/^[\t ]*/)||[''])[0]+'  ';
      const normalized=String(code).replace(/\r?\n/g,this.newline+indent);
      this.patch(offset,offset,this.newline+indent+normalized+this.newline+indent);
    }
    asyncSetup(){const fn=this.event('setup');if(!fn.async)this.patch(fn.start,fn.start,'async ');}
    finish(extra){return Object.assign({ok:true,patches:sourceDiff(this.original.source,this.source),source:this.source,projection:this.projection,focusId:null},extra||{});}
  }
  function soundEnabled(env){if(!env.soundEnabled)fail('sound_disabled','Enable p5.sound in Runtime settings before adding sound blocks.');}
  function loadedResource(edit,name,kind){const r=resources(edit.ctx).find(r=>r.name===name&&r.kind===kind);if(!r)fail('resource_missing','Choose a loaded global '+kind+' resource, or load one from Work assets.');return r;}
  function chooseAsset(env,kind,name){
    const asset=(env.assets||[]).find(a=>a?.name===name&&typeof a.mime==='string'&&a.mime.toLowerCase().startsWith(kind==='image'?'image/':'audio/'));
    if(!asset)fail('asset_missing','Choose a matching '+kind+' file from this work’s assets.');
    return asset;
  }
  function safeRuntime(edit,callback,names,resourceName){
    const fn=edit.event(callback);available(edit.ctx,names,fn);if(resourceName)resourceVisible(edit.ctx,resourceName,fn);return fn;
  }
  function audioGesture(code){return 'Promise.resolve(userStartAudio()).then(() => {\n  '+code.replace(/\n/g,'\n  ')+'\n});';}
  function useResource(edit,request){
    const action=request.action,kind=SOUND_ACTIONS.has(action)?'sound':TONE_ACTIONS.has(action)?'oscillator':['image','texture'].includes(action)?'image':null;
    if(!kind)fail('unknown_action','Choose a supported resource action.');
    if(kind!=='image')soundEnabled(edit.env);
    const resource=loadedResource(edit,request.name,kind);
    let target=request.targetEvent||({image:'draw',texture:'draw',soundStop:'mouseReleased',toneStop:'mouseReleased',toneFreq:'draw'}[action]||'mousePressed');
    eventDefinition(target,edit.env);
    if(['setup','preload'].includes(target)&&['soundPlay','soundLoop','toneStart'].includes(action))fail('gesture_required','Start audio from a mouse, touch or key event.');
    if(['soundPlay','soundLoop','toneStart'].includes(action)&&!GESTURES.has(target))fail('gesture_required','Start audio from a mouse, touch or key event.');
    let code;
    if(action==='image'){safeRuntime(edit,target,['image','width','height'],resource.name);code='if ('+resource.name+') image('+resource.name+', 0, 0, width, height);';}
    if(action==='texture'){
      if(!canvasInfo(edit.ctx).webgl)fail('webgl_required','Use a WEBGL canvas before adding a texture.');
      safeRuntime(edit,target,['texture'],resource.name);code='if ('+resource.name+') texture('+resource.name+');';
    }
    if(kind==='sound'){
      safeRuntime(edit,target,['soundPlay','soundLoop'].includes(action)?['userStartAudio','Promise']:[],resource.name);
      if(action==='soundPlay')code=audioGesture('if ('+resource.name+') '+resource.name+'.play();');
      if(action==='soundLoop')code=audioGesture('if ('+resource.name+') {\n  '+resource.name+'.loop();'+(major(edit.env)===2?'\n  '+resource.name+'.play();':'')+'\n}');
      if(action==='soundStop')code='if ('+resource.name+') '+resource.name+'.stop();';
      if(action==='soundVolume')code='if ('+resource.name+') '+resource.name+(major(edit.env)===1?'.setVolume(0.5);':'.amp(0.5);');
      if(action==='soundRate')code='if ('+resource.name+') '+resource.name+'.rate(1);';
      if(action==='soundPan'){
        if(major(edit.env)===1)code='if ('+resource.name+') '+resource.name+'.pan(0);';
        else {
          let panner=null,custom=false,disconnects=0;
          const panners=new Set();
          walk(edit.ctx.ast,node=>{if(node.type==='NewExpression'&&member(node.callee,'p5','Panner')){const parent=edit.ctx.parents.get(node);const id=parent?.type==='AssignmentExpression'?parent.left:parent?.type==='VariableDeclarator'?parent.id:null;if(id?.type==='Identifier'&&edit.ctx.globalNames.has(id.name))panners.add(id.name);}});
          walk(edit.ctx.ast,node=>{
            if(callMember(node,resource.name,'connect')){const destination=unwrap(node.arguments[0]);if(destination?.type==='Identifier'&&panners.has(destination.name)&&!panner)panner=destination.name;else custom=true;}
            if(callMember(node,resource.name,'disconnect'))disconnects++;
          });
          if(custom||disconnects&&!panner)fail('custom_audio_routing','This sound already has custom audio routing. Adjust its panner in code.');
          if(!panner){
            panner=edit.unique('rinPanner');edit.global(panner);
            safeRuntime(edit,'setup',['p5'],resource.name);
            edit.body('setup',panner+' = new p5.Panner(0);\n'+resource.name+'.disconnect();\n'+resource.name+'.connect('+panner+');');
          }
          resourceVisible(edit.ctx,panner,edit.event(target));code='if ('+panner+') '+panner+'.pan(0);';
        }
      }
    }
    if(kind==='oscillator'){
      safeRuntime(edit,target,action==='toneStart'?['userStartAudio','Promise']:[],resource.name);
      if(action==='toneStart'){
        let flag=resource.startedName;
        if(!flag){flag=edit.unique(resource.name+'Started');edit.global(flag,'false');}
        resourceVisible(edit.ctx,flag,edit.event(target));
        code=audioGesture('if ('+resource.name+') {\n  if (!'+flag+') {\n    '+resource.name+'.start();\n    '+flag+' = true;\n  }\n  '+resource.name+'.amp(0.1, 0.05);\n}');
      }
      if(action==='toneStop')code='if ('+resource.name+') '+resource.name+'.amp(0, 0.1);';
      if(action==='toneFreq')code='if ('+resource.name+') '+resource.name+'.freq(440);';
      if(action==='toneVolume')code='if ('+resource.name+') '+resource.name+'.amp(0.1);';
    }
    let placement;
    if(action==='texture'){
      const fn=edit.event(target),mesh=fn.body.body.find(statement=>{const e=statement.type==='ExpressionStatement'?unwrap(statement.expression):null;return e?.type==='CallExpression'&&e.callee.type==='Identifier'&&MESH_CALLS.has(e.callee.name);});
      if(mesh)placement={offset:mesh.start};else placement='start';
    }
    edit.body(target,code,placement);
    return edit.finish({focusId:projectNode(edit.ctx,existingEvent(edit.ctx,target)),resourceName:resource.name});
  }
  function plan(projection,request,env){
    env=env||{};
    try{
      if(!request||typeof request.type!=='string')fail('unknown_action','Choose an authoring action.');
      const edit=new Edit(projection,env);
      if(request.type==='ensureEvent'){
        const fn=edit.event(request.name);return edit.finish({focusId:projectNode(edit.ctx,fn)});
      }
      if(request.type==='loadAsset'){
        if(!['image','sound'].includes(request.kind))fail('asset_kind','Choose an image or audio file.');
        if(request.kind==='sound')soundEnabled(env);
        const asset=chooseAsset(env,request.kind,request.assetName);
        const previous=resources(edit.ctx).find(r=>r.kind===request.kind&&r.assetName===asset.name);
        if(previous)return edit.finish({resourceName:previous.name,focusId:projectNode(edit.ctx,previous.statement)});
        const name=edit.unique(request.kind==='image'?'rinImage':'rinSound'),callback=major(env)===1?'preload':'setup',call=request.kind==='image'?'loadImage':'loadSound';
        const existing=existingEvent(edit.ctx,callback);available(edit.ctx,[call],existing);
        edit.global(name);edit.event(callback);
        if(major(env)===2)edit.asyncSetup();
        safeRuntime(edit,callback,[call],name);
        edit.body(callback,name+' = '+(major(env)===2?'await ':'')+call+'('+JSON.stringify(asset.name)+');','start');
        // A v1 preload-only work still needs a usable canvas before a later drawing action.
        if(major(env)===1)edit.event('setup');
        return edit.finish({resourceName:name,focusId:projectNode(edit.ctx,existingEvent(edit.ctx,callback))});
      }
      if(request.type==='createOscillator'){
        soundEnabled(env);const existing=existingEvent(edit.ctx,'setup');available(edit.ctx,['p5'],existing);
        const name=edit.unique('rinTone');edit.global(name);const flag=edit.unique(name+'Started');edit.global(flag,'false');
        safeRuntime(edit,'setup',['p5'],name);
        edit.body('setup',name+' = new p5.Oscillator("sine");\n'+flag+' = false;\n'+name+'.amp(0);','start');
        return edit.finish({resourceName:name,focusId:projectNode(edit.ctx,existingEvent(edit.ctx,'setup'))});
      }
      if(request.type==='useResource')return useResource(edit,request);
      if(request.type==='ensureWebGL'){
        const canvas=canvasInfo(edit.ctx);
        if(canvas.renderer==='WEBGL'&&!canvas.ambiguous)return edit.finish({focusId:projectNode(edit.ctx,canvas.calls[0])});
        if(!request.confirmed)fail('webgl_confirmation','Changing the canvas renderer updates createCanvas() to WEBGL. Confirm this code change.');
        if(canvas.ambiguous)fail('ambiguous_canvas','This setup has multiple or custom canvases. Choose the renderer in code.');
        safeRuntime(edit,'setup',['createCanvas','WEBGL']);
        const info=canvasInfo(edit.ctx);
        if(!info.calls.length)edit.body('setup','createCanvas(400, 400, WEBGL);','start');
        else {
          const call=info.calls[0],args=call.arguments;
          if(args.length<2||args.some(a=>a.type==='SpreadElement'))fail('ambiguous_canvas','Choose this custom canvas renderer in code.');
          if(args[2])edit.patch(args[2].start,args[2].end,'WEBGL');
          else edit.patch(args[1].end,args[1].end,', WEBGL');
        }
        const updated=canvasInfo(edit.ctx);return edit.finish({focusId:projectNode(edit.ctx,updated.calls[0])});
      }
      if(request.type==='createVariable'){
        const name=validName(request.name,projection.sourceType||'script');
        if(edit.ctx.globalNames.has(name)||edit.ctx.globalWrites.has(name))fail('name_in_use','The variable "'+name+'" already exists.');
        const init=validInit(name,request.init,projection.sourceType||'script');
        edit.variable(name,init);
        const addedNode=edit.ctx.ast.body.find(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id.name===name));
        return edit.finish({variableName:name,focusId:projectNode(edit.ctx,addedNode)});
      }
      fail('unknown_action','Choose a supported authoring action.');
    }catch(error){return {ok:false,errorCode:error.code||'invalid_change',error:error.message,range:error.range?{start:error.range.start,end:error.range.end}:null,patches:[],source:projection?.source||'',projection,focusId:null};}
  }
  return Object.freeze({API_VERSION:1,inspect,plan});
}));
