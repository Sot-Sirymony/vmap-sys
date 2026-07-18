import {
  CheckSquare,
  ClipboardList,
  Compass,
  FileSpreadsheet,
  Flag,
  LayoutDashboard,
  ListChecks,
  MessageSquare,
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
      { to: '/import-export', label: 'Import / Export', icon: FileSpreadsheet },
    ],
  },
];

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
