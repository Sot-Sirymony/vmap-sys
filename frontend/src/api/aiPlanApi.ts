import { apiClient } from './apiClient';
import type {
  AssistantApplied, AssistantChange, AssistantProposals, AssistantSection, AssistantSurface, SectionCheck,
  ClarifyField, ClarifyQuestion, Dream, DreamSuggestions, GoalRequest, PlanAsk, PlanClarification, GoalSuggestions, PlanAddResult, PlanCheck, PlanDraft, PlanDraftContent, PlanMetrics,
  PlanStage, SlippingStep, StepRescue, DraftCheck, DraftFix, HelpersAndRisks, PlanCapacity, StepSuggestions, TaskSuggestions, VisionAreaSuggestions, VisionStepRequest,
} from '../types/vision';

// V9 Phase 5: the AI vision area, dream, goal and plan builders. They only suggest; adding goes through the normal APIs.

export function suggestVisionAreas(token: string, language: string) {
  return apiClient<VisionAreaSuggestions>(`/ai/plan/vision-area-suggestions?language=${language}`, { method: 'POST', token });
}

export function suggestDreams(token: string, visionAreaId: number, language: string, stretch = false) {
  return apiClient<DreamSuggestions>(
    `/ai/plan/vision-areas/${visionAreaId}/dream-suggestions?stretch=${stretch}&language=${language}`,
    { method: 'POST', token },
  );
}

export function suggestGoals(token: string, dreamId: number, language: string, stretch = false, ask?: PlanAsk) {
  return apiClient<GoalSuggestions>(`/ai/plan/dreams/${dreamId}/goal-suggestions?stretch=${stretch}&language=${language}`, {
    method: 'POST',
    token,
    body: ask ? JSON.stringify(ask) : undefined,
  });
}

export function suggestSteps(token: string, goalId: number, language: string, ask?: PlanAsk) {
  return apiClient<StepSuggestions>(`/ai/plan/goals/${goalId}/step-suggestions?language=${language}`, {
    method: 'POST',
    token,
    body: ask ? JSON.stringify(ask) : undefined,
  });
}

export function checkPlan(token: string, dreamId: number, language: string) {
  return apiClient<PlanCheck>(`/ai/plan/dreams/${dreamId}/check?language=${language}`, { method: 'POST', token });
}

export function listSlippingSteps(token: string) {
  return apiClient<SlippingStep[]>('/ai/plan/slipping-steps', { token });
}

export function rescueStep(token: string, stepId: number, language: string) {
  return apiClient<StepRescue>(`/ai/plan/steps/${stepId}/rescue?language=${language}`, { method: 'POST', token });
}

// V11 Phase 1: what happens to suggestions after they are shown.

/** PB-FR-34: 1 thumbs up, -1 thumbs down, 0 clears. */
export function ratePlanAnswer(token: string, requestId: number, rating: number) {
  return apiClient<void>(`/ai/plan/requests/${requestId}/rating`, { method: 'PUT', token, body: JSON.stringify({ rating }) });
}

/** PB-FR-35: counts only, for suggestions saved through the normal create endpoints. */
export function recordPlanOutcome(token: string, requestId: number, added: number, edited: number) {
  return apiClient<void>(`/ai/plan/requests/${requestId}/outcome`, { method: 'POST', token, body: JSON.stringify({ added, edited }) });
}

/** PB-FR-31: everything kept, saved all together or not at all. */
export function addPlan(token: string, body: {
  requestId?: number | null;
  goals?: { goal: GoalRequest; edited: boolean }[];
  steps?: { step: VisionStepRequest; edited: boolean }[];
}) {
  return apiClient<PlanAddResult>('/ai/plan/add', { method: 'POST', token, body: JSON.stringify(body) });
}

/** PB-FR-32: archives what one add saved. */
export function undoPlan(token: string, body: { requestId?: number | null; goalIds?: number[]; stepIds?: number[]; taskIds?: number[] }) {
  return apiClient<void>('/ai/plan/undo', { method: 'POST', token, body: JSON.stringify(body) });
}

/** PB-FR-36: admins only. */
export function getPlanMetrics(token: string, days = 30) {
  return apiClient<PlanMetrics>(`/admin/ai/plan-metrics?days=${days}`, { token });
}

// V11 Phase 2: the Build my plan draft for one dream.

/** PB-FR-30: null when there is no unfinished draft (the server answers 204). */
export async function getPlanDraft(token: string, dreamId: number) {
  return (await apiClient<PlanDraft | undefined>(`/ai/plan/drafts/${dreamId}`, { token })) ?? null;
}

export function savePlanDraft(token: string, dreamId: number, stage: PlanStage, content: PlanDraftContent) {
  // Matched partners are shown for this session only; they are not part of the saved draft.
  const stored = { ...content, helpers: content.helpers?.map(({ matches: _matches, ...helper }) => helper) };
  return apiClient<PlanDraft>(`/ai/plan/drafts/${dreamId}`, { method: 'PUT', token, body: JSON.stringify({ stage, content: stored }) });
}

export function discardPlanDraft(token: string, dreamId: number) {
  return apiClient<void>(`/ai/plan/drafts/${dreamId}`, { method: 'DELETE', token });
}

/** PB-FR-31: every new item in the draft, saved together or not at all. The draft is then removed. */
export function addPlanDraft(token: string, dreamId: number) {
  return apiClient<PlanAddResult>(`/ai/plan/drafts/${dreamId}/add`, { method: 'POST', token });
}

