import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const getAppearancePreferences = vi.fn();
const updateAppearancePreferences = vi.fn();

vi.mock('../api/preferencesApi', () => ({
  getAppearancePreferences: (...args: unknown[]) => getAppearancePreferences(...args),
  updateAppearancePreferences: (...args: unknown[]) => updateAppearancePreferences(...args),
}));

const authState: { token: string | null; appearance: unknown } = { token: null, appearance: null };
vi.mock('./AuthContext', () => ({
  useAuth: () => authState,
}));

const { ThemeModeProvider, useThemeSettings } = await import('./ThemeModeContext');

function Probe() {
  const { settings, preset, update, applyPreset } = useThemeSettings();
  return (
    <div>
      <output data-testid="state">{`${preset}|${settings.mode}|${settings.accent}|${settings.highContrast}|${settings.reduceMotion}`}</output>
      <button type="button" onClick={() => update({ accent: 'brass' })}>brass</button>
      <button type="button" onClick={() => update({ highContrast: true })}>contrast</button>
      <button type="button" onClick={() => update({ reduceMotion: true })}>motion</button>
      <button type="button" onClick={() => update({ backgroundTone: 'warm' })}>warm</button>
      <button type="button" onClick={() => update({ backgroundTone: 'neutral' })}>neutraltone</button>
      <button type="button" onClick={() => update({ interfaceStyle: 'modern' })}>modern</button>
      <button type="button" onClick={() => update({ interfaceStyle: 'classic' })}>classic</button>
      <button type="button" onClick={() => applyPreset('dark', 'purple')}>midnight</button>
    </div>
  );
}

const STORED = {
  themePreset: 'OCEAN',
  themeMode: 'LIGHT',
  themeAccent: 'TEAL',
  uiDensity: 'COMFORTABLE',
  fontSize: 'MEDIUM',
  backgroundTone: 'NEUTRAL',
  fontFamily: 'SYSTEM',
  interfaceStyle: 'CLASSIC',
  highContrast: false,
  reduceMotion: false,
};

function state() {
  return screen.getByTestId('state').textContent;
}

