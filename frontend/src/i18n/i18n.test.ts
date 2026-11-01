import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { translate } from './I18nContext';
import { km } from './km';

describe('translate', () => {
  it('returns Khmer when there is an entry, filling in values', () => {
    expect(translate('km', 'Today')).toBe('ថ្ងៃនេះ');
    expect(translate('km', '{score} of 5', { score: 4 })).toBe('4 លើ 5');
  });

  it('falls back to English when there is no entry', () => {
    expect(translate('km', 'A sentence nobody translated')).toBe('A sentence nobody translated');
  });

  it('leaves English untouched apart from values', () => {
    expect(translate('en', '{score} of 5', { score: 4 })).toBe('4 of 5');
  });

  it('keeps every placeholder in every Khmer entry', () => {
    for (const [english, khmer] of Object.entries(km)) {
      const placeholders = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort();
      expect(placeholders(khmer), english).toEqual(placeholders(english));
    }
  });

  it('wraps every backend message the pages show, so the server text is translated too', () => {
    const service = readFileSync(resolve(__dirname, '../../../backend/src/main/java/com/visionmapping/service/LearningModuleService.java'), 'utf-8');
    const messages = [...service.matchAll(/\? null : "([^"]+)"/g)].map((match) => match[1])
      // Names the user's own dreams, so it has no fixed text to translate.
      .filter((message) => !message.endsWith(': '));
    expect(messages.length).toBeGreaterThan(5);
    for (const message of messages) {
      expect(km[message], message).toBeDefined();
    }
  });
});
