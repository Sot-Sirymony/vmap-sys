import { describe, expect, it } from 'vitest';
import { generateMessageBody, type MessageGeneratorFields } from './communicationMessageGenerator';

const BASE_FIELDS: MessageGeneratorFields = {
  recipient: 'Alex',
  hook: 'I loved your talk on mentorship last month.',
  purpose: 'building a literature review workflow',
  problem: 'I keep losing track of sources across three tools.',
  wordPicture: '',
  objectionsAndAnswers: '',
  socialProof: '',
  valueComparison: '',
  request: 'a 30-minute call to walk through your process',
  expectedOutcome: 'me finish the review two weeks sooner',
  benefitToPartner: 'a case study you could reference later',
  callToAction: '',
};

describe('generateMessageBody', () => {
  it('is unaffected by the FR-52 fields when they are blank (AC #2)', () => {
    const withoutFr52 = generateMessageBody(BASE_FIELDS);
    const explicitlyBlankFr52 = generateMessageBody({
      ...BASE_FIELDS,
      objectionsAndAnswers: '   ',
      socialProof: '',
      valueComparison: '',
      callToAction: '   ',
    });

    expect(withoutFr52).toBe(explicitlyBlankFr52);
    expect(withoutFr52).toContain('Would you be open to a short conversation about this?');
  });

  it('includes the FR-52 fields in the documented order when filled (AC #3)', () => {
    const body = generateMessageBody({
      ...BASE_FIELDS,
      objectionsAndAnswers: 'You might wonder if this is worth your time — it should take only 30 minutes.',
      socialProof: 'Two of your former mentees mentioned how useful this was for them.',
      valueComparison: 'The half hour costs you little next to what a public case study could bring you.',
      callToAction: 'Reply with a day that works and I will send a calendar invite.',
    });

    const orderedFragments = [
      'I loved your talk',
      'building a literature review workflow',
      'You might wonder if this is worth your time',
      'Two of your former mentees',
      'The half hour costs you little',
      'a 30-minute call to walk through your process',
      // Pre-existing (pre-FR-52) closing order is outcome then benefit —
      // preserved as-is, not "corrected" to the FR-17 doc's stated order.
      'me finish the review two weeks sooner',
      'a case study you could reference later',
      'Reply with a day that works',
    ];
    let cursor = -1;
    for (const fragment of orderedFragments) {
      const index = body.indexOf(fragment);
      expect(index).toBeGreaterThan(cursor);
      cursor = index;
    }
    // The custom call to action replaces the generic closer, not adds to it.
    expect(body).not.toContain('Would you be open to a short conversation about this?');
  });

  it('keeps the standalone pronoun "I" capitalized when lowering a sentence', () => {
    const body = generateMessageBody({
      ...BASE_FIELDS,
      hook: '',
      purpose: '',
      problem: "I've been stuck on this for weeks.",
      request: "I'd love your perspective.",
    });

    expect(body).toContain("Right now, I've been stuck on this for weeks.");
    expect(body).toContain("I would be grateful for your help with I'd love your perspective.");
  });

  it('does not change the default tone\'s output (FR-54.3 AC #3)', () => {
    expect(generateMessageBody(BASE_FIELDS, 'DEFAULT')).toBe(generateMessageBody(BASE_FIELDS));
  });

  it('composes the three-part positive/correction/confidence structure for constructive feedback (FR-54.3 AC #3)', () => {
    const body = generateMessageBody({
      ...BASE_FIELDS,
      hook: 'Your presentation to the team was clear and well organized.',
      request: 'bring in supporting data before proposing a change like that',
      expectedOutcome: 'the next proposal lands even better than this one did',
    }, 'CONSTRUCTIVE_FEEDBACK');

    const observationIndex = body.indexOf('Your presentation to the team was clear and well organized.');
    const correctionIndex = body.indexOf('bring in supporting data before proposing a change like that');
    const confidenceIndex = body.indexOf('the next proposal lands even better than this one did');

    expect(observationIndex).toBeGreaterThan(-1);
    expect(correctionIndex).toBeGreaterThan(observationIndex);
    expect(confidenceIndex).toBeGreaterThan(correctionIndex);
    // Fields that aren't part of the three-part structure are not pulled in.
    expect(body).not.toContain(BASE_FIELDS.purpose);
    expect(body).not.toContain(BASE_FIELDS.benefitToPartner);
  });
});
