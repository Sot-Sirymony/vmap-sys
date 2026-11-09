import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getModuleHelp } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { LearningModuleDetail, ModuleRow } from '../../types/vision';
import { Button } from '../common/Button';

type Finding = { key: string; text: string };

/**
 * V15 (AH-FR-20): Check before I save. With AI, each field that is empty or
 * too short gets a one-line tip; without AI, empty fields are listed. It never
 * changes the answer and never blocks saving.
 */
export function ModuleReadinessCheck({ token, module, rows }: Readonly<{ token: string; module: LearningModuleDetail; rows: ModuleRow[] }>) {
  const { language } = useI18n();
  const [aiOn, setAiOn] = useState(false);
  const [findings, setFindings] = useState<Finding[] | null>(null);
  const [fromAi, setFromAi] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'MODULE'))).catch(() => setAiOn(false));
  }, [token]);

  if (module.fields.length === 0) {
    return null;
  }
  const label = (key: string, row: number) => {
    const field = module.fields.find((item) => item.key === key);
    const name = field?.label ?? key;
    return rows.length > 1 ? `${name} (${tr('row {number}', { number: row + 1 })})` : name;
  };

  function emptyFields(): Finding[] {
    const out: Finding[] = [];
    rows.forEach((row, index) => module.fields.forEach((field) => {
      if (!(row[field.key] ?? '').trim()) {
        out.push({ key: `${index}-${field.key}`, text: tr('{field} is empty.', { field: label(field.key, index) }) });
      }
    }));
    return out;
  }

  async function check() {
    setBusy(true);
    setError('');
    setFromAi(false);
    try {
      if (aiOn) {
        try {
          const help = await getModuleHelp(token, module.number, 'CHECK', rows, language);
          setFindings((help.checks ?? []).map((item) => ({ key: `${item.row}-${item.field}`, text: `${label(item.field, item.row)}: ${item.tip}` })));
          setFromAi(true);
          return;
        } catch {
          // Fall back to the empty-field check below.
        }
      }
      setFindings(emptyFields());
    } catch (checkError) {
      setError(checkError instanceof Error ? tr(checkError.message) : tr('The checks could not be loaded.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('Check before I save')}>
      <Button variant="secondary" disabled={busy} onClick={() => void check()}>
        {busy ? tr('Checking…') : tr('Check before I save')}
      </Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {findings && (
        <Box aria-live="polite" sx={{ mt: 1 }}>
          {fromAi && <Chip size="small" label={tr('AI suggestion')} sx={{ mb: 0.5 }} />}
          {findings.length === 0
            ? <Typography variant="body2">{tr('Your answer looks ready to save.')}</Typography>
            : (
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {findings.map((item) => <li key={item.key}><Typography variant="body2">{item.text}</Typography></li>)}
              </Box>
            )}
        </Box>
      )}
    </Box>
  );
}
