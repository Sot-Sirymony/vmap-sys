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
import { offerTypeLabels, partnerSupportTypeLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

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
    ACTIVE: hasLinkedWork ? 'Utilize' : 'Recruit',
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
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Unable to load partner.'))
      .finally(() => setLoading(false));
  }, [token, partnerId]);

  if (loading) {
    return <PageSection title="Partner" subtitle="Loading partner details."><Loading /></PageSection>;
  }
  if (error || !partner) {
    return (
      <PageSection title="Partner" subtitle="Partner details.">
        <ErrorMessage message={error || 'Partner not found.'} />
        <Button type="button" onClick={() => navigate('/partners')}>Back to partners</Button>
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
    area && { label: 'Vision Area', name: area.name, to: `/dreams?visionAreaId=${area.id}` },
    dream && { label: 'Dream', name: dream.title, to: `/dreams/${dream.id}` },
    goal && { label: 'Goal', name: goal.title, to: `/steps?goalId=${goal.id}` },
    step && { label: 'Step', name: step.title, to: `/tasks?stepId=${step.id}` },
    task && { label: 'Task', name: task.title, to: `/tasks?stepId=${task.stepId}` },
  ].filter((item): item is { label: string; name: string; to: string } => Boolean(item));

  const profileRows: { label: string; value: string }[] = [
    { label: 'Role', value: partner.role ?? '-' },
    { label: 'Organization', value: partner.organization ?? '-' },
    { label: 'Email', value: partner.email ?? '-' },
    { label: 'Phone', value: partner.phone ?? '-' },
    { label: 'Strength', value: partner.strength ?? '-' },
    { label: 'Support Type', value: partnerSupportTypeLabels[partner.supportType] },
    { label: 'Offer Type', value: partner.offerType ? offerTypeLabels[partner.offerType] : '-' },
    { label: 'Notes', value: partner.notes ?? '-' },
  ];

  return (
    <PageSection title={`${partner.name} (${partner.code})`} subtitle="Recruitment profile, linked work, and message history.">
      <div className="toolbar">
        <Link to="/partners">← Back to partners</Link>
        <StatusBadge status={partner.status} />
      </div>
      <Card>
        <CardHeader
          title="Recruitment stage"
          subheader="Identify the right person, make contact, recruit them, then put the partnership to work"
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
                  {candidate}
                </Box>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
      <div className="two-column">
        <Card>
          <CardHeader title="Profile" />
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
          <CardHeader title="Linked work" subheader="Where this partner is expected to help" />
          <CardContent>
            {linkedItems.length === 0 ? (
              <EmptyState>Not linked to any work item yet.</EmptyState>
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
      <Card>
        <CardHeader title="Communication history" subheader="Every message prepared for this partner" />
        <CardContent>
          {messages.length === 0 ? (
            <EmptyState>
              No messages yet. <Link to="/communication">Draft one in the Communication Builder.</Link>
            </EmptyState>
          ) : (
            <TableContainer>
              <Table className="data-table">
                <TableHead>
                  <TableRow>
                    <TableCell>Subject</TableCell>
                    <TableCell>Audience</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Follow Up</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {messages.map((message) => (
                    <TableRow key={message.id}>
                      <TableCell sx={{ fontWeight: 500 }}>{message.subject || '(No subject)'}</TableCell>
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
