import { waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as oauthApi from '../api/oauthApi';
import { currentLocation, renderPage } from '../test/render';
import { OAuthGoogleStartPage } from './OAuthGoogleStartPage';

vi.mock('../api/oauthApi');

/** V14 phase 4 (GA-FR-22): "remember me" and the page to return to go to the server with the attempt. */
describe('OAuthGoogleStartPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('passes the Sign In page choices to the server', async () => {
    vi.mocked(oauthApi.getGoogleAuthorizeUrl).mockReturnValue(new Promise(() => undefined));
    renderPage(<OAuthGoogleStartPage />, { route: '/oauth/google', state: { from: '/tasks', remember: false } });
    await waitFor(() => expect(oauthApi.getGoogleAuthorizeUrl).toHaveBeenCalledWith(false, '/tasks'));
  });

  it('remembers the session and returns home when opened directly', async () => {
    vi.mocked(oauthApi.getGoogleAuthorizeUrl).mockReturnValue(new Promise(() => undefined));
    renderPage(<OAuthGoogleStartPage />, { route: '/oauth/google' });
    await waitFor(() => expect(oauthApi.getGoogleAuthorizeUrl).toHaveBeenCalledWith(true, '/'));
  });

  it('opens the error screen when Google sign-in is not set up', async () => {
    vi.mocked(oauthApi.getGoogleAuthorizeUrl).mockRejectedValue(
      new Error('Google sign-in is not set up yet. Please use your email and password.'));
    renderPage(<OAuthGoogleStartPage />, { route: '/oauth/google' });
    await waitFor(() => expect(currentLocation()).toBe('/oauth/google/error'));
  });
});
