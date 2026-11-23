import { useEffect, useRef, useState } from 'react';

/**
 * BRD 25 §8 — counts a number from its previous value (0 on first show) to the
 * new one over `durationMs`, easing out. With `reduced`, it returns the value
 * at once. Screen readers should be given the real value separately; this is
 * only for the visible digits.
 */
export function useCountUp(value: number, reduced: boolean, durationMs = 600): number {
  const [shown, setShown] = useState(reduced ? value : 0);
  const from = useRef(reduced ? value : 0);

  useEffect(() => {
    if (reduced || typeof requestAnimationFrame !== 'function') {
      from.current = value;
      setShown(value);
      return undefined;
    }
    const start = performance.now();
    const origin = from.current;
    let frame = 0;
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - progress, 3);
      setShown(Math.round(origin + (value - origin) * eased));
      if (progress < 1) {
        frame = requestAnimationFrame(step);
      } else {
        from.current = value;
      }
    };
    frame = requestAnimationFrame(step);
    return () => {
      cancelAnimationFrame(frame);
      from.current = value;
    };
  }, [value, reduced, durationMs]);

  return shown;
}
