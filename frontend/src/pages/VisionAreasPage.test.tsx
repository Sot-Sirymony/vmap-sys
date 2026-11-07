import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiPlanApi from '../api/aiPlanApi';
import * as dreamApi from '../api/dreamApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { VisionAreasPage } from './VisionAreasPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiPlanApi');
vi.mock('../api/dreamApi');
vi.mock('../api/visionAreaApi');

const career = aVisionArea({ id: 1, code: 'VA-001', name: 'Career', priority: 'HIGH', letterRank: 'A', sortOrder: 0 });
const health = aVisionArea({ id: 2, code: 'VA-002', name: 'Health', priority: 'MEDIUM', status: 'PAUSED', letterRank: 'B', sortOrder: 1 });
const family = aVisionArea({ id: 3, code: 'VA-003', name: 'Family', priority: 'LOW', sortOrder: 2 });

const row = (name: string) => screen.getByText(name, { selector: '.area-dot-label' }).closest('tr') as HTMLElement;

async function openRowMenu(name: string) {
  await userEvent.click(within(row(name)).getByRole('button', { name: 'Vision area actions' }));
}

describe('VisionAreasPage (CLAUDE.md page 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([aiPlanApi, dreamApi, visionAreaApi]);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 0, messagesLeftToday: 0 });
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([career, health, family]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, visionAreaId: 1 }), aDream({ id: 2, visionAreaId: 1 }), aDream({ id: 3, visionAreaId: 2 })]);
    vi.mocked(visionAreaApi.createVisionArea).mockResolvedValue(career);
    vi.mocked(visionAreaApi.updateVisionArea).mockResolvedValue(career);
    vi.mocked(visionAreaApi.archiveVisionArea).mockResolvedValue(undefined);
    vi.mocked(visionAreaApi.restoreVisionArea).mockResolvedValue(undefined);
    vi.mocked(visionAreaApi.reorderVisionAreas).mockResolvedValue(undefined as never);
    vi.mocked(visionAreaApi.getVisionAreaArchiveImpact).mockResolvedValue({ dreams: 2, goals: 3, steps: 4, tasks: 5 } as never);
  });

  it('lists areas in their order, with priority, rank, status and the number of dreams under each', async () => {
    renderPage(<VisionAreasPage />);
    expect(await screen.findByText('Career', { selector: '.area-dot-label' })).toBeInTheDocument();
    const careerRow = within(row('Career'));
    expect(careerRow.getByText('VA-001')).toBeInTheDocument();
    expect(careerRow.getByText('A')).toBeInTheDocument();
    expect(careerRow.getByText('2')).toBeInTheDocument();
    expect(within(row('Health')).getByText('1')).toBeInTheDocument();
    expect(within(row('Family')).getAllByText('0').length).toBeGreaterThan(0);
    const names = screen.getAllByText(/^(Career|Health|Family)$/, { selector: '.area-dot-label' }).map((cell) => cell.textContent);
    expect(names).toEqual(['Career', 'Health', 'Family']);
  });

  it('creates an area with the form once areas exist', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await userEvent.click(screen.getByRole('button', { name: 'Create vision area' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Name/ }), 'Finance');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Rank/ }), 'c');
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Priority' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Critical' }));
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create vision area' }).at(-1)!);
    expect(visionAreaApi.createVisionArea).toHaveBeenCalledWith('token', expect.objectContaining({ name: 'Finance', priority: 'CRITICAL', letterRank: 'C', status: 'ACTIVE' }));
  });

  it('opens the guided wizard when there are no areas yet', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([]);
    renderPage(<VisionAreasPage />);
    expect(await screen.findByText('No vision areas yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create your first vision area' }));
    expect(await screen.findByLabelText(/What area of life or work is this/)).toBeInTheDocument();
  });

  it('opens the create flow from a ?create=area link, then removes it from the address', async () => {
    renderPage(<VisionAreasPage />, { route: '/vision-areas?create=area' });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(currentLocation()).toBe('/vision-areas'));
  });

  it('edits an area with its current values filled in', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await openRowMenu('Career');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Vision Area' });
    const name = within(dialog).getByRole('textbox', { name: /^Name/ });
    expect(name).toHaveValue('Career');
    await userEvent.clear(name);
    await userEvent.type(name, 'Career growth');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(visionAreaApi.updateVisionArea).toHaveBeenCalledWith('token', 1, expect.objectContaining({ name: 'Career growth', letterRank: 'A' }));
  });

  it('cancels an edit without saving', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await openRowMenu('Career');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    expect(visionAreaApi.updateVisionArea).not.toHaveBeenCalled();
  });

  it('archives an area only after showing everything that would be archived with it (BR-12)', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await openRowMenu('Career');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/Archiving "Career" also archives 2 dream\(s\), 3 goal\(s\), 4 step\(s\), and 5 task\(s\)/)).toBeInTheDocument();
    expect(visionAreaApi.archiveVisionArea).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(visionAreaApi.archiveVisionArea).toHaveBeenCalledWith('token', 1);
  });

  it('opens the dreams of an area, or a new dream under it', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await openRowMenu('Career');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Add dream' }));
    expect(currentLocation()).toBe('/dreams?create=dream&parent=1');
    await openRowMenu('Health');
    await userEvent.click(screen.getByRole('menuitem', { name: 'View dreams' }));
    expect(currentLocation()).toBe('/dreams?visionAreaId=2');
  });

  it('filters by search, priority, rank and status, and says when nothing matches', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search vision areas' }), 'heal');
    expect(screen.queryByText('Career', { selector: '.area-dot-label' })).not.toBeInTheDocument();
    expect(screen.getByText('Health', { selector: '.area-dot-label' })).toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search vision areas' }), 'zzz');
    expect(await screen.findByText('No vision areas match these filters.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));

    const filters = screen.getByText('Status', { selector: 'label' });
    await userEvent.click(within(filters).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Paused' }));
    expect(screen.queryByText('Career', { selector: '.area-dot-label' })).not.toBeInTheDocument();
    expect(screen.getByText('Health', { selector: '.area-dot-label' })).toBeInTheDocument();
  });

  it('shows a board by status and moves an area to another status', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    const [firstMove] = screen.getAllByRole('combobox', { name: 'Move to status' });
    await userEvent.click(firstMove);
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(visionAreaApi.updateVisionArea).toHaveBeenCalledWith('token', expect.any(Number), expect.objectContaining({ status: 'COMPLETED' }));
    expect(localStorage.getItem('vms-view-vision-areas')).toContain('board');
  });

  it('reorders areas by dragging one row onto another', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    fireEvent.dragStart(row('Family'));
    fireEvent.dragOver(row('Career'));
    fireEvent.drop(row('Career'));
    await waitFor(() => expect(visionAreaApi.reorderVisionAreas).toHaveBeenCalledWith('token', [3, 1, 2]));
  });

  it('archives selected areas together after confirming', async () => {
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Career' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Family' }));
    await userEvent.click(screen.getByRole('button', { name: /Archive selected|Archive 2/ }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(visionAreaApi.archiveVisionArea).toHaveBeenCalledTimes(2));
  });

  it('shows archived areas on request, and restores one', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockImplementation(async (_token, includeArchived) => (
      includeArchived ? [career, aVisionArea({ id: 9, name: 'Old area', archived: true, status: 'ARCHIVED' })] : [career]));
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    expect(await screen.findByText('Old area', { selector: '.area-dot-label' })).toBeInTheDocument();
    await openRowMenu('Old area');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(visionAreaApi.restoreVisionArea).toHaveBeenCalledWith('token', 9);
  });

  it('shows why the areas could not load, with a retry', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockRejectedValueOnce(new Error('Server is down.')).mockResolvedValue([career]);
    renderPage(<VisionAreasPage />);
    expect(await screen.findByText('Server is down.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Retry|Try again/ }));
    expect(await screen.findByText('Career', { selector: '.area-dot-label' })).toBeInTheDocument();
  });

  it('switches from the wizard to the plain form, and reloads after the wizard creates an area', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValueOnce([]).mockResolvedValue([career]);
    renderPage(<VisionAreasPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Create your first vision area' }));
    await userEvent.click(screen.getByRole('button', { name: 'Skip the guide' }));
    expect(await screen.findByRole('dialog', { name: 'Create Vision Area' })).toBeInTheDocument();
  });

  it('refreshes areas and dream counts once the wizard has created an area', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValueOnce([]).mockResolvedValue([career]);
    renderPage(<VisionAreasPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Create your first vision area' }));
    await userEvent.type(screen.getByLabelText(/What area of life or work is this/), 'Career');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create vision area' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Done' }));
    expect(await screen.findByText('Career', { selector: '.area-dot-label' })).toBeInTheDocument();
    expect(dreamApi.listDreams).toHaveBeenCalledTimes(2);
  });

  it('restores or permanently deletes selected archived areas together', async () => {
    const old = aVisionArea({ id: 9, name: 'Old area', archived: true, status: 'ARCHIVED' });
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([career, old]);
    vi.mocked(visionAreaApi.permanentlyDeleteVisionArea).mockResolvedValue(undefined);
    renderPage(<VisionAreasPage />);
    await screen.findByText('Old area', { selector: '.area-dot-label' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Old area' }));
    await userEvent.click(screen.getByRole('button', { name: /Restore/ }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Restore' }));
    await waitFor(() => expect(visionAreaApi.restoreVisionArea).toHaveBeenCalledWith('token', 9));

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Old area' }));
    await userEvent.click(screen.getByRole('button', { name: /Delete permanently/ }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete permanently' }));
    await waitFor(() => expect(visionAreaApi.permanentlyDeleteVisionArea).toHaveBeenCalledWith('token', 9));
  });

  it('shows why a board move or a reorder failed', async () => {
    vi.mocked(visionAreaApi.updateVisionArea).mockRejectedValue(new Error('Area has active dreams.'));
    vi.mocked(visionAreaApi.reorderVisionAreas).mockRejectedValue(new Error('Order changed elsewhere.'));
    renderPage(<VisionAreasPage />);
    await screen.findByText('Career', { selector: '.area-dot-label' });
    fireEvent.dragStart(row('Family'));
    fireEvent.dragOver(row('Career'));
    fireEvent.drop(row('Career'));
    expect(await screen.findByText('Order changed elsewhere.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    await userEvent.click(screen.getAllByRole('combobox', { name: 'Move to status' })[0]);
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(await screen.findByText('Area has active dreams.')).toBeInTheDocument();
  });
});
