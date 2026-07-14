import Chip from '@mui/material/Chip';
import { alpha, darken } from '@mui/material/styles';

// Shared badge treatment for enum values (status, priority). The tint carries
// the value at a glance; the label is what actually names it. Text is darkened
// off the same hue rather than using the hue directly, which on its own tint
// would fall under the 4.5:1 contrast minimum for small text.
export function TintedChip({ label, hue }: { label: string; hue: string }) {
  return (
    <Chip
      size="small"
      label={label}
      sx={{
        bgcolor: alpha(hue, 0.14),
        color: darken(hue, 0.25),
        border: `1px solid ${alpha(hue, 0.35)}`,
      }}
    />
  );
}
