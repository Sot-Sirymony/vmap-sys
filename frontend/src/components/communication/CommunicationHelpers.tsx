import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { checkPageArea, recordPageOutcome } from '../../api/aiPageApi';
import { communicationHelp } from '../../api/communicationHelpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type {
  CommunicationHelpAnswer, CommunicationHelpMode, CommunicationStatus, MeetingTask, MessageToneChange, NewMessageDraft, PageChange, PageIssue,
  PartnerMotivator, WorkStyleArchetype,
} from '../../types/vision';
import { followUpTemplate, thankYouTemplate } from '../../utils/messageTemplates';
import { ARCHETYPE_GUIDANCE, workStyleArchetypeLabels } from '../../utils/workStyleAssessment';
import { MOTIVATOR_TIPS } from '../../utils/workStyleContent';
import { localDateIso } from '../../utils/localDate';
import { Button } from '../common/Button';
import { PitchCoachCard } from '../ai/PitchCoachCard';
import { SuggestionRating } from '../ai/SuggestionRating';
import { PageChangeCard } from '../ai/page-assistant/PageChangeCard';
import { MESSAGE_FIELD_LABELS } from '../ai/page-assistant/pageHelp';

const REPLY_KINDS: Record<'YES' | 'NO' | 'QUESTION' | 'LATER', string> = {
  YES: 'A yes', NO: 'A no', QUESTION: 'A question', LATER: 'Later',
};

const TONES: { value: MessageToneChange; label: string }[] = [
  { value: 'SHORTER', label: 'Make it shorter' },
  { value: 'WARMER', label: 'Make it warmer' },
  { value: 'FORMAL', label: 'Make it more formal' },
  { value: 'KHMER', label: 'In Khmer' },
  { value: 'ENGLISH', label: 'In English' },
];

type Props = {
  token: string;
  /** The saved message being edited, or null for a new one. */
  messageId: number | null;
  status: CommunicationStatus;
  partnerName: string | null;
  /** Progress of the linked task or goal, for the thank-you template ("Concept note is now 80% done."). */
  progressText: string;
  /** The unsaved form, as the page assistant reads it. */
  read: () => Record<string, string>;
  /** Fills one form field; the page's own Save stores it. */
  apply: (field: string, value: string) => void;
  /** The program's message template (Generate message), used to rebuild the body without AI. */
  onRebuildWithTemplate: () => void;
  /** Opens the create form with a new message to the same partner. */
  onNewMessage: (draft: NewMessageDraft) => void;
  /** AH-FR-59: the chosen partner's work style and primary motivator, as the participant recorded them. */
  partnerStyle?: WorkStyleArchetype | null;
  partnerMotivator?: PartnerMotivator | null;
};

/**
 * V15 Phase 3 (AH-FR-44 to AH-FR-54, AH-FR-62 to AH-FR-65): helpers in the
 * message form. Check my message, Rebuild message, follow-up and thank-you work
 * without AI (rules and program templates); the rest need AI and are hidden
 * when it is off. Nothing is saved or sent: cards fill the form, a follow-up or
 * thank-you opens as a new message, and the meeting plan opens the task form.
 */
