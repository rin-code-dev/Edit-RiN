/* global Blockly, EditRinBlockCatalogue */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const C = EditRinBlockCatalogue;
  // Some Android WebViews start with a zero-height CSS layout viewport even after
  // their native view is measured. Percentage/vh heights then keep the editor blank.
  // Use the measured window height and refresh it when the Compose pane changes size.
  function resizeViewport() {
    const height = window.innerHeight;
    if (height > 0 && document.documentElement.style.height !== height + 'px') {
      document.documentElement.style.height = height + 'px';
    }
  }
  resizeViewport();
  addEventListener('resize', resizeViewport);
  window.visualViewport?.addEventListener('resize', resizeViewport);
  addEventListener('pageshow', resizeViewport);
  const TEXT = {
    connecting:['接続しています…','Connecting…','正在连接…'], ready:['ブロックを組み立てて、同じ作品のコードを編集できます。','Build blocks to edit this work’s JavaScript.','组装积木来编辑同一作品的 JavaScript。'],
    program:['作品の処理','Program','程序'], orphan:['外したブロックは実行されません。','Detached blocks do not run.','未连接的积木不会执行。'],
    search:['ブロックを検索','Search blocks','搜索积木'], all:['すべて','All','全部'], examples:['完成例','Examples','示例'], undo:['元に戻す','Undo','撤销'], redo:['やり直す','Redo','重做'], fit:['全体を表示','Fit blocks','适合屏幕'], runtime:['実行環境','Runtime settings','运行环境'], help:['操作の説明','Block editor help','积木编辑帮助'], cancel:['キャンセル','Cancel','取消'],
    fullscreen:['全画面','Full screen','全屏'], exitFullscreen:['分割表示','Split view','分屏'],
    readOnly:['この作品は閲覧専用です。','This work is read only.','此作品为只读。'], syntax:['コードに構文エラーがあります。表示中のブロックは編集できません。ドキュメントバーの「コード」で修正すると再開できます。','The code has a syntax error. The displayed blocks cannot be edited. Switch to Code in the document bar to fix it.','代码有语法错误，当前显示的积木无法编辑。请在文档栏切换到代码并修复。'],
    busy:['変更を反映しています…','Applying change…','正在应用更改…'], rejected:['変更を適用できませんでした。最新のコードから表示を戻しました。','The change could not be applied. Restored blocks from the current code.','无法应用更改。已从当前代码恢复积木。'],
    unsupported:['この接続の変更は安全にコードへ反映できません。コードで編集してください。','This connection cannot be changed safely here. Edit it in code.','无法安全地更改此连接。请在代码中编辑。'],
    code:['コードで編集','Edit in code','在代码中编辑'], insert:['追加する場所','Where to insert','插入位置'], before:['選択した処理の前','Before selected statement','所选语句之前'], after:['選択した処理の後','After selected statement','所选语句之后'], inside:['選択した処理の中','Inside selected statement','所选语句内部'], append:['作品の最後','End of program','程序末尾'], tray:['未接続の部品として置く','Keep as detached block','保留为未连接的积木'], replace:['選択した値を置き換える','Replace selected value','替换所选值'],
    replaceTitle:['完成例で作品を置き換える','Replace work with example','用示例替换作品'], replaceDescription:['現在のコードを完成例に置き換えます。元に戻す操作で復元できます。','This replaces the current code. Undo restores your previous work.','这将替换当前代码。撤销可恢复原作品。'], confirm:['置き換える','Replace','替换'],
    sound:['このブロックには p5.sound が必要です。実行環境から有効にしてください。','This block requires p5.sound. Enable it in Runtime settings.','此积木需要 p5.sound。请在运行环境中启用。'],
    custom:['複雑なコードはそのまま保持しています。タップしてコードで編集できます。','Custom code is preserved. Open it in the code editor to change it.','已保留自定义代码。请打开代码编辑器进行修改。'],
    empty:['検索に一致するブロックがありません。','No matching blocks.','没有匹配的积木。'],
    helpBody:['画面下の「＋ ブロックを追加」からハーフシートを開き、ブロックをタップして追加します。シート上部の「追加する場所」で挿入先（選択した処理の後・中・作品の最後など）を切り替えられます。\nブロック上の色スウォッチや数値カプセルをタップすると、その場で直接変更できます。ブロック本体をタップすると、操作バー（複製・削除・コード表示など）が開きます。\nブロックを移動するときは少し長押ししてからドラッグします。2本指のピンチ操作で拡大・縮小できます。\n上部バーやPiP小窓の全画面ボタンから、作品を大きくプレビューできます。右下のメニューからは分割表示への切り替えも行えます。\n外したブロックは保存されますが実行されません。変更は構文を検証してからコードへ反映され、元のコードは安全に保たれます。','Open the half-sheet from "+ Add block" at the bottom and tap a block to insert it. Use the "Where to insert" bar at the top of the sheet to switch the target (after/inside selected statement, end of program, etc.).\nTap a color swatch or value capsule on a block to edit it directly in place. Tap a block body to show its action bar (duplicate, delete, code preview, etc.).\nTo move a block, press and hold it briefly, then drag. Pinch with two fingers to zoom.\nUse the full-screen preview button in the top bar or floating PiP header to inspect your artwork at full size, or switch to split view from the More actions menu.\nDetached blocks are saved but do not run. Changes are syntax-checked before updating your source code.','点击底部的“＋ 添加积木”打开半屏面板，点击积木即可插入。可通过面板顶部的“插入位置”栏切换目标位置（所选语句之后、内部、程序末尾等）。\n点击积木上的颜色色块或数值胶囊可直接就地修改。点击积木本体会显示操作栏（复制、删除、代码预览等）。\n移动积木时，先按住片刻再拖动。双指捏合可缩放画布。\n点击顶部栏或悬浮小窗标题栏的全屏按钮可全屏预览作品，也可在右下角菜单中切换分屏视图。\n未连接的积木会保存但不会执行。所有更改都会先进行语法检查，确保原始代码安全。'],
    add:['追加','Add','添加'], more:['その他の操作','More options','更多操作'], zoomReset:['拡大率を100%に戻す','Reset zoom to 100%','缩放重置为 100%'], close:['閉じる','Close','关闭'],
    actItems:['項目を編集','Edit items','编辑项目'], actCode:['コード','Code','代码'], actDuplicate:['複製','Duplicate','复制'], actDelete:['削除','Delete','删除'], actColor:['色','Color','颜色'], actNumber:['数値','Number','数值'], actName:['名前','Name','名称'], actCollapse:['折りたたむ','Collapse','折叠'], actExpand:['展開','Expand','展开'], actError:['エラー','Error','错误'],
    codePreview:['コードプレビュー','Code preview','代码预览'], openInEditor:['テキストエディタで開く','Open in editor','在代码编辑器中打开'], copied:['コピーしました','Copied','已复制'], copy:['コピー','Copy','复制'], selectedBlock:['選択ブロック','Selected block','已选积木'], entireProgram:['作品全体のコード','Entire program code','全部作品代码'], copyFailed:['コピーできませんでした','Failed to copy','复制失败'], jumpToCode:['コードで位置を確認','Jump to code','在代码中查看'],
    waiting:['ブロックエディターの応答がありません。コードは保持されています。モードを切り替えて再度開いてください。','The block editor is not responding. Your code is preserved. Switch modes and open it again.','积木编辑器无响应。代码已保留。请切换模式后重新打开。'],
  };
  let language = 'ja', localeGeneration = 0, localeWork = Promise.resolve();
  const localeCache = new Map();
  const tr = key => (TEXT[key] || [key,key,key])[language === 'en' ? 1 : language === 'zh' ? 2 : 0];
  const friendlyErrorMessage = C.friendlyErrorMessage || ((msg, lang) => String(msg || ''));
  function formatErrorStatus(error) {
    if (!error) return '';
    const linePrefix = error.line != null && Number(error.line) > 0 ?
      (language === 'en' ? `Line ${error.line}: ` : language === 'zh' ? `第 ${error.line} 行: ` : `行 ${error.line}: `) : '';
    return linePrefix + friendlyErrorMessage(error.message, language);
  }
  const label = obj => typeof obj === 'string' ? obj : (obj && (obj[language] || obj.en || obj.ja) || '');
  const state = {sessionId:null,workId:null,source:'',revision:'',metadata:null,projection:null,readOnly:true,p5Version:'',soundEnabled:false,assets:[],dark:false,sourceType:'script',editable:false,pending:false,generation:0,view:null};
  let port = null, workspace = null, rootBlock = null, worker = null, workerId = 0, editId = 0, muted = 0, category = 'all', changeTimer = null, busyWork = Promise.resolve(), flushWork = Promise.resolve(), lastGood = null;
  const requests = new Map(), models = new Map(), types = new Map();
  const pendingEvents = [];
  let inputGeneration = 0;
  const paletteSources = new Map();
  const projectionIndexes=new WeakMap();
  const globalVariablesByProjection=new WeakMap();
  let presentation=null,nativeChrome=null,operationError=null,restoredSelection=null;
  state.displayPreferences={fontSize:12.5,density:'standard'};
  function projectionNode(projection,id){
    if(!projection||!id)return null;
    let index=projectionIndexes.get(projection);if(!index){index=new Map(projection.nodes.map(node=>[node.id,node]));projectionIndexes.set(projection,index);}
    return index.get(id)||null;
  }
  const hash = text => { let n=2166136261; for(let i=0;i<text.length;i++){n^=text.charCodeAt(i);n=Math.imul(n,16777619);}return (n>>>0).toString(36); };
  const fieldsOf = node => node.fields || {};
  function status(message, error) {
    if(operationError&&!error){message=operationError;error=true;}
    $('status').textContent=message;$('status').title=message;$('status').classList.toggle('error',!!error);
    $('statusDismiss').hidden=!operationError;if(error)$('announcement').textContent=message;
  }
  function failOperation(message){operationError=message||tr('rejected');status(operationError,true);}
  function clearOperationError(){operationError=null;$('statusDismiss').hidden=true;}
  function displayPreferences(value){return {fontSize:Number.isFinite(Number(value?.fontSize))?Math.max(12,Math.min(22,Number(value.fontSize))):12.5,density:value?.density==='comfortable'?'comfortable':'standard'};}
  async function applyDisplayPreferences(value,notify=true){
    const next=displayPreferences(value);if(JSON.stringify(next)===JSON.stringify(state.displayPreferences))return;
    rememberView();state.displayPreferences=next;
    if(workspace){
      const constants=workspace.getRenderer().getConstants(),height=Math.max(next.density==='comfortable'?32:22,next.fontSize+9);
      constants.FIELD_BORDER_RECT_HEIGHT=height;constants.FIELD_DROPDOWN_BORDER_RECT_HEIGHT=height;constants.MIN_BLOCK_HEIGHT=height+8;constants.EMPTY_INLINE_INPUT_HEIGHT=height+4;
      quiet(()=>{workspace.setTheme(theme());for(const block of workspace.getAllBlocks(false))block.render();});
      await Blockly.renderManagement.finishQueuedRenders();Blockly.svgResize(workspace);
      if(state.view){workspace.setScale(state.view.scale);workspace.scroll(state.view.x,state.view.y);}
    }
    if(notify)send({type:'preferences',preferences:next});
  }
  function send(message) { if(port)port.postMessage(JSON.stringify(Object.assign({sessionId:state.sessionId,workId:state.workId,expectedRevision:state.revision},message))); }
  function ensureDocument(source, revision) { if(source!==state.source||revision!==state.revision)throw new Error(tr('rejected')); }
  function newWorker() {
    worker = new Worker('worker.js');
    worker.onmessage = event => {const reply=event.data||{};const job=requests.get(reply.id);if(!job)return;requests.delete(reply.id);clearTimeout(job.timer);reply.ok?job.resolve(reply.result):job.reject(new Error(reply.error));};
    worker.onerror = () => {worker?.terminate();worker=null;state.generation++;for(const job of requests.values()){clearTimeout(job.timer);job.reject(new Error(tr('waiting')));}requests.clear();state.editable=false;state.loading=false;applyReadOnly();status(tr('waiting'),true);};
  }
  function ask(type, values) {
    if(!worker)newWorker();
    return new Promise((resolve,reject)=>{const id=++workerId;const timer=setTimeout(()=>{worker?.terminate();worker=null;state.generation++;for(const job of requests.values()){clearTimeout(job.timer);job.reject(new Error(tr('waiting')));}requests.clear();state.editable=false;state.loading=false;applyReadOnly();status(tr('waiting'),true);},15000);requests.set(id,{resolve,reject,timer});worker.postMessage(Object.assign({id,type},values));});
  }
  function options(metadata) {return {sourceType:state.sourceType,p5Version:state.p5Version,metadata:metadata||null};}
  function quiet(action) {muted++;Blockly.Events.disable();try{return action();}finally{Blockly.Events.enable();muted--;}}
  function localizedUI() {
    document.documentElement.lang=language;document.body.classList.toggle('dark',state.dark);
    $('editor').setAttribute('aria-label',language==='ja'?'ブロックエディター':language==='zh'?'积木编辑器':'Block editor');
    $('workspace').setAttribute('aria-label',language==='ja'?'作品のブロック':language==='zh'?'作品积木':'Work blocks');
    $('palette').setAttribute('aria-label',tr('search'));$('categories').setAttribute('aria-label',language==='ja'?'カテゴリー':language==='zh'?'分类':'Categories');
    $('searchLabel').textContent=tr('search');$('search').placeholder=tr('search');
    for(const id of ['undo','redo','fit','runtime','help','codePreview']){if($(id)){$(id).title=tr(id);$(id).setAttribute('aria-label',tr(id));}}
    $('paletteToggleLabel').textContent=tr('add');$('paletteToggle').title=tr('search');
    $('more').title=tr('more');$('more').setAttribute('aria-label',tr('more'));
    $('examplesLabel').textContent=tr('examples');$('runtimeLabel').textContent=tr('runtime');$('helpLabel').textContent=tr('help');$('zoomResetLabel').textContent=tr('zoomReset');
    $('paletteClose').title=tr('close');$('paletteClose').setAttribute('aria-label',tr('close'));
    if($('codePreview')){$('codePreview').title=tr('codePreview');$('codePreview').setAttribute('aria-label',tr('codePreview'));}
    if($('codeDialogClose')){$('codeDialogClose').title=tr('close');$('codeDialogClose').setAttribute('aria-label',tr('close'));}
    $('dialogCancel').textContent=tr('cancel');
    updateFullscreenControl();
    presentation?.localize();
    renderPalette();
    if(actionBlock)showBlockActions(actionBlock);
  }
  async function loadLocale() {
    const generation=++localeGeneration,requestedLanguage=language;
    localeWork=localeWork.catch(()=>{}).then(async()=>{if(localeCache.has(requestedLanguage)){Blockly.setLocale(localeCache.get(requestedLanguage));return;}await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='vendor/msg/'+(requestedLanguage==='zh'?'zh-hans':requestedLanguage)+'.js';script.onload=resolve;script.onerror=()=>reject(new Error(tr('waiting')));document.head.appendChild(script);});localeCache.set(requestedLanguage,Object.assign({},Blockly.Msg));});
    await localeWork;
    if(generation!==localeGeneration)return;
    localizedUI();
  }
  function updateFullscreenControl() {
    const button=$('fullscreen'),text=tr(state.fullscreen?'exitFullscreen':'fullscreen');$('fullscreenLabel').textContent=text;
    button.querySelector('use').setAttribute('href',state.fullscreen?'#i-split':'#i-fullscreen');
    button.title=text;button.setAttribute('aria-label',text);
    button.setAttribute('aria-pressed',String(!!state.fullscreen));
  }
  // The native host sends the app's Material colour scheme. Only plain #RRGGBB
  // values are accepted, then exposed as CSS variables shared by the chrome and Blockly.
  const THEME_VARS={background:'--bg',surface:'--surface',surfaceContainerLow:'--surface-low',surfaceContainer:'--surface-container',surfaceContainerHigh:'--surface-high',surfaceContainerHighest:'--surface-highest',surfaceContainerLowest:'--surface-lowest',onSurface:'--text',onSurfaceVariant:'--muted',outline:'--outline',outlineVariant:'--outline-variant',primary:'--primary',onPrimary:'--on-primary',primaryContainer:'--primary-container',onPrimaryContainer:'--on-primary-container',secondaryContainer:'--secondary-container',onSecondaryContainer:'--on-secondary-container',error:'--error',errorContainer:'--error-container',onErrorContainer:'--on-error-container'};
  let appliedTheme='';
  function applyAppTheme(colors){
    const valid=colors&&typeof colors==='object'?Object.fromEntries(Object.entries(colors).filter(([key,value])=>THEME_VARS[key]&&/^#[0-9a-f]{6}$/i.test(String(value)))):{};
    const signature=JSON.stringify(valid);if(signature===appliedTheme)return;appliedTheme=signature;
    // Variables live on <body> so they override both the light and `.dark` defaults.
    const root=document.body.style;for(const name of Object.values(THEME_VARS))root.removeProperty(name);
    root.removeProperty('--scrollbar');
    for(const [key,value]of Object.entries(valid))root.setProperty(THEME_VARS[key],value);
    // The workspace uses the editor surface; scrollbars follow the outline colour.
    if(valid.surface)root.setProperty('--bg',valid.surface);
    if(valid.outline)root.setProperty('--scrollbar',valid.outline+'99');
  }
  const cssVar=name=>getComputedStyle(document.body).getPropertyValue(name).trim();
  function theme() {const bg=cssVar('--bg')||'#f2efe7',primary=cssVar('--primary')||'#9f4029',text=cssVar('--text')||'#222';return Blockly.Theme.defineTheme('rin_'+hash(appliedTheme+'|'+state.dark+'|'+state.displayPreferences.fontSize),{base:Blockly.Themes.Classic,componentStyles:{workspaceBackgroundColour:bg,toolboxBackgroundColour:bg,flyoutBackgroundColour:bg,flyoutOpacity:1,scrollbarColour:cssVar('--outline')||'#81776d',insertionMarkerColour:primary,insertionMarkerOpacity:.45,cursorColour:text},fontStyle:{family:'system-ui, -apple-system, "Noto Sans JP", Roboto, sans-serif',size:state.displayPreferences.fontSize}});}
  // A flat Edit:RiN renderer. Retain Blockly's connection geometry and hit testing
  // while giving statements and values a quieter silhouette and finger-sized fields.
  class RinRenderer extends Blockly.thrasos.Renderer {
    makeConstants_() {
      const constants=super.makeConstants_();
      constants.CORNER_RADIUS=8;
      constants.NOTCH_HEIGHT=4;
      constants.NOTCH_WIDTH=12;
      constants.TAB_WIDTH=6;
      constants.ADD_START_HATS=false;
      const roomy=state.displayPreferences.density==='comfortable',height=Math.max(roomy?32:22,state.displayPreferences.fontSize+9);
      constants.MIN_BLOCK_HEIGHT=height+8;
      constants.FIELD_BORDER_RECT_HEIGHT=height;
      constants.FIELD_BORDER_RECT_RADIUS=11;
      constants.FIELD_BORDER_RECT_X_PADDING=8;
      constants.FIELD_DROPDOWN_BORDER_RECT_HEIGHT=height;
      constants.EMPTY_INLINE_INPUT_HEIGHT=height+4;
      return constants;
    }
  }
  Blockly.blockRendering.register('edit_rin',RinRenderer);
  function initWorkspace() {
    resizeViewport();
    if(workspace)return;
    Blockly.Blocks.rin_program={init:function(){this.appendDummyInput().appendField(tr('program'));this.appendStatementInput('BODY');this.setColour('#454b62');this.setDeletable(false);this.setMovable(true);this.setEditable(false);this.setTooltip(tr('orphan'));}};
    workspace=Blockly.inject('workspace',{renderer:'edit_rin',theme:theme(),media:'vendor/media/',sounds:false,trashcan:true,maxTrashcanContents:0,scrollbars:true,zoom:{controls:false,wheel:true,pinch:true,startScale:1,maxScale:2,minScale:.3,scaleSpeed:1.15},move:{scrollbars:true,drag:true,wheel:true},readOnly:false,maxBlocks:2500,oneBasedIndex:false,toolbox:null});
    // Keep the original draggable handle rectangles. A transparent gradient paints
    // only their centre stripe while preserving the whole touch-sized drag target.
    const svg=workspace.getParentSvg(),ns='http://www.w3.org/2000/svg';
    let defs=svg.querySelector('defs');if(!defs){defs=document.createElementNS(ns,'defs');svg.prepend(defs);}
    const edge=(1-6/(Blockly.Scrollbar.scrollbarThickness-5))*50;
    for(const axis of ['horizontal','vertical']){
      const id='rin_scrollbar_'+hash(workspace.id)+'_'+axis,gradient=document.createElementNS(ns,'linearGradient');
      gradient.id=id;gradient.setAttribute('x1','0%');gradient.setAttribute('y1','0%');
      gradient.setAttribute('x2',axis==='vertical'?'100%':'0%');gradient.setAttribute('y2',axis==='horizontal'?'100%':'0%');
      for(const [offset,opacity]of [[0,0],[edge,0],[edge,1],[100-edge,1],[100-edge,0],[100,0]]){
        const stop=document.createElementNS(ns,'stop');stop.setAttribute('offset',offset+'%');
        stop.setAttribute('stop-opacity',String(opacity));stop.style.stopColor='var(--rin-scrollbar-colour)';gradient.appendChild(stop);
      }
      defs.appendChild(gradient);$('workspace').style.setProperty('--rin-scrollbar-'+axis+'-fill','url(#'+id+')');
    }
    // Zoom uses pinch, the wheel, "Fit" and the menu's 100% reset; there are no
    // on-canvas zoom buttons. The trash is shown only while a block is dragged.
    workspace.addChangeListener(onChange);
    workspace.addChangeListener(onUiEvent);
    installTouchHold();
    new ResizeObserver(()=>Blockly.svgResize(workspace)).observe($('workspace'));
    for(const id of ['undoWorkspace','redoWorkspace','blockDuplicate','blockDisable','blockComment','blockInline','blockCollapseExpand','collapseWorkspace','expandWorkspace','commentCreate','commentDuplicate']){try{Blockly.ContextMenuRegistry.registry.unregister(id);}catch(_){}}
    Blockly.ContextMenuRegistry.registry.register({id:'rin_undo',scopeType:Blockly.ContextMenuRegistry.ScopeType.WORKSPACE,displayText:()=>tr('undo'),preconditionFn:()=>state.pending?'disabled':'enabled',callback:()=>historyAction('undo'),weight:1});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_redo',scopeType:Blockly.ContextMenuRegistry.ScopeType.WORKSPACE,displayText:()=>tr('redo'),preconditionFn:()=>state.pending?'disabled':'enabled',callback:()=>historyAction('redo'),weight:2});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_edit_items',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>structureLabel(),preconditionFn:scope=>state.editable&&!state.pending&&canStructure(nodeFor(scope.block))?'enabled':'hidden',callback:scope=>editStructure(scope.block).catch(error=>rollback(error.message)),weight:0});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_duplicate',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>language==='ja'?'部品として複製':language==='zh'?'复制为未连接积木':'Duplicate as detached block',preconditionFn:scope=>state.editable&&!state.pending&&models.has(scope.block.id)?'enabled':'hidden',callback:scope=>duplicateBlock(scope.block).catch(error=>rollback(error.message)),weight:1});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_edit_source',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>tr('code'),preconditionFn:scope=>models.get(scope.block.id)?.bound?'enabled':'hidden',callback:scope=>openCode(scope.block),weight:2});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_choose_color',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>language==='ja'?'色を選ぶ':language==='zh'?'选择颜色':'Choose color',preconditionFn:scope=>state.editable&&findColorTarget(scope.block)?'enabled':'hidden',callback:scope=>chooseColor(findColorTarget(scope.block)||scope.block),weight:3});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_choose_variable',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>language==='ja'?'この場所で使える名前':language==='zh'?'此处可用的名称':'Names available here',preconditionFn:scope=>state.editable&&nodeFor(scope.block)?.kind==='identifier'&&nodeFor(scope.block).visibleNames?.length?'enabled':'hidden',callback:scope=>chooseVisibleName(scope.block),weight:4});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_adjust_number',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:()=>language==='ja'?'数値を調整':language==='zh'?'调整数值':'Adjust number',preconditionFn:scope=>state.editable&&nodeFor(scope.block)?.valueType==='number'?'enabled':'hidden',callback:scope=>adjustNumber(scope.block),weight:3});
    Blockly.ContextMenuRegistry.registry.register({id:'rin_toggle_collapse',scopeType:Blockly.ContextMenuRegistry.ScopeType.BLOCK,displayText:scope=>scope.block.isCollapsed()?(language==='ja'?'展開する':language==='zh'?'展开':'Expand'):(language==='ja'?'折りたたむ':language==='zh'?'折叠':'Collapse'),preconditionFn:scope=>state.editable&&!state.pending&&models.has(scope.block.id)?'enabled':'hidden',callback:scope=>scope.block.setCollapsed(!scope.block.isCollapsed()),weight:5});
  }
  const words=(ja,en,zh)=>language==='en'?en:language==='zh'?zh:ja;
  // Touch: a block moves only after the finger has rested on it briefly. Moving
  // sooner pans the workspace, so brushing a block while scrolling cannot move it.
  // Blockly decides block-vs-workspace drag once, when the drag radius is exceeded,
  // by asking the selected block whether it is movable.
  function safeVibrate(pattern=[15]){try{if(typeof navigator!=='undefined'&&typeof navigator.vibrate==='function')navigator.vibrate(pattern);}catch(_){}}
  const TOUCH_HOLD_MS=220,TOUCH_DRAG_RADIUS=10;
  let touchLocked=false,touchHold=null,grabBlock=null,actionBlock=null,selectedBlockId=null,paletteSelectionId=null,pendingPaletteSelection;
  function selectedBlock(){
    if(!$('palette').hidden)return paletteSelectionId?workspace?.getBlockById(paletteSelectionId)||null:null;
    const focused=Blockly.getSelected();
    if(focused?.workspace===workspace)return models.has(focused.id)?focused:null;
    return selectedBlockId?workspace?.getBlockById(selectedBlockId)||null:null;
  }
  function clearGrab(){grabBlock?.getSvgRoot?.()?.classList.remove('rin-grab-ready');grabBlock=null;}
  function dragUnit(block){
    const parent=block?.getParent();
    return parent&&nodeFor(parent)?.kind==='expression_statement'?parent:block;
  }
  function installTouchHold(){
    Blockly.config.dragRadius=TOUCH_DRAG_RADIUS;
    const movable=Blockly.BlockSvg.prototype.isMovable;
    Blockly.BlockSvg.prototype.isMovable=function(){return !touchLocked&&movable.call(this);};
    const rightClick=Blockly.Gesture.prototype.handleRightClick;
    Blockly.Gesture.prototype.handleRightClick=function(event){
      if(event?.pointerType==='touch'&&this.targetBlock)return;
      return rightClick.call(this,event);
    };
    const dragWorkspace=Blockly.Gesture.prototype.updateIsDraggingWorkspace;
    const setStartBlock=Blockly.Gesture.prototype.setStartBlock;
    Blockly.Gesture.prototype.setStartBlock=function(block){
      setStartBlock.call(this,block);
      if(this.targetBlock)this.setTargetBlock(dragUnit(this.targetBlock));
    };
    Blockly.Gesture.prototype.updateIsDraggingWorkspace=function(){
      clearGrab();hideBlockActions();
      if(touchLocked&&this.targetBlock)this.targetBlock.unselect?.();
      return dragWorkspace.call(this);
    };
    const fieldClick=Blockly.Gesture.prototype.doFieldClick;
    Blockly.Gesture.prototype.doFieldClick=function(){
      if(this.startBlock&&this.startBlock!==rootBlock)showBlockActions(this.startBlock);
      return fieldClick.call(this);
    };
    const release=event=>{if(!touchHold||event.pointerId!==touchHold.id)return;clearTimeout(touchHold.timer);touchHold=null;touchLocked=false;clearGrab();};
    $('workspace').addEventListener('pointerdown',event=>{
      if(event.pointerType!=='touch')return;
      if(!event.isPrimary){if(touchHold)clearTimeout(touchHold.timer);return;} // Pinch keeps blocks still.
      if(touchHold)clearTimeout(touchHold.timer);clearGrab();
      const element=event.target instanceof Element?event.target.closest('.blocklyDraggable[data-id]'):null;
      const block=dragUnit(element&&workspace?.getBlockById(element.getAttribute('data-id')));
      if(!block){touchHold=null;touchLocked=false;return;}
      touchLocked=true;
      const hold={id:event.pointerId,x:event.clientX,y:event.clientY,block,moved:false};
      hold.timer=setTimeout(()=>{if(touchHold!==hold||hold.moved)return;touchLocked=false;if(block.workspace===workspace&&!block.isDisposed?.()&&block.isMovable()){grabBlock=block;block.getSvgRoot()?.classList.add('rin-grab-ready');safeVibrate([15]);}},TOUCH_HOLD_MS);
      touchHold=hold;
    },true);
    addEventListener('pointermove',event=>{if(touchHold&&event.pointerId===touchHold.id&&!touchHold.moved&&touchLocked&&Math.hypot(event.clientX-touchHold.x,event.clientY-touchHold.y)>TOUCH_DRAG_RADIUS){touchHold.moved=true;clearTimeout(touchHold.timer);}},true);
    addEventListener('pointerup',release,true);addEventListener('pointercancel',release,true);
  }
  function onUiEvent(event){
    const E=Blockly.Events;
    if(event.type===E.VIEWPORT_CHANGE){presentation?.scheduleView();return;}
    if(event.type===E.BLOCK_MOVE){if(!muted&&event.newParentId&&(event.newParentId!==event.oldParentId||(event.newInputName&&event.newInputName!==event.oldInputName)))safeVibrate([15]);return;}
    if(event.type===E.BLOCK_DRAG){document.body.classList.toggle('rin-dragging',!!event.isStart);if(event.isStart){hideBlockActions();clearGrab();}return;}
    if(event.type===E.CLICK){if(event.targetType==='block'){const block=workspace.getBlockById(event.blockId);if(block&&block!==rootBlock)showBlockActions(block);else{selectedBlockId=null;hideBlockActions();}}else {selectedBlockId=null;hideBlockActions();}return;}
    if(event.type===E.SELECTED){
      const block=workspace.getBlockById(event.newElementId);
      if(block)selectedBlockId=models.has(block.id)?block.id:null;
      presentation?.scheduleView();
      if(actionBlock&&event.newElementId!==actionBlock.id)hideBlockActions();
      return;
    }
    if(event.type===E.BLOCK_DELETE&&(event.ids||[event.blockId]).includes(selectedBlockId))selectedBlockId=null;
    if(event.type===E.BLOCK_DELETE&&actionBlock&&(event.ids||[event.blockId]).includes(actionBlock.id))hideBlockActions();
  }
  function hideBlockActions(){actionBlock=null;const bar=$('blockActions');if(!bar.hidden){bar.hidden=true;bar.replaceChildren();}if(state.errors?.length)status(formatErrorStatus(state.errors[0]),true);}
  const COLOR_CALLS=['fill','stroke','background','color','ambientMaterial','specularMaterial'];
  function normalizeHex(hex){
    let h=String(hex||'').trim();
    if(!h.startsWith('#'))h='#'+h;
    if(/^#[0-9a-f]{3}$/i.test(h))h='#'+h.slice(1).split('').map(c=>c+c).join('');
    return /^#[0-9a-f]{6}$/i.test(h)?h.toLowerCase():'#6c5ce7';
  }
  function hexToRgb(hex){
    const h=normalizeHex(hex);
    const m=/^#([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(h);
    return m?{r:parseInt(m[1],16),g:parseInt(m[2],16),b:parseInt(m[3],16)}:{r:108,g:92,b:231};
  }
  function rgbToHex(r,g,b){
    const clamp=v=>Math.max(0,Math.min(255,Math.round(v)));
    return '#'+[clamp(r),clamp(g),clamp(b)].map(x=>x.toString(16).padStart(2,'0')).join('');
  }
  function colorSwatchFor(target){
    if(!target)return '#6c5ce7';
    if(target.getField?.('F_value'))return normalizeHex(target.getFieldValue('F_value'));
    const node=nodeFor(target);
    if(node?.colorSwatch)return normalizeHex(node.colorSwatch);
    if(node&&['p5_call','call'].includes(node.kind)){
      const args=node.inputs?.args||[];
      if(args.length===1||args.length===2){
        const child=target.getInputTargetBlock?.('I_args_0');
        const childNode=child&&nodeFor(child);
        const val=childNode?.fields?.value?.value;
        if(typeof val==='number'){
          const n=Math.max(0,Math.min(255,Math.round(val)));
          return rgbToHex(n,n,n);
        }
      }else if(args.length>=3){
        const getNum=idx=>{
          const child=target.getInputTargetBlock?.('I_args_'+idx);
          const childNode=child&&nodeFor(child);
          const val=Number(childNode?.fields?.value?.value);
          return Number.isFinite(val)?Math.max(0,Math.min(255,Math.round(val))):0;
        };
        return rgbToHex(getNum(0),getNum(1),getNum(2));
      }
    }
    return '#6c5ce7';
  }
  function findColorTarget(block){
    if(!block)return null;
    const node=nodeFor(block);if(!node)return null;
    if(node.kind==='expression_statement'){
      const expr=block.getInputTargetBlock?.('I_expression');
      return expr?findColorTarget(expr):null;
    }
    if(isColor(node,'value')&&block.getField('F_value'))return block;
    if(['p5_call','call'].includes(node.kind)){
      for(let i=0;i<4;i++){
        const child=block.getInputTargetBlock?.('I_args_'+i);
        if(child){
          const childNode=nodeFor(child);
          if(childNode&&isColor(childNode,'value')&&child.getField('F_value'))return child;
        }
      }
      if(node.p5&&COLOR_CALLS.includes(node.calleeName))return block;
    }
    return null;
  }
  function showBlockActions(block){
    if(!workspace||!block||block.workspace!==workspace||block.isDisposed?.()||block===rootBlock||!models.has(block.id)){hideBlockActions();return;}
    const node=nodeFor(block),model=modelFor(block);if(!node){hideBlockActions();return;}
    selectedBlockId=block.id;
    const editable=state.editable&&!state.pending&&!state.processing&&!state.loading,items=[];
    const add=(icon,key,run,extra)=>items.push(Object.assign({icon,key,run},extra));

    const blockError=block.rinRuntimeError||(state.errors||[]).find(e=>{
      return node?.loc&&e.line>=node.loc.start.line&&e.line<=node.loc.end.line;
    });
    if(blockError){
      status(formatErrorStatus(blockError),true);
      const errLineLabel=blockError.line?(language==='ja'?`行 ${blockError.line}`:language==='zh'?`第 ${blockError.line} 行`:`Line ${blockError.line}`):tr('error');
      add('i-error','actError',()=>showErrorDetail(block,blockError),{
        danger:true,
        customText:errLineLabel,
        isErrorAction:true
      });
    }else if(state.errors?.length){
      status(formatErrorStatus(state.errors[0]),true);
    }else if($('status')?.classList.contains('error')){
      const needsSound=!state.soundEnabled&&state.projection?.nodes.some(n=>n.requirements?.sound);
      status(state.readOnly?tr('readOnly'):needsSound?tr('sound'):tr('ready')+' '+tr('orphan'),false);
    }

    if(editable&&canStructure(node))add('i-items','actItems',()=>editStructure(block).catch(error=>rollback(error.message)));
    const colorTarget=findColorTarget(block);
    if(editable&&colorTarget)add(null,'actColor',()=>chooseColor(colorTarget),{swatch:colorSwatchFor(colorTarget)});
    if(editable&&node.valueType==='number'&&block.getField('F_value'))add('i-tune','actNumber',()=>adjustNumber(block));
    if(editable&&node.kind==='identifier'&&node.visibleNames?.length)add('i-rename','actName',()=>chooseVisibleName(block));
    if(model.bound)add('i-code','actCode',()=>showCodePreview(block));
    if(model.bound&&node.kind.startsWith('raw_'))add('i-code','code',()=>jumpToCode(block));
    if(editable)add('i-duplicate','actDuplicate',()=>duplicateBlock(block).catch(error=>rollback(error.message)));
    const hasBody=Object.keys(node.inputs||{}).some(key=>node.inputKinds?.[key]==='statement');
    if(editable&&(hasBody||block.isCollapsed()))add(block.isCollapsed()?'i-unfold':'i-fold',block.isCollapsed()?'actExpand':'actCollapse',()=>{block.setCollapsed(!block.isCollapsed());showBlockActions(block);});
    if(editable&&block.isDeletable())add('i-delete','actDelete',()=>{hideBlockActions();block.checkAndDelete();},{danger:true});
    const bar=$('blockActions');if(!items.length){hideBlockActions();return;}
    actionBlock=block;bar.setAttribute('aria-label',caption(node));
    bar.replaceChildren(...items.map(item=>{
      const button=document.createElement('button');button.type='button';if(item.danger)button.className='danger';
      if(item.isErrorAction)button.classList.add('error-action');
      if(item.icon){const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'),use=document.createElementNS('http://www.w3.org/2000/svg','use');svg.setAttribute('class','icon');use.setAttribute('href','#'+item.icon);svg.appendChild(use);button.appendChild(svg);}
      else{const swatch=document.createElement('i');swatch.className='swatch';swatch.style.background=item.swatch;button.appendChild(swatch);}
      const text=document.createElement('span');text.textContent=item.customText||tr(item.key);button.appendChild(text);button.title=item.customText?`${item.customText}: ${tr(item.key)}`:tr(item.key);
      button.onclick=()=>{if(actionBlock!==block)return;item.run();};
      return button;
    }));
    bar.hidden=false;
  }
  async function showErrorDetail(block,error){
    if(!error)return;
    const errorSource=state.source,errorRevision=state.revision,errorWorkId=state.workId;
    const dialog=$('actionDialog');
    const lineText=error.line?(language==='ja'?`行 ${error.line} のエラー`:language==='zh'?`第 ${error.line} 行错误`:`Line ${error.line} Error`):tr('error');
    $('dialogTitle').textContent=lineText;
    const friendly=friendlyErrorMessage(error.message,language);
    const rawMsg=String(error.message||'').trim();
    const detailLabel=language==='en'?'[Details]':language==='zh'?'[详细信息]':'[詳細]';
    $('dialogDescription').textContent=`${friendly}\n\n${detailLabel}\n${rawMsg}`;
    $('dialogChoices').replaceChildren();

    const jumpBtn=document.createElement('button');
    jumpBtn.type='button';
    jumpBtn.className='filled';
    jumpBtn.textContent=tr('jumpToCode');
    jumpBtn.onclick=()=>{
      dialog.close('jump');
      if(errorSource!==state.source||errorRevision!==state.revision||errorWorkId!==state.workId){status(tr('rejected'),true);return;}
      jumpToCode(block,error);
    };
    $('dialogChoices').appendChild(jumpBtn);

    const prevCancelText=$('dialogCancel').textContent;
    $('dialogCancel').textContent=tr('close');

    return new Promise(resolve=>{
      const done=()=>{
        dialog.removeEventListener('close',done);
        $('dialogCancel').textContent=prevCancelText;
        resolve();
      };
      dialog.addEventListener('close',done);
      dialog.returnValue='cancel';
      dialog.showModal();
    });
  }
  async function chooseVisibleName(block){
    const revision=state.revision,node=nodeFor(block);if(!node?.visibleNames?.length)return;
    await choose(words('名前を選ぶ','Choose name','选择名称'),'',node.visibleNames.map(name=>({value:name,label:name})),name=>{
      if(revision===state.revision&&block.workspace===workspace&&workspace.getBlockById(block.id)===block&&state.editable&&!state.pending&&!state.processing&&!state.loading)block.setFieldValue(name,'F_name');
    });
  }
  const structureLabel=()=>words('項目を編集','Edit items','编辑项目');
  function canStructure(node){return node&&(['array','if','function','call','p5_call','new'].includes(node.kind)||node.kind==='identifier'&&node.renameable);}
  async function textChoice(title,value){const dialog=$('actionDialog');$('dialogTitle').textContent=title;$('dialogDescription').textContent='';$('dialogChoices').replaceChildren();const input=document.createElement('input');input.type='text';input.value=value;input.setAttribute('aria-label',title);input.autocomplete='off';input.spellcheck=false;let result=null;const button=document.createElement('button');button.type='button';button.className='filled';button.textContent=words('適用','Apply','应用');button.onclick=()=>{if(input.value.trim()){result=input.value.trim();dialog.close('apply');}};$('dialogChoices').append(input,button);return new Promise(resolve=>{const done=()=>{dialog.removeEventListener('close',done);resolve(result);};dialog.addEventListener('close',done);dialog.returnValue='cancel';dialog.showModal();input.focus();input.select();});}
  async function semanticEdit(block,operation,source=state.source,revision=state.revision){
    ensureDocument(source,revision);const model=modelFor(block);if(!model||!state.editable||state.pending)throw new Error(tr('unsupported'));
    const result=await ask('proposePatch',{projection:model.bound?state.projection:model.projection,operations:operation});if(!result.ok)throw new Error(authoringError(result));
    let metadata=await snapshotMetadata();
    if(!model.bound){const top=block.getRootBlock(),meta=JSON.parse(metadata),id=top.rinOrphanId||top.id,entry=meta.orphans.find(o=>o.id===id);if(!entry)throw new Error(tr('unsupported'));entry.source=chain(top).map(part=>{const path=nodeFor(part)?.path,node=result.projection.nodes.find(n=>n.path===path);if(!node)throw new Error(tr('unsupported'));return node.raw;}).join('\n');metadata=JSON.stringify(meta);}
    await submit(model.bound?result.patches:[],metadata,source,revision);
  }
  async function editStructure(block){
    if(!state.editable||state.pending||!await flushAll())return;
    const model=modelFor(block),node=nodeFor(block);if(!model||!canStructure(node))return;
    const source=state.source,revision=state.revision,choices=[];
    if(['call','p5_call','new','array'].includes(node.kind)){
      const input=node.kind==='array'?'elements':'args',ids=node.inputs[input]||[];
      choices.push({value:'add',icon:'add',label:words((input==='elements'?'項目':'渡す値')+'を追加','Add '+(input==='elements'?'item':'argument'),'添加'+(input==='elements'?'元素':'参数'))});
      ids.forEach((id,i)=>choices.push({value:'remove:'+i,icon:'remove',label:words((i+1)+'番目を削除','Remove item '+(i+1),'删除第 '+(i+1)+' 项')}));
      if(node.kind==='call'&&simpleCallee(node))choices.push({value:'call',icon:'function',label:words('呼び出す関数を選ぶ','Choose function','选择函数')});
    }
    if(node.kind==='if')choices.push({value:node.inputs.alternate?'removeElse':'addElse',icon:node.inputs.alternate?'remove':'add',label:node.inputs.alternate?words('「そうでなければ」を削除','Remove else','删除否则'):words('「そうでなければ」を追加','Add else','添加否则')});
    if(node.kind==='function'&&!node.event&&node.parametersEditable){choices.push({value:'addParam',icon:'add',label:words('受け取る値を追加','Add parameter','添加形参')});(node.parameters||[]).forEach((param,i)=>{choices.push({value:'renameParam:'+i,icon:'rename',label:words(param.name+' の名前を変更','Rename '+param.name,'重命名 '+param.name)},{value:'removeParam:'+i,icon:'remove',label:words(param.name+' を削除','Remove '+param.name,'删除 '+param.name)});});}
    if(node.kind==='identifier'&&node.renameable||node.kind==='function'&&!node.event)choices.push({value:'rename',icon:'rename',label:words('使っている場所もまとめて名前を変更','Rename here and all matching uses','重命名及其所有引用')});
    if(!choices.length){await openCode(block);return;}
    const action=await choose(structureLabel(),words('この作品の sketch.js 内に反映します。元に戻す操作で戻せます。','Changes apply within sketch.js. Undo restores them.','更改应用于 sketch.js。可撤销。'),choices);if(action==='cancel')return;ensureDocument(source,revision);
    let op;
    if(action==='add')op={type:'insertInput',id:node.id,input:node.kind==='array'?'elements':'args',source:'0'};
    if(action.startsWith('remove:')){const index=Number(action.split(':')[1]);const confirm=await choose(words('項目を削除','Remove item','删除项目'),words('この値や処理を削除します。元に戻す操作で戻せます。','This removes the value and its expression. Undo restores it.','删除此值及其表达式。可撤销。'),[{value:'remove',label:words('削除する','Remove','删除')}]);if(confirm!=='remove')return;op={type:'removeInput',id:node.id,input:node.kind==='array'?'elements':'args',index};}
    if(action==='addElse')op={type:'setInput',id:node.id,input:'alternate',source:'{\n}'};
    if(action==='removeElse'){if(await choose(words('そうでなければを削除','Remove else','删除否则'),words('中にある処理も削除します。元に戻す操作で戻せます。','The statements inside will also be removed. Undo restores them.','其中的语句也会删除。可撤销。'),[{value:'remove',label:words('削除する','Remove','删除')}])!=='remove')return;op={type:'setInput',id:node.id,input:'alternate',source:''};}
    if(action==='rename'||action.startsWith('renameParam:')){const index=action==='rename'?null:Number(action.split(':')[1]),name=await textChoice(words('新しい名前','New name','新名称'),index==null?node.fields.name.value:node.parameters[index].name);if(name==null)return;op={type:'renameBinding',id:node.id,name};if(index!=null)op.parameterIndex=index;}
    if(action==='addParam'){let name='value',i=2;while((node.parameters||[]).some(p=>p.name===name))name='value'+i++;name=await textChoice(words('受け取る値の名前','Parameter name','形参名称'),name);if(name==null)return;op={type:'insertParameter',id:node.id,name,argument:'0'};}
    if(action.startsWith('removeParam:')){if(await choose(words('受け取る値を削除','Remove parameter','删除形参'),words('呼び出す場所で渡している値も削除します。処理内で使っている名前は削除できません。','Matching arguments at calls will also be removed. A parameter used in the body cannot be removed.','也会删除调用处对应的实参。函数内仍使用的形参无法删除。'),[{value:'remove',label:words('削除する','Remove','删除')}])!=='remove')return;op={type:'removeParameter',id:node.id,index:Number(action.split(':')[1])};}
    if(action==='call'){const available=(model.bound?state.projection:model.projection).nodes.filter(n=>(node.functionChoices||[]).some(choice=>choice.id===n.id)&&!n.event&&n.parametersEditable);const items=available.filter((n,i)=>available.findIndex(other=>other.fields.name.value===n.fields.name.value)===i);if(!items.length){status(words('先に「自分の関数」を作品に追加してください。','Add a custom function to the work first.','请先添加自定义函数。'),true);return;}const id=await choose(words('呼び出す関数','Function to call','调用函数'),'',items.map(fn=>({value:fn.id,label:fn.fields.name.value+'('+(fn.parameters||[]).map(p=>p.name).join(', ')+')'})));if(id==='cancel')return;const fn=items.find(n=>n.id===id);op={type:'setCall',id:node.id,callee:fn.fields.name.value,args:(fn.parameters||[]).map((_,i)=>(model.bound?state.projection:model.projection).nodes.find(n=>n.id===node.inputs.args[i])?.raw||'0')};}
    ensureDocument(source,revision);if(op)await semanticEdit(block,op,source,revision);
  }
  function modelFor(block) {return block?models.get(block.id):null;}
  function nodeFor(block) {const model=modelFor(block);return model?projectionNode(model.projection,model.nodeId):null;}
  async function jumpToCode(block, error = null) {
    const model=modelFor(block);
    if(error){
      const workId=state.workId,source=state.source,revision=state.revision;
      if(!await flushAll()||workId!==state.workId||source!==state.source||revision!==state.revision)return;
      const line=Number(error.line),lines=source.split('\n');
      if(!Number.isInteger(line)||line<1||line>lines.length){status(tr('rejected'),true);return;}
      let start=0;for(let i=0;i<line-1;i++)start+=lines[i].length+1;
      send({type:'openCode',start,end:start+lines[line-1].length});
      return;
    }
    if(!model?.bound){
      const workId=state.workId,source=state.source,revision=state.revision;
      if(!await flushAll()||workId!==state.workId||source!==state.source||revision!==state.revision)return;
      send({type:'openCode',start:0,end:0});
      return;
    }
    const id=model.nodeId,workId=state.workId,source=state.source,revision=state.revision;
    // Positional node IDs can be reused when the document changes during flush.
    if(!await flushAll()||workId!==state.workId||source!==state.source||revision!==state.revision)return;
    const node=state.projection?.nodes.find(n=>n.id===id);
    if(node)send({type:'openCode',start:node.start,end:node.end});
    else status(tr('rejected'),true);
  }
  const openCode = jumpToCode;
  function showCodePreview(block=null){
    const dialog=$('codeDialog');if(!dialog)return;
    let targetNode=null,snippet='',start=0,end=0;
    if(block&&models.has(block.id)){
      const model=modelFor(block);
      if(model?.bound){
        targetNode=state.projection?.nodes.find(n=>n.id===model.nodeId);
        if(targetNode){
          start=targetNode.start;end=targetNode.end;snippet=(state.source||'').slice(start,end).trim();
          const semiMatch=state.source.slice(end).match(/^\s*;/);
          if(semiMatch){snippet+=';';end+=semiMatch[0].length;}
          else if((targetNode.statement||block?.previousConnection||!block?.outputConnection)&&!snippet.endsWith(';')){snippet+=';';}
        }
      }
      if(!snippet){const node=nodeFor(block);snippet=node?.raw?.trim()||'';}
    }
    const hasBlockSnippet=Boolean(block&&snippet);
    const scopeToggle=$('codeScopeToggle'),scopeBlock=$('codeScopeBlock'),scopeAll=$('codeScopeAll');
    function updateView(showEntire){
      if(showEntire||!hasBlockSnippet){
        $('codeDialogTitle').textContent=tr('codePreview');
        $('codeDialogSubtitle').textContent='sketch.js · '+tr('entireProgram');
        $('codeDialogContent').textContent=state.source||'// (empty)';
        if(scopeBlock)scopeBlock.setAttribute('aria-pressed','false');
        if(scopeAll)scopeAll.setAttribute('aria-pressed','true');
      }else{
        const node=nodeFor(block),nodeTitle=node?.fields?.name?.value||node?.calleeName||caption(node);
        $('codeDialogTitle').textContent=tr('codePreview');
        $('codeDialogSubtitle').textContent=tr('selectedBlock')+(nodeTitle?' ('+nodeTitle+')':'');
        $('codeDialogContent').textContent=snippet;
        if(scopeBlock)scopeBlock.setAttribute('aria-pressed','true');
        if(scopeAll)scopeAll.setAttribute('aria-pressed','false');
      }
    }
    if(hasBlockSnippet){
      scopeToggle.hidden=false;
      scopeBlock.textContent=tr('selectedBlock');
      scopeAll.textContent=tr('entireProgram');
      scopeBlock.onclick=()=>updateView(false);
      scopeAll.onclick=()=>updateView(true);
      updateView(false);
    }else{
      scopeToggle.hidden=true;
      updateView(true);
    }
    const copyBtn=$('codeDialogCopy'),copyLabel=copyBtn.querySelector('span')||copyBtn;
    copyLabel.textContent=tr('copy');
    copyBtn.onclick=async()=>{
      const textToCopy=$('codeDialogContent').textContent;
      try{
        let copied=false;
        if(navigator.clipboard?.writeText){
          try{await navigator.clipboard.writeText(textToCopy);copied=true;}catch(_){}
        }
        if(!copied){
          const ta=document.createElement('textarea');
          ta.value=textToCopy;ta.style.position='fixed';ta.style.opacity='0';
          document.body.appendChild(ta);ta.focus();ta.select();
          copied=document.execCommand('copy');
          ta.remove();
        }
        if(copied){
          copyLabel.textContent=tr('copied');
          setTimeout(()=>{copyLabel.textContent=tr('copy');},1500);
        }else{
          throw new Error('Copy failed');
        }
      }catch(_){status(tr('copyFailed'),true);}
    };
    const jumpBtn=$('codeDialogJump');
    jumpBtn.textContent=tr('openInEditor');
    jumpBtn.onclick=async()=>{
      dialog.close();
      if(block)await jumpToCode(block);
      else{if(!await flushAll())return;send({type:'openCode',start:0,end:0});}
    };
    $('codeDialogDone').textContent=tr('close');
    $('codeDialogDone').onclick=()=>dialog.close();
    $('codeDialogClose').onclick=()=>dialog.close();
    if(dialog.showModal)dialog.showModal();else dialog.open=true;
  }
  const fieldLabel = key => ({name:['名前','name','名称'],params:['引数','parameters','参数'],value:['値','value','值'],operator:['演算','operator','运算'],kind:['宣言','declaration','声明'],async:['非同期','async','异步'],text:['内容','text','内容'],code:['コード','code','代码']}[key]||[key,key,key])[language==='en'?1:language==='zh'?2:0];
  const inputLabel = key => ({body:['処理','body','语句'],test:['条件','condition','条件'],consequent:['条件成立','then','成立时'],alternate:['それ以外','else','否则'],expression:['実行','expression','表达式'],callee:['関数','function','函数'],args:['引数','arguments','参数'],left:['左','left','左'],right:['右','right','右'],argument:['値','value','值'],init:['初期値','initial','初始值'],update:['更新','update','更新'],declarations:['変数','variables','变量'],elements:['要素','items','元素'],object:['対象','object','对象'],property:['項目','property','属性'],name:['名前','name','名称']}[key]||[key,key,key])[language==='en'?1:language==='zh'?2:0];
  function simpleCallee(node){return typeof node.calleeName==='string'&&/^[\w$]+(?:\.[\w$]+)*$/.test(node.calleeName);}
  function caption(node) {
    if(node.kind==='p5_call'){const entry=C.P5_CALLS[node.calleeName];return (entry?label(entry.label):node.calleeName)+' · '+node.calleeName;}
    if(['call','new'].includes(node.kind)&&simpleCallee(node)){
      if(node.calleeName?.endsWith('.push'))return words('配列に追加 · push','Append · push','追加到数组 · push');
      if(node.calleeName?.endsWith('.pop'))return words('末尾を取り出す · pop','Remove last · pop','取出末尾 · pop');
      return (label(C.NODE_LABELS[node.kind])||node.kind)+' · '+node.calleeName;
    }
    if(node.kind==='function'&&node.event){
      const fnName=fieldsOf(node).name?.value;
      const ev=C.EVENTS.find(e=>e.name===fnName);
      const title=ev?label(ev.label):({setup:language==='en'?'Once at start':language==='zh'?'启动时':'最初に一度',draw:language==='en'?'Every frame':language==='zh'?'每一帧':'毎フレーム'}[fnName]||label(C.NODE_LABELS.function));
      return title+' · '+fnName;
    }
    if(node.kind==='member'){
      if(node.computed)return words('配列の要素 [ ]','Array item [ ]','数组元素 [ ]');
      if(node.raw?.endsWith('.length'))return words('要素数 · length','Length · length','长度 · length');
      return label(C.NODE_LABELS.member)||node.kind;
    }
    return label(C.NODE_LABELS[node.kind])||node.kind;
  }
  function argumentLabel(node,index){
    const key=C.P5_CALLS[node.calleeName]?.args?.[index]||String(index);
    return ({
      width:['幅','width','宽'],height:['高さ','height','高'],diameter:['直径','diameter','直径'],
      radius:['半径','radius','半径'],color:['色','color','颜色'],text:['文字','text','文本'],
      angle:['角度','angle','角度'],size:['大きさ','size','大小'],weight:['太さ','weight','宽度'],
      factor:['倍率','factor','倍数'],minimum:['最小','min','最小'],maximum:['最大','max','最大'],
      file:['ファイル','file','文件'],
      x1:['x1','x1','x1'],y1:['y1','y1','y1'],x2:['x2','x2','x2'],y2:['y2','y2','y2'],
      start:['始点','start','起点'],stop:['終点','stop','终点'],amount:['割合','amount','比例'],
      value:['値','value','值'],a:['値A','a','值A'],b:['値B','b','值B']
    }[key]||[key,key,key])[language==='en'?1:language==='zh'?2:0];
  }
  function getGlobalVariables() {
    if(!state.projection?.ok)return [];
    const cached=globalVariablesByProjection.get(state.projection);if(cached)return cached;
    const vars=[];const seen=new Set();
    for(const rootId of state.projection.rootIds||[]){
      const stmt=projectionNode(state.projection,rootId);
      if(stmt?.kind==='variable_declaration'&&['let','const'].includes(stmt.fields?.kind?.value)){
        const kind=stmt.fields.kind.value;
        const declarators=Array.isArray(stmt.inputs?.declarations)?stmt.inputs.declarations:[stmt.inputs?.declarations];
        for(const decId of declarators){
          const dec=projectionNode(state.projection,decId);
          if(!dec)continue;
          const nameNode=projectionNode(state.projection,dec.inputs?.name);
          const varName=nameNode?.fields?.name?.value;
          if(varName&&!seen.has(varName)){
            seen.add(varName);
            const initNode=dec.inputs?.init?projectionNode(state.projection,dec.inputs.init):null;
            vars.push({name:varName,kind,init:initNode?.raw??null});
          }
        }
      }
    }
    globalVariablesByProjection.set(state.projection,vars);return vars;
  }
  function isGlobalVariable(name){return !!name&&getGlobalVariables().some(v=>v.name===name);}
  function color(node) {
    if(node.kind==='identifier'&&!node.declaration&&isGlobalVariable(node.fields?.name?.value))return '#824d6e';
    // Restrained ink colours keep white captions readable and distinguish roles.
    return node.kind.startsWith('raw')?'#725444':node.kind==='comment'?'#586473':
      node.kind==='function'?'#585184':node.kind==='p5_call'?'#226b72':
      ['literal','identifier'].includes(node.kind)?'#715c35':
      ['if','for','for_of','block'].includes(node.kind)?'#365f8b':
      node.kind.startsWith('variable')?'#824d6e':'#4c6860';
  }
  function isColor(node, key) {return key==='value'&&node.valueType==='string'&&/^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(String(node.fields[key]?.value));}
  async function chooseColor(block){
    const source=state.source,revision=state.revision,generation=state.generation;
    const dialog=$('actionDialog');
    $('dialogTitle').textContent=language==='ja'?'色を選ぶ':language==='zh'?'选择颜色':'Choose color';
    $('dialogDescription').textContent='';
    $('dialogChoices').replaceChildren();

    const PRESETS = [
      '#000000', '#444444', '#888888', '#cccccc', '#ffffff',
      '#e74c3c', '#ff7675', '#e67e22', '#fdcb6e', '#f1c40f',
      '#2ecc71', '#00b894', '#00cec9', '#0984e3', '#3498db',
      '#6c5ce7', '#a29bfe', '#9b59b6', '#fd79a8', '#e84393'
    ];

    let currentHex = colorSwatchFor(block);
    function rgbToHsb(r,g,b){
      r/=255; g/=255; b/=255;
      const max=Math.max(r,g,b), min=Math.min(r,g,b), d=max-min;
      let h=0; const s=max===0?0:d/max, v=max;
      if(max!==min){
        if(max===r) h=(g-b)/d+(g<b?6:0);
        else if(max===g) h=(b-r)/d+2;
        else h=(r-g)/d+4;
        h/=6;
      }
      return {h:Math.round(h*360), s:Math.round(s*100), b:Math.round(v*100)};
    }
    function hsbToRgb(h,s,b){
      h=(h%360)/360; s=Math.max(0,Math.min(100,s))/100; b=Math.max(0,Math.min(100,b))/100;
      let r,g,bl;
      const i=Math.floor(h*6), f=h*6-i;
      const p=b*(1-s), q=b*(1-f*s), t=b*(1-(1-f)*s);
      switch(i%6){
        case 0: r=b; g=t; bl=p; break;
        case 1: r=q; g=b; bl=p; break;
        case 2: r=p; g=b; bl=t; break;
        case 3: r=p; g=q; bl=b; break;
        case 4: r=t; g=p; bl=b; break;
        case 5: r=b; g=p; bl=q; break;
      }
      return {r:Math.round(r*255), g:Math.round(g*255), b:Math.round(bl*255)};
    }

    const container=document.createElement('div');
    container.className='rin-color-picker';

    // 1. Preview row
    const previewRow=document.createElement('div');
    previewRow.className='rin-color-preview-row';
    const swatch=document.createElement('div');
    swatch.className='rin-color-swatch-large';
    swatch.style.background=currentHex;

    const hexInput=document.createElement('input');
    hexInput.type='text';
    hexInput.className='rin-color-hex-input';
    hexInput.value=currentHex.toUpperCase();
    hexInput.setAttribute('aria-label',words('色コード','Color hex','颜色代码'));
    hexInput.autocomplete='off';
    hexInput.spellcheck=false;
    hexInput.maxLength=7;

    const nativePicker=document.createElement('input');
    nativePicker.type='color';
    nativePicker.className='rin-color-native-input';
    nativePicker.value=currentHex;
    nativePicker.setAttribute('aria-label',$('dialogTitle').textContent);

    previewRow.append(swatch,hexInput,nativePicker);

    // 2. Presets grid
    const presetLabel=document.createElement('div');
    presetLabel.className='rin-color-section-label';
    presetLabel.textContent=words('プリセット','Presets','预设');

    const presetsGrid=document.createElement('div');
    presetsGrid.className='rin-color-presets';

    PRESETS.forEach(colorHex=>{
      const btn=document.createElement('button');
      btn.type='button';
      btn.className='rin-color-preset-btn'+(colorHex.toLowerCase()===currentHex.toLowerCase()?' active':'');
      btn.style.background=colorHex;
      btn.dataset.color=colorHex;
      btn.title=colorHex;
      btn.onclick=()=>{
        updateAllFromHex(colorHex);
      };
      presetsGrid.appendChild(btn);
    });

    // 3. Mode Toggle (RGB / HSB)
    let mode='rgb';
    const modeToggle=document.createElement('div');
    modeToggle.className='rin-color-mode-toggle';
    const rgbModeBtn=document.createElement('button');
    rgbModeBtn.type='button';
    rgbModeBtn.className='rin-color-mode-btn active';
    rgbModeBtn.textContent='RGB';
    const hsbModeBtn=document.createElement('button');
    hsbModeBtn.type='button';
    hsbModeBtn.className='rin-color-mode-btn';
    hsbModeBtn.textContent='HSB';
    modeToggle.append(rgbModeBtn,hsbModeBtn);

    // 4. Sliders Container
    const slidersContainer=document.createElement('div');
    slidersContainer.className='rin-color-sliders';

    let rgbVals=hexToRgb(currentHex);
    let hsbVals=rgbToHsb(rgbVals.r,rgbVals.g,rgbVals.b);

    function createSliderRow(labelStr,min,max,val,unit,onChange){
      const row=document.createElement('div');
      row.className='rin-color-slider-row';
      const labelEl=document.createElement('span');
      labelEl.className='rin-color-slider-label';
      labelEl.textContent=labelStr;
      const slider=document.createElement('input');
      slider.type='range';
      slider.className='rin-color-slider';
      slider.setAttribute('aria-label',labelStr);
      slider.min=String(min);
      slider.max=String(max);
      slider.value=String(val);
      const valEl=document.createElement('span');
      valEl.className='rin-color-slider-val';
      valEl.textContent=val+unit;
      slider.oninput=()=>{
        valEl.textContent=slider.value+unit;
        onChange(Number(slider.value));
      };
      row.append(labelEl,slider,valEl);
      return {row,slider,valEl};
    }

    let rSlider,gSlider,bSlider,hSlider,sSlider,brSlider;

    function renderSliders(){
      slidersContainer.replaceChildren();
      if(mode==='rgb'){
        rSlider=createSliderRow('R',0,255,rgbVals.r,'',val=>{
          rgbVals.r=val;
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          hsbVals=rgbToHsb(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        gSlider=createSliderRow('G',0,255,rgbVals.g,'',val=>{
          rgbVals.g=val;
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          hsbVals=rgbToHsb(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        bSlider=createSliderRow('B',0,255,rgbVals.b,'',val=>{
          rgbVals.b=val;
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          hsbVals=rgbToHsb(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        slidersContainer.append(rSlider.row,gSlider.row,bSlider.row);
      } else {
        hSlider=createSliderRow('H',0,360,hsbVals.h,'°',val=>{
          hsbVals.h=val;
          rgbVals=hsbToRgb(hsbVals.h,hsbVals.s,hsbVals.b);
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        sSlider=createSliderRow('S',0,100,hsbVals.s,'%',val=>{
          hsbVals.s=val;
          rgbVals=hsbToRgb(hsbVals.h,hsbVals.s,hsbVals.b);
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        brSlider=createSliderRow('B',0,100,hsbVals.b,'%',val=>{
          hsbVals.b=val;
          rgbVals=hsbToRgb(hsbVals.h,hsbVals.s,hsbVals.b);
          currentHex=rgbToHex(rgbVals.r,rgbVals.g,rgbVals.b);
          syncPreview(false);
        });
        slidersContainer.append(hSlider.row,sSlider.row,brSlider.row);
      }
    }

    function syncPreview(updateSliders=true){
      swatch.style.background=currentHex;
      hexInput.value=currentHex.toUpperCase();
      nativePicker.value=currentHex;
      presetsGrid.querySelectorAll('.rin-color-preset-btn').forEach(btn=>{
        btn.classList.toggle('active',btn.dataset.color.toLowerCase()===currentHex.toLowerCase());
      });
      if(updateSliders){
        if(mode==='rgb'&&rSlider){
          rSlider.slider.value=String(rgbVals.r); rSlider.valEl.textContent=String(rgbVals.r);
          gSlider.slider.value=String(rgbVals.g); gSlider.valEl.textContent=String(rgbVals.g);
          bSlider.slider.value=String(rgbVals.b); bSlider.valEl.textContent=String(rgbVals.b);
        } else if(mode==='hsb'&&hSlider){
          hSlider.slider.value=String(hsbVals.h); hSlider.valEl.textContent=hsbVals.h+'°';
          sSlider.slider.value=String(hsbVals.s); sSlider.valEl.textContent=hsbVals.s+'%';
          brSlider.slider.value=String(hsbVals.b); brSlider.valEl.textContent=hsbVals.b+'%';
        }
      }
    }

    function updateAllFromHex(hex){
      if(!/^#[0-9a-f]{6}$/i.test(hex))return;
      currentHex=hex.toLowerCase();
      rgbVals=hexToRgb(currentHex);
      hsbVals=rgbToHsb(rgbVals.r,rgbVals.g,rgbVals.b);
      syncPreview(true);
    }

    hexInput.oninput=()=>{
      let val=hexInput.value.trim();
      if(!val.startsWith('#'))val='#'+val;
      if(/^#[0-9a-f]{3}$/i.test(val))val='#'+val.slice(1).split('').map(c=>c+c).join('');
      if(/^#[0-9a-f]{6}$/i.test(val)){
        updateAllFromHex(val);
      }
    };
    hexInput.onblur=()=>{
      hexInput.value=currentHex.toUpperCase();
    };

    nativePicker.oninput=()=>{
      updateAllFromHex(nativePicker.value);
    };

    rgbModeBtn.onclick=()=>{
      if(mode==='rgb')return;
      mode='rgb';
      rgbModeBtn.classList.add('active');
      hsbModeBtn.classList.remove('active');
      renderSliders();
    };
    hsbModeBtn.onclick=()=>{
      if(mode==='hsb')return;
      mode='hsb';
      hsbModeBtn.classList.add('active');
      rgbModeBtn.classList.remove('active');
      renderSliders();
    };

    renderSliders();

    container.append(previewRow,presetLabel,presetsGrid,modeToggle,slidersContainer);
    $('dialogChoices').appendChild(container);

    const button=document.createElement('button');
    button.type='button';
    button.className='filled';
    button.textContent=language==='ja'?'適用':language==='zh'?'应用':'Apply';
    button.onclick=async()=>{
      if(source===state.source&&revision===state.revision&&generation===state.generation&&
        block.workspace===workspace&&workspace.getBlockById(block.id)===block&&state.editable&&!state.pending&&!state.processing&&!state.loading){
        button.disabled=true;
        dialog.close('apply');
        if(block.getField?.('F_value')){
          block.setFieldValue(currentHex.toLowerCase(),'F_value');
        }else{
          const node=nodeFor(block);
          if(node&&['p5_call','call'].includes(node.kind)){
            await semanticEdit(block,{type:'setColor',id:node.id,hex:currentHex},source,revision).catch(error=>rollback(error.message));
          }
        }
      }else{
        dialog.close('cancel');
      }
    };
    $('dialogChoices').appendChild(button);
    dialog.showModal();
  }
  function adjustNumber(block){
    const revision=state.revision,dialog=$('actionDialog'),value=Number(block.getFieldValue('F_value')),span=Math.max(100,Math.abs(value));
    $('dialogTitle').textContent=language==='ja'?'数値を調整':language==='zh'?'调整数值':'Adjust number';$('dialogDescription').textContent='';$('dialogChoices').replaceChildren();
    const input=document.createElement('input');input.type='number';input.step='any';input.value=String(value);input.setAttribute('aria-label',$('dialogTitle').textContent);
    const slider=document.createElement('input');slider.type='range';slider.min=String(value-span);slider.max=String(value+span);slider.step=Number.isInteger(value)?'1':'.01';slider.value=String(value);slider.setAttribute('aria-label',$('dialogTitle').textContent);slider.style.width='100%';
    slider.oninput=()=>{input.value=slider.value;};input.oninput=()=>{slider.value=input.value;};
    const button=document.createElement('button');button.type='button';button.className='filled';button.textContent=language==='ja'?'適用':language==='zh'?'应用':'Apply';button.onclick=()=>{if(revision===state.revision&&block.workspace===workspace&&state.editable&&input.value.trim()&&Number.isFinite(Number(input.value))){block.setFieldValue(input.value,'F_value');dialog.close('apply');}};
    $('dialogChoices').append(input,slider,button);dialog.showModal();
  }
  function fieldFor(node,key,descriptor) {
    const value=descriptor.value;
    if(descriptor.type==='number')return new Blockly.FieldNumber(Number.isFinite(Number(value))?Number(value):0);
    if(descriptor.type==='dropdown'&&Array.isArray(descriptor.options)&&descriptor.options.length)return new Blockly.FieldDropdown(descriptor.options.map(x=>Array.isArray(x)?x:[String(x),String(x)]));
    if(node.kind==='identifier'&&key==='name'&&!node.declaration){
      const vars=getGlobalVariables();
      const varNames=vars.map(v=>v.name);
      if(varNames.includes(String(value))){
        return new Blockly.FieldDropdown(Array.from(new Set([String(value),...(node.visibleNames||[])])).map(name=>[name,name]));
      }
      const groups=[['CENTER','CORNER','CORNERS','RADIUS'],['LEFT','RIGHT','CENTER','TOP','BOTTOM','BASELINE'],['RGB','HSB','HSL'],['BLEND','ADD','DARKEST','LIGHTEST','DIFFERENCE','EXCLUSION','MULTIPLY','SCREEN','REPLACE','REMOVE','OVERLAY','HARD_LIGHT','SOFT_LIGHT','DODGE','BURN'],['RADIANS','DEGREES'],['OPEN','CHORD','PIE','CLOSE'],['WEBGL','P2D']];
      const group=groups.find(g=>g.includes(String(value)));
      if(group)return new Blockly.FieldDropdown(Array.from(new Set([...group,...(node.visibleNames||[])])).map(name=>[name,name]));
    }
    if(node.assetKind&&key==='value'){const options=[[String(value),String(value)]];for(const asset of state.assets.filter(a=>a.mime?.toLowerCase().startsWith(node.assetKind==='image'?'image/':'audio/'))){if(!options.some(x=>x[1]===asset.name))options.push([asset.name,asset.name]);}return new Blockly.FieldDropdown(options);}
    const field=new Blockly.FieldTextInput(value==null?'null':String(value));
    // This limits only the painted label; editing and source retain the whole comment.
    if(node.kind==='comment')field.maxDisplayLength=24;
    if(isColor(node,key))field.setTooltip(language==='ja'?'色コード #RRGGBB。メニューから色を選択できます。':'Color: #RRGGBB');
    return field;
  }
  function definitionSignature(node) {
    const varNames=getGlobalVariables().map(v=>v.name);
    const isVar=node.kind==='identifier'&&!node.declaration&&varNames.includes(node.fields?.name?.value);
    return JSON.stringify([language,node.kind,node.declaration,node.renameable,node.parameters,node.parametersEditable,caption(node),node.kind==='identifier'?node.fields.name?.value:null,node.kind==='identifier'?node.visibleNames:null,isVar?varNames:null,node.valueType==='string'?node.fields.value?.value:null,node.assetKind,node.statement,node.output,state.assets.map(a=>a.name),Object.keys(node.fields||{}).map(k=>[k,node.fields[k].type,node.fields[k].options,isColor(node,k)]),Object.keys(node.inputs||{}).map(k=>[k,node.inputKinds[k],Array.isArray(node.inputs[k])&&node.inputKinds[k]!=='statement'?node.inputs[k].length:null]),node.event?node.async:null,node.kind.startsWith('raw')?node.raw:null]);
  }
  function swatchDataUrl(hex){
    return 'data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22"><rect x="2" y="2" width="18" height="18" rx="9" stroke="#ffffffb3" stroke-width="1.5" fill="'+normalizeHex(hex)+'"/></svg>');
  }
  function definition(node) {
    const signature=definitionSignature(node);
    if(types.has(signature))return types.get(signature);
    const type='rin_'+hash(signature);types.set(signature,type);
    Blockly.Blocks[type]={init:function(){
      if(!['literal','identifier','expression_statement','parenthesized'].includes(node.kind)){
        const titleRow=this.appendDummyInput('TITLE').appendField(caption(node));
        if(node.p5&&COLOR_CALLS.includes(node.calleeName)&&(node.inputs?.args||[]).length){
          titleRow.appendField(new Blockly.FieldImage(swatchDataUrl('#6c5ce7'),20,20,language==='ja'?'色を選ぶ':language==='zh'?'选择颜色':'Choose color',()=>{if(state.editable)chooseColor(findColorTarget(this)||this);}),'SWATCH_COLOR');
        }
      }
      for(const [key,descriptor]of Object.entries(node.fields||{})){
        if(descriptor.type==='code')continue;
        if(node.kind==='function'&&key==='params')continue;
        if(node.kind==='function'&&node.event&&(key==='name'||key==='params'&&!descriptor.value||key==='async'&&!descriptor.value))continue;
        const row=['literal','identifier'].includes(node.kind)?this.appendDummyInput('FIELD_'+key):this.getInput('TITLE')||this.appendDummyInput('TITLE');
        if(!['literal','identifier'].includes(node.kind))row.appendField(fieldLabel(key));
        row.appendField(fieldFor(node,key,descriptor),'F_'+key);
        if(isColor(node,key)){this.getInput('FIELD_'+key).appendField(new Blockly.FieldImage(swatchDataUrl(node.fields[key].value),22,22,language==='ja'?'色を選ぶ':language==='zh'?'选择颜色':'Choose color',()=>{if(state.editable)chooseColor(this);}));}
      }
      if(node.kind==='function'&&(node.parameters||[]).length)this.appendDummyInput('PARAMETERS').appendField(words('受け取る値: ','Parameters: ','形参: ')+(node.parametersEditable||node.event?node.parameters.map(p=>p.name).join(', '):node.fields.params?.value||''));
      if(node.kind.startsWith('raw')){
        const preview=node.raw.replace(/\s+/g,' ').trim();
        this.appendDummyInput('SOURCE').appendField(preview.length>28?preview.slice(0,27)+'…':preview);
        this.setTooltip(node.raw.slice(0,1200));
      }else this.setTooltip(node.raw.slice(0,700));
      if(['p5_call','call','new'].includes(node.kind)&&(node.inputs.args||[]).length)this.appendEndRowInput('CALL_ROW');
      for(const [key,value]of Object.entries(node.inputs||{})){
        if((node.kind==='p5_call'||['call','new'].includes(node.kind)&&simpleCallee(node))&&key==='callee')continue;
        if(node.inputKinds[key]==='statement'){const input=this.appendStatementInput('I_'+key);if(key!=='body')input.appendField(inputLabel(key));}
        else if(Array.isArray(value)){for(let i=0;i<value.length;i++){const input=this.appendValueInput('I_'+key+'_'+i);input.appendField(node.kind==='p5_call'?argumentLabel(node,i):inputLabel(key)+' '+i);}}
        else {
          const input=this.appendValueInput('I_'+key);
          if(node.kind==='expression_statement'){
            const grip='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="14" height="22"><path d="M4 5h.1m6 0h.1M4 11h.1m6 0h.1M4 17h.1m6 0h.1" stroke="#81776d" stroke-width="3" stroke-linecap="round"/></svg>');
            const field=new Blockly.FieldImage(grip,14,22,words('処理全体を移動','Move entire statement','移动整个语句'));
            field.setTooltip(words('少し長押しして、この処理全体を移動します。','Hold briefly to move the entire statement.','按住片刻以移动整个语句。'));
            input.appendField(field);
          }
          if(node.kind==='member'){
            if(key==='object')input.appendField(node.computed?words('配列','array','数组'):words('対象','object','对象'));
            else if(key==='property')input.appendField(node.computed?words('番目 [ ]','index [ ]','索引 [ ]'):'.');
          } else if(!['expression_statement','parenthesized','binary','logical','assignment','update','unary'].includes(node.kind)){
            input.appendField(inputLabel(key));
          }
        }
      }
      this.setColour(color(node));if(node.statement){this.setPreviousStatement(true);this.setNextStatement(true);}else this.setOutput(true);
      this.setInputsInline(!['function','if','for','for_of','block'].includes(node.kind));
    }};
    return type;
  }
  function assetKindFor(projection,node){
    let parent=node;
    while(parent?.parentId){const next=projectionNode(projection,parent.parentId);if(next?.p5&&next.inputs.args?.[0]===parent.id&&['loadImage','loadSound'].includes(next.calleeName))return next.calleeName==='loadImage'?'image':'sound';if(next?.kind!=='parenthesized')break;parent=next;}
    return null;
  }
  function refreshCallSwatches(){
    if(!workspace)return;
    quiet(()=>{
      for(const block of workspace.getAllBlocks(false)){
        const swatchField=block.getField?.('SWATCH_COLOR');
        if(swatchField){
          const target=findColorTarget(block)||block;
          const url=swatchDataUrl(colorSwatchFor(target));
          if(swatchField.getValue()!==url)swatchField.setValue(url);
        }
      }
    });
  }
  function updateBlock(block,projection,node,bound,assetKind){
    models.set(block.id,{nodeId:node.id,projection,bound,assetKind});
    for(const [key,descriptor]of Object.entries(node.fields||{})){
      const field=block.getField('F_'+key),value=descriptor.value==null?'null':String(descriptor.value);
      if(descriptor.type!=='code'&&field&&String(field.getValue())!==value)field.setValue(value);
    }
    block.setTooltip(node.raw.slice(0,1200));
  }
  function createBlock(projection,node,bound,idSuffix){
    const assetKind=assetKindFor(projection,node),visual=Object.assign({},node,{assetKind});
    const block=workspace.newBlock(definition(visual),(bound?'bound_':'orphan_'+idSuffix+'_')+node.id);
    block.rinDefinitionSignature=definitionSignature(visual);updateBlock(block,projection,node,bound,assetKind);block.initSvg();return block;
  }
  function visualInputs(node){
    return Object.entries(node.inputs||{}).filter(([key])=>!((node.kind==='p5_call'||['call','new'].includes(node.kind)&&simpleCallee(node))&&key==='callee'));
  }
  function createTree(projection,nodeId,bound,idSuffix){
    const node=projectionNode(projection,nodeId);if(!node)return null;
    const block=createBlock(projection,node,bound,idSuffix);
    for(const [key,value]of visualInputs(node)){
      if(node.inputKinds[key]==='statement'){
        let previous=null;
        for(const childId of(Array.isArray(value)?value:[value])){if(!childId)continue;const child=createTree(projection,childId,bound,idSuffix);if(!child)continue;const connection=previous?previous.nextConnection:block.getInput('I_'+key)?.connection;if(connection&&child.previousConnection)connection.connect(child.previousConnection);previous=child;}
      }else if(Array.isArray(value)){value.forEach((childId,index)=>{const connection=block.getInput('I_'+key+'_'+index)?.connection;if(!childId||!connection)return;const child=createTree(projection,childId,bound,idSuffix);if(child?.outputConnection)connection.connect(child.outputConnection);});}
      else if(value){const connection=block.getInput('I_'+key)?.connection;if(connection){const child=createTree(projection,value,bound,idSuffix);if(child?.outputConnection)connection.connect(child.outputConnection);}}
    }
    block.render();return block;
  }
  function presentationEnvironment(){return JSON.stringify([language,state.p5Version,state.soundEnabled,state.assets,state.sourceType]);}
  function pruneDefinitions(){
    for(const id of models.keys())if(!workspace.getBlockById(id))models.delete(id);
    const used=new Set(workspace.getAllBlocks(false).map(block=>block.type));
    for(const [signature,type]of types)if(!used.has(type)){delete Blockly.Blocks[type];types.delete(signature);}
  }
  // Preserve independent parts; reconcile only the source-bound graph. All old
  // edges that differ are disconnected before any new edge is connected, so a
  // Blockly insertion cannot accidentally splice a surviving tail twice.
  async function reconcileAcknowledgedTree(projection,metadata){
    if(!workspace||!rootBlock||!lastGood||lastGood.environment!==presentationEnvironment())return false;
    const ownGeneration=state.generation;
    const meta=validMetadata(metadata),previous=validMetadata(lastGood.metadata);
    const partSources=value=>JSON.stringify((value?.orphans||[]).map(part=>[part.id,part.source,!!part.expression]));
    if(!meta||partSources(meta)!==partSources(previous))return false;
    const selected=selectedBlockId||Blockly.getSelected()?.id,refocus=!!document.activeElement?.closest?.('.blocklySvg'),visible=new Map();
    const visit=id=>{if(!id||visible.has(id))return;const node=projectionNode(projection,id);if(!node)return;visible.set(id,node);for(const [,ids]of visualInputs(node))for(const child of(Array.isArray(ids)?ids:[ids]))visit(child);};
    projection.rootIds.forEach(visit);
    try{
      quiet(()=>{
        const old=workspace.getAllBlocks(false).filter(block=>modelFor(block)?.bound),next=new Map();
        for(const [id,node]of visible){
          const existing=workspace.getBlockById('bound_'+id),assetKind=assetKindFor(projection,node);
          const signature=definitionSignature(Object.assign({},node,{assetKind}));
          if(existing?.rinDefinitionSignature===signature){updateBlock(existing,projection,node,true,assetKind);next.set(id,existing);}
          else{
            // Release an old ID before constructing its replacement, preserving
            // connected children until the edge comparison below has detached them.
            if(existing){for(const connection of connections(existing))if(connection.targetConnection)connection.disconnect();existing.dispose(false);models.delete(existing.id);}
            next.set(id,createBlock(projection,node,true,''));
          }
        }
        const desired=new Map(),edges=[];
        const edge=(a,b)=>{if(!a||!b)throw new Error('Missing visual connection');desired.set(a,b);desired.set(b,a);edges.push([a,b]);};
        const list=(connection,ids)=>{let previous=null;for(const id of(Array.isArray(ids)?ids:[ids])){if(!id)continue;const child=next.get(id);edge(previous?previous.nextConnection:connection,child?.previousConnection);previous=child;}};
        list(rootBlock.getInput('BODY').connection,projection.rootIds);
        for(const [id,node]of visible){const block=next.get(id);for(const [key,ids]of visualInputs(node)){
          if(node.inputKinds[key]==='statement')list(block.getInput('I_'+key)?.connection,ids);
          else if(Array.isArray(ids))ids.forEach((child,index)=>{if(child)edge(block.getInput('I_'+key+'_'+index)?.connection,next.get(child)?.outputConnection);});
          else if(ids)edge(block.getInput('I_'+key)?.connection,next.get(ids)?.outputConnection);
        }}
        for(const block of new Set([rootBlock,...old,...next.values()])){
          if(block.isDisposed?.())continue;
          for(const connection of connections(block))if(connection.targetConnection&&desired.get(connection)!==connection.targetConnection)connection.disconnect();
        }
        for(const block of old)if(!block.isDisposed?.()&&next.get(modelFor(block)?.nodeId)!==block){models.delete(block.id);block.dispose(false);}
        for(const [a,b]of edges)if(a.targetConnection!==b)a.connect(b);
        for(const block of next.values())if(!block.rendered)block.render();
        const pos=meta.positions?.program;if(Number.isFinite(pos?.x)&&Number.isFinite(pos?.y)){const current=rootBlock.getRelativeToSurfaceXY();if(current.x!==pos.x||current.y!==pos.y)rootBlock.moveBy(pos.x-current.x,pos.y-current.y);}
        for(const part of meta.orphans||[]){const block=workspace.getTopBlocks(false).find(block=>block.rinOrphanId===part.id);if(block&&Number.isFinite(part.x)&&Number.isFinite(part.y)){const current=block.getRelativeToSurfaceXY();if(current.x!==part.x||current.y!==part.y)block.moveBy(part.x-current.x,part.y-current.y);}}
        const collapsed=new Set(meta.collapsed||[]);for(const block of workspace.getAllBlocks(false))if(block.isCollapsed()!==collapsed.has(block.id))block.setCollapsed(collapsed.has(block.id));
        pruneDefinitions();workspace.clearUndo();presentation?.decorateParts();
      });
      await Blockly.renderManagement.finishQueuedRenders();
      if(ownGeneration!==state.generation)return false;
      if(selected&&workspace.getBlockById(selected))quiet(()=>{if(refocus)Blockly.getFocusManager().focusNode(workspace.getBlockById(selected));workspace.getBlockById(selected).select();});
      lastGood={projection,metadata,environment:presentationEnvironment()};presentation?.afterRender();rememberView();refreshCallSwatches();highlightRuntimeErrors();return true;
    }catch(_){return false;}
  }
  function connections(block){return [block.previousConnection,block.nextConnection,block.outputConnection,...block.inputList.map(input=>input.connection)].filter(Boolean);}
  function rememberView(){if(workspace)state.view={x:workspace.scrollX,y:workspace.scrollY,scale:workspace.scale};}
  async function parseTemplate(source, expression) {
    let projection=await ask('parse',{source,options:options()});
    let ids=projection.rootIds;
    if(expression){projection=await ask('parse',{source:'const __rin_value__ = ('+source+');',options:options()});const declarator=projection.nodes.find(n=>n.kind==='variable_declarator');let id=declarator?.inputs.init;const p=projection.nodes.find(n=>n.id===id);if(p?.kind==='parenthesized')id=p.inputs.expression;ids=id?[id]:[];}
    else if(!projection.ok){projection=await ask('parse',{source:'async function __rin_fragment__() {\n'+source+'\n}',options:options()});ids=projection.nodes.find(n=>n.kind==='function')?.inputs.body||[];}
    if(!projection.ok||!ids?.length)throw new Error(projection.diagnostics?.[0]?.message||tr('unsupported'));
    return {projection,ids};
  }
  function validMetadata(value) {
    if(!value)return {version:1,engine:null,orphans:[]};
    try{const meta=typeof value==='string'?JSON.parse(value):value;if(meta&&meta.version===1&&(!meta.orphans||Array.isArray(meta.orphans)))return meta;}catch(_){}
    return null;
  }
  async function rebuild(projection, metadata) {
    const ownGeneration=state.generation,initialView=!lastGood&&!state.view&&!state.restoreView;const meta=validMetadata(metadata)||{version:1,orphans:[]};
    const orphanTrees=[],unrendered=[];
    for(const orphan of (meta.orphans||[])){if(!orphan||typeof orphan.source!=='string'){unrendered.push(orphan);continue;}try{orphanTrees.push({orphan,...await parseTemplate(orphan.source,!!orphan.expression)});}catch(error){unrendered.push(orphan);status(String(error.message),true);}}
    if(ownGeneration!==state.generation)return;
    state.unrenderedOrphans=unrendered;hideBlockActions();clearGrab();initWorkspace();
    if(state.restoreView){state.view=state.restoreView;state.restoreView=null;}else rememberView();
    const viewToRestore=state.view;
    quiet(()=>{
      workspace.clear();models.clear();
      // Definitions capture field options and source-specific labels. Once all
      // old blocks are disposed, release them rather than retaining every edit.
      for(const type of types.values())delete Blockly.Blocks[type];
      types.clear();
      rootBlock=workspace.newBlock('rin_program','rin_program');rootBlock.initSvg();rootBlock.render();const pos=meta.positions?.program;rootBlock.moveBy(Number.isFinite(pos?.x)?pos.x:24,Number.isFinite(pos?.y)?pos.y:28);
      let previous=null;
      for(const id of projection.rootIds){const block=createTree(projection,id,true,'');if(!block)continue;(previous?previous.nextConnection:rootBlock.getInput('BODY').connection).connect(block.previousConnection);previous=block;}
      for(let i=0;i<orphanTrees.length;i++){const {orphan,projection:p,ids}=orphanTrees[i];let previousOrphan=null,first=null;for(const id of ids){const block=createTree(p,id,false,orphan.id||String(i));if(!first)first=block;if(previousOrphan?.nextConnection&&block.previousConnection)previousOrphan.nextConnection.connect(block.previousConnection);previousOrphan=block;}if(first){first.rinOrphanId=orphan.id||'o'+i;first.moveBy(Number.isFinite(orphan.x)?orphan.x:600,Number.isFinite(orphan.y)?orphan.y:60+i*120);}}
      for(const id of Array.isArray(meta.collapsed)?meta.collapsed:[])workspace.getBlockById(id)?.setCollapsed(true);
      workspace.setTheme(theme());presentation?.decorateParts();applyReadOnly();workspace.clearUndo();
      if(state.view){workspace.setScale(state.view.scale);workspace.scroll(state.view.x,state.view.y);}
      Blockly.svgResize(workspace);
    });
    await Blockly.renderManagement.finishQueuedRenders();
    // Disposing a focused tree queues a browser focus update. Restore the saved
    // node only after that update, otherwise it can jump back to the program root.
    await new Promise(resolve=>requestAnimationFrame(()=>setTimeout(resolve,0)));
    if(ownGeneration!==state.generation)return;
    // Preserve legible text on phones; horizontal panning is preferable to
    // shrinking an entire program to fit. Overview remains an explicit action.
    if(restoredSelection&&workspace.getBlockById(restoredSelection)){
      selectedBlockId=restoredSelection;quiet(()=>{Blockly.getFocusManager().focusNode(workspace.getBlockById(restoredSelection));workspace.getBlockById(restoredSelection).select();});
      await new Promise(resolve=>requestAnimationFrame(()=>setTimeout(resolve,0)));
      if(ownGeneration!==state.generation)return;
    }
    restoredSelection=null;
    if(viewToRestore)quiet(()=>{workspace.setScale(viewToRestore.scale);workspace.scroll(viewToRestore.x,viewToRestore.y);});
    else if(initialView)quiet(()=>workspace.scroll(0,0));
    lastGood={projection,metadata,environment:presentationEnvironment()};
    presentation?.afterRender();rememberView();refreshCallSwatches();
    highlightRuntimeErrors();
  }
  function applyReadOnly(){nativeChrome?.schedule();if(!workspace)return;const editable=state.editable&&!state.pending&&!state.processing&&!state.loading;rootBlock?.setMovable(editable);for(const block of workspace.getAllBlocks(false)){if(block.id==='rin_program')continue;block.setEditable(editable);block.setMovable(editable);block.setDeletable(editable);}for(const id of ['paletteToggle','examples'])$(id).disabled=!editable;for(const id of ['undo','redo'])$(id).disabled=state.pending||!!state.processing;if(actionBlock)showBlockActions(actionBlock);}
  function clearAllBlockErrors(){
    if(!workspace)return;
    for(const b of workspace.getAllBlocks(false)){
      b.getSvgRoot()?.classList.remove('rin-error-block');
      delete b.rinRuntimeError;
      b.setWarningText(null);
    }
  }
  function findBestBlockForError(error){
    if(!workspace||!error||error.line==null)return null;
    const line=Number(error.line);
    if(!line||line<=0)return null;
    let targetName=null;
    const msg=String(error.message||'');
    const refMatch=msg.match(/(?:ReferenceError:\s*|'|‘|")([a-zA-Z0-9_$]+)(?:'|’|"|\s+is not defined|\s+before initialization)/);
    if(refMatch)targetName=refMatch[1];
    else{
      const fnMatch=msg.match(/([a-zA-Z0-9_$]+)\s+is not a function/);
      if(fnMatch)targetName=fnMatch[1];
      else{
        const propMatch=msg.match(/(?:reading|setting)\s+['‘"]([a-zA-Z0-9_$]+)['’"]/);
        if(propMatch)targetName=propMatch[1];
      }
    }
    let best=null,bestScore=-Infinity;
    for(const block of workspace.getAllBlocks(false)){
      if(block.id==='rin_program')continue;
      const model=modelFor(block),node=nodeFor(block);
      if(!model?.bound||!node?.loc)continue;
      const start=node.loc.start.line,end=node.loc.end.line;
      if(line>=start&&line<=end){
        const lineSpan=end-start;
        let score= -lineSpan*1000;
        const name=node.fields?.name?.value||node.calleeName||(node.kind==='identifier'?node.raw:null);
        if(targetName&&name===targetName){
          score+=10000;
        }
        if(['p5_call','call','new'].includes(node.kind))score+=500;
        else if(['assignment','update'].includes(node.kind))score+=400;
        else if(node.kind==='member')score+=300;
        else if(['binary','logical','unary'].includes(node.kind))score+=200;
        else if(['if','for','for_of','while','return','function'].includes(node.kind))score+=150;
        else if(node.kind==='variable_declarator')score+=100;
        else if(node.kind==='expression_statement')score-=100;
        else if(node.kind==='identifier')score-=200;
        else if(node.kind==='literal')score-=300;
        else if(node.kind==='parenthesized')score-=400;

        score-=(node.end-node.start)*0.01;
        if(score>bestScore){
          bestScore=score;
          best=block;
        }
      }
    }
    return best;
  }
  function highlightRuntimeErrors(){
    if(!workspace)return;
    const errors=state.errors||[];
    quiet(()=>{
      clearAllBlockErrors();
      for(const err of errors){
        const target=findBestBlockForError(err);
        if(target){
          target.rinRuntimeError=err;
          target.getSvgRoot()?.classList.add('rin-error-block');
          const friendly=friendlyErrorMessage(err.message,language);
          const linePrefix=err.line?(language==='en'?`Line ${err.line}: `:language==='zh'?`第 ${err.line} 行: `:`行 ${err.line}: `):'';
          target.setWarningText(linePrefix+friendly);
        }
      }
    });
    if(errors.length>0){
      const activeErr=actionBlock?.rinRuntimeError||errors[0];
      status(formatErrorStatus(activeErr),true);
    }else{
      if($('status')?.classList.contains('error')){
        const needsSound=!state.soundEnabled&&state.projection?.nodes.some(n=>n.requirements?.sound);
        status(state.readOnly?tr('readOnly'):needsSound?tr('sound'):tr('ready')+' '+tr('orphan'),false);
      }
    }
    if(actionBlock)showBlockActions(actionBlock);
  }
  async function loadDocument(message, force=false){
    if(message.sessionId!==state.sessionId)return;
    if(typeof message.nativeChrome==='boolean')nativeChrome?.configure(message.nativeChrome);
    if(message.workId!==state.workId||message.source!==state.source||message.revision!==state.revision)clearOperationError();
    if(message.preferences)await applyDisplayPreferences(message.preferences,false);
    const nextLang=['ja','en','zh'].includes(message.language)?message.language:'ja';
    if(!force&&workspace&&!state.pending&&message.workId===state.workId&&message.source===state.source&&message.revision===state.revision&&
      (message.metadata||null)===state.metadata&&!!message.readOnly===state.readOnly&&
      nextLang===language&&!!message.dark===state.dark&&String(message.p5Version||'')===state.p5Version&&
      !!message.soundEnabled===state.soundEnabled&&JSON.stringify(message.assets||[])===JSON.stringify(state.assets)&&
      (message.sourceType==='module'?'module':'script')===state.sourceType){
      state.errors=Array.isArray(message.errors)?message.errors:[];
      state.assets=Array.isArray(message.assets)?message.assets:[];
      const previousTheme=JSON.stringify(state.theme);
      state.theme=message.theme||state.theme||null;
      applyAppTheme(state.theme);
      if(previousTheme!==JSON.stringify(state.theme))workspace.setTheme(theme());
      highlightRuntimeErrors();
      return;
    }
    if(state.pending&&message.workId===state.workId&&message.source===state.source&&message.revision===state.revision&&
      (message.metadata||null)===state.metadata&&!!message.readOnly===state.readOnly)return;
    if(state.pending&&state.ackResolve){state.ackResolve(false);state.ackResolve=null;}
    clearTimeout(state.ackTimer);state.pendingRequestId=null;
    state.generation++;const ownGeneration=state.generation;pendingEvents.length=0;clearTimeout(changeTimer);state.pending=false;state.loading=true;state.editable=false;applyReadOnly();
    const prevLanguage=language;
    if(message.workId!==state.workId){
      selectedBlockId=null;lastGood=null;clearOperationError();
      setPalette(false);setMore(false);
      const view=message.viewport,valid=view&&[view.x,view.y,view.scale].every(Number.isFinite)&&view.scale>=.3&&view.scale<=2&&Math.abs(view.x)<=1e7&&Math.abs(view.y)<=1e7;
      state.restoreView=valid?{x:view.x,y:view.y,scale:view.scale}:{x:0,y:0,scale:1};
      restoredSelection=typeof view?.selected==='string'?view.selected:null;
      for(const id of ['actionDialog','codeDialog','navigatorDialog'])if($(id).open)$(id).close('cancel');
    }
    Object.assign(state,{workId:String(message.workId||''),source:String(message.source||''),revision:String(message.revision||''),metadata:message.metadata||null,readOnly:!!message.readOnly,p5Version:message.p5Version||'',soundEnabled:!!message.soundEnabled,assets:Array.isArray(message.assets)?message.assets:[],dark:!!message.dark,theme:message.theme||state.theme||null,sourceType:message.sourceType==='module'?'module':'script',errors:Array.isArray(message.errors)?message.errors:[]});
    applyAppTheme(state.theme);
    language=['ja','en','zh'].includes(message.language)?message.language:'ja';
    try{
    if(prevLanguage!==language||!workspace)await loadLocale();else localizedUI();
    const meta=validMetadata(state.metadata);
    if(!meta){state.editable=false;applyReadOnly();status(language==='ja'?'ブロックの保存情報を読み込めません。コードは保持されています。':language==='zh'?'无法读取积木保存信息。代码已保留。':'Block metadata could not be read. Your code is preserved.',true);return;}
    const projection=await ask('parse',{source:state.source,options:options(meta.engine)});if(ownGeneration!==state.generation)return;state.projection=projection;state.editable=projection.ok&&!state.readOnly;
      if(!projection.ok){applyReadOnly();status(tr('syntax')+' '+(projection.diagnostics?.[0]?.message||''),true);send({type:'diagnostics',diagnostics:projection.diagnostics||[],editable:false});return;}
      const reconciled=!force&&await reconcileAcknowledgedTree(projection,state.metadata);if(ownGeneration!==state.generation)return;
      if(!reconciled)await rebuild(projection,state.metadata);if(ownGeneration!==state.generation)return;const needsSound=!state.soundEnabled&&projection.nodes.some(n=>n.requirements?.sound);if(!state.errors?.length)status(state.readOnly?tr('readOnly'):needsSound?tr('sound'):tr('ready')+' '+tr('orphan'),false);send({type:'diagnostics',diagnostics:[],editable:state.editable});
    }catch(error){state.editable=false;applyReadOnly();status(String(error.message),true);}
    finally{if(ownGeneration===state.generation){state.loading=false;applyReadOnly();}}
  }
  function chain(block){const values=[];for(let current=block;current;current=current.getNextBlock())values.push(current);return values;}
  function relation(block){
    if(!block)return null;let first=block;
    // A statement input also uses a previous connection. Only follow an actual previous sibling.
    while(first.getPreviousBlock()?.nextConnection?.targetBlock()===first)first=first.getPreviousBlock();
    const parent=first.getParent();
    if(!parent)return null;
    const input=parent.inputList.find(i=>i.connection?.targetBlock()===first);if(!input)return null;
    const model=modelFor(parent);return {parent,parentId:parent===rootBlock?null:model?.nodeId,input:parent===rootBlock?'root':input.name.slice(2).replace(/_\d+$/,''),index:chain(first).indexOf(block),inputName:input.name};
  }
  async function serialize(block, seen){
    if(!block) return '';
    seen=seen||new Set();if(seen.has(block.id))throw new Error(tr('unsupported'));seen.add(block.id);
    const model=modelFor(block),node=nodeFor(block);if(!model||!node)throw new Error(tr('unsupported'));
    const overrides={fields:{},inputs:{}};
    for(const [key,descriptor]of Object.entries(node.fields||{})){if(descriptor.type==='code'||!block.getField('F_'+key))continue;let value=block.getFieldValue('F_'+key);if(descriptor.type==='number')value=Number(value);else if(typeof descriptor.value==='boolean')value=value==='true';if(String(value)!==String(descriptor.value))overrides.fields[key]=value;}
    for(const [key,value]of Object.entries(node.inputs||{})){
      if(node.inputKinds[key]==='statement'){const children=chain(block.getInputTargetBlock('I_'+key));const sources=[];for(const child of children)sources.push(await serialize(child,seen));const old=(Array.isArray(value)?value:[value]).filter(Boolean).map(id=>projectionNode(model.projection,id)?.raw||'');if(JSON.stringify(old)!==JSON.stringify(sources))overrides.inputs[key]=Array.isArray(value)?sources:sources.join('\n');}
      else if(Array.isArray(value)){const sources=[];for(let i=0;i<value.length;i++){const child=block.getInputTargetBlock('I_'+key+'_'+i);sources.push(child?await serialize(child,seen):'');}const old=value.map(id=>projectionNode(model.projection,id)?.raw||'');if(JSON.stringify(old)!==JSON.stringify(sources))overrides.inputs[key]=sources;}
      else {if(!block.getInput('I_'+key))continue;const child=block.getInputTargetBlock('I_'+key),source=child?await serialize(child,seen):'',old=value?model.projection.nodes.find(n=>n.id===value)?.raw||'':'';if(source!==old)overrides.inputs[key]=source;}
    }
    if(!Object.keys(overrides.fields).length&&!Object.keys(overrides.inputs).length)return node.raw;
    const result=await ask('renderNode',{projection:model.projection,nodeId:model.nodeId,overrides});if(!result.ok)throw new Error(result.error||tr('unsupported'));return result.source;
  }
  async function snapshotMetadata(allowSourceDetach=false){
    // Only processChanges may save source-bound roots as parts: it validates
    // their source removal/replacement in the same native transaction.
    const previous=validMetadata(state.metadata)||{};const orphans=(state.unrenderedOrphans||[]).slice();
    for(const top of workspace.getTopBlocks(false)){
      if(top===rootBlock)continue;
      const model=modelFor(top);if(!model)continue;
      if(model.bound&&!allowSourceDetach)throw new Error(tr('busy'));
      const sources=[];for(const block of chain(top))sources.push(await serialize(block));
      const xy=top.getRelativeToSurfaceXY(),id=top.rinOrphanId||top.id;
      const old=(previous.orphans||[]).find(o=>o&&o.id===id)||{};
      orphans.push(Object.assign({},old,{id,source:sources.join('\n'),expression:!!top.outputConnection,x:xy.x,y:xy.y}));
    }
    const pos=rootBlock.getRelativeToSurfaceXY();return JSON.stringify(Object.assign({},previous,{version:1,engine:state.projection.metadata,orphans,collapsed:workspace.getAllBlocks(false).filter(block=>block.isCollapsed()).map(block=>block.id),positions:Object.assign({},previous.positions,{program:{x:pos.x,y:pos.y}})}));
  }
  function onChange(event){
    if(muted||event.isUiEvent||event.type===Blockly.Events.FINISHED_LOADING)return;
    if(event.type===Blockly.Events.BLOCK_FIELD_INTERMEDIATE_CHANGE){
      inputGeneration++;
      send({type:'draftChanged'});
      return;
    }
    if(!['change','move','delete','create'].includes(event.type))return;
    if(state.pending||state.loading||!state.editable)return;
    pendingEvents.push(event);clearTimeout(changeTimer);changeTimer=setTimeout(()=>{busyWork=busyWork.then(processChanges).catch(error=>rollback(error.message));},120);
  }
  async function rollback(message){pendingEvents.length=0;if(state.projection?.ok)await rebuild(state.projection,state.metadata);failOperation(message);}
  async function processChanges(){
    if(state.pending||state.loading||!state.editable||state.processing)return;
    if(!pendingEvents.length&&!workspace.getTopBlocks(false).some(top=>modelFor(top)?.bound))return;
    if(workspace.isDragging()){changeTimer=setTimeout(()=>{busyWork=busyWork.then(processChanges).catch(error=>rollback(error.message));},100);return;}
    const generation=state.generation,expectedSource=state.source,expectedRevision=state.revision,projection=state.projection;
    state.processing=true;applyReadOnly();
    const commit=async patches=>{const metadata=await snapshotMetadata(true);if(generation!==state.generation||expectedSource!==state.source||expectedRevision!==state.revision)return;await submit(patches,metadata,expectedSource,expectedRevision);};
    try{
    const events=pendingEvents.splice(0),operations=[];
    const fieldEvents=new Map(),moved=new Map(),deleted=new Set(),deletedNodes=[],swappedValues=new Set();
    for(const event of events){
      if(event.type==='change'&&event.element==='field'&&typeof event.name==='string'&&event.name.startsWith('F_'))fieldEvents.set(event.blockId+'/'+event.name,event);
      if(event.type==='move'){
        const previous=moved.get(event.blockId);
        // A drag emits disconnect/connect and coordinate events separately.
        // Preserve its original socket when later coordinate events overwrite
        // the final position, otherwise a detached block is saved as a copy.
        moved.set(event.blockId,previous?{...event,oldParentId:previous.oldParentId,
          oldInputName:previous.oldInputName,oldCoordinate:previous.oldCoordinate}:event);
      }
      if(event.type==='delete')for(const id of event.ids||[event.blockId])deleted.add(id);
    }
    // A source-bound block must never be persisted as a detached copy while its
    // original source is still present. Reconcile the actual forest even when a
    // disconnect notification was filtered out or only its final position arrived.
    for(const top of workspace.getTopBlocks(false)){
      if(top!==rootBlock&&modelFor(top)?.bound&&!moved.has(top.id))moved.set(top.id,{blockId:top.id});
    }
    for(const event of fieldEvents.values()){const block=workspace.getBlockById(event.blockId),model=modelFor(block),node=nodeFor(block),field=event.name.replace(/^F_/,'');if(model&&!model.bound&&field==='name'&&(node.kind==='function'||node.declaration))throw new Error(words('未接続の名前は「⋯」からまとめて変更してください。','Use ⋯ to rename a detached declaration and its uses.','请使用 ⋯ 重命名未连接的声明及其引用。'));if(model?.bound)operations.push(field==='name'&&(node.kind==='function'||node.kind==='identifier'&&node.declaration)?{type:'renameBinding',id:model.nodeId,name:block.getFieldValue(event.name)}:{type:'setField',id:model.nodeId,field,value:block.getFieldValue(event.name)});}
    for(const id of deleted){const model=models.get(id);if(model?.bound){const node=projectionNode(model.projection,model.nodeId);if(node?.parentId&&deleted.has('bound_'+node.parentId))continue;deletedNodes.push(model.nodeId);}}
    if(deletedNodes.length)operations.push({type:'delete',ids:deletedNodes});
    for(const [id,event]of moved){if(deleted.has(id)||swappedValues.has(id))continue;const block=workspace.getBlockById(id),model=modelFor(block);if(!block||!model)continue;
      // Block placement is authoring metadata. Viewport pan/zoom events are filtered above.
      if(!event.oldParentId&&!event.newParentId&&!(model.bound&&!block.getParent()))continue;
      const node=nodeFor(block),target=relation(block);
      if(model.bound){
        // Connecting a detached stack into an occupied statement socket also
        // moves the existing tail under that stack. It still belongs to its
        // original list; inserting the new statements already preserves it.
        const previous=block.getPreviousBlock();
        if(block.previousConnection&&previous?.nextConnection?.targetBlock()===block&&
          modelFor(previous)?.bound===false&&target&&
          ((!node.parentId&&target.parent===rootBlock)||
           (node.parentId===target.parentId&&node.parentInput===target.input)))continue;
        if(!block.previousConnection){
          if(!target){
            // Replacing an occupied socket detaches its old value; the incoming value edits the source.
            // Read the original socket from the acknowledged model. Missing event
            // parent IDs must not match another coordinate-only event (or itself).
            const oldParent=projection.nodes.find(n=>n.id===node.parentId),oldIds=oldParent?.inputs[node.parentInput];
            const inputName='I_'+node.parentInput+(Array.isArray(oldIds)?'_'+oldIds.indexOf(node.id):'');
            const parentBlock=oldParent&&workspace.getBlockById('bound_'+oldParent.id);
            const replacement=parentBlock?.getInputTargetBlock(inputName);
            if(replacement&&replacement!==block){
              if(!moved.has(replacement.id))moved.set(replacement.id,{blockId:replacement.id,newParentId:parentBlock.id,newInputName:inputName});
              continue;
            }
            throw new Error(tr('unsupported'));
          }
          const parent=nodeFor(target.parent),oldParent=state.projection.nodes.find(n=>n.id===node.parentId);
          if(!parent||!modelFor(target.parent)?.bound||!oldParent)throw new Error(tr('unsupported'));
          const key=target.input,targetIds=parent.inputs[key],index=Number(target.inputName.match(/_(\d+)$/)?.[1]||0);
          const replacedId=Array.isArray(targetIds)?targetIds[index]:targetIds,replaced=state.projection.nodes.find(n=>n.id===replacedId);
          if(!replaced||replaced.id===node.id||node.start<=parent.start&&node.end>=parent.end||replaced.start<=oldParent.start&&replaced.end>=oldParent.end)throw new Error(tr('unsupported'));
          const replaceInput=(container,input,id,source)=>{const current=container.inputs[input];return {type:'setInput',id:container.id,input,source:Array.isArray(current)?current.map(child=>child===id?source:state.projection.nodes.find(n=>n.id===child)?.raw||''):source};};
          operations.push(replaceInput(parent,key,replacedId,node.raw),replaceInput(oldParent,node.parentInput,node.id,replaced.raw));
          const originalBlock=workspace.getBlockById('bound_'+oldParent.id),displacedBlock=workspace.getBlockById('bound_'+replacedId),oldIds=oldParent.inputs[node.parentInput];
          const socket=originalBlock?.getInput('I_'+node.parentInput+(Array.isArray(oldIds)?'_'+oldIds.indexOf(node.id):''))?.connection;
          if(!socket||!displacedBlock?.outputConnection)throw new Error(tr('unsupported'));
          quiet(()=>socket.connect(displacedBlock.outputConnection));swappedValues.add('bound_'+replacedId);swappedValues.add(id);
          continue;
        }
        if(target){if(!block.previousConnection||!target.parent||target.parent!==rootBlock&&!modelFor(target.parent)?.bound)throw new Error(tr('unsupported'));const parentNode=nodeFor(target.parent);if(parentNode&&!Array.isArray(parentNode.inputs[target.input]))throw new Error(tr('unsupported'));const ids=chain(block).map(b=>modelFor(b)).filter(m=>m?.bound).map(m=>m.nodeId);operations.push({type:'move',ids,parentId:target.parentId,input:target.input,index:target.index});}
        else {if(!block.previousConnection)throw new Error(tr('unsupported'));const ids=chain(block).map(item=>modelFor(item)).filter(m=>m?.bound).map(m=>m.nodeId);operations.push({type:'delete',ids});}
      }else if(target){
        const source=await serialize(block),parentNode=nodeFor(target.parent);
        if(block.previousConnection){
          if(target.parent!==rootBlock&&(!modelFor(target.parent)?.bound||!Array.isArray(parentNode?.inputs[target.input])))throw new Error(tr('unsupported'));
          // Blockly moves a whole stack with one event. Serialize each detached
          // sibling, stopping at the existing bound tail spliced into the socket.
          const sources=[];
          for(const part of chain(block)){
            if(modelFor(part)?.bound)break;
            sources.push(await serialize(part));swappedValues.add(part.id);
          }
          operations.push({type:'insert',parentId:target.parentId,input:target.input,index:target.index,source:sources.join('\n')});
        }
        else {if(!parentNode||!modelFor(target.parent)?.bound)throw new Error(tr('unsupported'));const arr=parentNode.inputs[target.input];if(Array.isArray(arr)){const sources=[];for(let i=0;i<arr.length;i++){const child=target.parent.getInputTargetBlock('I_'+target.input+'_'+i);sources.push(child?await serialize(child):'');}operations.push({type:'setInput',id:parentNode.id,input:target.input,source:sources});}else operations.push({type:'setInput',id:parentNode.id,input:target.input,source});}
      }
    }
    if(!operations.length){if(events.some(e=>e.type==='create'||e.type==='delete'||e.type==='change'||e.type==='move'))await commit([]);return;}
    const result=await ask('proposePatch',{projection,operations});if(!result.ok)throw new Error(authoringError(result));
    await commit(result.patches);
    }finally{state.processing=false;applyReadOnly();}
  }
  async function submit(patches,metadata,expectedSource=state.source,expectedRevision=state.revision){
    ensureDocument(expectedSource,expectedRevision);
    if(!state.editable||state.pending)throw new Error(tr('readOnly'));clearOperationError();state.pending=true;state.pendingRequestId='edit-'+(++editId);applyReadOnly();status(tr('busy'),false);
    state.ackPromise=new Promise(resolve=>{state.ackResolve=resolve;});
    clearTimeout(state.ackTimer);state.ackTimer=setTimeout(()=>{state.pending=false;state.pendingRequestId=null;state.editable=false;state.ackResolve?.(false);state.ackResolve=null;applyReadOnly();status(tr('waiting'),true);},15000);
    send({type:'edit',requestId:state.pendingRequestId,expectedSource,expectedRevision,patches,metadata});
    return state.ackPromise;
  }
  async function handleAck(message){
    if(message.sessionId!==state.sessionId||message.workId!==state.workId)return;
    if(!state.pending||message.requestId!==state.pendingRequestId)return;
    const resolve=state.ackResolve;
    state.ackResolve=null;clearTimeout(state.ackTimer);state.pending=false;state.pendingRequestId=null;
    if(!message.accepted){await loadDocument(Object.assign({},state,{type:'load',sessionId:state.sessionId,source:typeof message.source==='string'?message.source:state.source,metadata:message.metadata===undefined?state.metadata:message.metadata,revision:message.revision||state.revision,language}),true);failOperation(message.reason||tr('rejected'));resolve?.(false);return;}
    await loadDocument(Object.assign({},state,{type:'load',sessionId:state.sessionId,source:message.source,metadata:message.metadata,revision:message.revision,language,errors:[]}));
    resolve?.(true);
  }
  function flushAll(hideChaff=true,onDraft=null){const next=flushWork.catch(()=>false).then(()=>flushOnce(hideChaff,onDraft));flushWork=next;return next;}
  function waitForBlockEvents(){
    return new Promise(resolve=>{
      let finished=false,frame=null;
      const finish=ok=>{if(finished)return;finished=true;clearTimeout(timer);if(frame!==null)cancelAnimationFrame(frame);resolve(ok);};
      // Hidden WebViews may stop producing frames. A timeout is NOT permission
      // to read the tree: Blockly's queued field/move events may still be pending.
      const timer=setTimeout(()=>finish(false),1000);
      frame=requestAnimationFrame(()=>setTimeout(()=>finish(true),0));
    });
  }
  async function snapshotInputDraft(){
    if(!document.querySelector('.blocklyHtmlInput'))return null;
    if(!state.editable||state.loading||state.pending||state.processing)throw new Error(tr('busy'));
    const generation=state.generation,inputVersion=inputGeneration,source=state.source,revision=state.revision,operations=[];
    // FieldInput emits intermediate events until its widget closes. Read its validated
    // values for recovery only; a native ACK/load here would interrupt continued typing.
    if(document.querySelector('.blocklyHtmlInput.blocklyInvalidInput'))throw new Error(tr('busy'));
    for(const block of workspace.getAllBlocks(false)){
      const model=modelFor(block),node=nodeFor(block);
      if(!model?.bound||!node)continue;
      for(const [field,descriptor]of Object.entries(node.fields||{})){
        if(descriptor.type==='code'||!block.getField('F_'+field))continue;
        const value=block.getFieldValue('F_'+field);
        if(String(value)===String(descriptor.value))continue;
        operations.push(field==='name'&&(node.kind==='function'||node.kind==='identifier'&&node.declaration)?
          {type:'renameBinding',id:model.nodeId,name:value}:{type:'setField',id:model.nodeId,field,value});
      }
    }
    const result=operations.length?await ask('proposePatch',{projection:state.projection,operations}):{ok:true,patches:[]};
    if(!result.ok)throw new Error(authoringError(result));
    const metadata=await snapshotMetadata();
    if(generation!==state.generation||inputVersion!==inputGeneration||source!==state.source||revision!==state.revision||
      state.pending||state.loading||state.processing||pendingEvents.length)
      throw new Error(tr('busy'));
    if(!operations.length&&JSON.stringify(validMetadata(metadata)?.orphans||[])===JSON.stringify(validMetadata(state.metadata)?.orphans||[]))return null;
    return {expectedSource:source,expectedRevision:revision,patches:result.patches,metadata};
  }
  async function flushOnce(hideChaff=true,onDraft=null){
    if(!workspace)return !state.pending;
    presentation?.sendView();
    if(hideChaff){
      Blockly.hideChaff();
    }
    // Blockly 13 dispatches events after an animation frame and a subsequent timer.
    // A timer alone can report a successful flush before the last field change exists.
    if(!await waitForBlockEvents())return false;
    clearTimeout(changeTimer);
    await busyWork;
    const loadingDeadline=Date.now()+4000;while(state.loading){if(Date.now()>loadingDeadline)return false;await new Promise(resolve=>setTimeout(resolve,10));}
    if(state.pending&&!await state.ackPromise)return false;
    try{await processChanges();}
    catch(error){
      // A save/navigation flush can beat the debounced change handler. Restore
      // the acknowledged tree on this path too, and keep the caller in the editor.
      await rollback(error.message);return false;
    }
    if(state.pending&&!await state.ackPromise)return false;
    if(!hideChaff&&onDraft)onDraft(await snapshotInputDraft());
    return !state.pending&&!pendingEvents.length;
  }
  async function historyAction(type){const workId=state.workId;if(await flushAll()&&workId===state.workId)send({type});}
  function setMore(open){$('moreMenu').hidden=!open;$('more').setAttribute('aria-expanded',String(open));}
  function setPalette(open){if(open){paletteSelectionId=pendingPaletteSelection!==undefined?pendingPaletteSelection:selectedBlock()?.id||null;pendingPaletteSelection=undefined;setMore(false);hideBlockActions();renderPalette();}else{paletteSelectionId=null;pendingPaletteSelection=undefined;}$('palette').hidden=!open;$('sheetScrim').hidden=!open;$('paletteToggle').setAttribute('aria-expanded',String(open));}
  function categoryColor(cat){return cat==='events'?'#585184':cat==='control'?'#365f8b':cat==='variables'?'#824d6e':['math','input'].includes(cat)?'#715c35':['functions','assets','sound'].includes(cat)?'#4c6860':'#226b72';}
  function authoringEnv(){return {p5Version:state.p5Version,soundEnabled:state.soundEnabled,assets:state.assets};}
  function supportedTemplate(template){return !template.versions||template.versions.includes(/^1(?:\.|$)/.test(state.p5Version)?1:2);}
  async function authoringInspect(){return ask('authoringInspect',{projection:state.projection,env:authoringEnv()});}
  function focusAuthored(id){if(!id)return;const block=workspace.getBlockById('bound_'+id);if(block){block.select();workspace.centerOnBlock(block.id);}}
  function authoringError(result){
    const messages={
      name_in_use:['この変数名はすでに使われています。別の名前を入力してください。','This variable name is already in use. Please enter a different name.','此变量名已在使用。请输入其他名称。'],
      invalid_name:['名前には、空白や記号を含まない有効な変数名を入力してください。','Please enter a valid variable name without spaces or symbols.','请输入不包含空格或特殊符号的有效变量名。'],
      invalid_init:['初期値には有効な数値や式（例: 0 や "テキスト"）を入力してください。','Please enter a valid initial number or expression (e.g. 0 or "text").','请输入有效的初始值（例如 0 或 "文本"）。'],
      instance_mode:['この作品は独自の方法で描画を作っています。イベントはコード画面で追加してください。','This work uses custom instance mode. Please add events in code.','此作品以自定义实例方式绘制。请在代码中添加事件。'],
      dynamic_scope:['この作品では処理の名前を実行中に変えています。この追加はコード画面で行ってください。','This work dynamically changes names. Please perform this change in code.','此作品会动态更改名称。请在代码中添加这些操作。'],
      callback_conflict:['同じイベントが独自の書き方や複数の場所で定義されています。コード画面で確認してください。','The event is defined in multiple places or uses a custom form. Check in code.','此事件有自定义或重复定义。请在代码中检查。'],
      runtime_shadowed:['追加する処理と同じ名前を、この作品で独自に使っています。コード画面で確認してください。','This work already uses the required name. Check in code.','此作品已将所需名称用于其他定义。请在代码中检查。'],
      resource_shadowed:['この処理の中で、素材と同じ名前を別の値に使っています。別のイベントを選んでください。','A local name conflicts with an asset in this event. Choose another event.','此事件中的局部名称与资源冲突。请选择其他事件。'],
      resource_missing:['先に画像や音を読み込むか、音の発生器を用意してください。','Please load an image or audio asset first, or prepare an oscillator.','请先加载图像或音频，或准备振荡器。'],
      asset_missing:['この作品にある、種類の合う素材を選んでください。','Please choose a matching asset from this work.','请选择此作品中类型匹配的素材。'],
      event_unavailable:['このイベントは現在の実行環境には対応していません。','This event is not supported in the current runtime environment.','当前运行环境不支持此事件。'],
      gesture_required:['音を鳴らす処理は、押したときやキーを押したときに追加してください。','Audio playback should be added to press or key events.','请将播放音频的操作添加到按下或按键事件。'],
      webgl_required:['先に「3Dキャンバスを使う WEBGL」で3Dに変更してください。','Please switch to 3D canvas (WEBGL) first.','请先选择“使用 3D 画布 · WEBGL”。'],
      ambiguous_canvas:['キャンバスを安全に変更できません。複数の作成処理などをコード画面で確認してください。','Cannot safely modify canvas. Check multiple setups in code.','无法安全更改画布。请在代码中检查多个或自定义画布。'],
      custom_audio_routing:['この音には独自のつなぎ方があります。左右の位置はコード画面で調整してください。','This audio uses custom routing. Adjust panning in code.','此音频使用自定义连接。请在代码中调整声像。'],
      sound_disabled:[tr('sound'),tr('sound'),tr('sound')],
      invalid_source:[tr('syntax'),tr('syntax'),tr('syntax')],
      ambiguous_branch:['条件によって実行結果が変わるため、安全に変更できません。コード画面で確認してください。','Cannot safely modify because execution varies across branches. Check in code.','由于不同分支的执行结果不一致，无法安全修改。请在代码中检查。'],
      unsupported_binding:['この宣言や代入の書き方はブロック編集に対応していません。コード画面で確認してください。','This declaration or binding form is not supported in blocks. Check in code.','积木编辑不支持此声明或绑定写法。请在代码中检查。'],
      unbound_name:['定義されていない名前が使われています。変数を作るかコード画面で確認してください。','An undefined name is referenced. Create the variable or check in code.','引用了未定义的名称。请创建变量或在代码中检查。'],
      external_binding:['外部の変数を変更する記述はブロックでは安全に編集できません。コード画面で確認してください。','External variable bindings cannot be safely edited with blocks. Check in code.','无法通过积木安全编辑外部变量绑定。请在代码中检查。'],
      reflective_global:['window や globalThis を使った動的な参照が含まれるため、コード画面で編集してください。','Dynamic global access cannot be edited here. Please edit in code.','包含动态全局访问，请在代码中编辑。'],
      complex_parameters:['分割代入や可変長引数などを含むため、コード画面で編集してください。','Complex parameter syntax is present. Please edit in code.','包含复杂参数语法，请在代码中编辑。'],
      name_collision:['同じ名前がすでに別の用途で使われています。別の名前を指定してください。','That name is already used for something else. Please choose another name.','该名称已被其他用途占用，请使用其他名称。'],
      name_capture:['内側の変数名とぶつかるため、このまま変更できません。別の名前にしてください。','This name conflicts with an inner variable. Please choose a different name.','此名称与内部变量冲突，请更换名称。'],
      invalid_argument:['引数の値が正しくありません。数値や式を確認してください。','Invalid argument value. Please check the number or expression.','参数值无效，请检查数值或表达式。'],
      invalid_list_index:['リストの番号（インデックス）が正しくありません。0以上の整数を確認してください。','Invalid list index. Please enter a non-negative integer or valid expression.','列表索引无效，请输入有效的非负整数或表达式。'],
      unsupported_list:['このリスト操作は現在のブロック構文では編集できません。コード画面で確認してください。','This list operation cannot be edited as a block. Check in code.','无法将此列表操作作为积木编辑。请在代码中检查。'],
      unsupported_call:['この関数呼び出しはブロック編集に対応していません。コード画面で確認してください。','This function call is not supported in block editing. Check in code.','积木编辑不支持此函数调用。请在代码中检查。'],
      event_parameters:['独自の引数付きイベントが定義されています。コード画面で確認してください。','This event has custom parameters. Please check in code.','此事件包含自定义参数，请在代码中检查。'],
      ambiguous_function:['関数の定義場所や使われ方を特定できません。コード画面で確認してください。','Cannot uniquely resolve this function definition. Check in code.','无法唯一确定此函数定义。请在代码中检查。'],
      escaped_function:['関数が別の変数や引数に渡されているため、安全に変更できません。コード画面で確認してください。','This function reference is passed around and cannot be safely rewritten. Check in code.','此函数被作为值传递，无法安全修改。请在代码中检查。'],
      spread_arguments:['スプレッド構文（...）を含む呼び出しはコード画面で編集してください。','Calls using spread syntax (...) must be edited in code.','包含展开语法 (...) 的调用请在代码中编辑。'],
      arguments_object:['arguments オブジェクトを使う処理はコード画面で編集してください。','Code using the arguments object must be edited in code.','使用 arguments 对象的代码请在代码中编辑。'],
      used_parameter:['別の処理で使われている引数のため変更できません。','Cannot modify a parameter that is referenced elsewhere.','无法修改已在其他处使用的参数。'],
      unsupported_color:['この色の書き方はブロックでは変更できません。標準の #RRGGBB 形式や数値を使ってください。','This color format cannot be edited here. Use #RRGGBB or numeric values.','此处无法编辑该颜色格式，请使用 #RRGGBB 或数值。'],
      unknown_event:['指定されたイベントはブロック追加に対応していません。','The selected event is not supported for block insertion.','不支持向所选事件插入积木。'],
      invalid_change:['この変更内容は現在の作品構造に適用できません。コード画面で確認してください。','This change cannot be applied to the current code structure. Check in code.','无法将此更改应用到当前代码结构。请在代码中检查。'],
      unknown_action:['不明なブロック操作が指定されました。','An unknown block action was requested.','请求了未知的积木操作。'],
      asset_kind:['選択した素材の種類（画像／音声）がブロックと一致していません。','The selected asset kind (image/audio) does not match this block.','所选素材类型（图像/音频）与此积木不匹配。'],
      webgl_confirmation:['3D（WEBGL）への切り替え確認が必要です。コード内の createCanvas を確認してください。','Confirmation is required to switch to 3D (WEBGL). Check createCanvas in code.','需要确认才能切换到 3D (WEBGL)。请检查代码中的 createCanvas。']
    };
    const row=messages[result.errorCode];
    if(row)return row[language==='en'?1:language==='zh'?2:0];
    return language==='en'?(result.error||tr('unsupported')):(result.error||tr('unsupported'));
  }
  async function applyAuthoring(request,source=state.source,revision=state.revision){ensureDocument(source,revision);if(!state.editable||state.pending)throw new Error(tr('readOnly'));const result=await ask('authoringPlan',{projection:state.projection,request,env:authoringEnv()});ensureDocument(source,revision);if(!result.ok)throw new Error(authoringError(result));if(result.patches.length){if(!await submit(result.patches,await snapshotMetadata(),source,revision))return null;}focusAuthored(result.focusId);return result;}
  async function confirmWebGL(source,revision){const confirm=await choose(words('3D用のキャンバスに変更','Change canvas to 3D','将画布改为 3D'),words('キャンバスに WEBGL を追加します。大きさは保ちますが、座標の中心や描き方が変わります。元に戻す操作で戻せます。','Add WEBGL to the canvas. Its size stays the same, but coordinates and drawing change. Undo restores it.','为画布添加 WEBGL。大小不变，但坐标和绘制方式会改变。可撤销。'),[{value:'change',label:words('3Dに変更する','Change to 3D','改为 3D')}]);if(confirm!=='change')return false;ensureDocument(source,revision);return !!await applyAuthoring({type:'ensureWebGL',confirmed:true},source,revision);}
  async function authorTemplate(template){if(state.guiding)return null;state.guiding=true;try{return await runAuthorTemplate(template);}finally{state.guiding=false;}}
  async function runAuthorTemplate(template){
    if(!state.editable||state.pending||!await flushAll())return;setPalette(false);const source=state.source,revision=state.revision,descriptor=template.authoringAction,request=Object.assign({},descriptor);
    if(!supportedTemplate(template))throw new Error(words('このイベントは現在の実行環境には対応していません。','This event is unavailable in this runtime.','当前运行环境不支持此事件。'));
    if((['sound','oscillator'].includes(descriptor.kind)||template.category==='sound')&&!state.soundEnabled){if(await choose(tr('runtime'),tr('sound'),[{value:'settings',label:words('実行環境を開く','Open runtime settings','打开运行环境设置')}])==='settings'){ensureDocument(source,revision);send({type:'openRuntime'});}return;}
    const info=await authoringInspect();ensureDocument(source,revision);
    if(template.requiresWebGL&&!info.webgl){if(!await confirmWebGL(source,revision))return;return runAuthorTemplate(template);}
    if(descriptor.type==='ensureWebGL'){if(info.webgl){status(words('この作品はすでに3D用キャンバスです。','This work already uses a 3D canvas.','此作品已使用 3D 画布。'),false);return;}return confirmWebGL(source,revision);}
    if(descriptor.type==='loadAsset'){
      const assets=state.assets.filter(a=>typeof a.mime==='string'&&a.mime.toLowerCase().startsWith(descriptor.kind==='image'?'image/':'audio/'));
      if(!assets.length){await choose(words('素材を追加してください','Add an asset first','请先添加素材'),words('作品のメニュー →「作品設定」→「作品の素材」から画像や音を追加して、もう一度このブロックを選んでください。','Add an image or audio file in Work settings → Work assets, then choose this block again.','在作品设置 → 作品素材中添加图像或音频文件，然后再次选择此积木。'),[]);return;}
      const name=await choose(words(descriptor.kind==='image'?'読み込む画像':'読み込む音','Asset to load','要加载的素材'),'',assets.map((a,i)=>({value:'asset-'+i,label:a.name})));if(name==='cancel')return;request.assetName=assets[Number(name.slice(6))].name;
    }
    if(descriptor.type==='useResource'){
      const resources=info.resources.filter(r=>r.kind===descriptor.kind);
      if(!resources.length){const title=words('先に読み込みを用意する','Prepare a resource first','请先准备资源'),next=descriptor.kind==='oscillator'?'oscillator':descriptor.kind==='image'?'imageLoader':'soundLoader';if(await choose(title,words('読み込んだ素材を選ぶと、別の処理からも使えます。','Choose a loaded resource to use it across handlers.','加载后的资源可供其他事件使用。'),[{value:'prepare',label:words(descriptor.kind==='oscillator'?'音の発生器を用意する':descriptor.kind==='image'?'画像を読み込む':'音を読み込む','Prepare resource','准备资源')}])!=='prepare')return;ensureDocument(source,revision);if(!await runAuthorTemplate(C.TEMPLATES.find(t=>t.id===next)))return;return runAuthorTemplate(template);}
      const selectedResource=await choose(words('使う素材・音','Resource to use','使用的资源'),'',resources.map((r,i)=>({value:'resource-'+i,label:r.name+(r.assetName?' · '+r.assetName:'')})));if(selectedResource==='cancel')return;request.name=resources[Number(selectedResource.slice(9))].name;
      const action=descriptor.action,play=/^(soundPlay|soundLoop|toneStart)$/.test(action),stop=/^(soundStop|toneStop)$/.test(action),preferred=/^(image|texture)$/.test(action)?'draw':play?'mousePressed':stop?'mouseReleased':'setup';
      const events=C.TEMPLATES.filter(t=>t.authoringAction?.type==='ensureEvent'&&supportedTemplate(t)&&!['preload'].includes(t.authoringAction.name)&&(!(play||stop)||!['setup','draw','windowResized','deviceMoved','deviceTurned','deviceShaken'].includes(t.authoringAction.name))).sort((a,b)=>(a.authoringAction.name===preferred?-1:b.authoringAction.name===preferred?1:0));
      request.targetEvent=await choose(words('いつ実行するか','When to run','何时执行'),'',events.map(t=>({value:t.authoringAction.name,label:label(t.label)})));if(request.targetEvent==='cancel')return;
    }
    ensureDocument(source,revision);const result=await applyAuthoring(request,source,revision);if(result&&descriptor.type==='loadAsset')status(words('読み込みを追加しました。続けて「画像を描く」や「音声を再生」から、使う素材を選べます。','Loading is ready. Choose Draw image or Play audio to use the resource.','加载已就绪。可选择绘制图像或播放音频使用资源。'),false);return result;
  }
  function effectiveTemplate(template){return Object.assign({},template,{source:/^1(?:\.|$)/.test(state.p5Version)&&template.sourceV1?template.sourceV1:template.source});}
  function getDynamicVariableTemplates() {
    const vars=getGlobalVariables();
    const templates=[];
    for(const v of vars){
      templates.push({
        id:'var_get_'+v.name,
        category:'variables',
        variableName:v.name,
        label:{ja:v.name,en:v.name,zh:v.name},
        source:v.name,
        isDynamicVariable:true
      });
      if(v.kind!=='const'){
        templates.push({
          id:'var_set_'+v.name,
          category:'variables',
          variableName:v.name,
          label:{ja:v.name+' を 10 にする',en:'set '+v.name+' to 10',zh:'将 '+v.name+' 设为 10'},
          source:v.name+' = 10;',
          isDynamicVariable:true
        });
        templates.push({
          id:'var_change_'+v.name,
          category:'variables',
          variableName:v.name,
          label:{ja:v.name+' を 1 ずつ変える',en:'change '+v.name+' by 1',zh:'将 '+v.name+' 增加 1'},
          source:v.name+' += 1;',
          isDynamicVariable:true
        });
      }
    }
    return templates;
  }
  async function showCreateVariableDialog(){
    if(!state.editable||state.pending)return;
    const dialog=$('actionDialog');
    $('dialogTitle').textContent=words('新しい変数を作る','Make a Variable','创建新变量');
    $('dialogDescription').textContent=words('変数名と初期値を入力してください。','Enter a variable name and initial value.','请输入变量名和初始值。');
    $('dialogChoices').replaceChildren();

    const form=document.createElement('div');
    form.style.display='flex';
    form.style.flexDirection='column';
    form.style.gap='8px';
    form.style.width='100%';

    const nameLabel=document.createElement('label');
    nameLabel.textContent=words('新しい変数名:','New variable name:','新变量名:');
    nameLabel.style.fontSize='13px';
    nameLabel.style.color='var(--muted)';

    const nameInput=document.createElement('input');
    nameInput.type='text';
    nameInput.id='varNameInput';
    nameInput.placeholder=words('スコア','score','得分');
    nameInput.autocomplete='off';
    nameInput.spellcheck=false;

    const initLabel=document.createElement('label');
    initLabel.textContent=words('初期値:','Initial value:','初始值:');
    initLabel.style.fontSize='13px';
    initLabel.style.color='var(--muted)';

    const initInput=document.createElement('input');
    initInput.type='text';
    initInput.id='varInitInput';
    initInput.value='0';
    initInput.placeholder='0';
    initInput.autocomplete='off';
    initInput.spellcheck=false;

    const errorMsg=document.createElement('p');
    errorMsg.id='varErrorMsg';
    errorMsg.style.color='var(--error,#e74c3c)';
    errorMsg.style.fontSize='13px';
    errorMsg.style.margin='4px 0 0';
    errorMsg.hidden=true;

    const btnRow=document.createElement('div');
    btnRow.style.display='flex';
    btnRow.style.justifyContent='flex-end';
    btnRow.style.gap='8px';
    btnRow.style.marginTop='6px';

    const cancelBtn=document.createElement('button');
    cancelBtn.type='button';
    cancelBtn.id='varCancelBtn';
    cancelBtn.textContent=tr('cancel');
    cancelBtn.onclick=()=>dialog.close('cancel');

    const submitBtn=document.createElement('button');
    submitBtn.type='button';
    submitBtn.className='filled';
    submitBtn.id='varSubmitBtn';
    submitBtn.textContent=words('作成','Create','创建');

    btnRow.append(cancelBtn,submitBtn);
    form.append(nameLabel,nameInput,initLabel,initInput,errorMsg,btnRow);
    $('dialogChoices').appendChild(form);

    const onSubmit=async()=>{
      const rawName=nameInput.value.trim();
      const rawInit=initInput.value.trim()||'0';
      if(!rawName){
        errorMsg.textContent=words('変数名を入力してください。','Please enter a variable name.','请输入变量名。');
        errorMsg.hidden=false;
        nameInput.focus();
        return;
      }
      const existing=getGlobalVariables();
      if(existing.some(v=>v.name===rawName)){
        errorMsg.textContent=words('「'+rawName+'」という名前の変数はすでに存在します。','Variable "'+rawName+'" already exists.','变量名「'+rawName+'」已存在。');
        errorMsg.hidden=false;
        nameInput.focus();
        nameInput.select();
        return;
      }
      submitBtn.disabled=true;
      try{
        const result=await applyAuthoring({type:'createVariable',name:rawName,init:rawInit});
        dialog.close('create');
        setPalette(false);
        if(result){
          status(words('変数「'+rawName+'」を作成しました','Created variable "'+rawName+'"','已创建变量「'+rawName+'」'),false);
          renderPalette();
        }
      }catch(err){
        submitBtn.disabled=false;
        errorMsg.textContent=err.message;
        errorMsg.hidden=false;
      }
    };

    submitBtn.onclick=onSubmit;
    nameInput.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();initInput.focus();}};
    initInput.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();onSubmit();}};

    dialog.showModal();
    nameInput.focus();
  }
  let paletteInsertMode='auto';
  function hasStatementBody(node){
    return !!node&&Object.entries(node.inputs||{}).some(([key,value])=>node.inputKinds[key]==='statement'&&Array.isArray(value));
  }
  function defaultFunctionBlock(){
    if(!state.projection?.ok||!workspace)return null;
    const functions=state.projection.nodes.filter(n=>n.kind==='function'&&!n.parentId).sort((a,b)=>({draw:0,setup:1}[a.fields?.name?.value]??2)-({draw:0,setup:1}[b.fields?.name?.value]??2));
    return functions.length?workspace.getBlockById('bound_'+functions[0].id):null;
  }
  function resolveStatementInsertion(template){
    const selected=selectedStatement(),rel=relation(selected),selectedNode=nodeFor(selected);
    if(paletteInsertMode==='tray')return {location:'tray',target:null,summary:tr('tray')};
    if(paletteInsertMode==='append'||template?.category==='events')return {location:'append',target:null,summary:tr('append')};
    if(paletteInsertMode==='before'&&rel)return {location:'before',target:selected,summary:words(`「${caption(selectedNode)}」の前`,`Before ${caption(selectedNode)}`,`「${caption(selectedNode)}」之前`)};
    if(paletteInsertMode==='inside'&&hasStatementBody(selectedNode))return {location:'inside',target:selected,summary:words(`「${caption(selectedNode)}」の中`,`Inside ${caption(selectedNode)}`,`「${caption(selectedNode)}」内部`)};
    if(paletteInsertMode==='after'&&rel)return {location:'after',target:selected,summary:words(`「${caption(selectedNode)}」の後ろ`,`After ${caption(selectedNode)}`,`「${caption(selectedNode)}」之后`)};
    if(hasStatementBody(selectedNode))return {location:'inside',target:selected,summary:words(`「${caption(selectedNode)}」の中`,`Inside ${caption(selectedNode)}`,`「${caption(selectedNode)}」内部`)};
    if(rel)return {location:'after',target:selected,summary:words(`「${caption(selectedNode)}」の後ろ`,`After ${caption(selectedNode)}`,`「${caption(selectedNode)}」之后`)};
    const fnBlock=defaultFunctionBlock(),fnNode=nodeFor(fnBlock);
    if(fnBlock&&fnNode)return {location:'inside',target:fnBlock,summary:words(`${caption(fnNode)} の中`,`Inside ${caption(fnNode)}`,`${caption(fnNode)} 内部`)};
    return {location:'append',target:null,summary:tr('append')};
  }
  function isReplaceableValueBlock(block){
    return !!block?.outputConnection&&!!modelFor(block)?.bound&&nodeFor(block.getParent?.())?.kind!=='expression_statement';
  }
  function updatePaletteTargetBar(){
    const labelEl=$('paletteTargetLabel'),btnEl=$('paletteTargetModeBtn');
    if(!labelEl||!btnEl)return;
    let summary='';
    if(paletteInsertMode==='ask'){
      summary=words('追加時に場所を選択','Ask where to insert','每次询问位置');
    }else{
      summary=resolveStatementInsertion(null).summary;
    }
    labelEl.textContent=words('処理の追加先: ','Statement target: ','语句插入位置：')+summary;
    labelEl.title=words('値は選択した値を置き換えるか未接続で追加します。イベントや素材は専用の手順で追加します。','Values replace the selected value or are added detached. Events and assets use their own steps.','值会替换所选值或作为未连接部件添加。事件和素材使用各自的步骤。');
    btnEl.textContent=words('位置変更 ▾','Position ▾','位置 ▾');
  }
  async function choosePaletteInsertMode(){
    const choices=[
      {value:'auto',label:words('自動（おすすめ・1タップ追加）','Auto (1-tap insert)','自动（一键插入）')},
      {value:'after',label:tr('after')},
      {value:'before',label:tr('before')},
      {value:'inside',label:tr('inside')},
      {value:'append',label:tr('append')},
      {value:'tray',label:tr('tray')},
      {value:'ask',label:words('毎回ダイアログで選ぶ','Ask every time','每次询问')}
    ];
    const picked=await choose(tr('insert'),words('処理ブロックの追加先を選びます。値は選択中の値を置き換えるか未接続で追加します。イベントや素材には専用の手順があります。','Choose where statements are inserted. Values replace the selected value or are added detached; events and assets use their own steps.','选择语句的插入位置。值会替换所选值或作为未连接部件添加；事件和素材使用各自的步骤。'),choices);
    if(picked&&picked!=='cancel'){
      paletteInsertMode=picked;
      updatePaletteTargetBar();
    }
  }
  function renderPalette(){
    updatePaletteTargetBar();
    $('categories').replaceChildren();const categories=['all',...Object.keys(C.CATEGORY_LABELS)];for(const id of categories){const button=document.createElement('button');button.type='button';button.textContent=id==='all'?tr('all'):label(C.CATEGORY_LABELS[id]);button.setAttribute('aria-pressed',String(category===id));button.onclick=()=>{category=id;renderPalette();};$('categories').appendChild(button);}
    const q=$('search').value.trim().toLocaleLowerCase();
    const dynamicVars=getDynamicVariableTemplates();
    const allTemplates=[...dynamicVars,...C.TEMPLATES.map(effectiveTemplate)];
    const entries=allTemplates.filter(t=>!t.hiddenFromPalette&&supportedTemplate(t)).filter(t=>(category==='all'||t.category===category)&&(!q||(t.id+' '+label(t.label)+' '+(t.source||'')).toLocaleLowerCase().includes(q)));
    if(q){
      const rank=t=>{const id=t.id.toLocaleLowerCase(),lbl=label(t.label).toLocaleLowerCase();return id===q||lbl===q?0:id.startsWith(q)||lbl.startsWith(q)?1:id.includes(q)||lbl.includes(q)?2:3;};
      entries.sort((a,b)=>rank(a)-rank(b));
    }
    $('paletteItems').replaceChildren();
    if(category==='variables'||(!category||category==='all')&&(!q||q.includes('変')||q.includes('变')||q.includes('var')||q.includes('make'))){
      const makeVarBtn=document.createElement('button');
      makeVarBtn.type='button';
      makeVarBtn.className='palette-make-var-btn';
      makeVarBtn.textContent=words('＋ 変数を作る','+ Make a Variable','＋ 创建变量');
      makeVarBtn.disabled=!state.editable||state.pending;
      makeVarBtn.onclick=()=>showCreateVariableDialog();
      $('paletteItems').appendChild(makeVarBtn);
    }
    for(const template of entries){const button=document.createElement('button');button.type='button';button.className='palette-item';const chip=document.createElement('span');chip.className='chip';chip.style.background=categoryColor(template.category);const texts=document.createElement('span');texts.className='texts';const title=document.createElement('span');title.className='title';title.textContent=label(template.label);const code=document.createElement('small');code.textContent=template.authoringAction?words('素材や追加する処理を選んで使います。','Choose a resource or a handler to use this block.','选择资源或事件后使用此积木。'):template.source.replace(/\s+/g,' ');texts.append(title,code);button.append(chip,texts);button.onclick=()=>showInsertion(template);$('paletteItems').appendChild(button);}if(!entries.length&&!$('paletteItems').children.length)$('paletteItems').textContent=tr('empty');
  }
  function choose(title,description,choices,onChoose){
    return new Promise(resolve=>{
      const dialog=$('actionDialog');
      $('dialogTitle').textContent=title;$('dialogDescription').textContent=description||'';$('dialogChoices').replaceChildren();
      for(const item of choices){
        const button=document.createElement('button');button.type='button';
        if(item.icon)button.dataset.icon=item.icon;button.textContent=item.label;
        // Simple field choices commit before a native save/flush can overtake
        // the queued close event. Multi-step dialogs wait for close as usual.
        button.onclick=()=>{onChoose?.(item.value);dialog.close(item.value);};
        $('dialogChoices').appendChild(button);
      }
      const done=()=>{dialog.removeEventListener('close',done);resolve(dialog.returnValue||'cancel');};
      dialog.addEventListener('close',done);dialog.returnValue='cancel';dialog.showModal();
    });
  }

  function selectedStatement(){let block=selectedBlock();if(!block||block.workspace!==workspace)return null;while(block&&!block.previousConnection)block=block.getParent();return block===rootBlock?null:block;}
  function templateExpression(template){return !/[;}\n]\s*$/.test(template.source)&&!/^\s*(function|if|for|let|const|return)\b/.test(template.source);}
  async function showInsertion(template){
    if(!state.editable||state.pending)return;if(template.authoringAction){try{await authorTemplate(template);}catch(error){await rollback(error.message);}return;}if(template.requiresWebGL){const source=state.source,revision=state.revision;try{const info=await authoringInspect();ensureDocument(source,revision);if(!info.webgl){if(!await confirmWebGL(source,revision))return;return showInsertion(template);}}catch(error){await rollback(error.message);return;}}
    const source=state.source,revision=state.revision,selected=selectedStatement(),rel=relation(selected),valueBlock=selectedBlock(),isExpr=templateExpression(template);
    const useOneTap=paletteInsertMode!=='ask'&&(!isExpr||document.body.classList.contains('native-chrome'));
    if(useOneTap){
      try{
        ensureDocument(source,revision);
        safeVibrate([10]);
        if(isExpr){
          const replaceTarget=paletteInsertMode!=='tray'&&isReplaceableValueBlock(valueBlock)?valueBlock:null;
          await addTemplate(template.id,replaceTarget?'replace':'tray',replaceTarget);
        }else{
          const resolved=resolveStatementInsertion(template);
          await addTemplate(template.id,resolved.location,resolved.target);
        }
      }catch(error){await rollback(error.message);}
      return;
    }
    const choices=[];
    if(!isExpr&&rel){choices.push({value:'before',label:tr('before')},{value:'after',label:tr('after')});}
    const selectedNode=nodeFor(selected);
    if(!isExpr&&selectedNode&&Object.entries(selectedNode.inputs).some(([key,value])=>selectedNode.inputKinds[key]==='statement'&&Array.isArray(value)))choices.push({value:'inside',label:tr('inside')});
    if(!isExpr&&template.category!=='events'){
      const functions=state.projection.nodes.filter(n=>n.kind==='function'&&!n.parentId).sort((a,b)=>({draw:0,setup:1}[a.fields.name.value]??2)-({draw:0,setup:1}[b.fields.name.value]??2));
      for(const fn of functions){const name=fn.fields.name.value;choices.push({value:'into:'+fn.id,label:language==='ja'?name+' の中に追加':language==='zh'?'添加到 '+name+' 内':'Add inside '+name});}
    }
    if(isExpr&&valueBlock?.outputConnection&&modelFor(valueBlock)?.bound)choices.push({value:'replace',label:tr('replace')});
    if(!isExpr)choices.push({value:'append',label:tr('append')});
    choices.push({value:'tray',label:tr('tray')});const location=await choose(tr('insert'),label(template.label),choices);if(location==='cancel')return;
    try{ensureDocument(source,revision);await addTemplate(template.id,location.startsWith('into:')?'inside':location,location==='replace'?valueBlock:location.startsWith('into:')?workspace.getBlockById('bound_'+location.slice(5)):selected);}catch(error){await rollback(error.message);}
  }
  async function addTemplate(id,location,selected){
    const originalTemplate=getDynamicVariableTemplates().find(t=>t.id===id)||C.TEMPLATES.find(t=>t.id===id),template=originalTemplate&&effectiveTemplate(originalTemplate);if(!template)throw new Error('Unknown block');if(template.authoringAction)return authorTemplate(template);if(!state.editable||state.pending)throw new Error(tr('readOnly'));
    if(template.category==='sound'&&!state.soundEnabled)status(tr('sound'),false);
    const editSource=state.source,editRevision=state.revision;
    setPalette(false);const expression=templateExpression(template);
    if(location==='replace'&&expression){const value=selected||selectedBlock(),node=nodeFor(value);if(!node||value.workspace!==workspace||workspace.getBlockById(value.id)!==value||!modelFor(value)?.bound)throw new Error(tr('unsupported'));const result=await ask('proposePatch',{projection:state.projection,operations:{type:'replace',id:node.id,source:template.source}});if(!result.ok)throw new Error(authoringError(result));await submit(result.patches,await snapshotMetadata(),editSource,editRevision);return;}
    if(location==='tray'||expression){const parsed=await parseTemplate(template.source,expression);ensureDocument(editSource,editRevision);quiet(()=>{let previous=null,first=null;const suffix='t'+Date.now().toString(36);for(const nodeId of parsed.ids){const block=createTree(parsed.projection,nodeId,false,suffix);if(!first)first=block;if(previous?.nextConnection&&block.previousConnection)previous.nextConnection.connect(block.previousConnection);previous=block;}if(first){first.rinOrphanId=suffix;presentation.placeDetached(first);first.select();}});await submit([],await snapshotMetadata(),editSource,editRevision);return;}
    const rel=relation(selected||selectedStatement()),operation={type:'insert',parentId:null,input:'root',index:state.projection.rootIds.length,source:template.source};
    if((location==='before'||location==='after')&&rel){operation.parentId=rel.parentId;operation.input=rel.input;operation.index=rel.index+(location==='after'?1:0);}
    if(location==='inside'){const target=selected||selectedStatement(),node=nodeFor(target);const key=Object.keys(node?.inputs||{}).find(key=>node.inputKinds[key]==='statement'&&Array.isArray(node.inputs[key]));if(!key)throw new Error(tr('unsupported'));operation.parentId=node.id;operation.input=key;operation.index=node.inputs[key].length;}
    if(template.category==='events'&&state.projection.nodes.some(n=>n.kind==='function'&&n.fields.name?.value===template.id))throw new Error(language==='ja'?'同じイベント関数がすでにあります。その中へ処理を追加してください。':language==='zh'?'该事件函数已存在。请向其中添加语句。':'This event function already exists. Add statements inside it.');
    const operations=[];
    if(/\bawait\b/.test(template.source)&&!/^1(?:\.|$)/.test(state.p5Version)&&operation.parentId){
      let owner=state.projection.nodes.find(n=>n.id===operation.parentId);
      while(owner&&owner.kind!=='function')owner=state.projection.nodes.find(n=>n.id===owner.parentId);
      if(owner&&!owner.async)operations.push({type:'setField',id:owner.id,field:'async',value:true});
    }
    operations.push(operation);
    const result=await ask('proposePatch',{projection:state.projection,operations});if(!result.ok)throw new Error(authoringError(result));await submit(result.patches,await snapshotMetadata(),editSource,editRevision);
  }
  async function replaceExample(id){const example=C.EXAMPLES.find(e=>e.id===id);if(!example)throw new Error('Unknown example');if(!state.editable||state.pending)throw new Error(tr('readOnly'));if(example.requiresSound&&!state.soundEnabled){status(tr('sound'),true);send({type:'openRuntime'});return false;}await submit([{start:0,end:state.source.length,text:example.source}],JSON.stringify({version:1,engine:null,orphans:[]}));return true;}
  async function duplicateBlock(block){if(!state.editable||state.pending)throw new Error(tr('readOnly'));const editSource=state.source,editRevision=state.revision;const source=await serialize(block),parsed=await parseTemplate(source,!!block.outputConnection),xy=block.getRelativeToSurfaceXY();ensureDocument(editSource,editRevision);quiet(()=>{let previous=null,first=null;const suffix='d'+Date.now().toString(36);for(const nodeId of parsed.ids){const copy=createTree(parsed.projection,nodeId,false,suffix);if(!first)first=copy;if(previous?.nextConnection&&copy.previousConnection)previous.nextConnection.connect(copy.previousConnection);previous=copy;}if(first){first.rinOrphanId=suffix;presentation.placeDetached(first,{x:xy.x+80,y:xy.y+80});first.select();}});await submit([],await snapshotMetadata(),editSource,editRevision);}
  $('paletteToggle').addEventListener('pointerdown',()=>{const block=Blockly.getSelected();pendingPaletteSelection=block?.workspace===workspace?(models.has(block.id)?block.id:null):selectedBlockId;});
  $('paletteToggle').onclick=()=>setPalette($('palette').hidden);$('paletteClose').onclick=()=>setPalette(false);$('sheetScrim').onclick=()=>setPalette(false);$('search').oninput=renderPalette;
  if($('paletteTargetModeBtn'))$('paletteTargetModeBtn').onclick=()=>choosePaletteInsertMode();
  $('more').onclick=event=>{event.stopPropagation();if(!$('palette').hidden)setPalette(false);setMore($('moreMenu').hidden);};
  $('zoomReset').onclick=()=>{setMore(false);if(workspace){workspace.setScale(1);workspace.scroll(0,0);}};
  $('fullscreen').onclick=()=>{setMore(false);historyAction('toggleFullscreen');};
  $('codePreview').onclick=$('menuCodePreview').onclick=()=>{setMore(false);showCodePreview(null);};
  $('menuFit').onclick=()=>{setMore(false);workspace?.zoomToFit();};
  const codeDialog=$('codeDialog');
  codeDialog?.addEventListener('click',event=>{
    if(event.target===codeDialog){
      const rect=codeDialog.getBoundingClientRect();
      const inDialog=(rect.top<=event.clientY&&event.clientY<=rect.bottom&&rect.left<=event.clientX&&event.clientX<=rect.right);
      if(!inDialog)codeDialog.close();
    }
  });
  const actionDialog=$('actionDialog');
  actionDialog?.addEventListener('click',event=>{
    if(event.target===actionDialog){
      const rect=actionDialog.getBoundingClientRect();
      const inDialog=(rect.top<=event.clientY&&event.clientY<=rect.bottom&&rect.left<=event.clientX&&event.clientX<=rect.right);
      if(!inDialog)actionDialog.close('cancel');
    }
  });
  $('undo').onclick=()=>historyAction('undo');$('redo').onclick=()=>historyAction('redo');$('runtime').onclick=()=>{setMore(false);historyAction('openRuntime');};$('fit').onclick=()=>workspace?.zoomToFit();
  $('help').onclick=()=>{setMore(false);choose(tr('help'),tr('helpBody'),[]);};
  $('examples').onclick=async()=>{setMore(false);if(!state.editable||state.pending)return;const source=state.source,revision=state.revision;try{const id=await choose(tr('examples'),'',C.EXAMPLES.map(e=>({value:e.id,label:label(e.label)})));if(id==='cancel')return;ensureDocument(source,revision);const confirmed=await choose(tr('replaceTitle'),tr('replaceDescription'),[{value:'replace',label:tr('confirm')}]);if(confirmed==='replace'){ensureDocument(source,revision);await replaceExample(id);}}catch(error){await rollback(error.message);}};
  addEventListener('pointerdown',event=>{if(!$('moreMenu').hidden&&!(event.target instanceof Element&&event.target.closest('#moreMenu,#more')))setMore(false);},true);
  addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='f'&&!['INPUT','TEXTAREA'].includes(event.target.tagName)){event.preventDefault();if(document.body.classList.contains('native-chrome'))send({type:'openSearch'});else presentation.openNavigator();return;}if(event.key==='Escape'){setPalette(false);setMore(false);const cd=$('codeDialog');if(cd?.open)cd.close();}if((event.ctrlKey||event.metaKey)&&event.target.tagName!=='INPUT'&&event.target.tagName!=='TEXTAREA'){const key=event.key.toLowerCase();if(['z','y'].includes(key)){event.preventDefault();event.stopImmediatePropagation();historyAction(key==='y'||event.shiftKey?'redo':'undo');}else if(key==='d'){event.preventDefault();event.stopImmediatePropagation();const block=Blockly.getSelected();if(block&&models.has(block.id))duplicateBlock(block).catch(error=>rollback(error.message));}else if(['c','x','v'].includes(key)){event.preventDefault();event.stopImmediatePropagation();status(language==='ja'?'ブロックの複製はメニューの「部品として複製」を使ってください。コードのコピー・貼り付けはコード画面で行えます。':language==='zh'?'请使用菜单中的“复制为未连接积木”。复制和粘贴代码请使用代码编辑器。':'Use “Duplicate as detached block” in the block menu. Copy and paste JavaScript in the code editor.',false);}}},true);
  addEventListener('message',event=>{
    let message;try{message=typeof event.data==='string'?JSON.parse(event.data):event.data;}catch(_){return;}
    if(!message||message.type!=='connect'||typeof message.sessionId!=='string'||event.ports.length!==1)return;
    if(event.origin&&event.origin!=='null'&&event.origin!==location.origin)return;
    if(port)port.close();state.sessionId=message.sessionId;port=event.ports[0];port.onmessage=async e=>{let item;try{item=typeof e.data==='string'?JSON.parse(e.data):e.data;}catch(_){return;}if(!item||item.sessionId!==state.sessionId)return;if(item.type==='load')await loadDocument(item);else if(item.type==='uiCommand'){try{await nativeChrome.command(item);}catch(error){failOperation(String(error.message));}}else if(item.type==='viewState'){state.fullscreen=!!item.fullscreen;updateFullscreenControl();resizeViewport();}else if(item.type==='ack')await handleAck(item);else if(item.type==='flush'){let ok=false,errorDetail=null,draft=null;try{ok=await flushAll(item.hideChaff!==false,value=>{draft=value===null?null:Object.assign({requestId:item.requestId},value);});}catch(error){errorDetail=String(error.stack||error.message);if(item.hideChaff!==false)status(String(error.message),true);}send({type:'flushed',requestId:item.requestId,ok,errorDetail,draft});}else if(item.type==='errors'){state.errors=Array.isArray(item.errors)?item.errors:[];highlightRuntimeErrors();}};port.start();send({type:'ready'});
  });
  $('status').onclick=()=>{
    if(!$('status').classList.contains('error'))return;
    const activeErr=actionBlock?.rinRuntimeError||(state.errors&&state.errors[0]);
    if(!activeErr)return;
    const blk=(actionBlock?.rinRuntimeError?actionBlock:null)||findBestBlockForError(activeErr);
    if(blk){
      workspace?.centerOnBlock(blk.id);
      showBlockActions(blk);
    }
    showErrorDetail(blk,activeErr);
  };
  Object.defineProperty(window,'RinBlocksEditor',{value:Object.freeze({get workspace(){return workspace;},get projection(){return state.projection;},get document(){return Object.freeze({source:state.source,revision:state.revision,metadata:state.metadata,workId:state.workId,sessionId:state.sessionId,editable:state.editable&&!state.loading&&!state.processing,readOnly:state.readOnly,pending:state.pending,loading:!!state.loading,processing:!!state.processing});},get status(){return $('status').textContent;},showCodePreview,showBlockActions,chooseColor,jumpToCode,safeVibrate,editStructure,authorTemplate,authoringInspect,addTemplate,replaceExample,showCreateVariableDialog,getGlobalVariables,highlightRuntimeErrors,friendlyErrorMessage,showErrorDetail,clearAllBlockErrors,findBestBlockForError,flush:flushAll})});
  presentation=EditRinBlockPresentation.create({workspace:()=>workspace,workId:()=>state.workId,ready:()=>!state.loading,selectedId:()=>selectedBlock()?.id,model:modelFor,node:nodeFor,projectionNode,caption,words,tr,send,actions:showBlockActions,flush:flushAll,preferences:()=>state.displayPreferences,applyPreferences:applyDisplayPreferences,closeOverlays:()=>{setPalette(false);setMore(false);hideBlockActions();}});
  nativeChrome=EditRinBlockChrome.create({document:()=>({workId:state.workId,revision:state.revision,notice:state.readOnly||!state.editable||(!state.soundEnabled&&!!state.projection?.nodes.some(node=>node.requirements?.sound)),editable:state.editable&&!state.loading&&!state.processing&&!state.pending,pending:!!state.pending,loading:!!state.loading,processing:!!state.processing}),send,parts:()=>workspace?.getTopBlocks(false).filter(block=>block.id!=='rin_program'&&modelFor(block)).length||0,search:(mode,query)=>presentation.searchResults(mode,query),locate:id=>presentation.locate(id),flush:flushAll,applyPreferences:applyDisplayPreferences,closeOverlays:()=>{setPalette(false);setMore(false);hideBlockActions();}});
  $('statusDismiss').onclick=()=>{clearOperationError();highlightRuntimeErrors();};
  status(tr('connecting'),false);localizedUI();
})();
