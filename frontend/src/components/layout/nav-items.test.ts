import { describe, expect, it } from 'vitest';
import { accountMenuItems, coachItem, groupIdFor, navGroupsFor, navItems, visionMapTabs } from './nav-items';

const user = (role: string, permissions?: string[]) => ({ id: 1, fullName: 'T', email: 't@example.com', role, permissions });
const paths = (who?: ReturnType<typeof user>) => navGroupsFor(who).flatMap((group) => group.items.map((item) => item.to));

describe('navGroupsFor', () => {
  it('shows coaching to coaches and admins, and cohorts to admins only (built-in roles, V9)', () => {
    expect(paths(user('USER'))).not.toContain('/coach');
    expect(paths(undefined)).not.toContain('/coach');
    expect(paths(user('COACH'))).toContain('/coach');
    expect(paths(user('COACH'))).not.toContain('/settings/cohorts');
    expect(paths(user('ADMIN'))).toEqual(expect.arrayContaining(['/coach', '/settings/cohorts']));
  });

  it('follows the permissions the server sent, not the role name (V23 UM-FR-05)', () => {
    const coachWithLog = user('COACH', ['USE_OWN_PLAN', 'COACH_COHORTS', 'VIEW_SECURITY_LOG']);
    expect(paths(coachWithLog)).toEqual(expect.arrayContaining(['/coach', '/settings/security-log']));
    expect(paths(coachWithLog)).not.toContain('/settings/cohorts');
    expect(paths(user('ADMIN', ['USE_OWN_PLAN']))).not.toContain('/settings/cohorts');
  });

  it('groups pages by activity and leaves out a group with nothing to show (V37 D-37-1)', () => {
    expect(navGroupsFor(user('USER')).map((group) => group.id)).toEqual(['home', 'plan', 'do', 'review', 'know']);
    const usersOnly = navGroupsFor(user('COACH', ['USE_OWN_PLAN', 'VIEW_USERS']));
    expect(usersOnly.find((group) => group.id === 'admin')?.items.map((item) => item.to)).toEqual(['/settings/users']);
  });
});

describe('every destination (V37 D-37-6, BR-37-1)', () => {
  it('lists the sidebar, the Vision Map tabs, the account menu and the coach once each', () => {
    const all = navItems.map((item) => item.to);
    for (const to of [...visionMapTabs, ...accountMenuItems, coachItem].map((item) => item.to)) {
      expect(all).toContain(to);
    }
    expect(new Set(all).size).toBe(all.length);
    // Every page of the old sidebar is still reachable.
    for (const to of ['/vision-map/timeline', '/vision-map/report', '/vision-map/reports', '/settings/appearance', '/settings/security',
      '/settings/account', '/import-export', '/issue-reports', '/dream-coach', '/journal', '/work-style', '/insights']) {
      expect(all).toContain(to);
    }
  });

  it('finds the group of a page, including the Vision Map tabs and a dream tree', () => {
    const groups = navGroupsFor(user('USER'));
    expect(groupIdFor('/tasks', groups)).toBe('do');
    expect(groupIdFor('/vision-map/report', groups)).toBe('plan');
    expect(groupIdFor('/dreams/4', groups)).toBe('plan');
    expect(groupIdFor('/settings/appearance', groups)).toBeNull();
  });
});
