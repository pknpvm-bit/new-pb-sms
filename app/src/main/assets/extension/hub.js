const svg=(body)=>`<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
const ICONS={
 formative:svg('<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>'),
 talk:svg('<path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/>'),
 lpcd:svg('<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>'),
 bco:svg('<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/>'),
 excel:svg('<rect x="3" y="3" width="18" height="18" rx="2.5"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/>'),
 marksheet:svg('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>'),
 result_ready:svg('<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/><line x1="3" y1="20" x2="21" y2="20"/>'),
 hprc_scan:svg('<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/>'),
 lock:svg('<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>')
};
const tools=[
 {key:'formative',name:'Formative Filler',desc:'Automatic marks filling',c1:'#3b82f6',c2:'#1d4ed8',url:'formative/popup.html'},
 {key:'talk',name:'Talk to Filler',desc:'Voice-controlled marks entry',c1:'#22d3ee',c2:'#0e7490',url:'talk/popup.html'},
 {key:'lpcd',name:'LPCD Filler',desc:'Qualitative data auto & manual',c1:'#fbbf24',c2:'#d97706',url:'lpcd/popup.html'},
 {key:'bco',name:'BCO Filler',desc:'A/B form group filler',c1:'#a78bfa',c2:'#6d28d9',url:'bco/ab_form_filler_v2/popup.html'},
 {key:'excel',name:'Excel Filler',desc:'Excel / CSV to webpage',c1:'#4ade80',c2:'#15803d',url:'excel/popup.html'},
 {key:'marksheet',name:'Bulk Marksheet',desc:'Automatic & manual HRPC marksheet download',c1:'#f87171',c2:'#b91c1c',url:'bulk_marksheet/popup.html',nativeAction:'BSP_OPEN_BULK_MARKSHEET'},
 {key:'result_ready',name:'Result Ready',desc:'Summative marks to Excel (.xlsx), class-wise',c1:'#818cf8',c2:'#4338ca',url:'result_ready/popup.html',nativeAction:'BSP_OPEN_RESULT_READY'},
 {key:'hprc_scan',name:'HPRC Scan',desc:'Live OCR scan to Excel & safe auto-fill',c1:'#f472b6',c2:'#be185d',url:'hprc_scan/popup.html',nativeAction:'BSP_OPEN_HPRC_SCAN'}
];
const $=id=>document.getElementById(id);let current=null;
const hasNative=()=>!!(window.BspAndroidBridge&&typeof window.BspAndroidBridge.notifyHubAction==='function');
if(hasNative())document.documentElement.classList.add('native');
function checkToolAllowed(key){
 try{
  if(window.BspAndroidBridge&&typeof window.BspAndroidBridge.isToolAllowed==='function'){
   return Boolean(window.BspAndroidBridge.isToolAllowed(key));
  }
 }catch(e){}
 return true;
}
function render(filter=''){
 const box=$('cards');box.innerHTML='';
 const q=filter.trim().toLowerCase();
 const list=tools.filter(t=>t.name.toLowerCase().includes(q)||t.desc.toLowerCase().includes(q));
 $('emptyMsg').hidden=list.length>0;
 list.forEach((t,i)=>{
  const allowed=checkToolAllowed(t.key);
  const b=document.createElement('button');
  b.className=allowed?'card':'card locked';
  b.style.setProperty('--c1',t.c1);b.style.setProperty('--c2',t.c2);
  b.style.animationDelay=(i*45)+'ms';
  b.innerHTML=`<span class="ico">${allowed?ICONS[t.key]:ICONS.lock}</span><b>${t.name}</b><small>${allowed?t.desc:'Disabled by Admin'}</small><span class="open"><span>${allowed?'Open window':'Locked'}</span><i>${allowed?'↗':'🔒'}</i></span>`;
  b.addEventListener('click',()=>openTool(t));
  box.appendChild(b);
 });
}
function openTool(t){
 if(!checkToolAllowed(t.key)){
  if(window.BspAndroidBridge&&window.BspAndroidBridge.notifyHubAction){
   window.BspAndroidBridge.notifyHubAction('BSP_TOOL_DENIED:'+t.key);
  }
  render($('search')?.value||'');
  return;
 }
 chrome.storage.local.set({bspLastTool:t.name});
 // Android app: every tool opens in its OWN floating window.
 if(hasNative()){
  window.BspAndroidBridge.notifyHubAction('BSP_OPEN_TOOL:'+t.key);
  return;
 }
 if(t.nativeAction){
  parent.postMessage({type:t.nativeAction},'*');return;
 }
 current=t;$('toolTitle').textContent=t.name;$('toolFrame').src=t.url;$('mainView').hidden=true;$('toolView').hidden=false;
}
function back(){ $('toolFrame').src='about:blank';$('toolView').hidden=true;$('mainView').hidden=false;current=null;render($('search')?.value||'');}
$('toolCount').textContent=tools.length+' tools available';
$('search').addEventListener('input',e=>render(e.target.value));$('backBtn').onclick=back;$('minBtn').onclick=$('toolMin').onclick=()=>parent.postMessage({type:'BSP_MINIMIZE'},'*');$('closeBtn').onclick=()=>parent.postMessage({type:'BSP_CLOSE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_RESTORE')parent.postMessage({type:'BSP_RESTORE'},'*');});render();

$('miniRestore').onclick=()=>parent.postMessage({type:'BSP_RESTORE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_MINIMIZED')document.body.classList.add('minimized');if(e.data?.type==='BSP_RESTORED')document.body.classList.remove('minimized');});$('topbar').addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;parent.postMessage({type:'BSP_DRAG_START',x:e.screenX,y:e.screenY},'*');});
