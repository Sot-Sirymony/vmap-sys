import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link } from 'react-router';
import type { SignInMethods } from '../../api/oauthApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';

/**
 * V14 (GA-FR-27, D-14-3): the ways this account can sign in. Shown only when
 * Google is linked or no password is set yet, so nothing changes for anyone
 * who never used Google.
 */
export function SignInMethodsCard({ methods }: { methods: SignInMethods | null }) {
  const google = methods?.linkedAccounts.find((account) => account.provider === 'GOOGLE');
  if (!methods || (!google && methods.passwordSet)) {
    return null;
  }
  return (
    <Card>
      <CardHeader title={tr('Sign-in methods')} />
      <CardContent>
        <Stack spacing={1}>
          {google && (
            <Typography variant="body2">
              {tr('Google: connected as {email}', { email: google.email })}
              <Typography component="span" variant="caption" color="text.secondary">
                {' · '}{tr('since {date}', { date: formatDate(new Date(google.linkedAt), { year: true }) })}
              </Typography>
            </Typography>
          )}
          <Typography variant="body2">
            {methods.passwordSet ? tr('Password: set') : tr('Password: not set yet')}
          </Typography>
          {!methods.passwordSet && (
            <Typography variant="caption" color="text.secondary">
              {tr('You signed up with Google.')} <Link to="/settings/security">{tr('Set a password')}</Link>{' '}
              {tr('to also sign in with your email.')}
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
