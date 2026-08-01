import { Link } from 'react-router';
import { CheckCircle2, Compass, ListChecks, Sparkles } from 'lucide-react';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { semanticTints } from '../../theme';

type GettingStartedProps = {
  hasArea: boolean;
  hasDream: boolean;
  hasGoal: boolean;
};

/**
 * FR-21.1 guided first steps. Replaces the old single-step first-run card:
 * each step chains into the next (audit H-01 — the old card dead-ended after
 * the first vision area), and the checklist stays visible until the user has
 * broken a dream down into at least one goal.
 */
export function GettingStarted({ hasArea, hasDream, hasGoal }: GettingStartedProps) {
  const steps = [
    {
      icon: Compass,
      done: hasArea,
      title: 'Create a Vision Area',
      description: 'A major area of your life or work — like Career, Health, or Family.',
      actionLabel: 'Create a vision area',
      to: '/vision-areas?create=area',
    },
    {
      icon: Sparkles,
      done: hasDream,
      title: 'Add a Dream',
      description: 'A meaningful outcome you want to reach. A short guide helps you make it clear.',
      actionLabel: 'Add a dream',
      to: '/dreams?create=dream',
    },
    {
      icon: ListChecks,
      done: hasGoal,
      title: 'Break it down',
      description: 'Turn the dream into goals, steps, and tasks on the Vision Map.',
      actionLabel: 'Open the Vision Map',
      to: '/vision-map',
    },
  ];
  const current = steps.findIndex((step) => !step.done);

  return (
    <Card>
      <CardContent>
        <Typography variant="h2" component="h2" sx={{ mb: 0.5 }}>Get started</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Three steps from a blank page to a plan. This checklist disappears once your first dream has goals.
        </Typography>
        <Stack spacing={1.5}>
          {steps.map((step, index) => {
            const isCurrent = index === current;
            return (
              <Stack direction="row" spacing={1.5} key={step.title} sx={{ alignItems: 'flex-start', opacity: step.done || isCurrent ? 1 : 0.55 }}>
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    bgcolor: step.done ? semanticTints.positive.bg : 'var(--accent)',
                    color: step.done ? semanticTints.positive.fg : 'var(--accent-foreground)',
                  }}
                >
                  {step.done ? <CheckCircle2 size={18} /> : <step.icon size={18} />}
                </Box>
                <Box sx={{ flexGrow: 1 }}>
                  <Typography variant="subtitle1" component="p" sx={{ textDecoration: step.done ? 'line-through' : 'none' }}>
                    {index + 1}. {step.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">{step.description}</Typography>
                </Box>
                {isCurrent && (
                  <MuiButton component={Link} to={step.to} variant="contained" disableElevation size="small" sx={{ flexShrink: 0 }}>
                    {step.actionLabel}
                  </MuiButton>
                )}
              </Stack>
            );
          })}
        </Stack>
      </CardContent>
    </Card>
  );
}
