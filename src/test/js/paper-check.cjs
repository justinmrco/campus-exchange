const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/site.js', 'utf8');
function run({type='navigate', reduced=false, stored={}, blocked=false, pathname='/marketplace', live=false, images=[]}={}) {
 const handlers={}, documentHandlers={}, links=[], forms=[], events={}, notices=[];let reloads=0;
 const classes=new Set(), stampClasses=new Set();
 const tokens=set=>({add:c=>set.add(c),remove:c=>set.delete(c)});
 const stamp={classList:tokens(stampClasses)};
 const sheet={prepend:n=>notices.push(n),classList:tokens(classes),dataset:{ledger:'7',state:'COMPLETED'},querySelector:()=>stamp};
 const window={addEventListener:(name,fn)=>(handlers[name]??=[]).push(fn)};
 const nav={href:'http://localhost/marketplace',setAttribute:(k,v)=>nav[k]=v,closest:()=>null};links.push(nav);
 const context={window,EventSource:class {constructor(){this.addEventListener=(name,fn)=>events[name]=fn;this.close=()=>{}}},confirm:()=>true,location:{pathname,reload:()=>reloads++},performance:{getEntriesByType:()=>[{type}]},matchMedia:()=>({matches:reduced}),sessionStorage:{getItem:key=>{if(blocked)throw Error('Storage disabled');return stored[key]},setItem:(key,value)=>{stored[key]=value}},document:{body:{dataset:{context:'/'}},createElement:()=>({setAttribute:()=>{},append:()=>{}}),createTextNode:text=>text,querySelector:selector=>selector==='main'||(selector==='main[data-live]'&&live)?sheet:null,querySelectorAll:selector=>selector==='.product-photo img'?images:selector==='.stamp-enter'?[stamp]:selector==='form'?forms:selector.includes('.main-nav')?links:[],addEventListener:(name,fn)=>documentHandlers[name]=fn},URL};
 window.EventSource=context.EventSource;
 vm.runInNewContext(source,context);
 return {classes,stampClasses,nav,events,notices,forms,documentHandlers,reloads:()=>reloads,dispatch:(name,event)=>handlers[name]?.forEach(fn=>fn(event))};
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
