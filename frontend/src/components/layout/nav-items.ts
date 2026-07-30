import {
  CheckSquare,
  Network,
  ClipboardList,
  Compass,
  FileSpreadsheet,
  Flag,
  LayoutDashboard,
  Lightbulb,
  ListChecks,
  MessageSquare,
  MessageSquareWarning,
  Palette,
  Sparkles,
  TriangleAlert,
  Users,
  type LucideIcon,
} from 'lucide-react';

export type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
};

export type NavGroup = {
  label: string;
  items: NavItem[];
};

// FR-23.2: three sections mirroring the method's mental model — set the
// direction (Plan), do the work (Execute), get help and review (Support).
export const navGroups: NavGroup[] = [
  {
    label: 'Plan',
    items: [
      { to: '/', label: 'Dashboard', icon: LayoutDashboard },
      { to: '/vision-areas', label: 'Vision Areas', icon: Compass },
      { to: '/dreams', label: 'Dreams', icon: Sparkles },
      { to: '/vision-map', label: 'Vision Map', icon: Network },
    ],
  },
  {
    label: 'Execute',
    items: [
      { to: '/goals', label: 'Goals', icon: Flag },
      { to: '/steps', label: 'Steps', icon: ListChecks },
      { to: '/tasks', label: 'Tasks', icon: CheckSquare },
      { to: '/obstacles', label: 'Obstacles', icon: TriangleAlert },
    ],
  },
  {
    label: 'Support',
    items: [
      { to: '/partners', label: 'Partners', icon: Users },
      { to: '/communication', label: 'Communication', icon: MessageSquare },
      { to: '/reviews', label: 'Reviews', icon: ClipboardList },
      { to: '/insights', label: 'Insights', icon: Lightbulb },
      { to: '/issue-reports', label: 'Issue Reports', icon: MessageSquareWarning },
      { to: '/import-export', label: 'Import / Export', icon: FileSpreadsheet },
      // FR-39.5: the full Appearance surface. Reachable from the sidebar as well
      // as the header menu, so the header icon isn't the only way to find it.
      { to: '/settings/appearance', label: 'Appearance', icon: Palette },
    ],
  },
];

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
