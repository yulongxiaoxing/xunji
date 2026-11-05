const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Asia/Shanghai'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const task=(id,time,date='2026-10-28')=>({id,title:'截止任务'+id,sourceId:'s',category:'作业',status:'todo',requirements:[],deadline:time,dueDate:date,dueTime:time});
  window.fixture={cards:[task('midnight','00:00'),task('same1','09:00'),task('same2','09:00'),task('near','09:20'),task('late','23:59'),task('tomorrow','00:00','2026-10-29'),task('date',''),...Array.from({length:8},(_,i)=>task('extra'+i,'18:00')),task('nextWeek','00:00','2026-11-02'),task('newYear','00:00','2027-01-01'),{...task('short','09:05'),title:'短活动',category:'活动',event:{startTime:'09:05',endTime:'09:35',location:'演示'}},{...task('class','09:00'),title:'课程',category:'课程',course:{startTime:'09:00',endTime:'10:00',location:'演示',teacher:'演示老师'}}],sources:[{id:'s',text:'演示原文'}],napcat:{groups:[],followed:[],pending:[]}};
  window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:()=>'{"ok":true}'};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const check=(v,m)=>{if(!v)throw Error(m)},button=n=>page.getByRole('button',{name:n,exact:true});
 await button('日程').click();await page.getByLabel('选择日程日期').fill('2026-10-28');
 const group=page.locator('.week-deadline[data-date="2026-10-28"][data-hour="9"]');
 check(await group.count()===1,'deadlines return to the time grid');check(await group.getAttribute('data-count')==='3','same-hour deadlines have an obvious exact count');
 check(await page.locator('.week-undated[data-date="2026-10-28"] .week-date-task').count()===1,'top area contains only untimed date items');
 const positions=await page.locator('.week-timed-day[data-date="2026-10-28"] .week-deadline').evaluateAll(es=>es.map(e=>({top:parseFloat(e.style.top),height:e.getBoundingClientRect().height})));
 check(positions.every((b,i)=>b.height<60&&(!i||b.top>=positions[i-1].top+positions[i-1].height)),'deadline groups do not cover each other or imply hour-long durations');
 await group.click();check((await page.locator('#deadlineChoices').innerText()).includes('3项截止任务'),'count opens all deadline choices');check(await button('查看时段：短活动').count()===1,'covered short interval must remain reachable through deadline marker');await button('查看时段：短活动').click();check((await page.locator('#agendaDetail').innerText()).includes('09:05—09:35'),'overlaid interval retains its actual times');await page.keyboard.press('Escape');await group.click();
 for(const name of ['same1','same2','near'])check((await page.locator('#deadlineChoices').innerText()).includes('截止任务'+name),'chooser retains '+name);
 await button('查看截止：截止任务near').click();check((await page.locator('#agendaDetail').innerText()).includes('09:20'),'exact deadline minute retained');await page.keyboard.press('Escape');
 const midnight=page.locator('.week-deadline[data-date="2026-10-27"][data-hour="23"]');check((await midnight.innerText()).includes('24:00'),'midnight displayed at previous day end');
 check(await page.locator('.week-deadline[data-date="2026-10-28"][data-hour="23"]').getAttribute('data-count')==='2','23:59 and next midnight share a readable last-hour count');
 await midnight.click();check((await page.locator('#agendaDetail').innerText()).includes('2026-10-28')&&(await page.locator('#agendaDetail').innerText()).includes('00:00'),'detail retains original midnight date');await page.keyboard.press('Escape');
 check(await page.locator('.week-deadline[data-date="2026-11-01"][data-hour="23"]').getAttribute('data-count')==='1','next Monday midnight shown on current Sunday');
 check(await page.evaluate(()=>weekLayout(calendarCards('2026-10-28')).find(b=>b.c.id==='class').overlap)===2,'deadline count does not inflate the two real overlapping intervals');
 await page.getByLabel('选择日程日期').fill('2026-12-31');check((await page.locator('.week-deadline[data-date="2026-12-31"][data-hour="23"]').innerText()).includes('24:00'),'midnight projection crosses year');
 await page.getByLabel('选择日程日期').fill('2026-10-28');
 for(const width of [320,390,667,1100]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'columns fit '+width);check(await page.locator('.week-deadline-count').evaluateAll(es=>es.every(e=>e.getBoundingClientRect().height>=14)),'counts legible '+width)}
 await page.setViewportSize({width:390,height:844});await page.locator('.week-scroll').evaluate(el=>el.scrollTop=530);await page.screenshot({path:path.resolve(__dirname,'../../build/deadline-stacks.png'),fullPage:true});
 await button('月视图').click();check((await page.locator('.month-day[data-date="2026-10-28"]').innerText()).includes('截止任务midnight'),'month retains original date');
 check(await page.evaluate(()=>fixture.cards.find(c=>c.id==='midnight').dueDate==='2026-10-28'&&fixture.cards.find(c=>c.id==='midnight').dueTime==='00:00'),'projection never mutates stored task');
 check(errors.length===0,errors.join(';'));console.log('PASS: counted deadline stacks in hour cells, dense/same-hour tasks, midnight previous-day/year/week boundaries, exact details, unchanged month/data');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
