import { FormEvent, useEffect, useState } from 'react';
import { archiveVisionArea, permanentlyDeleteVisionArea, createVisionArea, getVisionAreaArchiveImpact, listVisionAreas, restoreVisionArea, updateVisionArea } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import type { LifecycleStatus, Priority, VisionArea, VisionAreaRequest } from '../types/vision';
import { PageSection } from './PageSection';

export function VisionAreasPage() {
  const { token } = useAuth();
  const crud = useCrudEntity<VisionArea, VisionAreaRequest>({
    token,
    entityLabel: 'vision areas',
    list: listVisionAreas,
    create: createVisionArea,
    update: updateVisionArea,
    archive: archiveVisionArea,
    permanentlyDelete: permanentlyDeleteVisionArea,
    restore: restoreVisionArea,
  });
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [status, setStatus] = useState<LifecycleStatus>('ACTIVE');

  useEffect(() => {
    void crud.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({ name, description, priority, status });
    if (success) {
      setName('');
      setDescription('');
    }
    return success;
  }

  function startEdit(area: VisionArea) {
    crud.startEdit(area.id);
    setName(area.name);
    setDescription(area.description ?? '');
    setPriority(area.priority);
    setStatus(area.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setName('');
    setDescription('');
    setPriority('HIGH');
    setStatus('ACTIVE');
  }

  async function archiveImpactMessage(area: VisionArea) {
    if (!token) {
      return 'Archive this vision area?';
    }
    const impact = await getVisionAreaArchiveImpact(token, area.id);
    return `Archiving "${area.name}" also archives ${impact.dreams} dream(s), ${impact.goals} goal(s), ${impact.steps} step(s), and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const formFields = (
    <>
      <label>
        Name
        <Input value={name} onChange={(event) => setName(event.target.value)} required />
      </label>
      <label>
        Priority
        <FormControl fullWidth size="small">
          <Select value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
            <MenuItem value="LOW">Low</MenuItem>
            <MenuItem value="MEDIUM">Medium</MenuItem>
            <MenuItem value="HIGH">High</MenuItem>
            <MenuItem value="CRITICAL">Critical</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select value={status} onChange={(event) => setStatus(event.target.value as LifecycleStatus)}>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
            <MenuItem value="ARCHIVED">Archived</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection title="Vision Areas" subtitle="Organize the major areas of life or work.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create vision area"
        editTitle="Edit Vision Area"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        {crud.items.length === 0 ? (
          <EmptyState>No vision areas yet.</EmptyState>
        ) : (
          <TableContainer>
          <Table className="data-table">
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {crud.items.map((area) => (
                <TableRow key={area.id} className={area.archived ? 'row-archived' : ''}>
                  <TableCell>{area.code}</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>{area.name}</TableCell>
                  <TableCell><PriorityBadge priority={area.priority} /></TableCell>
                  <TableCell><StatusBadge status={area.status} /></TableCell>
                  <TableCell className="row-actions">
                    <RowActionsMenu
                      onEdit={() => startEdit(area)}
                      onArchive={() => void crud.archive(area.id)}
                      onRestore={() => void crud.restore(area.id)}
                      onDeletePermanently={() => void crud.permanentlyDelete(area.id)}
                      archived={area.archived}
                      confirmArchive={() => archiveImpactMessage(area)}
                      label="Vision area actions"
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          </TableContainer>
        )}
        </CardContent>
      </Card>
    </PageSection>
  );
}
