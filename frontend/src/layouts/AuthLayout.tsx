import type { ReactNode } from 'react';
import { BrandMark } from '../components/common/BrandMark';

/**
 * The Stitch auth shell: one centred card on the page canvas, an accent-tinted
 * gradient mesh in its corners, and the brand block (logo tile + wordmark) at
 * the top. Screens that open with their own status icon instead of the brand —
 * the OAuth error and account-linking screens — pass `brand={false}`.
 *
 * `subtitle` renders as the page's h2 directly under the wordmark, so each
 * screen states what it is ("Sign in to continue…", "Create your account")
 * inside the one shared header.
 */
export function AuthLayout({
  brand = true,
  subtitle,
  children,
}: {
  brand?: boolean;
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="auth-shell">
      <section className="auth-card">
        <div className="auth-card-glow" aria-hidden="true" />
        {brand && (
          <header className="auth-head">
            <span className="auth-logo">
              <BrandMark size={24} />
            </span>
            <h1 className="auth-title">Vision Map</h1>
            {subtitle && <h2 className="auth-subtitle">{subtitle}</h2>}
          </header>
        )}
        {children}
      </section>
    </main>
  );
}
