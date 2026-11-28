import { useState, type MouseEvent } from 'react';
import { ChevronDown, ChevronsUpDown, LogOut, Search } from 'lucide-react';
import { NavLink, useLocation, useNavigate } from 'react-router';
import Drawer from '@mui/material/Drawer';
import Toolbar from '@mui/material/Toolbar';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Tooltip from '@mui/material/Tooltip';
import Divider from '@mui/material/Divider';
import Box from '@mui/material/Box';
import { BrandMark } from '../common/BrandMark';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import ButtonBase from '@mui/material/ButtonBase';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { useAuth } from '@/context/AuthContext';
import { useInterfaceStyle } from '@/hooks/useInterfaceStyle';
import { useI18n } from '@/i18n/I18nContext';
import { accountMenuItems, activeNavTo, coachItem, groupIdFor, navGroupsFor, navPathname } from './nav-items';
import { useCommandPalette } from './command-palette-context';
import { useSidebarState } from './sidebar-context';
import { tr } from '../../i18n/translate';

const DRAWER_WIDTH = 256;
const DRAWER_WIDTH_COLLAPSED = 64;

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'VM';
}

/**
 * The modifier this device's users actually press. Printing ⌘ to a Windows user
 * is worse than printing nothing — it names a key their keyboard does not have.
 * Matches the check in GlobalShortcuts, which accepts either modifier.
 */
function shortcutModifier() {
  if (typeof navigator === 'undefined') {
    return 'Ctrl';
  }
  return /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent) ? '⌘' : 'Ctrl';
}

/**
 * FR-48.3 — the Modern sidebar's search affordance.
 *
 * It is a button, not an input, and that is deliberate: it opens the command
 * palette, which has its own field. Rendering a real text input here would mean
 * two search boxes, one of which throws its own keystrokes away the moment the
 * palette takes focus.
 */
function SidebarSearch() {
  const { openPalette } = useCommandPalette();
  return (
    <Box sx={{ px: 2, pb: 1.5 }}>
      <ButtonBase
        onClick={openPalette}
        // WCAG 2.5.3: the accessible name is the visible word, so speech users
        // can say what they see. The shortcut is announced through
        // aria-keyshortcuts and the kbd is hidden from assistive tech.
        aria-keyshortcuts="Control+K Meta+K"
        sx={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1.25,
          py: 0.875,
          borderRadius: 'var(--radius)',
          border: '1px solid',
          borderColor: 'divider',
          // Solid inside the floating sidebar (D-26-4).
          bgcolor: 'var(--style-card-bg)',
          color: 'text.secondary',
          transition: (theme) => theme.transitions.create(['border-color', 'background-color']),
          '&:hover': { borderColor: 'primary.main' },
        }}
      >
        <Search size={15} />
        <Typography variant="body2" sx={{ flex: 1, textAlign: 'left' }} noWrap>
          {tr('Search')}
        </Typography>
        <Box
          component="kbd"
          aria-hidden="true"
          sx={{
            px: 0.625,
            py: 0.125,
            borderRadius: 'var(--radius-sm)',
            border: '1px solid',
            borderColor: 'divider',
            bgcolor: 'var(--muted)',
            fontFamily: 'inherit',
            fontSize: 'var(--font-caption)',
            fontWeight: 600,
            lineHeight: 1.6,
            flexShrink: 0,
          }}
        >
          {shortcutModifier()} K
        </Box>
      </ButtonBase>
    </Box>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <Toolbar sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2 }}>
      {/* The colour mark carries its own palette now, so it sits directly on
          the sidebar instead of inside an accent tile. */}
      <Box sx={{ display: 'grid', placeItems: 'center', flexShrink: 0 }}>
        <BrandMark size={26} variant="color" />
      </Box>
      {!collapsed && (
        <Box sx={{ overflow: 'hidden' }}>
          <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{tr('Vision Map')}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap>Mapping System</Typography>
        </Box>
      )}
    </Toolbar>
  );
}

/**
 * The bottom card. Classic shows it as a plain user row; Modern presents the
 * same control as a workspace card — bordered, raised off the rail, workspace
 * name on top and the signed-in account beneath.
 *
 * The Modern label is honest about what the app currently is. The app has no
 * multi-tenancy, so the card names *this* user's workspace rather than offering
 * a list of workspaces to switch between: a chevron that opened a picker with
 * exactly one entry in it would be a promise the product does not keep. The
 * menu holds what it has always held.
 */
