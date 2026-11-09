import { test } from '@playwright/test';
import { createSeedAccount, deleteSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, type Seed } from './support';

/**
 * NFR-03: every signed-in page in Khmer. Khmer runs longer than English, so the
 * phone check matters most here. English still on screen is reported as an
 * annotation (user data such as dream titles is English in the seed account).
 */
const PAGES = ['/', '/today', '/journal', '/assessment', '/strengths', '/modules', '/plan', '/vision-areas', '/dreams', '/vision-map', '/goals',
  '/steps', '/steps/undated', '/tasks', '/obstacles', '/partners', '/work-style', '/communication', '/reviews', '/insights',
  '/issue-reports', '/import-export', '/settings/appearance', '/settings/security', '/settings/account'];

// The seed account's own records and fixed names, which are not UI text.
const SEED_TEXT = /Career|Own a home|Save a deposit|Compare loans|Call the bank|A Mentor|Need funding|A fair day|Advice|A helpful friend|E2E Tester|Moonshot|PDF|JSON|Excel|Google|VA-\d+|D-\d+|G-\d+|S-\d+|T-\d+|P-\d+|e2e-/g;

let seed: Seed;

test.beforeAll(async () => {
  seed = await createSeedAccount();
});

test.afterAll(async () => {
  await deleteSeedAccount(seed);
});

for (const path of PAGES) {
  test(`${path} in Khmer on a phone`, async ({ page }, testInfo) => {
    await page.addInitScript(() => localStorage.setItem('vms.language', 'km'));
    await page.setViewportSize({ width: 390, height: 844 });
    await signIn(page, seed.email);
    await page.goto(path);
    await settle(page);
    await expectNoSidewaysScroll(page);
    await expectNoAccessibilityViolations(page);

    const english = await page.evaluate(() => {
      const found = new Set<string>();
      const walker = document.createTreeWalker(document.querySelector('main') ?? document.body, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        const text = node.textContent?.trim() ?? '';
        if (/[A-Za-z]{3,}/.test(text) && node.parentElement?.offsetParent !== null) {
          found.add(text.slice(0, 80));
        }
      }
      return [...found];
    });
    const leftover = english.map((text) => text.replace(SEED_TEXT, '').trim()).filter((text) => /[A-Za-z]{3,}/.test(text));
    if (leftover.length > 0) {
      testInfo.annotations.push({ type: 'english', description: leftover.join(' | ') });
    }
  });
}
