import { useEffect, useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate, useSearchParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { getManagementReport, getReportRows } from '../api/reportApi';
import { createSavedReport } from '../api/savedReportApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { ReportControls } from '../components/report/ReportControls';
import { ReportTabs, reportTabFrom, type ReportTab } from '../components/report/ReportTabs';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n/I18nContext';
import type { Dream, ManagementReport, VisionArea } from '../types/vision';
import { filterFromSearch, filterToSearch, periodLabel, reportDate } from '../utils/managementReport';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

/**
 * V18 (BRD 18.0.0): the Management Report under the Vision Map. Summary
 * charts the plan's health; Details lists its rows; Export turns it into
 * documents to share. The scope, period, filters and tab live in the URL, so
 * a view can be bookmarked (MR-FR-02, MR-FR-03, RM-FR-06, RM-FR-10).
 * V19 RM-FR-16: Save as report keeps it, with commentary, on the Reports page.
 */
export function ManagementReportPage() {
  const { token } = useAuth();
  const { language } = useI18n();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = reportTabFrom(searchParams.get('tab'));
  const filter = filterFromSearch(searchParams);
  const filterKey = JSON.stringify(filter);

  const [areas, setAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [report, setReport] = useState<ManagementReport | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

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
    // filter is rebuilt from the URL each render; filterKey says when it changed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, filterKey]);

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    setSearchParams(next, { replace: true });
  }

  async function saveAsReport() {
    if (!token) return;
    setSaving(true);
    setError('');
    try {
      const saved = await createSavedReport(token, { ...filter, language });
      navigate(`/vision-map/reports/${saved.id}`);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : tr('Unable to save the report.'));
      setSaving(false);
    }
  }

  const scopeName = report?.scope.kind === 'ALL' || !report ? tr('All dreams') : report.scope.label ?? '';

  return (
    <PageSection
      title={tr('Management Report')}
      subtitle={tr('Plan health at a glance, ready to share with a manager, sponsor or mentor.')}
      actions={(
        <Stack direction="row" spacing={1}>
          <Button variant="secondary" onClick={() => navigate('/vision-map/reports')}>{tr('Saved reports')}</Button>
          <Button onClick={() => void saveAsReport()} disabled={saving || !report}>{saving ? tr('Saving…') : tr('Save as report')}</Button>
        </Stack>
      )}
    >
      <Card>
        <CardContent>
          <ReportControls value={filter} onChange={(next) => update(filterToSearch(next))} areas={areas} dreams={dreams}
            owners={report?.owners ?? []}>
            {report && (
              // MR-FR-04, RM-FR-07: the scope, the period and the date, on every view.
              <Typography variant="body2" color="text.secondary" sx={{ ml: { md: 'auto' } }} role="status">
                {tr('{scope} · {period} · as of {date}', { scope: scopeName, period: periodLabel(report.period), date: reportDate(report.asOf) })}
              </Typography>
            )}
          </ReportControls>
        </CardContent>
      </Card>

      {error && <ErrorMessage message={error} />}
      {loading && !report && <Loading />}

      {token && (
        <ReportTabs
          report={report}
          filter={filter}
          tab={tab}
          onTabChange={(next: ReportTab) => update({ tab: next === 'summary' ? '' : next })}
          loadRows={(rowsLanguage) => getReportRows(token, filter, rowsLanguage)}
          reloadKey={filterKey}
          token={token}
        />
      )}
    </PageSection>
  );
}
