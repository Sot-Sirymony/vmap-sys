import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiAdminApi from '../api/aiAdminApi';
import * as aiApi from '../api/aiApi';
import * as moduleContentApi from '../api/moduleContentApi';
import { renderPage } from '../test/render';
import type { ModuleContent } from '../types/vision';
import { ModuleContentPage } from './ModuleContentPage';

vi.mock('../api/moduleContentApi');
vi.mock('../api/aiApi');
vi.mock('../api/aiAdminApi');

const beliefs: ModuleContent = {
  number: 1, position: 1, language: 'km', translated: true, title: 'សរសេរជំនឿឡើងវិញ', summary: 'Summary one', guidance: 'Guidance one',
  exerciseInstructions: 'Instructions one', fields: [{ key: 'belief', label: 'ជំនឿ', type: 'text' }],
} as ModuleContent;
const partners: ModuleContent = {
  number: 2, position: 2, language: 'km', translated: false, title: 'Find your partners', summary: 'Summary two', guidance: 'Guidance two',
  exerciseInstructions: 'Instructions two', fields: [],
} as ModuleContent;

describe('ModuleContentPage (FR-78)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(moduleContentApi.listModuleContent).mockResolvedValue([beliefs, partners]);
    vi.mocked(moduleContentApi.updateModuleContent).mockResolvedValue(beliefs as never);
    vi.mocked(moduleContentApi.reorderModules).mockResolvedValue(undefined as never);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
  });

  it('tells a participant that only an administrator can edit, and loads nothing', () => {
    renderPage(<ModuleContentPage />);
    expect(screen.getByText('Only an administrator can edit module content.')).toBeInTheDocument();
    expect(moduleContentApi.listModuleContent).not.toHaveBeenCalled();
  });

  it('opens the first module in Khmer for an admin, with its text and field labels', async () => {
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    expect(await screen.findByRole('textbox', { name: 'Title' })).toHaveValue('សរសេរជំនឿឡើងវិញ');
    expect(moduleContentApi.listModuleContent).toHaveBeenCalledWith('token', 'km');
    expect(screen.getByRole('textbox', { name: 'Label for belief' })).toHaveValue('ជំនឿ');
  });

  it('saves the edited text and labels for the chosen language', async () => {
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    const title = await screen.findByRole('textbox', { name: 'Title' });
    await userEvent.clear(title);
    await userEvent.type(title, 'ជំនឿថ្មី');
    const label = screen.getByRole('textbox', { name: 'Label for belief' });
    await userEvent.clear(label);
    await userEvent.type(label, 'ជំនឿដែលរារាំង');
    await userEvent.click(screen.getByRole('button', { name: 'Save module' }));

    await waitFor(() => expect(moduleContentApi.updateModuleContent).toHaveBeenCalledWith('token', 1, 'km', {
      title: 'ជំនឿថ្មី', summary: 'Summary one', guidance: 'Guidance one', exerciseInstructions: 'Instructions one',
      fieldLabels: { belief: 'ជំនឿដែលរារាំង' },
    }));
    expect(await screen.findByText('Saved.')).toBeInTheDocument();
  });

  it('says when a module has no text in this language yet', async () => {
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await screen.findByRole('textbox', { name: 'Title' });
    await userEvent.click(screen.getByRole('button', { name: '2. Find your partners' }));
    expect(await screen.findByText('Showing English because this language has no text for this module yet.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Find your partners');
  });

  it('switches the language being edited', async () => {
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await screen.findByRole('textbox', { name: 'Title' });
    await userEvent.click(screen.getByRole('combobox', { name: 'Language' }));
    await userEvent.click(screen.getByRole('option', { name: 'English' }));
    await waitFor(() => expect(moduleContentApi.listModuleContent).toHaveBeenLastCalledWith('token', 'en'));
  });

  it('moves a module down, and cannot move the first one up', async () => {
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await screen.findByRole('textbox', { name: 'Title' });
    expect(screen.getByRole('button', { name: 'Move សរសេរជំនឿឡើងវិញ up' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move Find your partners down' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Move សរសេរជំនឿឡើងវិញ down' }));
    expect(moduleContentApi.reorderModules).toHaveBeenCalledWith('token', [2, 1]);
  });

  it('shows why a save or a move failed', async () => {
    vi.mocked(moduleContentApi.updateModuleContent).mockRejectedValue(new Error('Title is too long'));
    vi.mocked(moduleContentApi.reorderModules).mockRejectedValue(new Error('Order is out of date'));
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await screen.findByRole('textbox', { name: 'Title' });
    await userEvent.click(screen.getByRole('button', { name: 'Save module' }));
    expect(await screen.findByText('Title is too long')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Move Find your partners up' }));
    expect(await screen.findByText('Order is out of date')).toBeInTheDocument();
  });

  it('fills the form from the AI Khmer translation, for the admin to check before saving', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
    const english = { ...beliefs, language: 'en', title: 'Rewrite limiting beliefs', summary: 'S', guidance: 'G', exerciseInstructions: 'I', fields: [{ key: 'belief', label: 'Belief', type: 'text' }] };
    vi.mocked(moduleContentApi.listModuleContent).mockImplementation(async (_token, language) => (language === 'en' ? [english] : [beliefs, partners]) as never);
    vi.mocked(aiAdminApi.translateToKhmer).mockResolvedValue({ translations: ['ចំណងជើង', 'សង្ខេប', 'ការណែនាំ', 'សេចក្តីណែនាំ', 'ស្លាក'] });
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await userEvent.click(await screen.findByRole('button', { name: 'Translate from English with AI' }));

    expect(aiAdminApi.translateToKhmer).toHaveBeenCalledWith('token', ['Rewrite limiting beliefs', 'S', 'G', 'I', 'Belief']);
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('ចំណងជើង'));
    expect(screen.getByRole('textbox', { name: 'Label for belief' })).toHaveValue('ស្លាក');
    expect(screen.getByText(/Have a Khmer speaker check it before you save/)).toBeInTheDocument();
    expect(moduleContentApi.updateModuleContent).not.toHaveBeenCalled();
  });

  it('fills the form from an AI draft only when the admin chooses to', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
    vi.mocked(aiAdminApi.draftModuleContent).mockResolvedValue({ title: 'Question your beliefs', summary: 'A new summary', guidance: '', exerciseInstructions: '', quiz: [] });
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    await screen.findByRole('textbox', { name: 'Title' });
    // Drafting is offered on the English text; Khmer is translated from it.
    expect(screen.queryByRole('button', { name: 'Draft with AI' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: 'Language' }));
    await userEvent.click(screen.getByRole('option', { name: 'English' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'What should change? (optional)' }), 'Shorter');
    await userEvent.click(screen.getByRole('button', { name: 'Draft with AI' }));
    expect(aiAdminApi.draftModuleContent).toHaveBeenCalledWith('token', 1, 'Shorter');
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('សរសេរជំនឿឡើងវិញ');
    await userEvent.click(await screen.findByRole('button', { name: 'Put this text in the form' }));
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Question your beliefs');
    // Parts the draft left empty keep what was there.
    expect(screen.getByRole('textbox', { name: /^Guidance/ })).toHaveValue('Guidance one');
  });

  it('shows why the content could not load', async () => {
    vi.mocked(moduleContentApi.listModuleContent).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<ModuleContentPage />, { role: 'ADMIN' });
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
