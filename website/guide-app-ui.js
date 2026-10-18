/* Presentation and local interactions matched to the supplied Android screenshots.
 * The demo controllers still own drawing, values, and simulated persistence. */
(() => {
  'use strict';
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const bi = (ja,en) => `<span lang="ja">${esc(ja)}</span><span lang="en">${esc(en)}</span>`;
  const setAria = (el,ja,en) => { el.dataset.ariaJa = ja; el.dataset.ariaEn = en; el.setAttribute('aria-label', document.documentElement.lang === 'en' ? en : ja); };
  const icon = name => `<svg class="app-icon" aria-hidden="true" focusable="false"><use href="#app-icon-${name}"></use></svg>`;
  const node = (cls, html = '') => { const el = document.createElement('div'); el.className = cls; el.innerHTML = html; return el; };
  const button = (id,ja,en,glyph='') => `<button type="button" data-ui="${id}">${glyph ? icon(glyph) : ''}${bi(ja,en)}</button>`;
  const setButton = (b,ja,en,glyph,compact=false) => {
    if (!b) return;
    b.innerHTML = icon(glyph)+`<span class="${compact ? 'sr-only' : 'app-button-label'}">${bi(ja,en)}</span>`;
    b.classList.toggle('app-icon-button',compact);
  };
  const heading = (ja,en) => node('app-sheet-heading',`<h4>${bi(ja,en)}</h4>`);
  const header = (name='Practice') => node('app-topbar',`<div class="app-work-title"><small>Work</small><strong>${esc(name)}</strong></div><span class="app-header-icons" aria-hidden="true">${icon('pip_resize')}${icon('settings')}${icon('folder_code')}${icon('more_vertical')}</span>`);
  const art = () => node('app-art', '<svg viewBox="0 0 320 240" aria-hidden="true">'+Array.from({length:35},(_,i)=>`<path d="M ${60+i*2} ${42+i*2} Q ${280-i*2} ${20+i*3} ${240-i*2} ${200-i*2} Q ${40+i*3} ${210-i*2} ${60+i*2} ${42+i*2}" fill="none" stroke="${i%2?'#6a9c9e':'#d1a58a'}" stroke-width=".65"/>`).join('')+'</svg>');
  let controlId = 0;
  function labelControl(field) {
    const label = field.closest('label');
    if (!label) return;
    field.id ||= `guide-control-${++controlId}`;
    label.htmlFor = field.id;
  }
  function rangeButtons(root) {
    $$('input[type=range]',root).forEach(input=>{
      if(input.closest('.app-range'))return;
      labelControl(input);
      const wrap=node('app-range'); input.before(wrap);
      const minus=document.createElement('button'),plus=document.createElement('button');
      minus.type=plus.type='button'; minus.textContent='−';plus.textContent='+';
      setAria(minus,'減らす','Decrease');setAria(plus,'増やす','Increase');
      wrap.append(minus,input,plus);
      minus.addEventListener('click',()=>{input.stepDown();input.dispatchEvent(new Event('input',{bubbles:true}));});
      plus.addEventListener('click',()=>{input.stepUp();input.dispatchEvent(new Event('input',{bubbles:true}));});
    });
  }
  function segments(select, labels) {
    labelControl(select);
    const group=node('app-segments');select.before(group);select.classList.add('app-native-select');
    // The visible buttons replace the native select in the keyboard/accessibility tree.
    select.tabIndex=-1;select.setAttribute('aria-hidden','true');
    group.setAttribute('role','group');
    const caption=select.closest('label')?.querySelector(':scope > span');
    if(caption){caption.id ||= `guide-control-${++controlId}`;group.setAttribute('aria-labelledby',caption.id);}
    const refresh=()=>$$('button',group).forEach(b=>{b.setAttribute('aria-pressed',String(b.dataset.value===select.value));b.disabled=select.disabled;});
    [...select.options].forEach((opt,i)=>{
      const b=document.createElement('button');b.type='button';b.dataset.value=opt.value;
      b.innerHTML=labels ? bi(...labels[i]) : esc(opt.textContent);
      b.addEventListener('click',()=>{if(select.disabled)return;select.value=opt.value;select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));refresh();});group.append(b);
    });
    const observer=new MutationObserver(refresh);observer.observe(select,{attributes:true,attributeFilter:['disabled']});
    select.closest('[data-sim]')?._appUIAbort?.signal.addEventListener('abort',()=>observer.disconnect(),{once:true});
    select.addEventListener('input',refresh);select.addEventListener('change',refresh);refresh();return refresh;
  }
  function colorPicker(field, pair) {
    const open=document.createElement('button');open.type='button';open.className='app-color-swatch';
    setAria(open,'色を編集','Edit color');field.classList.add('app-color-native');pair.prepend(open);
    const modal=document.createElement('dialog');modal.className='app-color-dialog';
    modal.innerHTML=`<form method="dialog"><header><h4>${bi('色を編集','Edit color')}</h4><button type="button" data-color-cancel>${bi('キャンセル','Cancel')}</button></header><label class="control">${bi('色コード','Color code')}<input type="text" data-picker-hex maxlength="7"></label><div class="app-segments"><button type="button" data-color-tab="hsv" aria-pressed="true">HSV</button><button type="button" data-color-tab="rgb" aria-pressed="false">RGB</button></div><div data-color-space="hsv">${[['h','色相','Hue',360],['s','彩度','Saturation',100],['v','明度','Brightness',100]].map(([id,ja,en,max])=>`<label class="control">${bi(ja,en)}<input type="range" data-picker="${id}" min="0" max="${max}" step="1"></label>`).join('')}</div><div data-color-space="rgb" hidden>${['r','g','b'].map(c=>`<label class="control">${c.toUpperCase()}<input type="range" data-picker="${c}" min="0" max="255" step="1"></label>`).join('')}</div><div class="app-color-presets">${['#ef4444','#d81b60','#9333a5','#4853b5','#0284c7','#16a085'].map(c=>`<button type="button" data-preset="${c}" style="background:${c}" aria-label="${c}"></button>`).join('')}</div><button class="app-color-apply" value="apply">${bi('適用','Apply')}</button></form>`;
    const modalHeading=$('h4',modal);modalHeading.id=`guide-control-${++controlId}`;modal.setAttribute('aria-labelledby',modalHeading.id);
    field.closest('.app-screen').append(modal);
    const hex=$('[data-picker-hex]',modal),channel=id=>$(`[data-picker=${id}]`,modal);
    function setHex(value) {
      hex.value=value.toUpperCase();hex.setCustomValidity('');
      const [r,g,b]=[1,3,5].map(i=>parseInt(value.slice(i,i+2),16)/255),max=Math.max(r,g,b),min=Math.min(r,g,b),d=max-min;
      const h=d===0?0:60*(max===r?((g-b)/d+6)%6:max===g?(b-r)/d+2:(r-g)/d+4);
      const vals={r:r*255,g:g*255,b:b*255,h,s:max===0?0:d/max*100,v:max*100};Object.entries(vals).forEach(([id,v])=>{channel(id).value=Math.round(v);});
    }
    const rgbHex=rgb=>'#'+rgb.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
    modal.addEventListener('input',e=>{
      if(e.target===hex){if(/^#[\da-f]{6}$/i.test(hex.value))setHex(hex.value);else hex.setCustomValidity(document.documentElement.lang==='ja'?'#と6桁の16進数で入力してください。':'Enter # and six hexadecimal digits.');return;}
      const id=e.target.dataset.picker;if(!id)return;
      if('rgb'.includes(id)){setHex(rgbHex(['r','g','b'].map(c=>Number(channel(c).value))));return;}
      const h=Number(channel('h').value)/60,s=Number(channel('s').value)/100,v=Number(channel('v').value)/100;
      const c=v*s,x=c*(1-Math.abs(h%2-1)),m=v-c;
      const values=h<1?[c,x,0]:h<2?[x,c,0]:h<3?[0,c,x]:h<4?[0,x,c]:h<5?[x,0,c]:[c,0,x];
      // Preserve the HSV sliders while editing: RGB quantization must not move the thumb.
      hex.value=rgbHex(values.map(n=>(n+m)*255)).toUpperCase();hex.setCustomValidity('');
      [1,3,5].forEach((offset,i)=>{channel('rgb'[i]).value=parseInt(hex.value.slice(offset,offset+2),16);});
    });
    modal.addEventListener('click',e=>{if(e.target.closest('[data-color-cancel]'))modal.close('cancel');const tab=e.target.closest('[data-color-tab]');if(tab){$$('[data-color-tab]',modal).forEach(b=>b.setAttribute('aria-pressed',String(b===tab)));$$('[data-color-space]',modal).forEach(p=>p.hidden=p.dataset.colorSpace!==tab.dataset.colorTab);}const preset=e.target.closest('[data-preset]');if(preset)setHex(preset.dataset.preset);});
    modal.addEventListener('close',()=>{if(modal.returnValue==='apply' && /^#[\da-f]{6}$/i.test(hex.value)){field.value=hex.value;field.dispatchEvent(new Event('input',{bubbles:true}));}});
    open.addEventListener('click',()=>{setHex(field.value);modal.returnValue='';modal.showModal();});
    const sync=()=>{open.style.background=field.value;};field.addEventListener('input',sync);sync();return sync;
  }
  function codeView(source) {
    return source.split('\n').map((line,i)=>`<div class="app-code-line"><span class="app-line-number" aria-hidden="true">${i+1}</span><code>${line.split(/(\b(?:function|const|let|if|return|true|false)\b|#[0-9a-fA-F]{6}|\b\d+(?:\.\d+)?\b)/g).map((part,j)=>j%2?`<span class="app-token ${/^[#0-9]/.test(part)?'app-token-val':'app-token-kw'}">${esc(part)}</span>`:esc(part)).join('') || ' '}</code></div>`).join('');
  }
  function decorateLive(host, source) {
    host.classList.add('app-ui');
    const screen=$('.demo-studio',host);screen.classList.add('app-screen','app-live-screen');
    const preview=$('.demo-preview',host),tools=$('.studio-tools',host),bar=$('.preview-bar',host),actions=$('.demo-preview>.demo-actions',host);
    screen.prepend(header('Sketch / 01'));
    bar.classList.add('app-runtime-line');
    const display=node('app-editor-surface');display.tabIndex=0;setAria(display,'実行コード','Executed code');
    display.innerHTML=codeView(source.textContent);preview.append(display);
    new MutationObserver(()=>{display.innerHTML=codeView(source.textContent);}).observe(source,{childList:true,characterData:true,subtree:true});
    setButton($('[data-action=start]',host),'実行','Run','play');
    setButton($('[data-action=stop]',host),'停止','Pause','pause',true);
    setButton($('[data-action=reset]',host),'初期化','Reset','reload',true);
    actions.classList.add('app-preview-controls');
    if (tools) {
      tools.classList.add('app-sheet');
      const toggler=node('app-panel-toggle',button('toggle-tools','調整パネル','Adjust panel','tune'));
      actions.append(toggler.firstElementChild);
      const toggle=$('[data-ui=toggle-tools]',host);toggle.setAttribute('aria-expanded','true');
      toggle.addEventListener('click',()=>{tools.hidden=!tools.hidden;toggle.setAttribute('aria-expanded',String(!tools.hidden));});
      tools.prepend(node('app-sheet-handle'));
      const controls=$('.live-controls',host);
      if (['params','inline'].includes(host.dataset.live)) {
        tools.classList.add('app-parameters');
        $('h4',tools).innerHTML=bi('パラメータ','Parameters')+' <span class="app-count">5</span>';
        const toolbar=node('app-parameter-toolbar');$('h4',tools).after(toolbar);
        for (const [id,ja,en,glyph] of [['random','ランダム','Randomize','dice'],['compare','押して初期値と比較','Hold to compare','compare'],['set-defaults','現在値を初期値にする','Set as defaults','save'],['defaults','初期値に戻す','Restore default','restore']]) {
          const b=$(`[data-action=${id}]`,host);setButton(b,ja,en,glyph,['random','compare'].includes(id));toolbar.append(b);
        }
        toolbar.insertAdjacentHTML('beforeend',button('close-parameters','閉じる','Close','close'));
        $('[data-ui=close-parameters]',toolbar).addEventListener('click',()=>{toggle.click();toggle.focus();});
        const sync=[];
        $$('.parameter-row',controls).forEach(row=>{
          const pin=$('.parameter-pin',row);row.prepend(pin);
          pin.insertAdjacentHTML('beforeend',icon('pin'));
          const field=$('input:not([data-control^=pin]),select',row),label=field.closest('label');
          const key=field.dataset.control;
          const caption=node('app-parameter-key',esc(host.dataset.live==='params'?`rinParams.${key}`:key));
          label.insertBefore(caption,label.children[1] || null);
          if(field.type==='range')label.append(node('app-range-limits',`${field.min} … ${field.max} (${bi('刻み','step')}: ${field.step})`));
          if(field.tagName==='SELECT')sync.push(segments(field,[['円','circle'],['四角','square'],['三角','triangle']]));
          if(field.type==='checkbox')label.classList.add('app-switch');
          if(field.type==='color') {
            const hex=document.createElement('input');hex.type='text';hex.value=field.value.toUpperCase();hex.className='app-hex';hex.maxLength=7;setAria(hex,'色コード','Color code');
            const pair=node('app-color-pair');field.before(pair);pair.append(field,hex);
            hex.addEventListener('change',()=>{if(/^#[\da-f]{6}$/i.test(hex.value)){field.value=hex.value;field.dispatchEvent(new Event('input',{bubbles:true}));}else hex.value=field.value.toUpperCase();});
            sync.push(()=>{hex.value=field.value.toUpperCase();});
            sync.push(colorPicker(field,pair));
          }
        });
        $('.parameter-tools',host).remove();
        const readValues=()=>['size','count','ink','shape','moving'].map(k=>{const el=$(`[data-control=${k}]`,host);return el.type==='checkbox'?String(el.checked):el.value;}).join('|');
        const authoredDefaults=readValues();let baseline=authoredDefaults;
        const refresh=()=>{sync.forEach(f=>f());const changed=readValues()!==baseline;['set-defaults','defaults'].forEach(id=>{const b=$(`[data-action=${id}]`,host);if(!changed&&b===document.activeElement)$('[data-action=random]',host).focus();b.hidden=!changed;});};
        host.addEventListener('input',refresh);host.addEventListener('click',e=>{const id=e.target.closest('[data-action]')?.dataset.action;if(id==='set-defaults')baseline=readValues();if(id==='reset')baseline=authoredDefaults;queueMicrotask(refresh);});refresh();
      }
      rangeButtons(tools);
    }
    const footer=node('app-accessory',`<span aria-hidden="true">${icon('undo')}${icon('redo')}<i class="app-color-dot"></i>${icon('search')}${icon('fold')}${icon('code')}</span><small>sketch.js</small>`);preview.append(footer);
    if(host.dataset.live==='watch'){
      const watch=$('[data-watch]',host);watch.classList.add('app-watch-badge');display.after(watch);
    }
  }
  function decorateSimulation(host) {
    host.classList.add('app-ui');
    host._appUIAbort?.abort();const abort=new AbortController();host._appUIAbort=abort;
    const kind=host.dataset.sim,screen=$('.sim-panel',host),body=$('.sim-body',host),commands=$('.sim-commandbar',host),readout=$('[data-result]',host);
    screen.classList.add('app-screen');$('.sim-toolbar',host).replaceWith(header(['gallery','organize'].includes(kind)?'Practice / 01':'Sketch / 01'));
    screen.after(readout);readout.classList.add('app-outcome');
    const moveAction=(id,target,ja,en,glyph,compact=false)=>{const b=$(`[data-action="${id}"]`,host);if(b){if(glyph)setButton(b,ja,en,glyph,compact);target.append(b);}return b;};
    function sheet(ja,en,contents,cls='') {
      const s=node('app-sheet '+cls);s.append(node('app-sheet-handle'),heading(ja,en));
      for(const el of contents.filter(Boolean))s.append(el);body.append(s);return s;
    }
    function dialog(ja,en,contents,cls='') {
      const d=sheet(ja,en,contents,'app-window '+cls);d.insertAdjacentHTML('beforeend',button('close-window','閉じる','Close','close'));return d;
    }
    const on=(type,fn)=>host.addEventListener(type,fn,{signal:abort.signal});
    if (kind==='gallery') {
      $('.mock-section-label',host).innerHTML=`<span class="app-tab">${bi('自分の作品','My works')}</span><span class="app-tab selected">${bi('サンプル・読み取り専用','Samples · Read only')}</span>`;
      $$('.sim-card',host).forEach(card=>{const old=$('.mini-art',card);old.replaceChildren(art());});
      const copy=moveAction('copy',commands,'コピーして編集','Copy to edit','copy');
      commands.classList.add('app-sample-banner');commands.prepend(node('app-readonly',bi('サンプル・読み取り専用','Samples · Read only')));
      on('click',e=>{const id=e.target.closest('[data-action]')?.dataset.action;if(id==='copy'){copy.classList.add('app-copied');$('.app-readonly',host).innerHTML=bi('自分の作品','My works');}else if(id==='palette'||id==='weave'){copy.classList.remove('app-copied');$('.app-readonly',host).innerHTML=bi('サンプル・読み取り専用','Samples · Read only');}});
    } else if(kind==='editor') {
      const doc=$('.editor-document',host),accessory=$('.editor-accessory',host),find=$('[data-control=find]',host).closest('label');
      const search=node('app-search-panel');search.append(find);doc.before(search);
      const tools=node('app-accessory');
      moveAction('undo',tools,'元に戻す','Undo','undo',true);moveAction('fold',tools,'折りたたむ／展開','Fold / expand','fold',true);
      moveAction('replace',search,'circleをsquareに置換','Replace circle with square','search');
      accessory.append(tools);$('.document-tab',host).remove();
      doc.before(node('app-file-caption','sketch.js'));
    } else if(kind==='pip') {
      const win=$('.pip-window',host),tray=node('app-pip-controls');
      for(const [id,g] of [['corner','pip_resize'],['ghost','pip_opacity'],['size','fullscreen'],['minimize','pip_minimize']]) {
        const b=$(`[data-action=${id}]`,host),ja=b.querySelector('[lang=ja]').textContent,en=b.querySelector('[lang=en]').textContent;moveAction(id,tray,ja,en,g,true);
      }
      win.prepend(tray);win.insertAdjacentHTML('beforeend','<span class="app-pip-caption">PiP</span>');
      on('pointerdown',e=>{if(e.target.closest('.app-pip-controls'))e.stopPropagation();});
    } else if(kind==='files') {
      const browser=$('.mock-file-browser',host),mode=$('[data-control=mode]',host).closest('label');
      const assetRow=$('[data-file="3"]',browser);assetRow.remove();assetRow.removeAttribute('data-file');assetRow.dataset.ui='asset-row';
      const project=dialog('プロジェクトファイル','Project files',[browser]);
      const assets=dialog('作品の素材','Work assets',[node('app-asset-description',bi('画像・音声・動画・フォント・データ','Add images, audio, video, fonts and data')),assetRow]);assets.hidden=true;
      const assetPreview=node('app-asset-preview',`<strong>photo.png</strong><p>image/png · 400 × 280 px</p>`);assetPreview.append(art());assetPreview.hidden=true;assets.append(assetPreview);
      assetRow.insertAdjacentHTML('afterend',button('asset-preview','プレビュー','Preview','assets'));
      const runtime=dialog('実行環境','Runtime',[mode]);runtime.hidden=true;
      runtime.querySelector('.app-sheet-heading').after(node('app-runtime-options', `<label><input type="radio" name="guide-p5" value="2.3.4" checked><span>p5.js 2.3.4<small>${bi('標準','Current')}</small></span></label><label><input type="radio" name="guide-p5" value="1.11.5"><span>p5.js 1.11.5<small>${bi('従来版','Legacy')}</small></span></label>`));
      const loading=node('app-runtime-heading',bi('プロジェクトの読み込み設定','Project loading settings'));mode.before(loading);
      moveAction('resolve',runtime,'開始ファイルを確認','Inspect entry file','play');
      on('click',e=>{if(e.target.closest('[data-action=resolve]'))readout.insertAdjacentHTML('beforeend',`<br>p5.js ${$('input[name=guide-p5]:checked',host).value} · ${bi('模擬設定','Simulated setting')}`);});
      const nav=node('app-file-nav',button('show-files','プロジェクトファイル','Project files','folder_code')+button('show-runtime','実行環境','Runtime','code')+button('show-assets','作品の素材','Work assets','assets'));body.prepend(nav);
      $$('.file-kind',host).forEach(el=>el.innerHTML=icon('code'));
      const main=$('[data-file="0"]',host);main.insertAdjacentHTML('beforeend','<small class="app-main-badge">MAIN</small>');
      on('click',e=>{const id=e.target.closest('[data-ui]')?.dataset.ui;if(['show-files','show-runtime','show-assets'].includes(id)){project.hidden=id!=='show-files';runtime.hidden=id!=='show-runtime';assets.hidden=id!=='show-assets';}if(id==='asset-preview'||id==='asset-row'){assetPreview.hidden=!assetPreview.hidden;assetRow.setAttribute('aria-pressed',String(!assetPreview.hidden));}});
    } else if(kind==='history') {
      const preview=$('.history-preview',host),list=$('.snapshot-list',host);
      const snap=sheet('スナップショット','Snapshots',[list]);
      const save=moveAction('save',commands,'保存','Save','save'),create=moveAction('snapshot',snap,'現在の状態を保存','Snapshot current state','snapshot');list.before(create);
      moveAction('revert',commands,'保存済み状態に戻す','Revert to saved','restore');
      const diff=node('app-diff-panel');diff.hidden=true;snap.append(diff);
      const observer=new MutationObserver(()=>{
        $$('button[data-action^=restore-]',list).forEach(b=>{
          if(b.closest('.app-snapshot-card'))return;
          const card=node('app-snapshot-card');b.before(card);card.append(node('app-snapshot-meta',`<strong>${bi('スナップショット','Snapshot')} ${Number(b.dataset.action.slice(8))+1}</strong><small>sketch.js</small>`));
          const original=b.textContent.match(/\d+[^\d]+(\d+)/),value=original?.[1] || $('[data-edited]',host).textContent;
          card.append(node('app-snapshot-code',`<code>circle(200, 140, ${esc(value)});</code>`));
          const row=node('app-snapshot-actions',button('view-diff','差分を見る','View diff'));row.querySelector('button').dataset.snapshotValue=value;
          setButton(b,'復元','Restore','restore');row.append(b);card.append(row);
        });
      });observer.observe(list,{childList:true});abort.signal.addEventListener('abort',()=>observer.disconnect());
      on('click',e=>{const b=e.target.closest('[data-ui=view-diff]');if(b){diff._opener=b;diff.hidden=false;diff.innerHTML=`<h5>${bi('変更内容を確認','Review changes')}</h5><p>${bi('− 現在のコード / + 復元するコード','− Current code / + Snapshot')}</p><pre><del>− circle(200, 140, ${esc($('[data-edited]',host).textContent)});</del>\n<ins>+ circle(200, 140, ${esc(b.dataset.snapshotValue)});</ins></pre>${button('close-diff','閉じる','Close')}`;}
        if(e.target.closest('[data-ui=close-diff]')){diff.hidden=true;diff._opener?.focus();}
      });
      preview.classList.add('app-history-preview');save.classList.add('app-save-button');
    } else if(kind==='export') {
      const canvas=$('.export-canvas',host),scale=$('[data-control=scale]',host).closest('label'),format=$('[data-control=format]',host).closest('label');
      body.prepend(canvas);$('.mini-art',canvas).replaceChildren(art());
      const nav=node('app-export-tray',button('show-png','スクリーンショット','Screenshot','camera')+button('show-record','録画','Record','record'));canvas.after(nav);
      const png=dialog('PNG画像を書き出し','Export PNG image',[scale]),record=sheet('録画設定','Recording settings',[format]);record.hidden=true;
      segments($('select',scale),[['1倍','1×'],['2倍','2×'],['4倍','4×']]);
      segments($('select',format));
      moveAction('png',png,'画像を作成（模擬）','Create image (simulated)','camera');
      moveAction('record',record,'録画開始','Start recording','record');
      moveAction('finish',$('.record-indicator',host),'停止','Stop','stop',true);
      const retry=node('app-save-retry');for(const id of ['fail','retry','destination','discard']){const b=$(`[data-action=${id}]`,host);retry.append(b);}screen.after(retry);retry.classList.add('app-practice-controls');
      const receipt=$('.export-receipt',host);body.append(receipt);
      on('click',e=>{const id=e.target.closest('[data-ui]')?.dataset.ui;
        if(id==='show-png'||id==='show-record'){png.hidden=id!=='show-png';record.hidden=id!=='show-record';}
        if(e.target.closest('[data-action=record]')){record.hidden=true;png.hidden=true;$('[data-action=finish]',host).focus();}
        if(e.target.closest('[data-action=finish]'))$('[data-ui=show-record]',host).focus();
      });
    } else if(kind==='organize') {
      const choices=$('.sim-controls',host);choices.classList.add('app-work-selection');
      $$('.check',choices).forEach(label=>{const illustration=art();label.prepend(illustration);});
      const move=dialog('フォルダーに移動','Move to folder',[]),tags=dialog('選択した作品にタグ','Tag selected works',[]);
      move.append(node('app-folder-options',`<small>${bi('移動先のフォルダー','Destination folder')}</small><span class="app-tab selected">${bi('練習','Practice')}</span>`));
      moveAction('move',move,'移動','Go','folder_code');moveAction('tag',tags,'タグを追加','Add tag','tag');tags.insertBefore(node('app-tag-preview','#study'),tags.lastChild);
      tags.hidden=true;
      const nav=node('app-file-nav',button('show-move','移動','Move','folder_code')+button('show-tag','タグ','Tags','tag'));choices.after(nav);
      on('click',e=>{const id=e.target.closest('[data-ui]')?.dataset.ui;if(id==='show-move'||id==='show-tag'){move.hidden=id!=='show-move';tags.hidden=id!=='show-tag';}});
    } else if(kind==='backup') {
      $('.app-topbar',host).innerHTML=`${icon('back')}<h4>${bi('バックアップ','Backup')}</h4><span aria-hidden="true">${icon('help')}</span>`;
      const stateMap=$('.backup-map',host);screen.after(stateMap);stateMap.classList.add('app-training-state');
      const box=node('app-backup-card',`<strong>${bi('全体バックアップ','Full backup')}</strong><p>${bi('作品とエディター設定をZIPで保存・復元します','Save or restore all works and editor settings as ZIP')}</p>`);
      const row=node('app-backup-actions');moveAction('restore',row,'復元','Restore','restore');moveAction('backup',row,'ZIP保存','Save ZIP','save');box.append(row);body.prepend(box);
      const training=node('app-practice-controls');moveAction('clear',training,'移行先を空にする（練習）','Empty destination (practice)','reload');screen.after(training);
    } else if(kind==='share') {
      const preview=$('.share-card-preview',host),controls=$('.sim-controls',host);body.prepend(preview);
      const s=sheet('共有カード設定','Share Card Settings',[preview,controls]);
      s.append(commands);moveAction('share',commands,'共有画像を作成（模擬）','Create card (simulated)','share_card');
      $('[data-control=show-code]',host).closest('label').classList.add('app-switch');
      const theme=$('[data-control=theme]',host);segments(theme,[['ダーク','Dark'],['ライト','Light'],['墨','Sumi']]);
    } else if(kind==='recovery') {
      sheet('下書きの復旧（操作例）','Draft recovery (example)',[...body.children]);
    }
    on('click',e=>{const close=e.target.closest('[data-ui=close-window]');if(close){close.closest('.app-window').hidden=true;$('.app-file-nav button,.app-export-tray button',host)?.focus();}});
    // Controls without a corresponding device screenshot remain explicit input diagrams.
    if(['camera','mic','sensors','recovery'].includes(kind)) {
      host.classList.add('app-concept');
      $('.demo-status',host).innerHTML=bi('この部分は入力・復旧の流れを示す操作図です。実機画面の再現ではありません。','This diagram illustrates input or recovery; it does not reproduce a device screen.');
    }
    rangeButtons(body);
    if(!commands.children.length)commands.remove();
    on('keydown',e=>{if(e.key==='Escape'){
      const diff=$('.app-diff-panel',host);if(diff&&!diff.hidden){const focused=diff.contains(document.activeElement);diff.hidden=true;if(focused)diff._opener?.focus();}
    }});
  }
  window.GuideAppUI={decorateLive,decorateSimulation};
})();
