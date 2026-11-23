import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as securityAccessApi from '../api/securityAccessApi';
import { resetOfferedSignInMethods } from '../hooks/useOfferedSignInMethods';
import { renderPage } from '../test/render';
import { RegisterPage } from './RegisterPage';

vi.mock('../api/authApi');
vi.mock('../api/securityAccessApi');

describe('RegisterPage (V24 SA-FR-11)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetOfferedSignInMethods();
  });

  it('offers the password form and Google when both are on', async () => {
    vi.mocked(securityAccessApi.getOfferedSignInMethods).mockResolvedValue({ password: true, google: true });
    renderPage(<RegisterPage />, { route: '/register', auth: { token: null, user: null } });
    expect(await screen.findByRole('button', { name: /Google/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Full name')).toBeInTheDocument();
    expect(screen.getByText('or')).toBeInTheDocument();
  });

  it('offers Google only when password sign-in is off', async () => {
    vi.mocked(securityAccessApi.getOfferedSignInMethods).mockResolvedValue({ password: false, google: true });
    renderPage(<RegisterPage />, { route: '/register', auth: { token: null, user: null } });
    expect(await screen.findByText('Sign-up with a password is turned off. Sign up with Google.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Google/ })).toBeInTheDocument();
    expect(screen.queryByLabelText('Full name')).not.toBeInTheDocument();
  });
});
