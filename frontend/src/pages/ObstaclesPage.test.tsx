import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as obstacleApi from '../api/obstacleApi';
import * as partnerApi from '../api/partnerApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import { setCurrentLanguage } from '../i18n/translate';
import { answerEmptyLists, pageOf } from '../test/apiMocks';
import { aDream, anObstacle, aPartner, aStep } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { ObstaclesPage } from './ObstaclesPage';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/obstacleApi');
vi.mock('../api/partnerApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');

const noTime = anObstacle({ id: 1, title: 'No time on weekdays', obstacleType: 'TIME', severity: 'HIGH', status: 'OPEN', relatedDreamId: 1 });
const noBudget = anObstacle({ id: 2, title: 'No budget for the survey', obstacleType: 'MONEY', severity: 'CRITICAL', status: 'IN_PROGRESS', solution: 'Apply for a grant' });
const solvedTime = anObstacle({ id: 3, title: 'Evenings taken by meetings', obstacleType: 'TIME', severity: 'LOW', status: 'RESOLVED', rootCause: 'Said yes to every meeting' });
const coauthor = anObstacle({ id: 4, title: 'Co-author missed the deadline', obstacleType: 'PARTNER', severity: 'MEDIUM', status: 'OPEN', relatedStepId: 7 });

const row = (title: string) => screen.getByText(title, { selector: 'td, td *' }).closest('tr') as HTMLElement;

