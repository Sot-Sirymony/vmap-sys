import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Check } from 'lucide-react';
import { useRef, type KeyboardEvent } from 'react';
import { glassFallback, styleCssVariables, type InterfaceStyle, type Mode } from '../../design/tokens';
import { surface } from '../../design/surface';
import { useInterfaceStyle, type GlassFallbackReason } from '../../hooks/useInterfaceStyle';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { interfaceStyles } from '../../theme';
import { tr } from '../../i18n/translate';

/** The note shown under Liquid Glass when it is showing solid panels (§7, ISC-03). */
export function glassFallbackMessage(reason: GlassFallbackReason): string {
  switch (reason) {
    case 'highContrast':
      return tr('High contrast is on, so panels stay solid.');
    case 'unsupported':
      return tr('This browser cannot blur the background, so panels stay solid.');
    default:
      return tr('Your device asks for less transparency, so panels stay solid.');
  }
}

/**
 * The style's tokens as inline custom properties. A thumbnail cannot use
 * `data-style` (the tokens are declared on `:root`), so it carries the values
 * itself — the same values global.css uses, from the same function.
 */
function tokenScope(style: InterfaceStyle, mode: Mode, fallback: boolean): Record<string, string> {
  return { ...styleCssVariables(style, mode), ...(style === 'glass' && fallback ? glassFallback : {}) };
}

/**
 * A miniature of the app drawn with one style's real tokens (§7): a header
 * strip, a nav item in the style's shape, one card, one chip and a two-line
 * table, in the current mode and accent.
 */
