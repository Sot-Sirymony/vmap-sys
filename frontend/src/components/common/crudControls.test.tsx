import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type FormEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { CrudModalForm } from './CrudModalForm';
import { QuickAddRow } from './QuickAddRow';
import { RowActionsMenu } from './RowActionsMenu';

describe('QuickAddRow', () => {
  const parents = [{ value: '1', label: 'Search literature' }, { value: '2', label: 'Write rationale' }];

  function Harness({ onAdd, withDueDate = false }: { onAdd: (title: string, dueDate?: string) => Promise<void>; withDueDate?: boolean }) {
    const [parent, setParent] = useState('');
    return (
      <QuickAddRow parentLabel="Step" parents={parents} parentValue={parent} onParentChange={setParent}
        placeholder="New task title" withDueDate={withDueDate} onAdd={onAdd} />
    );
  }

  it('adds a titled item under the chosen parent, then clears the title', async () => {
    const onAdd = vi.fn().mockResolvedValue(undefined);
    render(<Harness onAdd={onAdd} />);
    const add = screen.getByRole('button', { name: 'Add' });
    await userEvent.type(screen.getByPlaceholderText('New task title'), '  Search PubMed  ');
    expect(add).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'Step' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Write rationale' }));
    await userEvent.click(add);
    expect(onAdd).toHaveBeenCalledWith('Search PubMed', undefined);
    await waitFor(() => expect(screen.getByPlaceholderText('New task title')).toHaveValue(''));
  });

  it('needs a due date when the item requires one (BR-5)', async () => {
    const onAdd = vi.fn().mockResolvedValue(undefined);
    render(<Harness onAdd={onAdd} withDueDate />);
    await userEvent.click(screen.getByRole('combobox', { name: 'Step' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Search literature' }));
    await userEvent.type(screen.getByPlaceholderText('New task title'), 'Search PubMed');
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/^Due date/), '2027-01-31');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(onAdd).toHaveBeenCalledWith('Search PubMed', '2027-01-31');
  });
});

describe('RowActionsMenu', () => {
  it('edits, runs extra actions, and archives only after confirming', async () => {
    const props = { onEdit: vi.fn(), onArchive: vi.fn(), extraActions: [{ label: 'Break into tasks', onClick: vi.fn() }] };
    render(<RowActionsMenu {...props} label="Actions for Search literature" />);
    const open = () => userEvent.click(screen.getByRole('button', { name: 'Actions for Search literature' }));

    await open();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Break into tasks' }));
    expect(props.extraActions[0].onClick).toHaveBeenCalled();
    await open();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    expect(props.onEdit).toHaveBeenCalled();

    await open();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText('Archive this record? You can bring it back later with "Show archived".')).toBeInTheDocument();
    expect(props.onArchive).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(props.onArchive).toHaveBeenCalled();
  });

  it('shows what else an archive would take with it, and keeps the record when cancelled', async () => {
    const onArchive = vi.fn();
    const impact = vi.fn().mockResolvedValue('Archiving "Career" also archives 2 dream(s).');
    render(<RowActionsMenu onEdit={vi.fn()} onArchive={onArchive} confirmArchive={impact} />);
    await userEvent.click(screen.getByRole('button', { name: 'Row actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText('Archiving "Career" also archives 2 dream(s).')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onArchive).not.toHaveBeenCalled();
  });

  it('keeps the general message when the impact cannot be loaded', async () => {
    render(<RowActionsMenu onEdit={vi.fn()} onArchive={vi.fn()} confirmArchive={vi.fn().mockRejectedValue(new Error('offline'))} />);
    await userEvent.click(screen.getByRole('button', { name: 'Row actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/Archive this record\?/)).toBeInTheDocument();
  });

  it('offers restore and permanent delete for an archived record, deleting only after confirming', async () => {
    const props = { onEdit: vi.fn(), onArchive: vi.fn(), onRestore: vi.fn(), onDeletePermanently: vi.fn() };
    render(<RowActionsMenu {...props} archived confirmDelete="Delete Career and its 2 dreams for good?" />);
    await userEvent.click(screen.getByRole('button', { name: 'Row actions' }));
    expect(screen.queryByRole('menuitem', { name: 'Edit' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(props.onRestore).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Row actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Delete permanently' }));
    expect(await screen.findByText('Delete Career and its 2 dreams for good?')).toBeInTheDocument();
    expect(props.onDeletePermanently).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /Delete/ }));
    expect(props.onDeletePermanently).toHaveBeenCalled();
  });
});

describe('CrudModalForm', () => {
  function Harness({ save = vi.fn().mockResolvedValue(true), editing = false, onCancelEdit = vi.fn() }) {
    const [title, setTitle] = useState('');
    return (
      <CrudModalForm editing={editing} createLabel="Create goal" editTitle="Edit goal" saving={false}
        onSubmit={(event: FormEvent) => { event.preventDefault(); return save(title); }} onCancelEdit={onCancelEdit}>
        <label>Title<input value={title} onChange={(event) => setTitle(event.target.value)} data-autofocus /></label>
      </CrudModalForm>
    );
  }

  it('opens a create form and closes it after a successful save', async () => {
    const save = vi.fn().mockResolvedValue(true);
    render(<Harness save={save} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create goal' }));
    expect(screen.getByRole('dialog', { name: 'Create goal' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Title'), 'Learn AI tools');
    await userEvent.click(screen.getAllByRole('button', { name: 'Create goal' }).at(-1)!);
    expect(save).toHaveBeenCalledWith('Learn AI tools');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('stays open after a failed save, and after "Save & add another" with a fresh form', async () => {
    const save = vi.fn().mockResolvedValueOnce(false).mockResolvedValueOnce(true);
    render(<Harness save={save} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create goal' }));
    await userEvent.type(screen.getByLabelText('Title'), 'Learn AI tools');
    await userEvent.click(screen.getAllByRole('button', { name: 'Create goal' }).at(-1)!);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Save & add another' }));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows an edit form with save and cancel', async () => {
    const onCancelEdit = vi.fn();
    render(<Harness editing onCancelEdit={onCancelEdit} />);
    expect(screen.getByRole('dialog', { name: 'Edit goal' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancelEdit).toHaveBeenCalled();
  });
});