describe('ThemeModeProvider (FR-39)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    localStorage.clear();
    authState.token = null;
    authState.appearance = null;
    getAppearancePreferences.mockReset().mockResolvedValue(STORED);
    updateAppearancePreferences.mockReset().mockResolvedValue(STORED);
    document.documentElement.removeAttribute('data-contrast');
    document.documentElement.removeAttribute('data-motion');
    document.documentElement.removeAttribute('data-tone');
    document.documentElement.removeAttribute('data-style');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('stamps the accessibility attributes only while they are on', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    // Absent, not "false" — global.css keys off presence, so an always-present
    // attribute would make the selectors need a value check everywhere.
    expect(document.documentElement.hasAttribute('data-contrast')).toBe(false);
    expect(document.documentElement.hasAttribute('data-motion')).toBe(false);

    await user.click(screen.getByText('contrast'));
    await user.click(screen.getByText('motion'));

    expect(document.documentElement.dataset.contrast).toBe('high');
    expect(document.documentElement.dataset.motion).toBe('reduced');
  });

  /**
   * FR-40: the attribute is absent on Neutral, so the default costs no selector
   * work and its absence unambiguously means "the surfaces that shipped before".
   */
  it('stamps data-tone only when the tone is not Neutral', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    expect(document.documentElement.hasAttribute('data-tone')).toBe(false);

    await user.click(screen.getByText('warm'));
    expect(document.documentElement.dataset.tone).toBe('warm');

    await user.click(screen.getByText('neutraltone'));
    expect(document.documentElement.hasAttribute('data-tone')).toBe(false);
  });

  /**
   * FR-48: same convention as the tone — absent means Classic, the shape that
   * shipped before the control existed.
   */
  it('stamps data-style only when the style is not Classic', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    expect(document.documentElement.hasAttribute('data-style')).toBe(false);

    await user.click(screen.getByText('modern'));
    expect(document.documentElement.dataset.style).toBe('modern');

    await user.click(screen.getByText('classic'));
    expect(document.documentElement.hasAttribute('data-style')).toBe(false);
  });

  /**
   * FR-48: unlike the tone, the style is NOT suppressed by high contrast. That
   * setting exists for legibility, and a corner radius cannot make anything
   * harder to read, so there is no reason to take the chosen shape away.
   */
  it('keeps the style applied while high contrast is on', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('modern'));
    await user.click(screen.getByText('contrast'));

    expect(document.documentElement.dataset.style).toBe('modern');
    expect(document.documentElement.dataset.contrast).toBe('high');
  });

  /** FR-48: the style decides no colour, so it must not disturb the preset. */
  it('leaves the preset alone when only the style changes', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('midnight'));
    expect(state()).toBe('MIDNIGHT|dark|purple|false|false');

    await user.click(screen.getByText('modern'));
    expect(state()).toBe('MIDNIGHT|dark|purple|false|false');
  });

  /** FR-40.4: tone is its own axis — changing it must not disturb the preset. */
  it('leaves the preset alone when only the tone changes', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('midnight'));
    expect(state()).toBe('MIDNIGHT|dark|purple|false|false');

    await user.click(screen.getByText('warm'));
    expect(state()).toBe('MIDNIGHT|dark|purple|false|false');
  });

  it('applies a preset as a single change and names it', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('midnight'));

    expect(state()).toBe('MIDNIGHT|dark|purple|false|false');
  });

  it('reports CUSTOM once an individual control is changed', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('midnight'));
    await user.click(screen.getByText('brass'));

    expect(state()).toBe('CUSTOM|dark|brass|false|false');
  });

  it('does not touch the account while nobody is signed in', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('brass'));
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(updateAppearancePreferences).not.toHaveBeenCalled();
    // It still persists locally, so the login screen keeps the chosen look.
    expect(localStorage.getItem('vms-theme-settings')).toContain('brass');
  });

  it('adopts the appearance that came with the session', async () => {
    authState.token = 'jwt';
    authState.appearance = STORED;
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await waitFor(() => expect(state()).toBe('OCEAN|light|teal|false|false'));
    // It arrived with the login response, so no extra request is needed.
    expect(getAppearancePreferences).not.toHaveBeenCalled();
  });

  it('fetches the appearance when the session was restored without it', async () => {
    authState.token = 'jwt';
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await waitFor(() => expect(getAppearancePreferences).toHaveBeenCalledWith('jwt'));
    await waitFor(() => expect(state()).toBe('OCEAN|light|teal|false|false'));
  });

  it('does not echo the loaded values straight back to the server', async () => {
    authState.token = 'jwt';
    authState.appearance = STORED;
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await waitFor(() => expect(state()).toBe('OCEAN|light|teal|false|false'));
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(updateAppearancePreferences).not.toHaveBeenCalled();
  });

  it('saves a change to the account, debounced into one request', async () => {
    authState.token = 'jwt';
    authState.appearance = STORED;
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);
    await waitFor(() => expect(state()).toBe('OCEAN|light|teal|false|false'));

    await user.click(screen.getByText('brass'));
    await user.click(screen.getByText('contrast'));
    await user.click(screen.getByText('motion'));
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(updateAppearancePreferences).toHaveBeenCalledTimes(1);
    expect(updateAppearancePreferences).toHaveBeenCalledWith('jwt', expect.objectContaining({
      themeAccent: 'BRASS',
      highContrast: true,
      reduceMotion: true,
    }));
  });

  /**
   * BR-33: a failed save must not roll the choice back. Appearance is not
   * allowed to become a feature that only works when the backend does.
   */
  it('keeps the choice applied when saving to the account fails', async () => {
    authState.token = 'jwt';
    authState.appearance = STORED;
    updateAppearancePreferences.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);
    await waitFor(() => expect(state()).toBe('OCEAN|light|teal|false|false'));

    await user.click(screen.getByText('brass'));
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(state()).toContain('brass');
    expect(localStorage.getItem('vms-theme-settings')).toContain('brass');
  });

  /**
   * The race worth guarding: a restored session fetches the stored appearance,
   * and a choice made while that request is in flight must not be undone by the
   * response arriving second.
   */
  it('does not let an in-flight load overwrite a change the user just made', async () => {
    authState.token = 'jwt';
    let resolveLoad: (value: unknown) => void = () => undefined;
    getAppearancePreferences.mockReturnValue(new Promise((resolve) => { resolveLoad = resolve; }));

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ThemeModeProvider><Probe /></ThemeModeProvider>);

    await user.click(screen.getByText('brass'));
    expect(state()).toContain('brass');

    await act(async () => {
      resolveLoad(STORED);
    });

    // The user's intent is newer than the server's answer.
    expect(state()).toContain('brass');
  });
});