/** Steps for a goal that exists only in the draft. */
export function suggestDraftGoalSteps(token: string, dreamId: number, language: string,
  goal: { title: string; description?: string | null; successCriteria?: string | null; targetDate?: string | null }, ask?: PlanAsk) {
  return apiClient<StepSuggestions>(`/ai/plan/dreams/${dreamId}/draft-step-suggestions?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify({
      title: goal.title, description: goal.description || undefined, successCriteria: goal.successCriteria || undefined,
      targetDate: goal.targetDate || undefined, ask,
    }),
  });
}

// V11 Phase 3: clarifying the dream.

/** PB-FR-08: the next question about the dream, one at a time; done when it is clear enough or after three answers. */
export function clarifyDream(token: string, dreamId: number, language: string, answers: PlanClarification[]) {
  return apiClient<ClarifyQuestion>(`/ai/plan/dreams/${dreamId}/clarify?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify({ answers: answers.slice(0, 3) }),
  });
}

/** PB-FR-10: saves one answer into the dream, only when the participant chooses to. */
export function saveClarifiedField(token: string, dreamId: number, field: ClarifyField, value: string) {
  return apiClient<Dream>(`/ai/plan/dreams/${dreamId}/clarified-field`, { method: 'PUT', token, body: JSON.stringify({ field, value }) });
}

// V11 Phase 4: the task builder (PB-FR-14).

export function suggestTasks(token: string, stepId: number, language: string, ask?: PlanAsk) {
  return apiClient<TaskSuggestions>(`/ai/plan/steps/${stepId}/task-suggestions?language=${language}`, {
    method: 'POST',
    token,
    body: ask ? JSON.stringify(ask) : undefined,
  });
}

export function suggestDraftStepTasks(token: string, dreamId: number, language: string, step: {
  goalTitle?: string; goalTargetDate?: string | null; stepTitle: string; stepDescription?: string | null; stepTargetDate?: string | null;
}, ask?: PlanAsk) {
  return apiClient<TaskSuggestions>(`/ai/plan/dreams/${dreamId}/draft-task-suggestions?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify({
      goalTitle: step.goalTitle || undefined, goalTargetDate: step.goalTargetDate || undefined, stepTitle: step.stepTitle,
      stepDescription: step.stepDescription || undefined, stepTargetDate: step.stepTargetDate || undefined, ask,
    }),
  });
}

// V11 Phase 5: helpers, risks and resources (PB-FR-23 to PB-FR-26, PB-FR-40).

export type PlanItemRef = { key: string; kind: 'GOAL' | 'STEP'; title: string; resources?: string | null; goalKey?: string };

export function suggestHelpersAndRisks(token: string, dreamId: number, language: string,
  body: { items: PlanItemRef[]; useFears: boolean; ask?: PlanAsk }) {
  return apiClient<HelpersAndRisks>(`/ai/plan/dreams/${dreamId}/helpers-and-risks?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify(body),
  });
}

// V11 Phase 6: the draft check, its fixes and weekly capacity.

/** PB-FR-28: checks the saved draft; rule checks work without AI. Save the draft first. */
export function checkPlanDraft(token: string, dreamId: number, language: string) {
  return apiClient<DraftCheck>(`/ai/plan/dreams/${dreamId}/draft-check?language=${language}`, { method: 'POST', token });
}

/** PB-FR-29: a proposed change for one AI issue; nothing is saved. */
export function fixPlanDraft(token: string, dreamId: number, language: string, body: { itemKey: string; kind: string; problem?: string }) {
  return apiClient<DraftFix>(`/ai/plan/dreams/${dreamId}/draft-fix?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify(body),
  });
}

/** PB-FR-16 */
export function getPlanCapacity(token: string) {
  return apiClient<PlanCapacity>('/ai/plan/capacity', { token });
}

export function savePlanCapacity(token: string, hoursPerWeek: number | null) {
  return apiClient<PlanCapacity>('/ai/plan/capacity', { method: 'PUT', token, body: JSON.stringify({ hoursPerWeek }) });
}

// V11 Phase 7: the plan assistant (PB-FR-41 to PB-FR-50). Its chat goes through streamAiMessage with a section.

export type AssistantTopic = { surface: AssistantSurface; section: AssistantSection; itemRef?: string | null };

/** PB-FR-49: the section's rule-based problems; never uses AI. */
export function checkAssistantSection(token: string, dreamId: number, topic: AssistantTopic) {
  return apiClient<SectionCheck>(`/ai/plan/dreams/${dreamId}/assistant/section-check`, {
    method: 'POST',
    token,
    body: JSON.stringify(topic),
  });
}

/** PB-FR-46: change cards for one section; nothing is saved. */
export function proposeAssistantChanges(token: string, dreamId: number, language: string, topic: AssistantTopic) {
  return apiClient<AssistantProposals>(`/ai/plan/dreams/${dreamId}/assistant/proposals?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify(topic),
  });
}

/** PB-FR-46: saves one card on the Plan page through the normal checks. */
export function applyAssistantChange(token: string, dreamId: number, change: AssistantChange, requestId: number | null | undefined,
  edited: boolean) {
  return apiClient<AssistantApplied>(`/ai/plan/dreams/${dreamId}/assistant/apply`, {
    method: 'POST',
    token,
    body: JSON.stringify({ ...change, requestId: requestId ?? null, edited }),
  });
}
