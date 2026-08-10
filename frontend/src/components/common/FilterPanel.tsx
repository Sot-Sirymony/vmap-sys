import { useId, useState, type ReactNode } from 'react';
import Card from '@mui/material/Card';
import ButtonBase from '@mui/material/ButtonBase';
import { ChevronDown, ListFilter } from 'lucide-react';

type FilterPanelProps = {
  children: ReactNode;
  /**
   * How many filters are currently narrowing the list. Shown on the collapsed
   * mobile toggle, so a filtered list never looks mysteriously short.
   */
  activeCount?: number;
};

/**
 * The list-page filter card. On desktop it renders as the always-open card it
 * has always been; below the md breakpoint the controls collapse behind a
 * "Filters" toggle, so the content itself — not a wall of empty dropdowns —
 * is what a phone shows above the fold. The toggle is display:none on
 * desktop, which is also what keeps the open/closed state harmless there.
 */
export function FilterPanel({ children, activeCount = 0 }: FilterPanelProps) {
  const [open, setOpen] = useState(false);
  const contentId = useId();

  return (
    <Card className="filter-panel">
      <ButtonBase
        className="filter-panel-toggle"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-controls={contentId}
      >
        <ListFilter size={16} aria-hidden />
        <span>Filters</span>
        {activeCount > 0 && <span className="filter-panel-count">{activeCount}</span>}
        <ChevronDown
          size={16}
          aria-hidden
          className={`filter-panel-chevron${open ? ' filter-panel-chevron--open' : ''}`}
        />
      </ButtonBase>
      <div id={contentId} className={`filter-bar flex-row${open ? '' : ' filter-bar--collapsed'}`}>
        {children}
      </div>
    </Card>
  );
}
