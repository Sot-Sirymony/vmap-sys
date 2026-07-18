import LinearProgress from '@mui/material/LinearProgress';

export function ProgressBar({ value }: { value: number }) {
  const percent = Math.min(100, Math.max(0, value));
  return <LinearProgress variant="determinate" value={percent} aria-label={`Progress ${percent}%`} sx={{ width: '100%', borderRadius: 999, '& .MuiLinearProgress-bar': { transition: 'transform var(--motion-slow) var(--motion-ease)' } }} />;
}
