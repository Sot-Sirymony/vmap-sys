import { expect, test } from '@playwright/test';
import { api, createSeedAccount, deleteSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, today, type Seed } from './support';

/**
 * BRD 27 SR-NFR-06: one full Streamline review in a real browser — start from a
 * goal, answer stage 1 in bulk, pass through the stages in order (one marked
 * Not needed), make a task repeat, finish, and read the summary. Axe on the way.
 */

let seed: Seed;
/** For the style matrix: a completed review of the seed step, and an open one of a second step. */
let completedId: number;
let openId: number;

type Review = { id: number; items: { id: number }[] };

test.beforeAll(async () => {
  seed = await createSeedAccount();
  const done = await api<Review>(seed.token, '/streamline-reviews', 'POST', { stepId: seed.stepId });
  await api(seed.token, `/streamline-reviews/${done.id}/items/bulk`, 'PUT',
    { itemIds: done.items.map((item) => item.id), requirementSource: 'SELF', requirementOwner: 'Me', verdict: 'NEEDED' });
  for (let stage = 1; stage <= 5; stage += 1) {
    await api(seed.token, `/streamline-reviews/${done.id}/stages/${stage}/complete`, 'POST');
  }
  completedId = done.id;
  const second = await api<{ id: number }>(seed.token, '/steps', 'POST', {
    goalId: seed.goalId, title: 'Gather documents', owner: 'E2E Tester', sequenceNumber: 2, complex: false, priority: 'MEDIUM',
    targetDate: today, status: 'NOT_STARTED',
  });
  for (const title of ['Find payslips', 'Scan ID card']) {
    await api(seed.token, '/tasks', 'POST', { stepId: second.id, title, owner: 'E2E Tester', priority: 'MEDIUM', dueDate: today,
      status: 'NOT_STARTED', progressPercent: 0 });
  }
  openId = (await api<{ id: number }>(seed.token, '/streamline-reviews', 'POST', { stepId: second.id })).id;
});

test.afterAll(async () => {
  await deleteSeedAccount(seed);
});

test('a full Streamline review of a goal, start to summary', async ({ page }) => {
  test.setTimeout(120_000);
  await page.setViewportSize({ width: 1280, height: 900 });
  await signIn(page, seed.email);

  await page.goto(`/streamline/new?goalId=${seed.goalId}`);
  await expect(page.getByRole('heading', { name: 'Stage 1: Question' })).toBeVisible();
  await settle(page);
  await expectNoAccessibilityViolations(page);
  // Later stages are locked until the stage before is done (D-27-3).
  await expect(page.getByRole('tab', { name: /Automate.*Locked/ })).toBeDisabled();

  // Stage 1: bulk marking (Q-27-2).
  await page.getByRole('checkbox', { name: /Select all/ }).check();
  const bulk = page.getByRole('group', { name: 'Answer several at once' });
  await bulk.getByRole('combobox', { name: 'Who asked for it' }).click();
  await page.getByRole('option', { name: 'Myself' }).click();
  await bulk.getByLabel('Name').fill('Me');
  await bulk.getByRole('combobox', { name: 'Verdict' }).click();
  await page.getByRole('option', { name: 'Needed', exact: true }).click();
  await bulk.getByRole('button', { name: 'Apply to selected' }).click();
  await page.getByRole('button', { name: 'Done, next stage' }).click();

  // Stage 2 and 3: nothing to cut or simplify in this small plan.
  await expect(page.getByRole('heading', { name: 'Stage 2: Delete' })).toBeVisible();
  await expectNoAccessibilityViolations(page);
  await page.getByRole('button', { name: 'Done, next stage' }).click();
  await expect(page.getByRole('heading', { name: 'Stage 3: Simplify' })).toBeVisible();
  await page.getByRole('button', { name: 'Done, next stage' }).click();

  // Stage 4: marked Not needed, with a reason.
  await expect(page.getByRole('heading', { name: 'Stage 4: Speed up' })).toBeVisible();
  await page.getByRole('button', { name: 'Not needed…' }).click();
  await page.getByLabel(/Reason/).fill('Nothing here has finished yet to measure.');
  await page.getByRole('button', { name: 'Mark not needed' }).click();

  // Stage 5: make the task repeat weekly, then finish.
  await expect(page.getByRole('heading', { name: 'Stage 5: Automate' })).toBeVisible();
  await expectNoAccessibilityViolations(page);
  await page.getByTestId(/^automate-T-/).first().getByRole('button', { name: 'Make it repeat' }).click();
  await expect(page.getByText(/will repeat\./)).toBeVisible();
  await page.getByRole('button', { name: 'Finish the review' }).click();

  const summary = page.getByRole('region', { name: 'Summary' });
  await expect(summary).toBeVisible();
  await expect(summary).toContainText('1 repeating');
  await expect(page.getByText('Completed').first()).toBeVisible();
  await expectNoAccessibilityViolations(page);

  // The review is listed, and the phone layout does not scroll sideways.
  await page.goto('/streamline');
  await expect(page.getByText(/Save a deposit/).first()).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await settle(page);
  await expectNoSidewaysScroll(page);
  await expectNoAccessibilityViolations(page);
});

/**
 * SR-NFR-02 / SRC-09: the Streamline pages pass axe in every interface style,
 * light and dark, English and Khmer; and Khmer does not overflow at 360px
 * (SR-NFR-05).
 */
const STYLES = ['MODERN', 'CLASSIC', 'LIQUID_GLASS'] as const;
const MODES = ['LIGHT', 'DARK'] as const;

for (const style of STYLES) {
  for (const mode of MODES) {
    for (const language of ['en', 'km'] as const) {
      test(`Streamline pages pass axe: ${style.toLowerCase()} ${mode.toLowerCase()} ${language}`, async ({ page }) => {
        test.setTimeout(90_000);
        await api(seed.token, '/preferences/appearance', 'PUT', { interfaceStyle: style, themeMode: mode, reduceMotion: true });
        await page.addInitScript((lang) => localStorage.setItem('vms.language', lang), language);
        await page.setViewportSize({ width: 1280, height: 900 });
        await signIn(page, seed.email);
        for (const path of [`/streamline/${openId}`, `/streamline/${completedId}`, '/streamline']) {
          await page.goto(path);
          await settle(page);
          await expectNoAccessibilityViolations(page);
        }
        if (language === 'km') {
          await page.setViewportSize({ width: 360, height: 780 });
          for (const path of [`/streamline/${openId}`, `/streamline/${completedId}`]) {
            await page.goto(path);
            await settle(page);
            await expectNoSidewaysScroll(page);
          }
        }
      });
    }
  }
}
