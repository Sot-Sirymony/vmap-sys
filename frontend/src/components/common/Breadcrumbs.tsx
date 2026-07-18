import { Fragment } from 'react';
import { Link } from 'react-router-dom';

export type Crumb = {
  label: string;
  /** Omit for the current/terminal segment, which renders as plain text. */
  to?: string;
};

/**
 * FR-23.1 ancestry breadcrumb: `Career › Become a researcher › Learn AI
 * tools`, each segment navigating to that entity's context. Rendered as a
 * caption-size line under titles or in table cells.
 */
export function Breadcrumbs({ crumbs }: { crumbs: Crumb[] }) {
  const shown = crumbs.filter((crumb) => crumb.label);
  if (shown.length === 0) {
    return null;
  }
  return (
    <nav className="crumbs" aria-label="Hierarchy">
      {shown.map((crumb, index) => (
        <Fragment key={`${crumb.label}-${index}`}>
          {index > 0 && <span aria-hidden="true" className="crumbs-sep">›</span>}
          {crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span>{crumb.label}</span>}
        </Fragment>
      ))}
    </nav>
  );
}
