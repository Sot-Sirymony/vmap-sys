import { useCallback, useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { getAiStatus } from '../api/aiApi';
import { listDreams } from '../api/dreamApi';
import type { ReportFilter } from '../api/reportApi';
import {
  archiveSavedReport, copySavedReport, deleteSavedReport, downloadSavedReport, draftReportCommentary, finalizeSavedReport,
  getSavedReport, getSavedReportRows, listSavedReports, renameSavedReport, restoreSavedReport, saveReportCommentary, saveReportSections,
  updateSavedReport,
} from '../api/savedReportApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { ReportCommentaryCard, type CommentarySection, type SaveState } from '../components/report/ReportCommentaryCard';
import { ReportComparePicker, comparableReports } from '../components/report/ReportComparePicker';
import { ReportControls } from '../components/report/ReportControls';
import { ReportExportHistory } from '../components/report/ReportExportHistory';
import { ReportShareCard } from '../components/report/ReportShareCard';
import { ReportTabs, reportTabFrom, type ReportTab } from '../components/report/ReportTabs';
import { useAuth } from '../context/AuthContext';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useI18n } from '../i18n/I18nContext';
import type { Dream, ReportSection, SavedReport, SavedReportCommentary, VisionArea } from '../types/vision';
import { periodLabel, reportDate } from '../utils/managementReport';
import { PageSection } from './PageSection';
import { ReportStatusChip } from './SavedReportsPage';
import { tr } from '../i18n/translate';

/** RM-FR-27: commentary is saved at most this often while typing. */
export const AUTOSAVE_MS = 2000;

/** A saved report's settings as the report controls use them. */
export function filterOf(saved: SavedReport): ReportFilter {
  const filter: ReportFilter = {};
  if (saved.dreamId) filter.dreamId = saved.dreamId;
  else if (saved.areaId) filter.areaId = saved.areaId;
  if (saved.periodKind !== 'THIS_MONTH') filter.period = saved.periodKind;
  if (saved.periodKind === 'CUSTOM' && saved.periodFrom && saved.periodTo) Object.assign(filter, { from: saved.periodFrom, to: saved.periodTo });
  if (saved.filters.priorities.length) filter.priorities = saved.filters.priorities;
  if (saved.filters.owner) filter.owner = saved.filters.owner;
  if (saved.filters.healths.length) filter.healths = saved.filters.healths;
  if (!saved.filters.includeCompleted) filter.includeCompleted = false;
  return filter;
}

type Pending = 'finalize' | 'delete' | 'archive' | null;

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): one saved report. A draft shows
 * live figures; its scope, period, filters and commentary can change, and
 * the commentary saves as the participant types. Finalise freezes the
 * figures; a final report is read-only except its name.
 */
