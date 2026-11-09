import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api';
const PASSWORD = 'Steady-Harbor-Lamp-42';

export const today = (() => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
})();

export type Seed = {
  email: string;
  token: string;
  dreamId: number;
  goalId: number;
  stepId: number;
  partnerId: number;
};

export async function api<T = unknown>(token: string | null, path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${method} ${path} answered ${response.status}: ${await response.text()}`);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : null) as T;
}

type WithId = { id: number };

/** A throwaway account with one record of each main kind, so pages show real rows. */
export async function createSeedAccount(): Promise<Seed> {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.invalid`;
  const { token } = await api<{ token: string }>(null, '/auth/register', 'POST', { fullName: 'E2E Tester', email, password: PASSWORD });
  const area = await api<WithId>(token, '/vision-areas', 'POST', { name: 'Career', priority: 'HIGH', status: 'ACTIVE' });
  const dream = await api<WithId>(token, '/dreams', 'POST', {
    visionAreaId: area.id, title: 'Own a home', whyImportant: 'For my family', successDefinition: 'Keys in hand', letterRank: 'A',
    dreamType: 'LONG_TERM', priority: 'HIGH', status: 'ACTIVE', moonshot: false, scheduleMode: 'BOTTOM_UP',
  });
  const goal = await api<WithId>(token, '/goals', 'POST', {
    dreamId: dream.id, title: 'Save a deposit', priority: 'HIGH', status: 'IN_PROGRESS', moonshot: false, scheduleMode: 'BOTTOM_UP',
  });
  const step = await api<WithId>(token, '/steps', 'POST', {
    goalId: goal.id, title: 'Compare loans', owner: 'E2E Tester', sequenceNumber: 1, complex: false, priority: 'HIGH', targetDate: today, status: 'NOT_STARTED',
  });
  await api(token, '/tasks', 'POST', {
    stepId: step.id, title: 'Call the bank', owner: 'E2E Tester', priority: 'CRITICAL', dueDate: today, status: 'NOT_STARTED', progressPercent: 0,
  });
  const partner = await api<WithId>(token, '/partners', 'POST', { name: 'A Mentor', supportType: 'MENTOR', status: 'AGREED', relatedDreamId: dream.id });
  await api(token, '/obstacles', 'POST', { relatedDreamId: dream.id, title: 'Need funding', obstacleType: 'MONEY', severity: 'HIGH', status: 'OPEN' });
  await api(token, '/reviews', 'POST', { reviewType: 'DAILY', reviewDate: `${today}T09:00:00`, summary: 'A fair day' });
  await api(token, '/communication-messages', 'POST', { partnerId: partner.id, audience: 'Mentor', purpose: 'Advice', status: 'DRAFT' });
  await api(token, '/gratitude-entries', 'POST', { category: 'OTHER', description: 'A helpful friend' });
  return { email, token, dreamId: dream.id, goalId: goal.id, stepId: step.id, partnerId: partner.id };
}

export async function deleteSeedAccount(seed: Seed) {
  await api(seed.token, '/account/delete', 'POST', { password: PASSWORD });
}

export async function signIn(page: Page, email: string) {
  await page.goto('/login');
  await page.locator('input[type=email], input[name=email]').first().fill(email);
  await page.locator('input[type=password]').first().fill(PASSWORD);
  await page.locator('button[type=submit]').first().click();
  await page.waitForURL((url) => !url.pathname.includes('login'));
}

/** Lets a page finish loading its data, so checks see content and not a spinner. */
export async function settle(page: Page) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(300);
}

export async function expectNoAccessibilityViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const summary = violations.map((violation) =>
    `${violation.id} (${violation.nodes.length}): ${violation.nodes.slice(0, 3).map((node) => node.html.slice(0, 130)).join(' | ')}`);
  expect(summary).toEqual([]);
}

export async function expectNoSidewaysScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, 'pixels of sideways scroll').toBeLessThanOrEqual(0);
}
