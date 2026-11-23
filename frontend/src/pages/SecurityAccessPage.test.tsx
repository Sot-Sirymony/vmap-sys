import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderPage } from '../test/render';
import { SecurityAccessPage } from './SecurityAccessPage';

const admin = { auth: { user: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'ADMIN', permissions: ['USE_OWN_PLAN', 'MANAGE_ROLES'] } } };
const participant = { auth: { user: { id: 2, fullName: 'P', email: 'p@example.com', role: 'USER', permissions: ['USE_OWN_PLAN'] } } };

const toggle = (name: RegExp) => screen.getByRole('switch', { name });

describe('SecurityAccessPage (BRD V24.0.0.0 prototype)', () => {
  it('FR-01: refuses anyone without Manage roles', () => {
    renderPage(<SecurityAccessPage />, participant);
    expect(screen.getByText('Only an admin can open this page.')).toBeInTheDocument();
    expect(screen.queryAllByRole('switch')).toHaveLength(0);
  });

  it('FR-02, FR-03, FR-07: lists all 57 items in 7 groups with totals and a prototype notice', () => {
    renderPage(<SecurityAccessPage />, admin);
    expect(screen.getAllByRole('switch')).toHaveLength(57);
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(7);
    expect(screen.getByText('Draft prototype')).toBeInTheDocument();
    expect(screen.getByText('7 of 57')).toBeInTheDocument();
    expect(screen.getByText(/Nothing is saved to the server/)).toBeInTheDocument();
    expect(toggle(/^Password-based: Allowed/)).toBeChecked();
    expect(toggle(/^SAML 2\.0: Blocked/)).not.toBeChecked();
  });

  it('FR-04, FR-11, FR-12: a switch makes an unsaved change that Discard reverts', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(toggle(/^SAML 2\.0/));
    const bar = screen.getByRole('region', { name: 'Unsaved changes' });
    expect(within(bar).getByText('1 unsaved change(s)')).toBeInTheDocument();
    expect(screen.getByText('Unsaved')).toBeInTheDocument();
    await user.click(within(bar).getByRole('button', { name: 'Discard' }));
    expect(toggle(/^SAML 2\.0/)).not.toBeChecked();
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).not.toBeInTheDocument();
  });

  it('FR-13: Save shows what will be allowed and blocked, then keeps it', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(toggle(/^Social login/));
    await user.click(toggle(/^Least privilege/));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('Will be allowed (1)')).toBeInTheDocument();
    expect(within(dialog).getByText(/AN-S03 · Social login/)).toBeInTheDocument();
    expect(within(dialog).getByText('Will be blocked (1)')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Confirm and save' }));
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).not.toBeInTheDocument();
    expect(toggle(/^Social login/)).toBeChecked();
  });

  it('BR-02: Block all keeps Password-based allowed and says why', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(screen.getByRole('button', { name: 'Block all' }));
    expect(screen.getByText(/Password-based stays allowed/)).toBeInTheDocument();
    expect(toggle(/^Password-based/)).toBeChecked();
    expect(screen.getByText('1 of 57')).toBeInTheDocument();
  });

  it('BR-03: allowing a deprecated item asks first', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(toggle(/^OAuth 2\.0 Implicit/));
    await user.click(screen.getByRole('button', { name: 'Keep blocked' }));
    expect(toggle(/^OAuth 2\.0 Implicit/)).not.toBeChecked();
    await user.click(toggle(/^OAuth 2\.0 Implicit/));
    await user.click(screen.getByRole('button', { name: 'Allow anyway' }));
    expect(toggle(/^OAuth 2\.0 Implicit/)).toBeChecked();
  });

  it('BR-04: warns when MFA is allowed with fewer than two factors', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(toggle(/^Something you have/));
    expect(screen.getByText(/fewer than two "By factor" items/)).toBeInTheDocument();
  });

  it('FR-08, FR-09, FR-10: search, filters and collapsing narrow the list', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.type(screen.getByLabelText('Search'), 'kerberos');
    expect(screen.getAllByRole('switch')).toHaveLength(1);
    await user.clear(screen.getByLabelText('Search'));

    await user.click(screen.getByLabelText('Status'));
    await user.click(screen.getByRole('option', { name: 'Allowed' }));
    expect(screen.getAllByRole('switch')).toHaveLength(7);

    await user.click(screen.getByLabelText('Family'));
    await user.click(screen.getByRole('option', { name: 'Authorization' }));
    expect(screen.getAllByRole('switch')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: 'Collapse Access control model' }));
    expect(screen.getByRole('button', { name: 'Expand Access control model' })).toHaveAttribute('aria-expanded', 'false');
  });
});
