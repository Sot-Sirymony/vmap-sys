import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { tr } from '../../i18n/translate';
import { saveBlob } from '../../utils/saveBlob';

/**
 * V21 (TF-FR-04): the ten recovery codes, shown once. Download, Copy and a
 * required "I have saved these codes" tick before the panel closes.
 */
export function RecoveryCodesPanel({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = codes.join('\n');

  function download() {
    const header = tr('Vision Mapping recovery codes. Each works once. Keep them somewhere safe.');
    saveBlob(new Blob([header + '\n\n' + text + '\n'], { type: 'text/plain' }), 'vision-mapping-recovery-codes.txt');
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Stack spacing={1.5}>
      <Alert severity="warning">
        {tr('Save these recovery codes now. Each one works once if you lose your phone. They will not be shown again.')}
      </Alert>
      <Box component="ol" aria-label={tr('Recovery codes')}
        sx={{ fontFamily: 'monospace', fontSize: '1rem', columns: 2, m: 0, pl: 3 }}>
        {codes.map((code) => <li key={code}>{code}</li>)}
      </Box>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Button variant="secondary" onClick={download}>{tr('Download')}</Button>
        <Button variant="secondary" onClick={() => void copy()}>{copied ? tr('Copied') : tr('Copy')}</Button>
      </Stack>
      <FormControlLabel
        control={<Checkbox checked={saved} onChange={(event) => setSaved(event.target.checked)} />}
        label={tr('I have saved these codes')}
      />
      <Box>
        <Button disabled={!saved} onClick={onDone}>{tr('Done')}</Button>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {tr('Lost them? Make new ones here at any time; the old ones then stop working.')}
      </Typography>
    </Stack>
  );
}
