import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as issueReportApi from '../api/issueReportApi';
import { setCurrentLanguage } from '../i18n/translate';
import { renderPage } from '../test/render';
import type { IssueReport } from '../types/vision';
import { IssueReportsPage } from './IssueReportsPage';

vi.mock('../api/issueReportApi');

const report = (overrides: Partial<IssueReport>): IssueReport => ({
  id: 1, code: 'IR-001', reporterId: 1, reporterName: 'Test Participant', reporterEmail: 'participant@example.com', reportType: 'BUG',
  title: 'Save button does nothing', description: 'On the Goals page.\nEvery time.', severity: 'HIGH', contextRoute: '/goals', appVersion: '1.4.0',
  status: 'OPEN', archived: false, createdAt: '2026-11-05T09:30:00Z', updatedAt: '2026-11-05T09:30:00Z', ...overrides,
});

const mine = report({});
const otherPersons = report({ id: 2, code: 'IR-002', reporterId: 9, reporterName: 'Lim Nary', title: 'Add a dark chart theme', reportType: 'IMPROVEMENT', severity: undefined, status: 'IN_REVIEW' });
const archived = report({ id: 3, code: 'IR-003', title: 'Old typo', archived: true, status: 'CLOSED', createdAt: '2026-10-01T09:00:00Z' });

const row = (title: string) => screen.getByRole('button', { name: title }).closest('tr') as HTMLElement;

async function chooseAction(title: string, action: string) {
  await userEvent.click(within(row(title)).getByRole('button', { name: 'Report actions' }));
  await userEvent.click(screen.getByRole('menuitem', { name: action }));
}

