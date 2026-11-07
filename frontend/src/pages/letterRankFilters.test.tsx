import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentType } from 'react';
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
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, anObstacle, aReview, aTask, aVisionArea } from '../test/builders';
import { renderPage } from '../test/render';
import { DreamsPage } from './DreamsPage';
import { GoalsPage } from './GoalsPage';
import { ObstaclesPage } from './ObstaclesPage';
import { ReviewsPage } from './ReviewsPage';
import { TasksBoardPage } from './TasksBoardPage';
import { VisionAreasPage } from './VisionAreasPage';

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

/** Items ranked C, A, none, C and B. */
function ranked<T>(build: (overrides: { id: number; letterRank: string | null }) => T): T[] {
  return ['C', 'A', null, 'C', 'B'].map((letterRank, index) => build({ id: index + 1, letterRank }));
}

const cases: [string, ComponentType, () => void][] = [
  ['Vision Areas', VisionAreasPage, () => vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(ranked((o) => aVisionArea({ ...o, name: `Area ${o.id}` })))],
  ['Dreams', DreamsPage, () => vi.mocked(dreamApi.listDreams).mockResolvedValue(ranked((o) => aDream({ ...o, title: `Dream ${o.id}` })))],
  ['Goals', GoalsPage, () => vi.mocked(goalApi.listGoals).mockResolvedValue(ranked((o) => aGoal({ ...o, title: `Goal ${o.id}` })))],
  ['Tasks', TasksBoardPage, () => vi.mocked(taskApi.listTasks).mockResolvedValue(ranked((o) => aTask({ ...o, title: `Task ${o.id}` })))],
  ['Obstacles', ObstaclesPage, () => vi.mocked(obstacleApi.listObstacles).mockResolvedValue(ranked((o) => anObstacle({ ...o, title: `Obstacle ${o.id}` })))],
  ['Reviews', ReviewsPage, () => vi.mocked(reviewApi.listReviews).mockResolvedValue(ranked((o) => aReview(o)))],
];

describe('letter rank filters', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    answerEmptyLists(modules, ['listPartners']);
  });

  it.each(cases)('%s page offers each rank in use once, in order', async (_name, Page, withItems) => {
    withItems();
    renderPage(<Page />);

    const filter = (await screen.findByText('Rank', { selector: 'label' })) as HTMLElement;
    await userEvent.click(within(filter).getByRole('combobox'));
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(options).toEqual(['A', 'B', 'C']);
  }, 30_000);
});
