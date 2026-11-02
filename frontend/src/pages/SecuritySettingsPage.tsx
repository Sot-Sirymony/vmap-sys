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
import { useAuth } from '../context/AuthContext';
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
      await changePassword({ currentPassword, newPassword }, token);
      setSaved(true);
      // Cleared on success so the new password is not left sitting in three
      // form fields on a screen the user may walk away from.
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      setError(error instanceof Error ? error.message : tr('Could not change the password.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageSection title={tr('Security')} subtitle={tr('Change the password for your account.')}>
      <Card sx={{ maxWidth: 520 }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {tr('Signed in as {email}. Enter your current password to confirm it is you.', { email: user?.email ?? '' })}
            </Typography>

            <form className="form-stack" onSubmit={handleSubmit}>
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
                {loading ? tr('Saving...') : tr('Change password')}
              </Button>
            </form>
          </Stack>
        </CardContent>
      </Card>
    </PageSection>
  );
}
