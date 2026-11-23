import { useCallback, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { createRole, deleteRole, listPermissions, listRoles, updateRole } from '../api/rolesApi';
import { DeleteRoleDialog, RoleEditor } from '../components/admin/RoleEditor';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { StepUpMailWarning } from '../components/step-up/StepUpMailWarning';
import { useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { tr } from '../i18n/translate';
import type { PermissionInfo, RoleInfo } from '../types/admin';
import { can } from '../utils/permissions';
import { PageSection } from './PageSection';

type Editing = { kind: 'edit'; role: RoleInfo | null } | { kind: 'delete'; role: RoleInfo } | null;

/**
 * V23 (BRD 23.0.0, UM-FR-08 to UM-FR-16): the roles and what each allows. Only
 * Admins change them; anyone with "View users" may read them.
 */
export function RolesPage() {
  const { token, user } = useAuth();
  const { showToast } = useToast();
  const stepUp = useStepUp(token);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [catalogue, setCatalogue] = useState<PermissionInfo[]>([]);
  const [editing, setEditing] = useState<Editing>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const manage = can(user, 'MANAGE_ROLES');

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const [nextRoles, nextCatalogue] = await Promise.all([listRoles(token), listPermissions(token)]);
      setRoles(nextRoles);
      setCatalogue(nextCatalogue);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not load the roles.'));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const label = (key: string) => tr(catalogue.find((permission) => permission.key === key)?.label ?? key);

  return (
    <PageSection title={tr('Roles')} subtitle={tr('What each role allows. Changes apply to everyone with the role on their next request.')}>
      <StepUpMailWarning token={token} />
      <Stack spacing={2}>
        {manage && (
          <Box>
            <Button onClick={() => setEditing({ kind: 'edit', role: null })} disabled={catalogue.length === 0}>{tr('New role')}</Button>
          </Box>
        )}
        {error && <ErrorMessage message={error} />}
        {loading && roles.length === 0 ? <Loading /> : roles.map((role) => (
          <Card key={role.key}>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="h6" component="h2">{tr(role.name)}</Typography>
                  {role.builtIn && <Chip size="small" label={tr('Built-in')} />}
                  {role.privileged && <Chip size="small" color="warning" variant="outlined" label={tr('Two-step sign-in required')} />}
                  <Chip size="small" variant="outlined" label={tr('{count} people', { count: role.holders })} />
                </Stack>
                {manage && (
                  <Stack direction="row" spacing={1}>
                    {role.editable && (
                      <Button variant="secondary" onClick={() => setEditing({ kind: 'edit', role })}>{tr('Edit')}</Button>
                    )}
                    {!role.builtIn && (
                      <Button variant="danger" onClick={() => setEditing({ kind: 'delete', role })}>{tr('Delete')}</Button>
                    )}
                  </Stack>
                )}
              </Stack>
              {role.description && <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{tr(role.description)}</Typography>}
              {!role.editable && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                  {tr('This role cannot be changed.')}
                </Typography>
              )}
              <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 1.5 }}>
                {role.permissions.map((key) => <Chip key={key} size="small" variant="outlined" label={label(key)} />)}
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      {token && editing?.kind === 'edit' && (
        <RoleEditor token={token} role={editing.role} roles={roles} catalogue={catalogue} onClose={() => setEditing(null)}
          onSave={async (input) => {
            const current = editing.role;
            const saved = await stepUp.protect('ADMIN_ROLE_EDIT', (code) => (current
              ? updateRole(token, current.key, input, code) : createRole(token, input, code)));
            setEditing(null);
            showToast(current ? tr('The role {name} was saved.', { name: saved.name }) : tr('The role {name} was created.', { name: saved.name }));
            await load();
          }} />
      )}
      {token && editing?.kind === 'delete' && (
        <DeleteRoleDialog role={editing.role} roles={roles} onClose={() => setEditing(null)}
          onSave={async (reason, moveTo) => {
            const doomed = editing.role;
            await stepUp.protect('ADMIN_ROLE_EDIT', (code) => deleteRole(token, doomed.key, reason, moveTo, code));
            setEditing(null);
            showToast(tr('The role {name} was deleted.', { name: doomed.name }));
            await load();
          }} />
      )}
      {stepUp.dialog}
    </PageSection>
  );
}
