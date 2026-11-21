import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportExportTab } from './ReportExportTab';

const downloadManagementReport = vi.fn();
const saveBlob = vi.fn();
vi.mock('../../api/reportApi', () => ({ downloadManagementReport: (...args: unknown[]) => downloadManagementReport(...args) }));
vi.mock('../../utils/saveBlob', () => ({ saveBlob: (...args: unknown[]) => saveBlob(...args) }));

describe('Management Report export tab (V18 MR-FR-15)', () => {
  beforeEach(() => {
    downloadManagementReport.mockReset();
    saveBlob.mockReset();
  });

  it('downloads the Excel report for the chosen scope under the server\'s file name', async () => {
    const blob = new Blob(['xlsx']);
    downloadManagementReport.mockResolvedValue({ blob, fileName: 'Management_Report_Dream_7_2026-11-16.xlsx' });
    render(<ReportExportTab token="t" filter={{ dreamId: 7 }} disabledReason="" />);

    await userEvent.click(screen.getByRole('button', { name: 'Export to Excel' }));
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'Management_Report_Dream_7_2026-11-16.xlsx'));
    expect(downloadManagementReport).toHaveBeenCalledWith('t', { dreamId: 7 }, 'xlsx', 'en', {});
    expect(screen.getByText('Downloaded Management_Report_Dream_7_2026-11-16.xlsx.')).toBeInTheDocument();
  });

  it('downloads the deck with its title and without the name when asked (MR-FR-23, MR-FR-31)', async () => {
    const blob = new Blob(['pptx']);
    downloadManagementReport.mockResolvedValue({ blob, fileName: 'Management_Report_All_dreams_2026-11-16.pptx' });
    render(<ReportExportTab token="t" filter={{}} disabledReason="" />);

    await userEvent.type(screen.getByRole('textbox', { name: 'Deck title (optional)' }), 'Quarterly review');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Put my name on the title slide' }));
    await userEvent.click(screen.getByRole('button', { name: 'Export to PowerPoint' }));

    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'Management_Report_All_dreams_2026-11-16.pptx'));
    expect(downloadManagementReport).toHaveBeenCalledWith('t', {}, 'pptx', 'en', { title: 'Quarterly review', includeName: false });
  });

  it('sends only the sections chosen and the file language picked (RM-FR-39, RM-FR-44)', async () => {
    const blob = new Blob(['xlsx']);
    downloadManagementReport.mockResolvedValue({ blob, fileName: 'report.xlsx' });
    const onSectionsChange = vi.fn();
    render(<ReportExportTab token="t" filter={{}} disabledReason="" onSectionsChange={onSectionsChange} />);

    await userEvent.click(screen.getByRole('checkbox', { name: 'Blockers' }));
    expect(onSectionsChange).toHaveBeenLastCalledWith(['COMMENTARY', 'SUMMARY', 'GOAL_HEALTH', 'DETAILS', 'CHANGES', 'NEXT_ACTIONS', 'SUPPORT']);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'File language' }), 'km');
    await userEvent.click(screen.getByRole('button', { name: 'Export to Excel' }));
    await waitFor(() => expect(downloadManagementReport).toHaveBeenCalledWith('t', {}, 'xlsx', 'km',
      { sections: ['COMMENTARY', 'SUMMARY', 'GOAL_HEALTH', 'DETAILS', 'CHANGES', 'NEXT_ACTIONS', 'SUPPORT'] }));
  });

  it('downloads the PDF and says so to whoever keeps the download history (RM-FR-42, RM-FR-25)', async () => {
    const blob = new Blob(['%PDF']);
    downloadManagementReport.mockResolvedValue({ blob, fileName: 'report.pdf' });
    const onDownloaded = vi.fn();
    render(<ReportExportTab token="t" filter={{}} disabledReason="" onDownloaded={onDownloaded} />);
    await userEvent.click(screen.getByRole('button', { name: 'Export to PDF' }));
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'report.pdf'));
    expect(downloadManagementReport).toHaveBeenCalledWith('t', {}, 'pdf', 'en', {});
    expect(onDownloaded).toHaveBeenCalledTimes(1);
  });

  it('turns the buttons off until at least one section is chosen', async () => {
    render(<ReportExportTab token="t" filter={{}} disabledReason="" sections={['SUMMARY']} />);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Summary' }));
    expect(screen.getByText('Choose at least one section.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export to Excel' })).toBeDisabled();
  });

  it('shows the server\'s reason when a download fails', async () => {
    downloadManagementReport.mockRejectedValue(new Error('Too many report downloads in a short time. Try again in a few minutes.'));
    render(<ReportExportTab token="t" filter={{}} disabledReason="" />);
    await userEvent.click(screen.getByRole('button', { name: 'Export to Excel' }));
    expect(await screen.findByText('Too many report downloads in a short time. Try again in a few minutes.')).toBeInTheDocument();
    expect(saveBlob).not.toHaveBeenCalled();
  });

  it('keeps the buttons off with the reason when there is nothing to export (MR-FR-05)', () => {
    render(<ReportExportTab token="t" filter={{}} disabledReason="Add goals to a dream first; there is nothing to export yet." />);
    expect(screen.getByRole('button', { name: 'Export to Excel' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Export to PowerPoint' })).toBeDisabled();
    expect(screen.getByText('Add goals to a dream first; there is nothing to export yet.')).toBeInTheDocument();
  });
});
