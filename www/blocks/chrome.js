/* Native editor chrome uses the same editing commands as the standalone page. */
(function(){
  'use strict';
  window.EditRinBlockChrome=Object.freeze({create(ctx){
    const $=id=>document.getElementById(id);
    let enabled=false,frame=null,last='';
    const dialogs=['actionDialog','codeDialog','navigatorDialog'];
    const fieldOverlay=()=>Array.from(document.querySelectorAll('.blocklyWidgetDiv,.blocklyDropDownDiv')).some(node=>getComputedStyle(node).display!=='none'&&getComputedStyle(node).visibility!=='hidden');
    const overlay=()=>!$('palette').hidden||dialogs.some(id=>$(id).open)||fieldOverlay();
    function publish(){
      frame=null;if(!enabled)return;
      const doc=ctx.document(),message={type:'chrome',editable:doc.editable,busy:doc.pending||doc.loading||doc.processing,error:$('status').classList.contains('error'),status:$('status').textContent.slice(0,2048),dismissable:!$('statusDismiss').hidden,notice:doc.notice,parts:ctx.parts(),overlay:overlay(),palette:!$('palette').hidden,easy:!!doc.easy};
      const serialized=JSON.stringify([doc.workId,doc.revision,message]);if(serialized===last)return;last=serialized;ctx.send(message);
    }
    function schedule(){if(enabled&&frame===null)frame=requestAnimationFrame(publish);}
    function dismiss(){
      for(const id of dialogs)if($(id).open)$(id).close('cancel');
      ctx.closeOverlays();Blockly.WidgetDiv.hide();Blockly.DropDownDiv.hideWithoutAnimation();schedule();
    }
    const observer=new MutationObserver(schedule);
    for(const id of ['status','statusDismiss','partsBadge','palette',...dialogs])observer.observe($(id),{attributes:true,childList:true,characterData:true,subtree:id==='status'});
    // Blockly's field editors live outside the workspace DOM.
    const watchField=node=>observer.observe(node,{attributes:true,attributeFilter:['style','class']});
    document.querySelectorAll('.blocklyWidgetDiv,.blocklyDropDownDiv').forEach(watchField);
    const bodyObserver=new MutationObserver(records=>{for(const record of records)for(const node of record.addedNodes)if(node.nodeType===1&&node.matches('.blocklyWidgetDiv,.blocklyDropDownDiv')){watchField(node);schedule();}});
    bodyObserver.observe(document.body,{childList:true});
    async function command(message){
      if(!enabled)return;
      const doc=ctx.document();
      if(message.workId!==doc.workId)return;
      if(message.action==='dismiss'){dismiss();return;}
      if(message.expectedRevision!==doc.revision||doc.pending||doc.loading||doc.processing)return;
      if(message.action==='search'){
        if(!['search','functions','parts'].includes(message.mode)||typeof message.query!=='string'||message.query.length>256||typeof message.requestId!=='string'||message.requestId.length>100)return;
        ctx.send(Object.assign({type:'searchResults',requestId:message.requestId},ctx.search(message.mode,message.query)));return;
      }
      if(message.action==='focus'&&typeof message.id==='string'&&message.id.length<=256){ctx.locate(message.id);return;}
      if(overlay())return;
      if(message.action==='setEasyMode'&&typeof message.easy==='boolean'){
        if(await ctx.flush()){const current=ctx.document();if(current.workId===doc.workId&&current.revision===doc.revision)await ctx.setEasyMode(message.easy);}return;
      }
      if(message.action==='toggleEasyMode'){
        if(await ctx.flush()){const current=ctx.document();if(current.workId===doc.workId&&current.revision===doc.revision)await ctx.setEasyMode(!current.easy);}return;
      }
      if(message.action==='displayPreferences'){
        const p=message.preferences;
        if(!p||typeof p.fontSize!=='number'||!Number.isFinite(p.fontSize)||p.fontSize<12||p.fontSize>22||!['standard','comfortable'].includes(p.density))return;
        if(await ctx.flush()){const current=ctx.document();if(current.workId===doc.workId&&current.revision===doc.revision)await ctx.applyPreferences(p);}return;
      }
      const controls={add:'paletteToggle',undo:'undo',redo:'redo',codePreview:'codePreview',fit:'fit',zoomReset:'zoomReset',examples:'examples',runtime:'runtime',help:'help',status:'status',statusDismiss:'statusDismiss'};
      const control=Object.hasOwn(controls,message.action)?$(controls[message.action]):null;
      if(control&&!control.disabled)control.click();
      schedule();
    }
    return Object.freeze({schedule,command,configure(value){enabled=value===true;document.body.classList.toggle('native-chrome',enabled);if(enabled){last='';schedule();}}});
  }});
})();
