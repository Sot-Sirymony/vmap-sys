import { tr } from '../i18n/translate';
import type { PartnerMotivator, StyleAction, WorkStyleArchetype, WorkStyleProfile } from '../types/vision';

/** AH-FR-71: where each practice is done. */
export const STYLE_ACTIONS: Record<StyleAction, { path: string; page: string }> = {
  UNDATED_STEPS: { path: '/steps/undated', page: 'Steps without a date' },
  STEPS: { path: '/steps', page: 'Steps' },
  TASKS: { path: '/tasks', page: 'Tasks' },
  PARTNERS: { path: '/partners', page: 'Partners' },
  TODAY: { path: '/today', page: 'Today' },
  PLAN: { path: '/plan', page: 'Plan' },
};

/**
 * V15 (AH-FR-72): three practices per style, used without AI. Each uses the
 * style's strength or covers its likely blind spot. To be approved by the lead
 * coach before release.
 */
export const STYLE_PRACTICES: Record<WorkStyleArchetype, { text: string; action: StyleAction; habit: string }[]> = {
  DRIVER: [
    { text: 'Give each of your next three steps an owner and a date before you start them.', action: 'UNDATED_STEPS', habit: 'Date before I start' },
    { text: 'Before you mark a step done, check one detail you would usually skip.', action: 'STEPS', habit: 'One detail check' },
    { text: 'Ask a partner who works slowly and carefully to look over your plan.', action: 'PARTNERS', habit: 'Ask for a careful eye' },
  ],
  CONNECTOR: [
    { text: 'Turn today\'s best idea into one task with a date.', action: 'TASKS', habit: 'Idea to dated task' },
    { text: 'Each morning, pick one routine task to finish before anything new.', action: 'TODAY', habit: 'Finish one first' },
    { text: 'Ask a partner to check in with you on one step each week.', action: 'PARTNERS', habit: 'Weekly check-in' },
  ],
  STEADIER: [
    { text: 'Set a decision date on one step that is waiting.', action: 'STEPS', habit: 'Decide by a date' },
    { text: 'Pick one small step and start it today, even if it feels early.', action: 'TODAY', habit: 'Start one today' },
    { text: 'Write down one request you have been putting off, and send it this week.', action: 'PARTNERS', habit: 'Send one ask' },
  ],
  PLANNER: [
    { text: 'Give your next step a start date this week, even if the plan is not perfect.', action: 'UNDATED_STEPS', habit: 'Start before perfect' },
    { text: 'Limit planning a step to one session, then act on it.', action: 'PLAN', habit: 'One planning session' },
    { text: 'When you ask a partner for help, open with how the work helps people, then the details.', action: 'PARTNERS', habit: 'People first' },
  ],
};

/** V15 (AH-FR-60): a fixed tip for each primary motivator, used without AI. */
export const MOTIVATOR_TIPS: Record<PartnerMotivator, string> = {
  FINANCIAL_GAIN: 'Say clearly what they gain: time, money or opportunity.',
  AVOIDING_LOSS: 'Say what could be lost or missed if nothing happens.',
  SHARED_VISION: 'Show how the work serves a vision you both care about.',
  RECOGNITION: 'Say how their part will be seen and credited.',
  OTHER: 'Ask what matters most to them, and build the message around it.',
};

/**
 * V15 (AH-FR-70): without AI, one rule sentence for each axis margin, from
 * the participant's own pole counts.
 */
export function axisSentences(profile: WorkStyleProfile): string[] {
  const sentences: string[] = [];
  const fast = profile.paceFastScore ?? 0;
  const deliberate = profile.paceDeliberateScore ?? 0;
  const task = profile.focusTaskScore ?? 0;
  const people = profile.focusPeopleScore ?? 0;
  if (fast + deliberate > 0) {
    sentences.push(Math.abs(fast - deliberate) <= 2
      ? tr('Pace was close: {fast} fast and {deliberate} deliberate picks, so you use both.', { fast, deliberate })
      : tr('Pace is a clear lean: {count} of {total} picks were {side}.', {
        count: Math.max(fast, deliberate), total: fast + deliberate, side: fast > deliberate ? tr('fast') : tr('deliberate'),
      }));
  }
  if (task + people > 0) {
    sentences.push(Math.abs(task - people) <= 2
      ? tr('Focus was close: {task} on the task and {people} on people, so you use both.', { task, people })
      : tr('Focus is a clear lean: {count} of {total} picks were on {side}.', {
        count: Math.max(task, people), total: task + people, side: task > people ? tr('the task') : tr('people'),
      }));
  }
  return sentences;
}

/**
 * V15 (AH-FR-74): four short forced-choice questions about how a partner
 * usually works, two on pace and two on focus. The answers suggest a style by
 * rule, with no AI; it is only ever the participant's estimate.
 */
export const ESTIMATE_QUESTIONS: { axis: 'pace' | 'focus'; a: string; b: string }[] = [
  { axis: 'pace', a: 'They decide quickly.', b: 'They take time to decide.' },
  { axis: 'pace', a: 'They like to start right away.', b: 'They like to plan before starting.' },
  { axis: 'focus', a: 'They talk mostly about the work and results.', b: 'They talk mostly about people and how they feel.' },
  { axis: 'focus', a: 'They prefer facts and details.', b: 'They prefer stories and relationships.' },
];

/** a = fast or task, b = deliberate or people. A tie on an axis leans fast, or task. */
export function estimateStyle(answers: ('a' | 'b')[]): WorkStyleArchetype {
  const count = (axis: 'pace' | 'focus') => ESTIMATE_QUESTIONS
    .map((question, index) => (question.axis === axis && answers[index] === 'a' ? 1 : 0) as number)
    .reduce((sum, value) => sum + value, 0);
  const fast = count('pace') >= 1;
  const task = count('focus') >= 1;
  if (fast) {
    return task ? 'DRIVER' : 'CONNECTOR';
  }
  return task ? 'PLANNER' : 'STEADIER';
}