export function SavedReportPage() {
  const { token } = useAuth();
  const { language } = useI18n();
  const navigate = useNavigate();
  const { id: idParam } = useParams();
  const id = Number(idParam);
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = reportTabFrom(searchParams.get('tab'));

  const [saved, setSaved] = useState<SavedReport | null>(null);
  const [areas, setAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [others, setOthers] = useState<SavedReport[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<Pending>(null);
  const [name, setName] = useState('');
  const [commentary, setCommentary] = useState<SavedReportCommentary | null>(null);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [suggested, setSuggested] = useState<Set<CommentarySection>>(new Set());
  const [suggesting, setSuggesting] = useState(false);
  const [aiOn, setAiOn] = useState(false);
  const [openLinks, setOpenLinks] = useState(0);
  const [downloads, setDownloads] = useState(0);
  const countOpenLinks = useCallback((count: number) => setOpenLinks(count), []);
  const lastSaved = useRef('');
  const debounced = useDebouncedValue(commentary, AUTOSAVE_MS);
  const isDraft = saved?.status === 'DRAFT';

  function show(report: SavedReport) {
    setSaved(report);
    setName(report.name);
  }

  useEffect(() => {
    if (!token || !id) return;
    let active = true;
    getSavedReport(token, id)
      .then((report) => {
        if (!active) return;
        show(report);
        setCommentary(report.commentary);
        lastSaved.current = JSON.stringify(report.commentary);
      })
      .catch((loadError) => active && setError(loadError instanceof Error ? loadError.message : tr('Unable to load the report.')));
    void Promise.all([listVisionAreas(token), listDreams(token)])
      .then(([areaData, dreamData]) => {
        if (!active) return;
        setAreas(areaData.filter((area) => !area.archived));
        setDreams(dreamData.filter((dream) => !dream.archived));
      })
      .catch(() => undefined);
    // RM-FR-32: the final reports a draft can be compared with.
    void listSavedReports(token, true).then((list) => active && setOthers(list)).catch(() => undefined);
    // RM-FR-29: Draft with AI shows only while AI can run for this participant.
    void getAiStatus(token).then((status) => active && setAiOn(Boolean(status.available))).catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, id]);

  async function saveCommentary(value: SavedReportCommentary) {
    if (!token || !isDraft || JSON.stringify(value) === lastSaved.current) return;
    setSaveState('saving');
    try {
      await saveReportCommentary(token, id, value);
      lastSaved.current = JSON.stringify(value);
      setSaveState('saved');
    } catch {
      setSaveState('error');
    }
  }

  // RM-FR-27: save once typing pauses.
  useEffect(() => {
    if (debounced) void saveCommentary(debounced);
    // saveCommentary reads the latest state; only a new pause should trigger it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : tr('Something went wrong. Please try again.'));
    } finally {
      setBusy(false);
    }
  }

  /** RM-FR-18, RM-FR-32: a draft's settings; a comparison holds only within one scope, so a new scope drops it. */
  function changeSettings(next: ReportFilter, compareToReportId = saved?.compareToReportId) {
    if (!token || !saved) return;
    const sameScope = (next.dreamId ?? null) === (saved.dreamId ?? null) && (next.areaId ?? null) === (saved.areaId ?? null);
    void run(async () => show(await updateSavedReport(token, id, {
      ...next, name: saved.name, compareToReportId: sameScope ? compareToReportId : null,
    })));
  }

  /** RM-FR-39: remembered for the next download; a failure only means the choice is not kept. */
  function changeSections(sections: ReportSection[]) {
    if (!token) return;
    void saveReportSections(token, id, sections).catch(() => undefined);
  }

  function rename() {
    if (!token || !name.trim()) return;
    void run(async () => show(await renameSavedReport(token, id, name.trim())));
  }

  function finalize() {
    if (!token || !commentary) return;
    setPending(null);
    void run(async () => {
      await saveCommentary(commentary);
      show(await finalizeSavedReport(token, id, language));
    });
  }

  function copy() {
    if (!token) return;
    void run(async () => navigate(`/vision-map/reports/${(await copySavedReport(token, id)).id}`));
  }

  /** RM-FR-51: archiving stops the report's share links, so it asks first when any are open. */
  function archive(confirmed = false) {
    if (!token || !saved) return;
    if (!saved.archived && openLinks > 0 && !confirmed) {
      setPending('archive');
      return;
    }
    setPending(null);
    void run(async () => show(await (saved.archived ? restoreSavedReport(token, id) : archiveSavedReport(token, id))));
  }

  function remove() {
    if (!token) return;
    setPending(null);
    void run(async () => {
      await deleteSavedReport(token, id);
      navigate('/vision-map/reports');
    });
  }

  async function suggest(mode: 'RULES' | 'AI' = 'RULES') {
    if (!token || !commentary) return;
    setSuggesting(true);
    setError('');
    try {
      const draft = await draftReportCommentary(token, id, language, mode);
      const next = { ...commentary };
      const marked = new Set(suggested);
      (['highlights', 'risks', 'decisionsNeeded', 'nextFocus'] as CommentarySection[]).forEach((section) => {
        const text = draft[section];
        if (text && !next[section]?.trim()) {
          next[section] = text;
          marked.add(section);
        }
      });
      setCommentary(next);
      setSuggested(marked);
    } catch (suggestError) {
      setError(suggestError instanceof Error ? tr(suggestError.message) : tr('Unable to suggest a draft.'));
    } finally {
      setSuggesting(false);
    }
  }

  if (error && !saved) return <PageSection title={tr('Saved report')} subtitle=""><ErrorMessage message={error} /></PageSection>;
  if (!saved || !commentary || !token) return <Loading />;

  const report = saved.report ?? null;
  const filter = filterOf(saved);
  const goals = (report?.goals ?? []).map((goal) => ({ goalId: goal.goalId, goalTitle: goal.goalTitle }));
  const scopeName = saved.scopeKind === 'ALL' ? tr('All dreams') : saved.scopeLabel ?? '';

  return (
    <PageSection
      title={saved.name}
      subtitle={`${saved.code} · ${scopeName}${report ? ` · ${periodLabel(report.period)}` : ''}`}
      actions={(
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Button variant="secondary" onClick={() => navigate('/vision-map/reports')}>{tr('All saved reports')}</Button>
          {isDraft && <Button onClick={() => setPending('finalize')} disabled={busy}>{tr('Finalise')}</Button>}
          <Button variant="secondary" onClick={copy} disabled={busy}>{tr('New report from this')}</Button>
          <Button variant="secondary" onClick={() => archive()} disabled={busy}>{saved.archived ? tr('Restore') : tr('Archive')}</Button>
          {isDraft && <Button variant="danger" onClick={() => setPending('delete')} disabled={busy}>{tr('Delete draft')}</Button>}
        </Stack>
      )}
    >
      <Card>
        <CardContent sx={{ display: 'grid', gap: 1.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <ReportStatusChip status={saved.status} />
            {saved.status === 'FINAL' && saved.finalizedAt ? (
              <Typography variant="body2" role="status">
                {tr('Final · frozen {date}', { date: reportDate(saved.finalizedAt.slice(0, 10)) })}
              </Typography>
            ) : (
              <Typography variant="body2" role="status">{tr('Draft · figures update until you finalise')}</Typography>
            )}
            <Box component="form" sx={{ display: 'flex', gap: 1, ml: { md: 'auto' }, alignItems: 'center' }}
              onSubmit={(event) => { event.preventDefault(); rename(); }}>
              <Box sx={{ width: 280 }}>
                <Input aria-label={tr('Report name')} value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
              </Box>
              <Button type="submit" variant="secondary" disabled={busy || !name.trim() || name.trim() === saved.name}>{tr('Rename')}</Button>
            </Box>
          </Box>
          {saved.verified === false && (
            <Alert severity="warning">{tr('This report could not be verified. Its stored figures may have been changed.')}</Alert>
          )}
          {isDraft && (
            <ReportControls value={filter} onChange={(next) => changeSettings(next)} areas={areas} dreams={dreams} owners={report?.owners ?? []}>
              <ReportComparePicker reports={comparableReports(others, filter, saved.id)} value={saved.compareToReportId}
                onChange={(compareTo) => changeSettings(filter, compareTo ?? null)} emptyLabel={tr('The latest final report')} />
            </ReportControls>
          )}
        </CardContent>
      </Card>

      {error && <ErrorMessage message={error} />}

      {saved.status === 'FINAL' && (
        // RM-FR-46: only a final report can be shared (RMC-18).
        <ReportShareCard token={token} reportId={saved.id} onOpenLinks={countOpenLinks} reloadKey={`${saved.archived}`}
          disabledReason={saved.archived ? tr('Restore this report before you share it.')
            : saved.verified === false ? tr('This report could not be verified, so it cannot be shared.') : ''} />
      )}

      <ReportTabs
        report={report}
        filter={filter}
        tab={tab}
        onTabChange={(next: ReportTab) => {
          const params = new URLSearchParams(searchParams);
          if (next === 'summary') params.delete('tab'); else params.set('tab', next);
          setSearchParams(params, { replace: true });
        }}
        loadRows={(rowsLanguage) => getSavedReportRows(token, id, rowsLanguage)}
        reloadKey={`${saved.id}|${saved.updatedAt}|${saved.status}`}
        token={token}
        download={(format, fileLanguage, deck) => downloadSavedReport(token, id, format, fileLanguage, deck)}
        belowExport={<ReportExportHistory token={token} reportId={saved.id} reloadKey={String(downloads)} />}
        onDownloaded={() => setDownloads((count) => count + 1)}
        sections={saved.sections}
        onSectionsChange={changeSections}
        defaultLanguage={isDraft ? undefined : saved.language}
        comparisonHint={tr('Finalise this report to compare the next one with it.')}
        exportNote={isDraft ? tr('These files show the draft\'s live figures and are marked DRAFT.')
          : tr('These files show the figures frozen on {date}.', { date: reportDate((saved.finalizedAt ?? '').slice(0, 10) || saved.updatedAt.slice(0, 10)) })}
        aboveSummary={(
          <ReportCommentaryCard
            commentary={commentary}
            goals={goals}
            editable={isDraft}
            onChange={(next, edited) => {
              setCommentary(next);
              if (edited && suggested.has(edited)) {
                const marked = new Set(suggested);
                marked.delete(edited);
                setSuggested(marked);
              }
            }}
            onBlur={() => void saveCommentary(commentary)}
            onSuggest={() => void suggest()}
            onSuggestAi={aiOn ? () => void suggest('AI') : undefined}
            suggested={suggested}
            saveState={saveState}
            suggesting={suggesting}
          />
        )}
      />

      {pending === 'finalize' && (
        <ConfirmDialog
          title={tr('Finalise this report?')}
          message={tr('Figures will be frozen as of {date}. You can still archive it or make a new draft from it.', { date: reportDate(report?.asOf ?? new Date().toISOString().slice(0, 10)) })}
          confirmLabel={tr('Finalise')}
          onConfirm={finalize}
          onClose={() => setPending(null)}
        />
      )}
      {pending === 'archive' && (
        <ConfirmDialog
          title={tr('Archive this report?')}
          message={tr('Its {count} open share link(s) will stop working at once. Restoring the report does not reopen them.', { count: openLinks })}
          confirmLabel={tr('Archive')}
          onConfirm={() => archive(true)}
          onClose={() => setPending(null)}
        />
      )}
      {pending === 'delete' && (
        <ConfirmDialog
          title={tr('Delete this draft?')}
          message={tr('The draft and its commentary are removed for good. This cannot be undone.')}
          confirmLabel={tr('Delete draft')}
          danger
          onConfirm={remove}
          onClose={() => setPending(null)}
        />
      )}
    </PageSection>
  );
}
