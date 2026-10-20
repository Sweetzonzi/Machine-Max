'use strict';
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => document.querySelectorAll(selector);
const stage = $('#stage');
const feeds = [
  {name:'APHE', id:'BR-350B', ammo:24, initial:24},
  {name:'HE-FRAG', id:'OF-350', ammo:12, initial:12},
  {name:'SMOKE', id:'D-350', ammo:6, initial:6}
];
const directions = {
  field:{label:'01 / FIELD INSTRUMENT', title:'野战仪表 / Field instrument', text:'最接近参考图一。用反白标题、机械线框与逐格指示，让武器状态像设备上的读数；留给游戏场景的空间，由薄底板和局部边框共同控制。', focus:'武器轮廓 / 弹药大字 / 分段供弹', use:'第三人称 · 载具与机甲的常驻武器 HUD'},
  terminal:{label:'02 / FIRE CONTROL TERMINAL', title:'火控终端 / Fire control terminal', text:'把整组武器压进一个字符网格。列对齐、地址编号、反白选中行和短状态回执，传达专用火控计算机的秩序；大字弹量保留战斗中的扫读效率。', focus:'字符栅格 / 数据列 / 反白选中行', use:'第三人称 · 武器组与弹链管理'},
  sight:{label:'03 / OPTICAL SIGHT', title:'观瞄分划 / Optical sight', text:'把细线分划留在中心，弹量和装填压在下缘，测距与俯仰移到外围。分划与武器读数共享配色，但在游戏实现中分别属于观瞄系统与武器控制器。', focus:'中心留白 / 细分划 / 下缘读数', use:'第一人称观瞄 · 低遮挡战斗界面'}
};
let selected = 0;
let state = 'ready';
let reloadStart = 0;
let reloadTimer = null;
const reloadDuration = 3200;
const pad = value => String(value).padStart(3, '0');
function announce(message){$('#actionStatus').textContent = message;}
function fit(){
  const viewport = $('.viewport');
  const focused = document.body.classList.contains('focused');
  const scale = Math.min(viewport.clientWidth / 1440, viewport.clientHeight / 810);
  stage.style.transform = focused ? `translate(-50%, -50%) scale(${scale})` : `scale(${scale})`;
}
new ResizeObserver(fit).observe($('.viewport'));
window.addEventListener('resize', fit);
for(let i=0;i<8;i++) $('#feedSegments').append(document.createElement('i'));
function render(){
  stage.dataset.state = state;
  $('#stateSelect').value = state;
  const feed = feeds[selected];
  const status = {ready:'+ READY TO FIRE',loading:'/ RELOADING',empty:'! FEED EMPTY',fault:'X BREECH FAULT'}[state];
  $$('[data-ammo]').forEach(el => el.textContent = pad(feed.ammo));
  $$('[data-status]').forEach(el => el.textContent = status);
  $$('[data-feed]').forEach(el => el.textContent = `${feed.name} / ${feed.id}`);
  $$('[data-stock]').forEach(el => el.textContent = pad(feeds[Number(el.dataset.stock)].ammo));
  $$('[data-select]').forEach(el => {
    const on = Number(el.dataset.select) === selected;
    el.textContent = on ? '>' : '-';
    el.closest('tr').classList.toggle('selected',on);
  });
  $$('[data-belt]').forEach(el => {
    const index = Number(el.dataset.belt);
    el.setAttribute('aria-pressed',String(index === selected));
    el.lastElementChild.textContent = pad(feeds[index].ammo);
  });
  $$('#feedSegments i').forEach((el,index) => el.classList.toggle('on',index < Math.ceil(feed.ammo / 30 * 8)));
  $('#fire').disabled = state !== 'ready';
  $('#reload').disabled = state === 'fault' || state === 'loading' || feed.ammo === 30;
  $('#switchFeed').disabled = state === 'loading';
  $$('[data-belt]').forEach(el => el.disabled = state === 'loading');
  const staticTime = state === 'fault' ? 'CHECK GUN' : state === 'empty' ? 'PRESS R' : 'STANDBY';
  if(state !== 'loading'){
    $$('[data-time]').forEach(el => el.textContent = staticTime);
    $$('[data-progress]').forEach(el => el.style.width = state === 'ready' ? '100%' : '0%');
  }
}
function stopReload(){if(reloadTimer !== null) clearInterval(reloadTimer); reloadTimer = null;}
function setState(value){
  stopReload();
  if(value === 'loading'){
    state = 'ready';
    feeds[selected].ammo = Math.min(feeds[selected].ammo,24);
    startReload();return;
  }
  state = value;
  if(value === 'empty') feeds[selected].ammo = 0;
  if(value === 'ready' && feeds[selected].ammo === 0) feeds[selected].ammo = feeds[selected].initial;
  render();
}
function startReload(){
  if(state === 'fault'){announce('故障锁定 · 使用“复位”恢复');return;}
  if(state === 'loading') return;
  if(feeds[selected].ammo === 30){announce('当前弹链已满');return;}
  stopReload();
  state = 'loading'; reloadStart = performance.now();
  render();
  $('#terminalEvent').textContent = '> FEED CYCLE STARTED_';
  function tick(){
    const p = Math.min((performance.now() - reloadStart) / reloadDuration,1);
    $$('[data-progress]').forEach(el => el.style.width = `${p * 100}%`);
    $$('[data-time]').forEach(el => el.textContent = `${((1 - p)*reloadDuration/1000).toFixed(1)} S`);
    if(p === 1){stopReload(); feeds[selected].ammo = 30; state = 'ready'; render(); $('#terminalEvent').textContent = '> FEED COMPLETE / BREECH LOCKED_';announce('装填完成 · 30 发就绪');}
  }
  tick();reloadTimer = setInterval(tick,50);announce('模拟装填中 · 3.2 秒');
}
function fire(){
  if(state !== 'ready'){announce('武器未就绪 · 无法射击');return;}
  feeds[selected].ammo = Math.max(0,feeds[selected].ammo - 1);
  state = feeds[selected].ammo ? 'ready' : 'empty';
  render();$('#terminalEvent').textContent = `> SHOT / FEED ${String(selected+1).padStart(2,'0')} / ${pad(feeds[selected].ammo)} LEFT_`;announce(`${feeds[selected].name} · 已射击，剩余 ${feeds[selected].ammo} 发`);
}
function selectFeed(index){
  if(state === 'loading'){announce('装填中 · 完成后可切换弹链');return;}
  selected = index;
  if(state !== 'fault') state = feeds[selected].ammo ? 'ready' : 'empty';
  render();announce(`当前弹链：${feeds[selected].name}`);
}
$$('[data-mode]').forEach(button => {
  if(button.tagName !== 'BUTTON') return;
  button.addEventListener('click',() => {
    const mode = button.dataset.mode;stage.dataset.mode = mode;
    $$('.directions button').forEach(el => el.setAttribute('aria-pressed',String(el === button)));
    const note = directions[mode];
    $('#noteIndex').textContent = `DIRECTION 0${Object.keys(directions).indexOf(mode)+1}`;
    $('#frameLabel').textContent = note.label;$('#noteTitle').textContent = note.title;
    $('#noteText').textContent = note.text;$('#noteFocus').textContent = note.focus;$('#noteUse').textContent = note.use;
  });
});
const colorNames = ['PHOSPHOR GREEN','IVORY WHITE','AMBER PHOSPHOR','ICE BLUE'];
function setColor(value,label){
  document.documentElement.style.setProperty('--accent',value);
  $('#customColor').value = value;
  $('#colorLabel').textContent = label;
  $$('[data-color]').forEach(el => el.setAttribute('aria-pressed',String(el.dataset.color === value.toLowerCase())));
}
$$('[data-color]').forEach((button,index) => button.addEventListener('click',() => setColor(button.dataset.color,colorNames[index])));
$('#customColor').addEventListener('input',event => setColor(event.target.value,`CUSTOM / ${event.target.value.toUpperCase()}`));
$('#brightness').addEventListener('input',event => {document.documentElement.style.setProperty('--scene-brightness',event.target.value/100);$('#brightnessValue').textContent = `${event.target.value}%`;});
$('#panelOpacity').addEventListener('input',event => {document.documentElement.style.setProperty('--panel-opacity',event.target.value/100);$('#opacityValue').textContent = `${event.target.value}%`;});
$('#neutral').addEventListener('change',event => stage.classList.toggle('neutral',event.target.checked));
$('#glow').addEventListener('change',event => $('#hud').classList.toggle('glow',event.target.checked));
$('#scanlines').addEventListener('change',event => stage.classList.toggle('scan',event.target.checked));
$('#longName').addEventListener('change',event => $$('[data-weapon-name]').forEach(el => {el.textContent = event.target.checked ? '76.2 MM ZIS-5 MODEL 1942 / STABILIZED CANNON' : '76.2 MM ZIS-5 CANNON';el.title = el.textContent;}));
$('#stateSelect').addEventListener('change',event => {setState(event.target.value);announce(`示例状态：${event.target.selectedOptions[0].textContent}`);});
$('#reset').addEventListener('click',() => {stopReload();feeds.forEach(feed => feed.ammo = feed.initial);selected = 0;state = 'ready';render();$('#terminalEvent').textContent = '> FIRE CONTROL READY_';announce('示例弹药和武器状态已复位');});
$('#fire').addEventListener('click',fire);$('#reload').addEventListener('click',startReload);$('#switchFeed').addEventListener('click',() => selectFeed((selected+1)%feeds.length));
$$('[data-belt]').forEach(el => el.addEventListener('click',() => selectFeed(Number(el.dataset.belt))));
function focusMode(value){document.body.classList.toggle('focused',value);$('#focusButton').setAttribute('aria-expanded',String(value));fit();(value ? $('#exitFocus') : $('#focusButton')).focus();}
$('#focusButton').addEventListener('click',() => focusMode(true));$('#exitFocus').addEventListener('click',() => focusMode(false));
document.addEventListener('keydown',event => {
  if(event.key === 'Escape'){if(document.body.classList.contains('focused')) focusMode(false);return;}
  if(event.repeat || event.ctrlKey || event.altKey || event.metaKey || /^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName)) return;
  if(event.code === 'Space' && /^(BUTTON|A)$/.test(event.target.tagName)) return;
  if(event.code === 'Space'){event.preventDefault();fire();}
  else if(event.code === 'KeyR') startReload();
  else if(event.code === 'KeyB') selectFeed((selected+1)%feeds.length);
});
render();fit();
