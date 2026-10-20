'use strict';
const $=s=>document.querySelector(s), $$=s=>document.querySelectorAll(s);
const model=HudMock;
let controllers=model.create();
model.startPod(controllers.find(w=>w.key==='pod'));
let selected='cannon',preset='tank',edit=false,paused=false,view='composition';
const configs={
  cannon:{enabled:true,grain:'feed',identity:true,stock:true,merge:true},
  coax:{enabled:true,grain:'compact',identity:true,stock:true,merge:true},
  hull:{enabled:true,grain:'compact',identity:true,stock:true,merge:true},
  roof:{enabled:true,grain:'compact',identity:true,stock:true,merge:true},
  pod:{enabled:false,grain:'tube',identity:true,stock:true,merge:false}
};
const positions={
  tank:{cannon:[1178,448,366],coax:[1212,668,332],hull:[1212,732,332],roof:[1212,796,332],pod:[44,526,430]},
  mixed:{cannon:[1178,448,366],coax:[1212,668,332],hull:[1212,732,332],roof:[1212,796,332],pod:[44,542,430]},
  strip:{cannon:[38,650,454],coax:[548,788,300],hull:[878,788,300],roof:[1208,788,340],pod:[38,340,430]}
};
let layout=structuredClone(positions.tank);
const hosts=new Map(),samples=[];
let allBindings=[];
function wcByKey(key){return controllers.find(w=>w.key===key);}
function clonePattern(name){const node=$(`#patterns [data-pattern="${name}"]`).cloneNode(true);node.removeAttribute('data-pattern');return node;}
function query(expression,snapshot,vars,config,key){
  const m=expression.match(/^(weapon|demo)\.([a-z_]+)(?:\(([^)]*)\))?$/);
  if(!m) throw new Error(`Unsupported demo expression: ${expression}`);
  const [,space,name,arg]=m;
  const index=arg===undefined?undefined:arg.startsWith('variable.')?vars[arg.slice(9)]:Number(arg);
  const t=snapshot.tubes[index],b=snapshot.belts[index];
  if(space==='weapon'){
    const item={tube_state:t?.state||'empty',tube_fill:t?.fill||0,tube_round_label:t?.roundLabel||'',tube_reload_text:t?.reloadText||'',belt_label:b?.label||'',belt_remaining:b?.remaining||0,belt_selected:b?.selected?1:0};
    return arg===undefined?snapshot[name]:item[name];
  }
  const chosen=snapshot.belts.find(item=>item.selected);
  const values={station:String(controllers.findIndex(w=>w.key===key)+1).padStart(2,'0'),short:config.identity?snapshot.short:'',stock_caption:chosen?`STORE / ${chosen.label}`:'STORE',compact_status:snapshot.compactStatus,capacity_text:snapshot.ammo_capacity>0?`/ ${snapshot.ammo_capacity}`:'',tube_number:String((index??0)+1).padStart(2,'0'),belt_pointer:b?.selected?'>':'',belt_fill:config.merge&&b?snapshot.beltFill(b.key):0,belt_status:config.merge&&b?snapshot.beltStatus(b.key):'',has_note:snapshot.note?1:0,chamber_note:snapshot.note,feed_hint:snapshot.belt_count>1?'SELECT >':'FIXED FEED'};
  return values[name]??'';
}
class Binder{
  constructor(root,key,config){
    this.root=root;this.key=key;this.config=config;this.regions=[];this.scopes=new WeakMap();this.cache=new WeakMap();
    root.querySelectorAll('[data-mm-repeat]').forEach(template=>{
      const collection=template.dataset.mmRepeat,index=template.dataset.mmIndex;
      if(template.querySelector('[data-mm-repeat]')) throw new Error('Nested repeats are outside this prototype contract');
      const end=document.createComment(`end ${collection}`);template.replaceWith(end);
      this.regions.push({collection,index,template,end,nodes:new Map()});
    });
  }
  render(snapshot){
    this.root.classList.toggle('hide-stock',!this.config.stock);
    for(const region of this.regions){
      const items=region.collection==='weapon.tubes'?snapshot.tubes:snapshot.belts;
      const keys=new Set(items.map(item=>JSON.stringify(item.key)));
      for(const [key,node] of region.nodes) if(!keys.has(key)){node.remove();region.nodes.delete(key);}
      const ordered=items.map((item,index)=>{
        const id=JSON.stringify(item.key);let node=region.nodes.get(id);
        if(!node){node=region.template.cloneNode(true);delete node.dataset.mmRepeat;delete node.dataset.mmIndex;region.nodes.set(id,node);}
        this.scopes.set(node,{[region.index]:index});return node;
      });
      let cursor=region.end;
      for(let i=ordered.length-1;i>=0;i--){const node=ordered[i];if(node.nextSibling!==cursor)region.end.parentNode.insertBefore(node,cursor);cursor=node;}
    }
    this.root.querySelectorAll('[data-mm-text],[data-mm-class],[data-mm-num]').forEach(node=>{
      let current=node,vars={};
      while(current&&current!==this.root.parentNode){if(this.scopes.has(current)){vars=this.scopes.get(current);break;}current=current.parentElement;}
      const old=this.cache.get(node)||{};
      if(node.dataset.mmText){const value=String(query(node.dataset.mmText,snapshot,vars,this.config,this.key));if(old.text!==value){node.textContent=value;old.text=value;}}
      if(node.dataset.mmClass){const value=`is-${query(node.dataset.mmClass,snapshot,vars,this.config,this.key)}`;if(old.class!==value){if(old.class)node.classList.remove(old.class);node.classList.add(value);old.class=value;}}
      if(node.dataset.mmNum){const value=`${Number(query(node.dataset.mmNum,snapshot,vars,this.config,this.key)).toFixed(2)}${node.dataset.mmUnit||''}`;if(old.num!==value){node.style.setProperty(node.dataset.mmVar,value);old.num=value;}}
      this.cache.set(node,old);
    });
    this.root.querySelectorAll('.compact').forEach(node=>node.classList.toggle('hide-stock',!this.config.stock));
  }
}
function buildComponent(key,config){
  const panel=document.createElement('article');panel.className=`hud-panel grain-${config.grain}`;panel.dataset.controller=key;
  if(config.grain==='compact')panel.append(clonePattern('compact'));
  else{
    if(config.identity)panel.append(clonePattern('identity'));
    const wc=wcByKey(key),hasBelts=wc.belts.length>0;
    const merged=config.grain==='feed'&&config.merge&&wc.tubes.length===1&&hasBelts;
    if(config.stock&&!(config.grain==='feed'&&hasBelts))panel.append(clonePattern('stock'));
    if(!merged)panel.append(clonePattern('tubes'));
    if(config.grain==='feed'&&hasBelts){panel.append(clonePattern('feed-caption'),clonePattern('belts'));if(merged)panel.append(clonePattern('note'));}
  }
  const binding=new Binder(panel,key,config);binding.render(model.snapshot(wcByKey(key)));return {panel,binding};
}
function mountHost(key){
  const prior=hosts.get(key);if(prior){prior.panel.remove();hosts.delete(key);}
  if(!configs[key].enabled)return;
  const instance=buildComponent(key,configs[key]);$('#hosts').append(instance.panel);hosts.set(key,instance);
  const [x,y,w]=layout[key];Object.assign(instance.panel.style,{left:`${x}px`,top:`${y}px`,width:`${w}px`});
  instance.panel.addEventListener('click',()=>{if(selected!==key)selectController(key);});
  instance.panel.addEventListener('pointerdown',event=>startDrag(event,key,instance.panel));
  refreshBindings();
}
function rebuildHosts(){hosts.forEach(h=>h.panel.remove());hosts.clear();Object.keys(configs).forEach(mountHost);refreshBindings();}
function mountSamples(){
  const gallery=[['sampleCompact','cannon',{grain:'compact',identity:true,stock:true,merge:true}],['sampleTube','cannon',{grain:'tube',identity:true,stock:true,merge:true}],['sampleFeed','cannon',{grain:'feed',identity:true,stock:true,merge:true}],['samplePod','pod',{grain:'tube',identity:true,stock:true,merge:false}]];
  gallery.forEach(([id,key,config])=>{const c=buildComponent(key,config);$(`#${id}`).append(c.panel);samples.push(c);});
  ['coax','hull','roof'].forEach(key=>{const c=buildComponent(key,{grain:'compact',identity:true,stock:true,merge:true});$('#sampleMGs').append(c.panel);samples.push(c);});refreshBindings();
}
function refreshBindings(){allBindings=[...hosts.values(),...samples].map(x=>x.binding);}
function render(){const snapshots=new Map(controllers.map(w=>[w.key,model.snapshot(w)]));allBindings.forEach(b=>b.render(snapshots.get(b.key)));}
function status(text){$('#simStatus').textContent=text;}
function refreshButtons(){
  const wc=wcByKey(selected),c=configs[selected];
  $('#inspectorName').textContent=`${wc.label} / ${wc.short}`;
  $$('.controller-select').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.key===selected)));
  $$('[data-grain]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.grain===c.grain)));
  $('#showIdentity').checked=c.identity;$('#showStock').checked=c.stock;$('#mergeProgress').checked=c.merge;
  $('#mergeProgress').disabled=wc.tubes.length!==1||!wc.belts.length||c.grain!=='feed';
  $('#cycle').disabled=wc.belts.length<2;
  $('#tubeTools').hidden=selected!=='pod';if(selected==='pod')$('#tubeCount').value=String(wc.tubes.length);
  const notes={compact:'L0 只显示库存与简短状态；库存不包含膛内那一发。',tube:'L1 每管一行：就绪显示膛内弹种，装填显示真实来源和剩余时间。',feed:wc.belts.length?'L2 列出供弹选项与各链库存。单管可把准备进度合入实际对应行。':'这台控制器没有可选弹链，L2 自然退为逐管读数，不生成空选项。'};
  $('#grainNote').textContent=notes[c.grain];
}
function selectController(key){selected=key;refreshButtons();}
function setupControllerList(){
  controllers.forEach(wc=>{
    const row=document.createElement('div');row.className='controller-entry';
    const enabled=document.createElement('input');enabled.type='checkbox';enabled.checked=configs[wc.key].enabled;enabled.setAttribute('aria-label',`显示${wc.label}`);enabled.dataset.enable=wc.key;
    enabled.addEventListener('change',()=>{configs[wc.key].enabled=enabled.checked;mountHost(wc.key);refreshBindings();});
    const button=document.createElement('button');button.className='controller-select';button.dataset.key=wc.key;button.setAttribute('aria-pressed',String(wc.key===selected));
    const name=document.createElement('b'),description=document.createElement('small');name.textContent=wc.label;description.textContent=wc.description;button.append(name,description);button.addEventListener('click',()=>selectController(wc.key));row.append(enabled,button);$('#controllerButtons').append(row);
  });
}
function applyPreset(name){
  preset=name;layout=structuredClone(positions[name]);
  configs.pod.enabled=name==='mixed';configs.roof.enabled=name!=='mixed';
  configs.cannon.grain=name==='strip'?'tube':'feed';configs.cannon.merge=true;
  ['coax','hull','roof'].forEach(key=>configs[key].grain='compact');
  if(name==='mixed'){
    const fresh=model.create().find(w=>w.key==='pod');controllers[controllers.findIndex(w=>w.key==='pod')]=fresh;model.startPod(fresh);
  }
  $$('[data-enable]').forEach(input=>input.checked=configs[input.dataset.enable].enabled);
  $$('[data-preset]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.preset===name)));
  $('#presetLabel').textContent={tank:'01 / TANK COMPOSITION',mixed:'02 / MIXED ARMAMENT',strip:'03 / LOWER EDGE COMPOSITION'}[name];
  rebuildHosts();refreshButtons();
}
function startDrag(event,key,panel){
  if(!edit||event.button!==0)return;
  event.preventDefault();panel.setPointerCapture(event.pointerId);selectController(key);
  const box=$('#stage').getBoundingClientRect(),scale=box.width/1600;
  const startX=event.clientX,startY=event.clientY,[left,top,width]=layout[key];
  function move(e){
    const x=Math.max(0,Math.min(1600-width,left+(e.clientX-startX)/scale));
    const y=Math.max(0,Math.min(900-panel.offsetHeight,top+(e.clientY-startY)/scale));
    layout[key]=[x,y,width];panel.style.left=`${x}px`;panel.style.top=`${y}px`;
  }
  function end(){panel.removeEventListener('pointermove',move);panel.removeEventListener('pointerup',end);panel.removeEventListener('pointercancel',end);}
  panel.addEventListener('pointermove',move);panel.addEventListener('pointerup',end);panel.addEventListener('pointercancel',end);
}
$$('[data-view]').forEach(button=>button.addEventListener('click',()=>{
  view=button.dataset.view;$$('.page-panel').forEach(panel=>panel.hidden=panel.id!==view);$$('[data-view]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));render();fit();
}));
$$('[data-preset]').forEach(button=>button.addEventListener('click',()=>{applyPreset(button.dataset.preset);status('预设由各控制器的位置与组件组合定义。');}));
$$('[data-grain]').forEach(button=>button.addEventListener('click',()=>{configs[selected].grain=button.dataset.grain;mountHost(selected);refreshButtons();}));
[['showIdentity','identity'],['showStock','stock'],['mergeProgress','merge']].forEach(([id,property])=>$(`#${id}`).addEventListener('change',event=>{configs[selected][property]=event.target.checked;mountHost(selected);refreshButtons();}));
$('#fire').addEventListener('click',()=>{const wc=wcByKey(selected);status(model.fire(wc)?`${wc.label}：已发射；空膛后准备下一发。`:`${wc.label}：没有就绪发射管。`);render();});
$('#reload').addEventListener('click',()=>{const wc=wcByKey(selected);const t=wc.tubes.find(t=>t.state==='empty');status(t&&model.begin(wc,t)?`${wc.label}：开始准备。`:'没有空膛可装填；可以先射击。');render();});
$('#cycle').addEventListener('click',()=>{const wc=wcByKey(selected);if(wc.belts.length<2)return;const index=wc.belts.findIndex(b=>b.key===wc.selected);wc.selected=wc.belts[(index+1)%wc.belts.length].key;status(`选择 ${model.currentBelt(wc).label}；膛内弹与进行中的装填保持各自来源。`);render();});
$('#restore').addEventListener('click',()=>{const fresh=model.create().find(w=>w.key===selected);controllers[controllers.findIndex(w=>w.key===selected)]=fresh;if(selected==='pod')model.startPod(fresh);mountHost(selected);refreshButtons();render();status('该控制器的示例弹药与管状态已复位。');});
$('#tubeCount').addEventListener('change',event=>{
  const wc=wcByKey('pod'),count=Number(event.target.value);
  if(count<wc.tubes.length)wc.tubes.splice(count);
  while(wc.tubes.length<count){const i=wc.tubes.length;wc.tubes.push({key:`pod-${String(i+1).padStart(2,'0')}`,state:'empty',chamber:null,source:null,loadingSource:null,elapsed:0,duration:0,fill:0});}
  wc.name=`ATGM / ${count}-TUBE POD`;model.startPod(wc);mountHost('pod');render();status(`${count} 根发射管使用同一条目模板。`);
});
$('#editLayout').addEventListener('change',event=>{edit=event.target.checked;$('#hosts').classList.toggle('editable',edit);});
$('#pause').addEventListener('change',event=>paused=event.target.checked);
$('#resetLayout').addEventListener('click',()=>{layout=structuredClone(positions[preset]);rebuildHosts();status('各控制器位置已恢复到预设。');});
function fit(){
  const viewport=$('#viewport');if(viewport.clientWidth===0)return;
  const focus=document.body.classList.contains('focused');const scale=Math.min(viewport.clientWidth/1600,viewport.clientHeight/900);
  $('#stage').style.transform=focus?`translate(-50%,-50%) scale(${scale})`:`scale(${scale})`;
}
new ResizeObserver(fit).observe($('#viewport'));window.addEventListener('resize',fit);
function focus(value){document.body.classList.toggle('focused',value);fit();(value?$('#exitFocus'):$('#focus')).focus();}
$('#focus').addEventListener('click',()=>focus(true));$('#exitFocus').addEventListener('click',()=>focus(false));document.addEventListener('keydown',event=>{if(event.key==='Escape'&&document.body.classList.contains('focused'))focus(false);});
function color(value){document.documentElement.style.setProperty('--phosphor',value);$('#customColor').value=value;$$('[data-color]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.color===value)));}
$$('[data-color]').forEach(button=>button.addEventListener('click',()=>color(button.dataset.color)));$('#customColor').addEventListener('input',event=>color(event.target.value));
$('#brightness').addEventListener('input',event=>{document.documentElement.style.setProperty('--brightness',event.target.value/100);$('#brightnessValue').textContent=`${event.target.value}%`;});
$('#opacity').addEventListener('input',event=>{document.documentElement.style.setProperty('--panel-opacity',event.target.value/100);$('#opacityValue').textContent=`${event.target.value}%`;});
$('#neutral').addEventListener('change',event=>$('#stage').classList.toggle('neutral',event.target.checked));
setupControllerList();rebuildHosts();mountSamples();refreshButtons();fit();
let previous=performance.now();
setInterval(()=>{const now=performance.now(),dt=Math.min((now-previous)/1000,.25);previous=now;if(!paused)controllers.forEach(w=>model.advance(w,dt));render();},50);
