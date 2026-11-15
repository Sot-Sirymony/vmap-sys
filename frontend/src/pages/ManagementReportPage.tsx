import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import { useNavigate, useSearchParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { getManagementReport, type ReportFilter } from '../api/reportApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { ReportSummary } from '../components/report/ReportSummary';
import { ReportExportTab } from '../components/report/ReportExportTab';
import { useAuth } from '../context/AuthContext';
import type { Dream, ManagementReport, VisionArea } from '../types/vision';
import { formatDate } from '../i18n/formatDate';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';
import { FileBarChart } from 'lucide-react';

type ScopeKind = 'ALL' | 'AREA' | 'DREAM';

/**
 * V18 (BRD 18.0.0): the Management Report under the Vision Map. Summary
 * charts the plan's health; Export turns it into documents to share. The
 * scope (all dreams, one vision area or one dream) and the tab live in the
 * URL, so a view can be bookmarked (MR-FR-02, MR-FR-03).
 */
export function ManagementReportPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'export' ? 'export' : 'summary';
  const areaId = Number(searchParams.get('areaId')) || undefined;
  const dreamId = Number(searchParams.get('dreamId')) || undefined;
  const filter: ReportFilter = dreamId ? { dreamId } : areaId ? { areaId } : {};
  const scopeKind: ScopeKind = dreamId ? 'DREAM' : areaId ? 'AREA' : 'ALL';

  const [areas, setAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [report, setReport] = useState<ManagementReport | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    void Promise.all([listVisionAreas(token), listDreams(token)])
      .then(([areaData, dreamData]) => {
        setAreas(areaData.filter((area) => !area.archived));
        setDreams(dreamData.filter((dream) => !dream.archived));
      })
      .catch(() => undefined);
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let active = true;
    setLoading(true);
    setError('');
    getManagementReport(token, filter)
      .then((data) => active && setReport(data))
      .catch((loadError) => active && setError(loadError instanceof Error ? loadError.message : tr('Unable to load the report.')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
    // filter is rebuilt from these two each render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, areaId, dreamId]);

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    setSearchParams(next, { replace: true });
  }

  function changeScopeKind(kind: ScopeKind) {
    if (kind === 'ALL') update({ areaId: '', dreamId: '' });
    if (kind === 'AREA') update({ dreamId: '', areaId: String(areas[0]?.id ?? '') });
    if (kind === 'DREAM') update({ areaId: '', dreamId: String(dreams[0]?.id ?? '') });
  }

  const scopeName = report?.scope.kind === 'ALL' || !report ? tr('All dreams') : report.scope.label ?? '';
  const empty = report !== null && report.goals.length === 0;

  return (
    <PageSection
      title={tr('Management Report')}
      subtitle={tr('Plan health at a glance, ready to share with a manager, sponsor or mentor.')}
      actions={<Button variant="secondary" onClick={() => navigate('/vision-map')}>{tr('Back to the Vision Map')}</Button>}
    >
      <Card>
        <CardContent sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, flexWrap: 'wrap' }}>
          <label>
            {tr('Report on')}
            <FormControl size="small" sx={{ minWidth: 180, display: 'flex' }}>
              <Select SelectDisplayProps={{ 'aria-label': tr('Report on') }} value={scopeKind}
                onChange={(event) => changeScopeKind(event.target.value as ScopeKind)}>
                <MenuItem value="ALL">{tr('All dreams')}</MenuItem>
                <MenuItem value="AREA" disabled={areas.length === 0}>{tr('One vision area')}</MenuItem>
                <MenuItem value="DREAM" disabled={dreams.length === 0}>{tr('One dream')}</MenuItem>
              </Select>
            </FormControl>
          </label>
          {scopeKind === 'AREA' && (
            <label>
              {tr('Vision Area')}
              <FormControl size="small" sx={{ minWidth: 220, display: 'flex' }}>
                <Select SelectDisplayProps={{ 'aria-label': tr('Vision Area') }} value={areas.some((area) => area.id === areaId) ? String(areaId) : ''}
                  displayEmpty onChange={(event) => update({ areaId: event.target.value })}>
                  {areas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
                </Select>
              </FormControl>
            </label>
          )}
          {scopeKind === 'DREAM' && (
            <label>
              {tr('Dream')}
              <FormControl size="small" sx={{ minWidth: 220, display: 'flex' }}>
                <Select SelectDisplayProps={{ 'aria-label': tr('Dream') }} value={dreams.some((dream) => dream.id === dreamId) ? String(dreamId) : ''}
                  displayEmpty onChange={(event) => update({ dreamId: event.target.value })}>
                  {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
                </Select>
              </FormControl>
            </label>
          )}
          {report && (
            // MR-FR-04: the scope and the date, on every view.
            <Typography variant="body2" color="text.secondary" sx={{ ml: { md: 'auto' } }} role="status">
              {tr('{scope} · as of {date}', { scope: scopeName, date: formatDate(new Date(`${report.asOf}T00:00:00`), { year: true }) })}
            </Typography>
          )}
        </CardContent>
      </Card>

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_event, value: string) => update({ tab: value === 'export' ? 'export' : '' })} aria-label={tr('Report views')}>
          <Tab value="summary" label={tr('Summary')} id="report-tab-summary" aria-controls="report-panel-summary" />
          <Tab value="export" label={tr('Export')} id="report-tab-export" aria-controls="report-panel-export" />
        </Tabs>
      </Box>

      {error && <ErrorMessage message={error} />}
      {loading && !report && <Loading />}
      {empty && (
        // MR-FR-05.
        <EmptyState
          headline={tr('Nothing to report yet')}
          icon={FileBarChart}
          action={<Button onClick={() => navigate('/vision-map')}>{tr('Open the Vision Map')}</Button>}
        >
          {tr('Add goals, steps and tasks to a dream, and the report fills in from them.')}
        </EmptyState>
      )}

      <Box role="tabpanel" id="report-panel-summary" aria-labelledby="report-tab-summary" hidden={tab !== 'summary'}>
        {tab === 'summary' && report && !empty && <ReportSummary report={report} filter={filter} />}
      </Box>
      <Box role="tabpanel" id="report-panel-export" aria-labelledby="report-tab-export" hidden={tab !== 'export'}>
        {tab === 'export' && token && <ReportExportTab token={token} filter={filter} disabledReason={empty ? tr('Add goals to a dream first; there is nothing to export yet.') : ''} />}
      </Box>
    </PageSection>
  );
}
