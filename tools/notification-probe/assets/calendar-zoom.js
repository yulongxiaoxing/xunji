/* Scale calendar rows vertically; leave columns, text and stored schedules alone. */
function readCalendarZoom(view){try{const n=Number(localStorage.getItem('xunji-calendar-zoom-'+view));return Number.isFinite(n)&&n>=1&&n<=3?n:1}catch{return 1}}
let calendarWeekScale=readCalendarZoom('week'),calendarMonthScale=readCalendarZoom('month'),calendarPinching=false;
function currentCalendarZoom(){return calendarView==='week'?calendarWeekScale:calendarMonthScale}
function saveCalendarZoom(){try{localStorage.setItem('xunji-calendar-zoom-'+calendarView,String(currentCalendarZoom()))}catch{}}
function updateCalendarZoomControls(){
 const value=document.getElementById('calendarZoomValue');if(value)value.textContent=Math.round(currentCalendarZoom()*100)+'%';
 const less=document.querySelector('[aria-label="纵向缩小"]'),more=document.querySelector('[aria-label="纵向放大"]');if(less)less.disabled=currentCalendarZoom()<=1;if(more)more.disabled=currentCalendarZoom()>=3;
}
function applyCalendarZoomGeometry(){
 document.documentElement.style.setProperty('--week-hour',WEEK_HOUR+'px');
 document.querySelectorAll('.hour-label').forEach((el,i)=>el.style.top=i*WEEK_HOUR+'px');
 document.querySelectorAll('.week-event').forEach(el=>{const start=Number(el.dataset.start),end=Number(el.dataset.end);el.style.top=start*WEEK_HOUR/60+'px';el.style.height=Math.max(24,(end-start)*WEEK_HOUR/60)+'px'});
 document.querySelectorAll('.week-deadline').forEach(el=>el.style.top=(el.dataset.midnight==='true'?24*WEEK_HOUR-54:Number(el.dataset.hour)*WEEK_HOUR+4)+'px');
 const days=[...document.querySelectorAll('.month-day')];if(days.length){
  days.forEach(el=>el.style.minHeight='');const heights=days.map(el=>el.getBoundingClientRect().height);
  days.forEach((el,i)=>el.style.minHeight=heights[i]*calendarMonthScale+'px');
 }
 updateCalendarZoomControls();refreshWeekNow();
}
function setCalendarZoom(request,clientY,persist=true){
 const next=Math.max(1,Math.min(3,Math.round(request*100)/100)),old=currentCalendarZoom();
 const el=document.querySelector(calendarView==='week'?'.week-scroll':'.calendar-surface');if(!el)return;
 const bounds=el.getBoundingClientRect();let y=Number.isFinite(clientY)?clientY:bounds.top+bounds.height/2;
 const sticky=document.querySelector(calendarView==='week'?'.week-all-day':'.month-weekdays');y=Math.max(sticky?sticky.getBoundingClientRect().bottom+8:bounds.top+8,Math.min(bounds.bottom-8,y));
 let hour=null,monthAnchor=null;
 if(calendarView==='week')hour=(y-document.querySelector('.week-stage').getBoundingClientRect().top)/WEEK_HOUR;
 else{const day=[...el.querySelectorAll('.month-day')].find(day=>{const r=day.getBoundingClientRect();return r.top<=y&&r.bottom>y});if(day){const r=day.getBoundingClientRect();monthAnchor={date:day.dataset.date,fraction:(y-r.top)/r.height}}}
 if(calendarView==='week'){calendarWeekScale=next;WEEK_HOUR=64*next}else calendarMonthScale=next;
 const oldTop=el.scrollTop;applyCalendarZoomGeometry();
 if(hour!==null)el.scrollTop+=document.querySelector('.week-stage').getBoundingClientRect().top+hour*WEEK_HOUR-y;
 else if(monthAnchor){const day=el.querySelector('.month-day[data-date="'+monthAnchor.date+'"]');if(day){const r=day.getBoundingClientRect();el.scrollTop+=r.top+monthAnchor.fraction*r.height-y}}
 else el.scrollTop=oldTop*next/old;
 captureWeekScroll();if(persist)saveCalendarZoom();
}
function stepCalendarZoom(direction){setCalendarZoom(Math.round((currentCalendarZoom()+direction*.25)*4)/4)}
function initCalendarZoom(){
 const el=document.querySelector(calendarView==='week'?'.week-scroll':'.calendar-surface');if(!el)return;let pinch=null,suppressClickUntil=0;
 const distance=e=>Math.hypot(e.touches[0].clientX-e.touches[1].clientX,e.touches[0].clientY-e.touches[1].clientY);
 el.addEventListener('touchstart',e=>{if(e.touches.length!==2)return;const d=distance(e);if(d<8)return;pinch={distance:d,scale:currentCalendarZoom(),changed:false};calendarPinching=true;e.preventDefault()},{passive:false});
 el.addEventListener('touchmove',e=>{if(!pinch||e.touches.length!==2)return;e.preventDefault();pinch.changed=true;setCalendarZoom(pinch.scale*distance(e)/pinch.distance,(e.touches[0].clientY+e.touches[1].clientY)/2,false)},{passive:false});
 const end=e=>{if(!pinch||e.touches.length>=2)return;if(pinch.changed)suppressClickUntil=Date.now()+350;pinch=null;calendarPinching=false;saveCalendarZoom();captureWeekScroll();refreshNative()};
 el.addEventListener('touchend',end,{passive:true});el.addEventListener('touchcancel',end,{passive:true});
 el.addEventListener('click',e=>{if(Date.now()<suppressClickUntil){e.preventDefault();e.stopImmediatePropagation()}},true);
}
let calendarZoomResizeFrame=0;
window.addEventListener('resize',()=>{cancelAnimationFrame(calendarZoomResizeFrame);calendarZoomResizeFrame=requestAnimationFrame(()=>{if(page==='schedule'&&!document.getElementById('modal').open){applyCalendarZoomGeometry();captureWeekScroll()}})});
