const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/site.js', 'utf8');
function run({type='navigate', reduced=false, stored={}, blocked=false, pathname='/marketplace', live=false, images=[], dialog=false, nativeConfirm=true}={}) {
 const dialogs=[];let focused=0;
 const trigger={focus:()=>focused++};
 function element(tag){const e={children:[],dataset:{},hidden:false,textContent:'',setAttribute:()=>{},append(...children){this.children.push(...children)},replaceChildren(){this.children=[]},focus(){focused++}};
 if(tag==='dialog'){const parts={};e.querySelector=selector=>parts[selector]??=(element('part'));e.addEventListener=(name,fn)=>e[name]=fn;e.showModal=()=>{e.open=true};e.close=value=>{e.returnValue=value||'';e.open=false;e.closeEvent()};e.addEventListener=(name,fn)=>{if(name==='close')e.closeEvent=fn;else e[name]=fn};e.getBoundingClientRect=()=>({left:10,right:100,top:10,bottom:100});dialogs.push(e);}return e;}
 const handlers={}, documentHandlers={}, links=[], forms=[], events={}, notices=[];let reloads=0;
 const classes=new Set(), stampClasses=new Set();
 const tokens=set=>({add:c=>set.add(c),remove:c=>set.delete(c)});
 const stamp={classList:tokens(stampClasses)};
 const sheet={prepend:n=>notices.push(n),classList:tokens(classes),dataset:{ledger:'7',state:'COMPLETED'},querySelector:()=>stamp};
 const window={addEventListener:(name,fn)=>(handlers[name]??=[]).push(fn)};
 const nav={href:'http://localhost/marketplace',setAttribute:(k,v)=>nav[k]=v,closest:()=>null};links.push(nav);
 const context={window,EventSource:class {constructor(){this.addEventListener=(name,fn)=>events[name]=fn;this.close=()=>{}}},confirm:()=>nativeConfirm,location:{pathname,reload:()=>reloads++},performance:{getEntriesByType:()=>[{type}]},matchMedia:()=>({matches:reduced}),sessionStorage:{getItem:key=>{if(blocked)throw Error('Storage disabled');return stored[key]},setItem:(key,value)=>{stored[key]=value}},document:{body:{dataset:{context:'/'},append:()=>{}},activeElement:trigger,createElement:element,createTextNode:text=>text,querySelector:selector=>selector==='main'||(selector==='main[data-live]'&&live)?sheet:null,querySelectorAll:selector=>selector==='.product-photo img'?images:selector==='.stamp-enter'?[stamp]:selector==='form'?forms:selector.includes('.main-nav')?links:[],addEventListener:(name,fn)=>documentHandlers[name]=fn},URL};
 window.EventSource=context.EventSource;if(dialog)window.HTMLDialogElement={prototype:{showModal(){}}};
 vm.runInNewContext(source,context);
 return {dialogs,focused:()=>focused,classes,stampClasses,nav,events,notices,forms,documentHandlers,reloads:()=>reloads,dispatch:(name,event)=>handlers[name]?.forEach(fn=>fn(event))};
}
const memory={};const first=run({stored:memory});
assert(first.classes.has('paper-enter'));assert(first.stampClasses.has('stamp-enter'));
const second=run({stored:memory});assert(!second.classes.size&&!second.stampClasses.size,'Do not repeat entrance or stamp in the same tab');
for(const options of [{type:'reload'},{reduced:true},{blocked:true}]){const result=run(options);assert(!result.classes.size&&!result.stampClasses.size,'Reload, reduced motion and blocked storage retain a stable page')}
first.dispatch('pagereveal',{viewTransition:{}});assert(!first.classes.has('paper-enter'),'Native transitions must not double animate');
first.dispatch('pageshow',{persisted:true});assert(!first.stampClasses.size,'Back/forward restoration must not replay stamps');
console.log('PASS: first visit, per-tab replay prevention, reload, reduced motion, unavailable storage, native transitions and history restoration.');

