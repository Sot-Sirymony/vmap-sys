import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type { StreamlineDrafts, StreamlineSuggestion } from '../../types/streamline';
import { Button } from '../common/Button';
import { STAGE_NAMES } from './streamlineLabels';

/**
 * V32 (D-32-7, SF-FR-08): the drafts Draft all stages made for stages the user
 * has not reached. Read-only: the stage order lock stays (D-27-3), so these
 * cards become the stage's suggestions only when the user gets there.
 */
export function StreamlineDraftPreview({ drafts, currentStage, label }: {
  drafts: StreamlineDrafts;
  currentStage: number;
  label: (suggestion: StreamlineSuggestion) => string;
}) {
  const [open, setOpen] = useState<number | null>(null);
  const later = drafts.stages.filter((entry) => entry.preview && entry.stage > currentStage);
  if (later.length === 0) return null;
  const names = STAGE_NAMES();
  const shown = later.find((entry) => entry.stage === open);

  return (
    <Box component="section" aria-label={tr('Drafts for later stages')} sx={{ display: 'grid', gap: 0.75 }} data-testid="draft-preview">
      <Typography variant="subtitle2" component="h3">{tr('Drafts for later stages')}</Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {later.map((entry) => (
          <Button key={entry.stage} variant={open === entry.stage ? 'primary' : 'secondary'} aria-pressed={open === entry.stage}
            onClick={() => setOpen(open === entry.stage ? null : entry.stage)}>
            {tr('Stage {number}: {name} ({count})', { number: entry.stage, name: names[entry.stage - 1], count: entry.suggestions.suggestions.length })}
          </Button>
        ))}
      </Box>
      {shown && (
        <Box sx={{ display: 'grid', gap: 0.75 }}>
          <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
            {tr('You can apply these when you reach this stage.')}
          </Typography>
          {shown.suggestions.suggestions.length === 0 && <Typography variant="body2">{tr('No suggestions for this stage.')}</Typography>}
          {shown.suggestions.suggestions.map((card, index) => (
            <Box key={`${card.itemId}-${card.action}-${index}`} sx={{ ...surface('card', { flush: true }), p: 1.25, display: 'grid', gap: 0.5 }}
              data-testid="draft-card">
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{card.code} · {label(card)}</Typography>
              {card.newTitle && <Typography variant="body2">{tr('New title')}: {card.newTitle}</Typography>}
              {card.detail && <Typography variant="body2">{card.detail}</Typography>}
              {card.reason && <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>{card.reason}</Typography>}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
