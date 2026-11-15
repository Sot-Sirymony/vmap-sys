/**
 * V17 BL-FR-13: phrases that put a limit or a blocker into a dream or goal.
 * The method keeps dreams and goals bold and outcome-only; a gap like "no
 * budget" belongs on a step. This list is English only in V17 (BL-NFR-04) and
 * lives in one place so it can be tuned without touching the forms.
 *
 * Kept to phrases that rarely mean anything else, so the note stays rare.
 */
export const LIMIT_PHRASES: readonly string[] = [
  "can't afford",
  'cannot afford',
  'can not afford',
  'if i can afford',
  'if i have time',
  'if i had time',
  'if i find the time',
  'if i ever have time',
  "but i don't",
  'but i do not',
  "but i can't",
  'but i cannot',
  'not realistic',
  'unrealistic for me',
  "i'm not good at",
  'i am not good at',
  "i don't have the money",
  "i don't have enough money",
  "i don't have time",
  "i don't have the time",
  'i do not have time',
  'too old to',
  'too late for me',
  'probably impossible',
  'if nothing gets in the way',
];

/** The first limit phrase found in any of the texts, or null. Case and curly apostrophes don't matter. */
export function findLimitPhrase(texts: readonly (string | null | undefined)[]): string | null {
  const joined = texts
    .filter((text): text is string => Boolean(text))
    .join('\n')
    .toLowerCase()
    .replace(/[‘’]/g, "'");
  if (!joined) {
    return null;
  }
  return LIMIT_PHRASES.find((phrase) => joined.includes(phrase)) ?? null;
}
