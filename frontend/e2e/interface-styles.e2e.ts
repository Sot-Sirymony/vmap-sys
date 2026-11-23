import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { api, createSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, today, type Seed } from './support';

/**
 * BRD 26 phase 7 — the interface styles in a real browser.
 *
 *   ISC-08 / IS-NFR-05  visual snapshots: Dashboard, Tasks, Vision Map and a dialog
 *                       × 3 styles × 2 modes × 2 languages, and Khmer at 360px
 *   ISC-02 / IS-NFR-01  axe on every one of those, on three accents, and on Liquid
 *                       Glass over a dark and a bright background image
 *   ISC-05 / IS-NFR-03  at most 4 blurred layers, none inside a scrolling container
 *   ISC-03              glass falls back to solid under prefers-contrast and High contrast
 *   IS-NFR-04           Dashboard scroll timing with Liquid Glass at 4× CPU throttle
 *
 * Snapshot baselines live next to this file. Create or refresh them with
 * `npx playwright test interface-styles --update-snapshots`, then review the
 * images before committing: approving them is the ISC-08 sign-off.
 */

type StyleWire = 'MODERN' | 'CLASSIC' | 'LIQUID_GLASS';
type ModeWire = 'LIGHT' | 'DARK';

const STYLES: { wire: StyleWire; id: string }[] = [
  { wire: 'MODERN', id: 'modern' },
  { wire: 'CLASSIC', id: 'classic' },
  { wire: 'LIQUID_GLASS', id: 'glass' },
];
const MODES: ModeWire[] = ['LIGHT', 'DARK'];
const LANGUAGES = ['en', 'km'] as const;

const PAGES: { name: string; open: (page: Page) => Promise<void> }[] = [
  { name: 'dashboard', open: (page) => visit(page, '/') },
  { name: 'tasks', open: (page) => visit(page, '/tasks') },
  { name: 'vision-map', open: (page) => visit(page, '/vision-map') },
  {
    name: 'dialog',
    open: async (page) => {
      await visit(page, '/tasks?create=task');
      await expect(page.getByRole('dialog').last()).toBeVisible();
      await page.waitForTimeout(400);
    },
  },
];

let seed: Seed;

/**
 * Playwright starts a new worker after a failing test, which would run
 * beforeAll again and register another account, and the backend limits sign-ups
 * per network. So the seed account is cached under node_modules/.cache (which
 * Playwright does not clear between runs) and
 * reused for the whole run (up to 30 minutes).
 */
const SEED_FILE = resolve(process.cwd(), 'node_modules/.cache/vms-e2e/interface-styles-seed.json');

test.beforeAll(async () => {
  if (existsSync(SEED_FILE)) {
    const cached = JSON.parse(readFileSync(SEED_FILE, 'utf8')) as Seed & { createdAt: number };
    if (Date.now() - cached.createdAt < 30 * 60_000) {
      try {
        await api(cached.token, '/preferences/appearance');
        seed = cached;
        return;
      } catch {
        // Expired or deleted: make a new one.
      }
    }
  }
  seed = await createSeedAccount();
  mkdirSync(dirname(SEED_FILE), { recursive: true });
  writeFileSync(SEED_FILE, JSON.stringify({ ...seed, createdAt: Date.now() }));
});

// No afterAll delete: deleting an account needs an e-mailed step-up code since
// V20/V24, and a restarted worker must be able to reuse the cached account. The
// account is a throwaway @example.invalid one in the local database.

async function setAppearance(changes: Record<string, unknown>) {
  await api(seed.token, '/preferences/appearance', 'PUT', changes);
}

async function visit(page: Page, path: string) {
  await page.goto(path);
  await settle(page);
}

/** Same day as the seed data, at a fixed hour, so greetings and "due today" never drift. */
async function freezeClock(page: Page) {
  await page.clock.setFixedTime(new Date(`${today}T10:00:00`));
}

async function start(page: Page, options: { style: StyleWire; mode: ModeWire; language?: 'en' | 'km'; accent?: string; highContrast?: boolean; freeze?: boolean }) {
  // A second start in the same test signs in again from a clean browser state,
  // so the new appearance arrives with the login response.
  if (page.url().startsWith('http')) {
    await page.evaluate(() => localStorage.clear());
    await page.context().clearCookies();
  }
  await setAppearance({
    interfaceStyle: options.style,
    themeMode: options.mode,
    themeAccent: options.accent ?? 'BLUE',
    highContrast: options.highContrast ?? false,
    reduceMotion: true,
  });
  const language = options.language ?? 'en';
  await page.addInitScript((lang) => localStorage.setItem('vms.language', lang), language);
  if (options.freeze !== false) {
    await freezeClock(page);
  }
  await signIn(page, seed.email);
}

