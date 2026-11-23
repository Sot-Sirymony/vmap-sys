import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { renderHook, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { chartCategorical } from '../../design/tokens';
import { renderPage } from '../../test/render';
import { ChartCard } from './ChartCard';
import { useChartTheme } from './charts/chartTheme';
import { KpiCard } from './KpiCard';
import { dueLabel, NextUpList, sortNextUp, type NextUpTask } from './NextUpList';
import { ProgressRing } from './ProgressRing';
import { RankedProgressList } from './RankedProgressList';
import { StackedStatusBar } from './StackedStatusBar';
import { ZoneHeader } from './ZoneHeader';

/** Reduced motion on, so count-ups show their final value at once (D-25-8). */
function preferReducedMotion(reduced: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduced && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
}

beforeEach(() => preferReducedMotion(true));
afterEach(() => vi.unstubAllGlobals());

describe('ZoneHeader', () => {
  it('renders the zone label as a heading', () => {
    renderPage(<ZoneHeader label="Plan health" />);
    expect(screen.getByRole('heading', { name: 'Plan health' })).toBeInTheDocument();
  });
});

describe('KpiCard (BRD 25 §11)', () => {
  it('shows the number, and gives screen readers the final value', () => {
    renderPage(<KpiCard label="Due this week" value={9} caption="3 due today" />);
    expect(screen.getByText('Due this week')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-number')).toHaveTextContent('9');
    expect(screen.getByText('3 due today')).toBeInTheDocument();
  });

  it('turns a zero into a calm "All clear" when asked (DA-FR-05)', () => {
    renderPage(<KpiCard label="Overdue" value={0} clearWhenZero />);
    expect(screen.getByText('All clear')).toBeInTheDocument();
    expect(screen.queryByTestId('kpi-number')).not.toBeInTheDocument();
  });

  it('links to the list that reproduces its count', () => {
    renderPage(<KpiCard label="Blocked" value={1} to="/tasks?status=BLOCKED" />);
    expect(screen.getByRole('link')).toHaveAttribute('href', '/tasks?status=BLOCKED');
  });

  it('shows a delta beside the number', () => {
    renderPage(<KpiCard label="Overdue" value={2} delta={{ text: '1 more than last week', direction: 'up', tone: 'bad' }} />);
    expect(screen.getByText('1 more than last week')).toBeInTheDocument();
  });

  it('counts up when motion is allowed, and still names the final value', () => {
    preferReducedMotion(false);
    renderPage(<KpiCard label="Due this week" value={9} />);
    // The visible digits animate; the hidden text is the value at once.
    expect(screen.getAllByText('9').length).toBeGreaterThanOrEqual(1);
  });
});

describe('ChartCard (DA-FR-12, DA-FR-13)', () => {
  const table = (
    <table>
      <tbody>
        <tr>
          <td>Career</td>
          <td>63%</td>
        </tr>
      </tbody>
    </table>
  );

  it('toggles between the chart and an accessible table', async () => {
    renderPage(
      <ChartCard title="Vision Area progress" caption="Lowest first" table={table}>
        <div>chart body</div>
      </ChartCard>,
    );
    expect(screen.getByRole('region', { name: 'Vision Area progress' })).toBeInTheDocument();
    expect(screen.getByText('chart body')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'View as table' }));
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.queryByText('chart body')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View as chart' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('fails on its own with a retry, without hiding the title', async () => {
    const onRetry = vi.fn();
    renderPage(<ChartCard title="Progress trend" state="error" errorCode="DASH-504" onRetry={onRetry} table={table} />);
    expect(screen.getByRole('alert')).toHaveTextContent("We couldn't load this part of your dashboard.");
    expect(screen.queryByRole('button', { name: 'View as table' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('shows friendly copy when empty and marks itself busy while loading', () => {
    const { unmount } = renderPage(<ChartCard title="Review habit" state="empty" emptyMessage="Your first review takes 5 minutes." />);
    expect(screen.getByText('Your first review takes 5 minutes.')).toBeInTheDocument();
    unmount();
    renderPage(<ChartCard title="Review habit" state="loading" />);
    expect(screen.getByRole('region', { name: 'Review habit' })).toHaveAttribute('aria-busy', 'true');
  });
});

describe('ProgressRing (BRD 25 §7.2)', () => {
  it('names the proportion for screen readers and clamps it', () => {
    renderPage(<ProgressRing value={140} label="Overall progress" />);
    expect(screen.getByRole('img', { name: 'Overall progress: 100%' })).toBeInTheDocument();
  });

  it('draws the arc to the value', () => {
    renderPage(<ProgressRing value={25} label="Overall progress" thickness={10} />);
    const fill = screen.getByTestId('ring-fill');
    const circumference = Number(fill.getAttribute('stroke-dasharray'));
    expect(Number(fill.getAttribute('stroke-dashoffset'))).toBeCloseTo(circumference * 0.75, 3);
  });
});

describe('StackedStatusBar (BRD 25 §7.2)', () => {
  const counts = { NOT_STARTED: 4, IN_PROGRESS: 6, WAITING: 2, BLOCKED: 1, PAUSED: 0, COMPLETED: 5 };

  it('orders segments with Completed last and skips empty statuses', () => {
    const { container } = renderPage(<StackedStatusBar label="Goals" counts={counts} />);
    const segments = [...container.querySelectorAll('[data-status]')].map((node) => node.getAttribute('data-status'));
    expect(segments).toEqual(['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'COMPLETED']);
  });

  it('summarises every status for screen readers, including zeros', () => {
    renderPage(<StackedStatusBar label="Goals" counts={counts} />);
    expect(screen.getByRole('img')).toHaveAccessibleName('Goals: 18. Not started 4, In progress 6, Waiting 2, Blocked 1, Paused 0, Completed 5');
  });

  it('lists the counts under the bar with their labels', () => {
    renderPage(<StackedStatusBar label="Tasks" counts={counts} />);
    const list = screen.getByRole('list');
    expect(within(list).getAllByRole('listitem')).toHaveLength(5);
    expect(within(list).getByText('In progress').closest('li')).toHaveTextContent('6');
  });

  it('stripes Paused so it never merges with Not started', () => {
    const { container } = renderPage(<StackedStatusBar label="Tasks" counts={{ NOT_STARTED: 3, PAUSED: 2 }} />);
    const paused = container.querySelector('[data-status="PAUSED"]') as HTMLElement;
    expect(getComputedStyle(paused).backgroundImage).toContain('repeating-linear-gradient');
  });
});

describe('RankedProgressList (BRD 25 §7.2)', () => {
  const items = [
    { id: 1, label: 'Career', value: 63, target: 58, to: '/vision-areas/1' },
    { id: 2, label: 'Finance', value: 24, target: 35, caption: '11 pts behind plan' },
    { id: 3, label: 'Health', value: 41, target: 50 },
    { id: 4, label: 'Family', value: 78 },
  ];

  it('ranks the lowest first, so the top row needs attention', () => {
    renderPage(<RankedProgressList items={items} label="Vision Area progress" />);
    const rows = within(screen.getByRole('list', { name: 'Vision Area progress' })).getAllByRole('listitem');
    expect(rows.map((row) => row.textContent?.match(/[A-Z][a-z]+/)?.[0])).toEqual(['Finance', 'Health', 'Career', 'Family']);
  });

  it('draws a target tick only where a target exists, and links rows that have a page', () => {
    renderPage(<RankedProgressList items={items} label="Vision Area progress" />);
    expect(screen.getAllByTestId('target-tick')).toHaveLength(3);
    expect(screen.getByRole('img', { name: 'Finance 24%, Target 35%' })).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/vision-areas/1');
  });

  it('shows the lowest few with a "Show all" button', async () => {
    renderPage(<RankedProgressList items={items} label="Areas" limit={3} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    await userEvent.click(screen.getByRole('button', { name: 'Show all' }));
    expect(screen.getAllByRole('listitem')).toHaveLength(4);
    expect(screen.getByRole('button', { name: 'Show fewer' })).toHaveAttribute('aria-expanded', 'true');
  });
});

describe('NextUpList (DA-FR-02)', () => {
  const today = '2026-11-23';
  const tasks: NextUpTask[] = [
    { id: 1, title: 'Save 15 articles', priority: 'HIGH', dueDate: '2026-11-24', status: 'NOT_STARTED' },
    { id: 2, title: 'Draft methodology', priority: 'HIGH', dueDate: today, status: 'IN_PROGRESS', to: '/tasks/2' },
    { id: 3, title: 'Submit ethics form', priority: 'CRITICAL', dueDate: '2026-11-19', status: 'BLOCKED', blockerReason: 'Waiting for the faculty form' },
    { id: 4, title: 'Book runs', priority: 'MEDIUM', dueDate: '2026-11-27', status: 'NOT_STARTED' },
    { id: 5, title: 'Set savings transfer', priority: 'CRITICAL', dueDate: '2026-11-25', status: 'NOT_STARTED' },
    { id: 6, title: 'Already done', priority: 'CRITICAL', dueDate: '2026-11-01', status: 'COMPLETED' },
    { id: 7, title: 'Email mentor', priority: 'LOW', dueDate: '2026-12-15', status: 'WAITING' },
  ];

  it('orders overdue, then today, then by priority and due date, leaving out completed work', () => {
    expect(sortNextUp(tasks, today).map((task) => task.id)).toEqual([3, 2, 5, 1, 4, 7]);
  });

  it('labels due dates relative to today within a week', () => {
    expect(dueLabel('2026-11-19', today)).toEqual({ text: '4 days late', tone: 'late' });
    expect(dueLabel('2026-11-22', today)).toEqual({ text: '1 day late', tone: 'late' });
    expect(dueLabel(today, today)).toEqual({ text: 'Today', tone: 'today' });
    expect(dueLabel('2026-11-24', today)).toEqual({ text: 'Tomorrow', tone: 'soon' });
    expect(dueLabel('2026-11-27', today)).toEqual({ text: 'Fri', tone: 'soon' });
    expect(dueLabel('2026-12-15', today).tone).toBe('later');
    expect(dueLabel(null, today)).toEqual({ text: '', tone: 'none' });
  });

  it('shows at most five, with the blocker status and reason', () => {
    renderPage(<NextUpList tasks={tasks} today={today} onComplete={vi.fn()} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(5);
    const blocked = screen.getByText('Submit ethics form').closest('li') as HTMLElement;
    expect(within(blocked).getByText('Blocked')).toBeInTheDocument();
    expect(within(blocked).getByText('Waiting for the faculty form')).toBeInTheDocument();
    expect(within(blocked).getByText('4 days late')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Draft methodology' })).toHaveAttribute('href', '/tasks/2');
  });

  it('completes a task from its checkbox', async () => {
    const onComplete = vi.fn();
    renderPage(<NextUpList tasks={tasks} today={today} onComplete={onComplete} />);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Mark Draft methodology as done' }));
    expect(onComplete).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
  });

  it('has no action controls in the coach view (D-25-10)', () => {
    renderPage(<NextUpList tasks={tasks} today={today} onComplete={vi.fn()} readOnly />);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('says so when there is nothing to do', () => {
    renderPage(<NextUpList tasks={[]} today={today} emptyMessage="Nothing due today." />);
    expect(screen.getByText('Nothing due today.')).toBeInTheDocument();
  });
});

describe('useChartTheme (BRD 25 §7.1)', () => {
  it('falls back to the token values when the stylesheet is absent', () => {
    const { result } = renderHook(() => useChartTheme());
    expect(result.current.categorical).toEqual([...chartCategorical.light]);
    expect(result.current.grid).toMatchObject({ strokeDasharray: '2 4', vertical: false });
    expect(result.current.axis).toMatchObject({ axisLine: false, tickLine: false });
    expect(result.current.status('BLOCKED')).toBe('var(--st-blocked)');
  });

  it('turns chart animation off under reduced motion', () => {
    const { result } = renderHook(() => useChartTheme());
    expect(result.current.animation).toEqual({ isAnimationActive: false });
  });

  it('animates once, for 600ms, when motion is allowed', () => {
    preferReducedMotion(false);
    const { result } = renderHook(() => useChartTheme());
    expect(result.current.animation).toMatchObject({ isAnimationActive: true, animationDuration: 600 });
  });
});

/** DA-NFR-07: colour comes from tokens, never from hex in V25 dashboard components. */
describe('no hex colours in V25 dashboard components (DA-NFR-07)', () => {
  const files = [
    'components/dashboard/surface.ts',
    'components/dashboard/ZoneHeader.tsx',
    'components/dashboard/KpiCard.tsx',
    'components/dashboard/ChartCard.tsx',
    'components/dashboard/ProgressRing.tsx',
    'components/dashboard/StackedStatusBar.tsx',
    'components/dashboard/RankedProgressList.tsx',
    'components/dashboard/NextUpList.tsx',
    'components/dashboard/charts/chartTheme.tsx',
    'components/common/StatusChip.tsx',
  ];

  it.each(files)('%s', (file) => {
    const source = readFileSync(resolve(process.cwd(), 'src', file), 'utf8');
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i);
  });
});
