function visibleTextInputs(root) {
 return Array.from(root.querySelectorAll('input:not([type]),input[type="text"],input[type="search"],textarea'))
  .filter(el=>!el.disabled && !el.readOnly && el.getClientRects().length);
}
function cssEscape(s){return CSS.escape(s);}
function selectorFor(el){
 if(el.id) return "#"+cssEscape(el.id);
 const classes=Array.from(el.classList||[]).filter(Boolean).slice(0,3);
 return el.tagName.toLowerCase()+(classes.length?"."+classes.map(cssEscape).join("."):"");
}
function detectGroups(){
 // Find repeated elements whose descendants contain exactly 11 editable text fields.
 const candidates=Array.from(document.querySelectorAll("body *"))
  .filter(el=>el.children.length>0 && visibleTextInputs(el).length===11);
 // Prefer the smallest qualifying container, then group siblings with the same simple selector.
 const smallest=candidates.filter(el=>!Array.from(el.children).some(ch=>visibleTextInputs(ch).length===11));
 const counts=new Map();
 for(const el of smallest){
   const sel=selectorFor(el);
   const key=(el.parentElement?selectorFor(el.parentElement):"")+" > "+sel;
   if(!counts.has(key)) counts.set(key,{key,selector:sel,parent:el.parentElement,items:[]});
   counts.get(key).items.push(el);
 }
 const groups=Array.from(counts.values()).filter(g=>g.items.length>=2).sort((a,b)=>b.items.length-a.items.length);
 if(!groups.length) return null;
 const best=groups[0];
 // Build a page selector based on the detected parent and child selector.
 const parentSel=best.parent ? selectorFor(best.parent) : "body";
 return {row: parentSel+" > "+best.selector, fields:'input:not([type]),input[type="text"],input[type="search"],textarea', count:best.items.length};
}
function setValue(el,value){
 const proto=el instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
 const setter=Object.getOwnPropertyDescriptor(proto,"value")?.set;
 if(setter) setter.call(el,value); else el.value=value;
 el.dispatchEvent(new Event("input",{bubbles:true}));
 el.dispatchEvent(new Event("change",{bubbles:true}));
}
function shuffle(a){for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
// ---- Manual mode: tap a box inside a student's row, then pick A / B / C / D from a dropdown ----
window.__abManual = window.__abManual || {enabled:false,rowSelector:"",menu:null,target:null,touched:[],lastDetect:0};
function abManualResolveRow(preferred){
 const st=window.__abManual;
 st.lastDetect=Date.now();
 if(preferred){
  try{ if(document.querySelector(preferred)){ st.rowSelector=preferred; return true; } }catch(e){}
 }
 const d=detectGroups();
 if(d){ st.rowSelector=d.row; return true; }
 st.rowSelector="";
 return false;
}
function abManualCandidate(el){
 const st=window.__abManual;
 if(!(el instanceof HTMLInputElement||el instanceof HTMLTextAreaElement)) return false;
 if(el instanceof HTMLInputElement){
  const t=(el.type||"text").toLowerCase();
  if(t!=="text"&&t!=="search"&&t!=="") return false;
 }
 if(el.disabled||el.readOnly||!el.getClientRects().length) return false;
 const meta=((el.name||"")+" "+(el.id||"")+" "+(el.className||"")).toLowerCase();
 if(/captcha|password|login|filter/.test(meta)) return false;
 // Only boxes inside a student's row are handled, never the page's search or filter boxes
 if(!st.rowSelector && Date.now()-st.lastDetect>1500) abManualResolveRow("");
 if(!st.rowSelector) return false;
 try{ return !!el.closest(st.rowSelector); }catch(e){ return false; }
}
function abManualClose(){
 const st=window.__abManual;
 if(st.menu&&st.menu.parentNode) st.menu.parentNode.removeChild(st.menu);
 if(st.target){ try{ st.target.style.outline=""; }catch(e){} }
 st.menu=null; st.target=null;
}
function abManualChoose(letter){
 const st=window.__abManual;
 const el=st.target;
 if(!el) return;
 setValue(el,letter);
 abManualClose();
 try{ el.style.outline="2px solid #16a34a"; setTimeout(()=>{ try{ el.style.outline=""; }catch(e){} },700); }catch(e){}
}
function abManualOpen(el){
 abManualClose();
 const st=window.__abManual;
 st.target=el;
 try{ el.style.outline="2px solid #1769aa"; }catch(e){}
 const m=document.createElement("div");
 m.setAttribute("style","position:fixed;z-index:2147483647;background:#fff;border:1px solid #1769aa;border-radius:10px;box-shadow:0 6px 20px rgba(0,0,0,.35);padding:4px;display:flex;flex-direction:column;gap:4px;min-width:96px;");
 ["A","B","C","D"].forEach(L=>{
  const b=document.createElement("button");
  b.type="button";
  b.textContent=L;
  const on=(el.value||"").trim().toUpperCase()===L;
  b.setAttribute("style","height:42px;font:700 20px Arial,sans-serif;border:0;border-radius:7px;cursor:pointer;color:"+(on?"#fff":"#1769aa")+";background:"+(on?"#1769aa":"#eaf2fb")+";");
  b.addEventListener("click",ev=>{ev.preventDefault();ev.stopPropagation();abManualChoose(L);});
  m.appendChild(b);
 });
 document.body.appendChild(m);
 const r=el.getBoundingClientRect();
 const mw=m.offsetWidth,mh=m.offsetHeight;
 const left=Math.min(Math.max(4,r.left+r.width/2-mw/2),Math.max(4,window.innerWidth-mw-4));
 let top=r.bottom+4;
 if(top+mh>window.innerHeight-4) top=Math.max(4,r.top-mh-4);
 m.style.left=left+"px";
 m.style.top=top+"px";
 st.menu=m;
}
function abManualOnPointerDown(e){
 const st=window.__abManual;
 if(!st.enabled) return;
 const t=e.target;
 if(st.menu&&st.menu.contains(t)) return;
 if(abManualCandidate(t)){
  // Stop the phone keyboard from opening; the dropdown is used instead
  if(!st.touched.some(x=>x.el===t)) st.touched.push({el:t,prev:t.getAttribute("inputmode")});
  t.setAttribute("inputmode","none");
 }
}
function abManualOnClick(e){
 const st=window.__abManual;
 if(!st.enabled) return;
 const t=e.target;
 if(st.menu&&st.menu.contains(t)) return;
 if(abManualCandidate(t)){
  e.preventDefault();
  e.stopPropagation();
  try{ t.blur(); }catch(err){}
  abManualOpen(t);
 }else{
  abManualClose();
 }
}
function abManualOn(preferredRow){
 const st=window.__abManual;
 const found=abManualResolveRow(preferredRow||"");
 if(!st.enabled){
  document.addEventListener("pointerdown",abManualOnPointerDown,true);
  document.addEventListener("click",abManualOnClick,true);
  window.addEventListener("scroll",abManualClose,true);
  st.enabled=true;
 }
 return found;
}
function abManualOff(){
 const st=window.__abManual;
 document.removeEventListener("pointerdown",abManualOnPointerDown,true);
 document.removeEventListener("click",abManualOnClick,true);
 window.removeEventListener("scroll",abManualClose,true);
 abManualClose();
 st.touched.forEach(x=>{
  try{ if(x.prev===null) x.el.removeAttribute("inputmode"); else x.el.setAttribute("inputmode",x.prev); }catch(e){}
 });
 st.touched=[];
 st.enabled=false;
}
// Keep manual mode on after the portal page reloads (only becomes active on pages that contain BCO student rows)
try{
 chrome.storage.local.get({manualMode:false,row:""},saved=>{
  if(saved&&saved.manualMode) abManualOn(saved.row||"");
 });
}catch(e){}
chrome.runtime.onMessage.addListener((msg,sender,sendResponse)=>{
 try{
  if(msg.type==="AB_AUTO_DETECT"){
   const d=detectGroups();
   if(!d){sendResponse({message:"Auto-detect could not confidently find repeated groups of exactly 11 text fields. Use Manual selector settings, or ask me to adapt it for this page."});return true;}
   chrome.storage.local.set({row:d.row,fields:d.fields});
   sendResponse({message:`Detected about ${d.count} repeated form group(s). Selector saved. Check the page before filling.`});
   return true;
  }
  if(msg.type==="AB_MANUAL_ON"){
   const found=abManualOn((msg.settings&&msg.settings.row)||"");
   chrome.storage.local.set({manualMode:true});
   sendResponse({enabled:true,message:found
    ?"Manual mode ON. Tap any box in a student's row and choose A, B, C or D from the dropdown. Nothing is submitted."
    :"Manual mode ON, but no BCO student rows were found on this page yet. Open the BCO marks page; it starts working on the student rows automatically."});
   return true;
  }
  if(msg.type==="AB_MANUAL_OFF"){
   abManualOff();
   chrome.storage.local.set({manualMode:false});
   sendResponse({enabled:false,message:"Manual mode OFF. The boxes work normally again."});
   return true;
  }
  if(msg.type==="AB_MANUAL_STATUS"){
   sendResponse({enabled:!!window.__abManual.enabled});
   return true;
  }
  if(msg.type==="AB_FILL"){
   const settings=msg.settings||{};
   if(!settings.row||!settings.fields){sendResponse({message:"First click Auto Detect, or enter and save selectors in Manual selector settings."});return true;}
   const rows=Array.from(document.querySelectorAll(settings.row)).slice(0,settings.limit||100);
   let filled=0,skipped=0;
   rows.forEach((row,index)=>{
    const fields=Array.from(row.querySelectorAll(settings.fields))
      .filter(el=>(el instanceof HTMLInputElement||el instanceof HTMLTextAreaElement)&&!el.disabled&&!el.readOnly&&el.getClientRects().length)
      .slice(0,11);
    if(fields.length!==11){skipped++;return;}
    const bCount=Math.min(11,Math.floor(index/10)+2);
    const vals=shuffle([...Array(11-bCount).fill("A"),...Array(bCount).fill("B")]);
    fields.forEach((f,i)=>setValue(f,vals[i]));filled++;
   });
   sendResponse({message:`Filled ${filled} form(s); skipped ${skipped}. Nothing was submitted. Please review all entries before submitting on the website.`});
   return true;
  }
 }catch(e){sendResponse({message:"Error: "+e.message});return true;}
});
