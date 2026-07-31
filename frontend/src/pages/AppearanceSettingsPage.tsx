import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { PageSection } from './PageSection';
import { ProgressBar } from '../components/common/ProgressBar';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { accentOptions, backgroundTones, themePresets, type AccentId } from '../theme';
import { useThemeSettings, type FontSize, type ThemeMode } from '../context/ThemeModeContext';
import type { Density } from '../theme';

const MODE_OPTIONS: { value: ThemeMode; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
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

/** One labelled block of controls, so the page reads as a list of decisions. */
function SettingGroup({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <Box component="section" sx={{ mb: 3 }}>
      <Typography variant="h3" sx={{ mb: 0.25 }}>{title}</Typography>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>{hint}</Typography>
      {children}
    </Box>
  );
}

/**
 * FR-39.5 — the full Appearance surface.
 *
 * The reason this page exists alongside the header menu is the preview: a colour
 * choice is hard to judge from a swatch, and the components below are the actual
 * StatusBadge, PriorityBadge, and ProgressBar the app uses, not mock-ups of
 * them. What you see here is literally what every table and board will look
 * like, because it is rendered by the same components reading the same theme.
 */
export function AppearanceSettingsPage() {
  const { settings, resolvedMode, preset, update, applyPreset, reset, saveError } = useThemeSettings();

  const activePresetLabel = preset === 'CUSTOM'
    ? 'Custom'
    : themePresets.find((option) => option.stored === preset)?.label ?? 'Custom';

  return (
    <PageSection
      title="Appearance"
      subtitle="Choose how the app looks. Changes apply immediately and are saved to your account."
      actions={<Button onClick={reset} size="small" variant="outlined">Reset to defaults</Button>}
    >
      {/* BR-33: a failed save never blocks the choice — it applies locally and
          is retried. Saying so is better than pretending it saved. */}
      {saveError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Your choice is applied on this device, but could not be saved to your account: {saveError}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3 }}>
        <Box>
          <SettingGroup
            title="Theme"
            hint={`A preset sets the mode and accent together. Currently: ${activePresetLabel}.`}
          >
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1 }}>
              {themePresets.map((option) => {
                const selected = preset === option.stored;
                return (
                  <Card
                    key={option.id}
                    component="button"
                    type="button"
                    onClick={() => applyPreset(option.mode, option.accent)}
                    aria-pressed={selected}
                    sx={{
                      textAlign: 'left',
                      cursor: 'pointer',
                      p: 1.25,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      borderColor: selected ? 'primary.main' : 'divider',
                      borderWidth: selected ? 2 : 1,
                    }}
                  >
                    {/* The dot shows the accent in the mode the preset picks, not
                        the mode currently on screen — otherwise a dark preset's
                        swatch would lie while you're in light mode. */}
                    <Box
                      sx={{
                        width: 18,
                        height: 18,
                        borderRadius: '50%',
                        flexShrink: 0,
                        bgcolor: accentOptions[option.accent][option.mode === 'system' ? resolvedMode : option.mode].main,
                      }}
                    />
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body2" noWrap>{option.label}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                        {option.description}
                      </Typography>
                    </Box>
                    {selected && <Check size={16} style={{ marginLeft: 'auto', flexShrink: 0 }} />}
                  </Card>
                );
              })}
            </Box>
            {preset === 'CUSTOM' && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                Your settings don't match a preset, so they're shown as Custom. Pick a preset above to go back to one.
              </Typography>
            )}
          </SettingGroup>

          <SettingGroup title="Mode" hint="System follows your device setting and updates when it changes.">
            <ToggleButtonGroup
              exclusive
              size="small"
              value={settings.mode}
              onChange={(_, value: ThemeMode | null) => value && update({ mode: value })}
            >
              {MODE_OPTIONS.map(({ value, label, icon: Icon }) => (
                <ToggleButton key={value} value={value} sx={{ gap: 0.75, textTransform: 'none' }}>
                  <Icon size={15} />
                  {label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </SettingGroup>

          <SettingGroup title="Accent" hint="Every accent is checked for readable contrast in both light and dark.">
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.25 }}>
              {(Object.keys(accentOptions) as AccentId[]).map((accentId) => {
                const selected = settings.accent === accentId;
                return (
                  <Tooltip key={accentId} title={accentOptions[accentId].label}>
                    <Box
                      component="button"
                      type="button"
                      onClick={() => update({ accent: accentId })}
                      aria-label={`${accentOptions[accentId].label} accent`}
                      aria-pressed={selected}
                      sx={{
                        width: 30,
                        height: 30,
                        borderRadius: '50%',
                        cursor: 'pointer',
                        padding: 0,
                        bgcolor: accentOptions[accentId][resolvedMode].main,
                        border: '2px solid',
                        borderColor: selected ? 'text.primary' : 'transparent',
                        outlineOffset: 2,
                      }}
                    />
                  </Tooltip>
                );
              })}
            </Box>
          </SettingGroup>

          <SettingGroup
            title="Background"
            hint={
              settings.highContrast
                ? 'Unavailable while high contrast is on — it uses pure white or black surfaces.'
                : 'Changes the page, cards, and sidebar together.'
            }
          >
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {backgroundTones.map((tone) => {
                const selected = settings.backgroundTone === tone.id;
                const [canvas, card] = tone.preview[resolvedMode];
                return (
                  <Tooltip key={tone.id} title={tone.description}>
                    {/* The span keeps the tooltip working on a disabled control —
                        MUI can't attach a listener to a disabled button. */}
                    <span>
                      <Box
                        component="button"
                        type="button"
                        disabled={settings.highContrast}
                        onClick={() => update({ backgroundTone: tone.id })}
                        aria-label={`${tone.label} background`}
                        aria-pressed={selected}
                        sx={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'stretch',
                          gap: 0.5,
                          width: 78,
                          p: 0.75,
                          borderRadius: 1,
                          cursor: settings.highContrast ? 'not-allowed' : 'pointer',
                          opacity: settings.highContrast ? 0.5 : 1,
                          bgcolor: 'transparent',
                          border: '2px solid',
                          borderColor: selected ? 'primary.main' : 'divider',
                        }}
                      >
                        {/* A miniature of the real thing: canvas with a card on
                            it, so Flat's lack of a step is visible up front. */}
                        <Box sx={{ height: 34, borderRadius: '3px', bgcolor: canvas, border: '1px solid', borderColor: 'divider', p: 0.5 }}>
                          <Box sx={{ height: '100%', borderRadius: '2px', bgcolor: card, border: '1px solid', borderColor: 'divider' }} />
                        </Box>
                        <Typography variant="caption" noWrap>{tone.label}</Typography>
                      </Box>
                    </span>
                  </Tooltip>
                );
              })}
            </Box>
          </SettingGroup>

          <SettingGroup title="Density" hint="Compact fits more rows on screen without hiding anything.">
            <ToggleButtonGroup
              exclusive
              size="small"
              value={settings.density}
              onChange={(_, value: Density | null) => value && update({ density: value })}
            >
              {DENSITY_OPTIONS.map(({ value, label }) => (
                <ToggleButton key={value} value={value} sx={{ textTransform: 'none' }}>{label}</ToggleButton>
              ))}
            </ToggleButtonGroup>
          </SettingGroup>

          <SettingGroup title="Text size" hint="Scales all text proportionally, unlike browser zoom.">
            <ToggleButtonGroup
              exclusive
              size="small"
              value={settings.fontSize}
              onChange={(_, value: FontSize | null) => value && update({ fontSize: value })}
            >
              {FONT_SIZE_OPTIONS.map(({ value, label }) => (
                <ToggleButton key={value} value={value} sx={{ textTransform: 'none' }}>{label}</ToggleButton>
              ))}
            </ToggleButtonGroup>
          </SettingGroup>

          <SettingGroup title="Accessibility" hint="These work with any theme above — they don't replace your choice.">
            <FormControlLabel
              control={
                <Switch
                  checked={settings.highContrast}
                  onChange={(event) => update({ highContrast: event.target.checked })}
                />
              }
              label={
                <Box>
                  <Typography variant="body2">High contrast</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Stronger text, borders, and focus rings.
                  </Typography>
                </Box>
              }
              sx={{ display: 'flex', mb: 1 }}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={settings.reduceMotion}
                  onChange={(event) => update({ reduceMotion: event.target.checked })}
                />
              }
              label={
                <Box>
                  <Typography variant="body2">Reduce motion</Typography>
                  {/* Honest about the one-way rule: this can only ask for less
                      motion, so switching it off doesn't override the OS. */}
                  <Typography variant="caption" color="text.secondary">
                    Turns off animations. If your device already asks for reduced motion, that still applies.
                  </Typography>
                </Box>
              }
              sx={{ display: 'flex' }}
            />
          </SettingGroup>
        </Box>

        <Box>
          <Typography variant="h3" sx={{ mb: 0.25 }}>Preview</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            Real components, not mock-ups — this is how your data will look.
          </Typography>
          <Card>
            <CardContent sx={{ display: 'grid', gap: 2 }}>
              <Box>
                <Typography variant="h2">Prepare one concept note</Typography>
                <Typography variant="body2" color="text.secondary">
                  Goal under Career Development · target 30 Sep
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                <StatusBadge status="IN_PROGRESS" />
                <StatusBadge status="BLOCKED" />
                <StatusBadge status="COMPLETED" />
                <StatusBadge status="WAITING" />
              </Box>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                <PriorityBadge priority="LOW" />
                <PriorityBadge priority="MEDIUM" />
                <PriorityBadge priority="HIGH" />
                <PriorityBadge priority="CRITICAL" />
              </Box>

              <ProgressBar value={62} />

              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Button variant="contained" size="small">Primary action</Button>
                <Button variant="outlined" size="small">Secondary</Button>
                <Button size="small" color="error">Delete</Button>
              </Box>

              <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', fontSize: 'var(--font-body-sm)' }}>
                <Box component="thead">
                  <Box component="tr" sx={{ textAlign: 'left', color: 'text.secondary' }}>
                    <Box component="th" sx={{ p: 'var(--cell-pad-y) var(--cell-pad-x)', borderBottom: 1, borderColor: 'divider' }}>Task</Box>
                    <Box component="th" sx={{ p: 'var(--cell-pad-y) var(--cell-pad-x)', borderBottom: 1, borderColor: 'divider' }}>Due</Box>
                  </Box>
                </Box>
                <Box component="tbody">
                  {[
                    { task: 'Search PubMed', due: '12 Aug' },
                    { task: 'Summarize key findings', due: '19 Aug' },
                  ].map((row) => (
                    <Box component="tr" key={row.task}>
                      <Box component="td" sx={{ p: 'var(--cell-pad-y) var(--cell-pad-x)', borderBottom: 1, borderColor: 'divider' }}>{row.task}</Box>
                      <Box component="td" sx={{ p: 'var(--cell-pad-y) var(--cell-pad-x)', borderBottom: 1, borderColor: 'divider' }}>{row.due}</Box>
                    </Box>
                  ))}
                </Box>
              </Box>

              <Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center' }}>
                <Chip size="small" label="Focus mode" color="primary" />
                <Typography variant="caption" sx={{ color: 'var(--text-faint)' }}>G-001 · updated just now</Typography>
              </Box>
            </CardContent>
          </Card>
        </Box>
      </Box>
    </PageSection>
  );
}
