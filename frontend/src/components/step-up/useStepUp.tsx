import { useRef, useState, type ReactNode } from 'react';
import { ApiError } from '../../api/apiClient';
import type { StepUpAction } from '../../types/vision';
import { StepUpDialog } from './StepUpDialog';

/** Thrown when the person closes "Verify it's you": the page shows nothing. */
export class StepUpCancelled extends Error {
  constructor() {
    super('Verification cancelled.');
    this.name = 'StepUpCancelled';
  }
}

export function isStepUpCancelled(error: unknown): boolean {
  return error instanceof StepUpCancelled;
}

type Pending = {
  /** A fresh dialog for every request, so a second code never reuses the first one's state. */
  id: number;
  action: StepUpAction;
  target?: string;
  resolve: (challengeId: number | undefined) => void;
  reject: (error: Error) => void;
};

/**
 * V20 (SV-FR-24, SV-FR-25): runs a protected call. If the server answers
 * STEP_UP_REQUIRED, it opens "Verify it's you" and, once the code is right,
 * runs the same call again, once, with the verified code; the page keeps
 * everything the person typed. Render `dialog` somewhere on the page.
 */
export function useStepUp(token: string | null): {
  protect: <T>(action: StepUpAction | null, call: (stepUp?: number) => Promise<T>, target?: string) => Promise<T>;
  dialog: ReactNode;
} {
  const [pending, setPending] = useState<Pending | null>(null);
  const requests = useRef(0);

  /** action null: whichever code the server names, for a change whose code depends on what it does (SV-FR-06, SV-FR-08). */
  async function protect<T>(action: StepUpAction | null, call: (stepUp?: number) => Promise<T>, target?: string): Promise<T> {
    try {
      return await call(undefined);
    } catch (error) {
      // Only a request for this action's code: calls can be nested for an action that needs two codes (SV-FR-04).
      const named = error instanceof ApiError ? error.action as StepUpAction | undefined : undefined;
      const wanted = error instanceof ApiError && error.code === 'STEP_UP_REQUIRED' && (action === null ? Boolean(named) : !named || named === action);
      const dialogAction = action ?? named;
      if (!wanted || !token || !dialogAction) throw error;
      const challengeId = await new Promise<number | undefined>((resolve, reject) => {
        requests.current += 1;
        setPending({ id: requests.current, action: dialogAction, target, resolve, reject });
      });
      return call(challengeId);
    }
  }

  const dialog = pending && token ? (
    <StepUpDialog key={pending.id} token={token} action={pending.action} target={pending.target}
      onVerified={(challengeId) => {
        setPending(null);
        pending.resolve(challengeId);
      }}
      onCancel={() => {
        setPending(null);
        pending.reject(new StepUpCancelled());
      }} />
  ) : null;

  return { protect, dialog };
}
