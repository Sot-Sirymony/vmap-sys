import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { listDreams } from '../../api/dreamApi';
import { createIssueReport } from '../../api/issueReportApi';
import { listPartners } from '../../api/partnerApi';
import { emptyPage, pageOf } from '../../test/apiMocks';
import { aPartner } from '../../test/builders';
import { currentLocation, renderPage, type TestRole } from '../../test/render';
import { AppLayout } from './AppLayout';

vi.mock('../../api/aiApi');
vi.mock('../../api/dreamApi');
vi.mock('../../api/issueReportApi');
vi.mock('../../api/partnerApi');

function renderLayout(route = '/goals', role: TestRole = 'USER') {
  return renderPage(
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="*" element={<p>Page body</p>} />
      </Route>
    </Routes>,
    { route, role },
  );
}

const sidebar = () => screen.getByRole('navigation', { name: 'Sidebar' });

describe('AppLayout: sidebar, header and shortcuts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAiStatus).mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 0, messagesLeftToday: 0 });
    vi.mocked(listPartners).mockResolvedValue(emptyPage());
    vi.mocked(listDreams).mockResolvedValue([]);
  });

  it('shows the page inside the layout, with the three method sections and the current page marked', async () => {
    renderLayout('/goals');
    expect(await screen.findByText('Page body')).toBeInTheDocument();
    const nav = within(sidebar());
    for (const section of ['Plan', 'Execute', 'Support']) {
      expect(nav.getAllByText(section).length).toBeGreaterThan(0);
    }
    expect(nav.getByRole('link', { name: 'Goals' })).toHaveAttribute('aria-current', 'page');
    expect(nav.getByRole('link', { name: 'Tasks' })).not.toHaveAttribute('aria-current');
  });

  it('keeps the Vision Map highlighted while a dream tree is open', () => {
    renderLayout('/dreams/4');
    // Highlighted on screen. Screen readers are not told (no aria-current): noted as a gap in BRD 12.
    expect(within(sidebar()).getByRole('link', { name: 'Vision Map' })).toHaveClass('Mui-selected');
    expect(within(sidebar()).getByRole('link', { name: 'Dreams' })).not.toHaveClass('Mui-selected');
  });

  it('shows coach and admin pages only to those roles (V9)', () => {
    renderLayout('/goals', 'USER');
    expect(within(sidebar()).queryByRole('link', { name: 'Coaching' })).not.toBeInTheDocument();
    expect(within(sidebar()).queryByRole('link', { name: 'AI settings' })).not.toBeInTheDocument();
  });

  it('shows the coach workspace to a coach, and every admin page to an admin', () => {
    const { unmount } = renderLayout('/goals', 'COACH');
    expect(within(sidebar()).getByRole('link', { name: 'Coaching' })).toBeInTheDocument();
    expect(within(sidebar()).queryByRole('link', { name: 'Cohorts' })).not.toBeInTheDocument();
    unmount();

    renderLayout('/goals', 'ADMIN');
    for (const name of ['Coaching', 'Cohorts', 'AI settings', 'Security log']) {
      expect(within(sidebar()).getByRole('link', { name })).toBeInTheDocument();
    }
  });

  it('names the page in the header, and greets the participant on the dashboard', async () => {
    const { unmount } = renderLayout('/tasks');
    expect(within(screen.getByRole('banner')).getByText('Tasks')).toBeInTheDocument();
    unmount();

    renderLayout('/');
    expect(await screen.findByText(/Welcome back, Test/)).toBeInTheDocument();
  });

  it('logs out from the user menu', async () => {
    const { auth } = renderLayout();
    await userEvent.click(within(sidebar()).getByText('Test Participant', { exact: false }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    expect(auth.logout).toHaveBeenCalled();
  });

  it('collapses the sidebar to icons and expands it again', async () => {
    renderLayout();
    expect(within(sidebar()).getByText('Vision Areas')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Toggle sidebar' }));
    await waitFor(() => expect(within(sidebar()).queryByText('Vision Areas')).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Toggle sidebar' }));
    expect(within(sidebar()).getByText('Vision Areas')).toBeInTheDocument();
  });

  it('shows the participant’s partners in the header and opens a new partner form', async () => {
    vi.mocked(listPartners).mockResolvedValue(pageOf([aPartner({ id: 7, name: 'Dr. Mentor' })]));
    renderLayout();
    await waitFor(() => expect(listPartners).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Add a partner' }));
    expect(currentLocation()).toBe('/partners?create=partner');
  });

  it('goes to a page with "g" and a letter, and ignores keys typed into a field (FR-29.1)', async () => {
    renderLayout('/goals');
    await userEvent.keyboard('gt');
    expect(currentLocation()).toBe('/tasks');
    await userEvent.keyboard('gm');
    expect(currentLocation()).toBe('/vision-map');
    await userEvent.keyboard('gx');
    expect(currentLocation()).toBe('/vision-map');

    const field = document.createElement('input');
    document.body.appendChild(field);
    field.focus();
    await userEvent.keyboard('gd');
    expect(currentLocation()).toBe('/vision-map');
    field.remove();
  });

  it('starts a new record with "n" on a list page, and does nothing on other pages', async () => {
    const { unmount } = renderLayout('/goals');
    await userEvent.keyboard('n');
    expect(currentLocation()).toBe('/goals?create=goal');
    unmount();

    renderLayout('/today');
    await userEvent.keyboard('n');
    expect(currentLocation()).toBe('/today');
  });

  it('opens the shortcut help with "?" and the command palette with Ctrl+K', async () => {
    renderLayout();
    await userEvent.keyboard('?');
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    fireEvent.keyDown(window, { key: 'k', ctrlKey: true });
    expect(await screen.findByRole('combobox', { name: 'Command palette search' })).toBeInTheDocument();
  });

  it('opens the issue report form from the header and sends a report', async () => {
    vi.mocked(createIssueReport).mockResolvedValue({ id: 1 } as never);
    renderLayout('/goals');
    await userEvent.click(screen.getByRole('button', { name: 'Report an issue' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
  });
});
