const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.fixture={cards:[],sources:[],growth:[{id:'old',title:'旧记录',category:'竞赛',date:'2025-11-18',result:'旧的完整说明',details:{},attachments:[],state:'active',sourceCardId:''}],napcat:{groups:[],followed:[],pending:[]}};
  window.cancelledDrafts=[];window.failSave=false;window.Xunji={snapshot:()=>JSON.stringify(fixture),action:(type,raw)=>{
   if(type==='growthDraftCancel')cancelledDrafts.push(raw);
   if(type==='growthImages'){const d=JSON.parse(raw);setTimeout(()=>growthImagesReady({ok:true,token:d.token,images:[{id:'12345678-1234-1234-1234-123456789abc',name:'演示证明.png'}]}),10)}
  },mutate:(type,raw)=>{if(failSave)return JSON.stringify({ok:false,error:'测试保存失败'});const d=JSON.parse(raw);if(type==='saveGrowth'){const old=fixture.growth.find(g=>g.id===d.id);if(old)Object.assign(old,d);else fixture.growth.push({...d,id:'g'+fixture.growth.length,state:'active'})}return JSON.stringify({ok:true})}};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const button=n=>page.getByRole('button',{name:n,exact:true}),check=(v,m)=>{if(!v)throw Error(m)};
 await button('成长').click();await button('竞赛，1条经历').click();await button('加入经历').click();
 check(await page.evaluate(()=>!['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName)),'opening form must not focus text entry');
 check(await page.getByLabel('竞赛名称',{exact:true}).isVisible(),'competition name field');
 await page.getByLabel('竞赛名称',{exact:true}).fill('【演示】数学竞赛');await page.getByLabel('奖项',{exact:true}).fill('省二等奖');await page.getByLabel('时间（年月）',{exact:true}).fill('2026-10');
 await button('添加证明图片').click();await page.waitForFunction(()=>document.querySelectorAll('.growth-proof-thumb').length===1);
 await button('查看证明图片：演示证明.png').click();check(await page.locator('#growthProofView').isVisible(),'image preview');await page.keyboard.press('Escape');check(await page.locator('#growthForm').isVisible(),'preview escape keeps draft');
 await page.evaluate(()=>window.failSave=true);await button('保存经历').click();check(await page.locator('.growth-proof-thumb').count()===1,'failed save preserves image');await page.evaluate(()=>window.failSave=false);await button('保存经历').click();
 check(await page.getByText('2026年10月',{exact:true}).isVisible(),'records grouped by year month');
 await button('查看经历：【演示】数学竞赛').click();check((await page.locator('dialog').innerText()).includes('省二等奖'),'award shown in detail');check(await page.locator('.growth-proof-thumb').count()===1,'saved image detail');await button('编辑经历').click();
 check(await page.getByLabel('奖项',{exact:true}).inputValue()==='省二等奖','award edit persists');check(await page.getByLabel('时间（年月）',{exact:true}).inputValue()==='2026-10','month persists');
 await button('移除证明图片：演示证明.png').click();await page.keyboard.press('Escape');check(await button('继续编辑').isVisible(),'removed image marks draft dirty');await button('放弃修改').click();
 await button('查看经历：旧记录').click();await button('编辑经历').click();await page.getByLabel('竞赛名称',{exact:true}).fill('旧记录修改');await button('保存经历').click();check(await page.evaluate(()=>fixture.growth.find(g=>g.id==='old').date)==='2025-11-18','legacy exact day retained when month unchanged');
 await button('成长').click();await button('志愿，0条经历').click();await button('加入经历').click();check(await page.getByLabel('活动名称',{exact:true}).isVisible(),'volunteer fields');await page.getByLabel('服务时长（小时）',{exact:true}).fill('8.5');await page.getByLabel('活动名称',{exact:true}).fill('【演示】校园服务');await button('保存经历').click();check(await page.evaluate(()=>fixture.growth.find(g=>g.title==='【演示】校园服务').details.hours)==='8.5','hours persisted');
 for(const [category,title,extra] of [['证书','证书名称','颁发单位'],['项目','项目名称','个人角色'],['学生工作','职务或工作名称','组织单位'],['校园活动','活动名称','主办单位'],['实践','实践名称','实践单位'],['其他成果','成果名称',null]]){
  await button('成长').click();await button(category+'，0条经历').click();await button('加入经历').click();check(await page.getByLabel(title,{exact:true}).isVisible(),'category title '+category);if(extra)check(await page.getByLabel(extra,{exact:true}).isVisible(),'specific field '+category);await button('取消').click();
 }
 await button('成长').click();await button('竞赛，2条经历').click();await button('加入经历').click();await button('添加证明图片').click();await page.waitForFunction(()=>document.querySelectorAll('.growth-proof-thumb').length===1);
 await page.keyboard.press('Escape');await button('放弃修改').click();check(await page.evaluate(()=>cancelledDrafts.length)>0,'discard tells native to clean imports');
 await button('加入经历').click();for(const width of [320,390,667]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth),'form fits '+width)}
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.resolve(__dirname,'../../build/growth-competition-form.png'),fullPage:true});
 check(errors.length===0,errors.join(';'));console.log('PASS: no autofocus, category fields, year-month grouping, proof add/preview/remove/discard, legacy exact day, save failure');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
