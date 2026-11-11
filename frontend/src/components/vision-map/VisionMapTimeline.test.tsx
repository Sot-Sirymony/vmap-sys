import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { aDream, aGoal, aStep, aTask } from '../../test/builders';
import { renderPage } from '../../test/render';
import { toDay } from '../../utils/timeline';
import { TimelineView, VisionMapTimeline } from './VisionMapTimeline';
import { buildDreamTimeline } from '../../utils/timeline';

const TODAY = toDay('2026-11-15');
const dream = aDream({ id: 1, code: 'D-001', title: 'Become a researcher', commitmentStartDate: '2026-10-01', targetDate: '2027-06-30' });
const goals = [aGoal({ id: 10, dreamId: 1, code: 'G-001', title: 'Prepare a concept note', targetDate: '2027-03-31' })];
const steps = [aStep({ id: 20, goalId: 10, code: 'S-001', title: 'Search literature', targetDate: '2027-01-31' })];
const tasks = [
  aTask({ id: 30, stepId: 20, code: 'T-001', title: 'Search PubMed', startDate: '2026-10-05', dueDate: '2026-10-20', status: 'COMPLETED', progressPercent: 100 }),
  aTask({ id: 31, stepId: 20, code: 'T-002', title: 'Summarise findings', dueDate: '2026-12-10' }),
];

function renderTimeline(onOpen = vi.fn()) {
  renderPage(<VisionMapTimeline dream={dream} goals={goals} steps={steps} tasks={tasks} onOpen={onOpen} today={TODAY} />);
  return onOpen;
}

function asPhone() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('max-width:600px'), media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
  }));
}

