import { useState } from 'react';
import { useLocation } from 'react-router';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import { MessageSquareWarning, PanelLeft } from 'lucide-react';
import { AppearanceMenu } from './AppearanceMenu';
import { navItems } from './nav-items';
import { useSidebarState } from './sidebar-context';
import { ReportIssueModal } from '../common/ReportIssueModal';

function currentPageLabel(pathname: string) {
  const exact = navItems.find((item) => (item.to === '/' ? pathname === '/' : pathname === item.to));
  if (exact) {
    return exact.label;
  }
  const closest = navItems.find((item) => item.to !== '/' && pathname.startsWith(item.to));
  return closest?.label ?? 'Dashboard';
}

export function Header() {
  const location = useLocation();
  const { toggle } = useSidebarState();
  // FR-38.1: the "Report an issue" affordance lives in the header, so it's
  // reachable from every authenticated page without leaving the current one.
  const [reportOpen, setReportOpen] = useState(false);

  return (
    <AppBar component="header" position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.default' }}>
      <Toolbar sx={{ gap: 1.5, minHeight: '56px !important' }}>
        <IconButton onClick={toggle} aria-label="Toggle sidebar" edge="start" size="small">
          <PanelLeft size={18} />
        </IconButton>
        <Divider orientation="vertical" flexItem sx={{ height: 20, alignSelf: 'center' }} />
        <Breadcrumbs separator="›" sx={{ flexGrow: 1 }}>
          <Typography variant="body2" color="text.secondary">Vision Mapping</Typography>
          <Typography variant="body2" color="text.primary">{currentPageLabel(location.pathname)}</Typography>
        </Breadcrumbs>
        <Button
          onClick={() => setReportOpen(true)}
          size="small"
          color="inherit"
          startIcon={<MessageSquareWarning size={16} />}
          sx={{ textTransform: 'none' }}
        >
          Report an issue
        </Button>
        <AppearanceMenu />
      </Toolbar>
      {reportOpen && <ReportIssueModal onClose={() => setReportOpen(false)} />}
    </AppBar>
  );
}