const detail=run({pathname:'/listing/42'});assert.equal(detail.nav['aria-current'],'location','Item details retain the classifieds section');
const updated=run({live:true});updated.events.reload();assert.equal(updated.notices.length,1);assert.equal(updated.reloads(),0,'Live updates never reload without consent');updated.events.reload();assert.equal(updated.notices.length,1,'Updates share one notice');
const submission=run();const button={dataset:{pending:'Reserving…'},innerHTML:'Reserve',textContent:'Reserve',disabled:false};const form={dataset:{},method:'post',setAttribute:()=>{},removeAttribute:()=>{},querySelectorAll:()=>[button]};submission.forms.push(form);submission.documentHandlers.submit({target:form});assert.equal(button.textContent,'Reserving…');assert(button.disabled);let blocked=false;submission.documentHandlers.submit({target:form,preventDefault:()=>blocked=true});assert(blocked,'Duplicate submission blocked');submission.dispatch('pageshow',{persisted:false});assert(!button.disabled);assert.equal(button.innerHTML,'Reserve');
console.log('PASS: section navigation, persistent live notice, pending labels, duplicate prevention and restored buttons.');

const missing={hidden:true},caption={hidden:false},photoHandlers={};const broken={complete:true,naturalWidth:0,hidden:false,addEventListener:(name,fn)=>photoHandlers[name]=fn,closest:()=>({querySelector:selector=>selector==='.no-photo'?missing:caption})};run({images:[broken]});assert(broken.hidden&&!missing.hidden&&caption.hidden,'Missing photo uses the deliberate fallback');
console.log('PASS: broken local photo fallback.');

// Exercise the real submit interceptor and shared slip using a small native-dialog stand-in.
function reservable(app){let requests=0;const button={dataset:{pending:'Reserving'},innerHTML:'Reserve',disabled:false};const form={isConnected:true,dataset:{confirm:'Reserve safely',confirmTitle:'Your next find.',confirmAction:'Confirm reservation',confirmItem:'<b>Untrusted title</b>',confirmSeller:'Seller',confirmPrice:'58.00'},method:'post',setAttribute:()=>{},querySelectorAll:()=>[button],requestSubmit(submitter){assert.equal(submitter,button);app.documentHandlers.submit({target:form,submitter,preventDefault(){throw Error('Approved request blocked')}});requests++}};let blocked=0;return {form,button,requests:()=>requests,blocked:()=>blocked,submit(){app.documentHandlers.submit({target:form,submitter:button,preventDefault(){blocked++}})}};}
const slip=run({dialog:true});const reservation=reservable(slip);reservation.submit();reservation.submit();assert.equal(slip.dialogs.length,1);assert.equal(reservation.requests(),0);let d=slip.dialogs[0];assert(d.open);assert.equal(d.querySelector('dl').children[0].children[1].textContent,'<b>Untrusted title</b>','Summary uses plain text');d.querySelector('[data-slip-cancel]').onclick();assert(!d.open);assert.equal(reservation.requests(),0);assert(slip.focused()>1,'Cancel restores focus');reservation.submit();d.close('');assert.equal(reservation.requests(),0,'Escape does not submit');reservation.submit();d.click({target:d,clientX:0,clientY:0});assert.equal(reservation.requests(),0,'Backdrop dismisses');reservation.submit();d.querySelector('[data-slip-confirm]').onclick();assert.equal(reservation.requests(),1);assert(reservation.button.disabled);reservation.submit();assert.equal(reservation.requests(),1,'No duplicate after confirmation');
const invalid=run({dialog:true});const draft=reservable(invalid);draft.form.requestSubmit=()=>{};draft.submit();invalid.dialogs[0].close('confirm');draft.submit();assert(invalid.dialogs[0].open,'Approval resets when validation prevents submission');invalid.dialogs[0].close('cancel');
const fallback=run({nativeConfirm:false});const safe=reservable(fallback);safe.submit();assert.equal(safe.requests(),0,'Native cancel remains safe');
const refresh=run({dialog:true,live:true});refresh.events.reload();refresh.documentHandlers.input({target:{closest:()=>({})}});refresh.notices[0].children[1].onclick();assert.equal(refresh.reloads(),0);refresh.dialogs[0].close('cancel');assert.equal(refresh.reloads(),0);refresh.notices[0].children[1].onclick();refresh.dialogs[0].close('confirm');assert.equal(refresh.reloads(),1);
console.log('PASS: paper confirmation cancel/Escape/backdrop, safe text, focus restoration, submitter retention, one-shot approval, duplicates, native fallback and dirty refresh.');