async function chooseOption(dialog: HTMLElement, field: string, option: string) {
  await userEvent.click(within(dialog).getByRole('combobox', { name: field }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
}

async function openEdit(title: string) {
  await userEvent.click(within(row(title)).getByRole('button', { name: 'Obstacle actions' }));
  await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
  return screen.findByRole('dialog', { name: 'Edit Obstacle' });
}

async function openCreate() {
  await userEvent.click(screen.getByRole('button', { name: 'Create obstacle' }));
  return screen.findByRole('dialog');
}

// Long form pages: each test may take several seconds on a busy CI runner (COV-NFR-05).
describe('ObstaclesPage (CLAUDE.md data model 10)', { timeout: 30000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, obstacleApi, stepApi, taskApi]);
    vi.mocked(obstacleApi.listObstacles).mockResolvedValue([noTime, noBudget, solvedTime, coauthor]);
    vi.mocked(partnerApi.listPartners).mockResolvedValue(pageOf([aPartner({ id: 5, name: 'Dr. Mentor' })]));
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, title: 'Become a researcher' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 7, title: 'Write the paper' })]);
    vi.mocked(obstacleApi.createObstacle).mockResolvedValue(noTime);
    vi.mocked(obstacleApi.updateObstacle).mockResolvedValue(noTime);
    vi.mocked(obstacleApi.archiveObstacle).mockResolvedValue(undefined as never);
    vi.mocked(obstacleApi.listRelatedObstacles).mockResolvedValue([]);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('lists obstacles, most severe first, with the suggested partner for each', async () => {
    renderPage(<ObstaclesPage />);
    expect(await screen.findByText('No time on weekdays', { selector: 'td, td *' })).toBeInTheDocument();
    const titles = screen.getAllByRole('row').slice(1).map((tableRow) => within(tableRow).queryAllByRole('cell')[1]?.textContent);
    expect(titles).toEqual(['No budget for the survey', 'No time on weekdays', 'Co-author missed the deadline', 'Evenings taken by meetings']);
    expect(within(row('No budget for the survey')).getByText('Financial partner or sponsor')).toBeInTheDocument();
    expect(within(row('No time on weekdays')).getByText('Delegate or assistant')).toBeInTheDocument();
    // No partner type is suggested for a partner conflict.
    expect(within(row('Co-author missed the deadline')).getAllByRole('cell')[3]).toHaveTextContent(/^-$/);
    expect(within(row('No budget for the survey')).getByText('Apply for a grant')).toBeInTheDocument();
  });

  it.each([
    ['Knowledge', 'Mentor or expert'],
    ['Skill', 'Technical expert or trainer'],
    ['Time', 'Delegate or assistant'],
    ['Money', 'Financial partner or sponsor'],
    ['Motivation', 'Accountability partner'],
    ['Decision', 'Advisor'],
  ])('suggests the right support for a %s gap while the obstacle is written', async (type, suggestion) => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openCreate();
    await chooseOption(dialog, 'Type', type);
    expect(within(dialog).getByText(`Suggested support: ${suggestion}`)).toBeInTheDocument();
  });

  it('suggests no partner type for a system gap', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openCreate();
    await chooseOption(dialog, 'Type', 'System');
    expect(within(dialog).queryByText(/^Suggested support:/)).not.toBeInTheDocument();
  });

  it('creates an obstacle with its severity, rank, links and the partner it needs', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openCreate();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Cannot read Stata output');
    await chooseOption(dialog, 'Type', 'Skill');
    await chooseOption(dialog, 'Severity', 'Critical');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Rank/ }), 'b');
    await chooseOption(dialog, 'Dream', 'Become a researcher');
    await chooseOption(dialog, 'Required Partner', 'Dr. Mentor');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create obstacle' }).at(-1)!);

    await waitFor(() => expect(obstacleApi.createObstacle).toHaveBeenCalledWith('token', expect.objectContaining({
      title: 'Cannot read Stata output', obstacleType: 'SKILL', severity: 'CRITICAL', letterRank: 'B', relatedDreamId: 1, requiredPartnerId: 5, status: 'OPEN',
    })));
  });

  it('shows how a past obstacle of the same type was solved', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openCreate();
    await chooseOption(dialog, 'Type', 'Time');
    expect(within(dialog).getByText(/You've resolved 1 time obstacle\s+before/)).toBeInTheDocument();
    expect(within(dialog).getByText('Said yes to every meeting')).toBeInTheDocument();
  });

  it('resolves an obstacle with its root cause, after asking the server for related ones', async () => {
    vi.mocked(obstacleApi.listRelatedObstacles).mockResolvedValue([solvedTime]);
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openEdit('No time on weekdays');
    expect(obstacleApi.listRelatedObstacles).toHaveBeenCalledWith('token', 1);
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('No time on weekdays');
    await chooseOption(dialog, 'Status', 'Resolved');
    expect(within(dialog).getByRole('textbox', { name: /^Root Cause/ })).toBeRequired();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Root Cause/ }), 'Weekends were never protected');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(obstacleApi.updateObstacle).toHaveBeenCalledWith('token', 1, expect.objectContaining({
      status: 'RESOLVED', rootCause: 'Weekends were never protected', relatedDreamId: 1,
    })));
  });

  it('needs creative alternatives to accept an obstacle, and counts them as they are typed', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const dialog = await openEdit('No budget for the survey');
    await chooseOption(dialog, 'Status', 'Accepted');
    const alternatives = within(dialog).getByRole('textbox', { name: /^Creative Alternatives/ });
    expect(alternatives).toBeRequired();
    await userEvent.type(alternatives, 'Smaller sample{Enter}Online survey');
    expect(within(dialog).getByText(/2 of 3 listed so far/)).toBeInTheDocument();
  });

  it('offers the conflict worksheet for a partner obstacle, and turns kept criticism into a task', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('Co-author missed the deadline', { selector: 'td, td *' });
    const dialog = await openEdit('Co-author missed the deadline');
    expect(within(dialog).getByText('Conflict worksheet')).toBeInTheDocument();
    expect(within(dialog).getByText('0 of 4 answered.')).toBeInTheDocument();

    for (const question of [/attacking character/, /specific incident/, /threats, ultimatums/]) {
      await userEvent.click(within(within(dialog).getByRole('group', { name: question })).getByRole('button', { name: 'Yes' }));
    }
    await userEvent.click(within(within(dialog).getByRole('group', { name: /works for both sides/ })).getByRole('button', { name: 'No' }));
    expect(within(dialog).getByText('All four answered — Resolved is unblocked.')).toBeInTheDocument();
    await userEvent.click(within(within(dialog).getByRole('group', { name: /actually agree/ })).getByRole('button', { name: 'Unsure' }));

    const turnIntoTask = within(dialog).getByRole('button', { name: 'Turn this into a task' });
    expect(turnIntoTask).toBeDisabled();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Substance/ }), 'Share drafts a week earlier');
    await userEvent.click(turnIntoTask);
    expect(currentLocation()).toBe('/tasks?create=task&title=Share+drafts+a+week+earlier&parent=7');
  });

  it('saves the worksheet answers with the obstacle', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('Co-author missed the deadline', { selector: 'td, td *' });
    const dialog = await openEdit('Co-author missed the deadline');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^What happened/ }), 'Draft came two weeks late');
    await userEvent.click(within(within(dialog).getByRole('group', { name: /attacking character/ })).getByRole('button', { name: 'Yes' }));
    await userEvent.click(within(within(dialog).getByRole('group', { name: /actually agree/ })).getByRole('button', { name: 'No' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(obstacleApi.updateObstacle).toHaveBeenCalledWith('token', 4, expect.objectContaining({
      conflictIncident: 'Draft came two weeks late', conflictNoCharacterAttacks: true, conflictStayedOnIncident: undefined, conflictExpectationAgreed: 'NO',
    })));
  });

  it('releases an expectation once, then shows it was released', async () => {
    vi.mocked(obstacleApi.releaseExpectation).mockResolvedValue({ ...coauthor, expectationReleasedAt: '2026-11-07T10:00:00Z' });
    renderPage(<ObstaclesPage />);
    await screen.findByText('Co-author missed the deadline', { selector: 'td, td *' });
    const dialog = await openEdit('Co-author missed the deadline');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Release this expectation' }));
    expect(obstacleApi.releaseExpectation).toHaveBeenCalledWith('token', 4);
    expect(await within(dialog).findByText(/Expectation released/)).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Release this expectation' })).not.toBeInTheDocument();
  });

  it('shows why releasing an expectation failed', async () => {
    vi.mocked(obstacleApi.releaseExpectation).mockRejectedValue(new Error('Obstacle is archived'));
    renderPage(<ObstaclesPage />);
    await screen.findByText('Co-author missed the deadline', { selector: 'td, td *' });
    const dialog = await openEdit('Co-author missed the deadline');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Release this expectation' }));
    expect(await screen.findByText('Obstacle is archived')).toBeInTheDocument();
  });

  it('moves an obstacle on the board by sending the whole record with the new status', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('No time on weekdays').closest('article') as HTMLElement;
    expect(within(card).getByText('Time · Suggested: Delegate or assistant')).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'In Progress' }));
    await waitFor(() => expect(obstacleApi.updateObstacle).toHaveBeenCalledWith('token', 1, expect.objectContaining({
      title: 'No time on weekdays', obstacleType: 'TIME', severity: 'HIGH', relatedDreamId: 1, status: 'IN_PROGRESS',
    })));
  });

  it('shows why a board move failed', async () => {
    vi.mocked(obstacleApi.updateObstacle).mockRejectedValue(new Error('Root cause is required to resolve'));
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('No time on weekdays').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Resolved' }));
    expect(await screen.findByText('Root cause is required to resolve')).toBeInTheDocument();
  });

  it('archives an obstacle with a way to undo', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    await userEvent.click(within(row('No time on weekdays')).getByRole('button', { name: 'Obstacle actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(obstacleApi.archiveObstacle).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });

  it('filters by type from the address, by the summary chips, and by search', async () => {
    const { unmount } = renderPage(<ObstaclesPage />, { route: '/obstacles?type=MONEY' });
    expect(await screen.findByText('No budget for the survey', { selector: 'td, td *' })).toBeInTheDocument();
    expect(screen.queryByText('No time on weekdays', { selector: 'td, td *' })).not.toBeInTheDocument();
    unmount();

    renderPage(<ObstaclesPage />, { route: '/obstacles' });
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: /1 critical/ }));
    expect(screen.queryByText('No time on weekdays', { selector: 'td, td *' })).not.toBeInTheDocument();
    expect(currentLocation()).toBe('/obstacles?severity=CRITICAL');
    await userEvent.click(screen.getByRole('button', { name: /1 critical/ }));

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search obstacles' }), 'grant');
    expect(screen.getByText('No budget for the survey', { selector: 'td, td *' })).toBeInTheDocument();
    expect(screen.queryByText('No time on weekdays', { selector: 'td, td *' })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByRole('searchbox', { name: 'Search obstacles' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search obstacles' }), 'zzz');
    expect(screen.getByText('No obstacles match these filters.')).toBeInTheDocument();
  });

  it('cancels an edit and starts the next obstacle blank', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    const editDialog = await openEdit('No budget for the survey');
    await userEvent.click(within(editDialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit Obstacle' })).not.toBeInTheDocument());
    const dialog = await openCreate();
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('');
    expect(within(dialog).getByRole('combobox', { name: 'Type' })).toHaveTextContent('Knowledge');
    expect(within(dialog).getByRole('combobox', { name: 'Severity' })).toHaveTextContent('Medium');
  });

  it('archives several obstacles at once, and restores an archived one', async () => {
    renderPage(<ObstaclesPage />);
    await screen.findByText('No time on weekdays', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select No time on weekdays' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select No budget for the survey' }));
    await userEvent.click(screen.getByRole('button', { name: 'Archive selected (2)' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(obstacleApi.archiveObstacle).toHaveBeenCalledTimes(2));

    vi.mocked(obstacleApi.listObstacles).mockResolvedValue([{ ...noTime, archived: true }]);
    vi.mocked(obstacleApi.restoreObstacle).mockResolvedValue(undefined);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    await waitFor(() => expect(obstacleApi.listObstacles).toHaveBeenLastCalledWith('token', true));
    await userEvent.click(within(row('No time on weekdays')).getByRole('button', { name: 'Obstacle actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    await waitFor(() => expect(obstacleApi.restoreObstacle).toHaveBeenCalledWith('token', 1));
  });

  it('opens an obstacle with its links and checkup answers when editing', async () => {
    vi.mocked(obstacleApi.listObstacles).mockResolvedValue([{
      ...coauthor, relatedDreamId: 1, requiredPartnerId: 5, letterRank: 'C', conflictNoCharacterAttacks: true, conflictStayedOnIncident: false,
      conflictExpectationAgreed: 'YES', expectationReleasedAt: '2026-11-01T10:00:00Z', criticismSubstance: 'Agree dates in writing',
    }]);
    renderPage(<ObstaclesPage />);
    await screen.findByText('Co-author missed the deadline', { selector: 'td, td *' });
    const dialog = await openEdit('Co-author missed the deadline');
    expect(within(dialog).getByRole('combobox', { name: 'Dream' })).toHaveTextContent('Become a researcher');
    expect(within(dialog).getByRole('combobox', { name: 'Required Partner' })).toHaveTextContent('Dr. Mentor');
    expect(within(dialog).getByRole('textbox', { name: /^Rank/ })).toHaveValue('C');
    expect(within(dialog).getByText('2 of 4 answered.')).toBeInTheDocument();
    expect(within(dialog).getByText(/Expectation released/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Turn this into a task' })).toBeEnabled();
  });

  it('opens a new obstacle from a link, then cleans the address', async () => {
    renderPage(<ObstaclesPage />, { route: '/obstacles?create=obstacle' });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(currentLocation()).toBe('/obstacles');
  });

  it('says when there are no obstacles yet', async () => {
    vi.mocked(obstacleApi.listObstacles).mockResolvedValue([]);
    renderPage(<ObstaclesPage />);
    expect(await screen.findByText('No obstacles yet.')).toBeInTheDocument();
  });

  it('shows the page in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<ObstaclesPage />);
    expect(await screen.findByText('No time on weekdays', { selector: 'td, td *' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'បង្កើតឧបសគ្គ' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /ដៃគូដែលបានណែនាំ/ })).toBeInTheDocument();
  });
});
