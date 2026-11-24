import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useState } from 'react';
import { listTaskRepeatRules, updateTaskRepeatRule } from '../../api/streamlineApi';
import { surface } from '../../design/surface';
import { tr } from '../../i18n/translate';
import type { RepeatCadence, StreamlineRepeatRule } from '../../types/streamline';
import { Button } from '../common/Button';
import { CADENCE_LABELS } from './streamlineLabels';

/**
 * V27 SR-FR-09: the repeating tasks the app creates, with how often each comes
 * back, the next due date, and Stop / Restart. Tasks a rule already made stay.
 */
export function RepeatingTasksCard({ token }: { token: string }) {
  const [rules, setRules] = useState<StreamlineRepeatRule[] | null>(null);
  const [error, setError] = useState('');
  const cadences = CADENCE_LABELS();

  const load = useCallback(() => {
    listTaskRepeatRules(token).then(setRules).catch(() => setRules([]));
  }, [token]);

  useEffect(load, [load]);

  async function change(rule: StreamlineRepeatRule, body: { cadence?: RepeatCadence; active?: boolean }) {
    setError('');
    try {
      const updated = await updateTaskRepeatRule(token, rule.id, body);
      setRules((current) => (current ?? []).map((value) => (value.id === updated.id ? updated : value)));
    } catch (changeError) {
      setError(changeError instanceof Error ? tr(changeError.message) : tr('That did not work. Try again.'));
    }
  }

  if (!rules || rules.length === 0) return null;
  return (
    <Box component="section" aria-labelledby="repeating-tasks" sx={{ ...surface('card'), display: 'grid', gap: 1 }}>
      <Typography id="repeating-tasks" variant="h2">{tr('Repeating tasks')}</Typography>
      <Typography variant="body2" sx={{ color: 'var(--text-secondary)' }}>
        {tr('The app creates the next one once the current one is due. Stopping keeps the tasks already made.')}
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {rules.map((rule) => (
          <Box component="li" key={rule.id} data-testid={`repeat-rule-${rule.id}`}
            sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', justifyContent: 'space-between', py: 1, borderTop: '1px solid var(--divider)', '&:first-of-type': { borderTop: 'none' } }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body1" sx={{ fontWeight: 600 }}>{rule.title}</Typography>
              <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
                {rule.active ? tr('Next due {date}', { date: new Date(rule.nextDue).toLocaleDateString() }) : tr('Stopped')}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <TextField select size="small" label={tr('How often')} value={rule.cadence} disabled={!rule.active} sx={{ minWidth: 150 }}
                onChange={(event) => void change(rule, { cadence: event.target.value as RepeatCadence })}>
                {(Object.keys(cadences) as RepeatCadence[]).map((key) => <MenuItem key={key} value={key}>{cadences[key]}</MenuItem>)}
              </TextField>
              <Button variant="secondary" onClick={() => void change(rule, { active: !rule.active })}>
                {rule.active ? tr('Stop repeating') : tr('Restart')}
              </Button>
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}
