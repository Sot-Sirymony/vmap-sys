import { execFileSync } from 'node:child_process';
import { expect, test } from '@playwright/test';
import { api, createSeedAccount, deleteSeedAccount, expectNoAccessibilityViolations, expectNoSidewaysScroll, settle, signIn, type Seed } from './support';

/**
 * V9 Phase 1 across three accounts. Making an admin needs the database, so this
 * runs only when PGPASSWORD is set for the local database, and is skipped otherwise.
 */
const psql = (sql: string) => execFileSync('psql', ['-h', 'localhost', '-U', 'postgres', '-d', 'vision_mapping', '-tAqc', sql]).toString().trim();

test('admin, coach and participant run a cohort end to end', async ({ browser }) => {
  test.skip(!process.env.PGPASSWORD, 'Needs PGPASSWORD for the local database to create an admin.');
  test.setTimeout(90_000);
  const newPage = async () => (await browser.newContext({ baseURL: test.info().project.use.baseURL, channel: 'chrome' } as never)).newPage();
  const admin = await createSeedAccount();
  const coach = await createSeedAccount();
  psql(`update app_users set role = 'ADMIN' where email = '${admin.email}'`);
  const participantEmail = `e2e-p-${Date.now()}@example.invalid`;
  const cohortName = `E2E Pilot ${Date.now()}`;
  let participant: Seed | null = null;
  try {
    // Admin creates the cohort and names the coach.
    const adminPage = await newPage();
    await signIn(adminPage, admin.email);
    await adminPage.goto('/settings/cohorts');
    await settle(adminPage);
    await adminPage.getByLabel('Cohort name').fill(cohortName);
    await adminPage.getByLabel('Coach email').fill(coach.email);
    await adminPage.getByRole('button', { name: 'Create cohort' }).click();
    await expect(adminPage.getByRole('heading', { name: cohortName })).toBeVisible();
    await expectNoAccessibilityViolations(adminPage);
    const code = psql(`select join_code from cohorts where name = '${cohortName}'`);

    // A participant registers with the code from a link.
    const page = await newPage();
    await page.goto(`/register?code=${code}`);
    await expect(page.getByLabel('Cohort join code (optional)')).toHaveValue(code);
    await page.getByLabel('Full name').fill('Pat Participant');
    await page.getByLabel('Email', { exact: true }).fill(participantEmail);
    await page.locator('input[name=password]').fill('Password123');
    await page.locator('input[name=confirmPassword]').fill('Password123');
    await page.getByRole('button', { name: 'Create account' }).click();
    await page.waitForURL(/login/);
    const { token } = await api<{ token: string }>(null, '/auth/login', 'POST', { email: participantEmail, password: 'Password123' });
    participant = { email: participantEmail, token } as Seed;
    await api(token, '/fear-entries', 'POST', { fear: 'Secret fear', worstCase: 'x', likelihood: 'LOW', firstTest: 'y' });

    // The coach sees them, but not their journal, and comments.
    const coachPage = await newPage();
    await signIn(coachPage, coach.email);
    await coachPage.goto('/coach');
    await settle(coachPage);
    await expectNoAccessibilityViolations(coachPage);
    await coachPage.getByRole('link', { name: 'Pat Participant' }).click();
    await settle(coachPage);
    await expect(coachPage.getByText('Journals are private.', { exact: false })).toBeVisible();
    await expect(coachPage.getByText('Secret fear')).toHaveCount(0);
    await expectNoAccessibilityViolations(coachPage);
    await coachPage.getByLabel('Comment').fill('Welcome to the pilot!');
    await coachPage.getByRole('button', { name: 'Send comment' }).click();
    await expect(coachPage.getByText('Welcome to the pilot!')).toBeVisible();

    // The participant reads it on Today and shares journals from Account.
    await signIn(page, participantEmail);
    await page.goto('/today');
    await expect(page.getByText('Welcome to the pilot!')).toBeVisible();
    await page.getByRole('button', { name: 'Mark as read' }).click();
    await expect(page.getByText('All read')).toBeVisible();
    await page.goto('/settings/account');
    await settle(page);
    await expect(page.getByText(`You are in ${cohortName}, coached by E2E Tester.`)).toBeVisible();
    await expectNoAccessibilityViolations(page);
    await page.getByRole('switch', { name: 'Share my fear and attitude journals with my coach' }).click();
    await expect(page.getByRole('switch', { name: 'Share my fear and attitude journals with my coach' })).toBeChecked();

    await coachPage.reload();
    await settle(coachPage);
    await expect(coachPage.getByText(/Secret fear/)).toBeVisible();

    // Khmer on a phone.
    await coachPage.evaluate(() => localStorage.setItem('vms.language', 'km'));
    await coachPage.setViewportSize({ width: 390, height: 844 });
    await coachPage.goto('/coach');
    await settle(coachPage);
    await expect(coachPage.getByRole('heading', { name: 'ការណែនាំ' })).toBeVisible();
    await expectNoSidewaysScroll(coachPage);
    await expectNoAccessibilityViolations(coachPage);
  } finally {
    if (participant) {
      await api(participant.token, '/account/delete', 'POST', { password: 'Password123' });
    }
    await deleteSeedAccount(coach);
    await deleteSeedAccount(admin);
    psql(`update app_users set cohort_id = null where cohort_id in (select id from cohorts where name = '${cohortName}')`);
    psql(`delete from cohorts where name = '${cohortName}'`);
  }
});
