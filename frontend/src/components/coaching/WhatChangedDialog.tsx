import { useState, type FormEvent } from 'react';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import { tr } from '../../i18n/translate';

/**
 * V17 BL-FR-27: when a task leaves Blocked, one optional line on what
 * changed, kept in its progress log so the lesson is not lost. Skipping still
 * makes the status change.
 */
export function WhatChangedDialog({ taskTitle, onSubmit, onCancel }: {
  taskTitle: string;
  onSubmit: (note: string | undefined) => void;
  onCancel: () => void;
}) {
  const [note, setNote] = useState('');

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit(note.trim() || undefined);
  }

  return (
    <Modal title={tr('No longer blocked: {title}', { title: taskTitle })} onClose={onCancel}>
      <form className="stack-list" onSubmit={handleSubmit}>
        <label>
          {tr('What changed, or what will you do differently?')}
          <Input value={note} maxLength={1000} autoFocus placeholder={tr('Optional')} onChange={(event) => setNote(event.target.value)} />
        </label>
        <div className="row-actions">
          <Button type="submit">{tr('Save')}</Button>
          <Button type="button" variant="secondary" onClick={() => onSubmit(undefined)}>{tr('Skip')}</Button>
        </div>
      </form>
    </Modal>
  );
}
