import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as excelApi from '../api/excelApi';
import { setCurrentLanguage } from '../i18n/translate';
import { renderPage } from '../test/render';
import type { ExcelImportSummary } from '../types/vision';
import { ImportExportPage } from './ImportExportPage';

vi.mock('../api/excelApi');

const workbook = () => new File(['xlsx'], 'my-vision-map.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
const fileInput = () => document.querySelector('input[type="file"]') as HTMLInputElement;

const summary = (overrides: Partial<ExcelImportSummary> = {}): ExcelImportSummary => ({
  createdRecords: 12,
  skippedRecords: 2,
  validationErrors: ['Tasks row 4: owner is required', 'Tasks row 9: due date is not a date'],
  rowsBySheet: { 'Vision Areas': 2, Tasks: 10 },
  backupFile: 'backup-2026-11-07.json',
  ...overrides,
} as ExcelImportSummary);

describe('ImportExportPage (CLAUDE.md page 11)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    URL.createObjectURL = vi.fn(() => 'blob:workbook');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => setCurrentLanguage('en'));

  it('accepts only .xlsx workbooks', () => {
    renderPage(<ImportExportPage />);
    expect(fileInput()).toHaveAttribute('accept', '.xlsx');
  });

  it('imports a chosen workbook and shows created, skipped and each error', async () => {
    vi.mocked(excelApi.importExcelWorkbook).mockResolvedValue(summary());
    renderPage(<ImportExportPage />);
    const file = workbook();
    await userEvent.upload(fileInput(), file);

    expect(excelApi.importExcelWorkbook).toHaveBeenCalledWith('token', file);
    expect(await screen.findByText('Import Summary')).toBeInTheDocument();
    expect(screen.getByText('Selected: my-vision-map.xlsx')).toBeInTheDocument();
    expect(screen.getByText('Records created').nextSibling).toHaveTextContent('12');
    expect(screen.getByText('Rows skipped').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Errors').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Vision Areas: 2 · Tasks: 10')).toBeInTheDocument();
    expect(screen.getByText(/backup snapshot was saved before the import: backup-2026-11-07\.json/)).toBeInTheDocument();
    expect(screen.getByText('Tasks row 4: owner is required')).toBeInTheDocument();
    expect(screen.getByText('Tasks row 9: due date is not a date')).toBeInTheDocument();
    expect(screen.getByText('Open Goals, Steps, or Tasks to see the imported records.')).toBeInTheDocument();
    // The input is cleared, so the same file can be chosen again.
    expect(fileInput().value).toBe('');
  });

  it('does not point to imported records when nothing was created', async () => {
    vi.mocked(excelApi.importExcelWorkbook).mockResolvedValue(summary({ createdRecords: 0, validationErrors: [], rowsBySheet: {}, backupFile: undefined }));
    renderPage(<ImportExportPage />);
    await userEvent.upload(fileInput(), workbook());
    expect(await screen.findByText('Import Summary')).toBeInTheDocument();
    expect(screen.queryByText('Open Goals, Steps, or Tasks to see the imported records.')).not.toBeInTheDocument();
    expect(screen.queryByText(/backup snapshot/)).not.toBeInTheDocument();
  });

  it('shows why an import failed', async () => {
    vi.mocked(excelApi.importExcelWorkbook).mockRejectedValue(new Error('Sheet "Tasks" is missing the column "Owner"'));
    renderPage(<ImportExportPage />);
    await userEvent.upload(fileInput(), workbook());
    expect(await screen.findByText('Sheet "Tasks" is missing the column "Owner"')).toBeInTheDocument();
    expect(screen.queryByText('Import Summary')).not.toBeInTheDocument();
  });

  it('downloads the export as a dated .xlsx file', async () => {
    vi.mocked(excelApi.exportExcelWorkbook).mockResolvedValue(new Blob(['xlsx']));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    renderPage(<ImportExportPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Export workbook' }));

    await waitFor(() => expect(click).toHaveBeenCalled());
    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toMatch(/^vision-mapping-export-\d{4}-\d{2}-\d{2}\.xlsx$/);
    expect(link.href).toBe('blob:workbook');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:workbook');
    click.mockRestore();
  });

  it('shows why an export failed', async () => {
    vi.mocked(excelApi.exportExcelWorkbook).mockRejectedValue(new Error('Export failed with status 500'));
    renderPage(<ImportExportPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Export workbook' }));
    expect(await screen.findByText('Export failed with status 500')).toBeInTheDocument();
  });

  it('shows the page in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    vi.mocked(excelApi.importExcelWorkbook).mockResolvedValue(summary());
    renderPage(<ImportExportPage />);
    expect(screen.getByText('ជ្រើសសៀវភៅបញ្ជី')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'នាំចេញសៀវភៅបញ្ជី' })).toBeInTheDocument();
    await userEvent.upload(fileInput(), workbook());
    expect(await screen.findByText('កំណត់ត្រាបានបង្កើត')).toBeInTheDocument();
    expect(screen.getByText('ជួរបានរំលង')).toBeInTheDocument();
  });
});
