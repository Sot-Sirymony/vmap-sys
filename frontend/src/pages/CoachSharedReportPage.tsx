import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { getCoachSharedReport } from '../api/cohortApi';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { SharedReportView } from '../components/report/SharedReportView';
import { useAuth } from '../context/AuthContext';
import { tr } from '../i18n/translate';
import type { SharedReport } from '../types/vision';
import { PageSection } from './PageSection';

/**
 * V19 (RM-FR-50, D-19-3): a final report a participant shared with their
 * coach, read-only, with only the sections they chose. The server checks the
 * V9 D-6 rules; a report no longer shared answers "Report not found."
 */
export function CoachSharedReportPage() {
  const { token } = useAuth();
  const params = useParams();
  const userId = Number(params.userId);
  const reportId = Number(params.reportId);
  const [shared, setShared] = useState<SharedReport | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    let active = true;
    getCoachSharedReport(token, userId, reportId)
      .then((report) => active && setShared(report))
      .catch((loadError: unknown) => active && setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load the report.')));
    return () => {
      active = false;
    };
  }, [token, userId, reportId]);

  return (
    <PageSection title={tr('Shared report')} subtitle={tr('Read-only view for their coach')}>
      <Breadcrumbs crumbs={[{ label: tr('Coaching'), to: '/coach' }, { label: tr('Participant'), to: `/coach/participants/${userId}` },
        { label: shared?.code ?? tr('Shared report') }]} />
      {error && <ErrorMessage message={error} />}
      {!shared && !error && <Loading />}
      {shared && (
        <SharedReportView shared={shared} reloadKey={`${userId}-${reportId}`} titleLevel="h2"
          sharedLine={tr('{name} shared this report with you, read-only', { name: shared.sharedBy })} />
      )}
    </PageSection>
  );
}
