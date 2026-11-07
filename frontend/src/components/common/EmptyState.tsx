import { Inbox, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import Stack from '@mui/material/Stack';

type EmptyStateProps = {
  /** Description text (the only content in the compact in-panel form). */
  children: ReactNode;
  /**
   * FR-20.3 full-size form: icon + headline + description + one primary
   * action ("No goals yet — Add a goal to this dream"). Passing `headline`
   * switches from the compact row to the centered page/panel block.
   */
  headline?: string;
  /** Concept icon from the nav vocabulary (defaults to Inbox). */
  icon?: LucideIcon;
  /** Primary action button; renders under the description. */
  action?: ReactNode;
};

export function EmptyState({ children, headline, icon: Icon = Inbox, action }: EmptyStateProps) {
  if (!headline) {
    return (
      <Stack className="empty-state" direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
        <Icon size={16} />
        <span>{children}</span>
        {/* An action passed to the compact form is shown too, never dropped. */}
        {action}
      </Stack>
    );
  }

  return (
    <Stack className="empty-state empty-state--page" spacing={1.5} sx={{ alignItems: 'center', justifyContent: 'center', textAlign: 'center', py: 6, px: 3 }}>
      <span className="empty-state-icon"><Icon size={24} /></span>
      <strong className="empty-state-headline">{headline}</strong>
      <span className="empty-state-body">{children}</span>
      {action}
    </Stack>
  );
}
