import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { suggestHelpersAndRisks } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { DraftHelper, DraftRisk, Dream, PlanDraftContent, ResourceIdea } from '../../types/vision';
import { safeHttpUrl } from '../../utils/safeUrl';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import {
  type DraftUpdate, addHelpersAndRisks, emptyHelper, emptyRisk, helperFromSuggestion, planItems, removeHelper, removeRisk,
  riskFromSuggestion, updateHelper, updateRisk,
} from './draftModel';
import { ItemCard, Prompts, twoColumns } from './parts';
import { useAiAction } from './useAiAction';

// CLAUDE.md: which kind of helper fits which gap.
const GAPS: [string, string][] = [
  ['Knowledge', 'Mentor or expert'],
  ['Skill', 'Technical expert or trainer'],
  ['Time', 'Someone to delegate to, or an assistant'],
  ['Money', 'Financial partner or sponsor'],
  ['Motivation', 'Accountability partner'],
  ['Decision', 'Advisor'],
];

// PB-FR-25: the kind of helper that fits each kind of gap (CLAUDE.md), as the server suggests it.
const HELPER_FOR_GAP: Record<string, string> = {
  KNOWLEDGE: 'Mentor or expert',
  SKILL: 'Technical expert or trainer',
  TIME: 'Someone to delegate to, or an assistant',
  MONEY: 'Financial partner or sponsor',
  MOTIVATION: 'Accountability partner',
  DECISION: 'Advisor',
  PARTNER: 'Advisor',
  SYSTEM: 'Technical expert or trainer',
};

const SUPPORT_TYPES = ['MENTOR', 'EXPERT', 'ADVISOR', 'COLLEAGUE', 'FINANCIAL', 'TECHNICAL', 'EMOTIONAL', 'OTHER'];
const OBSTACLE_TYPES = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'MOTIVATION', 'PARTNER', 'SYSTEM', 'DECISION', 'OTHER'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const label = (value: string) => tr(value.charAt(0) + value.slice(1).toLowerCase());

type Props = {
  dream: Dream;
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  token: string;
  language: string;
  aiReady: boolean;
};

/**
 * Stage 5: who can help and what could get in the way. With AI, the plan
 * builder names kinds of helpers, matched to the participant's own partners on
 * the server (PB-FR-23), likely obstacles with the kind of helper that fits each
 * (PB-FR-24, PB-FR-25), and resources, approved ones first (PB-FR-40). The fear
 * journal is used only when ticked for this request (PB-FR-26). Without AI,
 * helpers and risks can be written by hand with the same guide.
 */
