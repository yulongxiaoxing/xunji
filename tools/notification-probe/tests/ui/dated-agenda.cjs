const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path = require('node:path'), { pathToFileURL } = require('node:url');
let browser;
(async () => {
  browser = await chromium.launch({ executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai' });
  await page.clock.setFixedTime(new Date('2026-10-23T12:00:00+08:00'));
  await page.addInitScript(() => {
    const make = (id, category, date, time, info) => ({ id, title: id, category, status: 'todo', dueDate: date, dueTime: time, requirements: [], deadline: '', ...(category === '课程' ? { course: info } : info ? { event: info } : {}) });
    window.fixture = { cards: [
      make('下午考试', '考试', '2026-10-23', '14:00', { startTime: '14:00', endTime: '16:00', location: 'A203' }),
      make('早间课程', '课程', '2026-10-23', '08:00', { startTime: '08:00', endTime: '09:40', location: 'B201', teacher: '测试老师' }),
      make('提交报告', '作业', '2026-10-23', '10:00'),
      make('明天活动', '活动', '2026-10-24', '18:30', { startTime: '18:30', endTime: '20:00', location: '活动中心' }),
      make('下月竞赛', '竞赛', '2026-11-03', '14:00', { startTime: '14:00', endTime: '17:00', location: '机房' }),
      make('无日期事项', '其他', '', ''), make('过期事项', '作业', '2026-10-22', '12:00'),
      { ...make('已删除的课程', '课程', '2026-10-23', '09:00', { startTime: '09:00', endTime: '10:00' }), status: 'deleted' },
      { ...make('未确认消息', '活动', '2026-10-23', '11:00'), status: 'inbox' },
    ], sources: [], napcat: { groups: [], followed: [], pending: [] } };
    window.Xunji = { snapshot: () => JSON.stringify(fixture), action: () => {}, mutate: (type, raw) => { const d = JSON.parse(raw); if (type === 'card') fixture.cards.find(c => c.id === d.id).status = d.state; return '{"ok":true}'; } };
  });
  const check = (v, m) => { if (!v) throw Error(m); };
  const button = name => page.getByRole('button', { name, exact: true });
  await page.goto(pathToFileURL(path.resolve(__dirname, '../../assets/index.html')).href);
  const today = page.locator('[data-agenda-date="2026-10-23"]');
  check(await today.count() === 1, 'today has one combined date box');
  check(JSON.stringify(await today.locator('.agenda-title').allTextContents()) === JSON.stringify(['早间课程', '提交报告', '下午考试']), 'course and other tasks share chronological order');
  for (const name of ['课程', '今日待办', '未来七天', '未明确截止时间']) check(await page.getByRole('heading', { name, exact: true }).count() === 0, 'removed old section ' + name);
  check(await page.locator('.date-agenda').evaluateAll(es => es.every((e, i) => i === 0 || e.dataset.agendaDate >= es[i - 1].dataset.agendaDate)), 'date boxes ordered');
  check((await page.locator('[data-agenda-date="2026-10-24"]').innerText()).includes('明天活动'), 'tomorrow own box');
  check((await page.locator('[data-agenda-date="2026-10-25"]').innerText()).includes('后天'), 'day after tomorrow present even when empty');
  check((await page.locator('[data-agenda-date="2026-11-03"]').innerText()).includes('下月竞赛'), 'future events not lost after seven days');
  check(!(await page.locator('#app').innerText()).includes('无日期事项') && !(await page.locator('#app').innerText()).includes('过期事项'), 'no guessed date or leftover sections');
  await today.getByRole('button', { name: '完成', exact: true }).first().click();
  check(!(await today.innerText()).includes('提交报告'), 'completion updates combined list');
  await button('课程管理').click();
  await page.getByLabel('已移除课程').check();
  check((await page.locator('#courseList').innerText()).includes('已删除的课程'), 'removed course management retained');
  await page.keyboard.press('Escape');
  await button('日程').click();
  const exam = page.locator('.week-event[data-id="下午考试"]');
  check(await exam.getAttribute('data-start') === '840' && await exam.getAttribute('data-end') === '960', 'exam exact two-hour interval in calendar');
  await button('今日').click();
  for (const width of [320, 390, 667]) { await page.setViewportSize({ width, height: 844 }); check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'date boxes fit ' + width); }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: path.resolve(__dirname, '../../build/dated-agenda.png'), fullPage: true });
  await page.clock.setFixedTime(new Date('2026-10-24T00:01:00+08:00')); await page.evaluate(() => refreshNative());
  check(await page.locator('.date-agenda').first().getAttribute('data-agenda-date') === '2026-10-24', 'date boxes advance at midnight');
  console.log('PASS: unified dated agenda, mixed time order, future dates, removed headings, course recovery, event calendar intervals and midnight rollover');
  await browser.close();
})().catch(async e => { console.error(e); if (browser) await browser.close(); process.exitCode = 1; });
