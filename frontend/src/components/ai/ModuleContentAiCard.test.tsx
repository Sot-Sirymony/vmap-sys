import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { draftModuleContent, translateToKhmer } from '../../api/aiAdminApi';
import { listModuleContent } from '../../api/moduleContentApi';
import type { ModuleContent } from '../../types/vision';
import { ModuleContentAiCard } from './ModuleContentAiCard';

vi.mock('../../api/aiAdminApi', () => ({ draftModuleContent: vi.fn(), translateToKhmer: vi.fn() }));
vi.mock('../../api/moduleContentApi', () => ({ listModuleContent: vi.fn() }));

const english = {
  number: 1, position: 2, language: 'en', translated: true, title: 'Rewrite Limiting Beliefs', summary: 'S', guidance: 'G',
  exerciseInstructions: 'E', fields: [{ key: 'belief', label: 'Limiting belief', type: 'text' }],
} as unknown as ModuleContent;

describe('ModuleContentAiCard (AI-FR-22, AI-FR-23)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('puts a Khmer draft of the English text in the form, skipping anything dropped', async () => {
    vi.mocked(listModuleContent).mockResolvedValue([english]);
    vi.mocked(translateToKhmer).mockResolvedValue({ translations: ['សរសេរជំនឿឡើងវិញ', null, 'ការណែនាំ', 'លំហាត់', 'ជំនឿ'] });
    const onFill = vi.fn();
    render(<ModuleContentAiCard token="token" module={{ ...english, language: 'km' }} language="km" onFill={onFill} />);

    await userEvent.click(screen.getByRole('button', { name: 'Translate from English with AI' }));
    expect(translateToKhmer).toHaveBeenCalledWith('token', ['Rewrite Limiting Beliefs', 'S', 'G', 'E', 'Limiting belief']);
    expect(onFill).toHaveBeenCalledWith({ title: 'សរសេរជំនឿឡើងវិញ', summary: undefined, guidance: 'ការណែនាំ',
      exerciseInstructions: 'លំហាត់', labels: { belief: 'ជំនឿ' } });
    expect(await screen.findByText(/Have a Khmer speaker check it/)).toBeInTheDocument();
  });

  it('shows an English draft with quiz questions and fills the form only when asked', async () => {
    vi.mocked(draftModuleContent).mockResolvedValue({ title: 'Clearer title', guidance: 'Plain words', examples: 'Dara...',
      quiz: [{ question: 'What is a limiting belief?', answer: 'A thought that stops you.' }] });
    const onFill = vi.fn();
    render(<ModuleContentAiCard token="token" module={english} language="en" onFill={onFill} />);

    await userEvent.type(screen.getByLabelText('What should change? (optional)'), 'Simpler words');
    await userEvent.click(screen.getByRole('button', { name: 'Draft with AI' }));
    expect(draftModuleContent).toHaveBeenCalledWith('token', 1, 'Simpler words');
    expect(await screen.findByText(/What is a limiting belief\?/)).toBeInTheDocument();
    expect(onFill).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Put this text in the form' }));
    expect(onFill).toHaveBeenCalledWith(expect.objectContaining({ title: 'Clearer title', guidance: 'Plain words' }));
  });
});
