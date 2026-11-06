import { useState } from 'react';
import { tr } from '../../i18n/translate';

/**
 * One AI action inside a stage: busy, error, the last clarifying question and
 * the last answer's request id (for the rating). Suggestions themselves go
 * straight into the draft through the action's own callback.
 */
export function useAiAction() {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [question, setQuestion] = useState<string | null>(null);
  const [requestId, setRequestId] = useState<number | null>(null);
  // Which action the question and request id belong to, for stages with one action per goal.
  const [lastId, setLastId] = useState<string | null>(null);

  async function run(id: string, action: () => Promise<{ question?: string | null; requestId?: number | null }>) {
    setBusy(id);
    setError('');
    setQuestion(null);
    try {
      const answer = await action();
      setLastId(id);
      setQuestion(answer.question ?? null);
      setRequestId(answer.requestId ?? null);
    } catch (runError) {
      setError(runError instanceof Error ? tr(runError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(null);
    }
  }

  return { busy, error, question, requestId, lastId, run };
}
