import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as oauthApi from '../api/oauthApi';
import { currentLocation, renderPage } from '../test/render';
import { OAuthGoogleCallbackPage, readGoogleFragment } from './OAuthGoogleCallbackPage';

vi.mock('../api/oauthApi');

const session = { token: 'access', refreshToken: 'refresh', userId: 7, fullName: 'Ana', email: 'ana@gmail.com', role: 'USER' };

/** Puts the server's answer after "#", as the real redirect does. */
function arriveWith(fragment: string) {
  window.history.replaceState(null, '', `/oauth/google/callback#${fragment}`);
}

/** V14 phase 4 (GA-FR-21): the page the server sends the browser to after Google answers. */
describe('OAuthGoogleCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(oauthApi.exchangeGoogleCode).mockResolvedValue(session as never);
  });
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('turns a sign-in code into a session, stored as "remember me" chose, then opens the page from before', async () => {
    arriveWith('result=signed-in&code=one-time&remember=false&from=%2Fdreams%3Ftab%3Dall');
    const { auth } = renderPage(<OAuthGoogleCallbackPage />, { route: '/oauth/google/callback' });

    expect(window.location.hash).toBe('');
    await waitFor(() => expect(auth.setSession).toHaveBeenCalledWith(session, false));
    expect(oauthApi.exchangeGoogleCode).toHaveBeenCalledTimes(1);
    expect(oauthApi.exchangeGoogleCode).toHaveBeenCalledWith('one-time');
    expect(currentLocation()).toBe('/dreams?tab=all');
  });

  it('opens the linking screen with the link code', async () => {
    arriveWith('result=link&code=link-code&remember=true&from=%2F');
    renderPage(<OAuthGoogleCallbackPage />, { route: '/oauth/google/callback' });

    await waitFor(() => expect(currentLocation()).toBe('/oauth/google/link'));
    expect(oauthApi.exchangeGoogleCode).not.toHaveBeenCalled();
    expect(window.location.hash).toBe('');
  });

  it('opens the error screen when Google sign-in was cancelled', async () => {
    arriveWith('result=error&reason=cancelled');
    renderPage(<OAuthGoogleCallbackPage />, { route: '/oauth/google/callback' });
    await waitFor(() => expect(currentLocation()).toBe('/oauth/google/error'));
    expect(oauthApi.exchangeGoogleCode).not.toHaveBeenCalled();
  });

  it('opens the error screen when the code has expired', async () => {
    vi.mocked(oauthApi.exchangeGoogleCode).mockRejectedValue(new Error('This sign-in link has expired. Please try again.'));
    arriveWith('result=signed-in&code=old&remember=true&from=%2F');
    const { auth } = renderPage(<OAuthGoogleCallbackPage />, { route: '/oauth/google/callback' });
    await waitFor(() => expect(currentLocation()).toBe('/oauth/google/error'));
    expect(auth.setSession).not.toHaveBeenCalled();
  });

  it('opens the error screen when it is reached without an answer', async () => {
    arriveWith('');
    renderPage(<OAuthGoogleCallbackPage />, { route: '/oauth/google/callback' });
    await waitFor(() => expect(currentLocation()).toBe('/oauth/google/error'));
    expect(screen.queryByText('access')).not.toBeInTheDocument();
  });

  it('reads the answer, keeping only in-app pages to return to', () => {
    expect(readGoogleFragment('#result=signed-in&code=c&remember=true&from=%2Ftasks')).toEqual({
      result: 'signed-in', code: 'c', reason: '', remember: true, from: '/tasks',
    });
    expect(readGoogleFragment('#result=signed-in&code=c&from=%2F%2Fevil.example').from).toBe('/');
    expect(readGoogleFragment('#result=signed-in&code=c&from=https%3A%2F%2Fevil.example').from).toBe('/');
    expect(readGoogleFragment('#result=signed-in&code=c&remember=false').remember).toBe(false);
  });
});
