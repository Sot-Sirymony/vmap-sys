import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/preferencesApi', () => ({
  getAppearancePreferences: vi.fn().mockResolvedValue({}),
  updateAppearancePreferences: vi.fn().mockResolvedValue({}),
}));

// Signed out, so the page is exercised without the network: the controls and
// preview are what this file is about.
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ token: null, appearance: null }),
}));

const { ThemeModeProvider } = await import('../context/ThemeModeContext');
const { AppearanceSettingsPage } = await import('./AppearanceSettingsPage');

function renderPage() {
  return render(
    <ThemeModeProvider>
      <AppearanceSettingsPage />
    </ThemeModeProvider>,
  );
}

describe('AppearanceSettingsPage (FR-39.5)', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-contrast');
    document.documentElement.removeAttribute('data-tone');
  });

  it('renders exactly one page heading', () => {
    renderPage();

    const headings = screen.getAllByRole('heading', { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent('Appearance');
  });

  it('offers every preset and all ten accents', () => {
    renderPage();

    expect(screen.getByText('Fluent System')).toBeInTheDocument();
    expect(screen.getByText('Midnight')).toBeInTheDocument();
    expect(screen.getByText('Ocean')).toBeInTheDocument();
    // The accents are swatches, so they're found by their accessible names.
    expect(screen.getAllByRole('button', { name: /accent$/ })).toHaveLength(10);
  });

  it('shows the live preview with real badge components', () => {
    renderPage();

    expect(screen.getByText('Preview')).toBeInTheDocument();
    // These are rendered by StatusBadge/PriorityBadge, so the preview cannot
    // drift from what the rest of the app shows.
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
    expect(screen.getByText('COMPLETED')).toBeInTheDocument();
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('applies a preset and reflects it back as selected', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByText('Midnight'));

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'));
  });

  it('turns high contrast on from the switch', async () => {
    const user = userEvent.setup();
    renderPage();

    // MUI's Switch declares role="switch", not checkbox.
    await user.click(screen.getByRole('switch', { name: /high contrast/i }));

    await waitFor(() => expect(document.documentElement.dataset.contrast).toBe('high'));
  });

  /** FR-39.1: adjusting one control must stop claiming to be the preset. */
  it('explains the Custom state when settings match no preset', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Pink accent' }));

    expect(await screen.findByText(/don't match a preset/i)).toBeInTheDocument();
  });

  it('offers the six background tones', () => {
    renderPage();

    expect(screen.getAllByRole('button', { name: /background$/ })).toHaveLength(6);
    expect(screen.getByRole('button', { name: 'Warm background' })).toBeInTheDocument();
  });

  it('applies a background tone', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Warm background' }));

    await waitFor(() => expect(document.documentElement.dataset.tone).toBe('warm'));
  });

  /**
   * FR-40.5: the control is disabled rather than left live and ignored. A user
   * who could click Warm, see nothing change, and get no explanation would
   * reasonably conclude the feature is broken.
   */
  it('disables the tone control while high contrast is on, and says why', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('switch', { name: /high contrast/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Warm background' })).toBeDisabled();
    });
    expect(screen.getByText(/unavailable while high contrast is on/i)).toBeInTheDocument();
  });

  it('resets back to the defaults', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Brass accent' }));
    await user.click(screen.getByRole('button', { name: /reset to defaults/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Blue accent' })).toHaveAttribute('aria-pressed', 'true');
    });
  });
});
