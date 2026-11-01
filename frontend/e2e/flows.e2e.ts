import { expect, test } from '@playwright/test';
import { api, createSeedAccount, deleteSeedAccount, signIn, settle, today, type Seed } from './support';

let seed: Seed;

test.beforeEach(async ({ page }) => {
  seed = await createSeedAccount();
  await signIn(page, seed.email);
});

test.afterEach(async () => {
  await deleteSeedAccount(seed);
});

test('a day on Today: attitude, habit, and finishing due work', async ({ page }) => {
  await page.goto('/today');
  await expect(page.getByText('Call the bank')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Start review' })).toBeVisible();

  await page.getByRole('button', { name: '4', exact: true }).click();
  await expect(page.getByRole('button', { name: '4', exact: true })).toHaveAttribute('aria-pressed', 'true');

  await page.getByLabel('New habit').fill('Read');
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Read', exact: true }).click();
  await expect(page.getByRole('checkbox', { name: 'Read', exact: true })).toBeChecked();
  await expect(page.getByText('1 day')).toBeVisible();

  await page.getByRole('checkbox', { name: 'Complete Call the bank' }).click();
  await expect(page.getByText('Call the bank')).toHaveCount(0);
  // The backend completes a step when its last task finishes.
  await expect.poll(async () => (await api<{ status: string }>(seed.token, `/steps/${seed.stepId}`)).status).toBe('COMPLETED');
});

test('the fear log saves, edits, archives and restores', async ({ page }) => {
  await page.goto('/journal');
  await page.getByLabel('What are you afraid of?').fill('Rejected by the bank');
  const answers = page.locator('form textarea:not([aria-hidden=true])');
  await answers.nth(0).fill('Wait a year');
  await answers.nth(1).fill('Ask one bank');
  await page.getByRole('button', { name: 'Save fear' }).click();
  await expect(page.getByText('Worst case: Wait a year')).toBeVisible();

  await page.getByRole('button', { name: 'Edit Rejected by the bank' }).click();
  await page.getByLabel('What are you afraid of?').fill('Edited fear');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Edited fear')).toBeVisible();

  await page.getByRole('button', { name: 'Remove Edited fear' }).click();
  await expect(page.getByText('No fears written down yet.')).toBeVisible();
  await page.getByLabel('Show archived').check();
  await page.getByRole('button', { name: 'Restore Edited fear' }).click();
  await expect(page.getByRole('button', { name: 'Edit Edited fear' })).toBeVisible();
});

test('modules unlock in order through real data and forms', async ({ page }) => {
  await page.goto('/modules');
  await page.getByText('See Your Dream Clearly').click();
  await page.getByRole('button', { name: 'Mark as done' }).click();
  await expect(page.getByText(/^Saved\./)).toBeVisible();

  await page.goto('/modules');
  await page.getByText('Rewrite Limiting Beliefs').click();
  await page.getByLabel('Limiting belief').fill('Too late');
  await page.getByLabel('Replacement statement').fill('I can start now');
  await page.getByRole('button', { name: 'Save exercise' }).click();
  await expect(page.getByText(/^Saved\./)).toBeVisible();

  await page.goto('/modules/3');
  await expect(page.getByText('Finish the earlier modules to unlock this one.')).toBeVisible();
});

test('plan, milestone, PDF, commitment and the objectives card', async ({ page }) => {
  await page.goto('/plan');
  await page.getByLabel('Milestone').fill('First payment');
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(page.getByText('First payment')).toBeVisible();

  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Download PDF' }).click()]);
  expect(download.suggestedFilename()).toMatch(/\.pdf$/);

  await page.goto(`/dreams/${seed.dreamId}`);
  await page.getByLabel('First action').fill('Call the bank');
  await page.getByRole('button', { name: 'Sign commitment' }).click();
  await expect(page.getByText('First action: Call the bank')).toBeVisible();

  await page.goto('/');
  await expect(page.getByText('Dreams written and ranked: 1')).toBeVisible();
  await expect(page.getByText('Milestones reached: 1')).toBeVisible();
});

test('assessment saves and reports results', async ({ page }) => {
  await page.goto('/assessment');
  for (const label of ['Settling for average', 'Fear of failure', 'Avoiding criticism', 'Unclear vision', 'Not knowing how', 'Lack of resources']) {
    await page.getByRole('group', { name: label }).getByRole('button', { name: '3' }).click();
  }
  await page.getByRole('button', { name: 'Save assessment' }).click();
  await expect(page.getByText('Your results')).toBeVisible();
  await expect(page.getByText(`Baseline taken ${today}`)).toBeVisible();
});

test('Khmer applies across the app and survives a reload', async ({ page }) => {
  await page.goto('/settings/account');
  await page.getByRole('button', { name: 'ខ្មែរ' }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'km');
  await page.goto('/modules');
  await expect(page.getByText('មើលឃើញក្តីស្រមៃរបស់អ្នកឱ្យច្បាស់')).toBeVisible();
  await page.goto('/today');
  await expect(page.getByText('អាទិភាព', { exact: true })).toBeVisible();
  await settle(page);
});