function NavUser({ collapsed, modern }: { collapsed: boolean; modern: boolean }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  function handleOpen(event: MouseEvent<HTMLElement>) {
    setAnchorEl(event.currentTarget);
  }

  function handleLogout() {
    setAnchorEl(null);
    logout();
  }

  const name = user?.fullName ?? tr('Workspace');
  const primaryLine = modern ? `${name.split(/\s+/)[0]}'s Workspace` : name;
  const secondaryLine = modern ? name : (user?.email ?? '');

  return (
    <>
      <ButtonBase
        onClick={handleOpen}
        // No aria-label: the visible name and email already name this button, and
        // a separate label must repeat all of that text anyway (WCAG 2.5.3).
        sx={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          p: 1,
          borderRadius: 'var(--style-radius-card)',
          justifyContent: collapsed ? 'center' : 'flex-start',
          // Modern lifts the card off the rail so it reads as a distinct
          // control rather than as the last row of the nav list.
          ...(modern && !collapsed
            ? {
                // A solid card inside the floating sidebar (D-26-4).
                bgcolor: 'var(--style-card-bg)',
                border: 'var(--style-card-border)',
                boxShadow: 'var(--style-shadow-rest)',
              }
            : null),
          '&:hover': { bgcolor: modern && !collapsed ? 'var(--style-card-bg)' : 'action.hover', borderColor: modern ? 'primary.main' : undefined },
        }}
      >
        <Avatar
          variant={modern ? 'rounded' : 'circular'}
          sx={{
            width: 32,
            height: 32,
            fontSize: 'var(--font-meta)',
            fontWeight: 600,
            ...(modern
              ? { bgcolor: 'primary.main', color: 'primary.contrastText', borderRadius: 'var(--radius-md)' }
              : { bgcolor: 'var(--muted)', color: 'var(--foreground)' }),
          }}
        >
          {initials(name)}
        </Avatar>
        {!collapsed && (
          <>
            <Box sx={{ flex: 1, overflow: 'hidden', textAlign: 'left' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{primaryLine}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>{secondaryLine}</Typography>
            </Box>
            <ChevronsUpDown size={16} />
          </>
        )}
      </ButtonBase>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        {/* V37 (D-37-4): the settings pages live here, out of the daily list. */}
        {accountMenuItems.map((item) => (
          <MenuItem key={item.to} onClick={() => { setAnchorEl(null); navigate(item.to); }}>
            <ListItemIcon sx={{ minWidth: 32 }}>
              <item.icon size={16} />
            </ListItemIcon>
            {tr(item.label)}
          </MenuItem>
        ))}
        <Divider />
        <MenuItem onClick={handleLogout}>
          <ListItemIcon sx={{ minWidth: 32 }}>
            <LogOut size={16} />
          </ListItemIcon>
          {tr('Log out')}
        </MenuItem>
      </Menu>
    </>
  );
}

function NavList({ collapsed, onNavigate, modern }: { collapsed: boolean; onNavigate: () => void; modern: boolean }) {
  const { t } = useI18n();
  const location = useLocation();
  const { user } = useAuth();
  const pathname = navPathname(location.pathname);
  const groups = navGroupsFor(user);
  const activeTo = activeNavTo(pathname, groups.flatMap((group) => group.items));
  // V37 (D-37-2, BR-37-3): the current page's group is always open; the others
  // stay as the person left them, on this device only.
  const currentGroup = groupIdFor(location.pathname, groups);
  const [openGroups, setOpenGroups] = useState<string[]>(readOpenGroups);

  function toggle(id: string) {
    const next = openGroups.includes(id) ? openGroups.filter((open) => open !== id) : [...openGroups, id];
    setOpenGroups(next);
    writeOpenGroups(next);
  }

  // V37 (D-37-1): five groups by activity, then Admin. Collapsed mode drops the
  // headings, shows every icon and keeps a subtle divider between groups.
  return (
    <Box>
    <List sx={{ px: 1 }}>
      <CoachButton collapsed={collapsed} onNavigate={onNavigate} modern={modern} active={pathname.startsWith(coachItem.to)} />
      {groups.map((group, groupIndex) => {
        const isCurrent = group.id === currentGroup;
        const open = collapsed || isCurrent || openGroups.includes(group.id);
        const listId = `nav-group-${group.id}`;
        return (
        <li key={group.id} style={{ listStyle: 'none' }}>
          {collapsed
            ? groupIndex > 0 && <Divider sx={{ my: 1 }} />
            : (
              <ButtonBase
                // The current page's group can't fold (BR-37-3); its heading still says it is open.
                onClick={isCurrent ? undefined : () => toggle(group.id)}
                aria-disabled={isCurrent || undefined}
                aria-expanded={open}
                aria-controls={listId}
                sx={{
                  width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  px: 1.5, pt: groupIndex === 0 ? 0.5 : 2, pb: 0.5, borderRadius: 'var(--style-radius-nav)',
                  color: 'text.secondary',
                }}
              >
                <Typography component="span" variant="overline" sx={{ letterSpacing: '0.05em', lineHeight: 1.6 }}>{t(group.label)}</Typography>
                <ChevronDown size={14} aria-hidden style={{ transform: open ? 'none' : 'rotate(-90deg)', transition: 'transform 120ms' }} />
              </ButtonBase>
            )}
          {open && (
          <List disablePadding id={listId}>
      {group.items.map((item) => {
        const isActive = item.to === activeTo;
        const button = (
          <ListItemButton
            component={NavLink}
            to={item.to}
            end={item.to === '/'}
            selected={isActive}
            onClick={onNavigate}
            sx={{
              minHeight: 40,
              justifyContent: collapsed ? 'center' : 'flex-start',
              px: collapsed ? 1 : 1.5,
              transition: (theme) => theme.transitions.create(['background-color', 'border-color', 'color'], { duration: 120 }),
              // FR-48.3: the active state is the one nav detail the two styles
              // disagree about. Classic marks it with a Fluent left rule;
              // Modern fills a soft pill instead. Both use the accent, so the
              // signal is the same colour either way — only its shape changes.
              ...(modern
                ? {
                    borderRadius: 'var(--style-radius-nav)',
                    '&.Mui-selected': {
                      bgcolor: 'var(--sidebar-accent)',
                      color: 'var(--sidebar-accent-foreground)',
                      fontWeight: 600,
                      '&:hover': { bgcolor: 'var(--sidebar-accent)' },
                    },
                  }
                : {
                    borderRadius: 'var(--style-radius-nav)',
                    borderLeft: 'var(--style-nav-bar) solid transparent',
                    '&:hover': { borderLeftColor: 'primary.light' },
                    '&.Mui-selected': { borderLeftColor: 'primary.main' },
                  }),
            }}
          >
            <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 1.5, justifyContent: 'center', color: 'inherit' }}>
              <item.icon size={18} />
            </ListItemIcon>
            {!collapsed && <ListItemText primary={t(item.label)} slotProps={{ primary: { sx: { fontSize: 'var(--font-body-sm)' } } }} />}
          </ListItemButton>
        );
        return (
          <ListItem key={item.to} disablePadding sx={{ display: 'block', mb: 0.5 }}>
            {collapsed ? <Tooltip title={t(item.label)} placement="right">{button}</Tooltip> : button}
          </ListItem>
        );
      })}
          </List>
          )}
        </li>
        );
      })}
    </List>
    </Box>
  );
}

