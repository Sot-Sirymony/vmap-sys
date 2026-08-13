import { useState, type MouseEvent } from 'react';
import { ChevronsUpDown, LogOut, Search } from 'lucide-react';
import { NavLink, useLocation } from 'react-router';
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
import { useThemeSettings } from '@/context/ThemeModeContext';
import { navGroups, navPathname } from './nav-items';
import { useCommandPalette } from './command-palette-context';
import { useSidebarState } from './sidebar-context';

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
        aria-label={`Search and commands (${shortcutModifier()}+K)`}
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
          bgcolor: 'background.paper',
          color: 'text.secondary',
          transition: (theme) => theme.transitions.create(['border-color', 'background-color']),
          '&:hover': { borderColor: 'primary.main' },
        }}
      >
        <Search size={15} />
        <Typography variant="body2" sx={{ flex: 1, textAlign: 'left' }} noWrap>
          Search
        </Typography>
        <Box
          component="kbd"
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
          <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>Vision Map</Typography>
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
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  function handleOpen(event: MouseEvent<HTMLElement>) {
    setAnchorEl(event.currentTarget);
  }

  function handleLogout() {
    setAnchorEl(null);
    logout();
  }

  const name = user?.fullName ?? 'Workspace';
  const primaryLine = modern ? `${name.split(/\s+/)[0]}'s Workspace` : name;
  const secondaryLine = modern ? name : (user?.email ?? '');

  return (
    <>
      <ButtonBase
        onClick={handleOpen}
        aria-label={modern ? `${primaryLine} — account menu` : 'Account menu'}
        sx={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          p: 1,
          borderRadius: modern ? 'var(--radius)' : 1.5,
          justifyContent: collapsed ? 'center' : 'flex-start',
          // Modern lifts the card off the rail so it reads as a distinct
          // control rather than as the last row of the nav list.
          ...(modern && !collapsed
            ? {
                border: '1px solid',
                borderColor: 'divider',
                bgcolor: 'background.paper',
                boxShadow: 'var(--shadow-2)',
              }
            : null),
          '&:hover': { bgcolor: modern && !collapsed ? 'background.paper' : 'action.hover', borderColor: modern ? 'primary.main' : undefined },
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
        <MenuItem onClick={handleLogout}>
          <ListItemIcon sx={{ minWidth: 32 }}>
            <LogOut size={16} />
          </ListItemIcon>
          Log out
        </MenuItem>
      </Menu>
    </>
  );
}

function NavList({ collapsed, onNavigate, modern }: { collapsed: boolean; onNavigate: () => void; modern: boolean }) {
  const location = useLocation();
  const pathname = navPathname(location.pathname);

  // FR-23.2: three labeled sections (Plan / Execute / Support) instead of a
  // flat list of eleven entries. Collapsed mode drops the labels and keeps a
  // subtle divider between groups.
  return (
    <Box>
    <List sx={{ px: 1 }}>
      {navGroups.map((group, groupIndex) => (
        <li key={group.label} style={{ listStyle: 'none' }}>
          {collapsed
            ? groupIndex > 0 && <Divider sx={{ my: 1 }} />
            : (
              <Typography
                component="p"
                variant="overline"
                sx={{ px: 1.5, pt: groupIndex === 0 ? 0.5 : 2, pb: 0.5, color: 'text.secondary', letterSpacing: '0.05em' }}
              >
                {group.label}
              </Typography>
            )}
          <List disablePadding>
      {group.items.map((item) => {
        const isActive = item.to === '/' ? pathname === '/' : pathname.startsWith(item.to);
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
                    borderRadius: 999,
                    '&.Mui-selected': {
                      bgcolor: 'var(--sidebar-accent)',
                      color: 'var(--sidebar-accent-foreground)',
                      fontWeight: 600,
                      '&:hover': { bgcolor: 'var(--sidebar-accent)' },
                    },
                  }
                : {
                    borderRadius: 1.5,
                    borderLeft: '3px solid transparent',
                    '&:hover': { borderLeftColor: 'primary.light' },
                    '&.Mui-selected': { borderLeftColor: 'primary.main' },
                  }),
            }}
          >
            <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 1.5, justifyContent: 'center', color: 'inherit' }}>
              <item.icon size={18} />
            </ListItemIcon>
            {!collapsed && <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontSize: 'var(--font-body-sm)' } } }} />}
          </ListItemButton>
        );
        return (
          <ListItem key={item.to} disablePadding sx={{ display: 'block', mb: 0.5 }}>
            {collapsed ? <Tooltip title={item.label} placement="right">{button}</Tooltip> : button}
          </ListItem>
        );
      })}
          </List>
        </li>
      ))}
    </List>
    </Box>
  );
}

function SidebarContents({ collapsed, onNavigate }: { collapsed: boolean; onNavigate: () => void }) {
  const { settings } = useThemeSettings();
  // Liquid Glass builds on the Modern chrome (search pill, workspace card) —
  // Classic is the only style that swaps the navigation anatomy.
  const modern = settings.interfaceStyle !== 'classic';

  return (
    <Box component="nav" aria-label="Sidebar" sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
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

export function Sidebar() {
  const { open, mobileOpen, closeMobile } = useSidebarState();

  return (
    <>
      {/* Mobile: temporary overlay drawer, always full width when open */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={closeMobile}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <SidebarContents collapsed={false} onNavigate={closeMobile} />
      </Drawer>

      {/* Desktop: permanent rail, collapsible to icon-only width */}
      <Drawer
        variant="permanent"
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
