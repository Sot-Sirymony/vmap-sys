import LinearProgress from '@mui/material/LinearProgress';
import { progressTone } from '../../utils/progressTone';

/**
 * A progress bar. With `colored`, the bar takes the colour of its progress
 * level (red, orange, blue, green — see progressTone); otherwise it uses the
 * app's accent colour.
 */
export function ProgressBar({ value, colored = false }: { value: number; colored?: boolean }) {
  const percent = Math.min(100, Math.max(0, value));
  return (
    <LinearProgress
      variant="determinate"
      value={percent}
      aria-label={`Progress ${percent}%`}
      className={colored ? `progress-bar--${progressTone(percent)}` : undefined}
      sx={{ width: '100%', borderRadius: 999, '& .MuiLinearProgress-bar': { transition: 'transform var(--motion-slow) var(--motion-ease)' } }}
    />
  );
}
