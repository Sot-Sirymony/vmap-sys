import { useCallback, useState } from 'react';
import { EnergyOvercommitDialog } from '../components/common/EnergyOvercommitDialog';
import { checkDrainOvercommit, type OvercommitResult } from '../utils/energyBudgetNudge';
import type { TaskItem } from '../types/vision';

/**
 * FR-34.4: shared driver for the over-commitment coaching prompt. A page calls
 * `maybeNudge` after a task save and renders `dialog`; the prompt appears only
 * when a DRAIN task pushes its week over the threshold, and never blocks the
 * save (FR-34.5). One place so the Tasks Board and the Vision Map tree behave
 * identically.
 */
export function useEnergyOvercommitNudge() {
  const [result, setResult] = useState<OvercommitResult | null>(null);

  const maybeNudge = useCallback(
    (dueDate: string | undefined, energyDemand: string | undefined, savedTaskId: number | null, tasks: TaskItem[]) => {
      setResult(checkDrainOvercommit(dueDate, energyDemand, savedTaskId, tasks));
    },
    [],
  );

  const dialog = (
    <EnergyOvercommitDialog
      open={result !== null}
      weekStart={result?.weekStart ?? ''}
      weekEnd={result?.weekEnd ?? ''}
      candidates={result?.candidates ?? []}
      onClose={() => setResult(null)}
    />
  );

  return { maybeNudge, dialog };
}
