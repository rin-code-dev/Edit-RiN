/* Presentation-only controls. They never write project source or authoring metadata. */
(function () {
  'use strict';
  window.EditRinBlockPresentation = Object.freeze({create(ctx) {
    const $=id=>document.getElementById(id),words=(...values)=>ctx.words(...values);
    let viewTimer=null,mode='search',pendingFocus=null,returnFocus=null;
    function localize(){
      const labels={workspaceSearch:words('作品内を探す','Find in work','在作品中查找'),outline:words('処理の一覧','Functions and events','函数与事件'),displaySettings:words('表示設定','Display settings','显示设置')};
      for(const [id,label]of Object.entries(labels)){$(id).title=label;$(id).setAttribute('aria-label',label);if($(id+'Label'))$(id+'Label').textContent=label;}
      $('menuCodePreviewLabel').textContent=ctx.tr('codePreview');$('menuFitLabel').textContent=ctx.tr('fit');
      $('navigatorClose').textContent=ctx.tr('close');$('statusDismiss').setAttribute('aria-label',words('操作メッセージを閉じる','Dismiss operation message','关闭操作提示'));
      syncBadge();
    }
    function sendView(){
      clearTimeout(viewTimer);const workspace=ctx.workspace();if(!workspace||!ctx.workId()||!ctx.ready())return;
      ctx.send({type:'view',viewport:{x:workspace.scrollX,y:workspace.scrollY,scale:workspace.scale,selected:ctx.selectedId()||null}});
    }
    function scheduleView(){clearTimeout(viewTimer);viewTimer=setTimeout(sendView,150);}
    const isDetachedRoot=block=>!!block&&block.id!=='rin_program'&&!block.getParent()&&!!ctx.model(block)&&!ctx.model(block).bound;
    const parts=()=>ctx.workspace()?.getTopBlocks(false).filter(isDetachedRoot)||[];
    function syncBadge(){
      const count=parts().length,button=$('partsBadge');button.hidden=!count;
      button.textContent=words('未接続 '+count,'Detached '+count,'未连接 '+count);
      button.title=words('未接続の部品は保存されますが実行されません。','Detached parts are saved but do not run.','未连接的部件会保存，但不执行。');
    }
    function decorateParts(){
      const workspace=ctx.workspace();if(!workspace)return;
      for(const block of workspace.getAllBlocks(false)){
        const detached=isDetachedRoot(block);
        block.getSvgRoot()?.classList.toggle('rin-detached',!!detached);
        if(block.getInput('DETACHED_STATUS'))block.removeInput('DETACHED_STATUS');
        if(detached){
          const rawText=ctx.node(block)?.raw||ctx.model(block)?.easyItem?.summary||'';
          block.setTooltip(words('未接続の部品です。作品の処理につなぐまで実行されません。','Detached part. Connect it to the program to run it.','未连接的部件。连接到程序后才会执行。')+(rawText?'\n'+rawText.slice(0,1000):''));
        }
      }
      syncBadge();
    }
    function focus(block){
      if(!block||block.isDisposed?.()||block.workspace!==ctx.workspace())return;
      const workspace=ctx.workspace();Blockly.getFocusManager().focusNode(block);block.select();workspace.centerOnBlock(block.id);ctx.actions(block);scheduleView();
    }
    function afterRender(){
      if(pendingFocus){const block=parts().find(block=>(block.rinOrphanId||block.id)===pendingFocus);pendingFocus=null;focus(block);}
      syncBadge();
      if($('navigatorDialog').open){renderTabs();renderResults();}
    }
    function bounds(block){const r=block.getBoundingRectangle();return {left:r.left,top:r.top,right:r.right,bottom:r.bottom};}
    function placeDetached(block,preferred){
      const workspace=ctx.workspace(),scale=workspace.scale,metrics=workspace.getMetrics();
      decorateParts();block.render();
      const boxes=workspace.getTopBlocks(false).filter(other=>other!==block).map(bounds);
      const size=bounds(block),width=size.right-size.left,height=size.bottom-size.top,gap=32;
      const left=Math.max(24,metrics.viewLeft/scale+24),top=Math.max(40,metrics.viewTop/scale+40);
      const candidates=[...(preferred?[preferred]:[]),{x:left,y:top}];
      for(const box of boxes)candidates.push({x:box.right+gap,y:top},{x:left,y:box.bottom+gap});
      const free=point=>boxes.every(box=>point.x+width+gap<=box.left||point.x>=box.right+gap||point.y+height+gap<=box.top||point.y>=box.bottom+gap);
      const point=candidates.find(free)||{x:Math.max(left,...boxes.map(box=>box.right+gap)),y:top};
      const current=block.getRelativeToSurfaceXY();block.moveBy(point.x-current.x,point.y-current.y);
      pendingFocus=block.rinOrphanId||block.id;
    }
    function describe(block){
      const node=ctx.node(block),model=ctx.model(block);if(!node||!model)return null;
      let parent=node,owner='';
      while(parent?.parentId){parent=ctx.projectionNode(model.projection,parent.parentId);if(parent?.kind==='function'){owner=parent.fields.name?.value||'';break;}}
      const displayNode=node.kind==='expression_statement'?ctx.projectionNode(model.projection,node.inputs.expression)||node:node;
      const title=model.easyItem?.title||(ctx.caption(displayNode)),source=node.raw.replace(/\s+/g,' ').trim();
      const root=block.getRootBlock();
      return {block,node,title,source,owner,detached:root.id!=='rin_program'&&!ctx.model(root)?.bound};
    }
    function findEntries(nextMode,search){
      const query=search.trim().toLocaleLowerCase(),workspace=ctx.workspace();
      let entries=(nextMode==='parts'?parts():workspace?.getAllBlocks(false)||[]).map(describe).filter(Boolean);
      if(nextMode==='search')entries=entries.filter(entry=>!['expression_statement','parenthesized'].includes(entry.node.kind));
      if(nextMode==='functions')entries=entries.filter(entry=>entry.node.kind==='function'&&!entry.detached);
      entries=entries.filter(entry=>!query||(entry.title+' '+entry.owner+' '+entry.source).toLocaleLowerCase().includes(query));
      entries.sort((a,b)=>Number(a.detached)-Number(b.detached)||a.node.start-b.node.start);
      return entries;
    }
    function entryDetail(entry){return [entry.detached?words('未接続 · 実行しません','Detached · does not run','未连接 · 不执行'):entry.owner,entry.source.slice(0,100)].filter(Boolean).join(' · ');}
    function searchResults(nextMode,query){
      const entries=findEntries(nextMode,query);
      return {total:entries.length,entries:entries.slice(0,100).map(entry=>({id:entry.block.id,title:entry.title.slice(0,240),detail:entryDetail(entry).slice(0,300)}))};
    }
    function renderResults(){
      const entries=findEntries(mode,$('navigatorSearch').value);
      const fragment=document.createDocumentFragment();
      for(const entry of entries.slice(0,100)){
        const button=document.createElement('button');button.type='button';
        const title=document.createElement('span');title.textContent=entry.title;
        const detail=document.createElement('small');detail.textContent=entryDetail(entry);
        button.append(title,detail);button.onclick=()=>{const current=ctx.workspace()?.getBlockById(entry.block.id);if(!current||current.isDisposed?.()){renderResults();return;}returnFocus=null;$('navigatorDialog').close();focus(current);};fragment.appendChild(button);
      }
      $('navigatorResults').replaceChildren(fragment);
      $('navigatorHint').textContent=!entries.length?words('一致するブロックがありません。','No matching blocks.','没有匹配的积木。'):entries.length>100?words('100件を表示しています。検索して絞り込めます。','Showing 100 results. Search to narrow the list.','显示 100 个结果，请搜索以缩小范围。'):words(entries.length+'件 · 選ぶとその場所へ移動します。',entries.length+' results · Select to locate.',entries.length+' 个结果 · 选择以定位。');
    }
    function renderTabs(){
      const host=$('navigatorTabs');if(!host)return;
      const partCount=parts().length;
      const tabs=[
        ['search',words('すべて','All','全部')],
        ['functions',words('処理の一覧','Functions','函数')],
        ['parts',words('未接続','Detached','未连接')+(partCount>0?' ('+partCount+')':'')]
      ];
      host.replaceChildren(...tabs.map(([id,title])=>{
        const btn=document.createElement('button');btn.type='button';btn.setAttribute('role','tab');
        btn.setAttribute('aria-selected',String(mode===id));
        btn.textContent=title;
        btn.onclick=()=>{mode=id;renderTabs();renderResults();};
        return btn;
      }));
    }
    function openNavigator(nextMode='search'){
      if(!ctx.workspace())return;
      ctx.closeOverlays();returnFocus=document.activeElement;mode=nextMode;
      $('navigatorTitle').textContent=words('作品内を探す','Find in work','在作品中查找');
      const label=words('名前・関数・コードで検索','Search names, functions or code','搜索名称、函数或代码');
      $('navigatorSearchLabel').textContent=label;$('navigatorSearch').placeholder=label;$('navigatorSearch').value='';
      renderTabs();renderResults();$('navigatorDialog').showModal();$('navigatorSearch').focus();
    }
    async function showDisplaySettings(){
      if(!await ctx.flush())return;
      ctx.closeOverlays();const dialog=$('actionDialog');
      $('dialogTitle').textContent=words('表示設定','Display settings','显示设置');
      $('dialogDescription').textContent=words('文字の大きさとブロックの間隔を選べます。','Choose the text size and spacing between blocks.','选择文字大小和积木之间的间距。');
      const settings=ctx.preferences(),label=document.createElement('label');label.className='display-setting';
      const text=document.createElement('span'),slider=document.createElement('input');slider.type='range';slider.min='12';slider.max='22';slider.step='.5';slider.value=String(settings.fontSize);
      slider.setAttribute('aria-label',words('ブロックの文字サイズ','Block text size','积木文字大小'));
      const updateLabel=()=>{text.textContent=words('文字サイズ: ','Text size: ','文字大小：')+slider.value;};slider.oninput=updateLabel;updateLabel();label.append(text,slider);
      const densityLabel=document.createElement('label');densityLabel.className='display-setting';densityLabel.textContent=words('表示の間隔','Spacing','显示间距');
      const density=document.createElement('select');for(const [value,title]of [['standard',words('標準','Standard','标准')],['comfortable',words('ゆったり（タッチ向け）','Roomy (touch)','宽松（触控）')]]){const option=document.createElement('option');option.value=value;option.textContent=title;density.appendChild(option);}density.value=settings.density;densityLabel.appendChild(density);
      const apply=document.createElement('button');apply.type='button';apply.className='filled';apply.textContent=words('適用','Apply','应用');
      apply.onclick=async()=>{apply.disabled=true;dialog.close('apply');await ctx.applyPreferences({fontSize:Number(slider.value),density:density.value});sendView();};
      $('dialogChoices').replaceChildren(label,densityLabel,apply);dialog.showModal();
    }
    $('workspaceSearch').onclick=()=>openNavigator();$('outline').onclick=()=>openNavigator('functions');$('partsBadge').onclick=()=>openNavigator('parts');
    $('displaySettings').onclick=showDisplaySettings;$('navigatorSearch').oninput=renderResults;$('navigatorClose').onclick=()=>$('navigatorDialog').close();
    $('navigatorDialog').addEventListener('close',()=>returnFocus?.isConnected&&returnFocus.focus());
    return Object.freeze({localize,sendView,scheduleView,decorateParts,afterRender,placeDetached,openNavigator,showDisplaySettings,searchResults,locate:id=>focus(ctx.workspace()?.getBlockById(id))});
  }});
})();
