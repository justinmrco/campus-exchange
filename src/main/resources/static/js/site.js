(()=>{'use strict';
const sheet=document.querySelector('main');
const photoInput=document.querySelector('input[type=file][name=photo]');
photoInput?.addEventListener('change',()=>{photoInput.setCustomValidity(photoInput.files[0]?.size>5*1024*1024?'Choose a photo up to 5 MB.':'');photoInput.reportValidity();});
document.querySelectorAll('.product-photo img').forEach(image=>{
 const failed=()=>{image.hidden=true;const figure=image.closest('.product-photo');figure.querySelector('.no-photo').hidden=false;const caption=figure.querySelector('figcaption');if(caption)caption.hidden=true;};
 image.addEventListener('error',failed);if(image.complete&&image.naturalWidth===0)failed();
});
const navigationType=performance.getEntriesByType('navigation')[0]?.type;
const reduced=matchMedia('(prefers-reduced-motion: reduce)');
// ponytail: per-tab presentation memory; storage failure keeps the complete static layout.
try {
 if(!sessionStorage.getItem('ce-paper-opened')){sessionStorage.setItem('ce-paper-opened','1');if(navigationType==='navigate'&&!reduced.matches)sheet?.classList.add('paper-enter');}
 const stamp=sheet?.querySelector('[data-status-stamp]');
 if(stamp){const key='ce-stamp:'+sheet.dataset.ledger+':'+sheet.dataset.state;if(!sessionStorage.getItem(key)){sessionStorage.setItem(key,'1');if(navigationType!=='reload'&&!reduced.matches)stamp.classList.add('stamp-enter');}}
} catch {}
window.addEventListener('pagereveal',e=>{if(e.viewTransition)sheet?.classList.remove('paper-enter');});
window.addEventListener('pageswap',()=>sheet?.classList.remove('paper-enter'));
window.addEventListener('pageshow',e=>{if(e.persisted){sheet?.classList.remove('paper-enter');document.querySelectorAll('.stamp-enter').forEach(el=>el.classList.remove('stamp-enter'));}});
// Keep the current section legible without changing native navigation.
const root=document.body.dataset.context||'/';
const current=location.pathname.slice(root.length-1);
const section=current.startsWith('/admin/')?'/admin/dashboard':current.startsWith('/handover/')?'/handover/transactions':current.startsWith('/dispute/')?'/dispute/my-disputes':/^\/listing\/\d+\/edit$/.test(current)?'/my-listings':/^\/listing\/\d+$/.test(current)?'/marketplace':current;
document.querySelectorAll('.main-nav>a,.admin-nav>a').forEach(a=>{const path=new URL(a.href).pathname.slice(root.length-1);if(path===(a.closest('.admin-nav')?current:section))a.setAttribute('aria-current',path===current?'page':'location');});
let dirty=false,notice,es,retry=false;const main=document.querySelector('main[data-live]');
function changed(){if(notice)return;notice=document.createElement('div');notice.className='live-notice';notice.setAttribute('role','status');notice.append(document.createTextNode('This page has an update. Review the latest information before continuing. '));const b=document.createElement('button');b.textContent='Refresh';b.onclick=()=>{if(!dirty||confirm('Discard unsaved changes and refresh?'))location.reload()};notice.append(b);document.querySelector('main')?.prepend(notice)}
document.addEventListener('input',e=>{if(e.target.closest('form[method="post"]'))dirty=true});
document.addEventListener('submit',e=>{const f=e.target;if(f.dataset.confirm&&!confirm(f.dataset.confirm)){e.preventDefault();return}if(f.dataset.sending){e.preventDefault();return}if(f.method.toLowerCase()==='post'){f.dataset.sending='true';f.setAttribute('aria-busy','true');f.querySelectorAll('button[type=submit]').forEach(b=>{b.dataset.idle=b.innerHTML;b.disabled=true;b.textContent=b.dataset.pending||'Sending…'});dirty=false}});
function connect(){if(!window.EventSource||!main||es)return;const root=document.body.dataset.context||'/';const scope=main.dataset.live;const url=scope==='ledger'?'handover/ledger/'+main.dataset.ledger+'/events':'events/'+scope;es=new EventSource(root+url);es.onopen=()=>{if(retry)changed();retry=false};es.onerror=()=>{retry=true};es.addEventListener('snapshot',e=>{try{const snapshot=JSON.parse(e.data);if(main.dataset.live==='ledger'?String(snapshot.version)!==main.dataset.version:String(snapshot.revision)!==main.dataset.revision)changed()}catch{changed()}});for(const name of ['reload','status','listing-created','listing-status-change','item-reserved','meetup-proposed','meetup-accepted','transaction-changed','dispute-resolved'])es.addEventListener(name,changed)}
window.addEventListener('pageshow',e=>{document.querySelectorAll('form').forEach(f=>{delete f.dataset.sending;f.removeAttribute('aria-busy');f.querySelectorAll('button[type=submit]').forEach(b=>{b.disabled=false;if(b.dataset.idle){b.innerHTML=b.dataset.idle;delete b.dataset.idle}})});if(e.persisted)changed();connect()});window.addEventListener('pagehide',()=>{es?.close();es=null});connect();})();
