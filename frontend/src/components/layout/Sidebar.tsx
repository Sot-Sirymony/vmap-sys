import { useState, type MouseEvent } from 'react';
import { ChevronsUpDown, LogOut } from 'lucide-react';
import { NavLink, useLocation } from 'react-router-dom';
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
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import ButtonBase from '@mui/material/ButtonBase';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { useAuth } from '@/context/AuthContext';
import { navItems } from './nav-items';
import { useSidebarState } from './sidebar-context';

const DRAWER_WIDTH = 256;
const DRAWER_WIDTH_COLLAPSED = 64;

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'VM';
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <Toolbar sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2 }}>
      <Box
        sx={{
          width: 32,
          height: 32,
          borderRadius: 1.5,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          display: 'grid',
          placeItems: 'center',
          fontWeight: 700,
          fontSize: '0.8rem',
          flexShrink: 0,
        }}
      >
        VM
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

function NavUser({ collapsed }: { collapsed: boolean }) {
  const { user, logout } = useAuth();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  function handleOpen(event: MouseEvent<HTMLElement>) {
    setAnchorEl(event.currentTarget);
  }

  function handleLogout() {
    setAnchorEl(null);
    logout();
  }

  return (
    <>
      <ButtonBase
        onClick={handleOpen}
        sx={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          p: 1,
          borderRadius: 1.5,
          justifyContent: collapsed ? 'center' : 'flex-start',
          '&:hover': { bgcolor: 'action.hover' },
        }}
      >
        <Avatar sx={{ width: 32, height: 32, fontSize: '0.8rem' }}>{initials(user?.fullName ?? 'VM')}</Avatar>
        {!collapsed && (
          <>
            <Box sx={{ flex: 1, overflow: 'hidden', textAlign: 'left' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{user?.fullName ?? 'Workspace'}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>{user?.email ?? ''}</Typography>
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

function NavList({ collapsed, onNavigate }: { collapsed: boolean; onNavigate: () => void }) {
  const location = useLocation();

  return (
    <List sx={{ px: 1 }}>
      {navItems.map((item) => {
        const isActive = item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to);
        const button = (
          <ListItemButton
            component={NavLink}
            to={item.to}
            end={item.to === '/'}
            selected={isActive}
            onClick={onNavigate}
            sx={{
              minHeight: 40,
              borderRadius: 1.5,
              justifyContent: collapsed ? 'center' : 'flex-start',
              px: collapsed ? 1 : 1.5,
              borderLeft: '3px solid transparent',
              transition: (theme) => theme.transitions.create(['background-color', 'border-color'], { duration: 120 }),
              '&:hover': { borderLeftColor: 'primary.light' },
              '&.Mui-selected': { borderLeftColor: 'primary.main' },
            }}
          >
            <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 1.5, justifyContent: 'center', color: 'inherit' }}>
              <item.icon size={18} />
            </ListItemIcon>
            {!collapsed && <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontSize: '0.875rem' } } }} />}
          </ListItemButton>
        );
        return (
          <ListItem key={item.to} disablePadding sx={{ display: 'block', mb: 0.5 }}>
            {collapsed ? <Tooltip title={item.label} placement="right">{button}</Tooltip> : button}
          </ListItem>
        );
      })}
    </List>
  );
}

function SidebarContents({ collapsed, onNavigate }: { collapsed: boolean; onNavigate: () => void }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Brand collapsed={collapsed} />
      <Divider />
      <Box sx={{ flexGrow: 1, overflowY: 'auto', overflowX: 'hidden', py: 1 }}>
        <NavList collapsed={collapsed} onNavigate={onNavigate} />
      </Box>
      <Divider />
      <Box sx={{ p: 1 }}>
        <NavUser collapsed={collapsed} />
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
