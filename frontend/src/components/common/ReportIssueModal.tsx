import { FormEvent, useState } from 'react';
import { useLocation } from 'react-router-dom';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { Button } from './Button';
import { ErrorMessage } from './ErrorMessage';
import { Input } from './Input';
import { Modal } from './Modal';
import { Textarea } from './Textarea';
import { createIssueReport } from '../../api/issueReportApi';
import { APP_VERSION } from '../../config/appMeta';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import type { ReportType, Severity } from '../../types/vision';
import { reportTypeLabels, severityLabels } from '../../utils/enumLabels';

const reportTypes: ReportType[] = ['BUG', 'IMPROVEMENT', 'QUESTION', 'OTHER'];
const severities: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

type ReportIssueModalProps = {
  onClose: () => void;
  /** Called after a report is created, so a listing can refresh. */
  onSubmitted?: () => void;
};

/**
 * FR-38.1/38.2: raise a bug or improvement from anywhere in the app. The route
 * the user is on and the app version are captured automatically on submit, so a
 * bug is reproducible without the user describing where they were. Severity is
 * asked for (and required) only when the type is Bug (BR-32).
 */
export function ReportIssueModal({ onClose, onSubmitted }: ReportIssueModalProps) {
  const { token } = useAuth();
  const { showToast } = useToast();
  const location = useLocation();
  const [reportType, setReportType] = useState<ReportType>('BUG');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      await createIssueReport(token, {
        reportType,
        title,
        description: description || undefined,
        severity: reportType === 'BUG' ? severity : undefined,
        contextRoute: `${location.pathname}${location.search}`,
        appVersion: APP_VERSION,
      });
      showToast('Thanks — your report was received.');
      onSubmitted?.();
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to submit your report.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="Report an issue" onClose={onClose}>
      <form className="form-grid" onSubmit={handleSubmit}>
        {error && <ErrorMessage message={error} />}
        <label>
          Type
          <FormControl fullWidth size="small">
            <Select
              SelectDisplayProps={{ 'aria-label': 'Type' }}
              value={reportType}
              onChange={(event) => setReportType(event.target.value as ReportType)}
            >
              {reportTypes.map((value) => (
                <MenuItem value={value} key={value}>{reportTypeLabels[value]}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </label>
        {reportType === 'BUG' && (
          <label>
            Severity
            <FormControl fullWidth size="small">
              <Select
                SelectDisplayProps={{ 'aria-label': 'Severity' }}
                value={severity}
                onChange={(event) => setSeverity(event.target.value as Severity)}
              >
                {severities.map((value) => (
                  <MenuItem value={value} key={value}>{severityLabels[value]}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </label>
        )}
        <label className="field-full">
          Title
          <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus maxLength={220} />
        </label>
        <label className="field-full">
          What happened?
          <Textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Steps you took, what you expected, and what happened instead."
          />
          <span className="field-hint">
            The page you're on ({location.pathname}) and app version ({APP_VERSION}) are attached automatically.
          </span>
        </label>
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', mt: 1 }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" disabled={saving}>{saving ? 'Sending…' : 'Send report'}</Button>
        </Stack>
      </form>
    </Modal>
  );
}
