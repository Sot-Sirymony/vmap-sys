import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { backgroundTones } from '../theme';
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
    document.documentElement.removeAttribute('data-style');
  });

  it('renders exactly one page heading', () => {
    renderPage();

    const headings = screen.getAllByRole('heading', { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent('Appearance');
  });

  it('offers every preset and all twenty-three accents', () => {
    renderPage();

    expect(screen.getByText('Fluent System')).toBeInTheDocument();
    expect(screen.getByText('Midnight')).toBeInTheDocument();
    expect(screen.getByText('Ocean')).toBeInTheDocument();
    expect(screen.getByText('Stitch')).toBeInTheDocument();
    // The accents are swatches, so they're found by their accessible names.
    expect(screen.getAllByRole('button', { name: /^\w+ accent$/ })).toHaveLength(23);
    expect(screen.getByRole('button', { name: 'Vermilion accent' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Violet accent' })).toBeInTheDocument();
  });

  /**
   * BRD 26 §7 (IS-FR-10): three style cards with live thumbnails, radio
   * semantics and arrow keys.
   */
  it('offers the three interface styles as a radio group and applies the one picked', async () => {
    const user = userEvent.setup();
    renderPage();

    const group = screen.getByRole('radiogroup', { name: 'Interface style' });
    expect(group).toBeInTheDocument();
    const modern = screen.getByRole('radio', { name: /^Modern/ });
    const classic = screen.getByRole('radio', { name: /^Classic/ });
    expect(screen.getByRole('radio', { name: /^Liquid Glass/ })).toBeInTheDocument();
    expect(modern).toHaveAttribute('aria-checked', 'true');
    expect(document.documentElement.dataset.style).toBe('modern');
    expect(screen.getByText('Calm and spacious. The recommended look.')).toBeInTheDocument();
    expect(screen.getByText('Pair with Compact density for the densest view.')).toBeInTheDocument();

    await user.click(classic);
    expect(classic).toHaveAttribute('aria-checked', 'true');
    expect(document.documentElement.dataset.style).toBe('classic');

    // Arrow keys move the selection (roving focus).
    classic.focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('radio', { name: /^Liquid Glass/ })).toHaveAttribute('aria-checked', 'true');
    expect(document.documentElement.dataset.style).toBe('glass');
  });

  it('says why Liquid Glass is solid under High contrast (ISC-03)', async () => {
    localStorage.setItem('vms-theme-settings', JSON.stringify({ interfaceStyle: 'glass', highContrast: true }));
    renderPage();
    expect(await screen.findByTestId('glass-fallback-note')).toHaveTextContent('High contrast is on, so panels stay solid.');
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

  it('offers the sixteen background tones', () => {
    renderPage();

    expect(backgroundTones).toHaveLength(16);
    backgroundTones.forEach((tone) => {
      expect(screen.getAllByRole('button', { name: new RegExp(`^${tone.label}`) }).length).toBeGreaterThan(0);
    });
    expect(screen.getByRole('button', { name: /^Warm/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Lavender/ })).toBeInTheDocument();
  });

  it('applies a background tone', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: /^Warm/ }));

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
      expect(screen.getByRole('button', { name: /^Warm/ })).toBeDisabled();
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
