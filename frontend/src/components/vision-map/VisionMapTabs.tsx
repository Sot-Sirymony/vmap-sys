import Box from '@mui/material/Box';
import { Link, useLocation } from 'react-router';
import { tr } from '../../i18n/translate';
import { activeNavTo, visionMapTabs } from '../layout/nav-items';

/**
 * V37 (D-37-3): one tab bar for the Vision Map pages: Map, Timeline,
 * Management Report and Saved reports. Each tab is a link to its own page,
 * so routes and bookmarks are unchanged. A dream tree (/dreams/{id}) and a
 * saved report (/vision-map/reports/{id}) mark the tab they belong to.
 */
export function VisionMapTabs() {
  const { pathname } = useLocation();
  const path = /^\/dreams\/\d+/.test(pathname) ? '/vision-map' : pathname;
  const current = activeNavTo(path, visionMapTabs);
  return (
    <Box component="nav" aria-label={tr('Vision Map')} className="vision-map-tabs"
      sx={{ display: 'flex', gap: 0.5, borderBottom: 1, borderColor: 'divider', mb: 2, overflowX: 'auto', flexWrap: 'nowrap' }}>
      {visionMapTabs.map((tab) => {
        const selected = tab.to === current;
        return (
          <Box key={tab.to} component={Link} to={tab.to} aria-current={selected ? 'page' : undefined}
            sx={{
              display: 'inline-flex', alignItems: 'center', gap: 0.75, px: 1.5, py: 1, whiteSpace: 'nowrap',
              textDecoration: 'none', fontSize: 'var(--font-body-sm)', color: selected ? 'primary.main' : 'text.secondary',
              fontWeight: selected ? 600 : 400, borderBottom: 2, borderColor: selected ? 'primary.main' : 'transparent', mb: '-1px',
              '&:hover': { color: 'primary.main' },
              '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 },
            }}>
            <tab.icon size={16} aria-hidden />
            {tr(tab.label)}
          </Box>
        );
      })}
    </Box>
  );
}
