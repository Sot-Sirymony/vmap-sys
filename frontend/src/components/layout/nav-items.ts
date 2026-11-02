import {
  BookOpen,
  Bug,
  CalendarCheck,
  CheckSquare,
  ClipboardList,
  Compass,
  FileSpreadsheet,
  Fingerprint,
  Footprints,
  Gauge,
  GraduationCap,
  Handshake,
  KeyRound,
  LayoutDashboard,
  Lightbulb,
  ListChecks,
  MoonStar,
  Network,
  Palette,
  Send,
  Target,
  TrafficCone,
  UserCog,
  type LucideIcon,
} from 'lucide-react';
import { tr } from '../../i18n/translate';

export type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
};

export type NavGroup = {
  label: string;
  items: NavItem[];
};

/**
 * Which nav destination a path belongs to. A dream tree at /dreams/:id is
 * reached from the Vision Map overview and renders the map, so the map stays
 * the highlighted destination — /dreams itself is still the Dreams list.
 * Shared by the sidebar and the mobile bottom nav so they can never disagree.
 */
export function navPathname(pathname: string): string {
  if (/^\/modules\/\d+/.test(pathname)) {
    return '/modules';
  }
  return /^\/dreams\/\d+/.test(pathname) ? '/vision-map' : pathname;
}

// FR-23.2: three sections mirroring the method's mental model — set the
// direction (Plan), do the work (Execute), get help and review (Support).
export const navGroups: NavGroup[] = [
  {
    label: tr('Plan'),
    items: [
      { to: '/', label: tr('Dashboard'), icon: LayoutDashboard },
      { to: '/vision-areas', label: tr('Vision Areas'), icon: Compass },
      { to: '/dreams', label: tr('Dreams'), icon: MoonStar },
      { to: '/vision-map', label: tr('Vision Map'), icon: Network },
      { to: '/plan', label: tr('Plan'), icon: ListChecks },
      { to: '/modules', label: tr('Modules'), icon: GraduationCap },
      { to: '/assessment', label: tr('Assessment'), icon: Gauge },
    ],
  },
  {
    label: tr('Execute'),
    items: [
      { to: '/today', label: tr('Today'), icon: CalendarCheck },
      { to: '/goals', label: tr('Goals'), icon: Target },
      { to: '/steps', label: tr('Steps'), icon: Footprints },
      { to: '/tasks', label: tr('Tasks'), icon: CheckSquare },
      { to: '/obstacles', label: tr('Obstacles'), icon: TrafficCone },
      { to: '/journal', label: tr('Journal'), icon: BookOpen },
    ],
  },
  {
    label: tr('Support'),
    items: [
      { to: '/partners', label: tr('Partners'), icon: Handshake },
      { to: '/work-style', label: tr('Work Style'), icon: Fingerprint },
      { to: '/communication', label: tr('Communication'), icon: Send },
      { to: '/reviews', label: tr('Reviews'), icon: ClipboardList },
      { to: '/insights', label: tr('Insights'), icon: Lightbulb },
      { to: '/issue-reports', label: tr('Issue Reports'), icon: Bug },
      { to: '/import-export', label: tr('Import / Export'), icon: FileSpreadsheet },
      // FR-39.5: the full Appearance surface. Reachable from the sidebar as well
      // as the header menu, so the header icon isn't the only way to find it.
      { to: '/settings/appearance', label: tr('Appearance'), icon: Palette },
      { to: '/settings/security', label: tr('Security'), icon: KeyRound },
      { to: '/settings/account', label: tr('Account'), icon: UserCog },
    ],
  },
];

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
