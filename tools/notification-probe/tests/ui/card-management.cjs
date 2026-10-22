const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.fixture={cards:[{id:'c1',sourceId:'s1',group:'测试群',publisher:'老师',category:'作业',title:'完成思考题',requirements:['第一题','第二题'],deadline:'周五',status:'inbox'},{id:'c2',sourceId:'s1',group:'测试群',publisher:'老师',category:'考试',title:'随堂测验',requirements:['计算器'],deadline:'',status:'done'}],sources:[{id:'s1',group:'测试群',publisher:'老师',text:'完整原文保持不变',receivedAt:1,aiState:'ready'}],napcat:{groups:[],followed:[],pending:[]}};
  window.failSave=false;window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:(type,value)=>{
   if(window.failSave)return JSON.stringify({ok:false,error:'测试保存失败，请重试'});
   const d=JSON.parse(value);let c=fixture.cards.find(c=>c.id===d.id);
   if(type==='card'){if(d.state==='deleted')c.previous=c.status;c.status=d.state==='restore'?c.previous:d.state;}
   if(type==='updateCard')Object.assign(c,d);
   if(type==='createCard'){fixture.sources.push({id:'m',group:'手动新建',text:d.title,receivedAt:2,aiState:'ready'});fixture.cards.push({...d,id:'m1',sourceId:'m',group:'手动新建',publisher:'',status:'todo'});}
   return JSON.stringify({ok:true});
  }};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 await page.getByRole('button',{name:'信息与待办',exact:true}).click();
 const check=(v,m)=>{if(!v)throw Error(m)};
 await page.screenshot({path:path.resolve(__dirname,'../../build/card-management-inbox.png'),fullPage:true});
 const button=(name)=>page.getByRole('button',{name,exact:true});
 check(await button('不加入待办').count()===1,'inbox must offer decline');
 check(await page.getByText('按消息截止时间自动显示',{exact:false}).count()>0,'schedule should not demand manual date per card');
 await button('不加入待办').click();await button('已忽略').click();check(await page.locator('article.card').count()===1,'ignored visible');
 await button('恢复到收件箱').click();await button('待确认').click();
 await button('更多操作').click();await button('编辑事项').click();await page.getByLabel('标题',{exact:true}).fill('修订标题');await page.getByLabel('具体要求',{exact:true}).fill('修改第一题\n保留第二题');
 await page.getByLabel('截止时间',{exact:true}).fill('下周一');
 await page.evaluate(()=>window.failSave=true);await button('保存修改').click();check(await page.locator('dialog').isVisible(),'failure retains form');check((await page.locator('dialog').innerText()).includes('测试保存失败'),'failure shown inline');
 await page.evaluate(()=>window.failSave=false);await button('保存修改').click();check((await page.locator('article.card').innerText()).includes('修订标题'),'edited content visible');
 await button('查看原文 ↗').click();check((await page.locator('dialog').innerText()).includes('完整原文保持不变'),'original immutable');await page.keyboard.press('Escape');
 await button('更多操作').click();await button('删除事项').click();await button('取消').click();check(await page.locator('article.card').count()===1,'cancel deletion');
 await button('更多操作').click();await button('删除事项').click();await button('移入回收站').click();await button('回收站').click();check(await page.locator('article.card').count()===1,'deleted visible in trash');
 await page.getByLabel('搜索事项、群名或发布人').fill('下周一');check(await page.locator('article.card').count()===1,'deadline searchable');await button('恢复事项').click();await button('待确认').click();
 await button('新建事项').click();await page.getByLabel('标题',{exact:true}).fill('自己新建的任务');await page.getByLabel('具体要求',{exact:true}).fill('手动内容');await button('创建待办').click();await button('待办').click();check((await page.locator('article.card').innerText()).includes('自己新建的任务'),'manual creation without key');
 await button('标记完成').click();await button('已完成').click();const done=page.locator('article.card').filter({hasText:'自己新建的任务'});await done.getByRole('button',{name:'更多操作',exact:true}).click();await button('删除事项').click();await button('移入回收站').click();await button('收件箱').click();await button('回收站').click();await button('恢复事项').click();await button('待办').click();await button('已完成').click();check(await page.locator('article.card').count()===2,'restore completed stays completed');
 for(const width of [320,390,667]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no overflow '+width);}
 await page.emulateMedia({reducedMotion:'reduce'});await button('新建事项').click();await page.getByLabel('标题',{exact:true}).fill('长'.repeat(300));await page.getByLabel('具体要求',{exact:true}).fill('long'.repeat(1000));check(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth),'long form no horizontal overflow');
 await page.screenshot({path:path.resolve(__dirname,'../../build/card-management-form.png'),fullPage:true});
 await page.evaluate(()=>{window.confirm=()=>{throw Error('WebView must not rely on JS confirm')};});
 await button('取消').click();check(await button('继续编辑').count()===1,'dirty editor needs in-page escape');await button('继续编辑').click();check((await page.getByLabel('标题',{exact:true}).inputValue()).length===300,'draft remains');
 await page.evaluate(()=>paste('分享的新通知'));check(await page.getByLabel('标题',{exact:true}).isVisible(),'incoming share preserves dirty draft until confirmed');
 await button('放弃修改').click();check((await page.locator('#body').inputValue())==='分享的新通知','confirmed discard continues queued share');
 await page.keyboard.press('Escape');await page.setViewportSize({width:320,height:640});await button('新建事项').click();await page.getByLabel('标题',{exact:true}).fill('大字号测试');
 await page.addStyleTag({content:'body{font-size:24px!important}button,input,textarea,select{font-size:24px!important}'});
 check(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth),'large text dialog fits small screen');
 check(await button('创建待办').isEnabled(),'large text actions operable');
 check(errors.length===0,errors.join(';'));console.log('PASS: decline/restore, edit/error recovery, immutable original, create, delete/cancel/restore-completed, search and responsive forms');
 await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});

