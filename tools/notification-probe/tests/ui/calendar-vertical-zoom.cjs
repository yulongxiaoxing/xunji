const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Asia/Shanghai'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const row=(id,time)=>({id,title:id,sourceId:'s',category:'作业',status:'todo',requirements:[],deadline:time,dueDate:'2026-10-28',dueTime:time});
  window.fixture={cards:[{...row('课程','09:00'),category:'课程',course:{startTime:'09:00',endTime:'10:00',location:'演示',teacher:'演示'}},row('截止任务','09:30'),row('零点任务','00:00')],sources:[{id:'s',text:'演示原文'}],napcat:{groups:[],followed:[],pending:[]}};
  window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:()=>'{"ok":true}'};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const button=n=>page.getByRole('button',{name:n,exact:true}),check=(v,m)=>{if(!v)throw Error(m)};
 await button('日程').click();await page.getByLabel('选择日程日期').fill('2026-10-28');
 const course=page.locator('.week-event[data-id="课程"]'),before=await course.boundingBox();const font=await course.evaluate(el=>getComputedStyle(el).fontSize);
 await page.locator('.week-scroll').evaluate(el=>el.scrollTop=600);
 const anchor=()=>page.evaluate(()=>{const sc=document.querySelector('.week-scroll'),r=sc.getBoundingClientRect(),y=r.top+r.height/2;return (y-document.querySelector('.week-stage').getBoundingClientRect().top)/WEEK_HOUR});
 const time=await anchor();await button('纵向放大').click();check(Math.abs((await course.boundingBox()).height-before.height*1.25)<1,'hour interval height scales vertically');check(Math.abs(await anchor()-time)<0.05,'zoom preserves viewed time');
 check(Math.abs((await course.boundingBox()).width-before.width)<1&&await course.evaluate(el=>getComputedStyle(el).fontSize)===font,'width and font unchanged');
 check(Math.abs(Number(await course.getAttribute('data-start'))*80/60-parseFloat(await course.evaluate(el=>el.style.top)))<1,'time position uses enlarged hour height');
 const ddl=page.locator('.week-deadline[data-date="2026-10-28"]');check(Math.abs(parseFloat(await ddl.evaluate(el=>el.style.top))-(9*80+4))<1,'deadline follows enlarged time cell');
 await ddl.click();check(await button('查看时段：课程').isVisible(),'zoomed deadline still offers covered course');await page.keyboard.press('Escape');
 await button('重置纵向缩放').click();check(Math.abs((await course.boundingBox()).height-before.height)<1,'reset returns to original scale');
 // Real two-touch events should change row heights without browser page zoom.
 const pinch=async(selector,ratio)=>page.locator(selector).evaluate((el,ratio)=>{
  const r=el.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;
  const send=(type,d)=>{const touches=type==='touchend'?[]:[new Touch({identifier:1,target:el,clientX:x,clientY:y-d/2}),new Touch({identifier:2,target:el,clientX:x,clientY:y+d/2})];const e=new TouchEvent(type,{bubbles:true,cancelable:true,touches});el.dispatchEvent(e);return e.defaultPrevented};
  send('touchstart',100);const prevented=send('touchmove',100*ratio);send('touchend',0);return prevented;
 },ratio);
 check(await pinch('.week-scroll',1.5),'week pinch handled');check(Math.abs((await course.boundingBox()).height-before.height*1.5)<1,'pinch enlarges hour height');await page.waitForTimeout(400);
 await button('月视图').click();const cell=page.locator('.month-day').first(),base=await cell.boundingBox();await button('纵向放大').click();const grown=await cell.boundingBox();check(grown.height>base.height*1.2&&Math.abs(grown.width-base.width)<1,'month enlarges date height only');
 check(await pinch('.calendar-surface',2),'month pinch handled');await page.waitForTimeout(400);check((await cell.boundingBox()).height>base.height*2,'month pinch enlarges rows');
 for(const width of [320,390,667,1100]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'zoom has no horizontal overflow '+width)}
 await page.reload();await button('日程').click();await page.getByLabel('选择日程日期').fill('2026-10-28');check(Math.abs((await page.locator('.week-event[data-id="课程"]').boundingBox()).height-before.height*1.5)<1,'week scale preference survives reopen');
 await button('月视图').click();check((await button('重置纵向缩放').innerText()).includes('250%'),'separate month scale preference survives reopen');
 check(errors.length===0,errors.join(';'));console.log('PASS: vertical-only week/month zoom, anchored time, pinch/buttons/reset, deadline geometry, persisted scales and responsive columns');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
