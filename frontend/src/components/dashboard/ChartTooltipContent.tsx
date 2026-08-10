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
export function ChartTooltipContent({ active, payload }: { active?: boolean; payload?: TooltipPayloadItem[] }) {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 1, boxShadow: 'var(--shadow-8)', px: 1.5, py: 1 }}>
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
