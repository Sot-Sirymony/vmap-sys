import { tr } from '../i18n/translate';

/** How far along something is, as one of four colour levels. */
export type ProgressTone = 'low' | 'mid' | 'high' | 'done';

/**
 * The colour level for a progress percentage: red when just started, orange
 * while in progress, blue when nearly done, green when complete. The number
 * is always shown next to the colour, so colour is never the only signal.
 */
export function progressTone(value: number): ProgressTone {
  const percent = Math.round(Math.min(100, Math.max(0, Number(value) || 0)));
  if (percent >= 100) {
    return 'done';
  }
  if (percent >= 75) {
    return 'high';
  }
  if (percent >= 25) {
    return 'mid';
  }
  return 'low';
}

/** Legend rows, in order from just started to done. */
export function progressToneLegend(): { tone: ProgressTone; range: string; label: string }[] {
  return [
    { tone: 'low', range: '0–24%', label: tr('Just started') },
    { tone: 'mid', range: '25–74%', label: tr('In progress') },
    { tone: 'high', range: '75–99%', label: tr('Nearly done') },
    { tone: 'done', range: '100%', label: tr('Done') },
  ];
}
