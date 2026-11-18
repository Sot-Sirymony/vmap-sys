import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import type { ReportPeriodKind } from '../../types/vision';
import { PERIOD_LABELS, customPeriodProblem } from '../../utils/managementReport';
import { localDateIso } from '../../utils/localDate';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { tr } from '../../i18n/translate';

export type PeriodChoice = { period: ReportPeriodKind; from?: string; to?: string };

/**
 * V19 RM-FR-06: the reporting period. A preset applies at once; a custom
 * period applies when both dates are valid and the participant presses Apply,
 * so the report does not reload on every keystroke.
 */
export function ReportPeriodPicker({ value, onChange }: Readonly<{
  value: PeriodChoice;
  onChange: (choice: PeriodChoice) => void;
}>) {
  const [custom, setCustom] = useState(value.period === 'CUSTOM');
  const [from, setFrom] = useState(value.from ?? '');
  const [to, setTo] = useState(value.to ?? '');
  const [problem, setProblem] = useState('');

  // Follow the URL when it changes from outside (Back, a bookmark).
  useEffect(() => {
    setCustom(value.period === 'CUSTOM');
    setFrom(value.from ?? '');
    setTo(value.to ?? '');
  }, [value.period, value.from, value.to]);

  function choose(kind: ReportPeriodKind) {
    setProblem('');
    if (kind === 'CUSTOM') {
      setCustom(true);
      return;
    }
    setCustom(false);
    onChange({ period: kind });
  }

  function apply() {
    const reason = customPeriodProblem(from, to, localDateIso());
    setProblem(reason);
    if (!reason) onChange({ period: 'CUSTOM', from, to });
  }

  return (
    <>
      <label>
        {tr('Period')}
        <FormControl size="small" sx={{ minWidth: 160, display: 'flex' }}>
          <Select SelectDisplayProps={{ 'aria-label': tr('Period') }} value={custom ? 'CUSTOM' : value.period}
            onChange={(event) => choose(event.target.value as ReportPeriodKind)}>
            {Object.entries(PERIOD_LABELS).map(([kind, label]) => <MenuItem value={kind} key={kind}>{tr(label)}</MenuItem>)}
            <MenuItem value="CUSTOM">{tr('Custom dates')}</MenuItem>
          </Select>
        </FormControl>
      </label>
      {custom && (
        <>
          <label>
            {tr('From')}
            <Input type="date" aria-label={tr('From')} value={from} max={localDateIso()} onChange={(event) => setFrom(event.target.value)} />
          </label>
          <label>
            {tr('To')}
            <Input type="date" aria-label={tr('To')} value={to} max={localDateIso()} onChange={(event) => setTo(event.target.value)} />
          </label>
          <Button variant="secondary" onClick={apply}>{tr('Apply')}</Button>
          {problem && <Alert severity="warning" sx={{ flexBasis: '100%' }}>{problem}</Alert>}
        </>
      )}
    </>
  );
}
