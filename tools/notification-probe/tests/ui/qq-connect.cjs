const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.actions=[];window.fixture={cards:[],sources:[],groups:[],growth:[],hasKey:false,napcat:{enabled:false,hasToken:true,status:'暂未连接电脑，进度已保留',groups:[],followed:['12345'],pending:[],sync:[{groupId:'12345',groupName:'测试群<script>',since:1793150000,checkedThrough:1793200000,batchEnd:1793700000,phase:'error',detail:'历史未接上上次位置',resuming:true}]},qqExtraction:{ready:3,pending:2,processing:1,error:1}};window.Xunji={snapshot:()=>JSON.stringify(fixture),action:(type,value)=>actions.push({type,value})};});
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 await page.getByRole('button',{name:'信息与待办',exact:true}).click();await page.getByRole('button',{name:'设置',exact:true}).click();
 const check=(v,m)=>{if(!v)throw Error(m)},button=n=>page.getByRole('button',{name:n,exact:true});
 check(await page.getByRole('heading',{name:'QQ 接入',exact:true}).isVisible(),'dedicated QQ entry');
 check(await page.locator('#qqConnection').innerText().then(t=>t.includes('电脑 NapCat')&&t.includes('USB')&&t.includes('收件箱')),'actual transport explained');
 await page.locator('#qqConnection').screenshot({path:path.resolve(__dirname,'../../build/qq-connect-overview.png')});
 await page.getByText('连接教程：从电脑到手机',{exact:true}).click();
 const guide=await page.locator('#qqConnectionGuide').innerText();for(const word of ['Python 3','Platform-Tools','v4.18.41','start-demo.bat','USB 调试','qrcode.png','onebot','webui','关注','自动补取'])check(guide.includes(word),'guide step '+word);
 await button('保存并连接').click();check(await page.evaluate(()=>actions.some(a=>a.type==='napcatConnect'&&a.value==='')),'saved token reused without retyping');
 await button('立即同步').click();check(await page.evaluate(()=>actions.some(a=>a.type==='napcatSync')),'manual check action');
 await button('暂停接收').click();check(await page.evaluate(()=>actions.some(a=>a.type==='napcatStop')),'pause action');
 check(await page.locator('#qqSyncProgress').innerText().then(t=>t.includes('测试群<script>')&&t.includes('历史未接上上次位置')&&t.includes('上次原文检查到')),'group progress and gap shown as text');
 check(await page.locator('#qqAiProgress').innerText().then(t=>t.includes('已整理 3')&&t.includes('待处理 3')&&t.includes('失败 1')),'AI queue distinct from raw sync');
 await page.locator('#napcatKey').fill('unsaved-private-token');await page.evaluate(()=>{fixture.napcat.status='电脑QQ在线';refreshNative();});
 check(await page.locator('#napcatKey').inputValue()==='unsaved-private-token','status refresh cannot erase input');
 check(await page.locator('#qqConnectionGuide').evaluate(e=>e.open),'refresh preserves open tutorial');
 await button('复制启动命令').click();check(await page.evaluate(()=>actions.some(a=>a.type==='qqCopyGuide'&&a.value.includes('desktop.py start'))),'copy exact startup command');
 for(const width of [320,390,667]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no horizontal overflow '+width);}
 await page.setViewportSize({width:320,height:844});await page.addStyleTag({content:'body{font-size:22px}'});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'large type layout');
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.resolve(__dirname,'../../build/qq-connect-guide.png'),fullPage:true});
 check(errors.length===0,errors.join(';'));console.log('PASS: QQ guide, saved token, sync/pause, per-group gap, separate AI queue, refresh preservation, narrow/large-type layout');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
