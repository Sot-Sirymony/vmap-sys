import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListSubheader from '@mui/material/ListSubheader';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Check, Contrast, Monitor, Moon, Settings2, Sun, Waves } from 'lucide-react';
import { accentOptions, backgroundTones, themePresets, type AccentId, type Density } from '../../theme';
import { useThemeSettings, type FontSize, type ThemeMode } from '../../context/ThemeModeContext';

const MODE_OPTIONS: { value: ThemeMode; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
];

const DENSITY_OPTIONS: { value: Density; label: string }[] = [
  { value: 'comfortable', label: 'Comfortable' },
  { value: 'compact', label: 'Compact' },
];

const FONT_SIZE_OPTIONS: { value: FontSize; label: string }[] = [
  { value: 'small', label: 'Small' },
  { value: 'medium', label: 'Medium' },
  { value: 'large', label: 'Large' },
];

const SUBHEADER_SX = { lineHeight: '30px', bgcolor: 'transparent' } as const;

const MODE_ICONS: Record<ThemeMode, typeof Sun> = {
  light: Sun,
  dark: Moon,
  system: Monitor,
};

/**
 * A checkable row in the menu. The MenuItem itself is the control — role
 * `menuitemcheckbox` with `aria-checked` — and the Switch is presentational,
 * with pointer events off so a click can only ever be counted once.
 *
 * Doing it the other way round (an interactive Switch inside a clickable row)
 * needs the child's click stopped from bubbling, and leaves two focusable things
 * in one row for a keyboard user to tab through. This way the row is one stop,
 * Enter and Space toggle it, and screen readers announce the checked state.
 */
function ToggleRow({
  label,
  icon,
  checked,
  onToggle,
}: {
  label: string;
  icon: ReactNode;
  checked: boolean;
  onToggle: () => void;
}) {
  return (
    <MenuItem role="menuitemcheckbox" aria-checked={checked} onClick={onToggle}>
      <ListItemIcon sx={{ minWidth: 28 }}>{icon}</ListItemIcon>
      <Typography variant="body2" sx={{ flexGrow: 1 }}>{label}</Typography>
      <Switch size="small" checked={checked} tabIndex={-1} disableRipple sx={{ pointerEvents: 'none' }} />
    </MenuItem>
  );
}

/**
 * Quick-access Appearance controls (FR-18.6, extended by FR-39): theme preset,
 * mode, accent, density, text size, and the two accessibility toggles. Choices
 * apply instantly and save to the user's account (FR-39.6).
 *
 * This menu is deliberately the *shortcut*, not the whole surface — the full
 * page at /settings/appearance adds a live preview, which is what you want when
 * you're choosing rather than adjusting.
 */
