import type { AssistantSection } from '../../../types/vision';

/**
 * V11 Phase 7 (PB-FR-44, PB-FR-45, PB-FR-49): the program's own help for each
 * plan section. It is shown with or without AI: what the section needs, why it
 * matters, guiding questions (from the coaching behaviour in CLAUDE.md), and
 * three quick actions that send a ready-made question to the assistant.
 * English here; the Khmer is in i18n/km.ts.
 */
export type SectionHelp = {
  label: string;
  needs: string;
  why: string;
  questions: string[];
  quickActions: [string, string, string];
};

const STATEMENT_QUESTIONS = [
  'What exactly do you want to achieve?',
  'Why does this matter?',
  'What will success look like?',
  'What is your target date?',
];

const GOALS: SectionHelp = {
  label: 'Goals',
  needs: 'The few major results this dream needs, each specific and measurable, with a date.',
  why: 'Goals turn a dream into results you can check.',
  questions: ['What major results does this dream need?', 'How will you know each one is reached?', 'Which goal comes first?'],
  quickActions: ['Is this goal specific enough?', 'Make my goal measurable', 'What goal is missing?'],
};

const STEPS: SectionHelp = {
  label: 'Steps',
  needs: 'For each goal, the actions that get it done, in order.',
  why: 'Steps make a goal doable.',
  questions: ['What steps are required?', 'What must happen first?', 'What can block this goal?', 'Who can help?'],
  quickActions: ['What must happen first?', 'Is this step too big?', 'What step is missing?'],
};

export const SECTION_HELP: Record<AssistantSection, SectionHelp> = {
  WHOLE: {
    label: 'Whole plan',
    needs: 'A plan turns one dream into goals, steps with owners and dates, what is needed, and who can help.',
    why: 'Working through it in order means nothing important is forgotten.',
    questions: ['Which part of the plan is still empty?', 'What must happen first?', 'Who can help?'],
    quickActions: ['What should I work on next in this plan?', 'Check my plan for gaps', 'Explain how this page works'],
  },
  STATEMENT: {
    label: 'Dream statement',
    needs: 'Why this dream matters to you, and what success looks like.',
    why: 'A clear why keeps you going; a clear picture of success tells you when you are done.',
    questions: STATEMENT_QUESTIONS,
    quickActions: ['Help me write why this matters', 'What would success look like?', 'Is my dream clear enough?'],
  },
  GOALS,
  STEPS,
  DATES: {
    label: 'Owners and dates',
    needs: 'Every step needs an owner and a realistic date.',
    why: 'Without an owner and a date, a step tends to wait forever.',
    questions: ['Who will do it? It can be you.', 'When can it really be done?', 'Does it fit before the goal\'s date?'],
    quickActions: ['Are these dates realistic?', 'Which step should I date first?', 'Help me spread the dates'],
  },
  RESOURCES: {
    label: 'Resources needed',
    needs: 'What is missing, such as money, tools, skills or time, recorded as obstacles.',
    why: 'Naming what is missing early lets you plan around it.',
    questions: ['What do you need that you do not have yet?', 'What could get in the way?', 'What would solve it?'],
    quickActions: ['What could get in the way?', 'What resources does this plan need?', 'How do I handle a money gap?'],
  },
  PEOPLE: {
    label: 'People',
    needs: 'At least one partner or mentor linked to this dream or one of its goals.',
    why: 'Big dreams need help, and the right person can save months.',
    questions: [
      'Who already knows how to do this?',
      'What help do you need: knowledge, skill, time, money, motivation or a decision?',
      'What exactly would you ask them for?',
    ],
    quickActions: ['What kind of helper do I need?', 'How do I ask a mentor for help?', 'Who could help with this goal?'],
  },
  MILESTONES: {
    label: 'Milestones',
    needs: 'Results you have reached on the way to this dream, with the date.',
    why: 'Seeing your progress keeps you motivated.',
    questions: ['What have you finished lately?', 'What would be worth celebrating next?'],
    quickActions: ['What could count as a milestone?', 'How am I doing on this dream?', 'What should my next milestone be?'],
  },
  CLARIFY: {
    label: 'Clarify',
    needs: 'A dream clear enough to plan: what, why, what success looks like and when.',
    why: 'A vague dream gives vague goals. A few clear answers make every later stage easier.',
    questions: STATEMENT_QUESTIONS,
    quickActions: ['Is my dream clear enough?', 'Help me write why this matters', 'What would success look like?'],
  },
  TASKS: {
    label: 'Tasks',
    needs: 'Complex steps broken into small tasks, each with an owner, a date and a priority.',
    why: 'A task you can finish in one sitting is easy to start.',
    questions: ['Is this step simple or complex?', 'Can it be done in one action?', 'Does it need several tasks?'],
    quickActions: ['Break this step into tasks', 'Is this step complex?', 'Which task comes first?'],
  },
  HELPERS: {
    label: 'Helpers and risks',
    needs: 'Who can help with each step, and what could get in the way.',
    why: 'Knowing the risks and the right helper early stops a step from getting stuck.',
    questions: [
      'What is missing: knowledge, skill, time, money, a decision, a partner or motivation?',
      'Who has what is missing?',
      'What could get in the way?',
    ],
    quickActions: ['Who could help with this?', 'What could get in the way?', 'How do I ask for help?'],
  },
  REVIEW: {
    label: 'Check and add',
    needs: 'A last look: every new item has an owner and a date that fits, and complex steps have tasks.',
    why: 'Fixing problems now is easier than after the plan is added.',
    questions: ['Is anything missing an owner or a date?', 'Do the dates fit your week?', 'Is anything vague?'],
    quickActions: ['Explain these problems', 'What should I fix first?', 'Is my plan ready to add?'],
  },
};

/** The change card's heading, by kind and field. */
export function changeLabel(kind: string, field?: string | null) {
  if (kind === 'ADD_STEP') return 'Add a step';
  if (kind === 'ADD_TASK') return 'Add a task';
  if (kind === 'ADD_RISK') return 'Add a risk';
  switch (field) {
    case 'TITLE': return 'Change the title';
    case 'SUCCESS': return 'Change what success looks like';
    case 'WHY': return 'Change why it matters';
    case 'DATE': return 'Change the date';
    case 'OWNER': return 'Change the owner';
    default: return 'Change';
  }
}
