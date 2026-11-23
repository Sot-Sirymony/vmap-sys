import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { tr } from '../../i18n/translate';
import type { AuthUser } from '../../types/auth';
import { PERMISSION_LABELS, isPrivileged, permissionsOf } from '../../utils/permissions';

const BUILT_IN_NAMES: Record<string, string> = { USER: 'Participant', COACH: 'Coach', ADMIN: 'Admin' };

/** "PROGRAM_ASSISTANT" → "Program assistant", for a custom role the session names only by key. */
export function roleLabel(key: string) {
  if (BUILT_IN_NAMES[key]) return tr(BUILT_IN_NAMES[key]);
  const words = key.toLowerCase().split('_').filter(Boolean).join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** V23 (UM-FR-45): the person's own role and what it allows, in plain words. */
export function MyRoleCard({ user }: Readonly<{ user: AuthUser | null }>) {
  if (!user) return null;
  const permissions = permissionsOf(user);
  return (
    <Card>
      <CardHeader title={tr('Your role')} subheader={tr('Set by your program admin.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Typography variant="body1" sx={{ fontWeight: 600 }}>{roleLabel(user.role)}</Typography>
          <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {permissions.map((permission) => (
              <Chip key={permission} size="small" variant="outlined" label={tr(PERMISSION_LABELS[permission] ?? permission)} />
            ))}
          </Stack>
          {isPrivileged(user) && (
            <Typography variant="body2" color="text.secondary">
              {tr('Because your role can do more than use your own plan, your account must use two-step sign-in.')}
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
