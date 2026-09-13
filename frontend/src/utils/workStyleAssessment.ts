import type { WorkStyleArchetype, WorkStyleAssessmentRequest } from '../types/vision';

// FR-49.1: original wording, 8 items per axis. Each item is a forced choice
// between the two statements; "a" always maps to the first-named pole.
export type QuizAxis = 'pace' | 'focus';
export type QuizItem = {
  axis: QuizAxis;
  a: string;
  b: string;
};

// pace axis: a = fast-deciding, b = deliberate.
// focus axis: a = task-oriented, b = people-oriented.
export const QUIZ_ITEMS: QuizItem[] = [
  { axis: 'pace', a: "I'd rather decide quickly and adjust later", b: "I'd rather gather more information before deciding" },
  { axis: 'pace', a: "A rough plan that gets moving beats a perfect plan that's still being drafted", b: "A plan worth having is worth getting right before we start" },
  { axis: 'pace', a: 'I trust my gut in the moment', b: "I trust a process I've thought through" },
  { axis: 'pace', a: 'Waiting to act feels like wasted time', b: 'Acting without enough thought feels risky' },
  { axis: 'pace', a: "I'd rather try something and learn from the result", b: "I'd rather map out the risks before trying" },
  { axis: 'pace', a: 'Momentum matters more to me than precision', b: 'Precision matters more to me than speed' },
  { axis: 'pace', a: 'I make most decisions in the room, not after', b: 'I usually want to sleep on a decision' },
  { axis: 'pace', a: 'Long deliberation frustrates me', b: 'Rushed decisions worry me' },
  { axis: 'focus', a: 'I focus first on what needs to get done', b: 'I focus first on how the people involved are doing' },
  { axis: 'focus', a: 'Results are the clearest sign of a good week', b: 'Strong relationships are the clearest sign of a good week' },
  { axis: 'focus', a: "I'd rather solve the problem than talk through how everyone feels about it", b: "I'd rather talk it through so everyone feels heard" },
  { axis: 'focus', a: 'Efficiency is what I notice first in a process', b: 'Tone is what I notice first in a room' },
  { axis: 'focus', a: 'I measure progress by what got checked off', b: 'I measure progress by how the team is holding up' },
  { axis: 'focus', a: "I'd rather work alone and deliver", b: "I'd rather work with others and connect" },
  { axis: 'focus', a: 'Getting to the point matters more to me than the small talk first', b: 'The small talk first matters to me as much as the point' },
  { axis: 'focus', a: 'I judge a meeting by its outcomes', b: 'I judge a meeting by how people left it' },
];

/** FR-49.1: turns 16 'a'/'b' picks (one per QUIZ_ITEMS entry) into the four raw scores the backend stores. */
export function scoreAssessment(answers: ('a' | 'b')[]): WorkStyleAssessmentRequest {
  let paceFastScore = 0;
  let paceDeliberateScore = 0;
  let focusTaskScore = 0;
  let focusPeopleScore = 0;
  QUIZ_ITEMS.forEach((item, index) => {
    const answer = answers[index];
    if (item.axis === 'pace') {
      if (answer === 'a') paceFastScore += 1; else paceDeliberateScore += 1;
    } else {
      if (answer === 'a') focusTaskScore += 1; else focusPeopleScore += 1;
    }
  });
  return { paceFastScore, paceDeliberateScore, focusTaskScore, focusPeopleScore };
}

export const workStyleArchetypeLabels: Record<WorkStyleArchetype, string> = {
  DRIVER: 'Driver',
  CONNECTOR: 'Connector',
  STEADIER: 'Steadier',
  PLANNER: 'Planner',
};

// FR-49.5: original guidance copy per archetype — strengths, a likely blind
// spot, and one communication tip. Written for this product.
export const ARCHETYPE_GUIDANCE: Record<WorkStyleArchetype, { strengths: string; blindSpot: string; tip: string }> = {
  DRIVER: {
    strengths: 'Decides fast and drives work toward a concrete result without waiting for perfect conditions.',
    blindSpot: 'Detail and follow-through can get skipped in the rush to the next thing.',
    tip: 'Lead with the bottom line and the decision you need — save the background for if they ask.',
  },
  CONNECTOR: {
    strengths: 'Builds momentum and relationships quickly, and gets people energized to start.',
    blindSpot: 'Initial energy can outrun follow-through once the work turns routine.',
    tip: 'Pair enthusiasm with one specific, dated commitment so momentum has something to land on.',
  },
  STEADIER: {
    strengths: 'Patient, loyal, and steady — the person work can depend on staying done once it is.',
    blindSpot: 'Can be slow to push a decision or initiative forward under time pressure.',
    tip: 'Acknowledge the relationship first, then be direct about the ask — avoid burying it.',
  },
  PLANNER: {
    strengths: 'Systematic and detail-oriented, catching what a faster pass would miss.',
    blindSpot: 'Deliberation can run long, and the human side of a request can feel like an afterthought.',
    tip: 'Bring the plan and the data, but expect to be pushed on committing to a date sooner than feels ready.',
  },
};

// FR-49.4: who complements this archetype's likely blind spot. A Driver's
// gap is task follow-through, so Planner (same focus, opposite pace) is the
// one complement offered. A Connector's gap is broader (both structure and
// follow-through), so both deliberate-pace archetypes are offered.
const COMPLEMENTARY_ARCHETYPES: Record<WorkStyleArchetype, WorkStyleArchetype[]> = {
  DRIVER: ['PLANNER'],
  CONNECTOR: ['STEADIER', 'PLANNER'],
  STEADIER: ['DRIVER'],
  PLANNER: ['CONNECTOR'],
};

/** FR-49.4: null when the user has no profile — never an error, just nothing to suggest. */
export function getComplementarySuggestion(dominant: WorkStyleArchetype | null): WorkStyleArchetype[] | null {
  return dominant ? COMPLEMENTARY_ARCHETYPES[dominant] : null;
}
