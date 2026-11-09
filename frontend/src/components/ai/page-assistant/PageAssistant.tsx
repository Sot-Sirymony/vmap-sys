import { type ReactNode, createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { Bot, LifeBuoy, X } from 'lucide-react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
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
import { checkPageArea, proposePageChanges, recordPageOutcome } from '../../../api/aiPageApi';
import { useAuth } from '../../../context/AuthContext';
import { useI18n } from '../../../i18n/I18nContext';
import { tr } from '../../../i18n/translate';
import type { AiFocus, AiStatus, AssistantPage, PageCheck, PageChange, PageProposals } from '../../../types/vision';
import { AiConsentCard } from '../../common/AiConsentCard';
import { Button } from '../../common/Button';
import { AiStatusNotice } from '../AiStatusNotice';
import { DreamCoachChat } from '../DreamCoachChat';
import { SuggestionRating } from '../SuggestionRating';
import { aiOnFor, basedOn } from '../aiFocus';
import { PageChangeCard } from './PageChangeCard';
import { MESSAGE_FIELD_LABELS, OBSTACLE_FIELD_LABELS, PAGE_LABELS, helpFor } from './pageHelp';

/** What the assistant is helping with: an area of the page, and optionally one item. */
export type PageTopic = { area: string; itemId?: number | null; title?: string | null };

/**
 * AH-FR-05, AH-FR-06: the form on screen. read gives what the participant has
 * typed but not saved; apply fills one field when a card is applied.
 */
export type PageFormSource = {
  itemId?: number | null;
  read: () => Record<string, string>;
  apply: (field: string, value: string) => void;
  /** A label for a field the page names itself, such as a module's exercise fields. */
  fieldLabel?: (field: string) => string | undefined;
};

type ContextValue = { openFor: (topic: PageTopic) => void };

const PageAssistantContext = createContext<ContextValue | null>(null);

type Props = {
  page: AssistantPage;
  /** The item the whole page is about, such as the module number on a module page. */
  itemId?: number | null;
  /** The area the participant is working on; the panel follows it unless they keep the topic (AH-FR-03). */
  current?: PageTopic | null;
  /** The open form, or null when no form is open. */
  form?: PageFormSource | null;
  children: ReactNode;
};

const PAGE_TOPIC: PageTopic = { area: 'page' };

/** V15 (AH-FR-11): the conversation tag for a page and item, the same one the server uses. */
export function pageSection(page: AssistantPage, itemId?: number | null) {
  return `PAGE:${page}${itemId === null || itemId === undefined ? '' : `:${itemId}`}`;
}

/**
 * V15 Phase 1 (AH-FR-01 to AH-FR-12): the page assistant on the Modules,
 * Assessment, Obstacles, Communication and Work Style pages. Wraps the page.
 * The panel opens beside the page on wide screens, over any open form so the
 * form stays in view, and full screen on a phone. It shows the program's own
 * help and the area's rule checks (both work without AI), change cards that
 * only fill the form, and a chat that knows the page, item and unsaved form.
 * On these pages it takes the place of the Dream Coach button.
 */
export function PageAssistantProvider({ page, itemId = null, current = null, form = null, children }: Readonly<Props>) {
  const { token } = useAuth();
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('sm'));
  const [open, setOpen] = useState(false);
  const [topic, setTopic] = useState<PageTopic>(current ?? PAGE_TOPIC);
  const [keepTopic, setKeepTopic] = useState(false);
  const returnFocus = useRef<HTMLElement | null>(null);
  const formRef = useRef<PageFormSource | null>(form);
  formRef.current = form;
  const keepTopicRef = useRef(keepTopic);
  keepTopicRef.current = keepTopic;

  // AH-FR-03: follow the participant to another item unless they keep the earlier topic.
  useEffect(() => {
    if (!keepTopicRef.current) {
      setTopic(current ?? PAGE_TOPIC);
    }
  }, [current?.area, current?.itemId]); // eslint-disable-line react-hooks/exhaustive-deps

  const openFor = useCallback((next: PageTopic) => {
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setTopic(next);
    setKeepTopic(false);
    setOpen(true);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    // AH-NFR-09: back to the button that opened the panel.
    window.setTimeout(() => returnFocus.current?.focus?.(), 0);
  }, []);

  const value = useMemo(() => ({ openFor }), [openFor]);

  return (
    <PageAssistantContext.Provider value={value}>
      {children}
      {token && (
        <>
          <Drawer
            anchor="right"
            open={open}
            onClose={close}
            // Over any open form dialog. On wide screens there is no backdrop, so the form stays in view beside it.
            hideBackdrop={wide}
            sx={{ zIndex: (muiTheme) => muiTheme.zIndex.modal + 2 }}
            slotProps={{ paper: { sx: { width: { xs: '100%', sm: 440 }, p: 2, boxShadow: 8 }, 'aria-label': tr('Page assistant') } }}
          >
            {open && (
              <AssistantPanel token={token} page={page} pageItemId={itemId} topic={topic} formRef={formRef}
                keepTopic={keepTopic} onKeepTopic={setKeepTopic} onClose={close} />
            )}
          </Drawer>
          {!open && (
            <Fab color="primary" aria-label={tr('Open the page assistant')} onClick={() => openFor(current ?? PAGE_TOPIC)}
              sx={{ position: 'fixed', right: { xs: 16, md: 24 }, bottom: { xs: 88, md: 24 }, zIndex: (muiTheme) => muiTheme.zIndex.modal + 1 }}>
              <Bot size={24} />
            </Fab>
          )}
        </>
      )}
    </PageAssistantContext.Provider>
  );
}

