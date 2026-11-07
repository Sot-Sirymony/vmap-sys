import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentType } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiDailyApi from '../api/aiDailyApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as idealPartnerProfileApi from '../api/idealPartnerProfileApi';
import * as obstacleApi from '../api/obstacleApi';
import * as partnerApi from '../api/partnerApi';
import * as reviewApi from '../api/reviewApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { DreamsPage } from './DreamsPage';
import { GoalsPage } from './GoalsPage';
import { ObstaclesPage } from './ObstaclesPage';
import { ReviewsPage } from './ReviewsPage';
import { TasksBoardPage } from './TasksBoardPage';
import { VisionAreasPage } from './VisionAreasPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { fullName: 'Tester', role: 'USER' } }) }));
vi.mock('../context/ToastContext', () => ({ useToast: () => ({ showToast: vi.fn(), show: vi.fn() }) }));
vi.mock('../api/aiDailyApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/idealPartnerProfileApi');
vi.mock('../api/obstacleApi');
vi.mock('../api/partnerApi');
vi.mock('../api/reviewApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const modules: Record<string, unknown>[] = [aiDailyApi, dreamApi, goalApi, idealPartnerProfileApi, obstacleApi, partnerApi,
  reviewApi, stepApi, taskApi, visionAreaApi];

/** Every list call answers an empty list, unless a test gives it items. */
function emptyLists() {
  for (const module of modules) {
    for (const [name, value] of Object.entries(module)) {
      if (name.startsWith('list') && vi.isMockFunction(value)) {
        value.mockResolvedValue([]);
      }
    }
  }
  // Partners come back a page at a time.
  vi.mocked(partnerApi.listPartners).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 } as never);
}

/** Three items ranked C, A and C, plus one without a rank. */
function ranked<T>(base: T) {
  return ['C', 'A', null, 'C', 'B'].map((letterRank, index) => ({ ...base, id: index + 1, letterRank, title: `Item ${index + 1}`,
    name: `Item ${index + 1}` }));
}

const cases: [string, ComponentType, () => void][] = [
  ['Vision Areas', VisionAreasPage, () => vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(ranked({ status: 'ACTIVE', priority: 'HIGH' }) as never)],
  ['Dreams', DreamsPage, () => vi.mocked(dreamApi.listDreams).mockResolvedValue(ranked({ status: 'ACTIVE', priority: 'HIGH', visionAreaId: 1 }) as never)],
  ['Goals', GoalsPage, () => vi.mocked(goalApi.listGoals).mockResolvedValue(ranked({ status: 'IN_PROGRESS', priority: 'HIGH', dreamId: 1 }) as never)],
  ['Tasks', TasksBoardPage, () => vi.mocked(taskApi.listTasks).mockResolvedValue(ranked({ status: 'NOT_STARTED', priority: 'HIGH', stepId: 1, owner: 'Me' }) as never)],
  ['Obstacles', ObstaclesPage, () => vi.mocked(obstacleApi.listObstacles).mockResolvedValue(ranked({ status: 'OPEN', severity: 'HIGH', obstacleType: 'TIME' }) as never)],
  ['Reviews', ReviewsPage, () => vi.mocked(reviewApi.listReviews).mockResolvedValue(ranked({ reviewType: 'WEEKLY', reviewDate: '2026-11-01' }) as never)],
];

describe('letter rank filters', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    emptyLists();
  });

  it.each(cases)('%s page offers each rank in use once, in order', async (_name, Page, withItems) => {
    withItems();
    render(<MemoryRouter><Page /></MemoryRouter>);

    const filter = (await screen.findByText('Rank', { selector: 'label' })) as HTMLElement;
    await userEvent.click(within(filter).getByRole('combobox'));
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(options).toEqual(['A', 'B', 'C']);
  }, 30_000);
});
