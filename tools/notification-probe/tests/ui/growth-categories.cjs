const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url');const path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.fixture={cards:[],sources:[],growth:[{id:'g1',title:'【演示】校园志愿服务',category:'志愿',date:'2026-10-28',result:'完成现场服务',state:'active',sourceCardId:''},{id:'g2',title:'【演示】数学竞赛',category:'竞赛',date:'2026-10-27',result:'结果待公布',state:'active',sourceCardId:''},{id:'g3',title:'【演示】旧志愿经历',category:'志愿',date:'',result:'',state:'deleted',sourceCardId:''}],napcat:{groups:[],followed:[],pending:[]}};
  window.Xunji={snapshot:()=>JSON.stringify(fixture),action:()=>{},mutate:(type,raw)=>{
   const d=JSON.parse(raw);if(type==='saveGrowth')fixture.growth.push({...d,id:'g4',state:'active'});
   if(type==='growthState')fixture.growth.find(g=>g.id===d.id).state=d.state;
   return JSON.stringify({ok:true});
  }};
 });
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const check=(v,m)=>{if(!v)throw Error(m)},button=name=>page.getByRole('button',{name,exact:true});
 await button('成长').click();
 check(await page.locator('header').isHidden(),'growth removes brand and plus header');
 check((await page.locator('#app').innerText()).includes('留下走过的每一步')===false,'no decorative text');
 const rows=page.locator('.growth-category-entry');check(await rows.count()===8,'all eight vertical categories');
 const boxes=await rows.evaluateAll(els=>els.map(e=>{const b=e.getBoundingClientRect();return {x:b.x,y:b.y,w:b.width,h:b.height}}));
 check(boxes[0].y<24&&boxes.every((b,i)=>b.w>350&&b.h>=60&&(!i||b.y>=boxes[i-1].y+boxes[i-1].h-1)),'full-width ordered category rows');
 check(await button('新增经历').count()===0,'management moved inside category');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-categories.png'),fullPage:true});
 await button('志愿，1条经历').click();check(await rows.count()===8,'category expands inline without replacing other sections');check(await page.getByRole('region',{name:'志愿经历'}).isVisible(),'inline region below section');check(await page.getByText('是否加入新经历？',{exact:true}).isVisible(),'join prompt above records');check((await page.locator('#app').innerText()).includes('【演示】校园志愿服务'),'selected category records');
 check(!(await page.locator('#app').innerText()).includes('【演示】数学竞赛'),'other category excluded');
 await button('加入经历').click();check(await page.getByLabel('经历类别',{exact:true}).inputValue()==='志愿','new form defaults to selected category');
 await page.getByLabel('活动名称',{exact:true}).fill('【演示】补充志愿经历');await button('保存经历').click();
 check((await page.locator('#app').innerText()).includes('【演示】补充志愿经历'),'save stays in record category');
 await button('已移除').click();check((await page.locator('#app').innerText()).includes('【演示】旧志愿经历'),'category trash remains accessible');
 await button('恢复经历').click();await button('全部经历').click();check((await page.locator('#app').innerText()).includes('【演示】旧志愿经历'),'restored within category');
 await page.screenshot({path:path.resolve(__dirname,'../../build/growth-category-detail.png'),fullPage:true});
 await page.evaluate(()=>growthBack());check(await rows.count()===8,'native back collapses expanded section');
 await button('竞赛，1条经历').click();check(await page.getByRole('region',{name:'竞赛经历'}).isVisible(),'second category expands in place');await button('竞赛，1条经历').click();check(await page.getByRole('region',{name:'竞赛经历'}).count()===0,'same category collapses');await button('证书，0条经历').click();check(await page.getByText('暂无证书经历',{exact:true}).isVisible(),'empty category is concise');await button('证书，0条经历').click();
 await button('志愿，3条经历').click();await button('今日').click();check(await page.locator('header').isVisible(),'other page header preserved');await button('成长').click();check(await rows.count()===8&&await page.locator('.growth-category-entry[aria-expanded=true]').count()===0,'bottom growth collapses all categories');
 for(const width of [320,390,667]){
  await page.setViewportSize({width,height:844});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no horizontal overflow '+width);
  const last=await rows.last().boundingBox(),nav=await page.locator('#nav').boundingBox();check(last.y+last.height<=nav.y+1,'all categories fit before navigation '+width);
 }
 await page.addStyleTag({content:'body,button,input,textarea,select{font-size:24px!important}'});await page.setViewportSize({width:320,height:640});
 check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'large text fits');await rows.last().scrollIntoViewIfNeeded();check(await rows.last().isVisible(),'last category reachable with large text');
 check(errors.length===0,errors.join(';'));console.log('PASS: inline accordion categories, join prompt, concise empty states, scoped create/recovery, collapse/back, hidden brand, responsive layout');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