/** Every element with a backdrop filter that is on screen, and whether it sits inside a scrolling container. */
async function blurredLayers(page: Page) {
  return page.evaluate(() => {
    const scroller = document.scrollingElement;
    const layers: { tag: string; className: string; inScroller: boolean }[] = [];
    for (const element of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
      const style = getComputedStyle(element);
      const filter = style.backdropFilter || (style as unknown as Record<string, string>).webkitBackdropFilter;
      if (!filter || filter === 'none') continue;
      const rect = element.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0 || style.visibility === 'hidden' || style.display === 'none') continue;
      if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) continue;
      let inScroller = false;
      for (let parent = element.parentElement; parent && parent !== scroller && parent !== document.body; parent = parent.parentElement) {
        const parentStyle = getComputedStyle(parent);
        const scrolls = /(auto|scroll)/.test(parentStyle.overflowY + parentStyle.overflowX);
        if (scrolls && (parent.scrollHeight > parent.clientHeight + 1 || parent.scrollWidth > parent.clientWidth + 1)) {
          inScroller = true;
          break;
        }
      }
      layers.push({ tag: element.tagName.toLowerCase(), className: String(element.className).slice(0, 80), inScroller });
    }
    return layers;
  });
}

/** Text and data that change between runs (seed codes, generated e-mail). */
function masks(page: Page) {
  return [page.locator('text=/e2e-\\d+/'), page.locator('text=/\\b(VA|D|G|S|T|P)-\\d+\\b/')];
}

for (const style of STYLES) {
  for (const mode of MODES) {
    for (const language of LANGUAGES) {
      test(`${style.id} ${mode.toLowerCase()} ${language}: snapshots and axe`, async ({ page }) => {
        test.setTimeout(180_000);
        await page.setViewportSize({ width: 1280, height: 900 });
        await start(page, { style: style.wire, mode, language });
        await expect(page.locator('html')).toHaveAttribute('data-style', style.id);
        for (const target of PAGES) {
          await target.open(page);
          await expectNoAccessibilityViolations(page);
          await expect(page).toHaveScreenshot(`${target.name}-${style.id}-${mode.toLowerCase()}-${language}.png`, {
            animations: 'disabled',
            mask: masks(page),
            maxDiffPixelRatio: 0.01,
          });
        }
      });
    }

    test(`${style.id} ${mode.toLowerCase()}: Khmer at 360px does not clip`, async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 780 });
      await start(page, { style: style.wire, mode, language: 'km' });
      for (const path of ['/', '/tasks']) {
        await visit(page, path);
        await expectNoSidewaysScroll(page);
      }
      await visit(page, '/');
      await expect(page).toHaveScreenshot(`dashboard-${style.id}-${mode.toLowerCase()}-km-360.png`, {
        animations: 'disabled',
        fullPage: true,
        mask: masks(page),
        maxDiffPixelRatio: 0.01,
      });
    });
  }

  for (const accent of ['VIOLET', 'TEAL']) {
    test(`${style.id}: ${accent.toLowerCase()} accent passes axe in both modes`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 900 });
      for (const mode of MODES) {
        await start(page, { style: style.wire, mode, accent });
        for (const path of ['/', '/tasks']) {
          await visit(page, path);
          await expectNoAccessibilityViolations(page);
        }
      }
    });
  }
}

/** A plain SVG gradient as a data URL: the darkest and the brightest backdrops glass can meet. */
function backgroundImage(kind: 'dark' | 'bright'): string {
  const [a, b] = kind === 'dark' ? ['#000000', '#1d2a44'] : ['#ffffff', '#fff6c7'];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/></svg>`;
  return `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;
}

for (const kind of ['dark', 'bright'] as const) {
  for (const mode of MODES) {
    test(`glass over a ${kind} background image (${mode.toLowerCase()}) passes axe`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 900 });
      await page.addInitScript((image) => localStorage.setItem('vms-bg-image', image), backgroundImage(kind));
      await start(page, { style: 'LIQUID_GLASS', mode });
      await expect(page.locator('html')).toHaveAttribute('data-bg-image', 'on');
      for (const path of ['/', '/tasks']) {
        await visit(page, path);
        await page.mouse.wheel(0, 400);
        await page.waitForTimeout(300);
        await expectNoAccessibilityViolations(page);
      }
    });
  }
}

