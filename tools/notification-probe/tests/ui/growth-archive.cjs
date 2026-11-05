const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.clock.install({time:new Date('2026-10-28T12:00:00+08:00')});
 await page.addInitScript(()=>{
  window.fixture={growth:[],cards:[{id:'activity',sourceId:'s',title:'【演示】志愿服务实际参与',category:'活动',requirements:[],status:'todo',dueDate:'2026-10-28',dueTime:'',group:'演示',deadline:''},{id:'signup',sourceId:'s',title:'竞赛报名',category:'竞赛',requirements:[],status:'todo',dueDate:'2026-10-28',group:'演示',deadline:''}],sources:[{id:'s',group:'演示',text:'演示原文',receivedAt:1,aiState:'ready'}],napcat:{groups:[],followed:[],pending:[]}};
  window.failSave=false;window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:(type,raw)=>{
   const d=JSON.parse(raw);if(failSave)return JSON.stringify({ok:false,error:'测试保存失败'});
   if(type==='card')fixture.cards.find(c=>c.id===d.id).status=d.state;
   if(type==='saveGrowth'){
    const old=fixture.growth.find(g=>g.id===d.id||d.sourceCardId&&g.sourceCardId===d.sourceCardId);
    if(old&&!d.id)return JSON.stringify({ok:false,error:'该事项已有成长记录'});
    if(d.id)Object.assign(old,d);else fixture.growth.push({...d,id:'g'+(fixture.growth.length+1),state:'active'});
   }
   if(type==='growthState')fixture.growth.find(g=>g.id===d.id).state=d.state;
   return JSON.stringify({ok:true});
  }};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const button=name=>page.getByRole('button',{name,exact:true}),check=(v,m)=>{if(!v)throw Error(m)};
 await button('成长').click();check(await page.getByText('成长档案',{exact:true}).count()===1,'growth navigation: '+JSON.stringify({errors,text:await page.locator('#app').innerText()}));
 await button('其他成果，0条经历').click();await button('加入经历').click();await page.getByLabel('成果名称',{exact:true}).fill('数学竞赛实际参与');
 await page.getByLabel('经历类别',{exact:true}).selectOption('竞赛');await page.getByLabel('时间（年月）',{exact:true}).fill('2026-10');
 await page.getByLabel('参赛说明',{exact:true}).fill('完成现场答题，结果待公布');
 await page.evaluate(()=>window.failSave=true);await button('保存经历').click();check(await page.getByLabel('竞赛名称',{exact:true}).inputValue()==='数学竞赛实际参与','failed save retains draft');
 check((await page.locator('dialog').innerText()).includes('测试保存失败'),'inline save error');await page.evaluate(()=>window.failSave=false);
 await button('保存经历').click();check((await page.locator('#app').innerText()).includes('数学竞赛实际参与'),'manual growth saved');
 await button('查看经历：数学竞赛实际参与').click();await button('编辑经历').click();await page.getByLabel('参赛说明',{exact:true}).fill('已核对的比赛结果');
 await page.keyboard.press('Escape');check(await button('继续编辑').isVisible(),'dirty growth guarded on back');await button('继续编辑').click();await button('保存经历').click();
 await button('今日').click();await page.getByLabel('查看事项：【演示】志愿服务实际参与').click();await page.getByRole('dialog').getByRole('button',{name:'标记完成',exact:true}).click();
 await page.evaluate(()=>showAgenda('activity'));check(await button('建立成长记录').isVisible(),'completed source still allows manual growth');await button('建立成长记录').click();
 check(await page.getByLabel('活动名称',{exact:true}).inputValue()==='【演示】志愿服务实际参与','source title prefilled');
 await button('保存经历').click();check(await page.locator('#growthForm').isVisible(),'explicit participation required');
 await page.getByLabel('我已实际参与或完成这项经历').check();await button('保存经历').click();
 check(await page.evaluate(()=>fixture.growth.length)===2,'source created once');
 await button('成长').click();await button('志愿，1条经历').click();await button('查看经历：【演示】志愿服务实际参与').click();
 await button('移入已移除').click();await button('确认移除').click();await button('已移除').click();await button('恢复经历').click();
 check(await page.evaluate(()=>fixture.growth.find(g=>g.sourceCardId==='activity').state)==='active','soft delete recoverable');
 await button('全部经历').click();
 await page.evaluate(()=>{fixture.cards.find(c=>c.id==='activity').status='todo';refreshNative(true)});
 await button('查看经历：【演示】志愿服务实际参与').click();check((await page.locator('dialog').innerText()).includes('来源事项当前未完成'),'undo source leaves experience with warning');await page.keyboard.press('Escape');
 await page.evaluate(()=>{fixture.cards.find(c=>c.id==='activity').status='deleted';refreshNative(true)});
 await button('查看经历：【演示】志愿服务实际参与').click();check((await page.locator('dialog').innerText()).includes('来源事项已移入回收站'),'deleted source retains experience');await page.keyboard.press('Escape');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-list.png'),fullPage:true});
 await page.reload();await button('成长').click();
 // Reload gets a fresh native fixture in this browser harness; persistence is covered by SQLite instrumentation.
 await button('今日').click();await page.getByLabel('查看事项：竞赛报名').click();await page.getByRole('dialog').getByRole('button',{name:'标记完成',exact:true}).click();
 check(await button('建立成长记录').count()===0,'signup must not suggest participation');
 await button('成长').click();await button('其他成果，0条经历').click();await button('加入经历').click();await page.getByLabel('成果名称',{exact:true}).fill('<script>成长 & 真实文字</script>');
 await page.evaluate(()=>paste('外部分享通知'));check(await button('继续编辑').isVisible(),'incoming share cannot replace growth draft');await button('继续编辑').click();
 for(const width of [320,390,667]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'page fits '+width);check(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth),'form fits '+width);}
 await page.addStyleTag({content:'body,button,input,textarea,select{font-size:24px!important}'});await page.setViewportSize({width:320,height:640});
 check(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth),'large text fits');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-form.png'),fullPage:true});
 check(errors.length===0,errors.join(';'));console.log('PASS: growth navigation, manual/edit/error/draft, completion consent, source linking, registration exclusion, recoverable removal, responsive forms');
 await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
