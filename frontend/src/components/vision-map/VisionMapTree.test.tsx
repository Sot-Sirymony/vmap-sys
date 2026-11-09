import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { updateDream } from '../../api/dreamApi';
import type { Dream } from '../../types/vision';
import { VisionMapTree } from './VisionMapTree';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { fullName: 'Tester', role: 'USER' } }) }));
vi.mock('../../context/ToastContext', () => ({ useToast: () => ({ showToast: vi.fn(), show: vi.fn() }) }));
vi.mock('../../api/dreamApi');
vi.mock('../../api/goalApi');
vi.mock('../../api/stepApi');
vi.mock('../../api/taskApi');

const dream = {
  id: 5, code: 'D-005', title: 'Own a home', visionAreaId: 1, status: 'ACTIVE', priority: 'HIGH', dreamType: 'LONG_TERM',
  whyImportant: 'Family', successDefinition: 'Keys in hand', archived: false, progressPercent: 0,
} as unknown as Dream;

function renderTree(onDataChange = vi.fn().mockResolvedValue(undefined)) {
  render(
    <MemoryRouter>
      <VisionMapTree dream={dream} visionAreaName="Home" goals={[]} steps={[]} tasks={[]} token="token" onDataChange={onDataChange} />
    </MemoryRouter>,
  );
  return onDataChange;
}

describe('VisionMapTree', () => {
  beforeEach(() => vi.clearAllMocks());

  it('is a tree that can take focus from code without adding a tab stop', () => {
    renderTree();
    expect(screen.getByRole('tree', { name: 'Vision map for Own a home' })).toHaveAttribute('tabindex', '-1');
  });

  it('puts focus in the rename box when a title is double-clicked, and saves the new title on Enter', async () => {
    vi.mocked(updateDream).mockResolvedValue({ ...dream, title: 'Own a family home' });
    const onDataChange = renderTree();

    await userEvent.dblClick(screen.getByText('Own a home', { selector: '.map-title' }));
    const rename = screen.getByRole('textbox', { name: 'Rename' });
    expect(rename).toHaveFocus();

    await userEvent.clear(rename);
    await userEvent.type(rename, 'Own a family home{Enter}');
    expect(updateDream).toHaveBeenCalledWith('token', 5, expect.objectContaining({ title: 'Own a family home' }));
    expect(onDataChange).toHaveBeenCalled();
  });

  it('colours progress by how far along it is, with a legend for the colours', () => {
    renderTree();
    const legend = screen.getByRole('list', { name: 'Progress colours' });
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Just started 0–24%', 'In progress 25–74%', 'Nearly done 75–99%', 'Done 100%',
    ]);
    // A dream with no goals yet is 0%: the red "just started" level.
    expect(screen.getByLabelText('Progress 0%')).toHaveClass('progress-bar--low');
    expect(screen.getByText('0%', { selector: '.map-rail-value' })).toHaveClass('progress-text--low');
  });
});
