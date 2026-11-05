const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path=require('node:path'),{pathToFileURL}=require('node:url');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.fixture={cards:[],sources:[],growth:[],hasKey:true,napcat:{groups:[],followed:[],pending:[]}};window.requests=[];window.actions=[];window.saves=[];window.failSave=false;
  window.Xunji={snapshot:()=>JSON.stringify(fixture),action:(type,value)=>{actions.push(type);if(type==='growthMemoryPreview')requests.push(JSON.parse(value));},mutate:(type,raw)=>{const row=JSON.parse(raw);saves.push(type);if(failSave)return '{"ok":false,"error":"保存失败"}';if(type==='saveGrowth')fixture.growth.push({...row,id:'g'+fixture.growth.length,state:'active'});return '{"ok":true}';}};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const check=(v,m)=>{if(!v)throw Error(m)},button=name=>page.getByRole('button',{name,exact:true});
 const reply=async(record,extra={})=>page.evaluate(({record,extra})=>{const r=requests.at(-1);growthMemoryReady({ok:true,requestToken:r.requestToken,category:r.category,record,...extra})},{record,extra});
 await button('成长').click();await button('志愿，0条经历').click();
 check(await page.getByRole('region',{name:'志愿经历'}).getByRole('button',{name:'口头记忆',exact:true}).count()===1,'category exposes oral memory');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-memory-category.png'),fullPage:true});
 await button('口头记忆').click();check(await page.getByLabel('经历描述',{exact:true}).isVisible(),'experience text entry');
 await button('DeepSeek整理').click();check(await page.evaluate(()=>requests.length)===0,'empty text never requests AI');
 await page.getByLabel('经历描述',{exact:true}).fill('上个月参加校园迎新志愿服务，服务四小时。');await button('DeepSeek整理').click();
 check(await page.evaluate(()=>requests[0].category)==='志愿','request bound to expanded category');
 await reply({title:'校园迎新志愿服务',category:'竞赛',date:'2026-10',result:'服务四小时。',details:{hours:'4',organizer:'学校'}});
 check(await page.getByLabel('经历类别',{exact:true}).inputValue()==='志愿','model cannot switch selected category');
 check(await page.getByLabel('活动名称',{exact:true}).inputValue()==='校园迎新志愿服务','AI fills editable title');
 check(await page.getByLabel('服务时长（小时）',{exact:true}).inputValue()==='4','AI fills matching category fields');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-memory-form.png'),fullPage:true});
 check(await page.evaluate(()=>saves.length)===0,'preview never saves or creates todos');
 check((await page.locator('#growthMemoryOriginal').textContent()).includes('上个月参加校园迎新志愿服务'),'original available for review');
 await page.keyboard.press('Escape');check(await button('继续编辑').isVisible(),'AI-prefilled form is unsaved even without manual edits');await button('继续编辑').click();
 await page.getByLabel('活动名称',{exact:true}).fill('迎新志愿服务（核对后）');await page.evaluate(()=>failSave=true);await button('保存经历').click();check(await page.getByLabel('活动名称',{exact:true}).inputValue()==='迎新志愿服务（核对后）','save failure preserves draft');await page.evaluate(()=>failSave=false);await button('保存经历').click();
 check(await page.evaluate(()=>fixture.growth.length)===1&&await page.evaluate(()=>fixture.cards.length)===0,'confirmed save creates growth only');
 await button('口头记忆').click();await page.getByLabel('经历描述',{exact:true}).fill('新的志愿经历');await button('DeepSeek整理').click();await page.evaluate(()=>window.stale=requests.at(-1));
 await page.getByLabel('经历描述',{exact:true}).fill('修改后的志愿经历');await page.evaluate(()=>growthMemoryReady({ok:true,requestToken:stale.requestToken,category:stale.category,record:{title:'过期结果',details:{}}}));
 check(await page.getByLabel('经历描述',{exact:true}).inputValue()==='修改后的志愿经历','editing invalidates stale result');
 await button('DeepSeek整理').click();await reply(null,{ok:false,error:'网络暂时不可用'});check(await page.getByText('网络暂时不可用',{exact:true}).isVisible(),'AI error displayed');check(await page.getByLabel('经历描述',{exact:true}).inputValue()==='修改后的志愿经历','AI error keeps input');
 await page.keyboard.press('Escape');check(await button('继续编辑').isVisible(),'oral input protected on Escape');await button('继续编辑').click();
 await page.evaluate(()=>paste('外部分享的通知'));check(await button('放弃修改').isVisible()&&await page.getByLabel('经历描述',{exact:true}).isVisible(),'external share protects oral draft');await button('继续编辑').click();
 await page.mouse.click(3,3);check(await button('放弃修改').isVisible(),'backdrop protects oral draft');await button('放弃修改').click();
 await button('成长').click();await button('竞赛，0条经历').click();await button('口头记忆').click();
 await page.evaluate(()=>growthMemoryReady({ok:true,requestToken:stale.requestToken,category:stale.category,record:{title:'旧志愿结果',details:{}}}));check(await page.getByLabel('经历描述',{exact:true}).isVisible(),'reopened different category rejects old result');
 await page.getByLabel('经历描述',{exact:true}).fill('参加数学竞赛，结果还没公布。');await button('DeepSeek整理').click();await reply({title:'数学竞赛',date:'',result:'结果还没公布。',details:{award:''}});check(await page.getByLabel('奖项',{exact:true}).inputValue()===''&&await page.getByLabel('时间（年月）',{exact:true}).inputValue()==='','unknown award and month stay empty');
 await button('取消').click();await button('放弃修改').click();
 for(const category of ['志愿','证书','项目','学生工作','校园活动','实践','其他成果']){await button('成长').click();await button(category+'，'+(category==='志愿'?1:0)+'条经历').click();await button('口头记忆').click();check(await page.evaluate(()=>growthMemoryCategory)===category,'all category entries '+category);await button('关闭').click();}
 await button('成长').click();await button('项目，0条经历').click();for(const width of [320,390,760]){await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'category actions fit '+width);}
 check(errors.length===0,errors.join(';'));console.log('PASS: per-category growth memory, editable preview, explicit save, errors, stale callbacks, draft/share/backdrop guards and responsive actions');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
