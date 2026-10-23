const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path = require('node:path'), { pathToFileURL } = require('node:url');
let browser;
(async () => {
  browser = await chromium.launch({ executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai' });
  await page.clock.setFixedTime(new Date('2026-10-23T12:00:00+08:00'));
  await page.addInitScript(() => {
    const make = (id, time, end) => ({ id, sourceId: 's', title: id, category: '活动', status: 'todo', requirements: [], deadline: time,
      dueDate: '2026-10-28', dueTime: time, ...(end ? { event: { startTime: time, endTime: end, location: '测试地点' } } : {}) });
    window.fixture = { cards: [make('上午活动甲', '09:00', '11:00'), make('上午活动乙', '10:00', '12:00'), make('中午活动丙', '11:00', '13:00'), make('全天事项', ''), make('傍晚截止', '18:00')], sources: [{ id: 's', text: '保留完整原文' }], napcat: { groups: [], followed: [], pending: [] } };
    window.Xunji = { snapshot: () => JSON.stringify(fixture), action: () => {}, mutate: () => '{"ok":true}' };
  });
  const check = (v, m) => { if (!v) throw Error(m); };
  const button = name => page.getByRole('button', { name, exact: true });
  await page.goto(pathToFileURL(path.resolve(__dirname, '../../assets/index.html')).href);
  await button('日程').click(); await page.getByLabel('选择日程日期').fill('2026-10-28');
  const day = page.locator('.week-timed-day[data-date="2026-10-28"]');
  const boxes = await day.locator('.week-event').evaluateAll(es => es.map(e => ({ width: e.getBoundingClientRect().width, day: e.parentElement.clientWidth })));
  check(boxes.every(b => b.width >= b.day - 14), 'overlapping blocks stay nearly full width rather than split columns');
  const borders = await day.locator('.week-event:not(.is-point)').evaluateAll(es => es.map(e => ({left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right,top:parseFloat(e.style.top),borderLeft:getComputedStyle(e).borderLeftWidth,borderRight:getComputedStyle(e).borderRightWidth})));
  check(borders[0].left < borders[1].left && borders[0].right > borders[1].right && borders[1].left < borders[2].left && borders[1].right > borders[2].right, 'both lower event edges stay exposed underneath later overlays');
  check(borders.every(b=>parseFloat(b.borderLeft)>0&&parseFloat(b.borderRight)>0), 'both sides have visible borders');
  // Hit the shared 10:30 region, rather than just clicking a whole event's center.
  await page.locator('.week-scroll').evaluate(el => { el.scrollTop = 500; });
  await page.screenshot({path:path.resolve(__dirname,'../../build/overlay-borders.png'),fullPage:true});
  const at = await day.boundingBox();
  await page.mouse.click(at.x + at.width / 2, at.y + 630 * 64 / 60);
  check(await page.locator('#scheduleOverlap').count() === 1, 'overlap opens a chooser');
  const choices = await page.locator('#scheduleOverlap').innerText();
  check(choices.includes('上午活动甲') && choices.includes('上午活动乙') && !choices.includes('中午活动丙'), 'chooser contains only items overlapping the clicked time');
  await button('查看日程：上午活动乙').click();
  check((await page.locator('#agendaDetail').innerText()).includes('10:00—12:00'), 'chooser opens original detail');
  await button('关闭').click();
  await page.mouse.click(at.x + at.width / 2, at.y + 570 * 64 / 60);
  check(await page.locator('#scheduleOverlap').count() === 0 && (await page.locator('#agendaDetail').innerText()).includes('上午活动甲'), 'non-overlapping region opens the one matching item');
  await button('关闭').click();
  await button('月视图').click();
  const monthDay = page.locator('.month-day[data-date="2026-10-28"]');
  const names = await monthDay.locator('.month-event strong').allTextContents();
  check(names.join('|') === '全天事项|上午活动甲|上午活动乙|中午活动丙|傍晚截止', 'month day puts date-only item first, then time order');
  check((await monthDay.innerText()).includes('09:00—11:00') && (await monthDay.innerText()).includes('18:00'), 'month shows interval and deadline times');
  check(await page.locator('.calendar-surface').evaluate(el => el.scrollHeight > el.clientHeight), 'month scrolls instead of squeezing all dates into screen');
  await monthDay.getByRole('button', { name: '查看日程：全天事项', exact: true }).click();
  check((await page.locator('#agendaDetail').innerText()).includes('全天事项'), 'month item opens detail'); await button('关闭').click();
  await page.locator('.calendar-surface').evaluate(el => { el.scrollTop = 400; });
  await page.evaluate(() => { fixture.cards[0].title = '同步后的活动甲'; refreshNative(); });
  check(await page.locator('.calendar-surface').evaluate(el => el.scrollTop) === 400, 'month update preserves scroll');
  for (const width of [320, 390, 667, 1100]) { await page.setViewportSize({ width, height: 844 }); check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'month fits seven columns ' + width); }
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.calendar-surface').evaluate(el=>{el.scrollTop=el.scrollHeight}); await page.screenshot({ path: path.resolve(__dirname, '../../build/month-agenda.png'), fullPage: true });
  await button('今日').click(); await button('口头记忆').click();
  check(await button('开始说话').count()===0 && await button('语音权限设置').count()===0, 'memory only uses the system keyboard');
  await page.getByLabel('记忆内容').fill('通过输入法输入的原有文字');
  check(await page.getByLabel('记忆内容').inputValue()==='通过输入法输入的原有文字', 'memory accepts editable keyboard text');
  console.log('PASS: full-width overlays and minute hit selection, detailed scrollable month, order/scroll/details, simple keyboard memory input');
  await browser.close();
})().catch(async e => { console.error(e); if (browser) await browser.close(); process.exitCode = 1; });
