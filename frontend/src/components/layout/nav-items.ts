import {
  BookOpen,
  Bot,
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
  Scale,
  Send,
  ShieldAlert,
  Sparkles,
  Target,
  TrafficCone,
  UserCog,
  Users,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';
import { tr } from '../../i18n/translate';

export type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  /** V9: shown only to these roles; every role sees an item without it. */
  roles?: string[];
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
      // V8.1 FR-79 to FR-84: what I bring, what I lack, and how each gap is covered.
      { to: '/strengths', label: tr('Strengths and Gaps'), icon: Scale },
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
      // V9: the AI Dream Coach.
      { to: '/dream-coach', label: tr('Dream Coach'), icon: Bot },
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
      // V9: the coach workspace and cohort management.
      { to: '/coach', label: tr('Coaching'), icon: Users, roles: ['COACH', 'ADMIN'] },
      { to: '/settings/cohorts', label: tr('Cohorts'), icon: UsersRound, roles: ['ADMIN'] },
      { to: '/settings/ai', label: tr('AI settings'), icon: Sparkles, roles: ['ADMIN'] },
      // V10 (SEC-09-05): the read-only security log.
      { to: '/settings/security-log', label: tr('Security log'), icon: ShieldAlert, roles: ['ADMIN'] },
    ],
  },
];

/** The nav groups this role may see. */
export function navGroupsFor(role: string | undefined): NavGroup[] {
  return navGroups.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.roles || (role !== undefined && item.roles.includes(role))),
  }));
}

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
