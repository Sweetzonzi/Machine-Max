(function(root){
  'use strict';
  const belt = (key,label,remaining) => ({key,label,remaining});
  const tube = (key,chamber,source) => ({key,state:chamber?'ready':'empty',chamber,source,loadingSource:null,elapsed:0,duration:0,fill:chamber?100:0});
  function create(){return [
    {key:'cannon',name:'76.2 MM ZIS-5',short:'ZIS-5',label:'主炮',description:'单管 / 三条可选弹链',capacity:60,selected:'ap',belts:[belt('ap','APHE',24),belt('he','HE-FRAG',12),belt('smoke','SMOKE',6)],stock:0,cycle:4.8,tubes:[tube('breech-01','APHE','ap')]},
    {key:'coax',name:'7.62 MM DT / COAX',short:'DT / COAX',label:'同轴机枪',description:'独立控制器 / 无弹链选择',capacity:1000,selected:null,belts:[],stock:862,cycle:2.6,tubes:[tube('dt-coax','BALL / TRACER',null)]},
    {key:'hull',name:'7.62 MM DT / HULL',short:'DT / HULL',label:'车体机枪',description:'独立控制器 / 无弹链选择',capacity:1000,selected:null,belts:[],stock:614,cycle:3.1,tubes:[tube('dt-hull','BALL',null)]},
    {key:'roof',name:'12.7 MM DSHK / ROOF',short:'DSHK / ROOF',label:'高射机枪',description:'独立控制器 / 一条混装弹链',capacity:300,selected:'mixed',belts:[belt('mixed','API / TRACER',186)],stock:0,cycle:3.8,tubes:[tube('dshk-01','API','mixed')]},
    {key:'pod',name:'ATGM / 4-TUBE POD',short:'ATGM / POD',label:'多管导弹',description:'逐管状态 / 独立准备过程',capacity:0,selected:null,belts:[],stock:0,cycle:6.8,tubes:[tube('pod-01','ATGM',null),tube('pod-02','ATGM',null),tube('pod-03',null,null),tube('pod-04',null,null)]}
  ];}
  const currentBelt = wc => wc.belts.find(b=>b.key===wc.selected);
  const remaining = wc => wc.belts.length ? (currentBelt(wc)?.remaining || 0) : wc.stock;
  function sourceLabel(wc,key){return wc.belts.find(b=>b.key===key)?.label || (wc.key==='pod'?'ATGM':'BALL / TRACER');}
  function begin(wc,t){
    if(t.state==='loading') return false;
    const source=currentBelt(wc);
    if(wc.key!=='pod' && remaining(wc)<=0){t.state='empty';t.chamber=null;t.fill=0;return false;}
    t.chamber=null;t.state='loading';t.loadingSource=source?.key || null;
    t.loadingLabel=sourceLabel(wc,t.loadingSource);t.elapsed=0;t.duration=wc.cycle;t.fill=0;return true;
  }
  function fire(wc){
    const t=wc.tubes.find(t=>t.state==='ready');
    if(!t) return false;
    if(wc.key==='coax'||wc.key==='hull'||wc.key==='roof'){
      const b=currentBelt(wc);const count=Math.min(18,remaining(wc));
      if(b) b.remaining-=count;else wc.stock-=count;
    }
    t.chamber=null;t.state='empty';t.fill=0;
    begin(wc,t);return true;
  }
  function advance(wc,dt){
    wc.tubes.forEach(t=>{
      if(t.state!=='loading') return;
      t.elapsed=Math.min(t.elapsed+dt,t.duration);t.fill=t.elapsed/t.duration*100;
      if(t.elapsed<t.duration) return;
      const b=wc.belts.find(b=>b.key===t.loadingSource);
      if(wc.key==='cannon'){
        if(!b || b.remaining<=0){t.state='empty';t.fill=0;return;}
        b.remaining--;
      }
      t.state='ready';t.chamber=t.loadingLabel;t.source=t.loadingSource;t.loadingSource=null;t.fill=100;
    });
  }
  function startPod(wc){wc.tubes.forEach((t,i)=>{if(!t.chamber){begin(wc,t);t.duration=6.8+i*.9;t.elapsed=t.duration*(i===2?.52:.18);t.fill=t.elapsed/t.duration*100;}});}
  function snapshot(wc){
    const tubes=wc.tubes.map(t=>({...t,roundLabel:t.state==='ready'?t.chamber:t.state==='loading'?t.loadingLabel:'NO SUPPLY',reloadText:t.state==='ready'?'RDY':t.state==='loading'?`${Math.max(0,t.duration-t.elapsed).toFixed(1)} S`:'EMPTY'}));
    const ready=tubes.filter(t=>t.state==='ready').length;
    const selected=currentBelt(wc);
    const mismatch=tubes.some(t=>t.state==='ready' && selected && t.source!==selected.key);
    const changing=tubes.some(t=>t.state==='loading' && selected && t.loadingSource!==selected.key);
    const note=mismatch?`CHAMBER ${tubes.find(t=>t.state==='ready').roundLabel} / SELECT ${selected.label}`:changing?`LOADING ${tubes.find(t=>t.state==='loading').loadingLabel} / SELECT ${selected.label}`:'';
    const first=tubes[0];
    return {name:wc.name,short:wc.short,ammo_remaining:remaining(wc),ammo_capacity:wc.capacity,ammo_text:wc.capacity>0?`${remaining(wc)} / ${wc.capacity}`:String(remaining(wc)),tube_count:tubes.length,belt_count:wc.belts.length,state:ready?'ready':tubes.some(t=>t.state==='loading')?'reloading':'empty',tubes,belts:wc.belts.map(b=>({...b,selected:b.key===wc.selected})),ready,note,
      compactStatus:ready?'RDY':first?.reloadText || 'EMPTY',
      tubeLabel:t=>t?.roundLabel || '',
      beltFill:key=>tubes.length===1 && first && ((first.state==='ready'&&first.source===key)||(first.state==='loading'&&first.loadingSource===key))?first.fill:0,
      beltStatus:key=>tubes.length===1 && first && ((first.state==='ready'&&first.source===key)||(first.state==='loading'&&first.loadingSource===key))?first.reloadText:''};
  }
  const api={create,currentBelt,remaining,begin,fire,advance,startPod,snapshot};
  if(typeof module!=='undefined'&&module.exports) module.exports=api;else root.HudMock=api;
})(typeof window!=='undefined'?window:this);
