import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Button } from './Button';
import { Modal } from './Modal';

type CrudModalFormProps = {
  editing: boolean;
  createLabel: string;
  editTitle: string;
  saving: boolean;
  disabled?: boolean;
  extraActions?: ReactNode;
  /**
   * Open the create modal on arrival, without a button click. Set by a page that
   * was linked to with "create the child now" intent (e.g. a dream's "Add goal"
   * shortcut lands on the Goals page with this true). It only fires the initial
   * open, so cancelling the modal doesn't immediately reopen it.
   */
  autoOpenCreate?: boolean;
  onSubmit: (event: FormEvent) => void | Promise<boolean>;
  onCancelEdit: () => void;
  children: ReactNode;
};

/**
 * The create-button/edit-modal scaffold repeated across every CRUD page:
 * a "Create X" button that opens the fields in a Modal, or the same fields
 * in a Modal with Save/Cancel when editing an existing record. `extraActions`
 * renders before the submit button, for pages with an extra action (e.g.
 * Communication's "Generate message").
 *
 * `onSubmit` may return the save's success boolean (every page's handler
 * already gets this back from `crud.save()`) so the create modal can close
 * itself only once the record actually saved, instead of staying open on
 * a validation error.
 */
export function CrudModalForm({
  editing,
  createLabel,
  editTitle,
  saving,
  disabled,
  extraActions,
  autoOpenCreate = false,
  onSubmit,
  onCancelEdit,
  children,
}: CrudModalFormProps) {
  const [creating, setCreating] = useState(false);

  // Open once when arriving with create intent; the flag going false again
  // (after the page clears its URL param) must not slam the modal shut.
  useEffect(() => {
    if (autoOpenCreate) {
      setCreating(true);
    }
  }, [autoOpenCreate]);

  async function handleCreateSubmit(event: FormEvent) {
    const result = onSubmit(event);
    const success = result instanceof Promise ? await result : true;
    if (success) {
      setCreating(false);
    }
  }

  if (editing) {
    return (
      <Modal title={editTitle} onClose={onCancelEdit}>
        <form className="form-grid" onSubmit={onSubmit}>
          {children}
          <div className="field-full row-actions">
            {extraActions}
            <Button type="submit" disabled={saving}>{saving ? 'Saving...' : 'Save changes'}</Button>
            <Button type="button" variant="secondary" onClick={onCancelEdit}>Cancel</Button>
          </div>
        </form>
      </Modal>
    );
  }

  return (
    <>
      <Button type="button" className="create-trigger" onClick={() => setCreating(true)} disabled={disabled}>{createLabel}</Button>
      {creating && (
        <Modal title={editTitle.replace('Edit', 'Create')} onClose={() => setCreating(false)}>
          <form className="form-grid" onSubmit={(event) => void handleCreateSubmit(event)}>
            {children}
            <div className="field-full row-actions">
              {extraActions}
              <Button type="submit" disabled={saving}>{saving ? 'Saving...' : createLabel}</Button>
              <Button type="button" variant="secondary" onClick={() => setCreating(false)}>Cancel</Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
