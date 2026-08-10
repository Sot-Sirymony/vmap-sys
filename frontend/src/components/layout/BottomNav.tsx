import { CheckSquare, ClipboardList, LayoutDashboard, Menu, Network } from 'lucide-react';
import { NavLink, useLocation } from 'react-router';
import { navPathname } from './nav-items';
import { useSidebarState } from './sidebar-context';

// The four destinations that carry daily use, in the method's own order:
// see where you stand, work the map, execute tasks, review. Everything else
// lives behind More, which opens the full drawer. Icons come from the same
// concept vocabulary as the sidebar (docs/uxui-icons.md).
const TABS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/vision-map', label: 'Map', icon: Network },
  { to: '/tasks', label: 'Tasks', icon: CheckSquare },
  { to: '/reviews', label: 'Reviews', icon: ClipboardList },
];

/**
 * Mobile-only bottom tab bar (hidden ≥md, where the sidebar rail takes over).
 * On a phone the sidebar's sixteen destinations sit behind a hamburger; the
 * four core ones deserve to be one thumb-tap away.
 */
export function BottomNav() {
  const location = useLocation();
  const { toggle } = useSidebarState();
  const pathname = navPathname(location.pathname);

  return (
    <nav className="bottom-nav" aria-label="Primary">
      {TABS.map((tab) => {
        const active = tab.to === '/' ? pathname === '/' : pathname.startsWith(tab.to);
        return (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={`bottom-nav-item${active ? ' bottom-nav-item--active' : ''}`}
            aria-current={active ? 'page' : undefined}
          >
            <tab.icon size={20} aria-hidden />
            <span>{tab.label}</span>
          </NavLink>
        );
      })}
      <button type="button" className="bottom-nav-item" onClick={toggle} aria-label="More navigation">
        <Menu size={20} aria-hidden />
        <span>More</span>
      </button>
    </nav>
  );
}
