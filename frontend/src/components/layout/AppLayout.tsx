import { Outlet } from 'react-router';
import Box from '@mui/material/Box';
import { BottomNav } from './BottomNav';
import { GlobalShortcuts } from './GlobalShortcuts';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { SidebarStateProvider } from './sidebar-context';
import { CommandPaletteProvider } from './command-palette-context';

export function AppLayout() {
  return (
    <SidebarStateProvider>
      {/* FR-48.3: above both the shortcut listener and the sidebar, because in
          the Modern style each of them can open the palette. */}
      <CommandPaletteProvider>
        <GlobalShortcuts />
        <Box sx={{ display: 'flex', minHeight: '100vh' }}>
          <Sidebar />
          <Box sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
            <Header />
            <Box component="main" className="page-shell" sx={{ flexGrow: 1 }}>
              <Outlet />
            </Box>
          </Box>
        </Box>
        <BottomNav />
      </CommandPaletteProvider>
    </SidebarStateProvider>
  );
}
