import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Slider from '@mui/material/Slider';
import Switch from '@mui/material/Switch';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useEffect, useState, type ChangeEvent } from 'react';
import { fileToBackgroundDataUrl, MAX_BACKGROUND_DATA_URL_LENGTH } from '../utils/backgroundImage';
import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { loadFont } from '../fonts';
import { PageSection } from './PageSection';
import { ProgressBar } from '../components/common/ProgressBar';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { accentOptions, backgroundTones, fontFamilies, fontStack, interfaceStyles, styleShape, themePresets, type AccentId, type InterfaceStyleId } from '../theme';
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

/**
 * FR-48 — a miniature of the app's own anatomy (sidebar rail, nav pills, a card
 * on a canvas), drawn with the shape tokens the style actually applies.
 *
 * A swatch cannot show this choice: the difference is corner radius, shadow
 * diffusion, and pill-vs-square navigation, none of which a colour chip can
 * express. So the preview is built from `styleShape` rather than hand-drawn —
 * it cannot describe a look the theme does not produce.
 */
function StylePreview({ styleId, mode }: { styleId: InterfaceStyleId; mode: 'light' | 'dark' }) {
  const shape = styleShape(styleId, mode);
  return (
    <Box
      aria-hidden
      sx={{
        display: 'flex',
        gap: 0.5,
        height: 52,
        p: 0.5,
        bgcolor: 'var(--page)',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: `${Math.min(shape.cardRadius, 10)}px`,
        overflow: 'hidden',
      }}
    >
      {/* The rail, with the active nav item in this style's pill shape. */}
      <Box sx={{ width: 16, display: 'flex', flexDirection: 'column', gap: 0.375, flexShrink: 0 }}>
        <Box sx={{ height: 5, borderRadius: `${Math.min(shape.pillRadius, 4)}px`, bgcolor: 'primary.main' }} />
        <Box sx={{ height: 5, borderRadius: `${Math.min(shape.pillRadius, 4)}px`, bgcolor: 'var(--border)' }} />
        <Box sx={{ height: 5, borderRadius: `${Math.min(shape.pillRadius, 4)}px`, bgcolor: 'var(--border)' }} />
      </Box>
      {/* The card, carrying this style's radius, shadow, and accent wash. */}
      <Box
        sx={{
          flex: 1,
          bgcolor: 'background.paper',
          backgroundImage: shape.cardWash,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: `${Math.min(shape.cardRadius, 10)}px`,
          boxShadow: shape.cardShadow,
        }}
      />
    </Box>
  );
}

/** One labelled block of controls, so the page reads as a list of decisions. */
/**
 * The Tinted tone's swatch, mixed from the live accent (FR-40.3) — a fixed
 * blue stand-in would show every purple- or green-accented user the wrong
 * preview. Returns [canvas, card, sidebar]; the mix ratios mirror the
 * `[data-tone="tinted"]` blocks in global.css and must change with them.
 */
