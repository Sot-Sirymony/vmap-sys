import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { listCommunicationMessages } from '../api/communicationApi';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { getPartner } from '../api/partnerApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { StatusBadge } from '../components/common/StatusBadge';
import { useAuth } from '../context/AuthContext';
import type { CommunicationMessage, Dream, Goal, Partner, PartnerStatus, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { INTEGRITY_CHECKLIST_QUESTIONS, offerTypeLabels, partnerMotivatorLabels, partnerSupportTypeLabels } from '../utils/enumLabels';
import { workStyleArchetypeLabels } from '../utils/workStyleAssessment';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

// FR-15.4: the recruitment lifecycle the existing pipeline statuses map onto.
// Utilize = Active with at least one linked work item.
const RECRUITMENT_STAGES = ['Identify', 'Contact', 'Recruit', 'Utilize', 'Done'] as const;

function recruitmentStage(partner: Partner): (typeof RECRUITMENT_STAGES)[number] {
  const hasLinkedWork = Boolean(
    partner.relatedVisionAreaId || partner.relatedDreamId || partner.relatedGoalId
    || partner.relatedStepId || partner.relatedTaskId,
  );
  const byStatus: Record<PartnerStatus, (typeof RECRUITMENT_STAGES)[number]> = {
    TO_CONTACT: 'Identify',
    CONTACTED: 'Contact',
    AGREED: 'Recruit',
    ACTIVE: hasLinkedWork ? 'Utilize' : 'Recruit', // stage values; translated where shown
    WAITING: 'Recruit',
    DECLINED: 'Recruit',
    COMPLETED: 'Done',
  };
  return byStatus[partner.status];
}

export function PartnerDetailPage() {
  const { token } = useAuth();
  const { partnerId } = useParams();
  const navigate = useNavigate();
  const [partner, setPartner] = useState<Partner | null>(null);
  const [messages, setMessages] = useState<CommunicationMessage[]>([]);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token || !partnerId) {
      return;
    }
    setLoading(true);
    void Promise.all([
      getPartner(token, Number(partnerId)),
      listCommunicationMessages(token, 0, 100, false, 'id,desc', '', { partnerId, status: '' }),
      listVisionAreas(token),
      listDreams(token),
      listGoals(token),
      listSteps(token),
      listTasks(token),
    ])
      .then(([partnerData, messagePage, areaData, dreamData, goalData, stepData, taskData]) => {
        setPartner(partnerData);
        setMessages(messagePage.content);
        setVisionAreas(areaData);
        setDreams(dreamData);
        setGoals(goalData);
        setSteps(stepData);
        setTasks(taskData);
        setError('');
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : tr('Unable to load partner.')))
      .finally(() => setLoading(false));
  }, [token, partnerId]);

  if (loading) {
    return <PageSection title={tr('Partner')} subtitle={tr('Loading partner details.')}><Loading /></PageSection>;
  }
  if (error || !partner) {
    return (
      <PageSection title={tr('Partner')} subtitle={tr('Partner details.')}>
        <ErrorMessage message={error || tr('Partner not found.')} />
        <Button type="button" onClick={() => navigate('/partners')}>{tr('Back to partners')}</Button>
      </PageSection>
    );
  }

  const stage = recruitmentStage(partner);
  const area = visionAreas.find((item) => item.id === partner.relatedVisionAreaId);
  const dream = dreams.find((item) => item.id === partner.relatedDreamId);
  const goal = goals.find((item) => item.id === partner.relatedGoalId);
  const step = steps.find((item) => item.id === partner.relatedStepId);
  const task = tasks.find((item) => item.id === partner.relatedTaskId);
  const linkedItems = [
    area && { label: tr('Vision Area'), name: area.name, to: `/dreams?visionAreaId=${area.id}` },
    dream && { label: tr('Dream'), name: dream.title, to: `/dreams/${dream.id}` },
    goal && { label: tr('Goal'), name: goal.title, to: `/steps?goalId=${goal.id}` },
    step && { label: tr('Step'), name: step.title, to: `/tasks?stepId=${step.id}` },
    task && { label: tr('Task'), name: task.title, to: `/tasks?stepId=${task.stepId}` },
  ].filter((item): item is { label: string; name: string; to: string } => Boolean(item));

  const profileRows: { label: string; value: string }[] = [
    { label: tr('Role'), value: partner.role ?? '-' },
    { label: tr('Organization'), value: partner.organization ?? '-' },
    { label: tr('Email'), value: partner.email ?? '-' },
    { label: tr('Phone'), value: partner.phone ?? '-' },
    { label: tr('Strength'), value: partner.strength ?? '-' },
    { label: tr('Support Type'), value: partnerSupportTypeLabels[partner.supportType] },
    { label: tr('Offer Type'), value: partner.offerType ? offerTypeLabels[partner.offerType] : '-' },
    { label: tr('Primary Motivator'), value: partner.primaryMotivator ? partnerMotivatorLabels[partner.primaryMotivator] : '-' },
    { label: tr('Work Style'), value: partner.workStyleType ? workStyleArchetypeLabels[partner.workStyleType] : '-' },
    { label: tr('Notes'), value: partner.notes ?? '-' },
  ];

  // FR-50.3: an audit trail, not just a one-time gate — visible here after
  // the fact whether or not this partner was ever flagged.
  const flaggedConcerns = INTEGRITY_CHECKLIST_QUESTIONS.filter((question) => partner[question.key]).map((question) => question.label);
  const vettingRows: { label: string; value: string }[] = [
    { label: tr('Vetted'), value: partner.vettedAt ? new Date(partner.vettedAt).toLocaleString() : tr('Not yet vetted') },
    { label: tr('Concerns flagged'), value: flaggedConcerns.length > 0 ? flaggedConcerns.join('; ') : tr('None') },
    ...(partner.riskOverrideNote ? [{ label: tr('Why proceeded anyway'), value: partner.riskOverrideNote }] : []),
  ];

  return (
    <PageSection title={`${partner.name} (${partner.code})`} subtitle={tr('Recruitment profile, linked work, and message history.')}>
      <div className="toolbar">
        <Link to="/partners">{tr('← Back to partners')}</Link>
        <StatusBadge status={partner.status} />
      </div>
      <Card>
        <CardHeader
          title={tr('Recruitment stage')}
          subheader={tr('Identify the right person, make contact, recruit them, then put the partnership to work')}
        />
        <CardContent>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
            {RECRUITMENT_STAGES.map((candidate, index) => (
              <Stack key={candidate} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                {index > 0 && <Typography variant="body2" color="text.secondary">→</Typography>}
                <Box
                  sx={{
                    px: 1.5,
                    py: 0.5,
                    borderRadius: 1,
                    fontSize: 'var(--font-body-sm)',
                    fontWeight: candidate === stage ? 700 : 400,
                    bgcolor: candidate === stage ? 'var(--accent)' : 'transparent',
                    color: candidate === stage ? 'var(--accent-foreground)' : 'text.secondary',
                    border: '1px solid',
                    borderColor: candidate === stage ? 'primary.main' : 'divider',
                  }}
                >
                  {tr(candidate)}
                </Box>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
      <div className="two-column">
        <Card>
          <CardHeader title={tr('Profile')} />
          <CardContent>
            <div className="status-grid">
              {profileRows.map((row) => (
                <div className="status-row" key={row.label}>
                  <Typography variant="body2" color="text.secondary">{row.label}</Typography>
                  <Typography variant="body2" sx={{ textAlign: 'right', overflowWrap: 'anywhere' }}>{row.value}</Typography>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={tr('Linked work')} subheader={tr('Where this partner is expected to help')} />
          <CardContent>
            {linkedItems.length === 0 ? (
              <EmptyState>{tr('Not linked to any work item yet.')}</EmptyState>
            ) : (
              <div className="status-grid">
                {linkedItems.map((item) => (
                  <div className="status-row" key={item.label}>
                    <Typography variant="body2" color="text.secondary">{item.label}</Typography>
                    <Link to={item.to}>{item.name}</Link>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
      {(partner.supportType === 'FINANCIAL' || partner.supportType === 'TECHNICAL') && (
        <Card>
          <CardHeader title={tr('Integrity vetting')} subheader={tr('FR-50: checked once before this partner\'s first move to Active')} />
          <CardContent>
            <div className="status-grid">
              {vettingRows.map((row) => (
                <div className="status-row" key={row.label}>
                  <Typography variant="body2" color="text.secondary">{row.label}</Typography>
                  <Typography variant="body2" sx={{ textAlign: 'right', overflowWrap: 'anywhere' }}>{row.value}</Typography>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
      <Card>
        <CardHeader title={tr('Communication history')} subheader={tr('Every message prepared for this partner')} />
        <CardContent>
          {messages.length === 0 ? (
            <EmptyState>
              No messages yet. <Link to="/communication">{tr('Draft one in the Communication Builder.')}</Link>
            </EmptyState>
          ) : (
            <TableContainer tabIndex={0} role="region" aria-label={tr('Messages to this partner')}>
              <Table className="data-table">
                <TableHead>
                  <TableRow>
                    <TableCell>{tr('Subject')}</TableCell>
                    <TableCell>{tr('Audience')}</TableCell>
                    <TableCell>{tr('Status')}</TableCell>
                    <TableCell>{tr('Follow Up')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {messages.map((message) => (
                    <TableRow key={message.id}>
                      <TableCell sx={{ fontWeight: 500 }}>{message.subject || tr('(No subject)')}</TableCell>
                      <TableCell>{message.audience || '-'}</TableCell>
                      <TableCell><StatusBadge status={message.status} /></TableCell>
                      <TableCell>{message.followUpDate ?? '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </PageSection>
  );
}
