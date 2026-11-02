import { ChangeEvent, useState } from 'react';
import { exportExcelWorkbook, importExcelWorkbook } from '../api/excelApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import MuiButton from '@mui/material/Button';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import type { ExcelImportSummary } from '../types/vision';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

export function ImportExportPage() {
  const { token } = useAuth();
  const [summary, setSummary] = useState<ExcelImportSummary | null>(null);
  const [fileName, setFileName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleExport() {
    if (!token) {
      return;
    }
    setLoading(true);
    try {
      const blob = await exportExcelWorkbook(token);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `vision-mapping-export-${new Date().toISOString().slice(0, 10)}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
      setError('');
    } catch (exportError) {
      setError(exportError instanceof Error ? exportError.message : tr('Unable to export workbook.'));
    } finally {
      setLoading(false);
    }
  }

  async function handleImport(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!token || !file) {
      return;
    }
    setFileName(file.name);
    setLoading(true);
    try {
      setSummary(await importExcelWorkbook(token, file));
      setError('');
    } catch (importError) {
      setError(importError instanceof Error ? importError.message : tr('Unable to import workbook.'));
    } finally {
      setLoading(false);
      event.target.value = '';
    }
  }

  return (
    <PageSection title={tr('Import / Export')} subtitle={tr('Move Vision Mapping data between the system and Excel.')}>
      {loading && <Loading />}
      {error && <ErrorMessage message={error} />}
      <div className="two-column">
        <Card>
          <CardHeader title={tr('Import Excel')} subheader={tr('Upload an exported .xlsx workbook to recreate its vision areas, dreams, goals, steps, and tasks.')} />
          <CardContent className="card-action">
            <MuiButton component="label" variant="contained" color="secondary" size="small">
              {tr('Choose workbook')}
              <input type="file" accept=".xlsx" hidden onChange={(event) => void handleImport(event)} />
            </MuiButton>
            {fileName && <p className="field-hint">Selected: {fileName}</p>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={tr('Export Excel')} subheader={tr('Download all current Vision Mapping data as a formatted workbook.')} />
          <CardContent className="card-action">
            <Button type="button" onClick={() => void handleExport()}>{tr('Export workbook')}</Button>
          </CardContent>
        </Card>
      </div>
      {summary && (
        <Card>
          <CardHeader title={tr('Import Summary')} />
          <CardContent>
            <div className="metric-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
              <div className="metric-card">
                <p>{tr('Records created')}</p>
                <strong>{summary.createdRecords}</strong>
              </div>
              <div className="metric-card">
                <p>{tr('Rows skipped')}</p>
                <strong>{summary.skippedRecords}</strong>
              </div>
              <div className="metric-card">
                <p>{tr('Errors')}</p>
                <strong>{summary.validationErrors.length}</strong>
              </div>
            </div>
            {Object.keys(summary.rowsBySheet).length > 0 && (
              <p className="field-hint" style={{ color: 'var(--muted-foreground)' }}>
                {Object.entries(summary.rowsBySheet).map(([sheet, count]) => `${sheet}: ${count}`).join(' · ')}
              </p>
            )}
            {summary.backupFile && (
              <p className="field-hint">A backup snapshot was saved before the import: {summary.backupFile}</p>
            )}
            {summary.createdRecords > 0 && (
              <p className="field-hint">{tr('Open Goals, Steps, or Tasks to see the imported records.')}</p>
            )}
            {summary.validationErrors.length > 0 && (
              <div className="stack-list">
                {summary.validationErrors.map((validationError) => (
                  <ErrorMessage message={validationError} key={validationError} />
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </PageSection>
  );
}
