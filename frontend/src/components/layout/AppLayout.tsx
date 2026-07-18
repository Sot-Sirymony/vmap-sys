import { Outlet } from 'react-router-dom';
import Box from '@mui/material/Box';
import { GlobalShortcuts } from './GlobalShortcuts';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { SidebarStateProvider } from './sidebar-context';

export function AppLayout() {
  return (
    <SidebarStateProvider>
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
    </SidebarStateProvider>
  );
}