const OPEN_GROUPS_KEY = 'vms.sidebar.openGroups';

/** D-37-2: remembered on this device only; a missing or blocked store opens nothing extra. */
function readOpenGroups(): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem(OPEN_GROUPS_KEY) ?? '[]') as unknown;
    return Array.isArray(stored) ? stored.filter((value): value is string => typeof value === 'string') : [];
  } catch {
    return [];
  }
}

function writeOpenGroups(groups: string[]) {
  try {
    localStorage.setItem(OPEN_GROUPS_KEY, JSON.stringify(groups));
  } catch {
    // Without storage the groups still fold for this visit.
  }
}

/** V37 (D-37-5): "Ask the coach" at the top of the sidebar, opening the Dream Coach. */
function CoachButton({ collapsed, onNavigate, modern, active }: { collapsed: boolean; onNavigate: () => void; modern: boolean; active: boolean }) {
  const label = tr('Ask the coach');
  const button = (
    <ListItemButton
      component={NavLink}
      to={coachItem.to}
      selected={active}
      onClick={onNavigate}
      sx={{
        minHeight: 40,
        justifyContent: collapsed ? 'center' : 'flex-start',
        px: collapsed ? 1 : 1.5,
        border: 1,
        borderColor: 'primary.main',
        color: 'primary.main',
        borderRadius: modern ? 'var(--style-radius-nav)' : 'var(--style-radius-control)',
      }}
    >
      <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 1.5, justifyContent: 'center', color: 'inherit' }}>
        <coachItem.icon size={18} />
      </ListItemIcon>
      {!collapsed && <ListItemText primary={label} slotProps={{ primary: { sx: { fontSize: 'var(--font-body-sm)', fontWeight: 600 } } }} />}
    </ListItemButton>
  );
  return (
    <ListItem disablePadding sx={{ display: 'block', mb: 1 }}>
      {collapsed ? <Tooltip title={label} placement="right">{button}</Tooltip> : button}
    </ListItem>
  );
}

