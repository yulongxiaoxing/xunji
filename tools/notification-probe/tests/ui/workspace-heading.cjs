const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {pathToFileURL}=require('node:url'),path=require('node:path');let browser;
(async()=>{
 browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844}});
 await page.addInitScript(()=>{window.Xunji={snapshot:()=>JSON.stringify({cards:[],sources:[],growth:[],napcat:{groups:[],followed:[],pending:[]}}),action:()=>{}}});
 await page.goto(pathToFileURL(path.resolve(__dirname,'../../assets/index.html')).href);
 const check=(v,m)=>{if(!v)throw Error(m)},button=name=>page.getByRole('button',{name,exact:true});
 await button('信息与待办').click();
 for(const label of ['待办','关注群','设置']){
  await button(label).click();
  check(!await page.locator('.wrap>header').isVisible(),'workspace removes the old brand row');
  check(await button('添加').count()===1,'one accessible add button');
  for(const width of [320,390,760]){
   await page.setViewportSize({width,height:844});
   const title=await page.locator('.workspace-heading h1').boundingBox(),add=await button('添加').boundingBox();
   check(add.x>title.x+title.width&&Math.abs(add.y+add.height/2-title.y-title.height/2)<4,'add sits beside workspace title '+label+' '+width);
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no overflow');
  }
  await button('添加').click();check(await page.getByRole('dialog').isVisible(),'relocated add opens existing menu');await page.keyboard.press('Escape');
 }
 await button('收件箱').click();check(await button('添加').count()===0,'glass inbox keeps its existing compact navigation');
 await button('今日').click();check(await button('添加').count()===1,'today keeps existing add entry after returning');
 console.log('PASS: workspace brand removal, title-row add/menu, responsive layout, compact inbox and page return');await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
