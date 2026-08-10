/**
 * The product mark: one root branching into two nodes — the vision-map
 * hierarchy reduced to its smallest legible drawing. Same visual family as
 * the Vision Map nav icon (lucide Network), so the brand and the feature
 * that defines the product read as one idea. Geometry only, no text: a
 * lettermark depends on platform fonts and turns to mush at favicon sizes.
 *
 * Drawn in currentColor so the same mark sits on the accent tile in the
 * sidebar, the fixed navy tile on the auth hero, and anything added later.
 */
export function BrandMark({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden focusable="false">
      <path d="M12 5.5 5.5 18.5M12 5.5l6.5 13" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
      <circle cx="12" cy="5.5" r="3" fill="currentColor" />
      <circle cx="5.5" cy="18.5" r="2.7" fill="currentColor" />
      <circle cx="18.5" cy="18.5" r="2.7" fill="currentColor" />
    </svg>
  );
}
