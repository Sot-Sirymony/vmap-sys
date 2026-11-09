import type { AssistantPage } from '../../../types/vision';

/**
 * V15 Phase 1 (AH-FR-04, AH-FR-10, AH-FR-12): the program's own help for each
 * area of the five pages. It is shown whether or not AI is on, so no page loses
 * help when AI is off, over budget or not consented. Every string goes through
 * tr() where it is shown, and has a Khmer entry in km.ts.
 */
export type AreaHelp = {
  label: string;
  needs: string;
  why: string;
  questions: string[];
  quickActions: [string, string, string];
};

/** The kind of area, read from the start of the area key: "field:hook" is a field, "question:3" a question. */
export type AreaKind =
  | 'page' | 'list' | 'item' | 'form' | 'message' | 'field' | 'statement' | 'result' | 'question' | 'exercise' | 'partner';

export function areaKind(area: string): AreaKind {
  const kind = area.split(':')[0];
  const known: AreaKind[] = ['page', 'list', 'item', 'form', 'message', 'field', 'statement', 'result', 'question', 'exercise', 'partner'];
  return (known as string[]).includes(kind) ? (kind as AreaKind) : 'page';
}

const MODULES_LIST: AreaHelp = {
  label: 'the Modules list',
  needs: 'Fifteen short lessons in three stages. Finishing one unlocks the next.',
  why: 'Each module removes one barrier or builds one habit the method needs, so the order matters.',
  questions: [
    'Which barrier holds you back most right now?',
    'Which unlocked module have you been putting off?',
    'What did the last module change in your plan?',
  ],
  quickActions: ['Which module should I do next?', 'What is this program about?', 'How far have I come?'],
};

const MODULE_EXERCISE: AreaHelp = {
  label: 'this module',
  needs: 'Read the idea, then do the exercise in your own words. Some modules are done on another page.',
  why: 'The exercise turns the idea into something real in your own plan.',
  questions: [
    'What does this idea mean for your top dream?',
    'What is one real example from your own life?',
    'What would you do differently this week because of it?',
  ],
  quickActions: ['Explain this module', 'Help me start', 'Check my answer'],
};

const ASSESSMENT_STATEMENT: AreaHelp = {
  label: 'this statement',
  needs: 'Pick the number that fits how you usually act, from 1 (rarely true) to 5 (almost always true).',
  why: 'An honest score shows where to start. Only you can judge it; there is no right answer.',
  questions: [
    'Think of the last month: how often was this true?',
    'What did you actually do, not what you meant to do?',
    'Would someone who knows you well agree?',
  ],
  quickActions: ['What does this statement mean?', 'Explain my result', 'What should I practise this week?'],
};

const ASSESSMENT_RESULT: AreaHelp = {
  label: 'your result',
  needs: 'Your scores for the six barriers, and how they changed since your first take.',
  why: 'A low score points to the module and the practice that will help most.',
  questions: [
    'Which score surprised you?',
    'Which barrier, if it moved, would change the most for you?',
    'What did you do between takes that made a difference?',
  ],
  quickActions: ['Explain my result', 'What should I practise this week?', 'What does this statement mean?'],
};

const OBSTACLE_ITEM: AreaHelp = {
  label: 'this obstacle',
  needs: 'A clear title, what is in the way, its type and severity, the root cause, and one thing to try first.',
  why: 'A named obstacle with a root cause and a first step stops blocking your plan.',
  questions: [
    'What exactly is in the way?',
    'Why does it happen? And why does that happen?',
    'What is the smallest thing you could try this week?',
    'Who could help with this kind of gap?',
  ],
  quickActions: ['Help me understand this obstacle', 'What could I try?', 'Who could help?'],
};

const OBSTACLE_LIST: AreaHelp = {
  label: 'your obstacles',
  needs: 'Every blocker you have recorded, with its severity and status.',
  why: 'Tackling the right obstacle first frees the most work.',
  questions: [
    'Which obstacle holds up the most tasks?',
    'Which has been open the longest?',
    'Do several obstacles share one cause?',
  ],
  quickActions: ['Which obstacle should I tackle first?', 'Do you see a pattern?', 'Which blocked tasks need an obstacle?'],
};