export function HelpersStage({ dream, content, onChange, token, language, aiReady }: Props) {
  const ai = useAiAction();
  const [useFears, setUseFears] = useState(false);
  const [resources, setResources] = useState<ResourceIdea[]>([]);
  const helpers = content.helpers ?? [];
  const risks = content.risks ?? [];
  const items = planItems(content);
  const targets = [{ key: '', label: tr('The whole dream') }, ...items.map((item) => ({
    key: item.key, label: item.kind === 'GOAL' ? item.title : `— ${item.title}`,
  }))];
  const linkValue = (key?: string | null) => (key && targets.some((target) => target.key === key) ? key : '');

  const suggest = () => ai.run('helpers', async () => {
    const answer = await suggestHelpersAndRisks(token, dream.id, language, {
      items, useFears, ask: { answers: content.clarifications ?? [] },
    });
    const newHelpers = answer.helpers.map((helper) => helperFromSuggestion(helper, answer.requestId));
    const newRisks = answer.risks.map((risk) => riskFromSuggestion(risk, newHelpers, answer.requestId));
    onChange((current) => addHelpersAndRisks(current, newHelpers, newRisks));
    setResources(answer.resources);
    return { requestId: answer.requestId };
  });

  return (
    <Stack spacing={2}>
      <Prompts questions={[
        'Who has already done something like this?',
        'What could block this plan?',
        'What is missing: knowledge, skill, time, money, motivation or a decision?',
      ]} />
      {aiReady && (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button disabled={ai.busy !== null || items.length === 0} onClick={() => void suggest()}>{tr('Suggest helpers and risks')}</Button>
          <FormControlLabel control={<Checkbox checked={useFears} onChange={(event) => setUseFears(event.target.checked)} />}
            label={tr('Also use my fear journal for this')} />
        </Box>
      )}
      {ai.busy && <Box><Loading /><Typography variant="caption" color="text.secondary">{tr('This can take up to 30 seconds.')}</Typography></Box>}
      {ai.error && <Alert severity="error">{ai.error}</Alert>}
      <SuggestionRating key={ai.requestId ?? 'none'} token={token} requestId={ai.requestId} />

      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr('Helpers')}</Typography>
      {helpers.length === 0 && <Typography variant="body2" color="text.secondary">{tr('No helpers yet.')}</Typography>}
      {helpers.map((helper) => (
        <HelperCard key={helper.key} helper={helper} targets={targets} linkValue={linkValue(helper.linkKey)}
          onChange={(patch) => onChange((current) => updateHelper(current, helper.key, patch))}
          onRemove={() => onChange((current) => removeHelper(current, helper.key))} />
      ))}
      <Box>
        <Button variant="secondary" onClick={() => onChange((current) => addHelpersAndRisks(current, [emptyHelper()], []))}>
          {tr('Add a helper')}
        </Button>
      </Box>

      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr('Risks')}</Typography>
      {risks.length === 0 && <Typography variant="body2" color="text.secondary">{tr('No risks yet.')}</Typography>}
      {risks.map((risk) => (
        <RiskCard key={risk.key} risk={risk} helpers={helpers} targets={targets} linkValue={linkValue(risk.linkKey)}
          onChange={(patch) => onChange((current) => updateRisk(current, risk.key, patch))}
          onRemove={() => onChange((current) => removeRisk(current, risk.key))} />
      ))}
      <Box>
        <Button variant="secondary" onClick={() => onChange((current) => addHelpersAndRisks(current, [], [emptyRisk()]))}>
          {tr('Add a risk')}
        </Button>
      </Box>

      {resources.length > 0 && (
        <Box sx={{ display: 'grid', gap: 0.5 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr('Resources')}</Typography>
          {resources.map((resource, index) => {
            const url = resource.url ? safeHttpUrl(resource.url) : null;
            return (
              <Typography key={index} variant="body2">
                {url ? <Link href={url} target="_blank" rel="noopener noreferrer">{resource.name}</Link> : resource.name}
                {resource.why ? ` — ${resource.why}` : ''}{' '}
                {resource.checkBeforeUse && <Chip size="small" variant="outlined" label={tr('Check before use')} />}
              </Typography>
            );
          })}
        </Box>
      )}

      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{tr('If you are missing')}</TableCell>
              <TableCell>{tr('Look for')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {GAPS.map(([gap, helper]) => (
              <TableRow key={gap}>
                <TableCell>{tr(gap)}</TableCell>
                <TableCell>{tr(helper)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {tr('New helpers are added to your')}{' '}
        <Link component={RouterLink} to="/partners">{tr('Partners page')}</Link>
        {' '}{tr('as "To contact", and risks to your')}{' '}
        <Link component={RouterLink} to="/obstacles">{tr('Obstacles page')}</Link>, {tr('when you add the plan.')}
      </Typography>
    </Stack>
  );
}

type Target = { key: string; label: string };

function TargetSelect({ targets, value, onChange }: { targets: Target[]; value: string; onChange: (key: string | null) => void }) {
  return (
    <TextField select size="small" label={tr('For')} value={value} onChange={(event) => onChange(event.target.value || null)}>
      {targets.map((target) => <MenuItem key={target.key || 'dream'} value={target.key}>{target.label}</MenuItem>)}
    </TextField>
  );
}

/** PB-FR-23: link a matching partner, or add a new contact to reach. */
function HelperCard({ helper, targets, linkValue, onChange, onRemove }: {
  helper: DraftHelper;
  targets: Target[];
  linkValue: string;
  onChange: (patch: Partial<DraftHelper>) => void;
  onRemove: () => void;
}) {
  const matches = helper.matches ?? [];
  const choice = helper.partnerId != null ? String(helper.partnerId) : 'new';
  const options = [
    ...matches.map((match) => ({ value: String(match.partnerId), label: tr('Link {name}', { name: `${match.name}${match.role ? ` (${match.role})` : ''}` }) })),
    ...(helper.partnerId != null && !matches.some((match) => match.partnerId === helper.partnerId)
      ? [{ value: String(helper.partnerId), label: tr('Link {name}', { name: helper.partnerName ?? tr('your partner') }) }]
      : []),
    { value: 'new', label: tr('Add as a new contact to reach') },
  ];
  return (
    <ItemCard title={helper.role} fromAi={helper.requestId != null} edited={helper.edited} onRemove={onRemove}>
      <Box sx={twoColumns}>
        <TextField size="small" label={tr('Role')} value={helper.role} onChange={(event) => onChange({ role: event.target.value })} />
        <TextField select size="small" label={tr('Kind of support')} value={helper.supportType}
          onChange={(event) => onChange({ supportType: event.target.value })}>
          {SUPPORT_TYPES.map((type) => <MenuItem key={type} value={type}>{label(type)}</MenuItem>)}
        </TextField>
        <TargetSelect targets={targets} value={linkValue} onChange={(key) => onChange({ linkKey: key })} />
        <TextField select size="small" label={tr('Who')} value={choice} onChange={(event) => {
          const value = event.target.value;
          const match = matches.find((item) => String(item.partnerId) === value);
          onChange(value === 'new'
            ? { partnerId: null, partnerName: null }
            : { partnerId: Number(value), partnerName: match?.name ?? helper.partnerName ?? null });
        }}>
          {options.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
        </TextField>
      </Box>
      {choice === 'new' && (
        <TextField size="small" label={tr('Name, or how you will find them')} value={helper.name ?? ''}
          onChange={(event) => onChange({ name: event.target.value })} />
      )}
    </ItemCard>
  );
}

/** PB-FR-24, PB-FR-25: an obstacle, the part of the plan it threatens, and who could help. */
function RiskCard({ risk, helpers, targets, linkValue, onChange, onRemove }: {
  risk: DraftRisk;
  helpers: DraftHelper[];
  targets: Target[];
  linkValue: string;
  onChange: (patch: Partial<DraftRisk>) => void;
  onRemove: () => void;
}) {
  const helperValue = risk.helperKey && helpers.some((helper) => helper.key === risk.helperKey) ? risk.helperKey : '';
  return (
    <ItemCard title={risk.title} fromAi={risk.requestId != null} edited={risk.edited} onRemove={onRemove}>
      {risk.fromFears && <Chip size="small" color="secondary" variant="outlined" label={tr('From your fear journal')} sx={{ justifySelf: 'start' }} />}
      <TextField size="small" label={tr('Risk')} value={risk.title} required onChange={(event) => onChange({ title: event.target.value })} />
      <Box sx={twoColumns}>
        <TextField select size="small" label={tr('Kind of gap')} value={risk.obstacleType}
          onChange={(event) => onChange({ obstacleType: event.target.value })}>
          {OBSTACLE_TYPES.map((type) => <MenuItem key={type} value={type}>{label(type)}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={tr('Severity')} value={risk.severity} onChange={(event) => onChange({ severity: event.target.value })}>
          {SEVERITIES.map((severity) => <MenuItem key={severity} value={severity}>{label(severity)}</MenuItem>)}
        </TextField>
        <TargetSelect targets={targets} value={linkValue} onChange={(key) => onChange({ linkKey: key })} />
        <TextField select size="small" label={tr('Who could help')} value={helperValue}
          onChange={(event) => onChange({ helperKey: event.target.value || null })}>
          <MenuItem value="">{tr('No one yet')}</MenuItem>
          {helpers.map((helper) => <MenuItem key={helper.key} value={helper.key}>{helper.partnerName ?? helper.role ?? tr('Helper')}</MenuItem>)}
        </TextField>
      </Box>
      <TextField size="small" label={tr('First way to handle it')} value={risk.solution ?? ''} multiline
        onChange={(event) => onChange({ solution: event.target.value })} />
      {HELPER_FOR_GAP[risk.obstacleType] && (
        <Typography variant="caption" color="text.secondary">
          {tr('Who fits this kind of gap: {helper}', { helper: tr(HELPER_FOR_GAP[risk.obstacleType]) })}
        </Typography>
      )}
    </ItemCard>
  );
}
