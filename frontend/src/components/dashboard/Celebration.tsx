import { useEffect, useRef } from 'react';
import Box from '@mui/material/Box';
import { chartCategorical } from '../../design/tokens';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { cssVar } from './charts/chartTheme';

const STORAGE_PREFIX = 'vms.v25.completedGoals.';

/**
 * DA-FR-14 — whether to celebrate: true only when the count of completed goals
 * has risen since the last visit we remember. The first visit only remembers
 * the count, so nobody is congratulated for goals finished long ago.
 */
export function shouldCelebrate(previous: number | null, current: number): boolean {
  return previous !== null && current > previous;
}

/** Reads and updates the remembered count. Storage may be unavailable; then nothing celebrates. */
export function rememberCompletedGoals(userKey: string, current: number): number | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + userKey);
    window.localStorage.setItem(STORAGE_PREFIX + userKey, String(current));
    if (raw === null) {
      return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

type ConfettiBurstProps = {
  /** Increment to play the burst again. 0 never plays. */
  play: number;
};

const PARTICLES = 24;
const DURATION_MS = 1200;

/**
 * BRD 25 §8 — a quiet confetti burst: 24 pieces in the five chart colours,
 * 1.2 s, confined to its parent card (which must be `position: relative`).
 * Never plays under reduced motion; the caller's toast still says what happened.
 */
export function ConfettiBurst({ play }: ConfettiBurstProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const reduced = useReducedMotion();
  const { resolvedMode } = useThemeSettings();

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!play || reduced || !canvas || typeof requestAnimationFrame !== 'function') {
      return undefined;
    }
    const context = canvas.getContext?.('2d');
    if (!context) {
      return undefined;
    }
    const { width, height } = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    context.scale(ratio, ratio);
    const colors = chartCategorical[resolvedMode].map((fallback, index) => cssVar(`--chart-${index + 1}`, fallback));
    const pieces = Array.from({ length: PARTICLES }, (_, index) => ({
      x: width / 2,
      y: height * 0.4,
      vx: (Math.random() - 0.5) * 7,
      vy: -Math.random() * 6 - 2,
      size: 4 + Math.random() * 4,
      rotation: Math.random() * 6,
      spin: (Math.random() - 0.5) * 0.3,
      color: colors[index % colors.length],
    }));
    const start = performance.now();
    let frame = 0;
    const draw = (now: number) => {
      const progress = (now - start) / DURATION_MS;
      context.clearRect(0, 0, width, height);
      if (progress >= 1) {
        return;
      }
      context.globalAlpha = 1 - progress;
      for (const piece of pieces) {
        piece.vy += 0.22;
        piece.x += piece.vx;
        piece.y += piece.vy;
        piece.rotation += piece.spin;
        context.save();
        context.translate(piece.x, piece.y);
        context.rotate(piece.rotation);
        context.fillStyle = piece.color;
        context.fillRect(-piece.size / 2, -piece.size / 4, piece.size, piece.size / 2);
        context.restore();
      }
      frame = requestAnimationFrame(draw);
    };
    frame = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(frame);
  }, [play, reduced, resolvedMode]);

  return (
    <Box
      component="canvas"
      ref={canvasRef}
      aria-hidden="true"
      data-testid="confetti"
      sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none', borderRadius: 'inherit' }}
    />
  );
}