const MESSAGE_FIELD: AreaHelp = {
  label: 'this part of your message',
  needs: 'Each part of the message has one job. Keep it short and specific to this partner.',
  why: 'A message built from clear parts is easier to say yes to.',
  questions: [
    'What does this partner care about?',
    'Is this part about them, or only about you?',
    'Could you say it in one sentence?',
  ],
  quickActions: ['Improve this', 'Give me two other versions', 'Why does this matter?'],
};

const MESSAGE_WHOLE: AreaHelp = {
  label: 'your message',
  needs: 'One clear request, what is in it for the partner, and one next step, in a respectful tone.',
  why: 'A short, specific, polite ask gets more replies than a long or demanding one.',
  questions: [
    'Is there exactly one request?',
    'Does it say what, when and how long?',
    'Does it say what the partner gains?',
    'Would you be happy to receive it?',
  ],
  quickActions: ['Check my message', 'Make it shorter', 'Make it warmer'],
};

const MESSAGE_LIST: AreaHelp = {
  label: 'your messages',
  needs: 'Your drafts, sent messages and replies, with their follow-up dates.',
  why: 'A polite follow-up at the right time often turns silence into a yes.',
  questions: [
    'Which sent message is past its follow-up date?',
    'Which draft would unblock a task if you sent it?',
    'Who has helped you and deserves a thank-you?',
  ],
  quickActions: ['Who should I follow up with?', 'Which message should I send first?', 'How do I follow up politely?'],
};

const WORK_STYLE_QUESTION: AreaHelp = {
  label: 'this question',
  needs: 'Pick the statement closer to how you usually work, not how you wish you worked.',
  why: 'Honest picks give a result you can use. Neither choice is better.',
  questions: [
    'Think of a normal week at work or home: which did you do more often?',
    'If both fit, which fits a little more?',
  ],
  quickActions: ['What does this question mean?', 'Neither fits me well — what should I do?', 'Why does this test have only two choices?'],
};

const WORK_STYLE_RESULT: AreaHelp = {
  label: 'your work style',
  needs: 'Your main style and, if your answers were close, a second one. They are tendencies, not fixed traits.',
  why: 'Knowing your style helps you plan around blind spots and find partners who balance you.',
  questions: [
    'Where does this style already help your plan?',
    'Where does its blind spot slow you down?',
    'Which partner style would balance yours?',
  ],
  quickActions: ['What does my result mean?', 'How can my style help my plan?', 'Which partner style would balance mine?'],
};

const WORK_STYLE_PARTNER: AreaHelp = {
  label: 'this partner',
  needs: 'Your estimate of how this partner usually works, and how to work well together.',
  why: 'Fitting how you ask to how they work makes a yes more likely. Their style is only your estimate.',
  questions: [
    'How does this person usually make decisions?',
    'Do they talk more about the work, or about people?',
    'What has worked well with them before?',
  ],
  quickActions: ['How do I work well with this person?', 'Adapt my message to them', 'Help me estimate their style'],
};

const PAGE_HELP: Record<AssistantPage, Partial<Record<AreaKind, AreaHelp>> & { page: AreaHelp }> = {
  MODULES: { page: MODULES_LIST, list: MODULES_LIST },
  MODULE: { page: MODULE_EXERCISE, exercise: MODULE_EXERCISE },
  ASSESSMENT: { page: ASSESSMENT_STATEMENT, statement: ASSESSMENT_STATEMENT, result: ASSESSMENT_RESULT },
  OBSTACLES: { page: OBSTACLE_LIST, list: OBSTACLE_LIST, item: OBSTACLE_ITEM, form: OBSTACLE_ITEM },
  COMMUNICATION: { page: MESSAGE_LIST, list: MESSAGE_LIST, item: MESSAGE_WHOLE, message: MESSAGE_WHOLE, form: MESSAGE_WHOLE, field: MESSAGE_FIELD },
  WORK_STYLE: { page: WORK_STYLE_RESULT, result: WORK_STYLE_RESULT, question: WORK_STYLE_QUESTION, partner: WORK_STYLE_PARTNER },
};

/**
 * AH-FR-61: what each message field is for, and one example. Program content,
 * written once in English and Khmer; needs no AI.
 */
