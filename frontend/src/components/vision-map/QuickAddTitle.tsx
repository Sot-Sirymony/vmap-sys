import { FormEvent, useState } from 'react';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { tr } from '../../i18n/translate';

type QuickAddTitleProps = {
  placeholder: string;
  onAdd: (title: string) => Promise<void>;
};

export function QuickAddTitle({ placeholder, onAdd }: QuickAddTitleProps) {
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      return;
    }
    setBusy(true);
    try {
      await onAdd(title.trim());
      setTitle('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="quick-add" onSubmit={handleSubmit}>
      <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={placeholder} />
      <Button type="submit" variant="secondary" disabled={busy || !title.trim()}>{busy ? tr('Adding...') : tr('Add')}</Button>
    </form>
  );
}