describe('IssueReportsPage (FR-38.3, FR-38.4; BR-14)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(issueReportApi.listMyIssueReports).mockResolvedValue([mine, archived]);
    vi.mocked(issueReportApi.listAllIssueReports).mockResolvedValue([mine, otherPersons]);
    vi.mocked(issueReportApi.archiveIssueReport).mockResolvedValue(undefined as never);
    vi.mocked(issueReportApi.restoreIssueReport).mockResolvedValue(undefined as never);
    vi.mocked(issueReportApi.permanentlyDeleteIssueReport).mockResolvedValue(undefined as never);
    vi.mocked(issueReportApi.updateIssueReportStatus).mockResolvedValue(mine);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('shows a participant only their own reports, with no queue or status changes', async () => {
    renderPage(<IssueReportsPage />);
    expect(await screen.findByRole('button', { name: 'Save button does nothing' })).toBeInTheDocument();
    expect(issueReportApi.listMyIssueReports).toHaveBeenCalledWith('token', true);
    expect(issueReportApi.listAllIssueReports).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'All reports' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /Reporter/ })).not.toBeInTheDocument();

    await userEvent.click(within(row('Save button does nothing')).getByRole('button', { name: 'Report actions' }));
    expect(screen.getByRole('menuitem', { name: 'View details' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Change status' })).not.toBeInTheDocument();
  });

  it('lists reports newest first with type, severity and status', async () => {
    renderPage(<IssueReportsPage />);
    await screen.findByRole('button', { name: 'Save button does nothing' });
    const titles = screen.getAllByRole('row').slice(1).map((tableRow) => within(tableRow).getAllByRole('cell')[2].textContent);
    expect(titles).toEqual(['Save button does nothing', 'Old typo']);
    expect(within(row('Save button does nothing')).getByText('Bug')).toBeInTheDocument();
    expect(within(row('Save button does nothing')).getByText('HIGH')).toBeInTheDocument();
    expect(within(row('Save button does nothing')).getByText('OPEN')).toBeInTheDocument();
  });

  it('shows the details of a report', async () => {
    vi.mocked(issueReportApi.listMyIssueReports).mockResolvedValue([report({ resolutionNote: 'Fixed in 1.4.1' })]);
    renderPage(<IssueReportsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Save button does nothing' }));
    const dialog = await screen.findByRole('dialog', { name: 'IR-001 · Bug' });
    expect(within(dialog).getByText(/On the Goals page/)).toBeInTheDocument();
    expect(within(dialog).getByText('participant@example.com', { exact: false })).toBeInTheDocument();
    expect(within(dialog).getByText('/goals')).toBeInTheDocument();
    expect(within(dialog).getByText('1.4.0')).toBeInTheDocument();
    expect(within(dialog).getByText('Fixed in 1.4.1')).toBeInTheDocument();
  });

  it('archives a report after confirming, and restores or deletes an archived one', async () => {
    renderPage(<IssueReportsPage />);
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await chooseAction('Save button does nothing', 'Archive');
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(issueReportApi.archiveIssueReport).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByText('Archived.')).toBeInTheDocument();

    await chooseAction('Old typo', 'Restore');
    await waitFor(() => expect(issueReportApi.restoreIssueReport).toHaveBeenCalledWith('token', 3));

    await chooseAction('Old typo', 'Delete permanently');
    const confirm = await screen.findByRole('dialog');
    expect(within(confirm).getByText(/This cannot be undone/)).toBeInTheDocument();
    await userEvent.click(within(confirm).getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(issueReportApi.permanentlyDeleteIssueReport).toHaveBeenCalledWith('token', 3));
  });

  it('shows why an action failed', async () => {
    vi.mocked(issueReportApi.restoreIssueReport).mockRejectedValue(new Error('Report not found'));
    renderPage(<IssueReportsPage />);
    await screen.findByRole('button', { name: 'Old typo' });
    await chooseAction('Old typo', 'Restore');
    expect(await screen.findByText('Report not found')).toBeInTheDocument();
  });

  it('lets an admin switch to everyone’s reports, see the reporter, and filter the queue on the server', async () => {
    renderPage(<IssueReportsPage />, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await userEvent.click(screen.getByRole('button', { name: 'All reports' }));
    expect(await screen.findByRole('button', { name: 'Add a dark chart theme' })).toBeInTheDocument();
    expect(issueReportApi.listAllIssueReports).toHaveBeenCalledWith('token', { reportType: '', status: '', severity: '' });
    expect(within(row('Add a dark chart theme')).getByText('Lim Nary')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /All reports · 1 open/ })).toBeInTheDocument();

    await userEvent.click(within(screen.getByText('Type', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Improvement' }));
    await waitFor(() => expect(issueReportApi.listAllIssueReports).toHaveBeenLastCalledWith('token', { reportType: 'IMPROVEMENT', status: '', severity: '' }));
  });

  it('says when no reports match the admin’s filters', async () => {
    vi.mocked(issueReportApi.listAllIssueReports).mockResolvedValue([]);
    renderPage(<IssueReportsPage />, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await userEvent.click(screen.getByRole('button', { name: 'All reports' }));
    expect(await screen.findByText('No reports match these filters.')).toBeInTheDocument();
  });

  it('needs a resolution note before an admin resolves a bug (BR-32), then saves the new status', async () => {
    renderPage(<IssueReportsPage />, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await chooseAction('Save button does nothing', 'Change status');
    const dialog = await screen.findByRole('dialog', { name: 'Triage IR-001' });
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Resolved' }));
    expect(within(dialog).getByText(/must record how it was resolved/)).toBeInTheDocument();
    const note = within(dialog).getByRole('textbox', { name: /Resolution note/ });
    expect(note).toBeRequired();
    await userEvent.type(note, 'Fixed the click handler');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(issueReportApi.updateIssueReportStatus).toHaveBeenCalledWith('token', 1, { status: 'RESOLVED', resolutionNote: 'Fixed the click handler' });
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Triage IR-001' })).not.toBeInTheDocument());
    expect(await screen.findByText('Status updated.')).toBeInTheDocument();
  });

  it('shows why a status change was refused, and can be cancelled', async () => {
    vi.mocked(issueReportApi.updateIssueReportStatus).mockRejectedValue(new Error('A resolved bug needs a resolution note.'));
    renderPage(<IssueReportsPage />, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await chooseAction('Save button does nothing', 'Change status');
    const dialog = await screen.findByRole('dialog', { name: 'Triage IR-001' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('A resolved bug needs a resolution note.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Triage IR-001' })).not.toBeInTheDocument());
  });

  it('opens the report form from the page', async () => {
    renderPage(<IssueReportsPage />);
    await screen.findByRole('button', { name: 'Save button does nothing' });
    await userEvent.click(screen.getByRole('button', { name: 'Report an issue' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('invites a first report when there are none, and shows why reports could not load', async () => {
    vi.mocked(issueReportApi.listMyIssueReports).mockResolvedValueOnce([]);
    const { unmount } = renderPage(<IssueReportsPage />);
    expect(await screen.findByText(/You haven’t raised any reports yet/)).toBeInTheDocument();
    unmount();

    vi.mocked(issueReportApi.listMyIssueReports).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<IssueReportsPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });

  it('shows the page in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<IssueReportsPage />);
    await screen.findByRole('button', { name: 'Save button does nothing' });
    expect(screen.getByRole('button', { name: 'រាយការណ៍បញ្ហា' })).toBeInTheDocument();
  });
});