export const MESSAGE_FIELD_GUIDE: Record<string, { note: string; example: string }> = {
  audience: { note: 'Who you are writing to, so the message fits them.', example: 'Example: Dr Lina, head of the research unit' },
  purpose: { note: 'What you are working on and why it matters, in one sentence.', example: 'Example: I am preparing a concept note on clean water for rural clinics.' },
  subject: { note: 'A short subject line that says what the message is about.', example: 'Example: 20 minutes of advice on my concept note?' },
  hook: { note: 'An opening line about them, so they want to read on.', example: 'Example: Your talk on rural clinics changed how I plan my research.' },
  wordPicture: { note: 'A short scene that makes your need easy to picture.', example: 'Example: I have the pieces, but I keep taking wrong turns, like driving a new city without a map.' },
  problem: { note: 'The gap or problem you need help with.', example: 'Example: I am not sure my method can answer my research question.' },
  request: { note: 'Exactly what you are asking for: what, when and how long.', example: 'Example: Could you review my two-page method section for 20 minutes before 15 December?' },
  objectionsAndAnswers: { note: 'Reasons they might say no, and your honest answer to each.', example: 'Example: Too busy – it takes 20 minutes and I will send it ready to read.' },
  socialProof: { note: 'Why you are worth helping: results, references, what you have done.', example: 'Example: My first study was published in the national health journal.' },
  valueComparison: { note: 'Why the help is worth more to them than it costs them.', example: 'Example: Twenty minutes now could save a year of work on the wrong method.' },
  benefitToPartner: { note: 'What they gain by helping.', example: 'Example: An early look at data from three provinces, and credit in the paper.' },
  expectedOutcome: { note: 'What will happen because of their help.', example: 'Example: I can submit a stronger proposal to the fund in January.' },
  callToAction: { note: 'The one clear next step you are asking for.', example: 'Example: Would a short call on Tuesday or Thursday afternoon suit you?' },
  messageBody: { note: 'The full message, built from the parts above. Keep it short and polite.', example: 'Example: About 150 words: greeting, hook, request, benefit, next step, thanks.' },
};

export function helpFor(page: AssistantPage, area: string): AreaHelp {
  const help = PAGE_HELP[page];
  const kind = areaKind(area);
  const field = area.split(':')[1];
  const guide = page === 'COMMUNICATION' && kind === 'field' && field ? MESSAGE_FIELD_GUIDE[field] : undefined;
  if (guide) {
    // AH-FR-61: the field guide is the program's own help for one field.
    return { ...MESSAGE_FIELD, needs: guide.note, why: guide.example };
  }
  return help[kind] ?? help.page;
}

/** AH-FR-43: the message fields, by the key the form and the server use. */
export const MESSAGE_FIELD_LABELS: Record<string, string> = {
  audience: 'Audience',
  purpose: 'Purpose',
  subject: 'Subject',
  hook: 'Hook',
  wordPicture: 'Word Picture',
  problem: 'Problem',
  request: 'Request',
  objectionsAndAnswers: 'Objections & Answers',
  socialProof: 'Social Proof',
  valueComparison: 'Value Comparison',
  benefitToPartner: 'Benefit to Partner',
  expectedOutcome: 'Expected Outcome',
  callToAction: 'Call to Action',
  messageBody: 'Message',
  status: 'Status',
};

/** The obstacle fields a card may fill. */
export const OBSTACLE_FIELD_LABELS: Record<string, string> = {
  title: 'Title',
  description: 'Description',
  obstacleType: 'Type',
  severity: 'Severity',
  rootCause: 'Root cause',
  solution: 'Solution',
  creativeAlternatives: 'Creative alternatives',
};

/** Fields whose card offers a fixed list of values. */
export const FIELD_CHOICES: Record<string, string[]> = {
  obstacleType: ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'MOTIVATION', 'PARTNER', 'SYSTEM', 'DECISION', 'OTHER'],
  severity: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'],
  status: ['DRAFT', 'SENT', 'FOLLOWED_UP', 'REPLIED', 'CLOSED'],
};

/** Multi-line fields get a larger box on their card. */
export const SHORT_FIELDS = new Set(['title', 'subject', 'audience', 'hook']);

/** The page names shown in "Helping with:". */
export const PAGE_LABELS: Record<AssistantPage, string> = {
  MODULES: 'Modules',
  MODULE: 'Module',
  ASSESSMENT: 'Assessment',
  OBSTACLES: 'Obstacles',
  COMMUNICATION: 'Communication',
  WORK_STYLE: 'Work Style',
};
