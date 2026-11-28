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
  /** V37: a stable key, used to remember which groups are open. */
  id: string;
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

// V37 (D-37-1): five groups by what the person is doing, then Admin. Replaces
// FR-23.2's Plan / Execute / Support. Pages, routes and permissions are unchanged.
export const navGroups: NavGroup[] = [
  {
    id: 'home',
    label: tr('Home'),
    items: [
      { to: '/today', label: tr('Today'), icon: CalendarCheck },
      { to: '/', label: tr('Dashboard'), icon: LayoutDashboard },
    ],
  },
  {
    id: 'plan',
    label: tr('Plan'),
    items: [
      { to: '/vision-areas', label: tr('Vision Areas'), icon: Compass },
      { to: '/dreams', label: tr('Dreams'), icon: MoonStar },
      // V37 (D-37-3): Timeline, Management Report and Saved reports are tabs on this page.
      { to: '/vision-map', label: tr('Vision Map'), icon: Network },
      { to: '/plan', label: tr('Plan checklist'), icon: ListChecks },
    ],
  },
  {
    id: 'do',
    label: tr('Do'),
    items: [
      { to: '/goals', label: tr('Goals'), icon: Target },
      { to: '/steps', label: tr('Steps'), icon: Footprints },
      { to: '/tasks', label: tr('Tasks'), icon: CheckSquare },
      { to: '/obstacles', label: tr('Obstacles'), icon: TrafficCone },
      { to: '/partners', label: tr('Partners'), icon: Handshake },
      { to: '/communication', label: tr('Communication'), icon: Send },
    ],
  },
  {
    id: 'review',
    label: tr('Review'),
    items: [
      { to: '/reviews', label: tr('Reviews'), icon: ClipboardList },
      { to: '/streamline', label: tr('Streamline'), icon: Scissors },
      { to: '/insights', label: tr('Insights'), icon: Lightbulb },
    ],
  },
  {
    id: 'know',
    label: tr('Know yourself'),
    items: [
      { to: '/assessment', label: tr('Assessment'), icon: Gauge },
      // V8.1 FR-79 to FR-84: what I bring, what I lack, and how each gap is covered.
      { to: '/strengths', label: tr('Strengths and Gaps'), icon: Scale },
      { to: '/work-style', label: tr('Work Style'), icon: Fingerprint },
      { to: '/modules', label: tr('Modules'), icon: GraduationCap },
      { to: '/journal', label: tr('Journal'), icon: BookOpen },
    ],
  },
  {
    // V37 (D-37-1, BR-37-2): shown only with a permission, as before (V23 UM-FR-05).
    id: 'admin',
    label: tr('Admin'),
    items: [
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

/** V37 (D-37-3): the tabs shared by the Vision Map pages. */
export const visionMapTabs: NavItem[] = [
  { to: '/vision-map', label: tr('Map'), icon: Network },
  // V16 (TL-FR-05): every dream on one calendar.
  { to: '/vision-map/timeline', label: tr('Timeline'), icon: ChartGantt },
  // V18 (MR-FR-01): plan health and documents to share.
  { to: '/vision-map/report', label: tr('Management Report'), icon: FileBarChart },
  // V19 (RM-FR-17): reports saved from it, draft and final.
  { to: '/vision-map/reports', label: tr('Saved reports'), icon: FolderClock },
];

/** V37 (D-37-4): the account menu at the foot of the sidebar, before Log out. */
export const accountMenuItems: NavItem[] = [
  { to: '/settings/account', label: tr('Account'), icon: UserCog },
  { to: '/settings/security', label: tr('Security'), icon: KeyRound },
  { to: '/settings/appearance', label: tr('Appearance'), icon: Palette },
  { to: '/import-export', label: tr('Import / Export'), icon: FileSpreadsheet },
  { to: '/issue-reports', label: tr('Issue Reports'), icon: Bug },
];

/** V37 (D-37-5): the Dream Coach, opened by "Ask the coach" at the top of the sidebar. */
export const coachItem: NavItem = { to: '/dream-coach', label: tr('Dream Coach'), icon: Bot };

/** The group a path belongs to, so the sidebar can keep it unfolded (BR-37-3). */
export function groupIdFor(pathname: string, groups: NavGroup[]): string | null {
  const to = activeNavTo(navPathname(pathname), groups.flatMap((group) => group.items));
  return groups.find((group) => group.items.some((item) => item.to === to))?.id ?? null;
}

/** Whether this person may see the item: no permission named, or the one (V24: any one of those) named. */
export function canSeeNavItem(user: AuthUser | null | undefined, item: NavItem): boolean {
  if (!item.permission) return true;
  return Array.isArray(item.permission) ? item.permission.some((one) => can(user, one)) : can(user, item.permission);
}

/** The nav groups this person may see (V23: by permission, not role name). */
export function navGroupsFor(user: AuthUser | null | undefined): NavGroup[] {
  return navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => canSeeNavItem(user, item)) }))
    // V37 (D-37-1): a group with nothing this person may see is not shown.
    .filter((group) => group.items.length > 0);
}

/**
 * Every destination, wherever it lives (V37 D-37-6, BR-37-1): the sidebar
 * groups, the Vision Map tabs, the account menu and the coach. The header's
 * page title and the command palette read this, so no page is lost by moving.
 * The Map tab is the Vision Map link itself, so it is not listed twice.
 */
export const navItems: NavItem[] = [
  ...navGroups.flatMap((group) => group.items),
  ...visionMapTabs.filter((tab) => tab.to !== '/vision-map'),
  ...accountMenuItems,
  coachItem,
];
