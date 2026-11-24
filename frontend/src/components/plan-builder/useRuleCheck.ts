import { useEffect, useRef, useState } from 'react';
import { checkPlanDraft } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { DraftIssue, PlanDraftContent } from '../../types/vision';
import { countNew } from './draftModel';

/** V31 DC-FR-04: the rules re-run this long after the last change. */
export const CHECK_DELAY_MS = 1000;

/**
 * V31 (DC-FR-04, D-31-3): the server's rules R-01 to R-08 on the latest draft,
 * re-run a second after each change, without AI. The draft is saved first,
 * because the server checks what it has stored. `current` is true only while
 * the result matches the draft on screen. Used by the Dates and Check stages.
 */
export function useRuleCheck({ content, dreamId, token, language, saveDraft, dreamDate }: {
  content: PlanDraftContent;
  dreamId: number;
  token: string;
  language: string;
  saveDraft: () => Promise<void>;
  /** The dream's target date: moving it changes the rules' answer without changing the draft. */
  dreamDate?: string | null;
}) {
  const total = countNew(content).total;
  const version = `${JSON.stringify(content)}|${dreamDate ?? ''}`;
  const [report, setReport] = useState<{ version: string; issues: DraftIssue[] } | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState('');
  const saveRef = useRef(saveDraft);
  useEffect(() => {
    saveRef.current = saveDraft;
  });

  useEffect(() => {
    if (total === 0) {
      return undefined;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setChecking(true);
      setError('');
      void (async () => {
        try {
          await saveRef.current();
          const result = await checkPlanDraft(token, dreamId, language, true);
          if (!cancelled) {
            setReport({ version, issues: (result?.issues ?? []).filter((issue) => !issue.fromAi) });
          }
        } catch (checkError) {
          if (!cancelled) {
            setError(checkError instanceof Error ? tr(checkError.message) : tr('Unable to check your draft.'));
          }
        } finally {
          if (!cancelled) {
            setChecking(false);
          }
        }
      })();
    }, CHECK_DELAY_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [version, total, token, dreamId, language]);

  return {
    report,
    issues: report?.issues ?? [],
    current: report !== null && report.version === version,
    checking,
    error,
  };
}
