import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import type { SavedReportCommentary } from '../../types/vision';
import { Button } from '../common/Button';
import { Textarea } from '../common/Textarea';
import { tr } from '../../i18n/translate';

export type CommentarySection = 'highlights' | 'risks' | 'decisionsNeeded' | 'nextFocus';
export type SaveState = 'idle' | 'saving' | 'saved' | 'error';

export const SECTION_LABELS: Record<CommentarySection, string> = {
  highlights: 'Highlights',
  risks: 'Risks and issues',
  decisionsNeeded: 'Decisions and support needed',
  nextFocus: 'Focus for next period',
};

const SECTIONS = Object.keys(SECTION_LABELS) as CommentarySection[];
export const MAX_SECTION = 2000;
export const MAX_GOAL_NOTE = 500;

/**
 * V19 RM-FR-26 to RM-FR-31: the participant's own words on a report. Editable
 * on a draft (saved as they type, RM-FR-27), read-only on a final report.
 * Lines the rule-based draft filled in are marked "Suggested" until edited.
 */
export function ReportCommentaryCard({ commentary, goals, editable, onChange, onBlur, onSuggest, suggested, saveState, suggesting }: Readonly<{
  commentary: SavedReportCommentary;
  goals: { goalId: number; goalTitle: string }[];
  editable: boolean;
  onChange?: (next: SavedReportCommentary, edited?: CommentarySection) => void;
  /** Save at once when a field loses focus (RM-FR-27). */
  onBlur?: () => void;
  onSuggest?: () => void;
  suggested?: Set<CommentarySection>;
  saveState?: SaveState;
  suggesting?: boolean;
}>) {
  const notes = commentary.goalNotes ?? {};

  if (!editable) {
    const filled = SECTIONS.filter((section) => commentary[section]?.trim() || (section === 'decisionsNeeded' && commentary.nothingNeeded));
    const goalNotes = goals.filter((goal) => notes[String(goal.goalId)]);
    return (
      <Card component="section" aria-label={tr('Commentary')}>
        <CardHeader title={tr('Commentary')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }} />
        <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
          {filled.length === 0 && goalNotes.length === 0 && <Typography variant="body2">{tr('No commentary was written for this report.')}</Typography>}
          {filled.map((section) => (
            <Box key={section}>
              <Typography variant="subtitle2" component="h4">{tr(SECTION_LABELS[section])}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
                {section === 'decisionsNeeded' && commentary.nothingNeeded ? tr('Nothing needed this period.') : commentary[section]}
              </Typography>
            </Box>
          ))}
          {goalNotes.map((goal) => (
            <Typography variant="body2" key={goal.goalId}><strong>{goal.goalTitle}:</strong> {notes[String(goal.goalId)]}</Typography>
          ))}
        </CardContent>
      </Card>
    );
  }

  function setSection(section: CommentarySection, value: string) {
    onChange?.({ ...commentary, [section]: value }, section);
  }

  const status = {
    idle: '',
    saving: tr('Saving…'),
    saved: tr('Saved'),
    error: tr('Not saved, retry'),
  }[saveState ?? 'idle'];

  return (
    <Card component="section" aria-label={tr('Commentary')}>
      <CardHeader
        title={tr('Commentary')}
        titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
        subheader={tr('Your own words for the reader. Use "- " at the start of a line for a bullet.')}
        action={(
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            {status && (
              <Typography variant="caption" role="status" color={saveState === 'error' ? 'error' : 'text.secondary'}>{status}</Typography>
            )}
            <Button variant="secondary" onClick={onSuggest} disabled={suggesting}>
              {suggesting ? tr('Preparing…') : tr('Suggest a draft')}
            </Button>
          </Box>
        )}
      />
      <CardContent sx={{ pt: 0, display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        {SECTIONS.map((section) => (
          <Box key={section} sx={{ display: 'grid', gap: 0.5 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography component="label" htmlFor={`commentary-${section}`} variant="subtitle2">{tr(SECTION_LABELS[section])}</Typography>
              {suggested?.has(section) && <Chip size="small" label={tr('Suggested')} />}
            </Box>
            {section === 'decisionsNeeded' && (
              <label className="checkbox-field">
                <Checkbox checked={commentary.nothingNeeded} onChange={(event) => onChange?.({ ...commentary, nothingNeeded: event.target.checked })} />
                {tr('Nothing needed this period')}
              </label>
            )}
            <Textarea id={`commentary-${section}`} rows={4} value={commentary[section] ?? ''} maxLength={MAX_SECTION}
              disabled={section === 'decisionsNeeded' && commentary.nothingNeeded}
              onChange={(event) => setSection(section, event.target.value)} onBlur={onBlur} />
          </Box>
        ))}
        {goals.length > 0 && (
          <Box sx={{ gridColumn: '1 / -1', display: 'grid', gap: 1 }}>
            <Typography variant="subtitle2" component="h4">{tr('A note per goal (optional)')}</Typography>
            {goals.map((goal) => (
              <Box key={goal.goalId} sx={{ display: 'grid', gap: 0.5 }}>
                <Typography component="label" htmlFor={`goal-note-${goal.goalId}`} variant="body2">{goal.goalTitle}</Typography>
                <Textarea id={`goal-note-${goal.goalId}`} rows={2} maxLength={MAX_GOAL_NOTE} value={notes[String(goal.goalId)] ?? ''}
                  onChange={(event) => onChange?.({ ...commentary, goalNotes: { ...notes, [String(goal.goalId)]: event.target.value } })}
                  onBlur={onBlur} />
              </Box>
            ))}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