/** AH-FR-02: the page header's Help button. Renders nothing outside a PageAssistantProvider. */
export function PageHelpButton() {
  const context = useContext(PageAssistantContext);
  if (!context) {
    return null;
  }
  return (
    <MuiButton variant="outlined" startIcon={<LifeBuoy size={16} />} onClick={() => context.openFor(PAGE_TOPIC)}
      sx={{ minHeight: 40 }}>
      {tr('Help')}
    </MuiButton>
  );
}

/** AH-FR-02: opens the assistant focused on one area. Renders nothing outside a PageAssistantProvider. */
export function HelpWithThis({ area, itemId, title, label, compact = false }: Readonly<PageTopic & { label?: string; compact?: boolean }>) {
  const context = useContext(PageAssistantContext);
  if (!context) {
    return null;
  }
  const ariaLabel = tr('Help with {topic}', { topic: label ?? title ?? tr('this') });
  const handleClick = (event: { preventDefault: () => void; stopPropagation: () => void }) => {
    // A Help button inside a clickable row or label must not also trigger it.
    event.preventDefault();
    event.stopPropagation();
    context.openFor({ area, itemId, title: title ?? label });
  };
  if (compact) {
    // Table rows: an icon only, still at least 40 px to tap (AH-NFR-10).
    return (
      <IconButton size="small" aria-label={ariaLabel} title={tr('Help with this')} onClick={handleClick} sx={{ minWidth: 40, minHeight: 40 }}>
        <LifeBuoy size={16} />
      </IconButton>
    );
  }
  return (
    <MuiButton size="small" variant="text" startIcon={<LifeBuoy size={14} />} aria-label={ariaLabel} onClick={handleClick}
      sx={{ minHeight: 40, alignSelf: 'flex-start' }}>
      {tr('Help with this')}
    </MuiButton>
  );
}

type PanelProps = {
  token: string;
  page: AssistantPage;
  pageItemId: number | null;
  topic: PageTopic;
  formRef: { current: PageFormSource | null };
  keepTopic: boolean;
  onKeepTopic: (keep: boolean) => void;
  onClose: () => void;
};

type ShownCard = PageChange & { id: number };

