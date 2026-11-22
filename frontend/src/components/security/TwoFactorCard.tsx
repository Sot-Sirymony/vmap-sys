import { FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import QRCode from 'qrcode';
import {
  chooseEmailCodes,
  confirmAuthenticatorSetup,
  getTwoFactorStatus,
  newRecoveryCodes,
  removeAllTrustedDevices,
  removeTrustedDevice,
  startAuthenticatorSetup,
  turnOffTwoFactor,
  type TotpSetup,
  type TwoFactorStatus,
} from '../../api/twoFactorApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { isStepUpCancelled, useStepUp } from '../step-up/useStepUp';
import { RecoveryCodesPanel } from './RecoveryCodesPanel';

/** "ABCD EFGH …": easier to read and type than one long string. */
export function groupKey(secret: string) {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

/**
 * V21 (TF-FR-01 to TF-FR-09, TF-FR-23): two-step sign-in on the Security page.
 * Every change asks for a fresh "Verify it's you" code first (TF-FR-06).
 */
export function TwoFactorCard({ token }: { token: string | null }) {
  const [status, setStatus] = useState<TwoFactorStatus | null>(null);
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [qr, setQr] = useState('');
  const [code, setCode] = useState('');
  const [codes, setCodes] = useState<string[] | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const stepUp = useStepUp(token);

  const load = useCallback(() => {
    if (!token) return;
    getTwoFactorStatus(token)
      .then(setStatus)
      .catch((caught: unknown) => setError(caught instanceof Error ? tr(caught.message) : tr('Unable to load two-step sign-in.')));
  }, [token]);

  useEffect(load, [load]);

  useEffect(() => {
    if (!setup) {
      setQr('');
      return;
    }
    let active = true;
    QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 200 })
      .then((url) => { if (active) setQr(url); })
      .catch(() => { if (active) setQr(''); });
    return () => { active = false; };
  }, [setup]);

  async function run(action: () => Promise<void>) {
    setError('');
    setBusy(true);
    try {
      await action();
    } catch (caught) {
      if (!isStepUpCancelled(caught)) {
        setError(caught instanceof Error ? tr(caught.message) : tr('Something went wrong. Please try again.'));
      }
    } finally {
      setBusy(false);
    }
  }

  const startAuthenticator = () => run(async () => {
    setSetup(await stepUp.protect('TWO_FACTOR_SETTINGS', (stepUpId) => startAuthenticatorSetup(token, stepUpId)));
    setCode('');
  });

  const confirm = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      const result = await confirmAuthenticatorSetup(token, code);
      setSetup(null);
      setCodes(result.recoveryCodes);
      load();
    });
  };

  const chooseEmail = () => run(async () => {
    const result = await stepUp.protect('TWO_FACTOR_SETTINGS', (stepUpId) => chooseEmailCodes(token, stepUpId));
    setCodes(result.recoveryCodes);
    load();
  });

  const regenerate = () => run(async () => {
    const result = await stepUp.protect('TWO_FACTOR_SETTINGS', (stepUpId) => newRecoveryCodes(token, stepUpId));
    setCodes(result.recoveryCodes);
    load();
  });

  const turnOff = () => run(async () => {
    if (!window.confirm(tr('Turn off two-step sign-in? Your password alone will open your account again.'))) return;
    await stepUp.protect('TWO_FACTOR_SETTINGS', (stepUpId) => turnOffTwoFactor(token, stepUpId));
    load();
  });

  const removeDevice = (id: number) => run(async () => {
    await removeTrustedDevice(token, id);
    load();
  });

  const removeAll = () => run(async () => {
    await removeAllTrustedDevices(token);
    load();
  });

  const methodLabel = status?.method === 'EMAIL' ? tr('Email code') : tr('Authenticator app');

  return (
    <Card sx={{ maxWidth: 520 }}>
      <CardHeader title={tr('Two-step sign-in')}
        subheader={tr('After your password, prove it is you with a code from your phone or email.')} />
      <CardContent>
        <Stack spacing={2}>
          {error && <ErrorMessage message={error} />}
          {codes ? (
            <RecoveryCodesPanel codes={codes} onDone={() => setCodes(null)} />
          ) : setup ? (
            <Box component="form" onSubmit={confirm} sx={{ display: 'grid', gap: 1.5 }}>
              <Typography variant="body2">
                {tr('1. Scan this QR code with an authenticator app, such as Google Authenticator or Microsoft Authenticator.')}
              </Typography>
              {qr && <Box component="img" src={qr} alt={tr('QR code for your authenticator app')} sx={{ width: 200, height: 200 }} />}
              <Typography variant="body2">{tr('Or type this key into the app:')}</Typography>
              <Typography component="code" aria-label={tr('Setup key')} sx={{ fontFamily: 'monospace', fontSize: '1rem', wordBreak: 'break-all' }}>
                {groupKey(setup.secret)}
              </Typography>
              <label>
                {tr('2. Type the 6-digit code the app shows')}
                <Input value={code} onChange={(event) => setCode(event.target.value)} inputMode="numeric"
                  aria-label={tr('2. Type the 6-digit code the app shows')}
                  autoComplete="one-time-code" maxLength={7} required />
              </label>
              <Stack direction="row" spacing={1}>
                <Button type="submit" disabled={busy || !code.trim()}>{tr('Turn on')}</Button>
                <Button variant="secondary" onClick={() => setSetup(null)}>{tr('Cancel')}</Button>
              </Stack>
            </Box>
          ) : status?.enabled ? (
            <Stack spacing={1.5}>
              <Alert severity="success">
                {tr('On: {method}, since {date}.', {
                  method: methodLabel,
                  date: status.enabledAt ? formatDate(new Date(status.enabledAt), { year: true }) : '',
                })}
              </Alert>
              <Typography variant="body2">
                {tr('Recovery codes left: {count}', { count: status.recoveryCodesLeft })}
              </Typography>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                <Button variant="secondary" disabled={busy} onClick={() => void regenerate()}>{tr('New recovery codes')}</Button>
                <Button variant="secondary" disabled={busy} onClick={() => void startAuthenticator()}>
                  {status.method === 'EMAIL' ? tr('Switch to an authenticator app') : tr('Move to a new phone')}
                </Button>
                {status.method === 'TOTP' && status.emailMethodAvailable && (
                  <Button variant="secondary" disabled={busy} onClick={() => void chooseEmail()}>{tr('Switch to email codes')}</Button>
                )}
                {status.canTurnOff && (
                  <Button variant="danger" disabled={busy} onClick={() => void turnOff()}>{tr('Turn off')}</Button>
                )}
              </Stack>
            </Stack>
          ) : status ? (
            <Stack spacing={1.5}>
              <Typography variant="body2" color="text.secondary">{tr('Off. Your password alone opens your account.')}</Typography>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                <Button disabled={busy} onClick={() => void startAuthenticator()}>{tr('Set up an authenticator app')}</Button>
                {status.emailMethodAvailable && (
                  <Button variant="secondary" disabled={busy} onClick={() => void chooseEmail()}>{tr('Use email codes instead')}</Button>
                )}
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {tr('An authenticator app is safer: it works without email and cannot be read by someone who gets into your email.')}
              </Typography>
            </Stack>
          ) : null}

          {status && status.trustedDevices.length > 0 && !setup && !codes && (
            <Stack spacing={1}>
              <Typography variant="subtitle2">{tr('Remembered devices')}</Typography>
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {status.trustedDevices.map((device) => (
                  <li key={device.id}>
                    {tr('{device}, last used {date}', {
                      device: device.label,
                      date: formatDate(new Date(device.lastUsedAt), { year: true }),
                    })}{' '}
                    <Button variant="secondary" disabled={busy} onClick={() => void removeDevice(device.id)}
                      aria-label={tr('Remove {device}', { device: device.label })}>{tr('Remove')}</Button>
                  </li>
                ))}
              </Box>
              <Box>
                <Button variant="secondary" disabled={busy} onClick={() => void removeAll()}>{tr('Remove all')}</Button>
              </Box>
            </Stack>
          )}
        </Stack>
      </CardContent>
      {stepUp.dialog}
    </Card>
  );
}
