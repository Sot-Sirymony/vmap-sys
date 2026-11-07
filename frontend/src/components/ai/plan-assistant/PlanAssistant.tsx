import { type ReactNode, createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { Bot, LifeBuoy, X } from 'lucide-react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Drawer from '@mui/material/Drawer';
import Fab from '@mui/material/Fab';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { getAiStatus, listAiConversations } from '../../../api/aiApi';
import {
  applyAssistantChange, checkAssistantSection, proposeAssistantChanges, recordPlanOutcome, saveClarifiedField,
} from '../../../api/aiPlanApi';
import { useI18n } from '../../../i18n/I18nContext';
import { tr } from '../../../i18n/translate';
import type {
  AiStatus, AssistantChange, AssistantProposals, AssistantSection, AssistantSurface, ClarifyField, Dream, SectionCheck,
} from '../../../types/vision';
import { AiConsentCard } from '../../common/AiConsentCard';
import { Button } from '../../common/Button';
import { AiStatusNotice } from '../AiStatusNotice';
import { DreamCoachChat } from '../DreamCoachChat';
import { SuggestionRating } from '../SuggestionRating';
import { ChangeCard } from './ChangeCard';
import { SECTION_HELP } from './sectionHelp';

/** What the assistant is helping with: a section, and optionally one item in it. */
export type AssistantTopic = { section: AssistantSection; itemRef?: string | null; itemTitle?: string | null };

type ContextValue = { openFor: (topic: AssistantTopic) => void };

const PlanAssistantContext = createContext<ContextValue | null>(null);

const DREAM_FIELDS: Record<string, ClarifyField> = { WHY: 'WHY_IMPORTANT', SUCCESS: 'SUCCESS_DEFINITION', DATE: 'TARGET_DATE' };

type Props = {
  token: string;
  surface: AssistantSurface;
  dreamId: number;
  /** The section the page is on; the assistant follows it unless the participant keeps the topic (PB-FR-43). */
  current: AssistantTopic;
  /** Changes whenever the saved plan or draft changes, so the section check is read again. */
  version?: number;
  /** Build my plan: apply a card to the draft. */
  onDraftChange?: (change: AssistantChange) => void;
  /** Build my plan: a card changed the dream itself. */
  onDreamSaved?: (dream: Dream) => void;
  /** Plan page: a card was saved to the plan. */
  onPlanChanged?: () => void;
  children: ReactNode;
};

/**
 * V11 Phase 7 (PB-FR-41 to PB-FR-50): the plan assistant on the Plan page and
 * Build my plan. Wraps the page; the panel opens beside it on wide screens and
 * as a full-screen panel on smaller ones. It shows the program's help and the
 * section's rule-based check (both work without AI), a chat that knows only
 * this section, and change cards the participant applies or dismisses.
 * On these pages it takes the place of the Dream Coach button.
 */
export function PlanAssistantProvider({
  token, surface, dreamId, current, version = 0, onDraftChange, onDreamSaved, onPlanChanged, children,
}: Props) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('lg'));
  const [open, setOpen] = useState(false);
  const [topic, setTopic] = useState<AssistantTopic>(current);
  const [keepTopic, setKeepTopic] = useState(false);
  const returnFocus = useRef<HTMLElement | null>(null);
  const keepTopicRef = useRef(keepTopic);
  keepTopicRef.current = keepTopic;

  // PB-FR-43: follow the page's section unless the participant keeps the earlier topic.
  useEffect(() => {
    if (!keepTopicRef.current) {
      setTopic(current);
    }
  }, [current.section, current.itemRef]); // eslint-disable-line react-hooks/exhaustive-deps

  const openFor = useCallback((next: AssistantTopic) => {
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setTopic(next);
    setKeepTopic(false);
    setOpen(true);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    // PB-NFR-13: back to the button that opened the panel.
    window.setTimeout(() => returnFocus.current?.focus?.(), 0);
  }, []);

  const value = useMemo(() => ({ openFor }), [openFor]);

  const panel = (
    <AssistantPanel
      token={token} surface={surface} dreamId={dreamId} topic={topic} version={version}
      keepTopic={keepTopic} onKeepTopic={setKeepTopic} onClose={close}
      onDraftChange={onDraftChange} onDreamSaved={onDreamSaved} onPlanChanged={onPlanChanged}
    />
  );

  return (
    <PlanAssistantContext.Provider value={value}>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>{children}</Box>
        {open && wide && (
          <Box component="aside" aria-label={tr('Plan assistant')}
            sx={{ width: 400, flexShrink: 0, position: 'sticky', top: 80, maxHeight: 'calc(100vh - 96px)', overflowY: 'auto' }}>
            <Card><CardContent>{panel}</CardContent></Card>
          </Box>
        )}
      </Box>
      {!wide && (
        <Drawer anchor="right" open={open} onClose={close}
          slotProps={{ paper: { sx: { width: '100%', maxWidth: 560, p: 2 }, 'aria-label': tr('Plan assistant') } }}>
          {panel}
        </Drawer>
      )}
      {!open && (
        <Fab color="primary" aria-label={tr('Open the plan assistant')} onClick={() => openFor(current)}
          sx={{ position: 'fixed', right: { xs: 16, md: 24 }, bottom: { xs: 88, md: 24 }, zIndex: (muiTheme) => muiTheme.zIndex.modal + 1 }}>
          <Bot size={24} />
        </Fab>
      )}
    </PlanAssistantContext.Provider>
  );
}

