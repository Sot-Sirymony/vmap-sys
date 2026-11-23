import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

type TooltipPayloadItem = {
  name?: string;
  value?: number | string;
  color?: string;
  payload?: { fill?: string };
};

// A minimal, MUI-styled replacement for shadcn's ChartTooltipContent — used
// as the `content` render prop for Recharts' own <Tooltip>, so charts stay
// on plain Recharts + MUI without the shadcn chart wrapper.
// BRD 25 §7.1: raised surface, 8px radius, overlay shadow, a quiet title row,
// then swatch · label · right-aligned tabular value.
export function ChartTooltipContent({ active, payload, label }: { active?: boolean; payload?: TooltipPayloadItem[]; label?: string | number }) {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <Box sx={{ bgcolor: 'var(--style-chart-tooltip-bg)', backdropFilter: 'var(--style-chart-tooltip-blur)', WebkitBackdropFilter: 'var(--style-chart-tooltip-blur)', border: 'var(--style-floating-border)', borderRadius: 'var(--style-chart-tooltip-radius)', boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)', '--text-tertiary': 'var(--style-floating-text-tertiary)', px: 1.5, py: 1.25, minWidth: 120, fontVariantNumeric: 'tabular-nums' }}>
      {label !== undefined && label !== '' && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>{label}</Typography>
      )}
      {payload.map((item, index) => (
        <Stack key={index} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Box sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: item.payload?.fill ?? item.color, flexShrink: 0 }} />
          <Typography variant="caption" color="text.secondary">{item.name}</Typography>
          <Typography variant="caption" sx={{ fontWeight: 600, ml: 'auto', pl: 2 }}>{item.value}</Typography>
        </Stack>
      ))}
    </Box>
  );
}
