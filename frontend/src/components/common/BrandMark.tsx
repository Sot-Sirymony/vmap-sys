import { useId } from 'react';

/**
 * The product mark: a V sweeping up into an arrow, a chart line with two
 * nodes, a rising bar, and a star — the brand set in frontend/public/brand/
 * (vm-mark.svg and friends), drawn inline so no asset request is needed.
 *
 * `variant="color"` renders the brand palette (Teal #006D77 → Aqua #14B8A6 →
 * Green #84CC16); `variant="mono"` draws everything in currentColor, for
 * sitting on accent tiles or in monochrome contexts — the same roles the
 * black/white SVG files serve outside the app.
 *
 * `size` is the rendered height; the mark is wider than tall (620:480).
 */
export function BrandMark({ size = 18, variant = 'mono' }: { size?: number; variant?: 'color' | 'mono' }) {
  // Gradient ids must be unique per mounted instance — two color marks on one
  // page would otherwise silently share (or fight over) defs. useId's colons
  // are stripped: they are legal in an id attribute but not in url(#…) refs
  // in every SVG renderer.
  const uid = useId().replaceAll(':', '');
  const gv = `vm-gv-${uid}`;
  const ga = `vm-ga-${uid}`;
  const color = variant === 'color';
  const stroke = color ? `url(#${gv})` : 'currentColor';
  const accent = color ? `url(#${ga})` : 'currentColor';
  const aqua = color ? '#14B8A6' : 'currentColor';
  const green = color ? '#84CC16' : 'currentColor';

  return (
    <svg
      width={Math.round((size * 620) / 480)}
      height={size}
      viewBox="0 0 620 480"
      fill="none"
      aria-hidden
      focusable="false"
    >
      {color && (
        <defs>
          <linearGradient id={gv} x1="0" y1="1" x2="0.7" y2="0">
            <stop offset="0" stopColor="#006D77" />
            <stop offset="1" stopColor="#14B8A6" />
          </linearGradient>
          <linearGradient id={ga} x1="0" y1="1" x2="0" y2="0">
            <stop offset="0" stopColor="#14B8A6" />
            <stop offset="1" stopColor="#84CC16" />
          </linearGradient>
        </defs>
      )}
      <path d="M58,150 L166,436 L320,162" stroke={stroke} strokeWidth="56" strokeLinejoin="miter" />
      <polygon points="369,75 276,138 364,187" fill={accent} />
      <path d="M300,326 L344,252 L386,306 L430,216" stroke={accent} strokeWidth="18" strokeLinecap="round" />
      <circle cx="344" cy="252" r="25" fill={aqua} />
      <circle cx="430" cy="216" r="25" fill={green} />
      <rect x="466" y="252" width="54" height="188" rx="10" fill={green} />
      <polygon
        points="530,64 543,103 584,103 551,127 563,166 530,142 497,166 509,127 476,103 517,103"
        fill={green}
      />
    </svg>
  );
}
