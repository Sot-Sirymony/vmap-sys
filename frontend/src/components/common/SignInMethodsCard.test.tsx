import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { SignInMethods } from '../../api/oauthApi';
import { SignInMethodsCard } from './SignInMethodsCard';

function renderCard(methods: SignInMethods | null) {
  return render(<MemoryRouter><SignInMethodsCard methods={methods} /></MemoryRouter>);
}

/** V14 phase 4 (GA-FR-27, D-14-3): the Account page's sign-in methods. */
describe('SignInMethodsCard', () => {
  it('shows the linked Google account and a way to set a first password', () => {
    renderCard({
      passwordSet: false,
      linkedAccounts: [{ provider: 'GOOGLE', email: 'pat@gmail.com', linkedAt: '2026-11-21T10:00:00Z' }],
    });
    expect(screen.getByText('Google: connected as pat@gmail.com')).toBeInTheDocument();
    expect(screen.getByText('Password: not set yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Set a password' })).toHaveAttribute('href', '/settings/security');
  });

  it('stays hidden for an account that never used Google, or before the answer arrives', () => {
    expect(renderCard({ passwordSet: true, linkedAccounts: [] }).container).toBeEmptyDOMElement();
    expect(renderCard(null).container).toBeEmptyDOMElement();
  });
});
