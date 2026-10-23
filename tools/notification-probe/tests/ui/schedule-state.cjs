const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/17478/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
let browser;

(async () => {
  browser = await chromium.launch({
    executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai' });
  await page.clock.setFixedTime(new Date('2026-10-23T12:00:00+08:00'));
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.addInitScript(() => {
    window.fixture = {
      cards: Array.from({ length: 14 }, (_, i) => ({
        id: 'task-' + i, title: '日程事项 ' + i, category: '作业', status: 'todo',
        dueDate: '2026-10-28', dueTime: '10:00', deadline: '10:00', requirements: [],
      })), sources: [], napcat: { groups: [], followed: [], pending: [] },
    };
    window.Xunji = {
      snapshot: () => JSON.stringify(fixture), action: () => {},
      mutate: (type, raw) => {
        const payload = JSON.parse(raw);
        if (type === 'card') fixture.cards.find(c => c.id === payload.id).status = payload.state;
        return '{"ok":true}';
      },
    };
  });
  const check = (value, message) => { if (!value) throw Error(message); };
  const button = name => page.getByRole('button', { name, exact: true });
  await page.goto(pathToFileURL(path.resolve(__dirname, '../../assets/index.html')).href);
  await button('日程').click();
  await page.getByLabel('选择日程日期').fill('2026-10-28');
  const details = page.locator('.schedule-day-details');
  check(!await details.evaluate(el => el.open), 'day list starts collapsed');
  await details.locator('summary').click();
  await details.evaluate(el => { el.scrollTop = 120; });
  const listTop = await details.evaluate(el => el.scrollTop);
  await page.locator('.week-scroll').evaluate(el => { el.scrollTop = 400; });
  await page.evaluate(() => { fixture.cards[0].title = '已同步的新标题'; refreshNative(); });
  check(await details.evaluate(el => el.open), 'live sync keeps the day list expanded');
  check(await details.evaluate(el => el.scrollTop) === listTop, 'live sync preserves day list scroll');
  check(await page.locator('.week-scroll').evaluate(el => el.scrollTop) === 400, 'live sync preserves the resized week timeline scroll');
  check((await details.innerText()).includes('已同步的新标题'), 'expanded list shows fresh data');

  // Use the actual row action; another task must remain reachable after completing one.
  await details.getByRole('button', { name: '完成', exact: true }).first().click();
  check(await details.evaluate(el => el.open), 'completing a task keeps the day list expanded');
  check(await details.getByRole('button', { name: '完成', exact: true }).count() === 13, 'completed item removed and remaining tasks stay available');
  await page.screenshot({ path: path.resolve(__dirname, '../../build/schedule-state.png'), fullPage: true });
  await button('月视图').click();
  check(await details.evaluate(el => el.open), 'view switch keeps the same day list expanded');
  await details.locator('summary').click();
  await page.evaluate(() => { fixture.cards[1].title = '再次同步'; refreshNative(); });
  check(!await details.evaluate(el => el.open), 'sync respects an explicitly collapsed list');
  await details.locator('summary').click();
  await page.getByLabel('选择日程日期').fill('2026-10-29');
  check(!await details.evaluate(el => el.open), 'different day starts collapsed');
  check((await details.innerText()).includes('0项'), 'different day has its own list');
  check(!errors.length, errors.join(';'));
  console.log('PASS: day list expansion and scroll survive sync, completion and view switch; collapse and date changes respected');
  await browser.close();
})().catch(async error => {
  console.error(error);
  if (browser) await browser.close();
  process.exitCode = 1;
});
