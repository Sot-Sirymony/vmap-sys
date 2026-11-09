import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getPartnerBalance, setWorkStyleAiUse, workStyleHelp } from '../../api/workStyleProfileApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { StylePractice, WorkStyleArchetype, WorkStyleBalance, WorkStyleProfile } from '../../types/vision';
import { ARCHETYPE_GUIDANCE, workStyleArchetypeLabels } from '../../utils/workStyleAssessment';
import { ESTIMATE_QUESTIONS, STYLE_ACTIONS, STYLE_PRACTICES, axisSentences, estimateStyle } from '../../utils/workStyleContent';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';
import { HelpWithThis } from '../ai/page-assistant/PageAssistant';

function useAiOn(token: string) {
  const [aiOn, setAiOn] = useState(false);
  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'WORK_STYLE'))).catch(() => setAiOn(false));
  }, [token]);
  return aiOn;
}

const styleLabel = (style: WorkStyleArchetype) => tr(workStyleArchetypeLabels[style]);

/** V15 (AH-FR-76): the participant's switch for letting other AI helpers use their dominant and secondary style. */
export function AiUseSwitch({ token, profile, onChange }: Readonly<{ token: string; profile: WorkStyleProfile; onChange: (next: WorkStyleProfile) => void }>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function toggle(enabled: boolean) {
    setBusy(true);
    setError('');
    try {
      onChange(await setWorkStyleAiUse(token, enabled));
    } catch (toggleError) {
      setError(toggleError instanceof Error ? tr(toggleError.message) : tr('Unable to save this setting.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box>
      <FormControlLabel
        control={<Switch checked={Boolean(profile.aiUseWorkStyle)} disabled={busy} onChange={(event) => void toggle(event.target.checked)} />}
        label={tr('Let AI helpers use my work style')}
      />
      <Typography variant="caption" color="text.secondary" component="p">
        {tr('When on, the plan builder, next-step suggestions, module help and message adapting may use your main and second style. Never your answers.')}
      </Typography>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
    </Box>
  );
}

/**
 * V15 (AH-FR-70 to AH-FR-72): what the result means and how to put the style
 * to work. With AI the answer uses the participant's own counts and plan;
 * without AI, a rule sentence per axis and the program's three practices.
 */
export function StyleResultHelpers({ token, profile }: Readonly<{ token: string; profile: WorkStyleProfile }>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const aiOn = useAiOn(token);
  const [meaning, setMeaning] = useState<{ text: string[]; requestId: number | null; ai: boolean } | null>(null);
  const [practices, setPractices] = useState<{ list: StylePractice[]; requestId: number | null; ai: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const dominant = profile.dominant;
  if (!dominant) {
    return null;
  }

  async function explain() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          const answer = await workStyleHelp(token, language, { mode: 'EXPLAIN' });
          setMeaning({ text: [answer.text ?? ''], requestId: answer.requestId, ai: true });
          return;
        } catch {
          // Fall back to the rule sentences below.
        }
      }
      setMeaning({ text: axisSentences(profile), requestId: null, ai: false });
    } finally {
      setBusy(false);
    }
  }

  async function putToWork() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          const answer = await workStyleHelp(token, language, { mode: 'TIPS' });
          if (answer.practices.length > 0) {
            setPractices({ list: answer.practices, requestId: answer.requestId, ai: true });
            return;
          }
        } catch {
          // Fall back to the program's practices below.
        }
      }
      setPractices({ list: STYLE_PRACTICES[dominant as WorkStyleArchetype].map((item) => ({ ...item })), requestId: null, ai: false });
    } catch (practiceError) {
      setError(practiceError instanceof Error ? tr(practiceError.message) : tr('Unable to load practices.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('Use your result')} sx={{ display: 'grid', gap: 1 }}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button variant="secondary" disabled={busy} onClick={() => void explain()}>{tr('What does my result mean?')}</Button>
        <Button variant="secondary" disabled={busy} onClick={() => void putToWork()}>{tr('Put my style to work')}</Button>
      </Box>
      {busy && <Typography variant="body2" color="text.secondary">{tr('The Dream Coach is thinking…')}</Typography>}
      {error && <Alert severity="error">{error}</Alert>}
      <Box aria-live="polite" sx={{ display: 'grid', gap: 1 }}>
        {meaning && (
          <Box>
            {meaning.ai && <Chip size="small" label={tr('AI suggestion')} sx={{ mb: 0.5 }} />}
            {meaning.text.map((line) => <Typography key={line} variant="body2">{line}</Typography>)}
            <Typography variant="caption" color="text.secondary">{tr('Styles are tendencies, not fixed traits. No style is better than another.')}</Typography>
            <SuggestionRating token={token} requestId={meaning.requestId} />
          </Box>
        )}
        {practices && (
          <Stack spacing={1}>
            {practices.list.map((item) => {
              const action = item.action ? STYLE_ACTIONS[item.action] : null;
              return (
                <Card key={item.text} variant="outlined">
                  <CardContent sx={{ display: 'grid', gap: 1 }}>
                    {practices.ai && <Chip size="small" label={tr('AI suggestion')} sx={{ justifySelf: 'start' }} />}
                    <Typography variant="body2">{practices.ai ? item.text : tr(item.text)}</Typography>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                      {action && <Button onClick={() => navigate(action.path)}>{tr('Do it now')}</Button>}
                      {item.habit && (
                        <Button variant="secondary" onClick={() => navigate(`/today?habit=${encodeURIComponent(practices.ai ? item.habit : tr(item.habit))}`)}>
                          {tr('Add as a habit')}
                        </Button>
                      )}
                    </Box>
                  </CardContent>
                </Card>
              );
            })}
            <SuggestionRating token={token} requestId={practices.requestId} />
          </Stack>
        )}
      </Box>
    </Box>
  );
}

/**
 * V15 (AH-FR-73 to AH-FR-75): how the participant's partners balance their
 * style, a four-question estimate for partners with no style, and tips for
 * working with a partner who has one. Balance and the estimate need no AI.
 * Nothing here changes a partner: the estimate opens the partner form, filled.
 */
export function PartnerBalanceCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const aiOn = useAiOn(token);
  const [balance, setBalance] = useState<WorkStyleBalance | null>(null);
  const [why, setWhy] = useState<{ text: string; requestId: number | null } | null>(null);
  const [estimating, setEstimating] = useState<number | null>(null);
  const [answers, setAnswers] = useState<('a' | 'b' | null)[]>(ESTIMATE_QUESTIONS.map(() => null));
  const [tipsFor, setTipsFor] = useState<number | null>(null);
  const [notes, setNotes] = useState('');
  const [tips, setTips] = useState<{ partnerId: number; list: string[]; requestId: number | null; ai: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getPartnerBalance(token).then(setBalance).catch(() => setBalance(null));
  }, [token]);

  if (!balance || !balance.dominant) {
    return null;
  }

  async function explainBalance() {
    setBusy(true);
    setError('');
    try {
      const answer = await workStyleHelp(token, language, { mode: 'BALANCE' });
      setWhy({ text: answer.text ?? '', requestId: answer.requestId });
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  async function workingWith(partnerId: number, style: WorkStyleArchetype) {
    setError('');
    if (!aiOn) {
      // AH-FR-60 pattern: without AI, the program's fixed tip for that style.
      setTips({ partnerId, list: [tr(ARCHETYPE_GUIDANCE[style].tip)], requestId: null, ai: false });
      return;
    }
    setBusy(true);
    try {
      const answer = await workStyleHelp(token, language, { mode: 'PARTNER', partnerId, partnerNotes: notes.trim() || undefined });
      setTips({ partnerId, list: answer.tips, requestId: answer.requestId, ai: true });
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  function startEstimate(partnerId: number) {
    setEstimating(partnerId);
    setAnswers(ESTIMATE_QUESTIONS.map(() => null));
  }

  const estimate = answers.every((answer) => answer !== null) ? estimateStyle(answers as ('a' | 'b')[]) : null;

  return (
    <Card component="section" aria-label={tr('Balance my partners')} sx={{ maxWidth: 640 }}>
      <CardHeader title={tr('Balance my partners')} subheader={tr('Partner styles are your own estimates.')} />
      <CardContent sx={{ display: 'grid', gap: 1.5 }}>
        <Typography variant="body2">
          {tr('Styles that would balance yours: {styles}', { styles: balance.complementary.map(styleLabel).join(', ') })}
        </Typography>
        <Typography variant="body2">
          {(Object.keys(balance.counts) as WorkStyleArchetype[]).map((style) => `${styleLabel(style)}: ${balance.counts[style]}`).join(' · ')}
          {` · ${tr('No style set: {count}', { count: balance.noStyle })}`}
        </Typography>
        {balance.missing.map((style) => (
          <Alert key={style} severity="info">{tr('None of your partners is a {style} yet.', { style: styleLabel(style) })}</Alert>
        ))}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button variant="secondary" onClick={() => navigate('/partners')}>{tr('Set partner styles')}</Button>
          <Button variant="secondary" onClick={() => navigate('/steps')}>{tr('Ideal Partner Profile on complex steps')}</Button>
          {aiOn && <Button variant="secondary" disabled={busy} onClick={() => void explainBalance()}>{tr('Why would this help my plan?')}</Button>}
        </Box>
        {why && (
          <Box aria-live="polite">
            <Chip size="small" label={tr('AI suggestion')} sx={{ mb: 0.5 }} />
            <Typography variant="body2">{why.text}</Typography>
            <SuggestionRating token={token} requestId={why.requestId} />
          </Box>
        )}
        {error && <Alert severity="error">{error}</Alert>}

        {balance.partners.length > 0 && (
          <Box component="ul" sx={{ m: 0, pl: 0, listStyle: 'none', display: 'grid', gap: 1 }} aria-label={tr('Your partners')}>
            {balance.partners.map((partner) => (
              <li key={partner.id}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{partner.name}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {partner.workStyleType ? tr('You described them as a {style}', { style: styleLabel(partner.workStyleType) }) : tr('No style set')}
                  </Typography>
                  <HelpWithThis compact area={`partner:${partner.id}`} label={partner.name} />
                  {partner.workStyleType ? (
                    <>
                      <Button variant="secondary" disabled={busy} onClick={() => setTipsFor(tipsFor === partner.id ? null : partner.id)}>
                        {tr('Working with them')}
                      </Button>
                      <Button variant="secondary" onClick={() => navigate(`/communication?create=message&partnerId=${partner.id}`)}>
                        {tr('Adapt my message')}
                      </Button>
                    </>
                  ) : (
                    <Button variant="secondary" onClick={() => startEstimate(partner.id)}>{tr('Estimate their style')}</Button>
                  )}
                </Box>

                {tipsFor === partner.id && partner.workStyleType && (
                  <Box sx={{ mt: 1, display: 'grid', gap: 1 }}>
                    {aiOn && (
                      <TextField size="small" label={tr('How do they usually work? (optional, not saved)')} value={notes}
                        onChange={(event) => setNotes(event.target.value)} slotProps={{ htmlInput: { maxLength: 500, style: { fontSize: 16 } } }} />
                    )}
                    <Box>
                      <Button disabled={busy} onClick={() => void workingWith(partner.id, partner.workStyleType as WorkStyleArchetype)}>
                        {tr('How do I work well with this person?')}
                      </Button>
                    </Box>
                    {tips?.partnerId === partner.id && (
                      <Box aria-live="polite">
                        {tips.ai && <Chip size="small" label={tr('AI suggestion')} sx={{ mb: 0.5 }} />}
                        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                          {tips.list.map((tip) => <li key={tip}><Typography variant="body2">{tip}</Typography></li>)}
                        </Box>
                        <SuggestionRating token={token} requestId={tips.requestId} />
                      </Box>
                    )}
                  </Box>
                )}

                {estimating === partner.id && (
                  <Box sx={{ mt: 1, display: 'grid', gap: 1 }} component="section" aria-label={tr('Estimate their style')}>
                    <Typography variant="body2">{tr('Pick what is closer to how they usually work.')}</Typography>
                    {ESTIMATE_QUESTIONS.map((question, index) => (
                      <ToggleButtonGroup key={question.a} exclusive size="small" orientation="vertical" fullWidth value={answers[index]}
                        aria-label={tr('Question {number}', { number: index + 1 })}
                        onChange={(_event, value: 'a' | 'b' | null) => setAnswers((current) => current.map((item, at) => (at === index ? value : item)))}>
                        <ToggleButton value="a" sx={{ textTransform: 'none', justifyContent: 'flex-start' }}>{tr(question.a)}</ToggleButton>
                        <ToggleButton value="b" sx={{ textTransform: 'none', justifyContent: 'flex-start' }}>{tr(question.b)}</ToggleButton>
                      </ToggleButtonGroup>
                    ))}
                    {estimate && (
                      <Card variant="outlined" component="section" aria-label={tr('Your estimate')}>
                        <CardContent sx={{ display: 'grid', gap: 1 }}>
                          <Typography variant="subtitle2">{tr('Your estimate: {style}', { style: styleLabel(estimate) })}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {tr('This is only your estimate of how they usually work, not a fact about them.')}
                          </Typography>
                          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                            <Button onClick={() => navigate(`/partners?edit=${partner.id}&workStyleType=${estimate}`)}>{tr('Open the partner form')}</Button>
                            <Button variant="secondary" onClick={() => setEstimating(null)}>{tr('Dismiss')}</Button>
                          </Box>
                        </CardContent>
                      </Card>
                    )}
                  </Box>
                )}
              </li>
            ))}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
