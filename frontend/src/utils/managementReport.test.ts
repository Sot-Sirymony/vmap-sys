import { describe, expect, it } from 'vitest';
import { customPeriodProblem, healthReasonText, periodLabel } from './managementReport';

describe('managementReport helpers (V19 RM-FR-01, RM-FR-06)', () => {
  it('puts every health reason into words', () => {
    expect(healthReasonText({ code: 'COMPLETED' })).toBe('Completed');
    expect(healthReasonText({ code: 'COMPLETED_OPEN_WORK', value: 2 })).toBe('Completed, but 2 task(s) are still open');
    expect(healthReasonText({ code: 'GOAL_OVERDUE', value: 3 })).toBe('Target date passed 3 day(s) ago');
    expect(healthReasonText({ code: 'BLOCKED_TASKS', value: 1 })).toBe('1 blocked task(s)');
    expect(healthReasonText({ code: 'CRITICAL_BLOCKER', value: 1, title: 'No budget' })).toBe('Critical blocker: No budget');
    expect(healthReasonText({ code: 'CRITICAL_BLOCKER', value: 3, title: 'No budget' })).toBe('Critical blocker: No budget and 2 more');
    expect(healthReasonText({ code: 'OVERDUE_WORK', value: 2 })).toBe('2 overdue task(s) or step(s)');
    expect(healthReasonText({ code: 'OPEN_BLOCKERS', value: 1 })).toBe('1 open blocker(s)');
    expect(healthReasonText({ code: 'BEHIND_SCHEDULE', value: 25, elapsed: 70 })).toBe('Progress 25% with 70% of the time gone');
    expect(healthReasonText({ code: 'ON_TRACK' })).toBe('No overdue work or open blockers');
    // A code from a newer server is shown as it is, never dropped.
    expect(healthReasonText({ code: 'NEW_RULE' } as never)).toBe('NEW_RULE');
  });

  it('checks a custom period the same way the server does', () => {
    const today = '2026-11-17';
    expect(customPeriodProblem('', '2026-11-10', today)).toBe('Choose a from date and a to date.');
    expect(customPeriodProblem('2026-11-10', '2026-11-01', today)).toBe('The period must start on or before its end.');
    expect(customPeriodProblem('2026-11-01', '2026-11-18', today)).toBe('The period cannot end after today.');
    expect(customPeriodProblem('2025-11-16', today, today)).toBe('A custom period can cover at most 366 days.');
    expect(customPeriodProblem('2025-11-17', today, today)).toBe('');
  });

  it('names a preset, or the dates of a custom period', () => {
    expect(periodLabel({ kind: 'LAST_QUARTER', from: '2026-07-01', to: '2026-09-30', statusAsOfToday: true, closedBeforeRecording: true }))
      .toBe('Last quarter');
    expect(periodLabel({ kind: 'CUSTOM', from: '2026-11-01', to: '2026-11-10', statusAsOfToday: false, closedBeforeRecording: true }))
      .toBe('Nov 1, 2026 to Nov 10, 2026');
  });
});

describe('report filter in the URL (V19 RM-FR-06, RM-FR-10)', () => {
  it('reads only known values, leaving out the defaults, and writes them back', async () => {
    const { filterFromSearch, filterToSearch } = await import('./managementReport');
    expect(filterFromSearch(new URLSearchParams(''))).toEqual({});
    const filter = filterFromSearch(new URLSearchParams('dreamId=7&areaId=3&period=CUSTOM&from=2026-11-01&to=2026-11-05'
      + '&priority=HIGH,BOGUS&owner=%20Dara%20&health=RED,NOPE&completed=0'));
    expect(filter).toEqual({ dreamId: 7, period: 'CUSTOM', from: '2026-11-01', to: '2026-11-05', priorities: ['HIGH'], owner: 'Dara',
      healths: ['RED'], includeCompleted: false });
    expect(filterFromSearch(new URLSearchParams('areaId=3&period=THIS_MONTH'))).toEqual({ areaId: 3 });
    expect(filterFromSearch(new URLSearchParams('period=CUSTOM&from=bad'))).toEqual({});
    expect(filterToSearch(filter)).toEqual({ dreamId: '7', areaId: '', period: 'CUSTOM', from: '2026-11-01', to: '2026-11-05',
      priority: 'HIGH', owner: 'Dara', health: 'RED', completed: '0' });
    expect(filterToSearch({ areaId: 3, period: 'LAST_WEEK' })).toMatchObject({ areaId: '3', period: 'LAST_WEEK', from: '', completed: '' });
  });
});
