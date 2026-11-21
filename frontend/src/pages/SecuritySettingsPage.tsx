import { FormEvent, useState } from 'react';
import Alert from '@mui/material/Alert';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { changePassword } from '../api/authApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { isStepUpCancelled, useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { useSignInMethods } from '../hooks/useSignInMethods';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

/** Matches the backend's @Size(min = 8) on the new password. */
const MIN_PASSWORD_LENGTH = 8;

export function SecuritySettingsPage() {
  const { token, user } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(false);
  const stepUp = useStepUp(token);
  // V14 (D-14-3): an account created with Google sets its first password with only the email code.
  const { methods, setMethods } = useSignInMethods(token);
  const firstPassword = methods?.passwordSet === false;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setSaved(false);

    // Checked here rather than server-side: the confirmation field exists to
    // catch a typo before it becomes a password nobody knows, and the backend
    // has no use for a value whose only job is to equal another one.
    if (newPassword !== confirmPassword) {
      setError(tr('The new passwords do not match.'));
      return;
    }

    setLoading(true);
    try {
      // V20 SV-FR-01: the server may ask "Verify it's you" first; the form keeps what was typed.
      const request = firstPassword ? { newPassword } : { currentPassword, newPassword };
      await stepUp.protect('CHANGE_PASSWORD', (code) => changePassword(request, token, code));
      setSaved(true);
      if (methods) setMethods({ ...methods, passwordSet: true });
      // Cleared on success so the new password is not left sitting in three
      // form fields on a screen the user may walk away from.
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      if (isStepUpCancelled(error)) return;
      setError(error instanceof Error ? tr(error.message) : tr('Could not change the password.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageSection title={tr('Security')} subtitle={firstPassword ? tr('Set a password for your account.') : tr('Change the password for your account.')}>
      <Card sx={{ maxWidth: 520 }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {firstPassword
                ? tr('Signed in as {email}. You signed up with Google, so there is no current password. Set one to also sign in with your email.', { email: user?.email ?? '' })
                : tr('Signed in as {email}. Enter your current password to confirm it is you.', { email: user?.email ?? '' })}
            </Typography>

            <form className="form-stack" onSubmit={handleSubmit}>
              {!firstPassword && (
                <label>
                  {tr('Current password')}
                  <Input
                    type="password"
                    name="currentPassword"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(event) => setCurrentPassword(event.target.value)}
                    required
                  />
                </label>
              )}
              <label>
                {tr('New password')}
                <Input
                  type="password"
                  name="newPassword"
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  required
                />
              </label>
              <label>
                {tr('Confirm new password')}
                <Input
                  type="password"
                  name="confirmPassword"
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  required
                />
              </label>

              <Typography variant="caption" color="text.secondary">
                {tr('Use at least {count} characters.', { count: MIN_PASSWORD_LENGTH })}
              </Typography>

              {error && <ErrorMessage message={error} />}
              {saved && (
                <Alert severity="success">
                  {tr('Password changed. Use the new password next time you sign in.')}
                </Alert>
              )}

              <Button type="submit" disabled={loading}>
                {loading ? tr('Saving...') : firstPassword ? tr('Set password') : tr('Change password')}
              </Button>
              <Typography variant="caption" color="text.secondary">
                {tr("We'll email you a code to confirm it's you.")}
              </Typography>
            </form>
          </Stack>
        </CardContent>
      </Card>
      {stepUp.dialog}
    </PageSection>
  );
}
