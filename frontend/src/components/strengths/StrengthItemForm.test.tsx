import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../test/render';
import { EMPTY_STRENGTH_FORM, StrengthItemForm } from './StrengthItemForm';

describe('StrengthItemForm (V8.1 FR-79, FR-83)', () => {
  it('records an outside view and whether the user agrees with it', async () => {
    const onSubmit = vi.fn();
    renderPage(<StrengthItemForm dreamId={2} initial={EMPTY_STRENGTH_FORM} editing={false} saving={false} onSubmit={onSubmit} onCancel={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Gap to cover' }));
    await userEvent.type(screen.getByRole('textbox', { name: /What do you lack/ }), 'Public speaking');
    await userEvent.type(screen.getByRole('textbox', { name: /Evidence/ }), 'I avoid presenting');
    await userEvent.click(screen.getByRole('combobox', { name: 'How much does it matter to this dream?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Matters little' }));
    expect(screen.queryByRole('group', { name: 'Do you agree with them?' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: /Who said it/ }), 'My manager');
    await userEvent.type(screen.getByRole('textbox', { name: /What did they say/ }), 'You present well');
    await userEvent.click(screen.getByRole('button', { name: 'No' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(onSubmit).toHaveBeenCalledWith({
      dreamId: 2, kind: 'WEAKNESS', description: 'Public speaking', evidence: 'I avoid presenting', relevance: 'LOW',
      source: 'MANUAL', outsideBy: 'My manager', outsideView: 'You present well', outsideAgrees: false,
    });
  });

  it('leaves the outside view out when nothing was said, and can be cancelled while editing', async () => {
    const onSubmit = vi.fn();
    const onCancel = vi.fn();
    renderPage(
      <StrengthItemForm dreamId={2} editing saving={false} onSubmit={onSubmit} onCancel={onCancel}
        initial={{ ...EMPTY_STRENGTH_FORM, description: 'Sales', evidence: 'Top seller', relevance: 'HIGH', outsideBy: 'Someone' }} />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ outsideBy: undefined, outsideView: undefined, outsideAgrees: null }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalled();
  });
});