export function AppearanceMenu() {
  const { settings, resolvedMode, preset, update, applyPreset } = useThemeSettings();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const navigate = useNavigate();

  const ModeIcon = MODE_ICONS[settings.mode];

  function check(active: boolean) {
    return <ListItemIcon sx={{ minWidth: 28 }}>{active && <Check size={16} />}</ListItemIcon>;
  }

  return (
    <>
      <Tooltip title="Appearance">
        <IconButton onClick={(event) => setAnchor(event.currentTarget)} aria-label="Appearance settings" size="small">
          <ModeIcon size={18} />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={() => setAnchor(null)}
        slotProps={{ list: { dense: true }, paper: { sx: { maxHeight: '80vh', width: 268 } } }}
      >
        <ListSubheader sx={SUBHEADER_SX}>Theme</ListSubheader>
        {themePresets.map((option) => (
          <MenuItem key={option.id} onClick={() => applyPreset(option.mode, option.accent)}>
            {check(preset === option.stored)}
            <Box>
              <Typography variant="body2">{option.label}</Typography>
              <Typography variant="caption" color="text.secondary">{option.description}</Typography>
            </Box>
          </MenuItem>
        ))}
        {/* FR-39.1: shown, never selectable — "Custom" is what the current
            settings ARE when they match no preset, not a thing you can pick. */}
        {preset === 'CUSTOM' && (
          <MenuItem disabled>
            {check(true)}
            <Typography variant="body2">Custom</Typography>
          </MenuItem>
        )}

        <Divider />
        <ListSubheader sx={SUBHEADER_SX}>Mode</ListSubheader>
        {MODE_OPTIONS.map((option) => (
          <MenuItem key={option.value} onClick={() => update({ mode: option.value })}>
            {check(settings.mode === option.value)}
            {option.label}
          </MenuItem>
        ))}

        <Divider />
        <ListSubheader sx={SUBHEADER_SX}>Accent</ListSubheader>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, px: 2, py: 0.5 }}>
          {(Object.keys(accentOptions) as AccentId[]).map((accentId) => {
            const swatch = accentOptions[accentId][resolvedMode];
            const selected = settings.accent === accentId;
            return (
              <Tooltip title={accentOptions[accentId].label} key={accentId}>
                <Box
                  component="button"
                  type="button"
                  onClick={() => update({ accent: accentId })}
                  aria-label={`${accentOptions[accentId].label} accent`}
                  aria-pressed={selected}
                  sx={{
                    width: 22,
                    height: 22,
                    borderRadius: '50%',
                    cursor: 'pointer',
                    bgcolor: swatch.main,
                    border: '2px solid',
                    borderColor: selected ? 'text.primary' : 'transparent',
                    padding: 0,
                  }}
                />
              </Tooltip>
            );
          })}
        </Box>

        <Divider sx={{ mt: 1 }} />
        <ListSubheader sx={SUBHEADER_SX}>Background</ListSubheader>
        {/* FR-40.5: high contrast paints pure white/black surfaces, so the tone
            has no effect while it is on. Disabling the options states that,
            instead of letting a click appear to work and change nothing. */}
        {settings.highContrast && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 2, pb: 0.5 }}>
            Off while high contrast is on
          </Typography>
        )}
        {backgroundTones.map((tone) => (
          <MenuItem
            key={tone.id}
            disabled={settings.highContrast}
            onClick={() => update({ backgroundTone: tone.id })}
          >
            {check(settings.backgroundTone === tone.id)}
            <Box
              sx={{
                width: 16,
                height: 16,
                borderRadius: '3px',
                mr: 1,
                flexShrink: 0,
                border: '1px solid',
                borderColor: 'divider',
                bgcolor: tone.preview[resolvedMode][0],
              }}
            />
            {tone.label}
          </MenuItem>
        ))}

        <Divider />
        <ListSubheader sx={SUBHEADER_SX}>Density</ListSubheader>
        {DENSITY_OPTIONS.map((option) => (
          <MenuItem key={option.value} onClick={() => update({ density: option.value })}>
            {check(settings.density === option.value)}
            {option.label}
          </MenuItem>
        ))}

        <Divider />
        <ListSubheader sx={SUBHEADER_SX}>Text size</ListSubheader>
        {FONT_SIZE_OPTIONS.map((option) => (
          <MenuItem key={option.value} onClick={() => update({ fontSize: option.value })}>
            {check(settings.fontSize === option.value)}
            {option.label}
          </MenuItem>
        ))}

        <Divider />
        <ListSubheader sx={SUBHEADER_SX}>Accessibility</ListSubheader>
        {/* FR-39.3/39.4: toggles rather than presets — both must hold whatever
            look the user chose, so they compose with mode instead of replacing it. */}
        <ToggleRow
          label="High contrast"
          icon={<Contrast size={16} />}
          checked={settings.highContrast}
          onToggle={() => update({ highContrast: !settings.highContrast })}
        />
        <ToggleRow
          label="Reduce motion"
          icon={<Waves size={16} />}
          checked={settings.reduceMotion}
          onToggle={() => update({ reduceMotion: !settings.reduceMotion })}
        />

        <Divider />
        <MenuItem
          onClick={() => {
            setAnchor(null);
            navigate('/settings/appearance');
          }}
        >
          <ListItemIcon sx={{ minWidth: 28 }}><Settings2 size={16} /></ListItemIcon>
          <Typography variant="body2">All appearance settings…</Typography>
        </MenuItem>
      </Menu>
    </>
  );
}
