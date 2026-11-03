import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { getModuleHelp } from '../../api/aiModuleApi';
import type { AiStatus, LearningModuleDetail } from '../../types/vision';
import { ModuleHelpCard } from './ModuleHelpCard';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiModuleApi', () => ({ getModuleHelp: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const beliefs = {
  number: 1,
  title: 'Rewrite Limiting Beliefs',
  fields: [{ key: 'belief', label: 'Limiting belief', type: 'text' }, { key: 'replacement', label: 'Replacement statement', type: 'text' }],
  rows: [],
} as unknown as LearningModuleDetail;

describe('ModuleHelpCard (V9 Phase 7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('asks its questions one at a time', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getModuleHelp).mockResolvedValue({ mode: 'START', questions: ['What stops you?', 'Where did it come from?'],
      example: 'Dara believed she was too old.', rows: [], improvements: [] });
    render(<ModuleHelpCard token="token" module={beliefs} rows={[{ belief: '', replacement: '' }]} onUseDraft={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Help me start' }));
    expect(await screen.findByText('What stops you?')).toBeInTheDocument();
    expect(screen.queryByText('Where did it come from?')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next question' }));
    expect(screen.getByText('Where did it come from?')).toBeInTheDocument();
    expect(screen.getByText('Dara believed she was too old.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Feedback on my answer' })).toBeDisabled();
  });

  it('puts a draft in the form only when asked, and never saves it', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    const draft = [{ belief: 'It is too late for me', replacement: 'I can start small today' }];
    vi.mocked(getModuleHelp).mockResolvedValue({ mode: 'DRAFT', questions: [], rows: draft, improvements: [] });
    const onUseDraft = vi.fn();
    render(<ModuleHelpCard token="token" module={beliefs} rows={[{ belief: '', replacement: '' }]} onUseDraft={onUseDraft} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Draft my answer' }));
    expect(await screen.findByText('It is too late for me', { exact: false })).toBeInTheDocument();
    expect(onUseDraft).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Put this draft in the form' }));
    expect(onUseDraft).toHaveBeenCalledWith(draft);
    expect(screen.getByText(/then save the exercise/)).toBeInTheDocument();
  });
});