describe('VisionMapTimeline (BRD 16.0.0 phase 4)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('moves between rows with the keyboard and folds with left and right (TL-FR-29)', async () => {
    const onOpen = renderTimeline();
    const region = within(screen.getByRole('region', { name: 'Timeline of Become a researcher' }));
    const bar = (name: RegExp) => region.getByRole('button', { name });

    bar(/^Dream D-001/).focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(bar(/^Goal G-001/)).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(bar(/^Step S-001/)).toHaveFocus();

    // The step's tasks start folded; right unfolds them, down moves into them.
    expect(region.queryByRole('button', { name: /^Task T-001/ })).not.toBeInTheDocument();
    await userEvent.keyboard('{ArrowRight}');
    await userEvent.keyboard('{ArrowDown}');
    expect(bar(/^Task T-001/)).toHaveFocus();

    // Left on a task goes back to its step; left again folds the step.
    await userEvent.keyboard('{ArrowLeft}');
    expect(bar(/^Step S-001/)).toHaveFocus();
    await userEvent.keyboard('{ArrowLeft}');
    expect(region.queryByRole('button', { name: /^Task T-001/ })).not.toBeInTheDocument();

    await userEvent.keyboard('{Home}');
    expect(bar(/^Dream D-001/)).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(bar(/^Step S-001/)).toHaveFocus();

    await userEvent.keyboard('{Enter}');
    expect(onOpen).toHaveBeenCalledWith('step', 20);
  });

  it('lists every row as a table, with worked-out dates marked (TL-FR-47)', async () => {
    const onOpen = renderTimeline();
    await userEvent.click(screen.getByRole('button', { name: 'Show as table' }));

    const table = within(screen.getByRole('table', { name: 'Timeline of Become a researcher' }));
    const rows = table.getAllByRole('row').slice(1);
    expect(rows.map((row) => within(row).getAllByRole('cell')[1].textContent)).toEqual(['D-001', 'G-001', 'S-001', 'T-001', 'T-002']);
    const summarise = rows[4];
    expect(within(summarise).getByText('10 Dec 2026 (worked out)')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Timeline of Become a researcher' })).not.toBeInTheDocument();

    await userEvent.click(table.getByRole('button', { name: 'Search literature' }));
    expect(onOpen).toHaveBeenCalledWith('step', 20);

    await userEvent.click(screen.getByRole('button', { name: 'Show as calendar' }));
    expect(screen.getByRole('region', { name: 'Timeline of Become a researcher' })).toBeInTheDocument();
  });

  it('shows a phone a month-by-month agenda that opens on this month (TL-FR-44)', async () => {
    asPhone();
    const onOpen = renderTimeline();

    expect(screen.queryByRole('region', { name: 'Timeline of Become a researcher' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Week' })).not.toBeInTheDocument();
    const december = within(screen.getByRole('region', { name: 'December 2026' }));
    expect(december.getByRole('button', { name: 'Due: Task T-002 · Summarise findings' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'October 2026' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Show earlier months (1)' }));
    const october = within(screen.getByRole('region', { name: 'October 2026' }));
    expect(october.getByRole('button', { name: 'Starts: Task T-001 · Search PubMed' })).toBeInTheDocument();

    await userEvent.click(december.getByRole('button', { name: 'Due: Task T-002 · Summarise findings' }));
    expect(onOpen).toHaveBeenCalledWith('task', 31);
  });

  describe('phase 5: changing dates and adding from the timeline', () => {
    function renderEditable(onReschedule = vi.fn().mockResolvedValue(undefined), onQuickAdd = vi.fn()) {
      renderPage(
        <VisionMapTimeline dream={dream} goals={goals} steps={steps} tasks={tasks} onOpen={vi.fn()} today={TODAY}
          onReschedule={onReschedule} onQuickAdd={onQuickAdd} />,
      );
      return { onReschedule, onQuickAdd, region: within(screen.getByRole('region', { name: 'Timeline of Become a researcher' })) };
    }

    it('offers Edit dates only where dates can be saved, and keeps it off at first (TL-FR-42)', () => {
      const { unmount } = renderPage(<VisionMapTimeline dream={dream} goals={goals} steps={steps} tasks={tasks} onOpen={vi.fn()} today={TODAY} />);
      expect(screen.queryByRole('switch', { name: 'Edit dates' })).not.toBeInTheDocument();
      unmount();
      renderEditable();
      expect(screen.getByRole('switch', { name: 'Edit dates' })).not.toBeChecked();
    });

    it('moves a task a day at a time with Shift and the arrows, then saves on Enter (TL-FR-43)', async () => {
      const { onReschedule, region } = renderEditable();
      await userEvent.click(screen.getByRole('switch', { name: 'Edit dates' }));
      await userEvent.click(region.getByRole('button', { name: 'Expand Search literature' }));
      const bar = region.getByRole('button', { name: /^Task T-002 Summarise findings/ });
      bar.focus();

      await userEvent.keyboard('{Shift>}{ArrowRight}{ArrowRight}{/Shift}');
      expect(bar).toHaveAccessibleName(/moving to 12 Dec 2026 – 12 Dec 2026, Enter saves/);
      await userEvent.keyboard('{Enter}');
      // T-002 has no start date of its own, so it keeps none; only the due date moves.
      await waitFor(() => expect(onReschedule).toHaveBeenCalledWith({ kind: 'task', id: 31, startDate: null, dueDate: '2026-12-12' }));

      await userEvent.keyboard('{Shift>}{ArrowLeft}{/Shift}{Escape}');
      expect(onReschedule).toHaveBeenCalledTimes(1);
    });

    it('drags the end of a step to change its target date (TL-FR-39)', async () => {
      const { onReschedule, region } = renderEditable();
      await userEvent.click(screen.getByRole('switch', { name: 'Edit dates' }));
      const bar = region.getByRole('button', { name: /^Step S-001 Search literature/ });
      const handle = bar.querySelector('[data-handle="end"]') as HTMLElement;
      // jsdom has no PointerEvent; a MouseEvent with a pointer id is enough here.
      vi.stubGlobal('PointerEvent', class extends MouseEvent {
        pointerId: number;
        constructor(type: string, init: PointerEventInit = {}) {
          super(type, init);
          this.pointerId = init.pointerId ?? 1;
        }
      });
      // Month zoom draws 6 px a day, so 60 px is 10 days later.
      fireEvent.pointerDown(handle, { button: 0, clientX: 100, pointerId: 1 });
      fireEvent.pointerMove(bar, { clientX: 160, pointerId: 1 });
      fireEvent.pointerUp(bar, { clientX: 160, pointerId: 1 });
      await waitFor(() => expect(onReschedule).toHaveBeenCalledWith({ kind: 'step', id: 20, targetDate: '2027-02-10' }));
    });

    it('puts a refused change back and says why (TL-FR-40)', async () => {
      const refuse = vi.fn().mockRejectedValue(new Error('The step must end on or before its goal (31 Mar 2027).'));
      const { region } = renderEditable(refuse);
      await userEvent.click(screen.getByRole('switch', { name: 'Edit dates' }));
      const bar = region.getByRole('button', { name: /^Step S-001 Search literature/ });
      bar.focus();
      await userEvent.keyboard('{Shift>}{ArrowRight}{/Shift}{Enter}');
      expect(await screen.findByRole('alert')).toHaveTextContent('The step must end on or before its goal (31 Mar 2027).');
      expect(bar).toHaveAccessibleName(/^Step S-001 Search literature, 5 Oct 2026 – 31 Jan 2027/);
    });

    it('offers to add a child on dream, goal and step rows (TL-FR-31)', async () => {
      const { onQuickAdd, region } = renderEditable();
      await userEvent.click(region.getByRole('button', { name: 'Add a step to Prepare a concept note' }));
      expect(onQuickAdd).toHaveBeenCalledWith(expect.objectContaining({ kind: 'goal', id: 10, dreamId: 1 }));
      expect(region.getByRole('button', { name: 'Add a goal to Become a researcher' })).toBeInTheDocument();
      expect(region.getByRole('button', { name: 'Add a task to Search literature' })).toBeInTheDocument();
    });

    it('shows a view-only timeline with nothing to press (TL-FR-48)', () => {
      const timeline = buildDreamTimeline(dream, goals, steps, tasks, TODAY);
      renderPage(<TimelineView timeline={timeline} label="Shared" initialExpanded={new Set(['dream-1'])} onOpen={vi.fn()} readOnly today={TODAY} />);
      const region = within(screen.getByRole('region', { name: 'Shared' }));
      expect(region.getByRole('img', { name: /^Goal G-001 Prepare a concept note/ })).toBeInTheDocument();
      expect(region.queryByRole('button', { name: /^Goal G-001/ })).not.toBeInTheDocument();
      expect(region.queryByRole('button', { name: 'Prepare a concept note' })).not.toBeInTheDocument();
      expect(screen.queryByRole('switch', { name: 'Edit dates' })).not.toBeInTheDocument();
    });
  });

  describe('row actions: add, edit, archive and restore', () => {
    function renderWithActions(rowTasks = tasks) {
      const handlers = { onOpen: vi.fn(), onQuickAdd: vi.fn(), onArchive: vi.fn(), onRestore: vi.fn(), onAddDream: vi.fn() };
      renderPage(<VisionMapTimeline dream={dream} goals={goals} steps={steps} tasks={rowTasks} today={TODAY} {...handlers} />);
      return { ...handlers, region: within(screen.getByRole('region', { name: 'Timeline of Become a researcher' })) };
    }

    it('offers Add, Edit and Archive on every live level, and no Add on a task', async () => {
      const { onQuickAdd, onOpen, onArchive, region } = renderWithActions();

      await userEvent.click(region.getByRole('button', { name: 'Actions for Become a researcher' }));
      await userEvent.click(screen.getByRole('menuitem', { name: 'Add goal' }));
      expect(onQuickAdd).toHaveBeenCalledWith(expect.objectContaining({ kind: 'dream', id: 1, title: 'Become a researcher' }));

      await userEvent.click(region.getByRole('button', { name: 'Actions for Prepare a concept note' }));
      await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
      expect(onOpen).toHaveBeenCalledWith('goal', 10);

      await userEvent.click(region.getByRole('button', { name: 'Actions for Search literature' }));
      await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
      expect(onArchive).toHaveBeenCalledWith(expect.objectContaining({ kind: 'step', id: 20 }));

      await userEvent.click(region.getByRole('button', { name: 'Expand Search literature' }));
      await userEvent.click(region.getByRole('button', { name: 'Actions for Summarise findings' }));
      expect(screen.queryByRole('menuitem', { name: /^Add/ })).not.toBeInTheDocument();
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument();
    });

    it('offers only Restore on an archived row', async () => {
      const archivedTasks = [tasks[0], { ...tasks[1], archived: true }];
      const { onRestore, region } = renderWithActions(archivedTasks);
      await userEvent.click(region.getByRole('button', { name: 'Expand Search literature' }));
      await userEvent.click(region.getByRole('button', { name: 'Actions for Summarise findings' }));
      expect(screen.queryByRole('menuitem', { name: 'Edit' })).not.toBeInTheDocument();
      await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
      expect(onRestore).toHaveBeenCalledWith(expect.objectContaining({ kind: 'task', id: 31 }));
    });

    it('offers New dream in the toolbar', async () => {
      const { onAddDream } = renderWithActions();
      await userEvent.click(screen.getByRole('button', { name: 'New dream' }));
      expect(onAddDream).toHaveBeenCalled();
    });
  });

  describe('full-width calendar across zoom levels', () => {
    const shortDream = aDream({
      id: 2,
      code: 'D-002',
      title: 'One-month sprint',
      commitmentStartDate: '2026-11-01',
      targetDate: '2026-11-30',
    });
    const shortGoal = aGoal({ id: 11, dreamId: 2, code: 'G-002', title: 'Ship draft', targetDate: '2026-11-30' });

    function mockTimelineWidth(width = 1200) {
      vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function (this: HTMLElement) {
        return this.classList?.contains('vm-timeline__scroll') ? width : 0;
      });
    }

    afterEach(() => {
      vi.restoreAllMocks();
      localStorage.removeItem('vision-map:timeline-label-width');
    });

    it('extends month, quarter and year calendars to fill the full container width', async () => {
      mockTimelineWidth(1200);
      const shortStep = aStep({ id: 21, goalId: 11, code: 'S-002', title: 'Outline', targetDate: '2026-11-20' });
      const shortTask = aTask({ id: 32, stepId: 21, code: 'T-003', title: 'Write bullets', startDate: '2026-11-10', dueDate: '2026-11-20' });

      renderPage(<VisionMapTimeline dream={shortDream} goals={[shortGoal]} steps={[shortStep]} tasks={[shortTask]} onOpen={vi.fn()} today={TODAY} />);
      const region = screen.getByRole('region', { name: 'Timeline of One-month sprint' });
      const canvas = region.firstElementChild as HTMLElement;

      // Month zoom (6 px/day): extends past Dec 14 to fill at least 1200 px and completes February 2027.
      expect(Number.parseFloat(getComputedStyle(canvas).width)).toBeGreaterThanOrEqual(1200);
      expect(within(region).getByText('October 2026')).toBeInTheDocument();
      expect(within(region).getByText('February 2027')).toBeInTheDocument();
      const februaryCell = within(region).getByText('February 2027');
      expect(februaryCell.style.width).toBe(`${28 * 6}px`);
      expect(within(region).getByText('One-month sprint · 0%')).toBeInTheDocument();
      expect(within(region).getAllByText('Ship draft')).toHaveLength(2);

      // Quarter and Year zoom also fill at least 1200 px of the scroll viewport.
      await userEvent.click(screen.getByRole('button', { name: 'Quarter' }));
      expect(Number.parseFloat(getComputedStyle(canvas).width)).toBeGreaterThanOrEqual(1200);
      expect(within(region).getByText('Q4 2026')).toBeInTheDocument();
      expect(within(region).getByText('Q1 2027')).toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: 'Year' }));
      expect(Number.parseFloat(getComputedStyle(canvas).width)).toBeGreaterThanOrEqual(1200);
      expect(within(region).getByText('2026')).toBeInTheDocument();
      expect(within(region).getAllByText('Q4').length).toBeGreaterThanOrEqual(1);
    });

    it('keeps a short schedule centred instead of jumping into trailing empty months when switching to Week', async () => {
      mockTimelineWidth(1200);
      renderPage(<VisionMapTimeline dream={shortDream} goals={[shortGoal]} steps={[]} tasks={[]} onOpen={vi.fn()} today={TODAY} />);
      const region = screen.getByRole('region', { name: 'Timeline of One-month sprint' });

      await userEvent.click(screen.getByRole('button', { name: 'Week' }));
      // Today is Nov 15 (45 days from Oct 1); at 34 px/day and visibleTrack 900 px, scrollLeft centres on Nov 15.
      expect(region.scrollLeft).toBe(45 * 34 - 900 / 2);
      // Week view shades weekends and steps left/right via period navigation buttons.
      expect(region.querySelectorAll('.vm-timeline__grid-tick--weekend').length).toBeGreaterThan(0);

      const before = region.scrollLeft;
      await userEvent.click(screen.getByRole('button', { name: 'Next period' }));
      expect(region.scrollLeft).toBeGreaterThan(before);
      await userEvent.click(screen.getByRole('button', { name: 'Previous period' }));
      expect(region.scrollLeft).toBe(before);
    });

    it('lets the user drag or arrow-resize the middle divider to see full row titles', async () => {
      localStorage.removeItem('vision-map:timeline-label-width');
      mockTimelineWidth(1200);
      vi.stubGlobal('PointerEvent', class extends MouseEvent {
        pointerId: number;
        constructor(type: string, init: PointerEventInit = {}) {
          super(type, init);
          this.pointerId = init.pointerId ?? 1;
        }
      });
      renderPage(<VisionMapTimeline dream={shortDream} goals={[shortGoal]} steps={[]} tasks={[]} onOpen={vi.fn()} today={TODAY} />);
      const region = screen.getByRole('region', { name: 'Timeline of One-month sprint' });
      const resizer = within(region).getByRole('button', { name: 'Resize title column' });
      const stickyFrame = resizer.parentElement as HTMLElement;
      expect(Number.parseFloat(getComputedStyle(stickyFrame).width)).toBe(300);

      // Drag right by 180 px to reveal longer titles.
      fireEvent.pointerDown(resizer, { button: 0, clientX: 300 });
      fireEvent.pointerMove(window, { clientX: 480 });
      fireEvent.pointerUp(window);
      expect(Number.parseFloat(getComputedStyle(stickyFrame).width)).toBe(480);

      // Keyboard arrows adjust width; double-clicking resets to default 300 px.
      resizer.focus();
      await userEvent.keyboard('{ArrowLeft}');
      expect(Number.parseFloat(getComputedStyle(stickyFrame).width)).toBe(460);

      fireEvent.doubleClick(resizer);
      expect(Number.parseFloat(getComputedStyle(stickyFrame).width)).toBe(300);
    });
  });
});
