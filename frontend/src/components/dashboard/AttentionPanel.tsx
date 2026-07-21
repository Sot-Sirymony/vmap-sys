import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import { semanticTints } from '../../theme';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Ban, CalendarClock, CheckCircle2, GitBranch, Rocket, Sparkles, Target } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { DashboardAttention } from '../../types/vision';

type Finding = {
  key: string;
  icon: LucideIcon;
  count: number;
  title: string;
  // Why this is a dead end, in the user's terms — not "rule violated" but what
  // it costs them.
  why: string;
  to: string;
  action: string;
};

// Appends the dashboard's vision-area scope so the target page shows the same
// rows the finding counted; without a scope the link keeps only its own filter.
function scoped(base: string, visionAreaId?: string) {
  if (!visionAreaId) {
    return base;
  }
  return `${base}${base.includes('?') ? '&' : '?'}visionAreaId=${visionAreaId}`;
}

function buildFindings(attention: DashboardAttention, overdueCount: number, visionAreaId?: string): Finding[] {
  // Ranked: hard blockers first, time pressure second, structural gaps after.
  const findings: Finding[] = [
    {
      key: 'blocked-no-partner',
      icon: Ban,
      count: attention.blockedTasksWithoutPartner.length,
      title: 'Blocked tasks with no partner',
      why: 'Nothing is lined up to unblock these. They will stay stuck until someone helps.',
      to: scoped('/tasks?status=BLOCKED', visionAreaId),
      action: 'Find a partner',
    },
    {
      key: 'overdue',
      icon: CalendarClock,
      count: overdueCount,
      title: 'Overdue tasks',
      why: 'Past their due date and not completed. Reschedule them or finish them — a stale date helps nobody.',
      to: scoped('/tasks?overdue=true', visionAreaId),
      action: 'Review overdue',
    },
    {
      key: 'complex-no-tasks',
      icon: GitBranch,
      count: attention.complexStepsWithoutTasks.length,
      title: 'Complex steps with no tasks',
      why: 'A step marked complex has nothing to actually do. There is no first action to take.',
      to: scoped('/steps?complex=true', visionAreaId),
      action: 'Break into tasks',
    },
    {
      key: 'dreams-no-goals',
      icon: Sparkles,
      count: attention.dreamsWithoutGoals.length,
      title: 'Dreams with no goals',
      why: 'A dream with no goals is still a wish — nothing can be executed on it.',
      to: scoped('/dreams', visionAreaId),
      action: 'Add goals',
    },
    {
      key: 'goals-no-steps',
      icon: Target,
      count: attention.goalsWithoutSteps.length,
      title: 'Goals with no steps',
      why: 'This goal has no path to it yet.',
      to: scoped('/goals', visionAreaId),
      action: 'Add steps',
    },
    {
      key: 'moonshot-inactive',
      icon: Rocket,
      count: (attention.inactiveMoonshotGoals ?? []).length,
      title: 'Moonshots not started',
      why: 'Ambition going stale — start the first step, or consciously revise the goal.',
      to: scoped('/goals?moonshot=true', visionAreaId),
      action: 'Start or revise',
    },
    {
      key: 'moonshot-dream-inactive',
      icon: Rocket,
      count: (attention.inactiveMoonshotDreams ?? []).length,
      title: 'Moonshot dreams still an idea',
      why: 'A moonshot that never left the idea stage is ambition going stale — promote it to active, or consciously revise it.',
      to: scoped('/dreams?moonshot=true', visionAreaId),
      action: 'Start or revise',
    },
  ];

  return findings.filter((finding) => finding.count > 0);
}

/**
 * The method's own rules, checked against the user's records. This is the one
 * part of the dashboard that tells the user what to *do* rather than what is —
 * so it disappears entirely when there is nothing to fix, rather than sitting
 * there as a permanent scold.
 */
export function AttentionPanel({ attention, overdueCount = 0, visionAreaId }: { attention: DashboardAttention | undefined; overdueCount?: number; visionAreaId?: string }) {
  if (!attention) {
    return null;
  }

  const findings = buildFindings(attention, overdueCount, visionAreaId);

  if (findings.length === 0) {
    return (
      <Card>
        <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 2.5 }}>
          <Box sx={{ color: semanticTints.positive.fg, display: 'flex' }}>
            <CheckCircle2 size={18} />
          </Box>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>Your map holds together</Typography>
            <Typography variant="caption" color="text.secondary">
              Every dream has goals, every goal has steps, every complex step has tasks, and nothing is
              blocked without someone to unblock it.
            </Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader
        title="Needs your attention"
        subheader="Places where the map has a dead end — nothing below can move until these are fixed"
      />
      <CardContent sx={{ pt: 0 }}>
        <Stack divider={<Box sx={{ borderTop: '1px solid', borderColor: 'divider' }} />}>
          {findings.map((finding) => (
            <Stack
              key={finding.key}
              component={Link}
              to={finding.to}
              direction="row"
              sx={{
                alignItems: 'center',
                gap: 1.5,
                py: 1.5,
                textDecoration: 'none',
                color: 'inherit',
                '&:hover .attention-action': { textDecoration: 'underline' },
              }}
            >
              <Box
                sx={{
                  width: 32,
                  height: 32,
                  borderRadius: 1,
                  flexShrink: 0,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: semanticTints.warning.bg,
                  color: semanticTints.warning.fg,
                }}
              >
                <finding.icon size={16} />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{finding.title}</Typography>
                  <Chip size="small" label={finding.count} sx={{ height: 20, fontWeight: 700 }} />
                </Stack>
                <Typography variant="caption" color="text.secondary">{finding.why}</Typography>
              </Box>
              <Typography
                className="attention-action"
                variant="body2"
                sx={{ color: 'primary.main', fontWeight: 600, flexShrink: 0 }}
              >
                {finding.action}
              </Typography>
            </Stack>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
