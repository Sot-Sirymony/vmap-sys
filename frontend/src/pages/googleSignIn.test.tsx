import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as oauthApi from '../api/oauthApi';
import { setCurrentLanguage } from '../i18n/translate';
import { currentLocation, renderPage } from '../test/render';
import { AccountLinkingPage } from './AccountLinkingPage';
import { OAuthGoogleErrorPage } from './OAuthGoogleErrorPage';
import { OAuthGoogleStartPage } from './OAuthGoogleStartPage';

vi.mock('../api/oauthApi');

/** BRD 12 COV-FR-24: Google sign-in and account linking (V10). */
describe('Google sign-in (V10)', () => {
  const assign = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('location', { ...window.location, assign });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    setCurrentLanguage('en');
  });

  describe('OAuthGoogleStartPage', () => {
    it('shows that sign-in is under way, then sends the browser to Google', async () => {
      vi.mocked(oauthApi.getGoogleAuthorizeUrl).mockResolvedValue({ url: 'https://accounts.google.com/o/oauth2/auth?state=abc' } as never);
      renderPage(<OAuthGoogleStartPage />, { route: '/oauth/google' });
      expect(screen.getByText('Signing you in with Google…').closest('[role="status"]')).toBeInTheDocument();
      await waitFor(() => expect(assign).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/auth?state=abc'));
    });

    it('goes to the error screen with the reason when Google sign-in cannot start', async () => {
      vi.mocked(oauthApi.getGoogleAuthorizeUrl).mockRejectedValue(new Error('Google sign-in is not set up on this server.'));
      renderPage(<OAuthGoogleStartPage />, { route: '/oauth/google' });
      await waitFor(() => expect(currentLocation()).toBe('/oauth/google/error'));
      expect(assign).not.toHaveBeenCalled();
    });
  });

  describe('OAuthGoogleErrorPage', () => {
    it('explains the failure, with the specific reason when there is one', () => {
      renderPage(<OAuthGoogleErrorPage />, { route: '/oauth/google/error', state: { message: 'The sign-in link has expired.' } });
      expect(screen.getByRole('heading', { name: 'We couldn\'t sign you in with Google' })).toBeInTheDocument();
      expect(screen.getByText('The sign-in link has expired.')).toBeInTheDocument();
    });

    it('shows only the general explanation when no reason was passed', () => {
      renderPage(<OAuthGoogleErrorPage />, { route: '/oauth/google/error' });
      expect(screen.getByText(/There was a problem authenticating your account/)).toBeInTheDocument();
      expect(document.querySelector('.auth-status-note')).toBeNull();
    });

    it('offers to try Google again or to use email instead', async () => {
      renderPage(<OAuthGoogleErrorPage />, { route: '/oauth/google/error' });
      expect(screen.getByRole('link', { name: 'Sign in with email instead' })).toHaveAttribute('href', '/login');
      await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
      expect(currentLocation()).toBe('/oauth/google');
    });

    it('shows the error screen in Khmer (COV-NFR-08)', () => {
      setCurrentLanguage('km');
      renderPage(<OAuthGoogleErrorPage />, { route: '/oauth/google/error' });
      expect(screen.getByRole('button', { name: 'ព្យាយាមម្តងទៀត' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'ចូលដោយអ៊ីមែលជំនួសវិញ' })).toBeInTheDocument();
    });
  });

  describe('AccountLinkingPage', () => {
    it('says the page is only reached from a Google sign-in when there is no link token', () => {
      renderPage(<AccountLinkingPage />, { route: '/oauth/google/link' });
      expect(screen.getByText(/only reachable from a Google sign-in/)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Go to sign in' })).toHaveAttribute('href', '/login');
      expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    });

    it('links the accounts with the password, opens a session and goes home', async () => {
      const session = { token: 'new-token', user: { id: 1 } };
      vi.mocked(oauthApi.linkGoogleAccount).mockResolvedValue(session as never);
      const { auth } = renderPage(<AccountLinkingPage />, { route: '/oauth/google/link', state: { linkToken: 'link-123' } });
      await userEvent.type(screen.getByLabelText('Password'), 'my-password');
      await userEvent.click(screen.getByRole('button', { name: 'Link accounts' }));

      expect(oauthApi.linkGoogleAccount).toHaveBeenCalledWith({ linkToken: 'link-123', password: 'my-password' });
      await waitFor(() => expect(auth.setSession).toHaveBeenCalledWith(session, true));
      expect(currentLocation()).toBe('/');
    });

    it('shows why linking failed and keeps the person on the page', async () => {
      vi.mocked(oauthApi.linkGoogleAccount).mockRejectedValue(new Error('Incorrect password.'));
      const { auth } = renderPage(<AccountLinkingPage />, { route: '/oauth/google/link', state: { linkToken: 'link-123' } });
      await userEvent.type(screen.getByLabelText('Password'), 'wrong');
      await userEvent.click(screen.getByRole('button', { name: 'Link accounts' }));
      expect(await screen.findByText('Incorrect password.')).toBeInTheDocument();
      expect(auth.setSession).not.toHaveBeenCalled();
      expect(currentLocation()).toBe('/oauth/google/link');
      expect(screen.getByRole('link', { name: 'Cancel' })).toHaveAttribute('href', '/login');
    });
  });
});
