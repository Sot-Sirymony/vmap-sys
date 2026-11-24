import type { AuthUser } from '../../types/auth';
import { can, type Permission } from '../../utils/permissions';
import { BookOpen, Bot, Bug, CalendarCheck, ChartGantt, FileBarChart, CheckSquare, ClipboardList, Compass, FileSpreadsheet, Fingerprint, Footprints, Gauge, GraduationCap, Handshake, KeyRound, LayoutDashboard, Lightbulb, ListChecks, LockKeyhole, MoonStar, Network, Palette, Scale, Send, ShieldAlert, Sparkles, Target, TrafficCone, UserCog, UserRoundCog, ShieldUser, Users, UsersRound, type LucideIcon, FolderClock, ShieldCheck, Scissors } from 'lucide-react';
import { tr } from '../../i18n/translate';

export type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  /** V23 (UM-FR-05): shown only with this permission (V24: or any one of these); everyone sees an item without it. */
  permission?: Permission | Permission[];
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
/**
 * The one destination to highlight: the longest item path the current path
 * starts with, so /vision-map/timeline lights Timeline, not Vision Map too.
 */
export function activeNavTo(pathname: string, items: NavItem[]): string | null {
  let best: string | null = null;
  for (const item of items) {
    const matches = item.to === '/' ? pathname === '/' : pathname.startsWith(item.to);
    if (matches && (best === null || item.to.length > best.length)) {
      best = item.to;
    }
  }
  return best;
}

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
      // V16 (TL-FR-05): every dream on one calendar.
      { to: '/vision-map/timeline', label: tr('Timeline'), icon: ChartGantt },
      // V18 (MR-FR-01): plan health and documents to share.
      { to: '/vision-map/report', label: tr('Management Report'), icon: FileBarChart },
      // V19 (RM-FR-17): reports saved from it, draft and final.
      { to: '/vision-map/reports', label: tr('Saved reports'), icon: FolderClock },
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
      // V27 (BRD 27): trim a goal or step before speeding it up or automating it.
      { to: '/streamline', label: tr('Streamline'), icon: Scissors },
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
      { to: '/coach', label: tr('Coaching'), icon: Users, permission: 'COACH_COHORTS' },
      // V23 (UM-FR-17, UM-FR-08): accounts and roles.
      { to: '/settings/users', label: tr('Users'), icon: UserRoundCog, permission: 'VIEW_USERS' },
      { to: '/settings/roles', label: tr('Roles'), icon: ShieldUser, permission: 'MANAGE_ROLES' },
      // V24 (D-24-2): Admins change sign-in methods; roles with "View security log" read them.
      { to: '/settings/security-access', label: tr('Security access'), icon: LockKeyhole, permission: ['MANAGE_ROLES', 'VIEW_SECURITY_LOG'] },
      { to: '/settings/cohorts', label: tr('Cohorts'), icon: UsersRound, permission: 'MANAGE_COHORTS' },
      { to: '/settings/ai', label: tr('AI settings'), icon: Sparkles, permission: 'MANAGE_AI_SETTINGS' },
      // V10 (SEC-09-05): the read-only security log.
      { to: '/settings/security-log', label: tr('Security log'), icon: ShieldAlert, permission: 'VIEW_SECURITY_LOG' },
      // V21 (TF-FR-36): who uses two-step sign-in, and resets.
      { to: '/settings/two-factor', label: tr('Two-step sign-in'), icon: ShieldCheck, permission: 'RESET_TWO_FACTOR' },
    ],
  },
];

/** Whether this person may see the item: no permission named, or the one (V24: any one of those) named. */
export function canSeeNavItem(user: AuthUser | null | undefined, item: NavItem): boolean {
  if (!item.permission) return true;
  return Array.isArray(item.permission) ? item.permission.some((one) => can(user, one)) : can(user, item.permission);
}

/** The nav groups this person may see (V23: by permission, not role name). */
export function navGroupsFor(user: AuthUser | null | undefined): NavGroup[] {
  return navGroups.map((group) => ({
    ...group,
    items: group.items.filter((item) => canSeeNavItem(user, item)),
  }));
}

// Flat view of the same items — the Header's page-label lookup keys off this.
export const navItems: NavItem[] = navGroups.flatMap((group) => group.items);
