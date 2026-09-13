// FR-17.3 / FR-52.2: compose in a fixed persuasive order — Hook, Problem
// (with the word picture folded in), Objections & Answers, Social Proof,
// Value Comparison, Request, Benefit, Expected Outcome, Call to Action —
// including only the parts the user actually filled, in respectful
// language. The draft stays fully editable afterward.
//
// Extracted from CommunicationBuilderPage so FR-52's "byte-for-byte
// unchanged when the four new fields are blank" acceptance criterion is
// directly testable, not just visually inspectable.

export type MessageGeneratorFields = {
  recipient: string;
  purpose: string;
  problem: string;
  wordPicture: string;
  objectionsAndAnswers: string;
  socialProof: string;
  valueComparison: string;
  request: string;
  expectedOutcome: string;
  benefitToPartner: string;
  callToAction: string;
  hook: string;
};

// Lowercases the first letter so a user's field (often written as its own
// sentence) reads naturally mid-sentence in the generated body. Leaves the
// standalone pronoun "I" capitalized, since lowering it to "i" reads wrong.
function lowerFirst(value: string) {
  if (/^I(\s|'|$)/.test(value)) {
    return value;
  }
  return value.charAt(0).toLowerCase() + value.slice(1);
}

const DEFAULT_CALL_TO_ACTION = 'Would you be open to a short conversation about this? No pressure either way, and thank you for considering it.';

// FR-54.3: an alternate tone for the same generator, not a second one — it
// reuses three existing structured fields rather than adding new ones.
// Positive observation = hook (already "an opening line that earns
// attention"); specific correction = request (already "the specific ask,"
// which for feedback IS the correction); closing confidence = expected
// outcome (already "the hoped-for result").
export type MessageTone = 'DEFAULT' | 'CONSTRUCTIVE_FEEDBACK';

export function generateMessageBody(fields: MessageGeneratorFields, tone: MessageTone = 'DEFAULT'): string {
  if (tone === 'CONSTRUCTIVE_FEEDBACK') {
    return generateConstructiveFeedbackBody(fields);
  }
  return generateDefaultBody(fields);
}

function generateConstructiveFeedbackBody(fields: MessageGeneratorFields): string {
  const paragraphs: string[] = [];
  if (fields.hook.trim()) {
    paragraphs.push(fields.hook.trim());
  }
  if (fields.request.trim()) {
    paragraphs.push(`One thing I'd love to see change: ${lowerFirst(fields.request.trim())}`);
  }
  if (fields.expectedOutcome.trim()) {
    paragraphs.push(`I have real confidence that ${lowerFirst(fields.expectedOutcome.trim())}`);
  }
  return `Dear ${fields.recipient},\n\n${paragraphs.join('\n\n')}\n\nBest regards`;
}

function generateDefaultBody(fields: MessageGeneratorFields): string {
  const paragraphs: string[] = [];

  if (fields.hook.trim()) {
    paragraphs.push(fields.hook.trim());
  }

  const contextSentences: string[] = [];
  if (fields.purpose.trim()) {
    contextSentences.push(`I am working on ${fields.purpose.trim()}.`);
  }
  if (fields.problem.trim()) {
    contextSentences.push(`Right now, ${lowerFirst(fields.problem.trim())}`);
  }
  if (fields.wordPicture.trim()) {
    contextSentences.push(`To put it plainly: ${lowerFirst(fields.wordPicture.trim())}`);
  }
  if (contextSentences.length > 0) {
    paragraphs.push(contextSentences.join(' '));
  }

  // FR-52.1/52.2: three freeform blobs, each its own paragraph when filled —
  // same "push verbatim, no synthetic wrapper" treatment as hook above,
  // since the user is expected to already phrase these in their own voice.
  if (fields.objectionsAndAnswers.trim()) {
    paragraphs.push(fields.objectionsAndAnswers.trim());
  }
  if (fields.socialProof.trim()) {
    paragraphs.push(fields.socialProof.trim());
  }
  if (fields.valueComparison.trim()) {
    paragraphs.push(fields.valueComparison.trim());
  }

  if (fields.request.trim()) {
    paragraphs.push(`I would be grateful for your help with ${lowerFirst(fields.request.trim())}`);
  }

  const closingSentences: string[] = [];
  if (fields.expectedOutcome.trim()) {
    closingSentences.push(`Your support would help ${lowerFirst(fields.expectedOutcome.trim())}`);
  }
  if (fields.benefitToPartner.trim()) {
    closingSentences.push(`I also hope this could be worthwhile for you: ${lowerFirst(fields.benefitToPartner.trim())}`);
  }
  if (closingSentences.length > 0) {
    paragraphs.push(closingSentences.join(' '));
  }

  // FR-52.1: a specific call to action replaces the generic closer when
  // supplied — FR-52.3 keeps the original wording when it's left blank.
  paragraphs.push(fields.callToAction.trim() || DEFAULT_CALL_TO_ACTION);

  return `Dear ${fields.recipient},\n\n${paragraphs.join('\n\n')}\n\nBest regards`;
}