function AssistantPanel({ token, page, pageItemId, topic, formRef, keepTopic, onKeepTopic, onClose }: Readonly<PanelProps>) {
  const { language } = useI18n();
  const { pathname } = useLocation();
  const help = helpFor(page, topic.area);
  const itemId = topic.itemId ?? formRef.current?.itemId ?? pageItemId;
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [check, setCheck] = useState<PageCheck | null>(null);
  const [checkFailed, setCheckFailed] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [proposals, setProposals] = useState<(PageProposals & { cards: ShownCard[] }) | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [applied, setApplied] = useState('');
  const headingRef = useRef<HTMLHeadingElement>(null);

  const request = useCallback(() => ({
    page,
    area: topic.area,
    itemId: itemId ?? null,
    form: formRef.current?.read() ?? {},
  }), [page, topic.area, itemId, formRef]);

  useEffect(() => {
    // AH-NFR-09: focus moves into the panel when it opens.
    headingRef.current?.focus();
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token]);

  const runCheck = useCallback(() => {
    setCheckFailed(false);
    checkPageArea(token, request()).then(setCheck).catch(() => {
      setCheck(null);
      setCheckFailed(true);
    });
  }, [token, request]);

  // AH-FR-10: the area's own checks, read again when the topic changes.
  useEffect(() => {
    runCheck();
  }, [runCheck]);

  // A new topic starts with no cards, and picks up its own conversation (AH-FR-11).
  useEffect(() => {
    setProposals(null);
    setError('');
    setApplied('');
    setConversationId(null);
    if (!status?.available) {
      return undefined;
    }
    let cancelled = false;
    listAiConversations(token, { pageSection: pageSection(page, itemId) })
      .then((list) => !cancelled && setConversationId(list[0]?.id ?? null))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token, page, itemId, topic.area, status?.available]);

  async function suggest() {
    setBusy(true);
    setError('');
    setApplied('');
    try {
      const answer = await proposePageChanges(token, language, request());
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

  function apply(card: ShownCard, value: string, edited: boolean) {
    const source = formRef.current;
    if (!source) {
      setError(tr('Open the form first, then apply the suggestion.'));
      return;
    }
    // AH-FR-06: fills the form only; the page's own Save stores it.
    source.apply(card.field, value);
    if (proposals?.requestId) {
      // Counts only; a failure here must not undo what the participant chose.
      recordPageOutcome(token, proposals.requestId, edited ? 0 : 1, edited ? 1 : 0).catch(() => undefined);
    }
    setApplied(tr('Added to the form. Nothing is saved until you press Save.'));
    dismiss(card.id);
  }

  function dismiss(id: number) {
    setProposals((current) => (current ? { ...current, cards: current.cards.filter((card) => card.id !== id) } : current));
  }

  function fieldLabel(field: string) {
    const label = formRef.current?.fieldLabel?.(field) ?? MESSAGE_FIELD_LABELS[field] ?? OBSTACLE_FIELD_LABELS[field];
    return label ? tr(label) : field;
  }

  const focus = useCallback((): AiFocus => ({
    page: pathname,
    assistantPage: page,
    area: topic.area,
    itemId: itemId ?? null,
    form: formRef.current?.read() ?? {},
  }), [pathname, page, topic.area, itemId, formRef]);

  // AH-FR-57: off when an admin has switched this page off, for everyone or for the person's cohort.
  const aiOn = aiOnFor(status, page);
  const pageOff = Boolean(status?.available) && !aiOn;
  const topicLabel = topic.title ? `${tr(help.label)}: ${topic.title}` : `${tr(PAGE_LABELS[page])} – ${tr(help.label)}`;
  const reason = proposals ? basedOn(proposals.sources) : null;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Typography variant="h6" component="h2" tabIndex={-1} ref={headingRef} sx={{ outline: 'none' }}>{tr('Page assistant')}</Typography>
        <IconButton aria-label={tr('Close')} onClick={onClose} sx={{ minWidth: 40, minHeight: 40 }}><X size={18} /></IconButton>
      </Box>
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('Helping with: {topic}', { topic: topicLabel })}</Typography>
        <FormControlLabel
          control={<Checkbox size="small" checked={keepTopic} onChange={(event) => onKeepTopic(event.target.checked)} />}
          label={<Typography variant="caption">{tr('Keep this topic when I open something else')}</Typography>}
        />
      </Box>

      <Box component="section" aria-label={tr('What this needs')}>
        <Typography variant="subtitle2" component="h3">{tr('What this needs')}</Typography>
        <Typography variant="body2">{tr(help.needs)}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{tr(help.why)}</Typography>
        <Typography variant="subtitle2" component="h3" sx={{ mt: 1 }}>{tr('Questions to think about')}</Typography>
        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
          {help.questions.map((question) => <li key={question}><Typography variant="body2">{tr(question)}</Typography></li>)}
        </Box>
      </Box>

      {check && check.issues.length > 0 && (
        <Box component="section" aria-label={tr('Checks')}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="subtitle2" component="h3">{tr('Checks')}</Typography>
            <MuiButton size="small" onClick={runCheck}>{tr('Check again')}</MuiButton>
          </Box>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {check.issues.map((issue) => (
              <li key={`${issue.field}-${issue.problem}`}>
                <Typography variant="body2">{tr(issue.problem)}{issue.fix ? ` ${tr(issue.fix)}` : ''}</Typography>
              </li>
            ))}
          </Box>
        </Box>
      )}
      {check && check.issues.length === 0 && check.canPropose && (
        <Typography variant="body2" color="text.secondary">{tr('No problems found.')}</Typography>
      )}
      {checkFailed && <Alert severity="warning">{tr('The checks could not be loaded.')}</Alert>}

      {status?.reason === 'NO_CONSENT' && <AiConsentCard token={token} onChange={setStatus} />}
      {status && <AiStatusNotice status={status} />}
      {status && !aiOn && status.reason !== 'NO_CONSENT' && (
        <Alert severity="info">
          {pageOff
            ? tr('AI help is switched off on this page. The help and checks above still work.')
            : tr('AI help is not available right now. The help and checks above still work.')}
        </Alert>
      )}

      {aiOn && check?.canPropose && (
        <Box component="section" aria-label={tr('Suggested changes')}>
          <Button variant="secondary" disabled={busy} onClick={() => void suggest()}>
            {busy ? tr('The Dream Coach is thinking…') : tr('Suggest changes')}
          </Button>
          {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
          {/* AH-NFR-09: new answers are announced. */}
          <Box aria-live="polite">
            {applied && <Alert severity="success" sx={{ mt: 1 }}>{applied}</Alert>}
            {proposals && (
              <Stack spacing={1} sx={{ mt: 1 }}>
                {proposals.note && <Typography variant="body2">{proposals.note}</Typography>}
                {proposals.changes.length === 0 && !proposals.note && (
                  <Typography variant="body2" color="text.secondary">{tr('No changes to suggest here.')}</Typography>
                )}
                {proposals.cards.map((card) => (
                  <PageChangeCard key={card.id} change={card} label={fieldLabel(card.field)}
                    onApply={(value, edited) => apply(card, value, edited)} onDismiss={() => dismiss(card.id)} />
                ))}
                {reason && <Typography variant="caption" color="text.secondary">{reason}</Typography>}
                <SuggestionRating token={token} requestId={proposals.requestId} />
              </Stack>
            )}
          </Box>
        </Box>
      )}

      {aiOn && status && (
        <Box component="section" aria-label={tr('Ask about this')}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle2" component="h3">{tr('Ask about this')}</Typography>
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
            focus={focus}
            quickActions={help.quickActions.map((action) => tr(action))}
          />
        </Box>
      )}
    </Stack>
  );
}