function StyleThumbnail({ style, mode, fallback }: { style: InterfaceStyle; mode: Mode; fallback: boolean }) {
  const pill = style !== 'classic';
  return (
    <Box
      aria-hidden
      sx={{
        ...tokenScope(style, mode, fallback),
        position: 'relative',
        height: 112,
        borderRadius: 'var(--style-radius-control)',
        overflow: 'hidden',
        border: '1px solid var(--border)',
        // A soft backdrop so the glass layers have something to frost.
        background: 'linear-gradient(135deg, color-mix(in srgb, var(--primary) 22%, var(--canvas)) 0%, var(--canvas) 55%, color-mix(in srgb, var(--primary) 10%, var(--canvas)) 100%)',
        display: 'grid',
        gridTemplateColumns: '34px 1fr',
        gridTemplateRows: '18px 1fr',
      }}
    >
      {/* Sidebar rail: a floating layer. */}
      <Box
        sx={{
          gridRow: '1 / 3',
          bgcolor: 'var(--style-floating-bg)',
          backdropFilter: 'var(--style-blur)',
          borderRight: 'var(--style-floating-border)',
          p: '6px 4px',
          display: 'grid',
          alignContent: 'start',
          gap: '4px',
        }}
      >
        <Box
          sx={{
            height: 7,
            borderRadius: 'var(--style-radius-nav)',
            bgcolor: pill ? 'var(--accent)' : 'transparent',
            borderLeft: 'var(--style-nav-bar) solid var(--primary)',
          }}
        />
        <Box sx={{ height: 7, borderRadius: 'var(--style-radius-nav)', bgcolor: 'var(--border)' }} />
        <Box sx={{ height: 7, borderRadius: 'var(--style-radius-nav)', bgcolor: 'var(--border)' }} />
      </Box>
      {/* Header strip: greeting line, or breadcrumbs in Classic. */}
      <Box
        sx={{
          bgcolor: 'var(--style-floating-bg)',
          backdropFilter: 'var(--style-blur)',
          borderBottom: 'var(--style-floating-border)',
          display: 'flex',
          alignItems: 'center',
          gap: '3px',
          px: '6px',
        }}
      >
        {pill ? (
          <Box sx={{ width: 38, height: 5, borderRadius: 'var(--style-radius-chip)', bgcolor: 'var(--text-secondary)', opacity: 0.6 }} />
        ) : (
          [16, 16, 22].map((width, index) => (
            <Box key={index} sx={{ width, height: 4, borderRadius: '1px', bgcolor: 'var(--text-secondary)', opacity: index === 2 ? 0.8 : 0.45 }} />
          ))
        )}
      </Box>
      {/* Content: one card with a chip, and a two-line table. Always solid. */}
      <Box sx={{ p: '6px', display: 'grid', gap: '5px', alignContent: 'start' }}>
        <Box
          sx={{
            bgcolor: 'var(--style-card-bg)',
            border: 'var(--style-card-border)',
            borderRadius: 'var(--style-radius-card)',
            boxShadow: 'var(--style-shadow-rest)',
            p: '5px 6px',
            display: 'flex',
            alignItems: 'center',
            gap: '5px',
          }}
        >
          <Box sx={{ width: 28, height: 5, borderRadius: 'var(--style-radius-chip)', bgcolor: 'var(--text-primary)', opacity: 0.7 }} />
          <Box sx={{ ml: 'auto', width: 22, height: 9, borderRadius: 'var(--style-radius-chip)', bgcolor: 'var(--st-moving-bg)', border: '1px solid var(--st-moving)' }} />
        </Box>
        <Box sx={{ bgcolor: 'var(--style-card-bg)', border: 'var(--style-card-border)', borderRadius: 'var(--style-radius-card)', overflow: 'hidden' }}>
          {[0, 1].map((row) => (
            <Box key={row} sx={{ display: 'flex', gap: '4px', alignItems: 'center', px: '6px', height: pill ? 13 : 10, borderTop: row ? '1px solid var(--divider)' : 'none' }}>
              <Box sx={{ width: 30, height: 4, borderRadius: 'var(--style-radius-chip)', bgcolor: 'var(--text-secondary)', opacity: 0.55 }} />
              <Box sx={{ width: 16, height: 4, borderRadius: 'var(--style-radius-chip)', bgcolor: 'var(--text-secondary)', opacity: 0.35 }} />
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

/**
 * IS-FR-10 / BRD 26 §7 — the interface style picker. Three cards (stacked on
 * phones) with live thumbnails, radio semantics and arrow-key movement.
 */
export function InterfaceStylePicker() {
  const { resolvedMode } = useThemeSettings();
  const { style: current, glassFallback: fallbackReason, setStyle } = useInterfaceStyle();
  const refs = useRef<(HTMLDivElement | null)[]>([]);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>, index: number) {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (step === undefined) {
      if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault();
        setStyle(interfaceStyles[index].id);
      }
      return;
    }
    event.preventDefault();
    const next = (index + step + interfaceStyles.length) % interfaceStyles.length;
    setStyle(interfaceStyles[next].id);
    refs.current[next]?.focus();
  }

  return (
    <Box role="radiogroup" aria-label={tr('Interface style')} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 1.5 }}>
      {interfaceStyles.map((option, index) => {
        const selected = current === option.id;
        return (
          <Box
            key={option.id}
            ref={(node: HTMLDivElement | null) => {
              refs.current[index] = node;
            }}
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => setStyle(option.id)}
            onKeyDown={(event) => onKeyDown(event, index)}
            sx={{
              ...surface('card', { flush: true }),
              p: 1.25,
              display: 'grid',
              gap: 0.75,
              alignContent: 'start',
              cursor: 'pointer',
              outlineOffset: 2,
              ...(selected ? { borderColor: 'primary.main', boxShadow: '0 0 0 1px var(--primary)' } : {}),
            }}
          >
            <StyleThumbnail style={option.id} mode={resolvedMode} fallback={option.id === 'glass' && fallbackReason !== null} />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{option.label}</Typography>
              {selected && <Check size={14} style={{ marginLeft: 'auto', flexShrink: 0 }} />}
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: -0.5 }}>
              {option.description}
            </Typography>
            {option.id === 'classic' && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {tr('Pair with Compact density for the densest view.')}
              </Typography>
            )}
            {option.id === 'glass' && fallbackReason !== null && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }} data-testid="glass-fallback-note">
                {glassFallbackMessage(fallbackReason)}
              </Typography>
            )}
          </Box>
        );
      })}
    </Box>
  );
}
