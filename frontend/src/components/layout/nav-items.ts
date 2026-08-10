import {
  Bug,
  CheckSquare,
  ClipboardList,
  Compass,
  FileSpreadsheet,
  Footprints,
  Handshake,
  KeyRound,
  LayoutDashboard,
  Lightbulb,
  MoonStar,
  Network,
  Palette,
  Send,
  Target,
  TrafficCone,
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
      { to: '/dreams', label: 'Dreams', icon: MoonStar },
      { to: '/vision-map', label: 'Vision Map', icon: Network },
    ],
  },
  {
    label: 'Execute',
    items: [
      { to: '/goals', label: 'Goals', icon: Target },
      { to: '/steps', label: 'Steps', icon: Footprints },
      { to: '/tasks', label: 'Tasks', icon: CheckSquare },
      { to: '/obstacles', label: 'Obstacles', icon: TrafficCone },
    ],
  },
  {
    label: 'Support',
    items: [
      { to: '/partners', label: 'Partners', icon: Handshake },
      { to: '/communication', label: 'Communication', icon: Send },
      { to: '/reviews', label: 'Reviews', icon: ClipboardList },
      { to: '/insights', label: 'Insights', icon: Lightbulb },
      { to: '/issue-reports', label: 'Issue Reports', icon: Bug },
      { to: '/import-export', label: 'Import / Export', icon: FileSpreadsheet },
      // FR-39.5: the full Appearance surface. Reachable from the sidebar as well
      // as the header menu, so the header icon isn't the only way to find it.
      { to: '/settings/appearance', label: 'Appearance', icon: Palette },
      { to: '/settings/security', label: 'Security', icon: KeyRound },
    ],
  },
];

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
