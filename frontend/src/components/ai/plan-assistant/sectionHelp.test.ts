import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { km } from '../../../i18n/km';
import { SECTION_HELP, changeLabel } from './sectionHelp';

/** Every tr('...') text in a file. */
function trTexts(file: string) {
  const source = readFileSync(resolve(__dirname, file), 'utf-8');
  return [...source.matchAll(/tr\('((?:[^'\\]|\\.)*)'/g)].map((match) => match[1].replace(/\\'/g, "'"));
}

describe('plan assistant text (V11 Phase 7, PB-NFR-13)', () => {
  it('gives every section three quick actions and at least two guiding questions', () => {
    for (const [section, help] of Object.entries(SECTION_HELP)) {
      expect(help.quickActions, section).toHaveLength(3);
      expect(help.questions.length, section).toBeGreaterThanOrEqual(2);
    }
  });

  it('has Khmer for every help text, card heading, panel text and server message', () => {
    const texts = new Set<string>();
    for (const help of Object.values(SECTION_HELP)) {
      [help.label, help.needs, help.why, ...help.questions, ...help.quickActions].forEach((text) => texts.add(text));
    }
    for (const kind of ['EDIT', 'ADD_STEP', 'ADD_TASK', 'ADD_RISK']) {
      for (const field of ['TITLE', 'SUCCESS', 'WHY', 'DATE', 'OWNER']) {
        texts.add(changeLabel(kind, field));
      }
    }
    trTexts('./PlanAssistant.tsx').forEach((text) => texts.add(text));
    trTexts('./ChangeCard.tsx').forEach((text) => texts.add(text));
    ['Knowledge', 'Skill', 'Time', 'Money', 'Motivation', 'Partner', 'System', 'Decision', 'Other', 'Low', 'Medium', 'High',
      'Critical', 'this section of your plan'].forEach((text) => texts.add(text));
    const service = readFileSync(resolve(__dirname,
      '../../../../../backend/src/main/java/com/visionmapping/service/ai/PlanAssistantService.java'), 'utf-8');
    [...service.matchAll(/(?:BusinessRuleException|ResourceNotFoundException|required\([^,]+,)\s*\(?"([^"]+)"/g)]
      .map((match) => match[1])
      // Followed by the validation message, so it has no fixed text to translate.
      .filter((text) => !text.endsWith(': '))
      .forEach((text) => texts.add(text));
    const missing = [...texts].filter((text) => km[text] === undefined);
    expect(missing).toEqual([]);
  });
});
