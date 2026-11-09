import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as communicationApi from '../api/communicationApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as planApi from '../api/planApi';
import * as reviewApi from '../api/reviewApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { VisionTimelinePage } from './VisionTimelinePage';

vi.mock('../api/communicationApi');
vi.mock('../api/dreamApi');
vi.mock('../api/planApi');
vi.mock('../api/reviewApi');
vi.mock('../api/goalApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

function renderTimeline(route = '/vision-map/timeline') {
  return renderPage(<VisionTimelinePage />, { route, path: '/vision-map/timeline' });
}

describe('VisionTimelinePage (BRD 16.0.0: the full timeline)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([
      aVisionArea({ id: 1, name: 'Career', priority: 'HIGH' }),
      aVisionArea({ id: 2, name: 'Health', priority: 'MEDIUM' }),
    ]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([
      aDream({ id: 1, code: 'D-001', visionAreaId: 1, title: 'Become a researcher', createdAt: '2026-10-01T00:00:00Z' }),
      aDream({ id: 2, code: 'D-002', visionAreaId: 2, title: 'Run a marathon', createdAt: '2026-10-01T00:00:00Z' }),
    ]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 10, dreamId: 1, title: 'Prepare one concept note' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 20, goalId: 10, title: 'Search literature' })]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([aTask({ id: 30, stepId: 20, title: 'Search PubMed' })]);
    vi.mocked(planApi.listMilestones).mockImplementation(async (_token, dreamId) => (dreamId === 1
      ? [{ id: 1, dreamId: 1, title: 'First draft done', achievedOn: '2026-12-01' } as never]
      : []));
    vi.mocked(reviewApi.listReviews).mockResolvedValue([
      { id: 5, reviewType: 'WEEKLY', reviewDate: '2026-11-20T09:00:00', relatedDreamId: 1, archived: false } as never,
    ]);
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue({
      content: [{ id: 7, relatedTaskId: 30, subject: 'Ask for data', status: 'SENT', followUpDate: '2026-11-25', archived: false } as never],
      totalElements: 1, totalPages: 1, number: 0, size: 500, first: true, last: true,
    } as never);
  });

  it('shows every dream folded to one bar under its vision area (TL-FR-32 to TL-FR-34)', async () => {
    renderTimeline();
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));
    const headings = timeline.getAllByRole('heading').map((heading) => heading.textContent);
    expect(headings).toEqual(['Career', 'Health']);
    expect(timeline.getByRole('button', { name: /^Dream D-001 Become a researcher/ })).toBeInTheDocument();
    expect(timeline.getByRole('button', { name: /^Dream D-002 Run a marathon/ })).toBeInTheDocument();
    expect(timeline.queryByRole('button', { name: /^Goal G-001/ })).not.toBeInTheDocument();
  });

  it('opens focused on the dream it was opened from', async () => {
    renderTimeline('/vision-map/timeline?focus=1');
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));
    expect(timeline.getByRole('button', { name: /^Goal G-001 Prepare one concept note/ })).toBeInTheDocument();
    expect(timeline.getByRole('button', { name: /^Step S-001 Search literature/ })).toBeInTheDocument();
  });

  it('keeps zoom, expanded rows and filters in the link (TL-FR-36)', async () => {
    renderTimeline();
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));

    await userEvent.click(screen.getByRole('button', { name: 'Quarter' }));
    expect(currentLocation()).toContain('zoom=quarter');

    await userEvent.click(timeline.getByRole('button', { name: 'Expand Become a researcher' }));
    expect(currentLocation()).toContain('open=dream-1');

    await userEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(currentLocation()).toContain('open=all');
    expect(timeline.getByRole('button', { name: /^Task T-001 Search PubMed/ })).toBeInTheDocument();
  });

  it('reopens the view a link describes', async () => {
    renderTimeline('/vision-map/timeline?visionAreaId=2&open=all');
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));
    expect(timeline.getByRole('button', { name: /^Dream D-002 Run a marathon/ })).toBeInTheDocument();
    expect(timeline.queryByRole('button', { name: /^Dream D-001/ })).not.toBeInTheDocument();
  });

  it('opens a bar on its dream map with the record to edit (TL-FR-27)', async () => {
    renderTimeline('/vision-map/timeline?open=all');
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));
    await userEvent.click(timeline.getByRole('button', { name: /^Step S-001 Search literature/ }));
    expect(currentLocation()).toBe('/dreams/1?view=timeline&edit=step-20');
  });

  it('offers to create a dream when there are none (TL-FR-06)', async () => {
    vi.mocked(dreamApi.listDreams).mockResolvedValue([]);
    renderTimeline();
    expect(await screen.findByText('No dreams on the map yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create your first dream' })).toBeInTheDocument();
  });

  it('marks milestones, reviews and follow-ups on the dream, and can hide each kind (TL-FR-13)', async () => {
    renderTimeline();
    const timeline = within(await screen.findByRole('region', { name: 'Timeline of all dreams' }));
    expect(await timeline.findByRole('img', { name: /^Milestone on 1 Dec 2026: First draft done/ })).toBeInTheDocument();
    expect(timeline.getByRole('img', { name: /^Weekly review on 20 Nov 2026/ })).toBeInTheDocument();
    expect(timeline.getByRole('img', { name: /^Follow up on 25 Nov 2026: Ask for data/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Reviews' }));
    expect(currentLocation()).toContain('hide=review');
    expect(timeline.queryByRole('img', { name: /review on/ })).not.toBeInTheDocument();
    expect(timeline.getByRole('img', { name: /^Milestone on/ })).toBeInTheDocument();
  });

  it('explains the colours and marks in a legend (TL-FR-22)', async () => {
    renderTimeline();
    await screen.findByRole('region', { name: 'Timeline of all dreams' });
    await userEvent.click(screen.getByRole('button', { name: 'Legend' }));
    expect(screen.getByText('Dashed end: date worked out, not entered')).toBeInTheDocument();
    expect(screen.getByText('Red end: overdue')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Hide legend' }));
    await waitFor(() => expect(screen.queryByText('Red end: overdue')).not.toBeInTheDocument());
  });

  it('shows how busy each period is in the long zooms (TL-FR-35)', async () => {
    renderTimeline('/vision-map/timeline?open=all');
    await screen.findByRole('region', { name: 'Timeline of all dreams' });
    expect(screen.queryByText('Busy (unfinished work)')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Quarter' }));
    expect(screen.getByText('Busy (unfinished work)')).toBeInTheDocument();
  });

  it('prints the timeline (TL-FR-38)', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    renderTimeline();
    await screen.findByRole('region', { name: 'Timeline of all dreams' });
    await userEvent.click(screen.getByRole('button', { name: 'Print' }));
    expect(print).toHaveBeenCalled();
    print.mockRestore();
  });
});