function SidebarContents({ collapsed, onNavigate }: { collapsed: boolean; onNavigate: () => void }) {
  // IS-FR-05: the navigation shape comes from the style's --style-nav-shape
  // token (pill for Modern and Liquid Glass, rect with an accent bar for
  // Classic), never from the style's name.
  const { navShape } = useInterfaceStyle();
  const modern = navShape === 'pill';

  return (
    <Box component="nav" aria-label={tr('Sidebar')} sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Brand collapsed={collapsed} />
      {/* Collapsed to an icon rail there is no room for the search row, and the
          ⌘K shortcut still works — so it is omitted rather than squeezed. */}
      {modern && !collapsed && <SidebarSearch />}
      {/* Modern separates the rail's regions with whitespace instead of rules,
          which is what makes the sections read as floating groups. */}
      {!modern && <Divider />}
      <Box sx={{ flexGrow: 1, overflowY: 'auto', overflowX: 'hidden', py: 1 }}>
        <NavList collapsed={collapsed} onNavigate={onNavigate} modern={modern} />
      </Box>
      {!modern && <Divider />}
      <Box sx={{ p: 1 }}>
        <NavUser collapsed={collapsed} modern={modern} />
        {!collapsed && (
          <Typography
            variant="caption"
            color="text.secondary"
            align="center"
            noWrap
            sx={{ display: 'block', pt: 1, opacity: 0.7 }}
          >
            v{import.meta.env.VITE_APP_VERSION}
          </Typography>
        )}
      </Box>
    </Box>
  );
}

/**
 * BRD 26: the sidebar is a floating layer — glass in Liquid Glass, solid
 * otherwise. Below 600px global.css turns the drawer solid (§5.6).
 */
const sidebarPaper = {
  // Sunken in Modern, raised in Classic, glass in Liquid Glass (--style-sidebar-bg).
  bgcolor: 'var(--style-sidebar-bg)',
  backdropFilter: 'var(--style-blur)',
  WebkitBackdropFilter: 'var(--style-blur)',
} as const;

export function Sidebar() {
  const { open, mobileOpen, closeMobile } = useSidebarState();

  return (
    <>
      {/* Mobile: temporary overlay drawer, always full width when open */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={closeMobile}
        slotProps={{ paper: { className: 'app-sidebar' } }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': { ...sidebarPaper, width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <SidebarContents collapsed={false} onNavigate={closeMobile} />
      </Drawer>

      {/* Desktop: permanent rail, collapsible to icon-only width */}
      <Drawer
        variant="permanent"
        slotProps={{ paper: { className: 'app-sidebar' } }}
        sx={{
          display: { xs: 'none', md: 'block' },
          width: open ? DRAWER_WIDTH : DRAWER_WIDTH_COLLAPSED,
          flexShrink: 0,
          whiteSpace: 'nowrap',
          boxSizing: 'border-box',
          transition: (theme) => theme.transitions.create('width', {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
          }),
          '& .MuiDrawer-paper': {
            ...sidebarPaper,
            width: open ? DRAWER_WIDTH : DRAWER_WIDTH_COLLAPSED,
            overflowX: 'hidden',
            boxSizing: 'border-box',
            transition: (theme) => theme.transitions.create('width', {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
          },
        }}
      >
        <SidebarContents collapsed={!open} onNavigate={() => {}} />
      </Drawer>
    </>
  );
}
