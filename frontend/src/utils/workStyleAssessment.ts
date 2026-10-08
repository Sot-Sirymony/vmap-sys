import type { WorkStyleArchetype, WorkStyleAssessmentRequest } from '../types/vision';

// FR-49.1: original wording, 8 items per axis. Each item is a forced choice
// between the two statements; "a" always maps to the first-named pole.
export type QuizAxis = 'pace' | 'focus';
export type QuizItem = {
  axis: QuizAxis;
  a: string;
  b: string;
  /** Khmer translation of `a`, shown under the English. */
  aKm: string;
  /** Khmer translation of `b`, shown under the English. */
  bKm: string;
};

// pace axis: a = fast-deciding, b = deliberate.
// focus axis: a = task-oriented, b = people-oriented.
export const QUIZ_ITEMS: QuizItem[] = [
  { axis: 'pace', a: "I'd rather decide quickly and adjust later", b: "I'd rather gather more information before deciding", aKm: 'ខ្ញុំចូលចិត្តសម្រេចចិត្តឱ្យរហ័ស ហើយកែតម្រូវនៅពេលក្រោយ', bKm: 'ខ្ញុំចូលចិត្តប្រមូលព័ត៌មានបន្ថែមសិន មុននឹងសម្រេចចិត្ត' },
  { axis: 'pace', a: "A rough plan that gets moving beats a perfect plan that's still being drafted", b: "A plan worth having is worth getting right before we start", aKm: 'ផែនការសង្ខេបដែលអាចចាប់ផ្តើមអនុវត្តបានភ្លាម ប្រសើរជាងផែនការល្អឥតខ្ចោះដែលនៅតែកំពុងព្រាង', bKm: 'ផែនការដែលមានតម្លៃ គួរតែរៀបចំឱ្យបានត្រឹមត្រូវ មុនពេលយើងចាប់ផ្តើម' },
  { axis: 'pace', a: 'I trust my gut in the moment', b: "I trust a process I've thought through", aKm: 'ខ្ញុំជឿលើវិចារណញាណរបស់ខ្ញុំនៅពេលនោះ', bKm: 'ខ្ញុំជឿលើដំណើរការដែលខ្ញុំបានគិតពិចារណារួចហើយ' },
  { axis: 'pace', a: 'Waiting to act feels like wasted time', b: 'Acting without enough thought feels risky', aKm: 'ការរង់ចាំមុននឹងធ្វើសកម្មភាព ហាក់ដូចជាខាតពេលវេលា', bKm: 'ការធ្វើសកម្មភាពដោយមិនបានគិតគ្រប់គ្រាន់ ហាក់ដូចជាមានហានិភ័យ' },
  { axis: 'pace', a: "I'd rather try something and learn from the result", b: "I'd rather map out the risks before trying", aKm: 'ខ្ញុំចូលចិត្តសាកល្បងធ្វើ ហើយរៀនពីលទ្ធផល', bKm: 'ខ្ញុំចូលចិត្តវិភាគហានិភ័យឱ្យច្បាស់សិន មុននឹងសាកល្បង' },
  { axis: 'pace', a: 'Momentum matters more to me than precision', b: 'Precision matters more to me than speed', aKm: 'សម្រាប់ខ្ញុំ សន្ទុះនៃការងារសំខាន់ជាងភាពជាក់លាក់', bKm: 'សម្រាប់ខ្ញុំ ភាពជាក់លាក់សំខាន់ជាងល្បឿន' },
  { axis: 'pace', a: 'I make most decisions in the room, not after', b: 'I usually want to sleep on a decision', aKm: 'ខ្ញុំសម្រេចចិត្តភាគច្រើននៅក្នុងកិច្ចប្រជុំតែម្តង មិនមែនបន្ទាប់ពីនោះទេ', bKm: 'ជាធម្មតា ខ្ញុំចង់ចំណាយពេលមួយយប់គិតសិន មុននឹងសម្រេចចិត្ត' },
  { axis: 'pace', a: 'Long deliberation frustrates me', b: 'Rushed decisions worry me', aKm: 'ការពិចារណាយូរពេក ធ្វើឱ្យខ្ញុំធុញថប់', bKm: 'ការសម្រេចចិត្តដោយប្រញាប់ប្រញាល់ ធ្វើឱ្យខ្ញុំព្រួយបារម្ភ' },
  { axis: 'focus', a: 'I focus first on what needs to get done', b: 'I focus first on how the people involved are doing', aKm: 'ខ្ញុំផ្តោតជាមុនលើអ្វីដែលត្រូវធ្វើឱ្យរួចរាល់', bKm: 'ខ្ញុំផ្តោតជាមុនលើស្ថានភាពរបស់មនុស្សដែលពាក់ព័ន្ធ' },
  { axis: 'focus', a: 'Results are the clearest sign of a good week', b: 'Strong relationships are the clearest sign of a good week', aKm: 'លទ្ធផលការងារ គឺជាសញ្ញាច្បាស់បំផុតនៃសប្តាហ៍ដ៏ល្អមួយ', bKm: 'ទំនាក់ទំនងដ៏រឹងមាំ គឺជាសញ្ញាច្បាស់បំផុតនៃសប្តាហ៍ដ៏ល្អមួយ' },
  { axis: 'focus', a: "I'd rather solve the problem than talk through how everyone feels about it", b: "I'd rather talk it through so everyone feels heard", aKm: 'ខ្ញុំចូលចិត្តដោះស្រាយបញ្ហាតែម្តង ជាជាងពិភាក្សាអំពីអារម្មណ៍របស់គ្រប់គ្នាចំពោះបញ្ហានោះ', bKm: 'ខ្ញុំចូលចិត្តពិភាក្សាគ្នាឱ្យបានល្អិតល្អន់ ដើម្បីឱ្យគ្រប់គ្នាមានអារម្មណ៍ថាត្រូវបានគេស្តាប់' },
  { axis: 'focus', a: 'Efficiency is what I notice first in a process', b: 'Tone is what I notice first in a room', aKm: 'ប្រសិទ្ធភាព គឺជាអ្វីដែលខ្ញុំកត់សម្គាល់មុនគេនៅក្នុងដំណើរការការងារ', bKm: 'បរិយាកាស គឺជាអ្វីដែលខ្ញុំកត់សម្គាល់មុនគេនៅពេលចូលក្នុងបន្ទប់' },
  { axis: 'focus', a: 'I measure progress by what got checked off', b: 'I measure progress by how the team is holding up', aKm: 'ខ្ញុំវាស់វឌ្ឍនភាពតាមការងារដែលបានបញ្ចប់', bKm: 'ខ្ញុំវាស់វឌ្ឍនភាពតាមស្ថានភាពរបស់ក្រុមការងារ' },
  { axis: 'focus', a: "I'd rather work alone and deliver", b: "I'd rather work with others and connect", aKm: 'ខ្ញុំចូលចិត្តធ្វើការម្នាក់ឯង ហើយផ្តល់លទ្ធផល', bKm: 'ខ្ញុំចូលចិត្តធ្វើការជាមួយអ្នកដទៃ និងកសាងទំនាក់ទំនង' },
  { axis: 'focus', a: 'Getting to the point matters more to me than the small talk first', b: 'The small talk first matters to me as much as the point', aKm: 'សម្រាប់ខ្ញុំ ការនិយាយចំចំណុចតែម្តង សំខាន់ជាងការជជែកលេងមុន', bKm: 'សម្រាប់ខ្ញុំ ការជជែកលេងបន្តិចមុន សំខាន់ដូចគ្នានឹងខ្លឹមសារសំខាន់ដែរ' },
  { axis: 'focus', a: 'I judge a meeting by its outcomes', b: 'I judge a meeting by how people left it', aKm: 'ខ្ញុំវាយតម្លៃកិច្ចប្រជុំមួយតាមលទ្ធផលរបស់វា', bKm: 'ខ្ញុំវាយតម្លៃកិច្ចប្រជុំមួយតាមអារម្មណ៍របស់មនុស្សនៅពេលចាកចេញ' },
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
