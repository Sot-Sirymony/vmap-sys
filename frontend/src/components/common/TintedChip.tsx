import Chip from '@mui/material/Chip';
import { alpha } from '@mui/material/styles';

/**
 * The tint for an enum value (status, priority), shared by badges and by
 * dropdowns that show the same value, so a Blocked dropdown looks like a
 * Blocked badge. FR-26.4: the text mixes the hue with the theme foreground —
 * dark text on light surfaces, light text on dark — so the 4.5:1 minimum holds
 * in both modes and under every accent, while the hue identity stays visible.
 */
export function tintColors(hue: string) {
  return {
    background: `color-mix(in srgb, ${hue} 14%, var(--card))`,
    text: `color-mix(in srgb, ${hue} 42%, var(--foreground))`,
    border: alpha(hue, 0.35),
  };
}

// Shared badge treatment for enum values (status, priority). The tint carries
// the value at a glance; the label is what actually names it.
export function TintedChip({ label, hue }: { label: string; hue: string }) {
  const tint = tintColors(hue);
  return (
    <Chip
      size="small"
      label={label}
      sx={{
        bgcolor: tint.background,
        color: tint.text,
        border: `1px solid ${tint.border}`,
      }}
    />
  );
}