test.describe('glass placement (ISC-05)', () => {
  for (const path of ['/', '/tasks', '/vision-map']) {
    test(`${path} has at most 4 blurred layers, none inside a scrolling container`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 900 });
      await start(page, { style: 'LIQUID_GLASS', mode: 'LIGHT' });
      await visit(page, path);
      await page.mouse.wheel(0, 400); // the header becomes glass once content scrolls under it
      await page.waitForTimeout(300);
      const layers = await blurredLayers(page);
      expect(layers.length, JSON.stringify(layers)).toBeLessThanOrEqual(4);
      expect(layers.filter((layer) => layer.inScroller), 'blur inside a scrolling container').toEqual([]);
    });
  }

  test('a dialog adds one layer and keeps the total at 4 or fewer', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await start(page, { style: 'LIQUID_GLASS', mode: 'DARK' });
    await visit(page, '/tasks?create=task');
    await expect(page.getByRole('dialog').last()).toBeVisible();
    const layers = await blurredLayers(page);
    expect(layers.length, JSON.stringify(layers)).toBeLessThanOrEqual(4);
  });

  test('Modern and Classic blur nothing', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    for (const style of ['MODERN', 'CLASSIC'] as const) {
      await start(page, { style, mode: 'LIGHT' });
      await visit(page, '/');
      await page.mouse.wheel(0, 400);
      await page.waitForTimeout(300);
      expect(await blurredLayers(page)).toEqual([]);
    }
  });
});

test.describe('glass fallbacks (ISC-03)', () => {
  test('prefers-contrast: more turns glass solid', async ({ page }) => {
    await page.emulateMedia({ contrast: 'more' });
    await page.setViewportSize({ width: 1280, height: 900 });
    await start(page, { style: 'LIQUID_GLASS', mode: 'LIGHT' });
    await visit(page, '/');
    await page.mouse.wheel(0, 400);
    await page.waitForTimeout(300);
    expect(await blurredLayers(page)).toEqual([]);
    await visit(page, '/settings/appearance');
    await expect(page.getByTestId('glass-fallback-note')).toHaveText('Your device asks for less transparency, so panels stay solid.');
  });

  test('High contrast turns glass solid and the picker says why', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await start(page, { style: 'LIQUID_GLASS', mode: 'DARK', highContrast: true });
    await visit(page, '/');
    await page.mouse.wheel(0, 400);
    await page.waitForTimeout(300);
    expect(await blurredLayers(page)).toEqual([]);
    await visit(page, '/settings/appearance');
    await expect(page.getByTestId('glass-fallback-note')).toHaveText('High contrast is on, so panels stay solid.');
  });
});

test.describe('header anatomy (ISC-07)', () => {
  test('Classic shows breadcrumbs and rectangular navigation; Modern shows the greeting and pills', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await start(page, { style: 'CLASSIC', mode: 'LIGHT' });
    await visit(page, '/tasks');
    const classicNav = await page.locator('.app-sidebar .Mui-selected').first().evaluate((element) => getComputedStyle(element).borderTopLeftRadius);
    expect(classicNav).toBe('4px');

    await start(page, { style: 'MODERN', mode: 'LIGHT' });
    await visit(page, '/tasks');
    const modernNav = await page.locator('.app-sidebar .Mui-selected').first().evaluate((element) => getComputedStyle(element).borderTopLeftRadius);
    expect(modernNav).toBe('999px');
  });
});

/**
 * IS-NFR-04. Headless Chrome caps requestAnimationFrame at 30 fps here for every
 * style, throttled or not, so frame intervals cannot show a 16 ms budget. What
 * this checks instead is the main-thread work per scrolled frame with Liquid
 * Glass at 4× CPU throttle (Chrome's Performance metrics), which must stay
 * under 16 ms. The blur itself runs on the GPU compositor, which a headless run
 * cannot measure: UAT_V26.md keeps a check on a real mid-range phone for that.
 */
test('Dashboard scroll work per frame stays under 16 ms with Liquid Glass at 4× CPU throttle', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  // No fixed clock here: frame timing needs the real performance.now().
  await start(page, { style: 'LIQUID_GLASS', mode: 'LIGHT', freeze: false });
  await visit(page, '/');
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Performance.enable');
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  const metrics = async () =>
    Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((metric) => [metric.name, metric.value])) as Record<string, number>;

  const before = await metrics();
  const frames = await page.evaluate(async () => {
    let count = 0;
    let running = true;
    const tick = () => {
      count += 1;
      if (running) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
    const max = document.documentElement.scrollHeight - window.innerHeight;
    for (let step = 0; step < 90; step += 1) {
      window.scrollTo(0, (max * (step % 30)) / 30);
      await new Promise((resolve) => setTimeout(resolve, 16));
    }
    running = false;
    return count;
  });
  const after = await metrics();
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });

  const perFrame = ((after.TaskDuration - before.TaskDuration) * 1000) / frames;
  testInfo.annotations.push({ type: 'frames', description: `${perFrame.toFixed(2)} ms main-thread work per frame over ${frames} frames at 4× throttle` });
  expect(perFrame).toBeLessThan(16);
});
