const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url'),path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:'Asia/Shanghai'});await page.clock.setFixedTime(new Date('2026-10-22T09:30:00+08:00'));const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  const make=(id,title,status,dueDate='',dueTime='')=>({id,title,status,dueDate,dueTime,sourceId:'s',group:'课程通知',publisher:'老师',category:'作业',requirements:['核对具体要求'],deadline:'周五前'});
  window.fixture={cards:[make('today','今天提交报告','todo','2026-10-22','18:00'),make('late','昨天未完成','todo','2026-10-21'),make('soon','三天后比赛','todo','2026-10-25'),make('unknown','尚未确认日期','todo'),make('incoming','未确认的收件箱事项','inbox','2026-10-22'),make('ignored','忽略的事项','ignored','2026-10-22'),make('deleted','删除的事项','deleted','2026-10-22'),make('done','今天已完成事项','done','2026-10-22')],sources:[{id:'s',text:'原文周五前',group:'课程通知',publisher:'老师',receivedAt:1,aiState:'ready'}],napcat:{groups:[],followed:[],pending:[]}};
  window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:(type,value)=>{const d=JSON.parse(value),c=fixture.cards.find(c=>c.id===d.id);if(type==='card')c.status=d.state;else if(type==='updateCard')Object.assign(c,d);return '{"ok":true}'}};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const check=(v,m)=>{if(!v)throw Error(m)},button=name=>page.getByRole('button',{name,exact:true});
 check(await page.getByRole('heading',{name:'今天截止',exact:true}).count()===1,'today is home');
 check((await page.locator('.today-list').innerText()).includes('今天提交报告'),'today task visible');
 check(!(await page.locator('.today-list').innerText()).includes('未确认的收件箱事项'),'inbox not scheduled');
 check((await page.locator('.overdue-list').innerText()).includes('昨天未完成'),'overdue visible');
 check((await page.locator('.upcoming-list').innerText()).includes('三天后比赛'),'future seven days');
 check(!(await page.locator('.today-list').innerText()).includes('尚未确认日期'),'unknown date not guessed');
 await page.screenshot({path:path.resolve(__dirname,'../../build/today-home.png'),fullPage:true});
 await button('信息与待办').click();check(await page.getByRole('navigation',{name:'信息与待办功能'}).count()===1,'legacy functions inside workspace');
 for(const label of ['收件箱','待办','关注群','设置']){await button(label).click();check((await page.locator('#nav').innerText()).includes('日程'),'primary nav retained');}
 await button('今日').click();await button('查看事项：尚未确认日期').click();await button('更多操作').click();await button('编辑事项').click();await page.getByText('调整截止日期与时间（可选）',{exact:true}).click();await page.getByLabel('截止日期',{exact:true}).fill('2026-10-22');await page.getByLabel('截止时刻',{exact:true}).fill('20:00');await button('保存修改').click();check((await page.locator('.today-list').innerText()).includes('尚未确认日期'),'date edit appears today');
 await button('日程').click();check(await page.locator('.agenda-item').count()===2,'day schedule only unfinished');await page.getByLabel('显示已完成事项').check();check(await page.locator('.agenda-item').count()===3,'done optional');
 await page.getByLabel('选择日程日期',{exact:true}).fill('2026-10-25');await page.getByLabel('选择日程日期',{exact:true}).dispatchEvent('change');check((await page.locator('.schedule-list').innerText()).includes('三天后比赛'),'date selection changes agenda');
 await page.getByLabel('选择日程日期',{exact:true}).fill('2026-12-31');await page.getByLabel('选择日程日期',{exact:true}).dispatchEvent('change');await button('后一天').click();check(await page.getByLabel('选择日程日期',{exact:true}).inputValue()==='2027-01-01','year boundary');
 await button('回到今天').click();await page.locator('.agenda-item').filter({hasText:'今天提交报告'}).getByRole('button',{name:'完成',exact:true}).click();await button('今日').click();check(!(await page.locator('.today-list').innerText()).includes('今天提交报告'),'completion syncs today');
 for(const width of [320,390,667]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'home no overflow '+width);await button('日程').click();check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'schedule no overflow '+width);await button('今日').click();}
 await page.clock.setFixedTime(new Date('2026-10-23T00:01:00+08:00'));await page.evaluate(()=>refreshNative());check((await page.locator('.date-line').innerText()).includes('10月23日'),'midnight refresh changes local day without data change');
 await page.setViewportSize({width:320,height:640});await page.addStyleTag({content:'body{font-size:24px!important}button,input{font-size:24px!important}'});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'large text home fits');await button('日程').click();check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'large text schedule fits');await button('今日').click();
 await page.evaluate(()=>{fixture.cards=[];refreshNative()});check((await page.locator('.today-list').innerText()).includes('今天没有截止事项'),'real empty home');
 const west=await browser.newPage({timezoneId:'America/Los_Angeles'});await west.clock.setFixedTime(new Date('2026-10-22T00:30:00Z'));await west.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);check((await west.locator('.date-line').innerText()).includes('10月21日'),'calendar follows local timezone rather than UTC');await west.close();
 check(errors.length===0,errors.join(';'));console.log('PASS: today/workspace, date confirmation, status exclusion, schedule filtering, complete sync, year boundary and mobile layouts');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