/** PB-FR-42: opens the plan assistant focused on one section. Renders nothing outside a PlanAssistantProvider. */
export function HelpWithSectionButton({ section, itemRef, itemTitle }: AssistantTopic) {
  const context = useContext(PlanAssistantContext);
  if (!context) {
    return null;
  }
  return (
    <MuiButton size="small" variant="text" startIcon={<LifeBuoy size={16} />}
      aria-label={tr('Help with {section}', { section: tr(SECTION_HELP[section].label) })}
      onClick={() => context.openFor({ section, itemRef, itemTitle })}>
      {tr('Help with this section')}
    </MuiButton>
  );
}

type PanelProps = {
  token: string;
  surface: AssistantSurface;
  dreamId: number;
  topic: AssistantTopic;
  version: number;
  keepTopic: boolean;
  onKeepTopic: (keep: boolean) => void;
  onClose: () => void;
  onDraftChange?: (change: AssistantChange) => void;
  onDreamSaved?: (dream: Dream) => void;
  onPlanChanged?: () => void;
};

type ProposedCard = AssistantChange & { id: number };

function AssistantPanel({
  token, surface, dreamId, topic, version, keepTopic, onKeepTopic, onClose, onDraftChange, onDreamSaved, onPlanChanged,
}: PanelProps) {
  const { language } = useI18n();
  const { pathname } = useLocation();
  const help = SECTION_HELP[topic.section];
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [check, setCheck] = useState<SectionCheck | null>(null);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [proposals, setProposals] = useState<(AssistantProposals & { cards: ProposedCard[] }) | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [applied, setApplied] = useState('');
  const headingRef = useRef<HTMLHeadingElement>(null);
  const label = tr(help.label);

  useEffect(() => {
    headingRef.current?.focus();
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token]);

  // PB-FR-49: the section's own problems, read again when the topic or the saved plan changes.
  useEffect(() => {
    let cancelled = false;
    checkAssistantSection(token, dreamId, { surface, section: topic.section, itemRef: topic.itemRef ?? null })
      .then((value) => !cancelled && setCheck(value))
      .catch(() => !cancelled && setCheck(null));
    return () => {
      cancelled = true;
    };
  }, [token, dreamId, surface, topic.section, topic.itemRef, version]);

  // A new topic starts with no cards, and picks up its own conversation (PB-FR-50).
  useEffect(() => {
    setProposals(null);
    setError('');
    setApplied('');
    setConversationId(null);
    if (!status?.available) {
      return undefined;
    }
    let cancelled = false;
    listAiConversations(token, { dreamId, section: topic.section })
      .then((list) => !cancelled && setConversationId(list[0]?.id ?? null))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token, dreamId, topic.section, topic.itemRef, status?.available]);

  async function suggest() {
    setBusy(true);
    setError('');
    setApplied('');
    try {
      const answer = await proposeAssistantChanges(token, dreamId, language, { surface, section: topic.section, itemRef: topic.itemRef ?? null });
      setProposals({ ...answer, cards: answer.changes.map((change, index) => ({ ...change, id: index })) });
      if (status) {
        setStatus({ ...status, messagesLeftToday: Math.max(0, status.messagesLeftToday - 1) });
      }
    } catch (suggestError) {
      setError(suggestError instanceof Error ? tr(suggestError.message) : tr('AI help is temporarily unavailable.'));
      getAiStatus(token).then(setStatus).catch(() => undefined);
    } finally {
      setBusy(false);
    }
  }

  async function apply(card: ProposedCard, change: AssistantChange, edited: boolean) {
    const requestId = proposals?.requestId ?? null;
    if (surface === 'BUILDER') {
      if (change.kind === 'EDIT' && change.itemType === 'DREAM' && change.field) {
        const dream = await saveClarifiedField(token, dreamId, DREAM_FIELDS[change.field], change.value ?? '');
        onDreamSaved?.(dream);
      } else {
        onDraftChange?.(change);
      }
      if (requestId) {
        // Counts only; a failure here must not undo what the participant chose.
        recordPlanOutcome(token, requestId, edited ? 0 : 1, edited ? 1 : 0).catch(() => undefined);
      }
      setApplied(tr('Applied to your draft.'));
    } else {
      await applyAssistantChange(token, dreamId, change, requestId, edited);
      onPlanChanged?.();
      setApplied(tr('Saved to your plan.'));
    }
    dismiss(card.id);
  }

  function dismiss(id: number) {
    setProposals((current) => (current ? { ...current, cards: current.cards.filter((card) => card.id !== id) } : current));
  }

  const aiOn = Boolean(status?.available);
  const topicLabel = topic.itemTitle ? `${label}: ${topic.itemTitle}` : label;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Typography variant="h6" component="h2" tabIndex={-1} ref={headingRef} sx={{ outline: 'none' }}>{tr('Plan assistant')}</Typography>
        <IconButton aria-label={tr('Close')} onClick={onClose}><X size={18} /></IconButton>
      </Box>
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('Helping with: {topic}', { topic: topicLabel })}</Typography>
        <FormControlLabel
          control={<Checkbox size="small" checked={keepTopic} onChange={(event) => onKeepTopic(event.target.checked)} />}
          label={<Typography variant="caption">{tr('Keep this topic when I move to another section')}</Typography>}
        />
      </Box>

      <Box component="section" aria-label={tr('What this section needs')}>
        <Typography variant="subtitle2" component="h3">{tr('What this section needs')}</Typography>
        <Typography variant="body2">{tr(help.needs)}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{tr(help.why)}</Typography>
        <Typography variant="subtitle2" component="h3" sx={{ mt: 1 }}>{tr('Questions to think about')}</Typography>
        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
          {help.questions.map((question) => <li key={question}><Typography variant="body2">{tr(question)}</Typography></li>)}
        </Box>
      </Box>

      <Box component="section" aria-label={tr('Plan check for this section')}>
        <Typography variant="subtitle2" component="h3">{tr('Plan check for this section')}</Typography>
        {check === null && <Typography variant="body2" color="text.secondary">{tr('Checking…')}</Typography>}
        {check && check.issues.length === 0 && (
          <Typography variant="body2" color="text.secondary">{tr('No problems found in this section.')}</Typography>
        )}
        {check && check.issues.length > 0 && (
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {check.issues.slice(0, 10).map((issue) => (
              <li key={`${issue.kind}-${issue.itemRef}`}>
                <Typography variant="body2">
                  {issue.itemTitle ? <strong>{issue.itemTitle}: </strong> : null}{tr(issue.problem)}
                  {issue.fix ? ` ${tr(issue.fix)}` : ''}
                </Typography>
              </li>
            ))}
          </Box>
        )}
      </Box>

      {status?.reason === 'NO_CONSENT' && <AiConsentCard token={token} onChange={setStatus} />}
      {status && <AiStatusNotice status={status} />}
      {status && !aiOn && status.reason !== 'NO_CONSENT' && (
        <Alert severity="info">{tr('AI help is not available right now. The help and plan check above still work.')}</Alert>
      )}

      {aiOn && check?.canPropose && (
        <Box component="section" aria-label={tr('Suggested changes')}>
          <Button variant="secondary" disabled={busy} onClick={() => void suggest()}>
            {busy ? tr('The Dream Coach is thinking…') : tr('Suggest changes')}
          </Button>
          {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
          <Box aria-live="polite">
            {applied && <Alert severity="success" sx={{ mt: 1 }}>{applied}</Alert>}
            {proposals && (
              <Stack spacing={1} sx={{ mt: 1 }}>
                {proposals.note && <Typography variant="body2">{proposals.note}</Typography>}
                {proposals.changes.length === 0 && !proposals.note && (
                  <Typography variant="body2" color="text.secondary">{tr('No changes to suggest for this section.')}</Typography>
                )}
                {proposals.cards.map((card) => (
                  <ChangeCard key={card.id} change={card}
                    applyLabel={surface === 'BUILDER' ? tr('Apply to draft') : tr('Save to my plan')}
                    onApply={(change, edited) => apply(card, change, edited)}
                    onDismiss={() => dismiss(card.id)} />
                ))}
                <SuggestionRating token={token} requestId={proposals.requestId} />
              </Stack>
            )}
          </Box>
        </Box>
      )}

      {aiOn && status && (
        <Box component="section" aria-label={tr('Ask about this section')}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle2" component="h3">{tr('Ask about this section')}</Typography>
            {conversationId !== null && (
              <Button variant="secondary" onClick={() => setConversationId(null)}>{tr('New conversation')}</Button>
            )}
          </Box>
          <DreamCoachChat
            token={token}
            status={status}
            onStatusChange={setStatus}
            conversationId={conversationId}
            onConversationChange={setConversationId}
            onNewConversation={() => setConversationId(null)}
            focus={{ page: pathname, dreamId, surface, section: topic.section, itemRef: topic.itemRef ?? null }}
            quickActions={help.quickActions.map((action) => tr(action))}
          />
        </Box>
      )}
    </Stack>
  );
}
