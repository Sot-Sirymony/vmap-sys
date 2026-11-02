import MuiSkeleton from '@mui/material/Skeleton';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import { tr } from '../../i18n/translate';

type LoadingVariant = 'lines' | 'table' | 'cards' | 'tree';

type LoadingProps = {
  /**
   * FR-20.3: match the skeleton to the surface it stands in for, so content
   * arriving does not re-flow the layout (M-8). `table` for list pages,
   * `cards` for tile grids/boards, `tree` for the vision map, `lines` when
   * the shape is unknown.
   */
  variant?: LoadingVariant;
  /** Row/tile count hint; defaults per variant. */
  rows?: number;
};

export function Loading({ variant = 'lines', rows }: LoadingProps) {
  if (variant === 'table') {
    const count = rows ?? 6;
    return (
      <Stack spacing={1} role="status" aria-label={tr('Loading')}>
        <MuiSkeleton variant="rounded" height={40} />
        {Array.from({ length: count }, (_, index) => (
          <MuiSkeleton variant="rounded" height={44} key={index} />
        ))}
      </Stack>
    );
  }

  if (variant === 'cards') {
    const count = rows ?? 3;
    return (
      <Box role="status" aria-label={tr('Loading')} sx={{ display: 'grid', gap: 2, gridTemplateColumns: `repeat(auto-fit, minmax(220px, 1fr))` }}>
        {Array.from({ length: count }, (_, index) => (
          <MuiSkeleton variant="rounded" height={120} key={index} />
        ))}
      </Box>
    );
  }

  if (variant === 'tree') {
    return (
      <Stack spacing={1} role="status" aria-label={tr('Loading')}>
        <MuiSkeleton variant="rounded" height={72} />
        <Stack spacing={1} sx={{ pl: 4 }}>
          <MuiSkeleton variant="rounded" height={56} />
          <Stack spacing={1} sx={{ pl: 4 }}>
            <MuiSkeleton variant="rounded" height={44} />
            <MuiSkeleton variant="rounded" height={44} />
          </Stack>
          <MuiSkeleton variant="rounded" height={56} />
        </Stack>
      </Stack>
    );
  }

  return (
    <Stack spacing={1} role="status" aria-label={tr('Loading')}>
      <MuiSkeleton variant="text" height={16} width="100%" />
      <MuiSkeleton variant="text" height={16} width="80%" />
      <MuiSkeleton variant="text" height={16} width="60%" />
    </Stack>
  );
}
