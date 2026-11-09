import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { aGoal } from '../../test/builders';
import { renderPage } from '../../test/render';
import type { SelfAssessmentItem } from '../../types/vision';
import { GapCoverDialog } from './GapCoverDialog';

const gap: SelfAssessmentItem = {
  id: 7, dreamId: 2, kind: 'WEAKNESS', description: 'Bookkeeping', evidence: 'Missed a filing', relevance: 'HIGH',
  source: 'MANUAL', outsideMismatch: false, archived: false, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z',
};

function open(overrides: Partial<Parameters<typeof GapCoverDialog>[0]> = {}) {
  const onSubmit = vi.fn();
  const onClose = vi.fn();
  renderPage(
    <GapCoverDialog item={gap} goals={[aGoal({ id: 30, dreamId: 2, title: 'Find clients' })]} learnInUse={0} learnLimit={2}
      saving={false} error="" onSubmit={onSubmit} onClose={onClose} {...overrides} />,
  );
  const dialog = within(screen.getByRole('dialog'));
  return { dialog, onSubmit, onClose };
}

describe('GapCoverDialog (V8.1 FR-82)', () => {
  it('adds a partner by name and support type, with an optional note', async () => {
    const { dialog, onSubmit } = open();
    const save = dialog.getByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    await userEvent.type(dialog.getByRole('textbox', { name: /Partner name/ }), 'A bookkeeper');
    await userEvent.click(dialog.getByRole('combobox', { name: 'Support type' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Technical' }));
    await userEvent.type(dialog.getByRole('textbox', { name: /What will they help with/ }), 'Monthly books');
    await userEvent.click(save);
    expect(onSubmit).toHaveBeenCalledWith({ method: 'PARTNER', partnerName: 'A bookkeeper', partnerSupportType: 'TECHNICAL', note: 'Monthly books' });
  });

  it('learns it by a date under one of the dream’s goals', async () => {
    const { dialog, onSubmit } = open();
    await userEvent.click(dialog.getByRole('radio', { name: /Learn it myself/ }));
    const save = dialog.getByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    fireEvent.change(screen.getByRole('dialog').querySelector('input[type="date"]') as HTMLInputElement, { target: { value: '2026-12-31' } });
    await userEvent.click(save);
    expect(onSubmit).toHaveBeenCalledWith({ method: 'LEARN', goalId: 30, targetDate: '2026-12-31', note: undefined });
  });

  it('asks for a goal first when the dream has none', async () => {
    const { dialog } = open({ goals: [] });
    await userEvent.click(dialog.getByRole('radio', { name: /Learn it myself/ }));
    expect(dialog.getByText(/This dream has no goals yet/)).toBeInTheDocument();
  });

  it.each([
    ['Delegate or automate', /Who or what will carry it/, 'DELEGATE'],
    ['Resource', /Who or what could supply it/, 'RESOURCE'],
  ])('%s needs a note before it can be saved', async (label, field, method) => {
    const { dialog, onSubmit } = open();
    await userEvent.click(dialog.getByRole('radio', { name: new RegExp(label) }));
    expect(dialog.getByRole('button', { name: 'Save' })).toBeDisabled();
    await userEvent.type(dialog.getByRole('textbox', { name: field }), 'A scheduling tool');
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(onSubmit).toHaveBeenCalledWith({ method, note: 'A scheduling tool' });
  });

  it('shows a server refusal and closes on Cancel', async () => {
    const { dialog, onClose } = open({ error: 'Choose one of this dream\'s goals.' });
    expect(dialog.getByText('Choose one of this dream\'s goals.')).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
  });
});
