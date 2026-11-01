import { test } from '@playwright/test';
import { createSeedAccount, deleteSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, type Seed } from './support';

const ROUTES: ((seed: Seed) => string)[] = [
  () => '/',
  () => '/today',
  () => '/journal',
  () => '/assessment',
  () => '/modules',
  () => '/plan',
  () => '/vision-areas',
  () => '/dreams',
  (seed) => `/dreams/${seed.dreamId}`,
  () => '/vision-map',
  () => '/goals',
  () => '/steps',
  () => '/steps/undated',
  () => '/tasks',
  () => '/obstacles',
  () => '/partners',
  (seed) => `/partners/${seed.partnerId}`,
  () => '/work-style',
  () => '/communication',
  () => '/reviews',
  () => '/insights',
  () => '/issue-reports',
  () => '/import-export',
  () => '/settings/appearance',
  () => '/settings/security',
  () => '/settings/account',
];

const NAMES = ['/', '/today', '/journal', '/assessment', '/modules', '/plan', '/vision-areas', '/dreams', '/dreams/:id', '/vision-map',
  '/goals', '/steps', '/steps/undated', '/tasks', '/obstacles', '/partners', '/partners/:id', '/work-style', '/communication', '/reviews',
  '/insights', '/issue-reports', '/import-export', '/settings/appearance', '/settings/security', '/settings/account'];

let seed: Seed;

test.beforeAll(async () => {
  seed = await createSeedAccount();
});

test.afterAll(async () => {
  await deleteSeedAccount(seed);
});

ROUTES.forEach((route, index) => {
  const name = NAMES[index];

  test.describe(name, () => {
    test('desktop has no accessibility violations', async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 900 });
      await signIn(page, seed.email);
      await page.goto(route(seed));
      await settle(page);
      await expectNoAccessibilityViolations(page);
    });

    test('dark mode has no accessibility violations', async ({ page }) => {
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.setViewportSize({ width: 1280, height: 900 });
      await signIn(page, seed.email);
      await page.goto(route(seed));
      await settle(page);
      await expectNoAccessibilityViolations(page);
    });

    test('phone has no sideways scroll or violations', async ({ page }) => {
      await page.setViewportSize({ width: 390, height: 844 });
      await signIn(page, seed.email);
      await page.goto(route(seed));
      await settle(page);
      await expectNoSidewaysScroll(page);
      await expectNoAccessibilityViolations(page);
    });
  });
});

for (const name of ['/login', '/register', '/forgot-password']) {
  test(`${name} has no violations signed out`, async ({ page }) => {
    await page.goto(name);
    await settle(page);
    await expectNoAccessibilityViolations(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await expectNoSidewaysScroll(page);
  });
}