export function CommunicationHelpers({
  token, messageId, status, partnerName, progressText, read, apply, onRebuildWithTemplate, onNewMessage, partnerStyle = null,
  partnerMotivator = null,
}: Readonly<Props>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const [aiOn, setAiOn] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [answer, setAnswer] = useState<CommunicationHelpAnswer | null>(null);
  const [cards, setCards] = useState<(PageChange & { id: number })[]>([]);
  const [ruleIssues, setRuleIssues] = useState<PageIssue[] | null>(null);
  const [applied, setApplied] = useState('');
  const [intent, setIntent] = useState('');
  const [meetingAgreed, setMeetingAgreed] = useState(false);
  const [meeting, setMeeting] = useState<(MeetingTask & { dueDate: string }) | null>(null);
  const [practising, setPractising] = useState(false);
  const [fixedTips, setFixedTips] = useState<string[] | null>(null);
  const [readingReply, setReadingReply] = useState(false);
  const [pasted, setPasted] = useState('');

  useEffect(() => {
    getAiStatus(token).then((value) => setAiOn(aiOnFor(value, 'COMMUNICATION'))).catch(() => setAiOn(false));
  }, [token]);

  const saved = messageId !== null;
  const awaitingReply = saved && (status === 'SENT' || status === 'FOLLOWED_UP');
  const helped = saved && (status === 'REPLIED' || status === 'CLOSED');

  function reset() {
    setFixedTips(null);
    setError('');
    setApplied('');
    setRuleIssues(null);
    setAnswer(null);
    setCards([]);
    setMeeting(null);
  }

  async function run(mode: CommunicationHelpMode, extra: { intent?: string; tone?: MessageToneChange; reply?: string } = {}) {
    reset();
    setBusy(true);
    try {
      const next = await communicationHelp(token, language, { mode, messageId, form: read(), ...extra });
      setAnswer(next);
      setCards(next.changes.map((change, index) => ({ ...change, id: index })));
      // A reply draft waits for the participant to choose it; a follow-up or thank-you opens straight away.
      if (next.message && mode !== 'REPLY') {
        count(next.requestId, false);
        onNewMessage(next.message);
      }
      if (next.task) {
        setMeeting({ ...next.task, dueDate: '' });
      }
    } catch (helpError) {
      setError(helpError instanceof Error ? tr(helpError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  function count(requestId: number | null | undefined, edited: boolean) {
    if (requestId) {
      // Counts only; a failure here must not undo what the participant chose.
      recordPageOutcome(token, requestId, edited ? 0 : 1, edited ? 1 : 0).catch(() => undefined);
    }
  }

  function applyCard(change: PageChange, value: string, edited: boolean, id?: number) {
    apply(change.field, value);
    count(answer?.requestId, edited);
    setApplied(tr('Added to the form. Nothing is saved until you press Save.'));
    if (id !== undefined) {
      setCards((current) => current.filter((card) => card.id !== id));
    }
  }

  async function checkMessage() {
    if (aiOn) {
      await run('CHECK');
      return;
    }
    // AH-FR-46: without AI, the rule checks only.
    reset();
    try {
      const check = await checkPageArea(token, { page: 'COMMUNICATION', area: 'message', itemId: messageId, form: read() });
      setRuleIssues(check.issues);
    } catch (checkError) {
      setError(checkError instanceof Error ? tr(checkError.message) : tr('The checks could not be loaded.'));
    }
  }

  function rebuild() {
    if (aiOn) {
      void run('BODY');
      return;
    }
    onRebuildWithTemplate();
    setApplied(tr('Message rebuilt from the fields. Nothing is saved until you press Save.'));
  }

  function fromTemplate(kind: 'FOLLOW_UP' | 'THANK_YOU') {
    const form = read();
    const id = (key: string) => (form[key] ? Number(form[key]) : null);
    onNewMessage({
      partnerId: id('partnerId'),
      subject: kind === 'FOLLOW_UP' ? tr('Following up: {subject}', { subject: form.subject ?? '' }) : tr('Thank you'),
      request: kind === 'FOLLOW_UP' ? form.request ?? '' : '',
      messageBody: kind === 'FOLLOW_UP'
        ? followUpTemplate(partnerName ?? '', form.subject ?? '', form.request ?? '')
        : thankYouTemplate(partnerName ?? '', form.request ?? '', progressText),
      relatedDreamId: id('relatedDreamId'),
      relatedGoalId: id('relatedGoalId'),
      relatedTaskId: id('relatedTaskId'),
    });
  }

  // AH-FR-59, AH-FR-60: with AI, cards for the hook, benefit and call to action; without AI, the program's fixed tips.
  function adapt() {
    if (aiOn) {
      void run('ADAPT');
      return;
    }
    reset();
    const tips: string[] = [];
    if (partnerStyle) {
      tips.push(tr('You described them as a {style}: {tip}', { style: tr(workStyleArchetypeLabels[partnerStyle]), tip: tr(ARCHETYPE_GUIDANCE[partnerStyle].tip) }));
    }
    if (partnerMotivator) {
      tips.push(tr(MOTIVATOR_TIPS[partnerMotivator]));
    }
    setFixedTips(tips);
  }

  function openMeetingTask() {
    if (!meeting) {
      return;
    }
    count(answer?.requestId, meeting.title !== answer?.task?.title || meeting.description !== answer?.task?.description);
    const params = new URLSearchParams({ create: 'task', title: meeting.title, description: meeting.description, owner: meeting.owner });
    if (meeting.dueDate) {
      params.set('dueDate', meeting.dueDate);
    }
    navigate(`/tasks?${params.toString()}`);
  }

  const form = read();
  const practicePitch = [form.request, form.benefitToPartner].filter(Boolean).join(' ');

  return (
    <Box className="field-full coaching-panel" component="section" aria-label={tr('Message helpers')}>
      <Typography variant="subtitle2" component="h3">{tr('Message helpers')}</Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
        <Button variant="secondary" disabled={busy} onClick={() => void checkMessage()}>{tr('Check my message')}</Button>
        <Button variant="secondary" disabled={busy} onClick={rebuild}>{tr('Rebuild message')}</Button>
        {awaitingReply && (
          <Button variant="secondary" disabled={busy} onClick={() => (aiOn ? void run('FOLLOW_UP') : fromTemplate('FOLLOW_UP'))}>
            {tr('Draft a follow-up')}
          </Button>
        )}
        {(partnerStyle || partnerMotivator) && (
          <Button variant="secondary" disabled={busy} onClick={adapt}>{tr('Adapt to this partner')}</Button>
        )}
        {helped && (
          <Button variant="secondary" disabled={busy} onClick={() => (aiOn ? void run('THANK_YOU') : fromTemplate('THANK_YOU'))}>
            {tr('Thank and report back')}
          </Button>
        )}
      </Box>

      {aiOn && (
        <Box sx={{ mt: 1.5 }}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start' }}>
            <TextField size="small" label={tr('What do you want to ask, in one sentence?')} value={intent}
              onChange={(event) => setIntent(event.target.value)} sx={{ flex: '1 1 260px' }}
              slotProps={{ htmlInput: { maxLength: 300, style: { fontSize: 16 } } }} />
            <Button variant="secondary" disabled={busy || !intent.trim()} onClick={() => void run('FILL', { intent: intent.trim() })}>
              {tr('Fill the empty fields')}
            </Button>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
            {TONES.map((tone) => (
              <Button key={tone.value} variant="secondary" disabled={busy} onClick={() => void run('TONE', { tone: tone.value })}>
                {tr(tone.label)}
              </Button>
            ))}
            <Button variant="secondary" disabled={busy} onClick={() => void run('OBJECTIONS')}>{tr('Prepare for objections')}</Button>
            {/* AH-FR-54: the pitch coach is an AI helper, so it is offered only while AI is on. */}
            <Button variant="secondary" onClick={() => setPractising((current) => !current)}>{tr('Practise this ask')}</Button>
            {saved && (status === 'SENT' || status === 'FOLLOWED_UP' || status === 'REPLIED') && (
              <Button variant="secondary" onClick={() => setReadingReply((current) => !current)}>{tr('Read a reply')}</Button>
            )}
          </Box>
          {readingReply && (
            // AH-FR-51: the pasted reply is read for this request only and never stored.
            <Box sx={{ display: 'grid', gap: 1, mt: 1 }}>
              <TextField size="small" multiline minRows={3} label={tr('Paste the reply here (not saved)')} value={pasted}
                onChange={(event) => setPasted(event.target.value)} slotProps={{ htmlInput: { maxLength: 4000, style: { fontSize: 16 } } }} />
              <Box>
                <Button variant="secondary" disabled={busy || !pasted.trim()} onClick={() => void run('REPLY', { reply: pasted.trim() })}>
                  {tr('Read it')}
                </Button>
              </Box>
            </Box>
          )}
          {saved && status === 'REPLIED' && (
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mt: 1 }}>
              <FormControlLabel control={<Checkbox size="small" checked={meetingAgreed} onChange={(event) => setMeetingAgreed(event.target.checked)} />}
                label={<Typography variant="body2">{tr('A meeting or call was agreed')}</Typography>} />
              {meetingAgreed && (
                <Button variant="secondary" disabled={busy} onClick={() => void run('MEETING_PREP')}>{tr('Prepare for the meeting')}</Button>
              )}
            </Box>
          )}
        </Box>
      )}

      {busy && <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{tr('The Dream Coach is thinking…')}</Typography>}
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}

      <Box aria-live="polite" sx={{ mt: 1 }}>
        {applied && <Alert severity="success" sx={{ mb: 1 }}>{applied}</Alert>}

        {fixedTips && (
          <Box component="ul" sx={{ m: 0, pl: 2.5 }} aria-label={tr('Tips for this partner')}>
            {fixedTips.map((tip) => <li key={tip}><Typography variant="body2">{tip}</Typography></li>)}
          </Box>
        )}
        {ruleIssues && ruleIssues.length === 0 && <Typography variant="body2">{tr('No problems found.')}</Typography>}
        {ruleIssues && ruleIssues.length > 0 && (
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {ruleIssues.map((issue) => (
              <li key={issue.problem}><Typography variant="body2">{tr(issue.problem)}{issue.fix ? ` ${tr(issue.fix)}` : ''}</Typography></li>
            ))}
          </Box>
        )}

        {answer?.mode === 'REPLY' && (
          <Stack spacing={1} sx={{ mb: 1 }}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <Chip size="small" label={tr('AI suggestion')} />
              {answer.reading?.kind && <Chip size="small" color="primary" label={tr(REPLY_KINDS[answer.reading.kind])} />}
            </Box>
            {answer.note && <Typography variant="body2">{answer.note}</Typography>}
            {answer.reading?.nextAction && (
              <Typography variant="body2"><strong>{tr('Next action:')}</strong> {answer.reading.nextAction}</Typography>
            )}
            {answer.message && (
              <Box>
                <Button variant="secondary" onClick={() => {
                  count(answer.requestId, false);
                  onNewMessage(answer.message as NewMessageDraft);
                }}>{tr('Use the reply draft')}</Button>
              </Box>
            )}
          </Stack>
        )}
        {answer?.mode === 'CHECK' && (
          <Stack spacing={1}>
            {answer.issues.length === 0 && <Typography variant="body2">{tr('No problems found.')}</Typography>}
            {answer.issues.map((issue, index) => (
              <Box key={`${issue.problem}-${index}`}>
                <Typography variant="body2">{issue.problem}</Typography>
                {issue.fix && (
                  <PageChangeCard change={issue.fix} label={tr(MESSAGE_FIELD_LABELS[issue.fix.field] ?? issue.fix.field)}
                    onApply={(value, edited) => applyCard(issue.fix as PageChange, value, edited)}
                    onDismiss={() => setAnswer({ ...answer, issues: answer.issues.map((item, at) => (at === index ? { ...item, fix: null } : item)) })} />
                )}
              </Box>
            ))}
          </Stack>
        )}

        {cards.length > 0 && (
          <Stack spacing={1}>
            {answer?.note && answer.mode !== 'REPLY' && <Typography variant="body2">{answer.note}</Typography>}
            {cards.map((card) => (
              <PageChangeCard key={card.id} change={card} label={tr(MESSAGE_FIELD_LABELS[card.field] ?? card.field)}
                onApply={(value, edited) => applyCard(card, value, edited, card.id)}
                onDismiss={() => setCards((current) => current.filter((item) => item.id !== card.id))} />
            ))}
          </Stack>
        )}

        {meeting && (
          <Card variant="outlined" component="section" aria-label={tr('Meeting plan')}>
            <CardContent sx={{ display: 'grid', gap: 1 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                <Chip size="small" label={tr('AI suggestion')} />
                <Typography variant="subtitle2" component="h4">{tr('Meeting plan')}</Typography>
              </Box>
              <TextField size="small" label={tr('Title')} value={meeting.title} onChange={(event) => setMeeting({ ...meeting, title: event.target.value })} />
              <TextField size="small" label={tr('Description')} multiline minRows={4} value={meeting.description}
                onChange={(event) => setMeeting({ ...meeting, description: event.target.value })} />
              <TextField size="small" type="date" label={tr('Due date')} value={meeting.dueDate}
                onChange={(event) => setMeeting({ ...meeting, dueDate: event.target.value })}
                slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: localDateIso() } }} helperText={tr('Pick a date')} />
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Button disabled={!meeting.title.trim()} onClick={openMeetingTask}>{tr('Open the task form')}</Button>
                <Button variant="secondary" onClick={() => setMeeting(null)}>{tr('Dismiss')}</Button>
              </Box>
            </CardContent>
          </Card>
        )}

        {answer && answer.mode !== 'CHECK' && answer.mode !== 'REPLY' && !answer.message && !answer.task && cards.length === 0 && !applied && (
          <Typography variant="body2" color="text.secondary">{answer.note ? tr(answer.note) : tr('No changes to suggest here.')}</Typography>
        )}
        {answer && <SuggestionRating token={token} requestId={answer.requestId} />}
      </Box>

      {aiOn && practising && (
        // AH-FR-54: the pitch coach, with the message's request and benefit filled in.
        <Box sx={{ mt: 1 }}><PitchCoachCard token={token} initialPitch={practicePitch} /></Box>
      )}
    </Box>
  );
}
