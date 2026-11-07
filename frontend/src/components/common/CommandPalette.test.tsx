import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listDreams } from '../../api/dreamApi';
import type { Dream } from '../../types/vision';
import { CommandPalette } from './CommandPalette';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { role: 'USER' } }) }));
vi.mock('../../api/dreamApi', () => ({ listDreams: vi.fn() }));

function Where() {
  const location = useLocation();
  return <p>At {location.pathname}{location.search}</p>;
}

function renderPalette(onClose = vi.fn()) {
  render(
    <MemoryRouter initialEntries={['/start']}>
      <Routes><Route path="*" element={<><Where /><CommandPalette open onClose={onClose} onShowShortcuts={vi.fn()} /></>} /></Routes>
    </MemoryRouter>,
  );
  return onClose;
}

describe('CommandPalette (FR-29.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDreams).mockResolvedValue([{ id: 4, title: 'Own a home', archived: false } as Dream]);
  });

  it('moves through matches with the arrow keys in the search box and runs the chosen one with Enter', async () => {
    const onClose = renderPalette();
    const search = screen.getByRole('combobox', { name: 'Command palette search' });
    expect(search).toHaveFocus();

    await userEvent.type(search, 'new');
    const first = screen.getByRole('option', { name: /New vision area/ });
    expect(first).toHaveAttribute('aria-selected', 'true');

    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('option', { name: /New dream/ })).toHaveAttribute('aria-selected', 'true');
    expect(search).toHaveAttribute('aria-activedescendant', 'palette-option-new-dream');
    await userEvent.keyboard('{ArrowUp}{ArrowUp}');
    expect(first).toHaveAttribute('aria-selected', 'true');

    await userEvent.keyboard('{ArrowDown}{Enter}');
    expect(onClose).toHaveBeenCalled();
    expect(screen.getByText('At /dreams?create=dream')).toBeInTheDocument();
  });

  it('finds a dream by name and opens its map', async () => {
    renderPalette();
    await userEvent.type(screen.getByRole('combobox', { name: 'Command palette search' }), 'own a');
    await userEvent.click(await screen.findByRole('option', { name: /Own a home — open map/ }));
    expect(screen.getByText('At /dreams/4')).toBeInTheDocument();
  });
});