function tintedSwatch(accentMain: string, mode: 'light' | 'dark'): [string, string, string] {
  if (mode === 'dark') {
    return [
      `color-mix(in srgb, ${accentMain} 6%, #1b1a19)`,
      `color-mix(in srgb, ${accentMain} 8%, #252423)`,
      `color-mix(in srgb, ${accentMain} 8%, #252423)`,
    ];
  }
  return [
    `color-mix(in srgb, ${accentMain} 4%, #F9FAFB)`,
    '#ffffff',
    `color-mix(in srgb, ${accentMain} 8%, #f5f5f5)`,
  ];
}

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
  const { settings, resolvedMode, preset, update, applyPreset, reset, saveError, backgroundImage, backgroundWash, backgroundContrast, setBackgroundImage, setBackgroundWash, setBackgroundContrast } = useThemeSettings();
  const [imageError, setImageError] = useState('');

  async function onPickBackgroundImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Cleared so picking the same file again still fires a change event.
    event.target.value = '';
    if (!file) {
      return;
    }
    setImageError('');
    try {
      const dataUrl = await fileToBackgroundDataUrl(file);
      if (dataUrl.length > MAX_BACKGROUND_DATA_URL_LENGTH) {
        setImageError('That image is too large even after compression — try a smaller one.');
        return;
      }
      setBackgroundImage(dataUrl);
    } catch {
      setImageError('Could not read that file as an image.');
    }
  }

  // The font cards render each face in itself, but ThemeModeProvider only
  // fetches the SELECTED font — so on this page (and only here) all of them
  // are loaded, or every unpicked preview silently falls back to the system
  // stack and the cards all look the same. loadFont memoises and swallows
  // failures, so this is one fetch per face, once, best-effort.
  useEffect(() => {
    for (const font of fontFamilies) {
      void loadFont(font.id);
    }
  }, []);

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

          <SettingGroup
            title="Interface style"
            hint="Changes the shape of the app — corners, shadows, and navigation — not its colours. Works with every theme above."
          >
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1 }}>
              {interfaceStyles.map((style) => {
                const selected = settings.interfaceStyle === style.id;
                return (
                  <Card
                    key={style.id}
                    component="button"
                    type="button"
                    onClick={() => update({ interfaceStyle: style.id })}
                    aria-pressed={selected}
                    aria-label={`${style.label} interface style`}
                    sx={{
                      textAlign: 'left',
                      cursor: 'pointer',
                      p: 1.25,
                      display: 'grid',
                      gap: 0.75,
                      borderColor: selected ? 'primary.main' : 'divider',
                      borderWidth: selected ? 2 : 1,
                    }}
                  >
                    <StylePreview styleId={style.id} mode={resolvedMode} />
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography variant="body2" noWrap>{style.label}</Typography>
                      {selected && <Check size={14} style={{ marginLeft: 'auto', flexShrink: 0 }} />}
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: -0.5 }}>
                      {style.description}
                    </Typography>
                  </Card>
                );
              })}
            </Box>
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
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: accentOptions[accentId][resolvedMode].main,
                        // The check is the primary signal; the ring backs it up
                        // for anyone who reads shape before colour.
                        color: accentOptions[accentId][resolvedMode].contrastText,
                        border: '2px solid',
                        borderColor: selected ? 'text.primary' : 'transparent',
                        outlineOffset: 2,
                      }}
                    >
                      {selected && <Check size={16} aria-hidden />}
                    </Box>
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
                const [canvas, card, sidebar] =
                  tone.id === 'tinted'
                    ? tintedSwatch(accentOptions[settings.accent][resolvedMode].main, resolvedMode)
                    : tone.preview[resolvedMode];
                return (
                  <Box
                    key={tone.id}
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
                      width: 126,
                      p: 0.75,
                      borderRadius: 1,
                      textAlign: 'left',
                      cursor: settings.highContrast ? 'not-allowed' : 'pointer',
                      opacity: settings.highContrast ? 0.5 : 1,
                      bgcolor: 'transparent',
                      border: '2px solid',
                      borderColor: selected ? 'primary.main' : 'divider',
                    }}
                  >
                    {/* A miniature of the real app — sidebar rail, canvas, and a
                        card on it — because the tone paints all three. Flat's
                        missing canvas step is visible up front. */}
                    <Box sx={{ display: 'flex', height: 44, borderRadius: '4px', overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
                      <Box sx={{ width: 18, flexShrink: 0, bgcolor: sidebar, borderRight: '1px solid', borderColor: 'divider' }} />
                      <Box sx={{ flex: 1, bgcolor: canvas, p: 0.5 }}>
                        <Box sx={{ height: '100%', borderRadius: '2px', bgcolor: card, border: '1px solid', borderColor: 'divider' }} />
                      </Box>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 0.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 600 }} noWrap>{tone.label}</Typography>
                      {selected && <Check size={12} aria-hidden />}
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                      {tone.description}
                    </Typography>
                  </Box>
                );
              })}
            </Box>
          </SettingGroup>

          <SettingGroup
            title="Background image"
            hint={
              settings.highContrast
                ? 'Paused while high contrast is on — the image is removed so text stays maximally legible. It comes back when high contrast is switched off.'
                : 'Your own picture behind the app — stored on this device only, never uploaded. A wash of the page colour keeps text readable.'
            }
          >
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, alignItems: 'flex-start' }}>
              {backgroundImage && (
                <Box
                  component="img"
                  src={backgroundImage}
                  alt="Current background image"
                  sx={{
                    width: 240,
                    height: 135,
                    objectFit: 'cover',
                    borderRadius: 1,
                    border: '1px solid',
                    borderColor: 'divider',
                    opacity: settings.highContrast ? 0.5 : 1,
                  }}
                />
              )}
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button variant="outlined" size="small" component="label" disabled={settings.highContrast}>
                  {backgroundImage ? 'Replace image' : 'Browse image'}
                  <input hidden type="file" accept="image/*" onChange={onPickBackgroundImage} />
                </Button>
                {backgroundImage && (
                  <Button size="small" color="error" onClick={() => setBackgroundImage(null)}>
                    Remove
                  </Button>
                )}
              </Box>
              {backgroundImage && !settings.highContrast && (
                <Box sx={{ width: 260, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                  <Typography variant="caption" color="text.secondary" id="bg-image-strength-label">
                    Image visibility — {100 - backgroundWash}%
                  </Typography>
                  {/* The slider shows how visible the image is; the stored value
                      is the scrim's opacity, so the two run in opposite
                      directions (visibility 5–100 ↔ wash 95–0). 100% means no
                      wash at all — the user's explicit call. */}
                  <Slider
                    size="small"
                    min={5}
                    max={100}
                    step={5}
                    value={100 - backgroundWash}
                    onChange={(_, value) => typeof value === 'number' && setBackgroundWash(100 - value)}
                    valueLabelDisplay="auto"
                    valueLabelFormat={(value) => `${value}%`}
                    aria-labelledby="bg-image-strength-label"
                  />
                  {100 - backgroundWash > 60 && (
                    <Typography variant="caption" color="text.secondary">
                      At high visibility, text sitting directly on the page canvas may be harder to
                      read over busy parts of the picture. Cards stay solid either way.
                    </Typography>
                  )}
                  <Typography variant="caption" color="text.secondary" id="bg-image-contrast-label">
                    Image contrast — {backgroundContrast}% {backgroundContrast === 100 ? '(as shot)' : ''}
                  </Typography>
                  {/* CSS contrast() on the image layer only — 100% is the photo
                      as shot, below softens it further, above makes it punchier.
                      The app's own text is never touched by this filter. */}
                  <Slider
                    size="small"
                    min={50}
                    max={150}
                    step={5}
                    value={backgroundContrast}
                    onChange={(_, value) => typeof value === 'number' && setBackgroundContrast(value)}
                    valueLabelDisplay="auto"
                    valueLabelFormat={(value) => `${value}%`}
                    marks={[{ value: 100 }]}
                    aria-labelledby="bg-image-contrast-label"
                  />
                </Box>
              )}
              {imageError && <Typography variant="caption" color="error">{imageError}</Typography>}
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

          <SettingGroup
            title="Font"
            hint="The default is your device's own font and loads instantly. The others download once, the first time you pick them."
          >
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1 }}>
              {fontFamilies.map((font) => {
                const selected = settings.fontFamily === font.id;
                return (
                  <Card
                    key={font.id}
                    component="button"
                    type="button"
                    onClick={() => update({ fontFamily: font.id })}
                    aria-pressed={selected}
                    aria-label={`${font.label} font`}
                    sx={{
                      textAlign: 'left',
                      cursor: 'pointer',
                      p: 1.25,
                      borderColor: selected ? 'primary.main' : 'divider',
                      borderWidth: selected ? 2 : 1,
                    }}
                  >
                    {/* Set in the font itself, so the sample is the decision
                        rather than a description of it. */}
                    <Typography sx={{ fontFamily: fontStack(font.id), fontSize: '1.05rem', fontWeight: 600, lineHeight: 1.3 }}>
                      {font.label}
                    </Typography>
                    <Typography sx={{ fontFamily: fontStack(font.id), fontSize: '0.8rem' }} color="text.secondary">
                      {font.description}
                    </Typography>
                  </Card>
                );
              })}
            </Box>
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

        {/* Sticky (from md, where it sits beside the controls): the whole
            point of a live preview is seeing it while changing the settings
            further down the page. top clears the sticky glass header. */}
        <Box sx={{ position: { md: 'sticky' }, top: { md: 88 }, alignSelf: 'start' }}>
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
