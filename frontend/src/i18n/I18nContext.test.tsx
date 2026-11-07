import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getUiTextOverrides } from '../api/uiTextApi';
import { I18nProvider, useI18n } from './I18nContext';
import { LANGUAGE_STORAGE_KEY, setCurrentLanguage, setOverrides } from './translate';

vi.mock('../api/uiTextApi');

function Probe() {
  const { language, setLanguage, t } = useI18n();
  return (
    <>
      <p>Language: {language}</p>
      <p>{t('Dashboard')}</p>
      <p>{t('Today')}</p>
      <button type="button" onClick={() => setLanguage('km')}>Khmer</button>
      <button type="button" onClick={() => setLanguage('en')}>English</button>
    </>
  );
}

describe('I18nProvider (NFR-03)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    setCurrentLanguage('en');
    setOverrides({});
  });

  afterEach(() => {
    setCurrentLanguage('en');
    setOverrides({});
  });

  it('starts in English and sets the page language', async () => {
    vi.mocked(getUiTextOverrides).mockResolvedValue({});
    render(<I18nProvider><Probe /></I18nProvider>);
    expect(screen.getByText('Language: en')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
    await waitFor(() => expect(getUiTextOverrides).toHaveBeenCalledWith('en'));
  });

  it('switches to Khmer, remembers the choice, and loads the Khmer admin changes', async () => {
    vi.mocked(getUiTextOverrides).mockResolvedValue({});
    render(<I18nProvider><Probe /></I18nProvider>);
    await userEvent.click(screen.getByRole('button', { name: 'Khmer' }));
    expect(screen.getByText('Language: km')).toBeInTheDocument();
    expect(screen.getByText('ផ្ទាំងគ្រប់គ្រង')).toBeInTheDocument();
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('km');
    expect(document.documentElement.lang).toBe('km');
    await waitFor(() => expect(getUiTextOverrides).toHaveBeenCalledWith('km'));
  });

  it('shows an admin’s change to a piece of interface text once it loads', async () => {
    vi.mocked(getUiTextOverrides).mockResolvedValue({ Today: 'My day' });
    render(<I18nProvider><Probe /></I18nProvider>);
    expect(await screen.findByText('My day')).toBeInTheDocument();
  });

  it('does nothing when the same language is chosen, and keeps working if admin changes cannot load', async () => {
    vi.mocked(getUiTextOverrides).mockRejectedValue(new Error('offline'));
    render(<I18nProvider><Probe /></I18nProvider>);
    await userEvent.click(screen.getByRole('button', { name: 'English' }));
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBeNull();
    expect(screen.getByText('Today')).toBeInTheDocument();
  });

  it('gives English text outside a provider', () => {
    render(<Probe />);
    expect(screen.getByText('Language: en')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });
});
