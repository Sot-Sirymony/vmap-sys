import { expect, test, type Page } from '@playwright/test';
import { createSeedAccount, deleteSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, type Seed } from './support';

/**
 * NFR-02: the page checks run with every dialog closed, so these open each kind
 * of dialog and check it while it is on screen: axe rules, focus held inside,
 * and Escape closing it.
 */
type DialogCase = { name: string; open: (page: Page, seed: Seed) => Promise<void> };

const byUrl = (path: string) => async (page: Page) => {
  await page.goto(path);
};

const byButton = (path: string, button: string) => async (page: Page) => {
  await page.goto(path);
  await settle(page);
  await page.getByRole('button', { name: button, exact: true }).first().click();
};

const CASES: DialogCase[] = [
  { name: 'vision area wizard', open: byUrl('/vision-areas?create=area') },
  { name: 'dream form', open: byUrl('/dreams?create=dream') },
  { name: 'goal form', open: byUrl('/goals?create=goal') },
  { name: 'step form', open: byUrl('/steps?create=step') },
  { name: 'task form', open: byUrl('/tasks?create=task') },
  { name: 'obstacle form', open: byUrl('/obstacles?create=obstacle') },
  { name: 'partner form', open: byUrl('/partners?create=partner') },
  { name: 'report an issue', open: byButton('/', 'Report an issue') },
  {
    name: 'archive confirmation',
    open: async (page) => {
      await page.goto('/vision-areas');
      await settle(page);
      await page.getByRole('button', { name: 'Vision area actions' }).first().click();
      await page.getByRole('menuitem', { name: 'Archive' }).click();
    },
  },
  {
    name: 'command palette',
    open: async (page) => {
      await page.goto('/');
      await settle(page);
      await page.keyboard.press('Control+k');
    },
  },
  {
    name: 'keyboard shortcuts',
    open: async (page) => {
      await page.goto('/');
      await settle(page);
      await page.locator('body').click({ position: { x: 5, y: 5 } });
      await page.keyboard.press('Shift+?');
    },
  },
];

let seed: Seed;

test.beforeAll(async () => {
  seed = await createSeedAccount();
});

test.afterAll(async () => {
  await deleteSeedAccount(seed);
});

async function openAndCheck(page: Page, dialogCase: DialogCase) {
  await signIn(page, seed.email);
  await dialogCase.open(page, seed);
  const dialog = page.getByRole('dialog').last();
  await expect(dialog).toBeVisible();
  // A screen reader announces the dialog by its name, so it must have one.
  await expect(dialog).toHaveAccessibleName(/\S/);
  await settle(page);
  await expectNoAccessibilityViolations(page);
  // Focus must sit inside the dialog, or keyboard users type into the page behind it.
  expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  return dialog;
}

for (const dialogCase of CASES) {
  test.describe(dialogCase.name, () => {
    test('open on desktop: no violations, focus inside, Escape closes', async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 900 });
      await openAndCheck(page, dialogCase);
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    test('open in dark mode on a phone: no violations or sideways scroll', async ({ page }) => {
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.setViewportSize({ width: 390, height: 844 });
      await openAndCheck(page, dialogCase);
      await expectNoSidewaysScroll(page);
    });
  });
}

test('the command palette still runs a command from the keyboard', async ({ page }) => {
  await signIn(page, seed.email);
  await settle(page);
  await page.keyboard.press('Control+k');
  const search = page.getByRole('combobox', { name: 'Command palette search' });
  await expect(search).toBeFocused();
  await search.fill('Obstacles');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('ArrowUp');
  await expect(search).toHaveAttribute('aria-activedescendant', /palette-option-/);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/obstacles/);
});
