import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as twoFactorApi from '../../api/twoFactorApi';
import { groupKey, TwoFactorCard } from './TwoFactorCard';

vi.mock('../../api/twoFactorApi');
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,QR') } }));

const off: twoFactorApi.TwoFactorStatus = {
  enabled: false, recoveryCodesLeft: 0, emailMethodAvailable: true, canTurnOff: true, trustedDevices: [],
};
const codes = Array.from({ length: 10 }, (_, i) => `ABCDE-FGH${'JKMNPQRSTU'[i]}K`.slice(0, 11));

/** V21 phase 3 (TF-FR-01 to TF-FR-09, TF-FR-23; TFC-01): two-step sign-in on the Security page. */
describe('TwoFactorCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('sets up an authenticator: QR code and key, a confirming code, then the recovery codes once', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValueOnce(off)
      .mockResolvedValue({ ...off, enabled: true, method: 'TOTP', enabledAt: '2026-11-22T10:00:00Z', recoveryCodesLeft: 10 });
    vi.mocked(twoFactorApi.startAuthenticatorSetup).mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x', expiresAt: '2026-11-22T10:15:00Z',
    });
    vi.mocked(twoFactorApi.confirmAuthenticatorSetup).mockResolvedValue({ recoveryCodes: codes });
    render(<TwoFactorCard token="t" />);

    await userEvent.click(await screen.findByRole('button', { name: 'Set up an authenticator app' }));
    expect(await screen.findByAltText('QR code for your authenticator app')).toHaveAttribute('src', 'data:image/png;base64,QR');
    expect(screen.getByLabelText('Setup key')).toHaveTextContent('JBSW Y3DP EHPK 3PXP');
    await userEvent.type(screen.getByLabelText('2. Type the 6-digit code the app shows'), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Turn on' }));

    expect(twoFactorApi.confirmAuthenticatorSetup).toHaveBeenCalledWith('t', '123456');
    expect(await screen.findByRole('list', { name: 'Recovery codes' })).toBeInTheDocument();
    const done = screen.getByRole('button', { name: 'Done' });
    expect(done).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: 'I have saved these codes' }));
    await userEvent.click(done);
    expect(await screen.findByText(/On: Authenticator app/)).toBeInTheDocument();
    expect(screen.getByText('Recovery codes left: 10')).toBeInTheDocument();
  });

  it('lets a participant choose email codes, and hides that choice when it is not allowed', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(off);
    vi.mocked(twoFactorApi.chooseEmailCodes).mockResolvedValue({ recoveryCodes: codes });
    const { unmount } = render(<TwoFactorCard token="t" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Use email codes instead' }));
    expect(twoFactorApi.chooseEmailCodes).toHaveBeenCalledWith('t', undefined);
    unmount();

    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue({ ...off, emailMethodAvailable: false });
    render(<TwoFactorCard token="t" />);
    await screen.findByRole('button', { name: 'Set up an authenticator app' });
    expect(screen.queryByRole('button', { name: 'Use email codes instead' })).not.toBeInTheDocument();
  });

  it('when on, offers new recovery codes, and turning off only when allowed', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue({
      ...off, enabled: true, method: 'TOTP', enabledAt: '2026-11-22T10:00:00Z', recoveryCodesLeft: 3, canTurnOff: false,
    });
    vi.mocked(twoFactorApi.newRecoveryCodes).mockResolvedValue({ recoveryCodes: codes });
    render(<TwoFactorCard token="t" />);
    expect(await screen.findByText('Recovery codes left: 3')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Turn off' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New recovery codes' }));
    expect(await screen.findByRole('list', { name: 'Recovery codes' })).toBeInTheDocument();
  });

  it('lists remembered devices and removes one', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue({
      ...off, enabled: true, method: 'TOTP', recoveryCodesLeft: 10,
      trustedDevices: [{ id: 5, label: 'Safari on iOS', createdAt: '2026-11-20T10:00:00Z', lastUsedAt: '2026-11-21T10:00:00Z',
        expiresAt: '2026-12-20T10:00:00Z' }],
    });
    vi.mocked(twoFactorApi.removeTrustedDevice).mockResolvedValue(undefined);
    render(<TwoFactorCard token="t" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Safari on iOS' }));
    await waitFor(() => expect(twoFactorApi.removeTrustedDevice).toHaveBeenCalledWith('t', 5));
  });

  it('groups a setup key in fours', () => {
    expect(groupKey('ABCDEFGHIJ')).toBe('ABCD EFGH IJ');
  });
});
