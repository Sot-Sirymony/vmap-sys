import type { ReactNode } from 'react';

const FUNNEL_LEVELS = ['Vision Area', 'Dream', 'Goal', 'Step', 'Task'];

/**
 * FR-27.3: the first screen says what the product is. Brand + one-line value
 * statement + the method's five-level funnel as a small illustration. The
 * navy hero keeps its own fixed palette (excluded from theming by FR-18's
 * scope note), so the illustration uses light fixed tones, not theme vars.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth-shell">
      <div className="auth-hero" aria-hidden="true">
        <p className="auth-brand"><span className="auth-brand-mark">VM</span> Vision Map</p>
        <p className="auth-tagline">Turn dreams into scheduled work.</p>
        <div className="auth-funnel">
          {FUNNEL_LEVELS.map((level, index) => (
            <div className="auth-funnel-level" style={{ width: `${100 - index * 14}%` }} key={level}>
              {level}
            </div>
          ))}
        </div>
      </div>
      <section className="auth-panel">{children}</section>
    </main>
  );
}
