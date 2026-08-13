import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Avatar from '@mui/material/Avatar';
import AvatarGroup from '@mui/material/AvatarGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import { Bug, PanelLeft, Plus, Upload } from 'lucide-react';
import { AppearanceMenu } from './AppearanceMenu';
import { navItems } from './nav-items';
import { useSidebarState } from './sidebar-context';
import { ReportIssueModal } from '../common/ReportIssueModal';
import { useAuth } from '../../context/AuthContext';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { listPartners } from '../../api/partnerApi';
import type { Partner } from '../../types/vision';

function currentPageLabel(pathname: string) {
  const exact = navItems.find((item) => (item.to === '/' ? pathname === '/' : pathname === item.to));
  if (exact) {
    return exact.label;
  }
  const closest = navItems.find((item) => item.to !== '/' && pathname.startsWith(item.to));
  return closest?.label ?? 'Dashboard';
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
}

/**
 * FR-48.4 — the collaborator stack.
 *
 * The pattern this is modelled on shows a team roster, and this app has no
 * teams. It has Partners: the real people a user has recorded as mentors,
 * experts, and advisors on their dreams. So the stack shows those, and the `+`
 * adds one — a decoration that means something and leads somewhere, rather than
 * placeholder faces implying a collaboration feature that does not exist.
 *
 * Failures are swallowed on purpose. This is header ornament; a partner list
 * that will not load must not put an error banner above every page, and the
 * Partners page itself reports the same failure properly.
 */
function PartnerStack() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [partners, setPartners] = useState<Partner[]>([]);

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    listPartners(token, 0, 5, false, 'updatedAt,desc')
      .then((page) => {
        if (!cancelled) {
          setPartners(page.content);
        }
      })
      .catch(() => {
        // Ornament, not information — see above.
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center', gap: 0.5 }}>
      {partners.length > 0 && (
        <AvatarGroup
          max={4}
          onClick={() => navigate('/partners')}
          sx={{
            cursor: 'pointer',
            '& .MuiAvatar-root': {
              width: 28,
              height: 28,
              fontSize: 'var(--font-caption)',
              fontWeight: 600,
              borderColor: 'background.paper',
              bgcolor: 'var(--muted)',
              color: 'var(--foreground)',
            },
          }}
        >
          {partners.map((partner) => (
            <Tooltip key={partner.id} title={partner.name}>
              <Avatar alt={partner.name}>{initials(partner.name)}</Avatar>
            </Tooltip>
          ))}
        </AvatarGroup>
      )}
      <Tooltip title="Add a partner">
        <IconButton
          size="small"
          aria-label="Add a partner"
          onClick={() => navigate('/partners?create=partner')}
          sx={{
            width: 28,
            height: 28,
            border: '1px dashed',
            borderColor: 'divider',
            color: 'text.secondary',
            '&:hover': { borderColor: 'primary.main', color: 'primary.main' },
          }}
        >
          <Plus size={14} />
        </IconButton>
      </Tooltip>
    </Box>
  );
}

/**
 * FR-48.4 — the Modern header's title region.
 *
 * A greeting belongs on the page you land on, not on all eleven of them, so it
 * renders on the dashboard and every other page states where you are. That is
 * still a change from Classic: one title, sized as a title, instead of a
 * two-link breadcrumb trail whose first link was never a destination.
 */
function ModernTitle({ pathname }: { pathname: string }) {
  const { user } = useAuth();
  const isDashboard = pathname === '/';
  const firstName = (user?.fullName ?? '').trim().split(/\s+/)[0];

  if (!isDashboard) {
    return (
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography variant="subtitle1" noWrap>{currentPageLabel(pathname)}</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
      <Typography variant="subtitle1" noWrap>
        {firstName ? `Welcome back, ${firstName}` : 'Welcome back'} <span aria-hidden>👋</span>
      </Typography>
      <Typography variant="caption" color="text.secondary" noWrap sx={{ display: { xs: 'none', sm: 'block' } }}>
        Here is where your dreams stand today.
      </Typography>
    </Box>
  );
}

export function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  const { toggle } = useSidebarState();
  const { settings } = useThemeSettings();
  // Liquid Glass builds on the Modern chrome (greeting header, not
  // breadcrumbs) — Classic is the only style that swaps the anatomy.
  const modern = settings.interfaceStyle !== 'classic';
  // FR-38.1: the "Report an issue" affordance lives in the header, so it's
  // reachable from every authenticated page without leaving the current one.
  const [reportOpen, setReportOpen] = useState(false);

  return (
    <AppBar
      component="header"
      position="sticky"
      color="transparent"
      elevation={0}
      className="app-header"
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        // Glass header: stays put while the page scrolls under it, translucent
        // enough to hint at what's passing beneath. High contrast makes it
        // opaque again via the .app-header override in global.css.
        bgcolor: 'color-mix(in srgb, var(--background) 82%, transparent)',
        backdropFilter: 'blur(10px)',
        WebkitBackdropFilter: 'blur(10px)',
      }}
    >
      <Toolbar sx={{ gap: 1.5, minHeight: `${modern ? 64 : 56}px !important` }}>
        <IconButton onClick={toggle} aria-label="Toggle sidebar" edge="start" size="small">
          <PanelLeft size={18} />
        </IconButton>

        {modern ? (
          <ModernTitle pathname={location.pathname} />
        ) : (
          <>
            <Divider orientation="vertical" flexItem sx={{ height: 20, alignSelf: 'center' }} />
            <Breadcrumbs separator="›" sx={{ flexGrow: 1 }}>
              <Typography variant="body2" color="text.secondary">Vision Mapping</Typography>
              <Typography variant="body2" color="text.primary">{currentPageLabel(location.pathname)}</Typography>
            </Breadcrumbs>
          </>
        )}

        {modern && <PartnerStack />}

        {/* Modern trades the labelled button for an icon to make room for the
            action cluster; the tooltip and aria-label keep it named. */}
        {modern ? (
          <Tooltip title="Report an issue">
            <IconButton size="small" aria-label="Report an issue" onClick={() => setReportOpen(true)}>
              <Bug size={18} />
            </IconButton>
          </Tooltip>
        ) : (
          <Button
            onClick={() => setReportOpen(true)}
            size="small"
            color="inherit"
            startIcon={<Bug size={16} />}
            sx={{ textTransform: 'none' }}
          >
            Report an issue
          </Button>
        )}

        {/* The reference design's primary header action is Share. This app has
            no sharing, so the slot holds the real way work leaves the system —
            the Excel export — rather than a button that would have nothing to
            do when pressed. */}
        {modern && (
          <Button
            onClick={() => navigate('/import-export')}
            size="small"
            variant="contained"
            startIcon={<Upload size={15} />}
            sx={{ display: { xs: 'none', sm: 'inline-flex' }, flexShrink: 0 }}
          >
            Export
          </Button>
        )}

        <AppearanceMenu />
      </Toolbar>
      {reportOpen && <ReportIssueModal onClose={() => setReportOpen(false)} />}
    </AppBar>
  );
}
